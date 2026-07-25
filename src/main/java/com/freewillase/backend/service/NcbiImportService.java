package com.freewillase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freewillase.backend.domain.EnzymeAnnotation;
import com.freewillase.backend.domain.EnzymeCrossRef;
import com.freewillase.backend.domain.EnzymeEntry;
import com.freewillase.backend.domain.EnzymeSequence;
import com.freewillase.backend.domain.EnzymeStructure;
import com.freewillase.backend.domain.NcbiImportTask;
import com.freewillase.backend.domain.NcbiImportTaskItem;
import com.freewillase.backend.dto.EnzymeEntryResponse;
import com.freewillase.backend.dto.ImportTaskItemResponse;
import com.freewillase.backend.dto.ImportTaskResponse;
import com.freewillase.backend.dto.SaveMiniFoldEnzymeRequest;
import com.freewillase.backend.dto.UpsertEnzymeAnnotationRequest;
import com.freewillase.backend.mapper.EnzymeAnnotationMapper;
import com.freewillase.backend.mapper.EnzymeCrossRefMapper;
import com.freewillase.backend.mapper.EnzymeEntryMapper;
import com.freewillase.backend.mapper.EnzymeSequenceMapper;
import com.freewillase.backend.mapper.EnzymeStructureMapper;
import com.freewillase.backend.mapper.LiteratureRelationMapper;
import com.freewillase.backend.mapper.NcbiImportTaskItemMapper;
import com.freewillase.backend.mapper.NcbiImportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class NcbiImportService {

    public static final String SOURCE_TYPE_NCBI_IMPORT = "NCBI_IMPORT";
    public static final String SOURCE_TYPE_MINIFOLD_PREDICTION = "MINIFOLD_PREDICTION";
    public static final String MOLECULE_TYPE_PROTEIN = "protein";
    public static final String MOLECULE_TYPE_RNA = "RNA";
    private static final Set<String> SUPPORTED_ANNOTATION_TYPES = Set.of("DOMAIN", "ACTIVE_SITE", "MUTATION");
    private static final String ANNOTATION_SOURCE_UNIPROT = "UNIPROT";
    private static final String ANNOTATION_SOURCE_NCBI_NUCLEOTIDE = "NCBI_NUCLEOTIDE";
    private static final int ANNOTATION_TITLE_MAX_LENGTH = 512;
    private static final int ANNOTATION_MUTATION_LABEL_MAX_LENGTH = 255;
    private static final int ANNOTATION_SOURCE_REF_MAX_LENGTH = 255;

    private final NcbiEutilsClient ncbiEutilsClient;
    private final UniProtClient uniProtClient;
    private final RcsbPdbClient rcsbPdbClient;
    private final EnzymeAnnotationMapper enzymeAnnotationMapper;
    private final EnzymeCrossRefMapper enzymeCrossRefMapper;
    private final EnzymeEntryMapper enzymeEntryMapper;
    private final EnzymeSequenceMapper enzymeSequenceMapper;
    private final EnzymeStructureMapper enzymeStructureMapper;
    private final NcbiImportTaskMapper taskMapper;
    private final NcbiImportTaskItemMapper taskItemMapper;
    private final LiteratureRelationMapper relationMapper;

    @Autowired
    @Lazy
    private NcbiImportService self;

    public NcbiImportService(
            NcbiEutilsClient ncbiEutilsClient,
            UniProtClient uniProtClient,
            RcsbPdbClient rcsbPdbClient,
            EnzymeAnnotationMapper enzymeAnnotationMapper,
            EnzymeCrossRefMapper enzymeCrossRefMapper,
            EnzymeEntryMapper enzymeEntryMapper,
            EnzymeSequenceMapper enzymeSequenceMapper,
            EnzymeStructureMapper enzymeStructureMapper,
            NcbiImportTaskMapper taskMapper,
            NcbiImportTaskItemMapper taskItemMapper,
            LiteratureRelationMapper relationMapper) {
        this.ncbiEutilsClient = ncbiEutilsClient;
        this.uniProtClient = uniProtClient;
        this.rcsbPdbClient = rcsbPdbClient;
        this.enzymeAnnotationMapper = enzymeAnnotationMapper;
        this.enzymeCrossRefMapper = enzymeCrossRefMapper;
        this.enzymeEntryMapper = enzymeEntryMapper;
        this.enzymeSequenceMapper = enzymeSequenceMapper;
        this.enzymeStructureMapper = enzymeStructureMapper;
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.relationMapper = relationMapper;
    }

    public ImportTaskResponse importAccessions(String taskName,
                                               List<String> accessions,
                                               String moleculeType,
                                               String email,
                                               String apiKey) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedMoleculeType = normalizeMoleculeType(moleculeType);
        
        // 1. Create Task Record
        NcbiImportTask task = NcbiImportTask.builder()
                .taskName(taskName == null || taskName.isBlank() ? "batch_import_" + UUID.randomUUID().toString().substring(0, 8) : taskName.trim())
                .sourceType(MOLECULE_TYPE_RNA.equals(normalizedMoleculeType) ? "NCBI_RNA" : "NCBI_PROTEIN")
                .totalCount(accessions.size())
                .status("RUNNING")
                .createdAt(now)
                .build();
        taskMapper.insert(task);

        // 2. Run Asynchronously via self-proxy
        self.executeImportTask(task, accessions, normalizedMoleculeType, email, apiKey);

        return toTaskResponse(task, null);
    }

    @Async
    public void executeImportTask(NcbiImportTask task,
                                  List<String> accessions,
                                  String moleculeType,
                                  String email,
                                  String apiKey) {
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
                boolean rnaImport = MOLECULE_TYPE_RNA.equals(moleculeType);
                NcbiEutilsClient.LookupResult result = rnaImport
                        ? ncbiEutilsClient.fetchNucleotideByAccession(accession, email, apiKey)
                        : ncbiEutilsClient.fetchProteinByAccession(accession, email, apiKey);
                Optional<UniProtClient.ProteinEnrichment> enrichment = rnaImport
                        ? Optional.empty()
                        : loadUniProtEnrichment(result);
                
                // Create Enzyme Entry
                EnzymeEntry entry = EnzymeEntry.builder()
                        .code("ENZ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .proteinAccession(result.getAccession())
                        .proteinVersion(extractAccessionVersion(result.getAccession()))
                        .geneSymbol(enrichment.map(UniProtClient.ProteinEnrichment::getGeneSymbol).orElse(null))
                        .name(result.getTitle())
                        .ecNumber(enrichment.map(UniProtClient.ProteinEnrichment::getEcNumber).orElse(null))
                        .organism(result.getOrganism())
                        .taxId(result.getTaxId())
                        .description(rnaImport
                                ? "来自 NCBI Nucleotide 的核酶候选条目，当前保留原始序列与基础元数据。"
                                : enrichment.map(UniProtClient.ProteinEnrichment::getFunctionSummary).orElse(null))
                        .sourceType(SOURCE_TYPE_NCBI_IMPORT)
                        .status("ACTIVE")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                enzymeEntryMapper.insert(entry);
                savePrimarySequence(entry.getId(), result.getSequence(), result.getSequenceLength(), rnaImport ? "NCBI_NUCLEOTIDE" : "NCBI_PROTEIN");
                saveCrossReference(
                        entry.getId(),
                        "NCBI",
                        rnaImport ? "NUCLEOTIDE_ACCESSION" : "PROTEIN_ACCESSION",
                        result.getAccession(),
                        buildNcbiUrl(result.getAccession(), moleculeType),
                        1
                );
                if (!rnaImport) {
                    applyUniProtEnrichment(entry.getId(), enrichment);
                    seedInitialAnnotations(entry.getId(), entry.getProteinAccession(), entry.getTaxId(), enrichment);
                }

                successCount++;
                saveTaskItem(task.getId(), entry.getProteinAccession(), "SUCCESS", buildSuccessMessage(moleculeType, enrichment), entry.getId());
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

    public List<EnzymeEntryResponse> listEnzymes(String sourceType) {
        LambdaQueryWrapper<EnzymeEntry> query = new LambdaQueryWrapper<EnzymeEntry>()
                .orderByDesc(EnzymeEntry::getCreatedAt);
        if (sourceType != null && !sourceType.isBlank()) {
            query.eq(EnzymeEntry::getSourceType, sourceType.trim());
        }

        List<EnzymeEntry> entries = enzymeEntryMapper.selectList(query);
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
                            .code(entry.getCode())
                            .sourceType(entry.getSourceType())
                            .moleculeType(resolveMoleculeType(entry, ncbiRef, primarySequences.get(entry.getId())))
                            .accession(readDisplayAccession(entry))
                            .proteinName(entry.getName())
                            .organismName(entry.getOrganism())
                            .description(entry.getDescription())
                            .taxId(entry.getTaxId())
                            .sequenceLength(readSequenceLength(primarySequences.get(entry.getId())))
                            .sequenceHash(readSequenceHash(primarySequences.get(entry.getId())))
                            .structureType(readStructureType(primaryStructure))
                            .structureId(readStructureId(primaryStructure))
                            .structureSourceDb(readStructureSourceDb(primaryStructure))
                            .structureUrl(readStructureUrl(primaryStructure))
                            .ncbiAccession(readCrossRefValue(ncbiRef))
                            .ncbiUrl(readCrossRefUrl(ncbiRef))
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
    public EnzymeEntryResponse saveMiniFoldResult(SaveMiniFoldEnzymeRequest request) {
        validateMiniFoldSaveRequest(request);

        String taskId = defaultString(request.getTaskId()).trim();
        if (!taskId.isEmpty()) {
            EnzymeCrossRef existingTaskRef = enzymeCrossRefMapper.selectOne(new LambdaQueryWrapper<EnzymeCrossRef>()
                    .eq(EnzymeCrossRef::getRefDb, "MINIFOLD")
                    .eq(EnzymeCrossRef::getRefType, "TASK_ID")
                    .eq(EnzymeCrossRef::getRefValue, taskId)
                    .last("LIMIT 1"));
            if (existingTaskRef != null) {
                return getEnzymeResponse(existingTaskRef.getEnzymeId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        EnzymeEntry entry = EnzymeEntry.builder()
                .code("PRED_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .name(request.getName().trim())
                .organism("MiniFold 本地预测")
                .description(buildMiniFoldDescription(request))
                .sourceType(SOURCE_TYPE_MINIFOLD_PREDICTION)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        enzymeEntryMapper.insert(entry);

        String normalizedSequence = normalizeSequence(request.getSequence());
        savePrimarySequence(entry.getId(), normalizedSequence, normalizedSequence.length(), "MINIFOLD");
        writeMiniFoldStructureFile(entry.getCode(), request.getPdb());
        saveStructure(
                entry.getId(),
                "PREDICTED",
                taskId.isEmpty() ? entry.getCode() : taskId,
                "MiniFold",
                "/api/enzymes/" + entry.getId() + "/structure",
                1
        );
        saveCrossReference(entry.getId(), "MINIFOLD", "TASK_ID", taskId, null, 1);

        return getEnzymeResponse(entry.getId());
    }

    public String getStructureContent(Long enzymeId) {
        EnzymeEntry entry = enzymeEntryMapper.selectById(enzymeId);
        if (entry == null) {
            return null;
        }
        Path structurePath = getStoredStructurePath(entry);
        if (structurePath == null || !Files.exists(structurePath)) {
            return null;
        }
        try {
            return Files.readString(structurePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取结构文件失败: " + e.getMessage(), e);
        }
    }

    public String getPrimarySequenceText(Long enzymeId) {
        ensureEnzymeExists(enzymeId);
        EnzymeSequence sequence = enzymeSequenceMapper.selectOne(new LambdaQueryWrapper<EnzymeSequence>()
                .eq(EnzymeSequence::getEnzymeId, enzymeId)
                .eq(EnzymeSequence::getIsPrimary, 1)
                .orderByDesc(EnzymeSequence::getVersionNo)
                .last("LIMIT 1"));
        return sequence == null ? null : defaultString(sequence.getSequenceText());
    }

    public EnzymeEntryResponse getEnzymeResponse(Long enzymeId) {
        EnzymeEntry entry = enzymeEntryMapper.selectById(enzymeId);
        if (entry == null) {
            throw new IllegalArgumentException("未找到酶条目: " + enzymeId);
        }
        return listEnzymes(null)
                .stream()
                .filter(item -> enzymeId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到酶条目: " + enzymeId));
    }

    public List<EnzymeAnnotation> listAnnotations(Long enzymeId) {
        ensureEnzymeExists(enzymeId);
        return enzymeAnnotationMapper.selectList(new LambdaQueryWrapper<EnzymeAnnotation>()
                .eq(EnzymeAnnotation::getEnzymeId, enzymeId)
                .orderByAsc(EnzymeAnnotation::getStartResidue)
                .orderByAsc(EnzymeAnnotation::getEndResidue)
                .orderByAsc(EnzymeAnnotation::getCreatedAt));
    }

    @Transactional
    public EnzymeAnnotation createAnnotation(Long enzymeId, UpsertEnzymeAnnotationRequest request) {
        ensureEnzymeExists(enzymeId);
        EnzymeAnnotation annotation = buildAnnotationEntity(enzymeId, null, request);
        enzymeAnnotationMapper.insert(annotation);
        return enzymeAnnotationMapper.selectById(annotation.getId());
    }

    @Transactional
    public EnzymeAnnotation updateAnnotation(Long enzymeId, Long annotationId, UpsertEnzymeAnnotationRequest request) {
        ensureEnzymeExists(enzymeId);
        EnzymeAnnotation existing = enzymeAnnotationMapper.selectById(annotationId);
        if (existing == null || !enzymeId.equals(existing.getEnzymeId())) {
            throw new IllegalArgumentException("未找到对应的酶注释记录");
        }
        EnzymeAnnotation annotation = buildAnnotationEntity(enzymeId, existing, request);
        annotation.setId(annotationId);
        enzymeAnnotationMapper.updateById(annotation);
        return enzymeAnnotationMapper.selectById(annotationId);
    }

    @Transactional
    public void deleteAnnotation(Long enzymeId, Long annotationId) {
        ensureEnzymeExists(enzymeId);
        EnzymeAnnotation existing = enzymeAnnotationMapper.selectById(annotationId);
        if (existing == null || !enzymeId.equals(existing.getEnzymeId())) {
            throw new IllegalArgumentException("未找到对应的酶注释记录");
        }
        enzymeAnnotationMapper.deleteById(annotationId);
    }

    @Transactional
    public List<EnzymeAnnotation> importAnnotationsFromUniProt(Long enzymeId) {
        EnzymeEntry entry = ensureEnzymeExists(enzymeId);
        EnzymeEntryResponse response = getEnzymeResponse(enzymeId);
        if (MOLECULE_TYPE_RNA.equalsIgnoreCase(response.getMoleculeType())) {
            return importAnnotationsFromNcbiNucleotide(enzymeId, entry, response);
        }
        Optional<UniProtClient.ProteinEnrichment> enrichment = loadUniProtEnrichmentForAnnotations(entry, response);
        List<EnzymeAnnotation> existing = listAnnotations(enzymeId);
        List<EnzymeAnnotation> imported = new ArrayList<>();

        String accession = resolveUniProtAccession(enzymeId, entry, response, enrichment);
        if (accession != null) {
            imported.addAll(importUniProtFeatures(enzymeId, accession, existing));
        }

        String pdbId = resolvePdbId(response, enrichment);
        if (pdbId != null) {
            imported.addAll(importPdbFeatures(enzymeId, pdbId, existing));
        }

        if (accession == null && pdbId == null) {
            throw new IllegalArgumentException("当前酶条目缺少 UniProt accession 和 PDB 结构编号，暂时无法自动导入初始注释");
        }
        return imported;
    }

    @Transactional
    public List<EnzymeAnnotation> importAnnotationsAutomatically(Long enzymeId) {
        return importAnnotationsFromUniProt(enzymeId);
    }

    @Transactional
    public void deleteEnzyme(Long id) {
        EnzymeEntry entry = enzymeEntryMapper.selectById(id);

        enzymeAnnotationMapper.delete(new LambdaQueryWrapper<EnzymeAnnotation>()
                .eq(EnzymeAnnotation::getEnzymeId, id));

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

        deleteStoredStructure(entry);

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

    private EnzymeEntry ensureEnzymeExists(Long enzymeId) {
        EnzymeEntry entry = enzymeId == null ? null : enzymeEntryMapper.selectById(enzymeId);
        if (entry == null) {
            throw new IllegalArgumentException("未找到酶条目: " + enzymeId);
        }
        return entry;
    }

    private EnzymeAnnotation buildAnnotationEntity(Long enzymeId, EnzymeAnnotation existing, UpsertEnzymeAnnotationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("缺少注释数据");
        }
        String annotationType = normalizeAnnotationType(request.getAnnotationType());
        Integer startResidue = request.getStartResidue();
        Integer endResidue = request.getEndResidue() != null ? request.getEndResidue() : request.getStartResidue();
        if (startResidue == null || startResidue <= 0) {
            throw new IllegalArgumentException("起始残基位点必须为正整数");
        }
        if (endResidue == null || endResidue <= 0) {
            throw new IllegalArgumentException("结束残基位点必须为正整数");
        }
        if (endResidue < startResidue) {
            throw new IllegalArgumentException("结束残基位点不能小于起始残基位点");
        }
        if ("MUTATION".equals(annotationType)) {
            endResidue = startResidue;
        }

        LocalDateTime now = LocalDateTime.now();
        return EnzymeAnnotation.builder()
                .id(existing != null ? existing.getId() : null)
                .enzymeId(enzymeId)
                .annotationType(annotationType)
                .title(buildAnnotationTitle(annotationType, request.getTitle(), startResidue, endResidue))
                .startResidue(startResidue)
                .endResidue(endResidue)
                .chainLabel(trimToNull(request.getChainLabel()))
                .mutationLabel(normalizeMutationLabel(annotationType, request.getMutationLabel()))
                .colorHex(normalizeColorHex(request.getColorHex()))
                .description(trimToNull(request.getDescription()))
                .sourceDb(existing != null ? existing.getSourceDb() : null)
                .sourceRef(existing != null ? truncateToLength(existing.getSourceRef(), ANNOTATION_SOURCE_REF_MAX_LENGTH) : null)
                .createdAt(existing != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private EnzymeAnnotation buildImportedAnnotation(Long enzymeId,
                                                     String annotationType,
                                                     String title,
                                                     Integer startResidue,
                                                     Integer endResidue,
                                                     String chainLabel,
                                                     String mutationLabel,
                                                     String description,
                                                     String sourceDb,
                                                     String sourceRef) {
        LocalDateTime now = LocalDateTime.now();
        return EnzymeAnnotation.builder()
                .enzymeId(enzymeId)
                .annotationType(annotationType)
                .title(buildAnnotationTitle(annotationType, title, startResidue, endResidue))
                .startResidue(startResidue)
                .endResidue(endResidue)
                .chainLabel(trimToNull(chainLabel))
                .mutationLabel(normalizeMutationLabel(annotationType, mutationLabel))
                .colorHex(defaultAnnotationColor(annotationType))
                .description(trimToNull(description))
                .sourceDb(trimToNull(sourceDb))
                .sourceRef(truncateToLength(sourceRef, ANNOTATION_SOURCE_REF_MAX_LENGTH))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private boolean annotationAlreadyExists(List<EnzymeAnnotation> existing,
                                            String annotationType,
                                            Integer startResidue,
                                            Integer endResidue,
                                            String title,
                                            String sourceDb,
                                            String sourceRef) {
        final String normalizedSourceDb = trimToNull(sourceDb);
        final String normalizedSourceRef = trimToNull(sourceRef);
        return existing.stream().anyMatch(item -> {
            if (normalizedSourceDb != null && normalizedSourceRef != null
                    && normalizedSourceDb.equalsIgnoreCase(defaultString(item.getSourceDb()))
                    && normalizedSourceRef.equalsIgnoreCase(defaultString(item.getSourceRef()))) {
                return true;
            }
            return annotationType.equalsIgnoreCase(defaultString(item.getAnnotationType()))
                    && startResidue.equals(item.getStartResidue())
                    && endResidue.equals(item.getEndResidue())
                    && defaultString(title).equalsIgnoreCase(defaultString(item.getTitle()));
        });
    }

    private String normalizeAnnotationType(String annotationType) {
        String normalized = trimToNull(annotationType);
        if (normalized == null) {
            throw new IllegalArgumentException("请选择注释类型");
        }
        normalized = normalized.toUpperCase();
        if (!SUPPORTED_ANNOTATION_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的注释类型: " + annotationType);
        }
        return normalized;
    }

    private String buildAnnotationTitle(String annotationType, String title, Integer startResidue, Integer endResidue) {
        String normalizedTitle = trimToNull(title);
        if (normalizedTitle == null) {
            if ("ACTIVE_SITE".equals(annotationType)) {
                normalizedTitle = "活性位点 " + startResidue;
            } else if ("MUTATION".equals(annotationType)) {
                normalizedTitle = "突变位点 " + startResidue;
            } else {
                normalizedTitle = "结构域 " + startResidue + "-" + endResidue;
            }
        }
        return truncateToLength(normalizedTitle, ANNOTATION_TITLE_MAX_LENGTH);
    }

    private String normalizeMutationLabel(String annotationType, String mutationLabel) {
        if (!"MUTATION".equals(annotationType)) {
            return truncateToLength(mutationLabel, ANNOTATION_MUTATION_LABEL_MAX_LENGTH);
        }
        return truncateToLength(mutationLabel, ANNOTATION_MUTATION_LABEL_MAX_LENGTH);
    }

    private String truncateToLength(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 3).trim() + "...";
    }

    private String normalizeColorHex(String colorHex) {
        String normalized = trimToNull(colorHex);
        if (normalized == null) {
            return "#3B82F6";
        }
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (!normalized.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("颜色值格式不正确，请使用 #RRGGBB");
        }
        return normalized.toUpperCase();
    }

    private String defaultAnnotationColor(String annotationType) {
        if ("ACTIVE_SITE".equalsIgnoreCase(annotationType)) {
            return "#10B981";
        }
        if ("MUTATION".equalsIgnoreCase(annotationType)) {
            return "#F97316";
        }
        return "#3B82F6";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Optional<UniProtClient.ProteinEnrichment> loadUniProtEnrichment(NcbiEutilsClient.LookupResult result) {
        try {
            return uniProtClient.enrichByRefSeqAccession(result.getAccession(), result.getTaxId());
        } catch (Exception ex) {
            log.warn("UniProt enrichment failed for accession {}", result.getAccession(), ex);
            return Optional.empty();
        }
    }

    private Optional<UniProtClient.ProteinEnrichment> loadUniProtEnrichment(EnzymeEntry entry) {
        if (entry == null || trimToNull(entry.getProteinAccession()) == null) {
            return Optional.empty();
        }
        try {
            return uniProtClient.enrichByRefSeqAccession(entry.getProteinAccession(), entry.getTaxId());
        } catch (Exception ex) {
            log.warn("UniProt enrichment failed for enzyme {}", entry.getId(), ex);
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

    private void seedInitialAnnotations(Long enzymeId,
                                        String proteinAccession,
                                        String taxId,
                                        Optional<UniProtClient.ProteinEnrichment> enrichmentOptional) {
        try {
            String accession = enrichmentOptional
                    .map(UniProtClient.ProteinEnrichment::getPrimaryAccession)
                    .map(this::trimToNull)
                    .orElse(null);
            if (accession == null) {
                accession = uniProtClient.enrichByRefSeqAccession(proteinAccession, taxId)
                        .map(UniProtClient.ProteinEnrichment::getPrimaryAccession)
                        .map(this::trimToNull)
                        .orElse(null);
            }
            List<EnzymeAnnotation> existing = listAnnotations(enzymeId);
            if (accession != null) {
                importUniProtFeatures(enzymeId, accession, existing);
            }

            String pdbId = enrichmentOptional
                    .filter(item -> !item.getPdbIds().isEmpty())
                    .map(item -> trimToNull(item.getPdbIds().get(0)))
                    .orElse(null);
            if (pdbId != null) {
                importPdbFeatures(enzymeId, pdbId, existing);
            }
        } catch (Exception ex) {
            log.warn("Failed to seed initial annotations for enzyme {}", enzymeId, ex);
        }
    }

    private Optional<UniProtClient.ProteinEnrichment> loadUniProtEnrichmentForAnnotations(EnzymeEntry entry,
                                                                                           EnzymeEntryResponse response) {
        if (trimToNull(response.getUniprotAccession()) != null || trimToNull(response.getPdbId()) != null) {
            return Optional.empty();
        }
        return loadUniProtEnrichment(entry);
    }

    private String resolveUniProtAccession(Long enzymeId,
                                           EnzymeEntry entry,
                                           EnzymeEntryResponse response,
                                           Optional<UniProtClient.ProteinEnrichment> enrichment) {
        String accession = trimToNull(response.getUniprotAccession());
        if (accession != null) {
            return accession;
        }

        accession = enrichment
                .map(UniProtClient.ProteinEnrichment::getPrimaryAccession)
                .map(this::trimToNull)
                .orElse(null);
        if (accession != null) {
            saveCrossReference(
                    enzymeId,
                    ANNOTATION_SOURCE_UNIPROT,
                    "ACCESSION",
                    accession,
                    buildUniProtUrl(accession),
                    1
            );
            return accession;
        }

        return loadUniProtEnrichment(entry)
                .map(UniProtClient.ProteinEnrichment::getPrimaryAccession)
                .map(this::trimToNull)
                .orElse(null);
    }

    private String resolvePdbId(EnzymeEntryResponse response,
                                Optional<UniProtClient.ProteinEnrichment> enrichment) {
        String pdbId = trimToNull(response.getPdbId());
        if (pdbId != null) {
            return pdbId;
        }
        return enrichment
                .filter(item -> !item.getPdbIds().isEmpty())
                .map(item -> trimToNull(item.getPdbIds().get(0)))
                .orElse(null);
    }

    private List<EnzymeAnnotation> importUniProtFeatures(Long enzymeId,
                                                         String accession,
                                                         List<EnzymeAnnotation> existing) {
        List<UniProtClient.FeatureAnnotation> features = uniProtClient.fetchFeatureAnnotations(accession);
        if (features.isEmpty()) {
            return List.of();
        }

        List<EnzymeAnnotation> imported = new ArrayList<>();
        for (UniProtClient.FeatureAnnotation feature : features) {
            if (annotationAlreadyExists(
                    existing,
                    feature.getAnnotationType(),
                    feature.getStartResidue(),
                    feature.getEndResidue(),
                    feature.getTitle(),
                    feature.getSourceDb(),
                    feature.getSourceRef())) {
                continue;
            }
            EnzymeAnnotation annotation = buildImportedAnnotation(
                    enzymeId,
                    feature.getAnnotationType(),
                    feature.getTitle(),
                    feature.getStartResidue(),
                    feature.getEndResidue(),
                    null,
                    feature.getMutationLabel(),
                    feature.getDescription(),
                    feature.getSourceDb(),
                    feature.getSourceRef()
            );
            enzymeAnnotationMapper.insert(annotation);
            EnzymeAnnotation saved = enzymeAnnotationMapper.selectById(annotation.getId());
            existing.add(saved);
            imported.add(saved);
        }
        return imported;
    }

    private List<EnzymeAnnotation> importPdbFeatures(Long enzymeId,
                                                     String pdbId,
                                                     List<EnzymeAnnotation> existing) {
        List<RcsbPdbClient.PdbFeatureAnnotation> features = rcsbPdbClient.fetchFeatureAnnotations(pdbId);
        if (features.isEmpty()) {
            return List.of();
        }

        List<EnzymeAnnotation> imported = new ArrayList<>();
        for (RcsbPdbClient.PdbFeatureAnnotation feature : features) {
            if (annotationAlreadyExists(
                    existing,
                    feature.getAnnotationType(),
                    feature.getStartResidue(),
                    feature.getEndResidue(),
                    feature.getTitle(),
                    feature.getSourceDb(),
                    feature.getSourceRef())) {
                continue;
            }
            EnzymeAnnotation annotation = buildImportedAnnotation(
                    enzymeId,
                    feature.getAnnotationType(),
                    feature.getTitle(),
                    feature.getStartResidue(),
                    feature.getEndResidue(),
                    feature.getChainLabel(),
                    null,
                    feature.getDescription(),
                    feature.getSourceDb(),
                    feature.getSourceRef()
            );
            enzymeAnnotationMapper.insert(annotation);
            EnzymeAnnotation saved = enzymeAnnotationMapper.selectById(annotation.getId());
            existing.add(saved);
            imported.add(saved);
        }
        return imported;
    }

    private List<EnzymeAnnotation> importAnnotationsFromNcbiNucleotide(Long enzymeId,
                                                                       EnzymeEntry entry,
                                                                       EnzymeEntryResponse response) {
        String accession = trimToNull(response.getNcbiAccession());
        if (accession == null) {
            accession = trimToNull(entry.getProteinAccession());
        }
        if (accession == null) {
            throw new IllegalArgumentException("当前 RNA 条目缺少 NCBI Nucleotide accession，暂时无法自动导入注释");
        }

        List<EnzymeAnnotation> existing = listAnnotations(enzymeId);
        List<NcbiEutilsClient.NucleotideFeatureAnnotation> features =
                ncbiEutilsClient.fetchNucleotideFeatureAnnotations(accession, null, null);
        if (features.isEmpty()) {
            return List.of();
        }

        List<EnzymeAnnotation> imported = new ArrayList<>();
        for (NcbiEutilsClient.NucleotideFeatureAnnotation feature : features) {
            if (annotationAlreadyExists(
                    existing,
                    feature.getAnnotationType(),
                    feature.getStartResidue(),
                    feature.getEndResidue(),
                    feature.getTitle(),
                    feature.getSourceDb(),
                    feature.getSourceRef())) {
                continue;
            }
            EnzymeAnnotation annotation = buildImportedAnnotation(
                    enzymeId,
                    feature.getAnnotationType(),
                    feature.getTitle(),
                    feature.getStartResidue(),
                    feature.getEndResidue(),
                    null,
                    null,
                    feature.getDescription(),
                    feature.getSourceDb(),
                    feature.getSourceRef()
            );
            enzymeAnnotationMapper.insert(annotation);
            EnzymeAnnotation saved = enzymeAnnotationMapper.selectById(annotation.getId());
            existing.add(saved);
            imported.add(saved);
        }
        return imported;
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

    private String readDisplayAccession(EnzymeEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.getProteinAccession() != null && !entry.getProteinAccession().isBlank()) {
            return entry.getProteinAccession();
        }
        return entry.getCode();
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

    private String buildNcbiUrl(String accession, String moleculeType) {
        return accession == null || accession.isBlank()
                ? null
                : MOLECULE_TYPE_RNA.equals(normalizeMoleculeType(moleculeType))
                ? "https://www.ncbi.nlm.nih.gov/nuccore/" + accession
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

    private String extractAccessionVersion(String accession) {
        if (accession == null || accession.isBlank()) {
            return null;
        }
        int dot = accession.indexOf('.');
        return dot >= 0 && dot + 1 < accession.length() ? accession.substring(dot + 1) : null;
    }

    private String buildSuccessMessage(String moleculeType, Optional<UniProtClient.ProteinEnrichment> enrichment) {
        if (MOLECULE_TYPE_RNA.equals(normalizeMoleculeType(moleculeType))) {
            return "已从 NCBI Nucleotide 写入基础信息与 RNA 序列";
        }
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

    private String resolveMoleculeType(EnzymeEntry entry, EnzymeCrossRef ncbiRef, EnzymeSequence primarySequence) {
        if (ncbiRef != null) {
            if ("NUCLEOTIDE_ACCESSION".equalsIgnoreCase(ncbiRef.getRefType())) {
                return MOLECULE_TYPE_RNA;
            }
            if ("PROTEIN_ACCESSION".equalsIgnoreCase(ncbiRef.getRefType())) {
                return MOLECULE_TYPE_PROTEIN;
            }
        }
        if (primarySequence != null && "NCBI_NUCLEOTIDE".equalsIgnoreCase(primarySequence.getSourceType())) {
            return MOLECULE_TYPE_RNA;
        }
        return MOLECULE_TYPE_PROTEIN;
    }

    private String normalizeMoleculeType(String moleculeType) {
        return MOLECULE_TYPE_RNA.equalsIgnoreCase(trimToNull(moleculeType))
                ? MOLECULE_TYPE_RNA
                : MOLECULE_TYPE_PROTEIN;
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

    private String normalizeSequence(String sequence) {
        return defaultString(sequence).replaceAll("\\s+", "").toUpperCase();
    }

    private void validateMiniFoldSaveRequest(SaveMiniFoldEnzymeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("缺少 MiniFold 入库数据");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("请先为预测结果起一个名字");
        }
        String sequence = normalizeSequence(request.getSequence());
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("预测序列不能为空");
        }
        if (request.getPdb() == null || request.getPdb().trim().isEmpty()) {
            throw new IllegalArgumentException("MiniFold 结构内容为空，无法入库");
        }
    }

    private String buildMiniFoldDescription(SaveMiniFoldEnzymeRequest request) {
        List<String> summary = new java.util.ArrayList<>();
        String taskId = defaultString(request.getTaskId()).trim();
        if (!taskId.isEmpty()) {
            summary.add("MiniFold Task: " + taskId);
        }
        if (request.getTargetChains() != null) {
            summary.add("Target Chains: " + request.getTargetChains());
        }
        String backend = defaultString(request.getBackend()).trim();
        if (!backend.isEmpty()) {
            summary.add("Backend: " + backend);
        }
        if (request.getUseAcceleration() != null) {
            summary.add("Acceleration: " + (request.getUseAcceleration() ? "enabled" : "disabled"));
        }
        String envText = defaultString(request.getEnvText()).trim();
        if (!envText.isEmpty()) {
            summary.add("Environment Notes: " + envText);
        }
        return summary.isEmpty() ? "MiniFold 本地预测结果，已由用户确认入库。" : String.join("\n", summary);
    }

    private void writeMiniFoldStructureFile(String entryCode, String pdb) {
        try {
            Path structurePath = getStoredStructurePath(entryCode);
            Files.createDirectories(structurePath.getParent());
            Files.writeString(structurePath, defaultString(pdb), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写入 MiniFold 结构文件失败: " + e.getMessage(), e);
        }
    }

    private Path getStoredStructurePath(EnzymeEntry entry) {
        if (entry == null || entry.getCode() == null || entry.getCode().isBlank()) {
            return null;
        }
        return getStoredStructurePath(entry.getCode());
    }

    private Path getStoredStructurePath(String entryCode) {
        return getProjectRoot()
                .resolve("api_output")
                .resolve("library")
                .resolve(entryCode)
                .resolve("structure.pdb");
    }

    private void deleteStoredStructure(EnzymeEntry entry) {
        Path structurePath = getStoredStructurePath(entry);
        if (structurePath == null || !Files.exists(structurePath)) {
            return;
        }
        Path libraryDir = structurePath.getParent();
        try (Stream<Path> stream = Files.walk(libraryDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    log.warn("Failed to delete path {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean stored structure for enzyme {}", entry != null ? entry.getId() : null, e);
        }
    }

    private Path getProjectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
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
