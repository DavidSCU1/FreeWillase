package com.freewillase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freewillase.backend.domain.EnzymeEntry;
import com.freewillase.backend.domain.LiteratureRecord;
import com.freewillase.backend.domain.LiteratureRelation;
import com.freewillase.backend.mapper.EnzymeEntryMapper;
import com.freewillase.backend.mapper.LiteratureRecordMapper;
import com.freewillase.backend.mapper.LiteratureRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureMatchService {

    private final LiteratureRecordMapper literatureMapper;
    private final LiteratureRelationMapper relationMapper;
    private final EnzymeEntryMapper enzymeMapper;
    private final NcbiEutilsClient ncbiClient;
    @org.springframework.beans.factory.annotation.Value("${app.storage.literature-dir:storage/literature}")
    private String literatureStorageDir;
    
    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private LiteratureMatchService self;

    private static final String ATTACHMENT_STATUS_NONE = "NONE";
    private static final String ATTACHMENT_STATUS_DOWNLOADED = "DOWNLOADED";
    private static final String ATTACHMENT_STATUS_NOT_OPEN_ACCESS = "NOT_OPEN_ACCESS";
    private static final String ATTACHMENT_STATUS_FAILED = "FAILED";
    private static final String SOURCE_DB_LOCAL_UPLOAD = "LOCAL_UPLOAD";
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");

    @Transactional
    public void matchLiteratureForEnzyme(Long enzymeId, String email, String apiKey) {
        EnzymeEntry enzyme = enzymeMapper.selectById(enzymeId);
        if (enzyme == null) return;

        log.info("Processing enzyme {}: {} ({})", enzymeId, enzyme.getProteinAccession(), enzyme.getName());

        // 1. Clear old candidate relations for this enzyme while keeping downloaded records.
        relationMapper.delete(new LambdaQueryWrapper<LiteratureRelation>()
                .eq(LiteratureRelation::getEnzymeId, enzymeId)
                .eq(LiteratureRelation::getSavedToLibrary, false));
        
        String accession = enzyme.getProteinAccession();
        String name = enzyme.getName();
        String organism = enzyme.getOrganism();
        
        if (accession == null || name == null) return;

        // 2. Multi-stage search strategy
        java.util.Map<String, NcbiEutilsClient.PubMedResult> resultsMap = new java.util.LinkedHashMap<>();
        java.util.Set<String> accessionHits = new java.util.HashSet<>();
        
        // Strategy A: Accession + Genus (High Precision)
        String genus = (organism != null && organism.contains(" ")) ? organism.split(" ")[0] : "";
        String accQuery = accession + (genus.isEmpty() ? "" : " " + genus);
        List<NcbiEutilsClient.PubMedResult> accResults = ncbiClient.searchPubMed(accQuery, 5, email, apiKey);
        for (NcbiEutilsClient.PubMedResult r : accResults) {
            resultsMap.put(r.getPmid(), r);
            accessionHits.add(r.getPmid());
        }
        
        // Strategy B: Cleaned Name + Genus (High Recall)
        if (resultsMap.size() < 10 && name != null && !genus.isEmpty()) {
            String cleanName = name.replaceAll("\\[.*?\\]", "").replaceAll("\\(.*?\\)", "").trim();
            String nameQuery = String.format("\"%s\" AND %s", cleanName, genus);
            ncbiClient.searchPubMed(nameQuery, 10, email, apiKey)
                    .forEach(r -> resultsMap.putIfAbsent(r.getPmid(), r));
        }

        // 3. Process collected results
        resultsMap.values().forEach(r -> {
            // If it came from accession search, give it a significant head start
            int baseScore = accessionHits.contains(r.getPmid()) ? 70 : 0;
            processPubMedResult(r, enzyme, baseScore);
        });
    }

    @org.springframework.scheduling.annotation.Async
    public void matchLiteratureForAll(String email, String apiKey) {
        matchLiteratureForEnzymes(null, email, apiKey);
    }

    @org.springframework.scheduling.annotation.Async
    public void matchLiteratureForEnzymes(List<Long> enzymeIds, String email, String apiKey) {
        List<EnzymeEntry> enzymes = loadTargetEnzymes(enzymeIds);
        log.info("Starting bulk literature match for {} enzymes. Email: {}, API Key: {}", 
            enzymes.size(), email, (apiKey != null ? "***" : "none"));
        
        int count = 0;
        for (EnzymeEntry enzyme : enzymes) {
            try {
                count++;
                log.info("[{}/{}] Matching literature for: {}", count, enzymes.size(), enzyme.getProteinAccession());
                self.matchLiteratureForEnzyme(enzyme.getId(), email, apiKey);
                
                // Rate limiting
                Thread.sleep(apiKey != null && !apiKey.isBlank() ? 150 : 400);
            } catch (Exception e) {
                log.error("Failed at enzyme {}: {}", enzyme.getProteinAccession(), e.getMessage());
            }
        }
        log.info("Bulk literature match completed. Processed {} enzymes.", count);
    }

    private void processPubMedResult(NcbiEutilsClient.PubMedResult pubMed, EnzymeEntry enzyme, int baseScore) {
        int score = calculateScore(pubMed, enzyme, baseScore);
        if (score < 40) return; // Higher threshold for automatic matching

        // Save literature record
        LiteratureRecord lit = LiteratureRecord.builder()
                .title(pubMed.getTitle())
                .authors(pubMed.getAuthors())
                .journal(pubMed.getJournal())
                .publishYear(pubMed.getPublishYear())
                .pmid(pubMed.getPmid())
                .doi(pubMed.getDoi())
                .abstractText("PubMed metadata matching...")
                .sourceDb("PubMed")
                .sourceUrl(buildPubMedUrl(pubMed.getPmid()))
                .attachmentStatus(ATTACHMENT_STATUS_NONE)
                .createdAt(LocalDateTime.now())
                .build();

        LiteratureRecord existing = literatureMapper.selectOne(new LambdaQueryWrapper<LiteratureRecord>()
                .eq(LiteratureRecord::getPmid, lit.getPmid()));
        
        if (existing == null) {
            literatureMapper.insert(lit);
        } else {
            lit = existing;
            boolean shouldUpdate = false;
            if ((lit.getSourceUrl() == null || lit.getSourceUrl().isBlank()) && pubMed.getPmid() != null) {
                lit.setSourceUrl(buildPubMedUrl(pubMed.getPmid()));
                shouldUpdate = true;
            }
            if ((lit.getDoi() == null || lit.getDoi().isBlank()) && pubMed.getDoi() != null && !pubMed.getDoi().isBlank()) {
                lit.setDoi(pubMed.getDoi());
                shouldUpdate = true;
            }
            if (shouldUpdate) {
                literatureMapper.updateById(lit);
            }
        }

        // Create relation if not exists
        String confidence = score >= 85 ? "STRONG" : (score >= 60 ? "WEAK" : "CANDIDATE");
        String relationType = score >= 85 ? "DIRECT_EVIDENCE" : "ASSOCIATED";
        
        // Build matched fields string
        List<String> matches = new ArrayList<>();
        String titleLower = pubMed.getTitle().toLowerCase();
        if (titleLower.contains(enzyme.getProteinAccession().toLowerCase())) matches.add("Accession");
        if (enzyme.getName() != null && titleLower.contains(enzyme.getName().toLowerCase())) matches.add("Name");
        if (enzyme.getOrganism() != null && titleLower.contains(enzyme.getOrganism().toLowerCase())) matches.add("Organism");
        if (enzyme.getEcNumber() != null && titleLower.contains(enzyme.getEcNumber().toLowerCase())) matches.add("EC");

        LiteratureRelation existingRelation = relationMapper.selectOne(new LambdaQueryWrapper<LiteratureRelation>()
                .eq(LiteratureRelation::getLiteratureId, lit.getId())
                .eq(LiteratureRelation::getEnzymeId, enzyme.getId()));

        if (existingRelation == null) {
            LiteratureRelation relation = LiteratureRelation.builder()
                    .literatureId(lit.getId())
                    .enzymeId(enzyme.getId())
                    .relationType(relationType)
                    .confidenceLevel(confidence)
                    .confidenceScore(new BigDecimal(score))
                    .matchedFields(String.join(", ", matches))
                    .createdAt(LocalDateTime.now())
                    .savedToLibrary(false)
                    .build();
            relationMapper.insert(relation);
        } else {
            existingRelation.setRelationType(relationType);
            existingRelation.setConfidenceLevel(confidence);
            existingRelation.setMatchedFields(String.join(", ", matches));
            if (new BigDecimal(score).compareTo(existingRelation.getConfidenceScore()) > 0) {
                existingRelation.setConfidenceScore(new BigDecimal(score));
            }
            relationMapper.updateById(existingRelation);
        }
    }

    private int calculateScore(NcbiEutilsClient.PubMedResult pubMed, EnzymeEntry enzyme, int baseScore) {
        int score = baseScore;
        String title = pubMed.getTitle().toLowerCase();
        String proteinName = (enzyme.getName() != null) ? enzyme.getName().toLowerCase() : "";
        String organism = (enzyme.getOrganism() != null) ? enzyme.getOrganism().toLowerCase() : "";
        String accession = (enzyme.getProteinAccession() != null) ? enzyme.getProteinAccession().toLowerCase() : "";
        String ec = (enzyme.getEcNumber() != null) ? enzyme.getEcNumber().toLowerCase() : "";

        // Log for debugging
        log.debug("Scoring PMID {} against Accession {}: Title={}", pubMed.getPmid(), accession, title);

        // 1. Accession match (Very strong)
        if (!accession.isEmpty() && title.contains(accession)) {
            score += 60;
        }

        // 2. Exact Name match (Strong)
        if (!proteinName.isEmpty() && title.contains(proteinName)) {
            score += 40;
        }

        // 3. Organism match (Supporting)
        if (!organism.isEmpty() && title.contains(organism)) {
            score += 20;
        }

        // 4. EC Number match (Strong)
        if (!ec.isEmpty() && title.contains(ec)) {
            score += 30;
        }

        // 5. Penalties for very short titles or generic terms
        if (title.length() < 30) score -= 10;
        
        int finalScore = Math.max(0, Math.min(score, 100));
        log.debug("Final Score for PMID {}: {}", pubMed.getPmid(), finalScore);
        return finalScore;
    }

    public List<LiteratureRecord> getLiteratureForEnzyme(Long enzymeId) {
        List<LiteratureRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<LiteratureRelation>()
                .eq(LiteratureRelation::getEnzymeId, enzymeId)
                .eq(LiteratureRelation::getSavedToLibrary, true)
                .orderByDesc(LiteratureRelation::getConfidenceScore));

        if (relations.isEmpty()) return List.of();

        return buildDisplayRecords(relations);
    }

    @Transactional
    public LiteratureRecord downloadLiterature(Long relationId) {
        LiteratureRelation relation = relationMapper.selectById(relationId);
        if (relation == null) {
            throw new IllegalArgumentException("未找到对应的文献匹配记录");
        }
        if (!Boolean.TRUE.equals(relation.getSavedToLibrary())) {
            relation.setSavedToLibrary(true);
            relationMapper.updateById(relation);
        }

        LiteratureRecord record = literatureMapper.selectById(relation.getLiteratureId());
        if (record == null) {
            throw new IllegalArgumentException("未找到对应的文献记录");
        }

        enrichWithFullTextAttachment(record);
        literatureMapper.updateById(record);
        return buildDisplayRecord(relation, record);
    }

    public List<LiteratureRecord> listAll() {
        List<LiteratureRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<LiteratureRelation>()
                .orderByDesc(LiteratureRelation::getSavedToLibrary)
                .orderByDesc(LiteratureRelation::getConfidenceScore));
        return buildDisplayRecords(relations);
    }

    public LiteratureRecord getLiteratureById(Long literatureId) {
        return literatureMapper.selectById(literatureId);
    }

    @Transactional
    public LiteratureRecord importLiteratureFromLocalFile(Long enzymeId, String filePath) {
        EnzymeEntry enzyme = enzymeMapper.selectById(enzymeId);
        if (enzyme == null) {
            throw new IllegalArgumentException("未找到对应的酶条目");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("请输入有效的本地文件路径");
        }

        Path sourcePath = Paths.get(filePath.trim()).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("指定文件不存在或不是有效文件");
        }

        String normalizedSource = sourcePath.toString();
        LiteratureRecord existingRecord = literatureMapper.selectOne(new LambdaQueryWrapper<LiteratureRecord>()
                .eq(LiteratureRecord::getSourceDb, SOURCE_DB_LOCAL_UPLOAD)
                .eq(LiteratureRecord::getAttachmentSourceUrl, normalizedSource));

        LiteratureRecord record;
        if (existingRecord == null) {
            record = createLocalUploadRecord(sourcePath);
            literatureMapper.insert(record);
        } else {
            record = existingRecord;
            if (!attachmentExists(record.getAttachmentPath())) {
                copyLocalAttachment(sourcePath, record);
                literatureMapper.updateById(record);
            }
        }

        LiteratureRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<LiteratureRelation>()
                .eq(LiteratureRelation::getLiteratureId, record.getId())
                .eq(LiteratureRelation::getEnzymeId, enzymeId));
        if (relation == null) {
            relation = LiteratureRelation.builder()
                    .literatureId(record.getId())
                    .enzymeId(enzymeId)
                    .relationType("MANUAL_UPLOAD")
                    .confidenceLevel("MANUAL")
                    .confidenceScore(BigDecimal.valueOf(100))
                    .matchedFields("Local file import")
                    .note("用户手动导入本地文献附件")
                    .savedToLibrary(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            relationMapper.insert(relation);
        } else if (!Boolean.TRUE.equals(relation.getSavedToLibrary())) {
            relation.setSavedToLibrary(true);
            relationMapper.updateById(relation);
        }

        return buildDisplayRecord(relation, record);
    }

    @Transactional
    public LiteratureRecord importLiteratureFromUpload(Long enzymeId, MultipartFile file) {
        EnzymeEntry enzyme = enzymeMapper.selectById(enzymeId);
        if (enzyme == null) {
            throw new IllegalArgumentException("未找到对应的酶条目");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的本地文件");
        }

        LiteratureRecord record = createUploadedRecord(file);
        literatureMapper.insert(record);

        LiteratureRelation relation = LiteratureRelation.builder()
                .literatureId(record.getId())
                .enzymeId(enzymeId)
                .relationType("MANUAL_UPLOAD")
                .confidenceLevel("MANUAL")
                .confidenceScore(BigDecimal.valueOf(100))
                .matchedFields("Browser file upload")
                .note("用户通过文件选择器上传本地文献附件")
                .savedToLibrary(true)
                .createdAt(LocalDateTime.now())
                .build();
        relationMapper.insert(relation);
        return buildDisplayRecord(relation, record);
    }

    private List<EnzymeEntry> loadTargetEnzymes(List<Long> enzymeIds) {
        if (enzymeIds == null || enzymeIds.isEmpty()) {
            return enzymeMapper.selectList(null);
        }
        List<EnzymeEntry> enzymes = enzymeMapper.selectBatchIds(enzymeIds);
        return enzymes != null ? enzymes : Collections.emptyList();
    }

    private List<LiteratureRecord> buildDisplayRecords(List<LiteratureRelation> relations) {
        List<LiteratureRecord> result = new ArrayList<>();
        for (LiteratureRelation rel : relations) {
            LiteratureRecord record = literatureMapper.selectById(rel.getLiteratureId());
            if (record == null) {
                continue;
            }

            LiteratureRecord displayRecord = LiteratureRecord.builder()
                    .id(record.getId())
                    .title(record.getTitle())
                    .authors(record.getAuthors())
                    .journal(record.getJournal())
                    .publishYear(record.getPublishYear())
                    .doi(record.getDoi())
                    .pmid(record.getPmid())
                    .keywords(record.getKeywords())
                    .abstractText(record.getAbstractText())
                    .sourceDb(record.getSourceDb())
                    .sourceUrl(record.getSourceUrl())
                    .attachmentStatus(record.getAttachmentStatus())
                    .attachmentFileName(record.getAttachmentFileName())
                    .attachmentPath(record.getAttachmentPath())
                    .attachmentContentType(record.getAttachmentContentType())
                    .attachmentSize(record.getAttachmentSize())
                    .attachmentSourceUrl(record.getAttachmentSourceUrl())
                    .createdAt(record.getCreatedAt())
                    .confidenceScore(rel.getConfidenceScore())
                    .confidenceLevel(rel.getConfidenceLevel())
                    .relationId(rel.getId())
                    .enzymeId(rel.getEnzymeId())
                    .matchedFields(rel.getMatchedFields())
                    .savedToLibrary(Boolean.TRUE.equals(rel.getSavedToLibrary()))
                    .build();

            EnzymeEntry enzyme = enzymeMapper.selectById(rel.getEnzymeId());
            if (enzyme != null) {
                displayRecord.setMatchedEnzymeName(enzyme.getName());
                displayRecord.setMatchedEnzymeAccession(enzyme.getProteinAccession());
            }
            result.add(displayRecord);
        }
        return result;
    }

    private LiteratureRecord buildDisplayRecord(LiteratureRelation relation, LiteratureRecord record) {
        LiteratureRecord displayRecord = LiteratureRecord.builder()
                .id(record.getId())
                .title(record.getTitle())
                .authors(record.getAuthors())
                .journal(record.getJournal())
                .publishYear(record.getPublishYear())
                .doi(record.getDoi())
                .pmid(record.getPmid())
                .keywords(record.getKeywords())
                .abstractText(record.getAbstractText())
                .sourceDb(record.getSourceDb())
                .sourceUrl(record.getSourceUrl())
                .attachmentStatus(record.getAttachmentStatus())
                .attachmentFileName(record.getAttachmentFileName())
                .attachmentPath(record.getAttachmentPath())
                .attachmentContentType(record.getAttachmentContentType())
                .attachmentSize(record.getAttachmentSize())
                .attachmentSourceUrl(record.getAttachmentSourceUrl())
                .createdAt(record.getCreatedAt())
                .confidenceScore(relation.getConfidenceScore())
                .confidenceLevel(relation.getConfidenceLevel())
                .relationId(relation.getId())
                .enzymeId(relation.getEnzymeId())
                .matchedFields(relation.getMatchedFields())
                .savedToLibrary(Boolean.TRUE.equals(relation.getSavedToLibrary()))
                .build();

        EnzymeEntry enzyme = enzymeMapper.selectById(relation.getEnzymeId());
        if (enzyme != null) {
            displayRecord.setMatchedEnzymeName(enzyme.getName());
            displayRecord.setMatchedEnzymeAccession(enzyme.getProteinAccession());
        }
        return displayRecord;
    }

    private void enrichWithFullTextAttachment(LiteratureRecord record) {
        if (ATTACHMENT_STATUS_DOWNLOADED.equals(record.getAttachmentStatus()) && attachmentExists(record.getAttachmentPath())) {
            return;
        }
        if (record.getPmid() == null || record.getPmid().isBlank()) {
            record.setAttachmentStatus(ATTACHMENT_STATUS_FAILED);
            return;
        }

        try {
            NcbiEutilsClient.PmcFullTextResult fullTextResult = ncbiClient.fetchPmcFullTextByPmid(record.getPmid(), null, null);
            if (fullTextResult == null || fullTextResult.getXmlContent() == null || fullTextResult.getXmlContent().isBlank()) {
                record.setAttachmentStatus(ATTACHMENT_STATUS_NOT_OPEN_ACCESS);
                record.setAttachmentFileName(null);
                record.setAttachmentPath(null);
                record.setAttachmentContentType(null);
                record.setAttachmentSize(null);
                record.setAttachmentSourceUrl(null);
                return;
            }

            Path storageDir = Paths.get(literatureStorageDir).toAbsolutePath().normalize();
            Files.createDirectories(storageDir);

            String baseName = buildAttachmentBaseName(record, fullTextResult.getPmcId());
            Path attachmentPath = storageDir.resolve(baseName + ".xml");
            byte[] content = fullTextResult.getXmlContent().getBytes(StandardCharsets.UTF_8);
            Files.write(attachmentPath, content);

            record.setAttachmentStatus(ATTACHMENT_STATUS_DOWNLOADED);
            record.setAttachmentFileName(attachmentPath.getFileName().toString());
            record.setAttachmentPath(attachmentPath.toString());
            record.setAttachmentContentType("application/xml");
            record.setAttachmentSize((long) content.length);
            record.setAttachmentSourceUrl(fullTextResult.getSourceUrl());

            if (record.getAbstractText() == null
                    || record.getAbstractText().isBlank()
                    || "PubMed metadata matching...".equals(record.getAbstractText())) {
                String abstractText = extractAbstractText(fullTextResult.getXmlContent());
                if (abstractText != null && !abstractText.isBlank()) {
                    record.setAbstractText(abstractText);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to download full text attachment for PMID {}: {}", record.getPmid(), e.getMessage());
            record.setAttachmentStatus(ATTACHMENT_STATUS_FAILED);
        }
    }

    private boolean attachmentExists(String attachmentPath) {
        if (attachmentPath == null || attachmentPath.isBlank()) {
            return false;
        }
        return Files.exists(Paths.get(attachmentPath));
    }

    private LiteratureRecord createLocalUploadRecord(Path sourcePath) {
        String fileName = sourcePath.getFileName().toString();
        String baseName = stripExtension(fileName);
        LiteratureRecord record = LiteratureRecord.builder()
                .title(baseName)
                .authors("用户手动导入")
                .journal("本地文件")
                .publishYear(LocalDateTime.now().getYear())
                .pmid("LOCAL-" + System.currentTimeMillis())
                .abstractText("用户从酶库中心手动导入的本地文献附件")
                .sourceDb(SOURCE_DB_LOCAL_UPLOAD)
                .sourceUrl(null)
                .attachmentStatus(ATTACHMENT_STATUS_DOWNLOADED)
                .attachmentSourceUrl(sourcePath.toString())
                .createdAt(LocalDateTime.now())
                .build();
        copyLocalAttachment(sourcePath, record);
        return record;
    }

    private LiteratureRecord createUploadedRecord(MultipartFile file) {
        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded-literature";
        String baseName = stripExtension(originalFileName);
        LiteratureRecord record = LiteratureRecord.builder()
                .title(baseName)
                .authors("用户手动导入")
                .journal("本地上传文件")
                .publishYear(LocalDateTime.now().getYear())
                .pmid("UPLOAD-" + System.currentTimeMillis())
                .abstractText("用户通过文件选择器上传的本地文献附件")
                .sourceDb(SOURCE_DB_LOCAL_UPLOAD)
                .sourceUrl(null)
                .attachmentStatus(ATTACHMENT_STATUS_DOWNLOADED)
                .createdAt(LocalDateTime.now())
                .build();
        copyUploadedAttachment(file, record);
        return record;
    }

    private void copyLocalAttachment(Path sourcePath, LiteratureRecord record) {
        try {
            Path storageDir = Paths.get(literatureStorageDir, "manual").toAbsolutePath().normalize();
            Files.createDirectories(storageDir);
            String extension = readExtension(sourcePath.getFileName().toString());
            String safeBaseName = buildAttachmentBaseName(record, "LOCAL_" + System.currentTimeMillis());
            Path targetPath = storageDir.resolve(safeBaseName + extension);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            String contentType = Files.probeContentType(targetPath);
            record.setAttachmentStatus(ATTACHMENT_STATUS_DOWNLOADED);
            record.setAttachmentFileName(targetPath.getFileName().toString());
            record.setAttachmentPath(targetPath.toString());
            record.setAttachmentContentType(contentType != null ? contentType : "application/octet-stream");
            record.setAttachmentSize(Files.size(targetPath));
            record.setAttachmentSourceUrl(sourcePath.toString());
        } catch (Exception e) {
            throw new IllegalStateException("复制本地文献附件失败: " + e.getMessage(), e);
        }
    }

    private void copyUploadedAttachment(MultipartFile file, LiteratureRecord record) {
        try {
            Path storageDir = Paths.get(literatureStorageDir, "manual").toAbsolutePath().normalize();
            Files.createDirectories(storageDir);
            String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded-literature";
            String extension = readExtension(originalFileName);
            String safeBaseName = buildAttachmentBaseName(record, "UPLOAD_" + System.currentTimeMillis());
            Path targetPath = storageDir.resolve(safeBaseName + extension);
            file.transferTo(targetPath);

            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(targetPath);
            }

            record.setAttachmentStatus(ATTACHMENT_STATUS_DOWNLOADED);
            record.setAttachmentFileName(originalFileName);
            record.setAttachmentPath(targetPath.toString());
            record.setAttachmentContentType(contentType != null ? contentType : "application/octet-stream");
            record.setAttachmentSize(Files.size(targetPath));
            record.setAttachmentSourceUrl("upload:" + originalFileName);
        } catch (Exception e) {
            throw new IllegalStateException("保存上传文献附件失败: " + e.getMessage(), e);
        }
    }

    private String buildAttachmentBaseName(LiteratureRecord record, String pmcId) {
        String preferredId = (pmcId != null && !pmcId.isBlank()) ? pmcId : "PMID_" + record.getPmid();
        return preferredId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    private String readExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return fileName.substring(index);
    }

    private String extractAbstractText(String xmlContent) {
        java.util.regex.Matcher matcher = Pattern.compile("(?is)<abstract[^>]*>(.*?)</abstract>").matcher(xmlContent);
        if (!matcher.find()) {
            return null;
        }
        String abstractBlock = matcher.group(1);
        String plainText = XML_TAG_PATTERN.matcher(abstractBlock).replaceAll(" ");
        return plainText.replaceAll("\\s+", " ").trim();
    }

    private String buildPubMedUrl(String pmid) {
        if (pmid == null || pmid.isBlank()) {
            return null;
        }
        return "https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/";
    }
}
