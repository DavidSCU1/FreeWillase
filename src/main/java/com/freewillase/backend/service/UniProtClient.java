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
import java.util.Set;

@Slf4j
@Component
public class UniProtClient {
    private static final Set<String> DOMAIN_FEATURE_TYPES = Set.of("DOMAIN", "REGION", "REPEAT", "ZN_FING");
    private static final Set<String> ACTIVE_SITE_FEATURE_TYPES = Set.of("ACT_SITE", "BINDING", "SITE");
    private static final Set<String> MUTATION_FEATURE_TYPES = Set.of("MUTAGEN", "VARIANT");

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

    public List<FeatureAnnotation> fetchFeatureAnnotations(String accession) {
        String normalizedAccession = normalize(accession);
        if (normalizedAccession.isBlank()) {
            return List.of();
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/uniprotkb/" + normalizedAccession + ".json")
                .build(true)
                .toUri();

        try {
            String body = restTemplate.getForObject(uri, String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode features = objectMapper.readTree(body).path("features");
            if (!features.isArray()) {
                return List.of();
            }

            List<FeatureAnnotation> annotations = new ArrayList<>();
            for (JsonNode feature : features) {
                FeatureAnnotation annotation = parseFeatureAnnotation(normalizedAccession, feature);
                if (annotation != null) {
                    annotations.add(annotation);
                }
            }
            return annotations;
        } catch (Exception ex) {
            log.warn("Failed to fetch feature annotations for UniProt {}", accession, ex);
            return List.of();
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

    private FeatureAnnotation parseFeatureAnnotation(String accession, JsonNode feature) {
        String featureType = readText(feature, "type");
        if (featureType == null || featureType.isBlank()) {
            return null;
        }

        String annotationType = mapFeatureType(featureType);
        if (annotationType == null) {
            return null;
        }

        JsonNode location = feature.path("location");
        Integer start = readLocationPosition(location.path("start"));
        Integer end = readLocationPosition(location.path("end"));
        if (start == null && end == null) {
          Integer position = readLocationPosition(location.path("position"));
          start = position;
          end = position;
        }
        if (start == null || start <= 0) {
            return null;
        }
        if (end == null || end <= 0) {
            end = start;
        }
        if ("MUTATION".equals(annotationType)) {
            end = start;
        }

        String title = firstNonBlank(
                readText(feature, "description"),
                readText(feature, "featureId"),
                defaultFeatureTitle(annotationType, start, end));
        String mutationLabel = null;
        if ("MUTATION".equals(annotationType)) {
            mutationLabel = readText(feature, "description");
        }
        String evidence = buildEvidenceText(feature.path("evidences"));
        String description = joinNonBlank(
                readText(feature, "description"),
                evidence == null ? null : "证据: " + evidence);

        return FeatureAnnotation.builder()
                .annotationType(annotationType)
                .title(title)
                .startResidue(start)
                .endResidue(end)
                .mutationLabel(mutationLabel)
                .description(description)
                .sourceDb("UNIPROT")
                .sourceRef(accession + ":" + featureType + ":" + start + "-" + end)
                .build();
    }

    private String mapFeatureType(String featureType) {
        String normalized = normalize(featureType);
        if (DOMAIN_FEATURE_TYPES.contains(normalized)) {
            return "DOMAIN";
        }
        if (ACTIVE_SITE_FEATURE_TYPES.contains(normalized)) {
            return "ACTIVE_SITE";
        }
        if (MUTATION_FEATURE_TYPES.contains(normalized)) {
            return "MUTATION";
        }
        return null;
    }

    private Integer readLocationPosition(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String raw = readText(node, "value");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildEvidenceText(JsonNode evidences) {
        if (!evidences.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode evidence : evidences) {
            String code = readText(evidence, "evidenceCode");
            if (code != null && !code.isBlank()) {
                parts.add(code);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String defaultFeatureTitle(String annotationType, Integer start, Integer end) {
        if ("ACTIVE_SITE".equals(annotationType)) {
            return "UniProt 活性位点 " + start;
        }
        if ("MUTATION".equals(annotationType)) {
            return "UniProt 突变位点 " + start;
        }
        return "UniProt 结构域 " + start + "-" + end;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join("；", parts);
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

    @Value
    @Builder
    public static class FeatureAnnotation {
        String annotationType;
        String title;
        Integer startResidue;
        Integer endResidue;
        String mutationLabel;
        String description;
        String sourceDb;
        String sourceRef;
    }
}
