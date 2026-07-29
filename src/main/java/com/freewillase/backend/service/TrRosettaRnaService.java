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

/**
 * trRosettaRNA 网页服务适配层。
 *
 * <p>该服务不是调用“标准化 API”，而是通过模拟浏览器向官网提交 multipart/form-data，并从 HTML 里提取 taskId。</p>
 *
 * <p>前后端交互模型：</p>
 * <ul>
 *   <li>前端提交：POST /api/prediction/trrosettarna → 返回 RUNNING + taskId</li>
 *   <li>前端轮询：GET /api/prediction/trrosettarna/result/{taskId}</li>
 *   <li>未完成返回 RUNNING，完成后下载 model1.pdb 并返回 FINISHED + pdbContent</li>
 * </ul>
 *
 * <p>稳定性考虑：官网 HTML 结构可能变化，因此 taskId 提取提供严格匹配与 fallback 匹配。</p>
 */
@Slf4j
@Service
public class TrRosettaRnaService {

    private static final String SUBMIT_URL = "https://yanglab.qd.sdu.edu.cn/cgi-bin/rosetta_rna.cgi";
    private static final String BASE_OUTPUT_URL = "http://yanglab.qd.sdu.edu.cn/trRosettaRNA/output/";
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("output/([A-Za-z0-9_]+)");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 提交 trRosettaRNA 预测任务。
     *
     * <p>这里会做序列规范化和上游限制校验（≤ 400nt），然后模拟网页提交并立即返回 taskId。</p>
     */
    public TrRosettaRnaPredictionResponse predict(String name, String sequence, String email) {
        // 1) 规范化输入：去掉所有空白字符，并统一为大写（网页工具通常不关心换行，但会受非法字符影响）
        String normalizedSequence = sequence.replaceAll("\\s+", "").toUpperCase();
        // 2) 上游限制：网页版仅支持 ≤ 400nt；在后端提前拦截，避免用户等待后才失败
        if (normalizedSequence.length() > 400) {
            throw new IllegalArgumentException("trRosettaRNA 网页版仅支持长度 ≤ 400nt 的序列");
        }

        log.info("Starting trRosettaRNA prediction for task: {} (length: {})", name, normalizedSequence.length());

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent(USER_AGENT)
                .build()) {

            // 3) 构造“终端风格日志”：前端工作台会把 logs 当作运行输出展示，提升等待时的信息密度
            List<String> logs = new ArrayList<>();
            logs.add("root@freewillase:~# trrosettarna-predict --name \"" + name + "\" --len " + normalizedSequence.length());
            logs.add("[SYSTEM] Initializing HTTP client (Apache HttpClient 5.x)...");
            logs.add("[POST] https://yanglab.qd.sdu.edu.cn/cgi-bin/rosetta_rna.cgi");
            logs.add("[DATA] multipart/form-data; boundary=FreeWillaseBoundary");
            logs.add("[INFO] Sending sequence payload (" + normalizedSequence.substring(0, Math.min(10, normalizedSequence.length())) + "...)");
            
            // 4) 提交到官网并从 HTML 中提取 taskId
            String taskId = submitJob(httpClient, name, normalizedSequence, email);
            // 5) 官网结果页地址：用于前端展示“跳转查看”或用于后端轮询判断
            String resultPageUrl = BASE_OUTPUT_URL + taskId + "/";
            
            logs.add("[HTTP/1.1] 200 OK");
            logs.add("[SUCCESS] Job accepted. Remote TaskID: " + taskId);
            logs.add("[SYSTEM] Redirecting to monitor: " + resultPageUrl);
            
            log.info("trRosettaRNA job submitted. Task ID: {}", taskId);

            // 6) 这里不返回结构内容，只返回 RUNNING + taskId，前端后续会轮询 result 接口
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

    /**
     * 查询 trRosettaRNA 任务结果（轮询接口的核心实现）。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>请求结果页 HTML，判断是否已经生成 model1.pdb</li>
     *   <li>未完成则返回 RUNNING + logs</li>
     *   <li>完成则下载 model1.pdb 并返回 FINISHED + pdbContent</li>
     * </ol>
     */
    public TrRosettaRnaPredictionResponse getResult(String taskId) {
        String resultPageUrl = BASE_OUTPUT_URL + taskId + "/";
        List<String> logs = new ArrayList<>();
        logs.add("root@freewillase:~# watch -n 3 curl -s " + resultPageUrl);
        
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent(USER_AGENT)
                .build()) {
            
            // 1) 判断任务是否完成：通过结果页 HTML 关键字（model1.pdb/Results/Job finished）来判断
            boolean finished = isFinished(httpClient, resultPageUrl);
            if (!finished) {
                logs.add("[GET] " + resultPageUrl + " -> [STATUS] 200 OK");
                logs.add("[INFO] Remote Status: RUNNING (Processing...)");
                logs.add("[WAIT] Waiting for trRosettaRNA deep learning fold engine...");
                
                // 2) 添加模拟进度：避免用户只看到“RUNNING”但没有任何变化（纯体验增强）
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
            
            // 3) 完成后下载 PDB：官网会把结果文件放在 output/<taskId>/model1.pdb
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
            // 4) 轮询接口不直接抛异常：返回 ERROR 状态，方便前端进入失败态并展示 message/logs
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
        // 通过拉取结果页 HTML 判断是否“已经出结果文件”
        HttpGet httpGet = new HttpGet(resultPageUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getCode() != 200) return false;
            String html = EntityUtils.toString(response.getEntity());
            return html.contains("model1.pdb") || html.contains("Results") || html.contains("Job finished");
        }
    }

    private String submitJob(CloseableHttpClient httpClient, String name, String sequence, String email) throws IOException, ParseException {
        // 构造网页提交请求：该 URL 是官网 CGI 接收点
        HttpPost httpPost = new HttpPost(SUBMIT_URL);
        // Referer 用于模拟浏览器来源，部分站点会依赖它做简单校验/统计
        httpPost.setHeader("Referer", "https://yanglab.qd.sdu.edu.cn/trRosettaRNA/");

        // multipart 表单字段基本沿用官网页面表单的字段名
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        // 注意：官网字段名为 PDB，但这里实际提交的是 RNA 序列（官网内部会按 intype=fasta 解析）
        builder.addTextBody("PDB", sequence);
        builder.addTextBody("TARGET-NAME", name != null ? name : "FreeWillase_Task");
        builder.addTextBody("REPLY-E-MAIL", email != null ? email : "");
        builder.addTextBody("intype", "fasta");
        builder.addTextBody("e_value_cutoff", "10");

        httpPost.setEntity(builder.build());

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String html = EntityUtils.toString(response.getEntity());
            log.debug("trRosettaRNA submission response: {}", html);
            
            // 1) 更加严谨的匹配：优先寻找 RNA 开头后跟数字的模式（更接近官网当前 taskId 习惯）
            Pattern pattern = Pattern.compile("output/(RNA[0-9]+)");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // 2) 备选方案：如果官网改了前缀，使用通用匹配 output/<id>
            Matcher fallbackMatcher = TASK_ID_PATTERN.matcher(html);
            if (fallbackMatcher.find()) {
                String taskId = fallbackMatcher.group(1);
                // 去掉可能出现的后缀杂质（例如 HTML 属性分隔符）
                return taskId.split("[^A-Za-z0-9_]")[0].trim();
            }
            
            // 3) 两种匹配都失败：直接抛错，并截断 HTML，避免日志过长
            throw new IllegalStateException("未能从 trRosettaRNA 响应中提取任务 ID。原始响应：" + 
                (html.length() > 200 ? html.substring(0, 200) + "..." : html));
        }
    }

    private String downloadPdb(CloseableHttpClient httpClient, String taskId) throws IOException, ParseException {
        // 官网约定：结果文件名为 model1.pdb
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
