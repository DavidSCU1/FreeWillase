package com.freewillase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class UniProtClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public UniProtClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${app.uniprot.base-url:https://rest.uniprot.org}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public Optional<ProteinEnrichment> enrichByRefSeqAccession(String accession, String taxId) {
        String normalizedAccession = normalize(accession);
        if (normalizedAccession.isBlank()) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/uniprotkb/search")
                .queryParam("query", normalizedAccession)
                .queryParam("format", "json")
                .queryParam("size", 5)
                .build(true)
                .toUri();

        try {
            String body = restTemplate.getForObject(uri, String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            JsonNode results = objectMapper.readTree(body).path("results");
            if (!results.isArray()) {
                return Optional.empty();
            }

            ProteinEnrichment best = null;
            int bestScore = Integer.MIN_VALUE;
            for (JsonNode candidate : results) {
                ProteinEnrichment enrichment = parseCandidate(candidate);
                if (enrichment == null) {
                    continue;
                }
                int score = scoreCandidate(enrichment, normalizedAccession, taxId);
                if (score > bestScore) {
                    best = enrichment;
                    bestScore = score;
                }
            }

            return Optional.ofNullable(best);
        } catch (Exception ex) {
            log.warn("Failed to enrich accession {} from UniProt", accession, ex);
            return Optional.empty();
        }
    }

    private ProteinEnrichment parseCandidate(JsonNode candidate) {
        String primaryAccession = readText(candidate, "primaryAccession");
        if (primaryAccession == null || primaryAccession.isBlank()) {
            return null;
        }

        JsonNode crossRefs = candidate.path("uniProtKBCrossReferences");
        List<String> refSeqAccessions = new ArrayList<>();
        List<String> pdbIds = new ArrayList<>();
        String alphaFoldAccession = null;
        if (crossRefs.isArray()) {
            for (JsonNode crossRef : crossRefs) {
                String database = readText(crossRef, "database");
                String id = readText(crossRef, "id");
                if (database == null || id == null || id.isBlank()) {
                    continue;
                }

                if ("RefSeq".equalsIgnoreCase(database)) {
                    refSeqAccessions.add(id);
                } else if ("PDB".equalsIgnoreCase(database)) {
                    pdbIds.add(id);
                } else if ("AlphaFoldDB".equalsIgnoreCase(database)) {
                    alphaFoldAccession = id;
                }
            }
        }

        String functionSummary = null;
        JsonNode comments = candidate.path("comments");
        if (comments.isArray()) {
            for (JsonNode comment : comments) {
                String commentType = readText(comment, "commentType");
                if (!"FUNCTION".equalsIgnoreCase(commentType)) {
                    continue;
                }
                JsonNode texts = comment.path("texts");
                if (texts.isArray() && texts.size() > 0) {
                    functionSummary = readText(texts.get(0), "value");
                    if (functionSummary != null && !functionSummary.isBlank()) {
                        break;
                    }
                }
            }
        }

        String ecNumber = null;
        JsonNode ecNumbers = candidate.path("proteinDescription")
                .path("recommendedName")
                .path("ecNumbers");
        if (ecNumbers.isArray() && ecNumbers.size() > 0) {
            ecNumber = readText(ecNumbers.get(0), "value");
        }

        String proteinName = readText(
                candidate.path("proteinDescription").path("recommendedName").path("fullName"),
                "value");
        String organismName = readText(candidate.path("organism"), "scientificName");
        String organismTaxId = readText(candidate.path("organism"), "taxonId");
        String geneSymbol = null;
        JsonNode genes = candidate.path("genes");
        if (genes.isArray() && genes.size() > 0) {
            geneSymbol = readText(genes.get(0).path("geneName"), "value");
        }

        return ProteinEnrichment.builder()
                .primaryAccession(primaryAccession)
                .proteinName(proteinName)
                .organismName(organismName)
                .taxId(organismTaxId)
                .geneSymbol(geneSymbol)
                .ecNumber(ecNumber)
                .functionSummary(functionSummary)
                .refSeqAccessions(refSeqAccessions)
                .pdbIds(pdbIds)
                .alphaFoldAccession(alphaFoldAccession)
                .build();
    }

    private int scoreCandidate(ProteinEnrichment enrichment, String accession, String taxId) {
        int score = 0;
        if (matchesRefSeq(enrichment.getRefSeqAccessions(), accession)) {
            score += 100;
        }
        if (taxId != null && !taxId.isBlank() && taxId.equals(enrichment.getTaxId())) {
            score += 20;
        }
        if (!enrichment.getPdbIds().isEmpty()) {
            score += 3;
        }
        if (enrichment.getAlphaFoldAccession() != null && !enrichment.getAlphaFoldAccession().isBlank()) {
            score += 2;
        }
        return score;
    }

    private boolean matchesRefSeq(List<String> refSeqAccessions, String accession) {
        String normalizedAccession = normalize(accession);
        String accessionWithoutVersion = stripVersion(normalizedAccession);
        for (String candidate : refSeqAccessions) {
            String normalizedCandidate = normalize(candidate);
            if (normalizedCandidate.equals(normalizedAccession)
                    || stripVersion(normalizedCandidate).equals(accessionWithoutVersion)) {
                return true;
            }
        }
        return false;
    }

    private String readText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String stripVersion(String accession) {
        int idx = accession.indexOf('.');
        return idx >= 0 ? accession.substring(0, idx) : accession;
    }

    @Value
    @Builder
    public static class ProteinEnrichment {
        String primaryAccession;
        String proteinName;
        String organismName;
        String taxId;
        String geneSymbol;
        String ecNumber;
        String functionSummary;
        @Builder.Default
        List<String> refSeqAccessions = List.of();
        @Builder.Default
        List<String> pdbIds = List.of();
        String alphaFoldAccession;
    }
}
