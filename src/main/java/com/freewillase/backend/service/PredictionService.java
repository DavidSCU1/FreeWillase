package com.freewillase.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freewillase.backend.dto.MiniFoldPredictionRequest;
import com.freewillase.backend.dto.MiniFoldPredictionResponse;
import com.freewillase.backend.dto.NvidiaPredictionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 预测业务编排层。
 *
 * <p>这个类承担两类职责：</p>
 * <ul>
 *   <li>本地任务：MiniFold 通过启动项目内置的 Python worker 执行，结果落盘（taskDir），前端通过轮询读取日志/结果。</li>
 *   <li>云端任务：NVIDIA ESMFold 通过 RestTemplate 调上游接口，直接把响应透传给前端。</li>
 * </ul>
 *
 * <p>注意：API Key 等敏感信息不会写入日志，读取来源为“用户私有 env 文件 > 系统环境变量”。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private static final String BOOST_MANAGED_PYTHON = "D:\\Program Files (x86)\\Boost\\python.exe";
    private static final String NVIDIA_DEFAULT_BASE_URL = "https://health.api.nvidia.com";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final UserAiConfigService userAiConfigService;

    /**
     * 提交项目内置 MiniFold 预测任务。
     *
     * <p>实现要点：</p>
     * <ul>
     *   <li>为每次提交生成 taskId，对应一个独立 taskDir（避免任务间互相覆盖）。</li>
     *   <li>将请求参数序列化为 request.json（便于复现与排查）。</li>
     *   <li>拼出 Python worker 命令并异步启动，日志输出到 launcher.log。</li>
     * </ul>
     */
    public MiniFoldPredictionResponse predictWithMiniFold(MiniFoldPredictionRequest request, String username) {
        // taskId 用于前端轮询、文件落盘目录名，以及日志/结果的唯一定位
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Path taskDir = getTaskDir(taskId);

        log.info("Submitting embedded MiniFold task {} for sequence length {}", taskId,
                request.getSequence() != null ? request.getSequence().length() : 0);
        log.info("Params: targetChains={}, useIgpu={}, backend={}, condaEnvName={}, envText={}",
                request.getTargetChains(),
                request.getUseIgpu(),
                request.getBackend(),
                request.getCondaEnvName(),
                request.getEnvText());

        try {
            // 1) 创建任务目录：minifold_runtime/tasks/<taskId>
            Files.createDirectories(taskDir);
            // 2) 构建 worker 需要的 payload（用于 Python 侧读取）
            Map<String, Object> payload = buildPayload(request);
            Path payloadPath = taskDir.resolve("request.json");
            // 3) 将 payload 落盘，方便之后定位问题（例如参数是否被错误传递）
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(payloadPath.toFile(), payload);

            // 4) 分子类型决定使用哪个 worker（蛋白 worker.py / RNA worker_rna.py）
            String moleculeType = defaultString(request.getMoleculeType(), "protein");
            // 5) 解析 Python 启动方式：支持用户指定 python.exe、conda env、环境变量 MINIFOLD_PYTHON、或自动探测
            List<String> command = new ArrayList<>(resolvePythonCommand(request));
            command.add("-u");
            command.add(getWorkerScript(moleculeType).toString());
            command.add("--task-dir");
            command.add(taskDir.toString());
            command.add("--payload");
            command.add(payloadPath.toString());

            log.info("Launching MiniFold command for task {}: {}", taskId, command);

            // 6) 启动子进程并将 stdout/stderr 合并重定向到 launcher.log（避免控制台被占用，也便于前端轮询）
            Path launchLog = taskDir.resolve("launcher.log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(getProjectRoot().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(launchLog.toFile());
            // 7) 确保 Python 输出编码为 UTF-8，避免 Windows 下日志乱码
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            builder.environment().put("PYTHONUTF8", "1");
            // 8) 将当前用户的私有 env 文件路径传给 Python（实现按用户隔离的 Ark/NVIDIA 等配置读取）
            userAiConfigService.resolveUserEnvFile(username)
                    .ifPresent(path -> builder.environment().put("FREEWILLASE_USER_ENV_FILE", path.toString()));

            // 9) 异步启动，不阻塞 HTTP 请求线程；结果由 Python 写回 result.json，前端轮询读取
            builder.start();

            return MiniFoldPredictionResponse.builder()
                    .taskId(taskId)
                    .status("running")
                    .build();
        } catch (Exception e) {
            log.error("Failed to start embedded MiniFold task {}", taskId, e);
            throw new RuntimeException("无法启动项目内置 MiniFold 进程: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 NVIDIA ESMFold 进行蛋白结构预测。
     *
     * <p>交互模型：同步请求，直接返回 NVIDIA 的响应 body（可能是 JSON，也可能是纯文本 PDB）。</p>
     * <p>这里选择 ResponseEntity&lt;String&gt; 是为了保留上游的 Content-Type，便于前端区分解析策略。</p>
     */
    public ResponseEntity<String> predictWithNvidia(NvidiaPredictionRequest request, String username) {
        // 前端可能传入 FASTA/Plain，后端这里只做最小规范化：去除两端空白
        String sequence = defaultString(request.getSequence()).trim();
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("序列不能为空");
        }

        // NVIDIA 凭证按用户读取：优先用户私有 env 文件，其次系统环境变量
        String apiKey = userAiConfigService.resolveProviderValue(username, "NVIDIA_API_KEY")
                .orElseThrow(() -> new IllegalArgumentException("当前账号尚未配置 NVIDIA API Key，请先在页面弹窗中填写"));
        // baseUrl 允许用户覆盖（例如企业内网转发或代理），未配置则使用默认 NVIDIA health api
        String baseUrl = userAiConfigService.resolveProviderValue(username, "NVIDIA_API_URL")
                .orElse(NVIDIA_DEFAULT_BASE_URL);
        // endpoint 拼接时避免双斜杠：trimTrailingSlash 统一把 baseUrl 尾部的 / 去掉
        String endpoint = trimTrailingSlash(baseUrl) + "/v1/biology/nvidia/esmfold";

        HttpHeaders headers = new HttpHeaders();
        // Bearer token 不写入日志；这里只设置 header
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Accept 同时允许 JSON / 纯文本 / 任意：兼容 NVIDIA 不同返回格式
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));

        Map<String, String> payload = Map.of("sequence", sequence);

        try {
            // 通过 RestTemplate 直接请求上游 NVIDIA 服务
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            // Content-Type 透传给前端：前端可根据 Content-Type 或 body 形态决定 JSON/PDB 的解析策略
            MediaType contentType = response.getHeaders().getContentType();
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(contentType != null ? contentType : MediaType.TEXT_PLAIN)
                    .body(response.getBody());
        } catch (HttpStatusCodeException e) {
            // 上游返回非 2xx 时，尽量把响应 body 带回给用户（通常包含更可读的失败原因）
            String detail = e.getResponseBodyAsString(StandardCharsets.UTF_8).trim();
            String message = detail.isEmpty()
                    ? "NVIDIA ESMFold 请求失败: " + e.getStatusCode().value()
                    : "NVIDIA ESMFold 请求失败: " + detail;
            // 抛 IllegalArgumentException 让统一异常处理返回 400，并在前端工作台直接展示 message
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 读取 MiniFold 任务日志（用于前端轮询展示）。
     *
     * <p>优先返回 process.log（worker 输出），其次返回 launcher.log（启动器输出）。</p>
     */
    public String getMiniFoldLogs(String taskId) {
        try {
            Path taskDir = getTaskDir(taskId);
            if (!Files.exists(taskDir)) {
                return "任务不存在: " + taskId;
            }

            Path processLog = taskDir.resolve("process.log");
            if (Files.exists(processLog)) {
                return Files.readString(processLog, StandardCharsets.UTF_8);
            }

            Path launcherLog = taskDir.resolve("launcher.log");
            if (Files.exists(launcherLog)) {
                return Files.readString(launcherLog, StandardCharsets.UTF_8);
            }

            return "";
        } catch (Exception e) {
            log.error("Failed to fetch logs for task {}", taskId, e);
            return "无法获取日志: " + e.getMessage();
        }
    }

    /**
     * 读取 MiniFold 任务结果（用于前端轮询）。
     *
     * <p>Python worker 完成后会在 taskDir 写入 result.json；未生成则视为仍在运行。</p>
     */
    public MiniFoldPredictionResponse getMiniFoldResult(String taskId) {
        try {
            Path taskDir = getTaskDir(taskId);
            if (!Files.exists(taskDir)) {
                return MiniFoldPredictionResponse.builder()
                        .status("failed")
                        .error("任务不存在: " + taskId)
                        .build();
            }

            Path resultPath = taskDir.resolve("result.json");
            if (!Files.exists(resultPath)) {
                return MiniFoldPredictionResponse.builder()
                        .taskId(taskId)
                        .status("running")
                        .build();
            }

            MiniFoldPredictionResponse response = objectMapper.readValue(resultPath.toFile(), MiniFoldPredictionResponse.class);
            if (response.getTaskId() == null || response.getTaskId().isBlank()) {
                response.setTaskId(taskId);
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch result for task {}", taskId, e);
            return MiniFoldPredictionResponse.builder()
                    .taskId(taskId)
                    .status("failed")
                    .error("获取结果失败: " + e.getMessage())
                    .build();
        }
    }

    private Map<String, Object> buildPayload(MiniFoldPredictionRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("moleculeType", defaultString(request.getMoleculeType(), "protein"));
        payload.put("sequence", request.getSequence());
        payload.put("envText", defaultString(request.getEnvText()));
        payload.put("targetChains", request.getTargetChains());
        payload.put("useIgpu", Boolean.TRUE.equals(request.getUseIgpu()));
        payload.put("backend", request.getBackend() != null && !request.getBackend().isBlank()
                ? request.getBackend()
                : (Boolean.TRUE.equals(request.getUseIgpu()) ? "auto" : "cpu"));
        payload.put("condaEnvName", defaultString(request.getCondaEnvName()));
        return payload;
    }

    private List<String> resolvePythonCommand(MiniFoldPredictionRequest request) {
        String pythonOrEnv = stripWrappingQuotes(defaultString(request.getCondaEnvName()).trim());
        if (!pythonOrEnv.isEmpty()) {
            List<String> directPython = resolveDirectPythonCommand(pythonOrEnv);
            if (directPython != null) {
                if (isPythonAvailable(directPython)) {
                    return directPython;
                }
                throw new IllegalStateException("无法使用指定的 Python 可执行文件 `" + pythonOrEnv
                        + "`，请确认路径存在且可运行 `python --version`");
            }

            List<String> condaCommand = buildCondaPythonCommand(pythonOrEnv);
            if (isPythonAvailable(condaCommand)) {
                return condaCommand;
            }

            String detail = isCondaAvailable()
                    ? "请确认环境名是否存在且可运行 `python`"
                    : "请确认当前系统可直接调用 conda";
            throw new IllegalStateException("无法使用指定的 Conda 环境 `" + pythonOrEnv + "`，" + detail);
        }

        String configured = stripWrappingQuotes(defaultString(System.getenv("MINIFOLD_PYTHON")).trim());
        if (!configured.isEmpty()) {
            List<String> configuredCommand = List.of(configured);
            if (isPythonAvailable(configuredCommand)) {
                return configuredCommand;
            }
            log.warn("Configured MINIFOLD_PYTHON is unavailable, falling back to system discovery: {}", configured);
        }

        List<String> managedCommand = resolveManagedPythonCommand();
        if (managedCommand != null) {
            return managedCommand;
        }

        List<List<String>> candidates = List.of(
                List.of("python"),
                List.of("python3"),
                List.of("py", "-3")
        );

        for (List<String> candidate : candidates) {
            if (isPythonAvailable(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("未找到可用的 Python 解释器，请安装 Python 或设置环境变量 MINIFOLD_PYTHON");
    }

    private List<String> resolveManagedPythonCommand() {
        for (Path candidate : getManagedPythonCandidates()) {
            if (candidate == null || !Files.exists(candidate)) {
                continue;
            }

            List<String> directCommand = List.of(candidate.toString());
            if (isPythonAvailable(directCommand)) {
                log.info("Using managed MiniFold Python runtime: {}", candidate);
                return directCommand;
            }

            log.warn("Managed MiniFold Python exists but is unavailable: {}", candidate);
        }
        return null;
    }

    private List<Path> getManagedPythonCandidates() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(getRuntimeRoot().resolve("envs").resolve("rna-conda").resolve("python.exe"));
        candidates.add(getRuntimeRoot().resolve("python-portable").resolve("python.exe"));
        candidates.add(Paths.get(BOOST_MANAGED_PYTHON));
        return candidates;
    }

    private List<String> resolveDirectPythonCommand(String value) {
        if (!looksLikePythonPath(value)) {
            return null;
        }
        return List.of(value);
    }

    private List<String> buildCondaPythonCommand(String condaEnvName) {
        if (isWindows()) {
            return List.of("cmd.exe", "/c", "conda", "run", "-n", condaEnvName, "python");
        }
        return List.of("conda", "run", "-n", condaEnvName, "python");
    }

    private boolean isCondaAvailable() {
        if (isWindows()) {
            return isCommandSuccessful(List.of("cmd.exe", "/c", "conda", "--version"));
        }
        return isCommandSuccessful(List.of("conda", "--version"));
    }

    private boolean isPythonAvailable(List<String> command) {
        List<String> probe = new ArrayList<>(command);
        probe.add("--version");
        return isCommandSuccessful(probe);
    }

    private boolean isCommandSuccessful(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean looksLikePythonPath(String value) {
        String normalized = value.toLowerCase();
        boolean hasWindowsDrivePrefix = normalized.length() > 1
                && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':';
        return normalized.contains("\\")
                || normalized.contains("/")
                || normalized.endsWith(".exe")
                || hasWindowsDrivePrefix;
    }

    private Path getProjectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private Path getRuntimeRoot() {
        return getProjectRoot().resolve("minifold_runtime");
    }

    private Path getWorkerScript(String moleculeType) {
        String scriptName = "RNA".equalsIgnoreCase(moleculeType) ? "worker_rna.py" : "worker.py";
        Path worker = getRuntimeRoot().resolve(scriptName);
        if (!Files.exists(worker)) {
            throw new IllegalStateException("未找到内置 MiniFold worker: " + worker);
        }
        return worker;
    }

    private Path getTaskDir(String taskId) {
        return getRuntimeRoot().resolve("tasks").resolve(taskId);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return defaultString(value);
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private String trimTrailingSlash(String value) {
        String normalized = defaultString(value).trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
