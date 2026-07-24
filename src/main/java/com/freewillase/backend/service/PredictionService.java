package com.freewillase.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freewillase.backend.dto.MiniFoldPredictionRequest;
import com.freewillase.backend.dto.MiniFoldPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final ObjectMapper objectMapper;

    public MiniFoldPredictionResponse predictWithMiniFold(MiniFoldPredictionRequest request) {
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
            Files.createDirectories(taskDir);
            Map<String, Object> payload = buildPayload(request);
            Path payloadPath = taskDir.resolve("request.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(payloadPath.toFile(), payload);

            List<String> command = new ArrayList<>(resolvePythonCommand(request));
            command.add("-u");
            command.add(getWorkerScript().toString());
            command.add("--task-dir");
            command.add(taskDir.toString());
            command.add("--payload");
            command.add(payloadPath.toString());

            Path launchLog = taskDir.resolve("launcher.log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(getProjectRoot().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(launchLog.toFile());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            builder.environment().put("PYTHONUTF8", "1");

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
        String condaEnvName = defaultString(request.getCondaEnvName()).trim();
        if (!condaEnvName.isEmpty()) {
            List<String> condaCommand = buildCondaPythonCommand(condaEnvName);
            if (isPythonAvailable(condaCommand)) {
                return condaCommand;
            }

            String detail = isCondaAvailable()
                    ? "请确认环境名是否存在且可运行 `python`"
                    : "请确认当前系统可直接调用 conda";
            throw new IllegalStateException("无法使用指定的 Conda 环境 `" + condaEnvName + "`，" + detail);
        }

        String configured = System.getenv("MINIFOLD_PYTHON");
        if (configured != null && !configured.isBlank()) {
            return List.of(configured.trim());
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

    private Path getProjectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private Path getRuntimeRoot() {
        return getProjectRoot().resolve("minifold_runtime");
    }

    private Path getWorkerScript() {
        Path worker = getRuntimeRoot().resolve("worker.py");
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
}
