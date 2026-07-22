package com.freewillase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freewillase.backend.domain.EnzymeEntry;
import com.freewillase.backend.domain.NcbiImportTask;
import com.freewillase.backend.domain.NcbiImportTaskItem;
import com.freewillase.backend.dto.EnzymeEntryResponse;
import com.freewillase.backend.dto.ImportTaskItemResponse;
import com.freewillase.backend.dto.ImportTaskResponse;
import com.freewillase.backend.mapper.EnzymeEntryMapper;
import com.freewillase.backend.mapper.NcbiImportTaskItemMapper;
import com.freewillase.backend.mapper.NcbiImportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NcbiImportService {

    private final NcbiEutilsClient ncbiEutilsClient;
    private final EnzymeEntryMapper enzymeEntryMapper;
    private final NcbiImportTaskMapper taskMapper;
    private final NcbiImportTaskItemMapper taskItemMapper;
    private final com.freewillase.backend.mapper.LiteratureRelationMapper relationMapper;

    @Autowired
    @Lazy
    private NcbiImportService self;

    public NcbiImportService(
            NcbiEutilsClient ncbiEutilsClient,
            EnzymeEntryMapper enzymeEntryMapper,
            NcbiImportTaskMapper taskMapper,
            NcbiImportTaskItemMapper taskItemMapper,
            com.freewillase.backend.mapper.LiteratureRelationMapper relationMapper) {
        this.ncbiEutilsClient = ncbiEutilsClient;
        this.enzymeEntryMapper = enzymeEntryMapper;
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.relationMapper = relationMapper;
    }

    public ImportTaskResponse importAccessions(String taskName, List<String> accessions, String email, String apiKey) {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. Create Task Record
        NcbiImportTask task = NcbiImportTask.builder()
                .taskName(taskName == null || taskName.isBlank() ? "batch_import_" + UUID.randomUUID().toString().substring(0, 8) : taskName.trim())
                .sourceType("NCBI")
                .totalCount(accessions.size())
                .status("RUNNING")
                .createdAt(now)
                .build();
        taskMapper.insert(task);

        // 2. Run Asynchronously via self-proxy
        self.executeImportTask(task, accessions, email, apiKey);

        return toTaskResponse(task, null);
    }

    @Async
    public void executeImportTask(NcbiImportTask task, List<String> accessions, String email, String apiKey) {
        log.info("Starting async import task: {}", task.getId());
        int successCount = 0;
        int failedCount = 0;
        int duplicateCount = 0;

        for (String rawAccession : accessions) {
            String accession = normalizeAccession(rawAccession);
            if (accession.isBlank()) {
                failedCount++;
                saveTaskItem(task.getId(), rawAccession, "FAILED", "accession 为空", null);
                continue;
            }

            // Check if already exists in DB
            EnzymeEntry existing = enzymeEntryMapper.selectOne(new LambdaQueryWrapper<EnzymeEntry>()
                    .eq(EnzymeEntry::getProteinAccession, accession));
            
            if (existing != null) {
                duplicateCount++;
                saveTaskItem(task.getId(), accession, "DUPLICATE", "该 accession 已在本地酶库中", existing.getId());
                continue;
            }

            try {
                NcbiEutilsClient.ProteinLookupResult result = ncbiEutilsClient.fetchProteinByAccession(accession, email, apiKey);
                
                // Create Enzyme Entry
                EnzymeEntry entry = EnzymeEntry.builder()
                        .code("ENZ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .proteinAccession(result.getAccession())
                        .name(result.getTitle())
                        .organism(result.getOrganism())
                        .taxId(result.getTaxId())
                        .sequenceLength(result.getSequenceLength())
                        .sequenceHash(calculateHash(result.getSequence()))
                        .status("ACTIVE")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                enzymeEntryMapper.insert(entry);

                successCount++;
                saveTaskItem(task.getId(), entry.getProteinAccession(), "SUCCESS", "已从 NCBI 补全并写入数据库", entry.getId());
            } catch (Exception ex) {
                log.error("Failed to import accession: {}", accession, ex);
                failedCount++;
                saveTaskItem(task.getId(), accession, "FAILED", ex.getMessage(), null);
            }

            // Update progress in task record (optional but good for polling)
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setDuplicateCount(duplicateCount);
            taskMapper.updateById(task);
        }

        // Final status
        task.setStatus(failedCount > 0 && successCount > 0 ? "PARTIAL_SUCCESS" : (failedCount > 0 ? "FAILED" : "SUCCESS"));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("Finished async import task: {}", task.getId());
    }

    private void saveTaskItem(Long taskId, String accession, String status, String message, Long enzymeId) {
        NcbiImportTaskItem item = NcbiImportTaskItem.builder()
                .taskId(taskId)
                .accession(accession)
                .status(status)
                .message(message)
                .enzymeId(enzymeId)
                .createdAt(LocalDateTime.now())
                .build();
        taskItemMapper.insert(item);
    }

    public ImportTaskResponse getTask(Long taskId) {
        NcbiImportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("未找到导入任务: " + taskId);
        }
        List<NcbiImportTaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<NcbiImportTaskItem>()
                .eq(NcbiImportTaskItem::getTaskId, taskId));
        return toTaskResponse(task, items);
    }

    public ImportTaskResponse getLatestTask() {
        NcbiImportTask task = taskMapper.selectOne(new LambdaQueryWrapper<NcbiImportTask>()
                .orderByDesc(NcbiImportTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) return null;
        
        List<NcbiImportTaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<NcbiImportTaskItem>()
                .eq(NcbiImportTaskItem::getTaskId, task.getId()));
        return toTaskResponse(task, items);
    }

    public List<EnzymeEntryResponse> listEnzymes() {
        return enzymeEntryMapper.selectList(new LambdaQueryWrapper<EnzymeEntry>()
                .orderByDesc(EnzymeEntry::getCreatedAt))
                .stream()
                .map(entry -> EnzymeEntryResponse.builder()
                        .id(entry.getId())
                        .accession(entry.getProteinAccession())
                        .proteinName(entry.getName())
                        .organismName(entry.getOrganism())
                        .taxId(entry.getTaxId())
                        .sequenceLength(entry.getSequenceLength())
                        .sequenceHash(entry.getSequenceHash())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteEnzyme(Long id) {
        // 1. Delete literature relations
        relationMapper.delete(new LambdaQueryWrapper<com.freewillase.backend.domain.LiteratureRelation>()
                .eq(com.freewillase.backend.domain.LiteratureRelation::getEnzymeId, id));
        
        // 2. Delete enzyme entry
        enzymeEntryMapper.deleteById(id);
        
        log.info("Deleted enzyme entry and related data for ID: {}", id);
    }

    private ImportTaskResponse toTaskResponse(NcbiImportTask task, List<NcbiImportTaskItem> items) {
        ImportTaskResponse.ImportTaskResponseBuilder builder = ImportTaskResponse.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .successCount(task.getSuccessCount())
                .failedCount(task.getFailedCount())
                .duplicateCount(task.getDuplicateCount())
                .createdAt(task.getCreatedAt())
                .finishedAt(task.getFinishedAt());
        
        if (items != null) {
            for (NcbiImportTaskItem item : items) {
                builder.item(ImportTaskItemResponse.builder()
                        .accession(item.getAccession())
                        .status(item.getStatus())
                        .message(item.getMessage())
                        .enzymeId(item.getEnzymeId())
                        .build());
            }
        }
        return builder.build();
    }

    private String normalizeAccession(String accession) {
        return accession == null ? "" : accession.trim().toUpperCase();
    }

    private String calculateHash(String sequence) {
        if (sequence == null || sequence.isBlank()) return "-";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sequence.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "-";
        }
    }
}
