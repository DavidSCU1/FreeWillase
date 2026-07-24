package com.freewillase.backend.service;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

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

    public ProteinLookupResult fetchProteinByAccession(String accession, String email, String apiKey) {
        String uid = searchProteinUid(accession, email, apiKey);
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("NCBI 未找到 accession: " + accession);
        }

        Map<String, Object> summaryRoot = getJson("/esummary.fcgi", query("db", "protein", "id", uid, "retmode", "json"), email, apiKey);
        Map<String, Object> resultNode = castMap(summaryRoot.get("result"));
        Map<String, Object> summaryNode = castMap(resultNode.get(uid));

        UriComponentsBuilder efetchBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/efetch.fcgi")
                .queryParam("db", "protein")
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
        return ProteinLookupResult.builder()
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

    private String searchProteinUid(String accession, String email, String apiKey) {
        Map<String, Object> searchRoot = getJson("/esearch.fcgi", query(
                "db", "protein",
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
    public static class ProteinLookupResult {
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
}
