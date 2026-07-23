package com.freewillase.backend.service;

import com.freewillase.backend.dto.RnaFoldPredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RnaFoldService {

    private static final String SUBMIT_URL = "http://rna.tbi.univie.ac.at/cgi-bin/RNAWebSuite/RNAfold.cgi";
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("PAGE=3&ID=([A-Za-z0-9_\\-]+)");
    private static final Pattern MFE_ENERGY_PATTERN = Pattern.compile("minimum free energy of\\s+(-?\\d+(?:\\.\\d+)?)\\s+kcal/mol", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENSEMBLE_ENERGY_PATTERN = Pattern.compile("thermodynamic ensemble is\\s+(-?\\d+(?:\\.\\d+)?)\\s+kcal/mol", Pattern.CASE_INSENSITIVE);
    private static final Pattern MFE_FREQUENCY_PATTERN = Pattern.compile("frequency of the MFE structure in the ensemble is\\s+(-?\\d+(?:\\.\\d+)?)\\s+%", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENSEMBLE_DIVERSITY_PATTERN = Pattern.compile("ensemble diversity is\\s+(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CENTROID_ENERGY_PATTERN = Pattern.compile("centroid secondary structure in dot-bracket notation with a minimum free energy of\\s+(-?\\d+(?:\\.\\d+)?)\\s+kcal/mol", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRE_BLOCK_PATTERN = Pattern.compile("<pre[^>]*>(.*?)</pre>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MFE_SEQUENCE_VAR_PATTERN = Pattern.compile("var\\s+MFE_sequence\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MFE_STRUCTURE_VAR_PATTERN = Pattern.compile("var\\s+MFE_structure\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CENTROID_STRUCTURE_VAR_PATTERN = Pattern.compile("var\\s+CENTROID_structure\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    public RnaFoldPredictionResponse predict(String name, String sequence) {
        String normalizedSequence = sequence.replaceAll("\\s+", "").toUpperCase();
        if (normalizedSequence.isBlank()) {
            throw new IllegalArgumentException("请填写 RNA 序列");
        }

        String jobId = submitJob(normalizedSequence);
        String resultPageUrl = SUBMIT_URL + "?PAGE=3&ID=" + jobId;
        String html = pollResult(resultPageUrl);

        String[] structures = extractStructures(html);
        String resolvedSequence = extractSequence(html);
        return RnaFoldPredictionResponse.builder()
                .providerName("RNAfold")
                .modelName("RNAfold")
                .format("dot-bracket")
                .structure(structures[0])
                .sequence(resolvedSequence != null && !resolvedSequence.isBlank() ? resolvedSequence : normalizedSequence)
                .resultPageUrl(resultPageUrl)
                .mfeStructure(structures[0])
                .mfeEnergy(extractNumber(html, MFE_ENERGY_PATTERN))
                .ensembleFreeEnergy(extractNumber(html, ENSEMBLE_ENERGY_PATTERN))
                .mfeFrequency(extractNumber(html, MFE_FREQUENCY_PATTERN))
                .ensembleDiversity(extractNumber(html, ENSEMBLE_DIVERSITY_PATTERN))
                .centroidStructure(structures[1])
                .centroidEnergy(extractNumber(html, CENTROID_ENERGY_PATTERN))
                .build();
    }

    private String submitJob(String sequence) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("PAGE", "2");
        form.add("SCREEN", sequence);
        form.add("method", "p");
        form.add("noLP", "on");
        form.add("dangling", "d2");
        form.add("param", "rna2004");
        form.add("Temp", "37");
        form.add("salt", "1.021");
        form.add("svg", "on");
        form.add("reliability", "on");
        form.add("mountain", "on");

        String html = restTemplate.postForObject(SUBMIT_URL, new HttpEntity<>(form, headers), String.class);
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("RNAfold 返回空响应");
        }

        Matcher matcher = JOB_ID_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IllegalArgumentException("未能获取 RNAfold 任务 ID，请稍后重试");
        }
        return matcher.group(1);
    }

    private String pollResult(String resultPageUrl) {
        for (int i = 0; i < 20; i++) {
            String html = restTemplate.getForObject(resultPageUrl, String.class);
            if (html != null && html.contains("Results for minimum free energy prediction")) {
                return html;
            }
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("RNAfold 任务轮询被中断");
            }
        }
        throw new IllegalArgumentException("RNAfold 任务等待超时，请稍后重试");
    }

    private String[] extractStructures(String html) {
        String mfe = extractJsDotBracket(html, MFE_STRUCTURE_VAR_PATTERN);
        if (mfe != null && !mfe.isBlank()) {
            String centroid = extractJsDotBracket(html, CENTROID_STRUCTURE_VAR_PATTERN);
            if (centroid == null || centroid.isBlank()) centroid = mfe;
            return new String[] { mfe, centroid };
        }

        Matcher matcher = PRE_BLOCK_PATTERN.matcher(html);
        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            String block = matcher.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .trim();
            if (block.isBlank()) continue;
            String[] lines = block.split("\\R");
            for (String line : lines) {
                String candidate = line.replaceFirst("^\\s*\\d+\\s+", "").trim();
                if (candidate.isBlank()) continue;
                if (!candidate.matches("[().<>\\[\\]{}.,x|]+")) continue;
                if (candidates.isEmpty() || !candidates.get(candidates.size() - 1).equals(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("未能解析 RNAfold 返回的 dot-bracket 结构");
        }
        String mfeFallback = candidates.get(0);
        String centroidFallback = candidates.size() > 1 ? candidates.get(1) : mfeFallback;
        return new String[] { mfeFallback, centroidFallback };
    }

    private Double extractNumber(String html, Pattern pattern) {
        String plain = html.replaceAll("<[^>]+>", " ");
        Matcher matcher = pattern.matcher(plain);
        if (!matcher.find()) return null;
        return Double.parseDouble(matcher.group(1));
    }

    private String extractSequence(String html) {
        Matcher matcher = MFE_SEQUENCE_VAR_PATTERN.matcher(html);
        if (!matcher.find()) return null;
        return matcher.group(1).replaceFirst("^\\s*\\d+\\s+", "").trim();
    }

    private String extractJsDotBracket(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) return null;
        String line = matcher.group(1);
        String candidate = line.replaceFirst("^\\s*\\d+\\s+", "").trim();
        if (!candidate.matches("[().<>\\[\\]{}.,x|]+")) return null;
        return candidate;
    }
}
