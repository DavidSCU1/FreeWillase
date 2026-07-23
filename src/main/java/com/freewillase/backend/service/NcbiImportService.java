package com.freewillase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freewillase.backend.domain.EnzymeCrossRef;
import com.freewillase.backend.domain.EnzymeEntry;
import com.freewillase.backend.domain.EnzymeSequence;
import com.freewillase.backend.domain.EnzymeStructure;
import com.freewillase.backend.domain.NcbiImportTask;
import com.freewillase.backend.domain.NcbiImportTaskItem;
import com.freewillase.backend.dto.EnzymeEntryResponse;
import com.freewillase.backend.dto.ImportTaskItemResponse;
import com.freewillase.backend.dto.ImportTaskResponse;
import com.freewillase.backend.mapper.EnzymeCrossRefMapper;
import com.freewillase.backend.mapper.EnzymeEntryMapper;
import com.freewillase.backend.mapper.EnzymeSequenceMapper;
import com.freewillase.backend.mapper.EnzymeStructureMapper;
import com.freewillase.backend.mapper.NcbiImportTaskItemMapper;
import com.freewillase.backend.mapper.NcbiImportTaskMapper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NcbiImportService {

    private final NcbiEutilsClient ncbiEutilsClient;
    private final UniProtClient uniProtClient;
    private final EnzymeCrossRefMapper enzymeCrossRefMapper;
    private final EnzymeEntryMapper enzymeEntryMapper;
    private final EnzymeSequenceMapper enzymeSequenceMapper;
    private final EnzymeStructureMapper enzymeStructureMapper;
    private final NcbiImportTaskMapper taskMapper;
    private final NcbiImportTaskItemMapper taskItemMapper;
    private final com.freewillase.backend.mapper.LiteratureRelationMapper relationMapper;

    @Autowired
    @Lazy
    private NcbiImportService self;

    public NcbiImportService(
            NcbiEutilsClient ncbiEutilsClient,
            UniProtClient uniProtClient,
            EnzymeCrossRefMapper enzymeCrossRefMapper,
            EnzymeEntryMapper enzymeEntryMapper,
            EnzymeSequenceMapper enzymeSequenceMapper,
            EnzymeStructureMapper enzymeStructureMapper,
            NcbiImportTaskMapper taskMapper,
            NcbiImportTaskItemMapper taskItemMapper,
            com.freewillase.backend.mapper.LiteratureRelationMapper relationMapper) {
        this.ncbiEutilsClient = ncbiEutilsClient;
        this.uniProtClient = uniProtClient;
        this.enzymeCrossRefMapper = enzymeCrossRefMapper;
        this.enzymeEntryMapper = enzymeEntryMapper;
        this.enzymeSequenceMapper = enzymeSequenceMapper;
        this.enzymeStructureMapper = enzymeStructureMapper;
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
                Optional<UniProtClient.ProteinEnrichment> enrichment = loadUniProtEnrichment(result);
                
                // Create Enzyme Entry
                EnzymeEntry entry = EnzymeEntry.builder()
                        .code("ENZ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .proteinAccession(result.getAccession())
                        .proteinVersion(extractProteinVersion(result.getAccession()))
                        .geneSymbol(enrichment.map(UniProtClient.ProteinEnrichment::getGeneSymbol).orElse(null))
                        .name(result.getTitle())
                        .ecNumber(enrichment.map(UniProtClient.ProteinEnrichment::getEcNumber).orElse(null))
                        .organism(result.getOrganism())
                        .taxId(result.getTaxId())
                        .description(enrichment.map(UniProtClient.ProteinEnrichment::getFunctionSummary).orElse(null))
                        .status("ACTIVE")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                enzymeEntryMapper.insert(entry);
                savePrimarySequence(entry.getId(), result.getSequence(), result.getSequenceLength(), "NCBI");
                saveCrossReference(
                        entry.getId(),
                        "NCBI",
                        "PROTEIN_ACCESSION",
                        result.getAccession(),
                        buildNcbiProteinUrl(result.getAccession()),
                        1
                );
                applyUniProtEnrichment(entry.getId(), enrichment);

                successCount++;
                saveTaskItem(task.getId(), entry.getProteinAccession(), "SUCCESS", buildSuccessMessage(enrichment), entry.getId());
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
        List<EnzymeEntry> entries = enzymeEntryMapper.selectList(new LambdaQueryWrapper<EnzymeEntry>()
                .orderByDesc(EnzymeEntry::getCreatedAt));
        Map<Long, EnzymeSequence> primarySequences = loadPrimarySequences(entries);
        Map<Long, EnzymeStructure> primaryStructures = loadPrimaryStructures(entries);
        Map<Long, Map<String, EnzymeCrossRef>> primaryCrossRefs = loadPrimaryCrossRefs(entries);

        return entries
                .stream()
                .map(entry -> {
                    EnzymeStructure primaryStructure = primaryStructures.get(entry.getId());
                    Map<String, EnzymeCrossRef> refs = primaryCrossRefs.getOrDefault(entry.getId(), Collections.emptyMap());
                    EnzymeCrossRef ncbiRef = refs.get("NCBI");
                    EnzymeCrossRef uniprotRef = refs.get("UNIPROT");
                    EnzymeCrossRef pdbRef = refs.get("PDB");

                    return EnzymeEntryResponse.builder()
                            .id(entry.getId())
                            .accession(entry.getProteinAccession())
                            .proteinName(entry.getName())
                            .organismName(entry.getOrganism())
                            .taxId(entry.getTaxId())
                            .sequenceLength(readSequenceLength(primarySequences.get(entry.getId())))
                            .sequenceHash(readSequenceHash(primarySequences.get(entry.getId())))
                            .structureType(readStructureType(primaryStructure))
                            .structureId(readStructureId(primaryStructure))
                            .structureSourceDb(readStructureSourceDb(primaryStructure))
                            .structureUrl(readStructureUrl(primaryStructure))
                            .ncbiProteinAccession(readCrossRefValue(ncbiRef))
                            .ncbiProteinUrl(readCrossRefUrl(ncbiRef))
                            .uniprotAccession(readCrossRefValue(uniprotRef))
                            .uniprotUrl(readCrossRefUrl(uniprotRef))
                            .pdbId(readPdbId(pdbRef, primaryStructure))
                            .pdbUrl(readPdbUrl(pdbRef, primaryStructure))
                            .createdAt(entry.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteEnzyme(Long id) {
        // 1. Delete literature relations
        relationMapper.delete(new LambdaQueryWrapper<com.freewillase.backend.domain.LiteratureRelation>()
                .eq(com.freewillase.backend.domain.LiteratureRelation::getEnzymeId, id));

        // 2. Delete related sequences
        enzymeSequenceMapper.delete(new LambdaQueryWrapper<EnzymeSequence>()
                .eq(EnzymeSequence::getEnzymeId, id));

        // 3. Delete related structures
        enzymeStructureMapper.delete(new LambdaQueryWrapper<EnzymeStructure>()
                .eq(EnzymeStructure::getEnzymeId, id));

        // 4. Delete cross references
        enzymeCrossRefMapper.delete(new LambdaQueryWrapper<EnzymeCrossRef>()
                .eq(EnzymeCrossRef::getEnzymeId, id));

        // 5. Delete enzyme entry
        enzymeEntryMapper.deleteById(id);

        log.info("Deleted enzyme entry and related data for ID: {}", id);
    }

    private void savePrimarySequence(Long enzymeId, String sequence, int sequenceLength, String sourceType) {
        EnzymeSequence enzymeSequence = EnzymeSequence.builder()
                .enzymeId(enzymeId)
                .versionNo(1)
                .sequenceText(sequence == null ? "" : sequence)
                .sequenceLength(sequenceLength)
                .sequenceHash(calculateHash(sequence))
                .isPrimary(1)
                .sourceType(sourceType)
                .createdAt(LocalDateTime.now())
                .build();
        enzymeSequenceMapper.insert(enzymeSequence);
    }

    private Optional<UniProtClient.ProteinEnrichment> loadUniProtEnrichment(NcbiEutilsClient.ProteinLookupResult result) {
        try {
            return uniProtClient.enrichByRefSeqAccession(result.getAccession(), result.getTaxId());
        } catch (Exception ex) {
            log.warn("UniProt enrichment failed for accession {}", result.getAccession(), ex);
            return Optional.empty();
        }
    }

    private void applyUniProtEnrichment(Long enzymeId, Optional<UniProtClient.ProteinEnrichment> enrichmentOptional) {
        if (enrichmentOptional.isEmpty()) {
            return;
        }

        UniProtClient.ProteinEnrichment enrichment = enrichmentOptional.get();
        saveCrossReference(
                enzymeId,
                "UNIPROT",
                "ACCESSION",
                enrichment.getPrimaryAccession(),
                buildUniProtUrl(enrichment.getPrimaryAccession()),
                1
        );

        boolean hasPdb = !enrichment.getPdbIds().isEmpty();
        if (hasPdb) {
            String pdbId = enrichment.getPdbIds().get(0);
            saveCrossReference(
                    enzymeId,
                    "PDB",
                    "STRUCTURE_ID",
                    pdbId,
                    buildPdbUrl(pdbId),
                    1
            );
            saveStructure(
                    enzymeId,
                    "EXPERIMENTAL",
                    pdbId,
                    "PDB",
                    buildPdbUrl(pdbId),
                    1
            );
        }

        if (enrichment.getAlphaFoldAccession() != null && !enrichment.getAlphaFoldAccession().isBlank()) {
            saveStructure(
                    enzymeId,
                    "PREDICTED",
                    enrichment.getAlphaFoldAccession(),
                    "AlphaFold",
                    buildAlphaFoldEntryUrl(enrichment.getAlphaFoldAccession()),
                    hasPdb ? 0 : 1
            );
        }
    }

    private void saveStructure(Long enzymeId, String structureType, String structureId, String sourceDb, String sourceUrl, int isPrimary) {
        if (structureId == null || structureId.isBlank()) {
            return;
        }

        EnzymeStructure existing = enzymeStructureMapper.selectOne(new LambdaQueryWrapper<EnzymeStructure>()
                .eq(EnzymeStructure::getEnzymeId, enzymeId)
                .eq(EnzymeStructure::getStructureType, structureType)
                .eq(EnzymeStructure::getStructureId, structureId)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        EnzymeStructure structure = EnzymeStructure.builder()
                .enzymeId(enzymeId)
                .structureType(structureType)
                .structureId(structureId)
                .sourceDb(sourceDb)
                .sourceUrl(sourceUrl)
                .isPrimary(isPrimary)
                .createdAt(LocalDateTime.now())
                .build();
        enzymeStructureMapper.insert(structure);
    }

    private Map<Long, EnzymeSequence> loadPrimarySequences(List<EnzymeEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> enzymeIds = entries.stream()
                .map(EnzymeEntry::getId)
                .collect(Collectors.toList());

        List<EnzymeSequence> sequences = enzymeSequenceMapper.selectList(new LambdaQueryWrapper<EnzymeSequence>()
                .in(EnzymeSequence::getEnzymeId, enzymeIds)
                .eq(EnzymeSequence::getIsPrimary, 1)
                .orderByDesc(EnzymeSequence::getVersionNo));

        Map<Long, EnzymeSequence> primarySequences = new HashMap<>();
        for (EnzymeSequence sequence : sequences) {
            primarySequences.putIfAbsent(sequence.getEnzymeId(), sequence);
        }
        return primarySequences;
    }

    private Map<Long, EnzymeStructure> loadPrimaryStructures(List<EnzymeEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> enzymeIds = entries.stream()
                .map(EnzymeEntry::getId)
                .collect(Collectors.toList());

        List<EnzymeStructure> structures = enzymeStructureMapper.selectList(new LambdaQueryWrapper<EnzymeStructure>()
                .in(EnzymeStructure::getEnzymeId, enzymeIds)
                .orderByDesc(EnzymeStructure::getIsPrimary)
                .orderByDesc(EnzymeStructure::getCreatedAt));

        Map<Long, EnzymeStructure> primaryStructures = new HashMap<>();
        for (EnzymeStructure structure : structures) {
            primaryStructures.putIfAbsent(structure.getEnzymeId(), structure);
        }
        return primaryStructures;
    }

    private Map<Long, Map<String, EnzymeCrossRef>> loadPrimaryCrossRefs(List<EnzymeEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> enzymeIds = entries.stream()
                .map(EnzymeEntry::getId)
                .collect(Collectors.toList());

        List<EnzymeCrossRef> refs = enzymeCrossRefMapper.selectList(new LambdaQueryWrapper<EnzymeCrossRef>()
                .in(EnzymeCrossRef::getEnzymeId, enzymeIds)
                .orderByDesc(EnzymeCrossRef::getIsPrimary)
                .orderByDesc(EnzymeCrossRef::getCreatedAt));

        Map<Long, Map<String, EnzymeCrossRef>> groupedRefs = new HashMap<>();
        for (EnzymeCrossRef ref : refs) {
            groupedRefs
                    .computeIfAbsent(ref.getEnzymeId(), key -> new HashMap<>())
                    .putIfAbsent(ref.getRefDb(), ref);
        }
        return groupedRefs;
    }

    private Integer readSequenceLength(EnzymeSequence sequence) {
        return sequence == null ? 0 : sequence.getSequenceLength();
    }

    private String readSequenceHash(EnzymeSequence sequence) {
        return sequence == null ? "-" : sequence.getSequenceHash();
    }

    private String readStructureType(EnzymeStructure structure) {
        return structure == null ? null : structure.getStructureType();
    }

    private String readStructureId(EnzymeStructure structure) {
        return structure == null ? null : structure.getStructureId();
    }

    private String readStructureSourceDb(EnzymeStructure structure) {
        return structure == null ? null : structure.getSourceDb();
    }

    private String readStructureUrl(EnzymeStructure structure) {
        return structure == null ? null : structure.getSourceUrl();
    }

    private String readCrossRefValue(EnzymeCrossRef ref) {
        return ref == null ? null : ref.getRefValue();
    }

    private String readCrossRefUrl(EnzymeCrossRef ref) {
        return ref == null ? null : ref.getRefUrl();
    }

    private String readPdbId(EnzymeCrossRef pdbRef, EnzymeStructure structure) {
        if (pdbRef != null) {
            return pdbRef.getRefValue();
        }
        if (structure != null && "PDB".equalsIgnoreCase(structure.getSourceDb())) {
            return structure.getStructureId();
        }
        return null;
    }

    private String readPdbUrl(EnzymeCrossRef pdbRef, EnzymeStructure structure) {
        if (pdbRef != null) {
            return pdbRef.getRefUrl();
        }
        if (structure != null && "PDB".equalsIgnoreCase(structure.getSourceDb()) && structure.getStructureId() != null) {
            return buildPdbUrl(structure.getStructureId());
        }
        return null;
    }

    private void saveCrossReference(Long enzymeId, String refDb, String refType, String refValue, String refUrl, int isPrimary) {
        if (refValue == null || refValue.isBlank()) {
            return;
        }

        EnzymeCrossRef existing = enzymeCrossRefMapper.selectOne(new LambdaQueryWrapper<EnzymeCrossRef>()
                .eq(EnzymeCrossRef::getEnzymeId, enzymeId)
                .eq(EnzymeCrossRef::getRefDb, refDb)
                .eq(EnzymeCrossRef::getRefType, refType)
                .eq(EnzymeCrossRef::getRefValue, refValue)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        EnzymeCrossRef crossRef = EnzymeCrossRef.builder()
                .enzymeId(enzymeId)
                .refDb(refDb)
                .refType(refType)
                .refValue(refValue)
                .refUrl(refUrl)
                .isPrimary(isPrimary)
                .createdAt(LocalDateTime.now())
                .build();
        enzymeCrossRefMapper.insert(crossRef);
    }

    private String buildNcbiProteinUrl(String accession) {
        return accession == null || accession.isBlank()
                ? null
                : "https://www.ncbi.nlm.nih.gov/protein/" + accession;
    }

    private String buildPdbUrl(String pdbId) {
        return pdbId == null || pdbId.isBlank()
                ? null
                : "https://www.rcsb.org/structure/" + pdbId;
    }

    private String buildUniProtUrl(String accession) {
        return accession == null || accession.isBlank()
                ? null
                : "https://www.uniprot.org/uniprotkb/" + accession;
    }

    private String buildAlphaFoldEntryUrl(String accession) {
        return accession == null || accession.isBlank()
                ? null
                : "https://alphafold.ebi.ac.uk/entry/" + accession;
    }

    private String extractProteinVersion(String accession) {
        if (accession == null || accession.isBlank()) {
            return null;
        }
        int dot = accession.indexOf('.');
        return dot >= 0 && dot + 1 < accession.length() ? accession.substring(dot + 1) : null;
    }

    private String buildSuccessMessage(Optional<UniProtClient.ProteinEnrichment> enrichment) {
        if (enrichment.isEmpty()) {
            return "已从 NCBI 写入基础信息";
        }
        UniProtClient.ProteinEnrichment value = enrichment.get();
        boolean hasPdb = !value.getPdbIds().isEmpty();
        boolean hasAlphaFold = value.getAlphaFoldAccession() != null && !value.getAlphaFoldAccession().isBlank();
        if (hasPdb || hasAlphaFold) {
            return "已从 NCBI 和 UniProt 补全基础信息与结构引用";
        }
        return "已从 NCBI 和 UniProt 补全基础信息";
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
