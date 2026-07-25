package com.freewillase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class RcsbPdbClient {
    private static final Set<String> DOMAIN_FEATURE_TYPES = Set.of(
            "PFAM", "CATH", "SCOP", "SCOP2", "SCOP2_SUPERFAMILY", "SCOP2_FAMILY",
            "SCOP2B_SUPERFAMILY", "SCOP2B_FAMILY", "ECOD", "INTERPRO"
    );
    private static final Set<String> ACTIVE_SITE_FEATURE_TYPES = Set.of(
            "BINDING_SITE", "ACTIVE_SITE", "CATALYTIC_SITE", "SITE"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String graphqlUrl;

    public RcsbPdbClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${app.rcsb.graphql-url:https://data.rcsb.org/graphql}") String graphqlUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.graphqlUrl = graphqlUrl;
    }

    public List<PdbFeatureAnnotation> fetchFeatureAnnotations(String pdbId) {
        String normalizedPdbId = normalize(pdbId);
        if (normalizedPdbId.isBlank()) {
            return List.of();
        }

        String query = """
                query {
                  entry(entry_id: "%s") {
                    polymer_entity_instances {
                      rcsb_id
                      rcsb_polymer_instance_feature {
                        type
                        feature_id
                        name
                        provenance_source
                        feature_positions {
                          beg_seq_id
                          end_seq_id
                        }
                      }
                    }
                  }
                }
                """.formatted(normalizedPdbId);

        try {
            String body = restTemplate.postForObject(
                    graphqlUrl,
                    java.util.Map.of("query", query),
                    String.class
            );
            if (body == null || body.isBlank()) {
                return List.of();
            }

            JsonNode instances = objectMapper.readTree(body)
                    .path("data")
                    .path("entry")
                    .path("polymer_entity_instances");
            if (!instances.isArray()) {
                return List.of();
            }

            List<PdbFeatureAnnotation> annotations = new ArrayList<>();
            for (JsonNode instance : instances) {
                String instanceId = readText(instance, "rcsb_id");
                String chainLabel = extractChainLabel(instanceId);
                JsonNode features = instance.path("rcsb_polymer_instance_feature");
                if (!features.isArray()) {
                    continue;
                }
                for (JsonNode feature : features) {
                    annotations.addAll(parseFeatureAnnotations(normalizedPdbId, chainLabel, feature));
                }
            }
            return annotations;
        } catch (Exception ex) {
            log.warn("Failed to fetch feature annotations for PDB {}", pdbId, ex);
            return List.of();
        }
    }

    private List<PdbFeatureAnnotation> parseFeatureAnnotations(String pdbId, String chainLabel, JsonNode feature) {
        String featureType = normalize(readText(feature, "type"));
        if (featureType.isBlank()) {
            return List.of();
        }

        String annotationType = mapFeatureType(featureType);
        if (annotationType == null) {
            return List.of();
        }

        String featureId = readText(feature, "feature_id");
        String featureName = readText(feature, "name");
        String provenanceSource = readText(feature, "provenance_source");
        JsonNode positions = feature.path("feature_positions");
        if (!positions.isArray() || positions.isEmpty()) {
            return List.of();
        }

        List<PdbFeatureAnnotation> annotations = new ArrayList<>();
        int index = 0;
        for (JsonNode position : positions) {
            Integer start = readInteger(position, "beg_seq_id");
            Integer end = readInteger(position, "end_seq_id");
            if (start == null || start <= 0) {
                continue;
            }
            if (end == null || end <= 0) {
                end = start;
            }

            index++;
            annotations.add(PdbFeatureAnnotation.builder()
                    .annotationType(annotationType)
                    .title(buildFeatureTitle(annotationType, featureType, featureName, start, end))
                    .startResidue(start)
                    .endResidue("ACTIVE_SITE".equals(annotationType) ? start : end)
                    .chainLabel(chainLabel)
                    .description(buildDescription(featureName, featureType, provenanceSource, featureId, chainLabel))
                    .sourceDb("PDB")
                    .sourceRef(pdbId + ":" + defaultString(chainLabel) + ":" + featureType + ":" + defaultString(featureId) + ":" + index + ":" + start + "-" + end)
                    .build());
        }
        return annotations;
    }

    private String mapFeatureType(String featureType) {
        if (DOMAIN_FEATURE_TYPES.contains(featureType)) {
            return "DOMAIN";
        }
        if (ACTIVE_SITE_FEATURE_TYPES.contains(featureType)) {
            return "ACTIVE_SITE";
        }
        return null;
    }

    private String buildFeatureTitle(String annotationType, String featureType, String featureName, Integer start, Integer end) {
        if (featureName != null && !featureName.isBlank()) {
            if ("ACTIVE_SITE".equals(annotationType)) {
                return "PDB 位点 " + featureName + "@" + start;
            }
            return "PDB 结构域 " + featureName + " (" + start + "-" + end + ")";
        }
        if ("ACTIVE_SITE".equals(annotationType)) {
            return "PDB 位点 " + start;
        }
        return "PDB " + featureType + " " + start + "-" + end;
    }

    private String buildDescription(String featureName,
                                    String featureType,
                                    String provenanceSource,
                                    String featureId,
                                    String chainLabel) {
        List<String> parts = new ArrayList<>();
        if (featureName != null && !featureName.isBlank()) {
            parts.add(featureName);
        }
        if (featureType != null && !featureType.isBlank()) {
            parts.add("类型: " + featureType);
        }
        if (chainLabel != null && !chainLabel.isBlank()) {
            parts.add("链: " + chainLabel);
        }
        if (provenanceSource != null && !provenanceSource.isBlank()) {
            parts.add("来源: " + provenanceSource);
        }
        if (featureId != null && !featureId.isBlank()) {
            parts.add("标识: " + featureId);
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private String extractChainLabel(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        int dotIndex = instanceId.indexOf('.');
        if (dotIndex < 0 || dotIndex + 1 >= instanceId.length()) {
            return null;
        }
        return instanceId.substring(dotIndex + 1);
    }

    private Integer readInteger(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.isInt() ? child.intValue() : child.asInt();
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

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    @Value
    @Builder
    public static class PdbFeatureAnnotation {
        String annotationType;
        String title;
        Integer startResidue;
        Integer endResidue;
        String chainLabel;
        String description;
        String sourceDb;
        String sourceRef;
    }
}
