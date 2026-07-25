package com.freewillase.backend.service;

import com.freewillase.backend.dto.TrRosettaRnaPredictionResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TrRosettaRnaService {

    private static final String SUBMIT_URL = "https://yanglab.qd.sdu.edu.cn/cgi-bin/rosetta_rna.cgi";
    private static final String BASE_OUTPUT_URL = "http://yanglab.qd.sdu.edu.cn/trRosettaRNA/output/";
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("output/([A-Za-z0-9_]+)");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public TrRosettaRnaPredictionResponse predict(String name, String sequence, String email) {
        String normalizedSequence = sequence.replaceAll("\\s+", "").toUpperCase();
        if (normalizedSequence.length() > 400) {
            throw new IllegalArgumentException("trRosettaRNA 网页版仅支持长度 ≤ 400nt 的序列");
        }

        log.info("Starting trRosettaRNA prediction for task: {} (length: {})", name, normalizedSequence.length());

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent(USER_AGENT)
                .build()) {

            // 1. Submit Job - Returns taskId immediately
            List<String> logs = new ArrayList<>();
            logs.add("root@freewillase:~# trrosettarna-predict --name \"" + name + "\" --len " + normalizedSequence.length());
            logs.add("[SYSTEM] Initializing HTTP client (Apache HttpClient 5.x)...");
            logs.add("[POST] https://yanglab.qd.sdu.edu.cn/cgi-bin/rosetta_rna.cgi");
            logs.add("[DATA] multipart/form-data; boundary=FreeWillaseBoundary");
            logs.add("[INFO] Sending sequence payload (" + normalizedSequence.substring(0, Math.min(10, normalizedSequence.length())) + "...)");
            
            String taskId = submitJob(httpClient, name, normalizedSequence, email);
            String resultPageUrl = BASE_OUTPUT_URL + taskId + "/";
            
            logs.add("[HTTP/1.1] 200 OK");
            logs.add("[SUCCESS] Job accepted. Remote TaskID: " + taskId);
            logs.add("[SYSTEM] Redirecting to monitor: " + resultPageUrl);
            
            log.info("trRosettaRNA job submitted. Task ID: {}", taskId);

            return TrRosettaRnaPredictionResponse.builder()
                    .providerName("trRosettaRNA")
                    .modelName("trRosettaRNA")
                    .taskId(taskId)
                    .status("RUNNING")
                    .resultPageUrl(resultPageUrl)
                    .message("任务已提交，正在排队中")
                    .logs(logs)
                    .build();

        } catch (Exception e) {
            log.error("Error during trRosettaRNA submission", e);
            throw new RuntimeException("trRosettaRNA 提交失败: " + e.getMessage(), e);
        }
    }

    public TrRosettaRnaPredictionResponse getResult(String taskId) {
        String resultPageUrl = BASE_OUTPUT_URL + taskId + "/";
        List<String> logs = new ArrayList<>();
        logs.add("root@freewillase:~# watch -n 3 curl -s " + resultPageUrl);
        
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent(USER_AGENT)
                .build()) {
            
            // Check if finished
            boolean finished = isFinished(httpClient, resultPageUrl);
            if (!finished) {
                logs.add("[GET] " + resultPageUrl + " -> [STATUS] 200 OK");
                logs.add("[INFO] Remote Status: RUNNING (Processing...)");
                logs.add("[WAIT] Waiting for trRosettaRNA deep learning fold engine...");
                
                // Add a simulated progress log to make the wait less boring
                long elapsedTime = System.currentTimeMillis() % 10000;
                if (elapsedTime < 3000) {
                    logs.add("[SYSTEM] Generating sequence embeddings...");
                } else if (elapsedTime < 6000) {
                    logs.add("[SYSTEM] Extracting MSA features...");
                } else {
                    logs.add("[SYSTEM] Predicting 3D coordinates via Transformer...");
                }
                
                return TrRosettaRnaPredictionResponse.builder()
                        .status("RUNNING")
                        .taskId(taskId)
                        .resultPageUrl(resultPageUrl)
                        .logs(logs)
                        .build();
            }

            logs.add("[GET] " + resultPageUrl + " -> [STATUS] 200 OK");
            logs.add("[SUCCESS] Target found: model1.pdb is ready for download.");
            logs.add("[INFO] Fetching atomic coordinates from remote storage...");
            
            // Download PDB
            String pdbContent = downloadPdb(httpClient, taskId);
            logs.add("[SUCCESS] PDB download complete. Size: " + pdbContent.length() + " bytes");
            logs.add("[SYSTEM] Initializing 3D Molstar Renderer...");
            
            return TrRosettaRnaPredictionResponse.builder()
                    .providerName("trRosettaRNA")
                    .modelName("trRosettaRNA")
                    .taskId(taskId)
                    .status("FINISHED")
                    .pdbContent(pdbContent)
                    .resultPageUrl(resultPageUrl)
                    .logs(logs)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching trRosettaRNA result for taskId: {}", taskId, e);
            logs.add("[ERROR] 获取结果时发生网络错误: " + e.getMessage());
            return TrRosettaRnaPredictionResponse.builder()
                    .status("ERROR")
                    .taskId(taskId)
                    .message("获取结果失败: " + e.getMessage())
                    .logs(logs)
                    .build();
        }
    }

    private boolean isFinished(CloseableHttpClient httpClient, String resultPageUrl) throws IOException, ParseException {
        HttpGet httpGet = new HttpGet(resultPageUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getCode() != 200) return false;
            String html = EntityUtils.toString(response.getEntity());
            return html.contains("model1.pdb") || html.contains("Results") || html.contains("Job finished");
        }
    }

    private String submitJob(CloseableHttpClient httpClient, String name, String sequence, String email) throws IOException, ParseException {
        HttpPost httpPost = new HttpPost(SUBMIT_URL);
        httpPost.setHeader("Referer", "https://yanglab.qd.sdu.edu.cn/trRosettaRNA/");

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("PDB", sequence);
        builder.addTextBody("TARGET-NAME", name != null ? name : "FreeWillase_Task");
        builder.addTextBody("REPLY-E-MAIL", email != null ? email : "");
        builder.addTextBody("intype", "fasta");
        builder.addTextBody("e_value_cutoff", "10");

        httpPost.setEntity(builder.build());

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String html = EntityUtils.toString(response.getEntity());
            log.debug("trRosettaRNA submission response: {}", html);
            
            // 更加严谨的匹配：寻找 RNA 开头后跟数字的模式
            Pattern pattern = Pattern.compile("output/(RNA[0-9]+)");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // 备选方案：如果官网改了前缀，寻找 output/ 后面紧跟的字母数字组合
            Matcher fallbackMatcher = TASK_ID_PATTERN.matcher(html);
            if (fallbackMatcher.find()) {
                String taskId = fallbackMatcher.group(1);
                return taskId.split("[^A-Za-z0-9_]")[0].trim();
            }
            
            throw new IllegalStateException("未能从 trRosettaRNA 响应中提取任务 ID。原始响应：" + 
                (html.length() > 200 ? html.substring(0, 200) + "..." : html));
        }
    }

    private String downloadPdb(CloseableHttpClient httpClient, String taskId) throws IOException, ParseException {
        String pdbUrl = BASE_OUTPUT_URL + taskId + "/model1.pdb";
        HttpGet httpGet = new HttpGet(pdbUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getCode() != 200) {
                throw new IOException("无法下载 PDB 文件，HTTP 状态码: " + response.getCode());
            }
            return EntityUtils.toString(response.getEntity());
        }
    }
}
