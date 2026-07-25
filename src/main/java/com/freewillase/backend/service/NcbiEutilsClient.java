package com.freewillase.backend.service;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NcbiEutilsClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String toolName;

    public NcbiEutilsClient(
            RestTemplate restTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.ncbi.base-url}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${app.ncbi.tool}") String toolName
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.toolName = toolName;
    }

    public LookupResult fetchProteinByAccession(String accession, String email, String apiKey) {
        return fetchByAccession("protein", accession, email, apiKey);
    }

    public LookupResult fetchNucleotideByAccession(String accession, String email, String apiKey) {
        return fetchByAccession("nucleotide", accession, email, apiKey);
    }

    public List<NucleotideFeatureAnnotation> fetchNucleotideFeatureAnnotations(String accession, String email, String apiKey) {
        String uid = searchUid("nucleotide", accession, email, apiKey);
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("NCBI 未找到 accession: " + accession);
        }

        UriComponentsBuilder efetchBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/efetch.fcgi")
                .queryParam("db", "nucleotide")
                .queryParam("id", uid)
                .queryParam("rettype", "gbc")
                .queryParam("retmode", "xml")
                .queryParam("tool", toolName);

        if (email != null && !email.isBlank()) efetchBuilder.queryParam("email", email);
        if (apiKey != null && !apiKey.isBlank()) efetchBuilder.queryParam("api_key", apiKey);

        String xmlText = restTemplate.getForObject(
                efetchBuilder.encode().build().toUri(),
                String.class
        );
        if (xmlText == null || xmlText.isBlank()) {
            return List.of();
        }
        return parseNucleotideFeatureAnnotations(accession, xmlText);
    }

    private LookupResult fetchByAccession(String db, String accession, String email, String apiKey) {
        String uid = searchUid(db, accession, email, apiKey);
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("NCBI 未找到 accession: " + accession);
        }

        Map<String, Object> summaryRoot = getJson("/esummary.fcgi", query("db", db, "id", uid, "retmode", "json"), email, apiKey);
        Map<String, Object> resultNode = castMap(summaryRoot.get("result"));
        Map<String, Object> summaryNode = castMap(resultNode.get(uid));

        UriComponentsBuilder efetchBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/efetch.fcgi")
                .queryParam("db", db)
                .queryParam("id", uid)
                .queryParam("rettype", "fasta")
                .queryParam("retmode", "text")
                .queryParam("tool", toolName);
        
        if (email != null && !email.isBlank()) efetchBuilder.queryParam("email", email);
        if (apiKey != null && !apiKey.isBlank()) efetchBuilder.queryParam("api_key", apiKey);

        String fastaText = restTemplate.getForObject(
                efetchBuilder.encode().build().toUri(),
                String.class
        );

        String sequence = parseSequence(fastaText);
        return LookupResult.builder()
                .uid(uid)
                .accession(readString(summaryNode, "accessionversion", accession))
                .title(readString(summaryNode, "title", accession))
                .organism(readString(summaryNode, "organism", "Unknown organism"))
                .taxId(readString(summaryNode, "taxid", null))
                .sequence(sequence)
                .sequenceLength(sequence.length())
                .build();
    }

    public List<PubMedResult> searchPubMed(String term, int maxResults, String email, String apiKey) {
        Map<String, Object> searchRoot = getJson("/esearch.fcgi", query(
                "db", "pubmed",
                "term", term,
                "retmax", String.valueOf(maxResults),
                "retmode", "json"
        ), email, apiKey);
        
        Map<String, Object> resultNode = castMap(searchRoot.get("esearchresult"));
        Object idListNode = resultNode.get("idlist");
        if (!(idListNode instanceof List)) return List.of();
        
        List<?> ids = (List<?>) idListNode;
        if (ids.isEmpty()) return List.of();
        
        // Deduplicate IDs before fetching summary
        List<String> uniqueIds = ids.stream()
                .map(Object::toString)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        
        String idParam = String.join(",", uniqueIds);
        Map<String, Object> summaryRoot = getJson("/esummary.fcgi", query(
                "db", "pubmed",
                "id", idParam,
                "retmode", "json"
        ), email, apiKey);
        
        Map<String, Object> summaryResult = castMap(summaryRoot.get("result"));
        List<String> uids = (List<String>) summaryResult.get("uids");
        
        return uids.stream().map(uid -> {
            Map<String, Object> doc = castMap(summaryResult.get(uid));
            return PubMedResult.builder()
                    .pmid(uid)
                    .title(readString(doc, "title", "No Title"))
                    .authors(parseAuthors(doc))
                    .journal(readString(doc, "fulljournalname", "Unknown Journal"))
                    .publishYear(parseYear(readString(doc, "pubdate", "")))
                    .doi(readString(doc, "elocationid", "").replace("doi: ", ""))
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }

    public PmcFullTextResult fetchPmcFullTextByPmid(String pmid, String email, String apiKey) {
        Map<String, Object> linkRoot = getJson("/elink.fcgi", query(
                "dbfrom", "pubmed",
                "db", "pmc",
                "id", pmid,
                "retmode", "json"
        ), email, apiKey);

        List<Map<String, Object>> linkSets = castList(linkRoot.get("linksets"));
        if (linkSets.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> linkSetDbs = castList(linkSets.get(0).get("linksetdbs"));
        if (linkSetDbs.isEmpty()) {
            return null;
        }

        List<?> links = castRawList(linkSetDbs.get(0).get("links"));
        if (links.isEmpty() || links.get(0) == null) {
            return null;
        }

        String pmcId = links.get(0).toString();
        UriComponentsBuilder efetchBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/efetch.fcgi")
                .queryParam("db", "pmc")
                .queryParam("id", pmcId)
                .queryParam("retmode", "xml")
                .queryParam("tool", toolName);

        if (email != null && !email.isBlank()) efetchBuilder.queryParam("email", email);
        if (apiKey != null && !apiKey.isBlank()) efetchBuilder.queryParam("api_key", apiKey);

        String xmlText = restTemplate.getForObject(
                efetchBuilder.encode().build().toUri(),
                String.class
        );

        if (xmlText == null || xmlText.isBlank()) {
            return null;
        }

        return PmcFullTextResult.builder()
                .pmcId("PMC" + pmcId)
                .xmlContent(xmlText)
                .sourceUrl("https://pmc.ncbi.nlm.nih.gov/articles/PMC" + pmcId + "/")
                .build();
    }

    private String parseAuthors(Map<String, Object> doc) {
        Object authorsNode = doc.get("authors");
        if (authorsNode instanceof List) {
            List<Map<String, Object>> authorList = (List<Map<String, Object>>) authorsNode;
            return authorList.stream()
                    .map(a -> readString(a, "name", ""))
                    .filter(s -> !s.isBlank())
                    .limit(3)
                    .collect(java.util.stream.Collectors.joining(", ")) + (authorList.size() > 3 ? " et al." : "");
        }
        return "Unknown Authors";
    }

    private int parseYear(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return 0;
        try {
            return Integer.parseInt(pubDate.substring(0, 4));
        } catch (Exception e) {
            return 0;
        }
    }

    private String searchUid(String db, String accession, String email, String apiKey) {
        Map<String, Object> searchRoot = getJson("/esearch.fcgi", query(
                "db", db,
                "term", accession + "[Accession]",
                "retmode", "json"
        ), email, apiKey);
        Map<String, Object> resultNode = castMap(searchRoot.get("esearchresult"));
        Object idListNode = resultNode.get("idlist");
        if (!(idListNode instanceof List)) {
            return null;
        }
        List<?> ids = (List<?>) idListNode;
        if (ids.isEmpty()) {
            return null;
        }
        Object first = ids.get(0);
        return first == null ? null : first.toString();
    }

    private Map<String, Object> getJson(String path, MultiValueMap<String, String> queryParams, String email, String apiKey) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path)
                .queryParams(queryParams)
                .queryParam("tool", toolName);
        
        if (email != null && !email.isBlank()) builder.queryParam("email", email);
        if (apiKey != null && !apiKey.isBlank()) builder.queryParam("api_key", apiKey);

        java.net.URI uri = builder.encode().build().toUri();
        log.debug("NCBI Request: {}", uri);

        return restTemplate.getForObject(
                uri,
                Map.class
        );
    }

    private MultiValueMap<String, String> query(String... kvPairs) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            query.add(kvPairs[i], kvPairs[i + 1]);
        }
        return query;
    }

    private String parseSequence(String fastaText) {
        if (fastaText == null || fastaText.isBlank()) {
            return "";
        }
        String[] lines = fastaText.split("\\R");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (!line.startsWith(">")) {
                builder.append(line.trim());
            }
        }
        return builder.toString();
    }

    private List<NucleotideFeatureAnnotation> parseNucleotideFeatureAnnotations(String accession, String xmlText) {
        try {
            String sanitizedXml = xmlText.replaceFirst("(?s)<!DOCTYPE[^>]*>", "");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(sanitizedXml)));
            NodeList seqNodes = document.getElementsByTagName("INSDSeq");
            if (seqNodes.getLength() == 0) {
                return List.of();
            }

            Element seqElement = (Element) seqNodes.item(0);
            String definition = childText(seqElement, "INSDSeq_definition");
            String comment = childText(seqElement, "INSDSeq_comment");
            String taxonomy = childText(seqElement, "INSDSeq_taxonomy");
            String organism = childText(seqElement, "INSDSeq_organism");
            int sequenceLength = parseInteger(childText(seqElement, "INSDSeq_length"));

            List<NucleotideFeatureAnnotation> annotations = new ArrayList<>();
            NodeList featureNodes = seqElement.getElementsByTagName("INSDFeature");
            for (int i = 0; i < featureNodes.getLength(); i++) {
                Element featureElement = (Element) featureNodes.item(i);
                String featureKey = childText(featureElement, "INSDFeature_key");
                LocationRange range = parseFeatureRange(featureElement);
                if (featureKey == null || range == null || shouldSkipFeature(featureKey, range, sequenceLength)) {
                    continue;
                }

                Map<String, String> qualifiers = parseFeatureQualifiers(featureElement);
                NucleotideFeatureAnnotation annotation = buildNucleotideFeatureAnnotation(
                        accession,
                        featureKey,
                        range,
                        qualifiers
                );
                if (annotation != null) {
                    annotations.add(annotation);
                }
            }

            if (annotations.isEmpty()) {
                NucleotideFeatureAnnotation inferred = buildFallbackRibozymeAnnotation(
                        accession,
                        definition,
                        comment,
                        taxonomy,
                        organism,
                        sequenceLength
                );
                if (inferred != null) {
                    annotations.add(inferred);
                }
            }
            return annotations;
        } catch (Exception ex) {
            log.warn("Failed to parse nucleotide feature annotations for {}", accession, ex);
            return List.of();
        }
    }

    private NucleotideFeatureAnnotation buildNucleotideFeatureAnnotation(String accession,
                                                                        String featureKey,
                                                                        LocationRange range,
                                                                        Map<String, String> qualifiers) {
        String combinedText = StreamText.join(featureKey, qualifiers.values()).toLowerCase(Locale.ROOT);
        String annotationType = mapNucleotideFeatureType(featureKey, combinedText);
        if (annotationType == null) {
            return null;
        }

        int endResidue = "MUTATION".equals(annotationType) ? range.start() : range.end();
        String title = buildNucleotideFeatureTitle(annotationType, featureKey, range, qualifiers, combinedText);
        String description = buildNucleotideFeatureDescription(featureKey, qualifiers);

        return NucleotideFeatureAnnotation.builder()
                .annotationType(annotationType)
                .title(title)
                .startResidue(range.start())
                .endResidue(endResidue)
                .description(description)
                .sourceDb("NCBI_NUCLEOTIDE")
                .sourceRef(accession + ":" + featureKey + ":" + range.start() + "-" + endResidue)
                .build();
    }

    private NucleotideFeatureAnnotation buildFallbackRibozymeAnnotation(String accession,
                                                                        String definition,
                                                                        String comment,
                                                                        String taxonomy,
                                                                        String organism,
                                                                        int sequenceLength) {
        if (sequenceLength <= 0) {
            return null;
        }

        String combinedText = StreamText.join(definition, comment, taxonomy, organism).toLowerCase(Locale.ROOT);
        String title = null;
        String description = null;

        if (combinedText.contains("hammerhead")) {
            title = "Hammerhead ribozyme 候选区域（全长）";
            description = "GenBank feature table 未提供可直接切分的区间，已根据条目标题或注释中的 hammerhead 线索回填全长候选区域。";
        } else if (combinedText.contains("ribozyme")
                || combinedText.contains("self-cleavage")
                || combinedText.contains("self-cleaving")
                || combinedText.contains("catalytic rna")) {
            title = "Ribozyme 候选区域（全长）";
            description = "GenBank feature table 未提供可直接切分的区间，已根据条目标题或注释中的核酶线索回填全长候选区域。";
        } else if (combinedText.contains("avsunviroidae")
                || combinedText.contains("avsunviroid")
                || combinedText.contains("pelamoviroid")) {
            title = "Avsunviroidae 核酶相关区域（全长候选）";
            description = "当前记录缺少细分 feature，已根据 Avsunviroidae / Pelamoviroid 分类信息回填全长核酶相关候选区域。";
        }

        if (title == null) {
            return null;
        }

        return NucleotideFeatureAnnotation.builder()
                .annotationType("DOMAIN")
                .title(title)
                .startResidue(1)
                .endResidue(sequenceLength)
                .description(description)
                .sourceDb("NCBI_NUCLEOTIDE")
                .sourceRef(accession + ":inferred:1-" + sequenceLength)
                .build();
    }

    private String mapNucleotideFeatureType(String featureKey, String combinedText) {
        String normalizedKey = featureKey == null ? "" : featureKey.toLowerCase(Locale.ROOT);
        if (normalizedKey.equals("variation")
                || normalizedKey.equals("conflict")
                || normalizedKey.equals("unsure")
                || combinedText.contains("mutation")
                || combinedText.contains("variant")
                || combinedText.contains("substitution")
                || combinedText.contains("deletion")
                || combinedText.contains("insertion")) {
            return "MUTATION";
        }

        if (normalizedKey.equals("misc_binding")
                || normalizedKey.equals("protein_bind")
                || normalizedKey.equals("modified_base")
                || combinedText.contains("active site")
                || combinedText.contains("catalytic")
                || combinedText.contains("cleavage site")
                || combinedText.contains("self-cleavage")
                || combinedText.contains("binding site")) {
            return "ACTIVE_SITE";
        }

        if (normalizedKey.equals("stem_loop")
                || normalizedKey.equals("regulatory")
                || normalizedKey.equals("repeat_region")
                || normalizedKey.equals("misc_feature")
                || normalizedKey.equals("precrna")
                || normalizedKey.equals("precursor_rna")
                || normalizedKey.equals("ncrna")
                || normalizedKey.equals("ncrna_class")
                || normalizedKey.equals("rrna")
                || combinedText.contains("hammerhead")
                || combinedText.contains("ribozyme")
                || combinedText.contains("motif")
                || combinedText.contains("domain")
                || combinedText.contains("stem-loop")
                || combinedText.contains("junction")
                || combinedText.contains("loop")) {
            return "DOMAIN";
        }
        return null;
    }

    private String buildNucleotideFeatureTitle(String annotationType,
                                               String featureKey,
                                               LocationRange range,
                                               Map<String, String> qualifiers,
                                               String combinedText) {
        String candidate = firstNonBlank(
                qualifiers.get("standard_name"),
                qualifiers.get("label"),
                qualifiers.get("product"),
                qualifiers.get("gene"),
                qualifiers.get("ncRNA_class"),
                qualifiers.get("rpt_family"),
                qualifiers.get("bound_moiety"),
                qualifiers.get("note"),
                qualifiers.get("function")
        );
        if (candidate != null) {
            return candidate;
        }
        if ("ACTIVE_SITE".equals(annotationType)) {
            if (combinedText.contains("hammerhead") || combinedText.contains("cleavage")) {
                return "RNA 裂解相关位点 " + range.start();
            }
            return "RNA 关键位点 " + range.start();
        }
        if ("MUTATION".equals(annotationType)) {
            return "RNA 变异位点 " + range.start();
        }
        String displayKey = featureKey == null ? "RNA 功能区" : featureKey.replace('_', ' ');
        return displayKey + " " + range.start() + "-" + range.end();
    }

    private String buildNucleotideFeatureDescription(String featureKey, Map<String, String> qualifiers) {
        List<String> parts = new ArrayList<>();
        if (featureKey != null && !featureKey.isBlank()) {
            parts.add("Feature: " + featureKey);
        }
        qualifiers.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                parts.add(key + ": " + value);
            }
        });
        String description = parts.stream().collect(Collectors.joining(" | "));
        return description.isBlank() ? null : description;
    }

    private boolean shouldSkipFeature(String featureKey, LocationRange range, int sequenceLength) {
        String normalizedKey = featureKey == null ? "" : featureKey.toLowerCase(Locale.ROOT);
        if ("source".equals(normalizedKey)) {
            return true;
        }
        return range.start() <= 0
                || range.end() < range.start()
                || (sequenceLength > 0 && range.start() == 1 && range.end() == sequenceLength && !"misc_feature".equals(normalizedKey));
    }

    private LocationRange parseFeatureRange(Element featureElement) {
        NodeList intervalNodes = featureElement.getElementsByTagName("INSDInterval");
        Integer min = null;
        Integer max = null;
        for (int i = 0; i < intervalNodes.getLength(); i++) {
            Element interval = (Element) intervalNodes.item(i);
            Integer from = parseInteger(firstNonBlank(
                    childText(interval, "INSDInterval_from"),
                    childText(interval, "INSDInterval_point")
            ));
            Integer to = parseInteger(firstNonBlank(
                    childText(interval, "INSDInterval_to"),
                    childText(interval, "INSDInterval_point")
            ));
            if (from == null || to == null) {
                continue;
            }
            int start = Math.min(from, to);
            int end = Math.max(from, to);
            min = min == null ? start : Math.min(min, start);
            max = max == null ? end : Math.max(max, end);
        }
        if (min == null || max == null) {
            return null;
        }
        return new LocationRange(min, max);
    }

    private Map<String, String> parseFeatureQualifiers(Element featureElement) {
        java.util.LinkedHashMap<String, String> qualifiers = new java.util.LinkedHashMap<>();
        NodeList qualifierNodes = featureElement.getElementsByTagName("INSDQualifier");
        for (int i = 0; i < qualifierNodes.getLength(); i++) {
            Element qualifier = (Element) qualifierNodes.item(i);
            String name = childText(qualifier, "INSDQualifier_name");
            String value = childText(qualifier, "INSDQualifier_value");
            if (name == null || value == null || value.isBlank()) {
                continue;
            }
            qualifiers.merge(name, value, (left, right) -> left.contains(right) ? left : left + "; " + right);
        }
        return qualifiers;
    }

    private String childText(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        NodeList nodeList = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node != null && node.getParentNode() == parent) {
                String text = node.getTextContent();
                return text == null || text.isBlank() ? null : text.trim();
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String readString(Map<String, Object> node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        Object value = node.get(field);
        return value == null ? fallback : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new IllegalStateException("NCBI 返回结构异常");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> castRawList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return List.of();
    }

    @lombok.Value
    @Builder
    public static class LookupResult {
        String uid;
        String accession;
        String title;
        String organism;
        String taxId;
        String sequence;
        int sequenceLength;
    }

    @lombok.Value
    @Builder
    public static class PubMedResult {
        String pmid;
        String title;
        String authors;
        String journal;
        int publishYear;
        String doi;
    }

    @lombok.Value
    @Builder
    public static class PmcFullTextResult {
        String pmcId;
        String xmlContent;
        String sourceUrl;
    }

    @lombok.Value
    @Builder
    public static class NucleotideFeatureAnnotation {
        String annotationType;
        String title;
        Integer startResidue;
        Integer endResidue;
        String description;
        String sourceDb;
        String sourceRef;
    }

    private record LocationRange(int start, int end) {
    }

    private static final class StreamText {
        private StreamText() {
        }

        private static String join(String first, java.util.Collection<String> rest) {
            List<String> values = new ArrayList<>();
            if (first != null && !first.isBlank()) {
                values.add(first);
            }
            if (rest != null) {
                rest.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(values::add);
            }
            return String.join(" ", values);
        }

        private static String join(String... values) {
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    normalized.add(value);
                }
            }
            return String.join(" ", normalized);
        }
    }
}
