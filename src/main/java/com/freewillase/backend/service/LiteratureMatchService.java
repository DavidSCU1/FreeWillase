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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureMatchService {

    private final LiteratureRecordMapper literatureMapper;
    private final LiteratureRelationMapper relationMapper;
    private final EnzymeEntryMapper enzymeMapper;
    private final NcbiEutilsClient ncbiClient;
    
    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private LiteratureMatchService self;

    @Transactional
    public void matchLiteratureForEnzyme(Long enzymeId, String email, String apiKey) {
        EnzymeEntry enzyme = enzymeMapper.selectById(enzymeId);
        if (enzyme == null) return;

        log.info("Processing enzyme {}: {} ({})", enzymeId, enzyme.getProteinAccession(), enzyme.getName());

        // 1. Clear old relations for this enzyme
        relationMapper.delete(new LambdaQueryWrapper<LiteratureRelation>().eq(LiteratureRelation::getEnzymeId, enzymeId));
        
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
        List<EnzymeEntry> enzymes = enzymeMapper.selectList(null);
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
                .createdAt(LocalDateTime.now())
                .build();

        LiteratureRecord existing = literatureMapper.selectOne(new LambdaQueryWrapper<LiteratureRecord>()
                .eq(LiteratureRecord::getPmid, lit.getPmid()));
        
        if (existing == null) {
            literatureMapper.insert(lit);
        } else {
            lit = existing;
            // Update if needed (optional)
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
                    .build();
            relationMapper.insert(relation);
        } else {
            // Update existing relation score if new one is higher
            if (new BigDecimal(score).compareTo(existingRelation.getConfidenceScore()) > 0) {
                existingRelation.setConfidenceScore(new BigDecimal(score));
                existingRelation.setConfidenceLevel(confidence);
                existingRelation.setMatchedFields(String.join(", ", matches));
                relationMapper.updateById(existingRelation);
            }
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
                .eq(LiteratureRelation::getEnzymeId, enzymeId));
        
        List<Long> litIds = relations.stream().map(LiteratureRelation::getLiteratureId).collect(java.util.stream.Collectors.toList());
        
        if (litIds.isEmpty()) return List.of();
        
        return literatureMapper.selectBatchIds(litIds);
    }

    public List<LiteratureRecord> listAll() {
        // Return a flattened list of "Matching Evidences" (one per relation)
        List<LiteratureRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<LiteratureRelation>()
                .orderByDesc(LiteratureRelation::getConfidenceScore));
        
        List<LiteratureRecord> result = new ArrayList<>();
        for (LiteratureRelation rel : relations) {
            LiteratureRecord record = literatureMapper.selectById(rel.getLiteratureId());
            if (record != null) {
                // Clone or create a copy to avoid shared state if the same record matches multiple enzymes
                LiteratureRecord displayRecord = LiteratureRecord.builder()
                        .id(record.getId())
                        .title(record.getTitle())
                        .authors(record.getAuthors())
                        .journal(record.getJournal())
                        .publishYear(record.getPublishYear())
                        .doi(record.getDoi())
                        .pmid(record.getPmid())
                        .abstractText(record.getAbstractText())
                        .sourceDb(record.getSourceDb())
                        .createdAt(record.getCreatedAt())
                        .confidenceScore(rel.getConfidenceScore())
                        .confidenceLevel(rel.getConfidenceLevel())
                        .matchedFields(rel.getMatchedFields())
                        .build();

                EnzymeEntry enzyme = enzymeMapper.selectById(rel.getEnzymeId());
                if (enzyme != null) {
                    displayRecord.setMatchedEnzymeName(enzyme.getName());
                    displayRecord.setMatchedEnzymeAccession(enzyme.getProteinAccession());
                }
                result.add(displayRecord);
            }
        }
        return result;
    }
}
