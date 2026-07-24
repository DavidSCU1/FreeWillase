package com.freewillase.backend.service;

import com.freewillase.backend.dto.RnaFoldPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RnaFoldService {

    private static final String SUBMIT_URL = "https://rna.tbi.univie.ac.at/cgi-bin/RNAWebSuite/RNAfold.cgi";
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

        log.info("Starting RNAfold prediction for task: {} (length: {})", name, normalizedSequence.length());
        
        try {
            String jobId = submitJob(normalizedSequence);
            String resultPageUrl = SUBMIT_URL + "?PAGE=3&ID=" + jobId;
            log.info("RNAfold job submitted successfully. Job ID: {}. Polling for results...", jobId);
            
            String html = pollResult(resultPageUrl);
            log.info("RNAfold result page fetched. Parsing structures...");

            String[] structures = extractStructures(html);
            String resolvedSequence = extractSequence(html);
            
            log.info("RNAfold task completed successfully: {}", name);
            
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
        } catch (RestClientException e) {
            log.error("Network error while calling RNAfold for task: {}", name, e);
            throw new RuntimeException("无法连接维也纳大学 RNAfold 服务，请检查网络设置或稍后重试", e);
        } catch (Exception e) {
            log.error("Unexpected error during RNAfold prediction for task: {}", name, e);
            throw e;
        }
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
            throw new IllegalStateException("RNAfold 服务返回空响应，任务提交失败");
        }

        Matcher matcher = JOB_ID_PATTERN.matcher(html);
        if (!matcher.find()) {
            log.warn("Failed to find Job ID in HTML response: {}", html.length() > 500 ? html.substring(0, 500) + "..." : html);
            throw new IllegalStateException("未能从 RNAfold 响应中提取任务 ID，可能是序列过长或格式不被支持");
        }
        return matcher.group(1);
    }

    private String pollResult(String resultPageUrl) {
        int maxAttempts = 25;
        for (int i = 1; i <= maxAttempts; i++) {
            log.debug("Polling RNAfold result, attempt {}/{}", i, maxAttempts);
            String html = restTemplate.getForObject(resultPageUrl, String.class);
            
            if (html != null && html.contains("Results for minimum free energy prediction")) {
                return html;
            }
            
            if (html != null && html.contains("An error occurred during calculation")) {
                log.error("RNAfold server reported a calculation error for URL: {}", resultPageUrl);
                throw new IllegalStateException("RNAfold 服务端计算出错，请检查序列是否包含过多非法字符");
            }

            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("任务轮询被中断");
            }
        }
        log.warn("RNAfold polling timed out for URL: {}", resultPageUrl);
        throw new RuntimeException("RNAfold 任务等待超时（已等待 50s），目标服务器可能负载过高，请稍后直接在结果页查看");
    }

    private String[] extractStructures(String html) {
        String mfe = extractJsDotBracket(html, MFE_STRUCTURE_VAR_PATTERN);
        if (mfe != null && !mfe.isBlank()) {
            log.debug("Successfully extracted MFE structure from JavaScript variables.");
            String centroid = extractJsDotBracket(html, CENTROID_STRUCTURE_VAR_PATTERN);
            if (centroid == null || centroid.isBlank()) centroid = mfe;
            return new String[] { mfe, centroid };
        }

        log.info("MFE structure not found in JS variables, falling back to <pre> block parsing...");
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
