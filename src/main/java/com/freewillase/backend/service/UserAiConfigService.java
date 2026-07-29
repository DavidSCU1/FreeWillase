package com.freewillase.backend.service;

import com.freewillase.backend.dto.UserAiConfigSaveRequest;
import com.freewillase.backend.dto.UserAiConfigStatusResponse;
import com.freewillase.backend.dto.UserAiProviderStatus;
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
import java.util.Optional;

/**
 * 用户级 AI 配置服务（按账号隔离）。
 *
 * <p>核心目标：</p>
 * <ul>
 *   <li>不同用户可以在同一套部署里配置自己的 NVIDIA API Key / URL，不互相影响。</li>
 *   <li>MiniFold（Ark）同样按用户保存 Key，但 URL 由运行时固定，不允许用户覆盖。</li>
 * </ul>
 *
 * <p>存储方式：</p>
 * <ul>
 *   <li>写入项目目录下的 <code>.freewillase/user-env/&lt;username&gt;.env.local</code>（非 Git 版本文件）。</li>
 *   <li>读取优先级：用户私有文件 &gt; 系统环境变量。</li>
 * </ul>
 */
@Service
public class UserAiConfigService {

    private static final String PROVIDER_MINIFOLD = "minifold";
    private static final String PROVIDER_NVIDIA = "nvidia";
    private static final String ARK_API_KEY = "ARK_API_KEY";
    private static final String ARK_API_URL = "ARK_API_URL";
    private static final String NVIDIA_API_KEY = "NVIDIA_API_KEY";
    private static final String NVIDIA_API_URL = "NVIDIA_API_URL";

    /**
     * 返回当前用户各 provider 的配置状态，用于前端判断是否需要弹窗提示填写。
     */
    public UserAiConfigStatusResponse getStatus(String username) {
        return UserAiConfigStatusResponse.builder()
                .minifold(buildProviderStatus(username, PROVIDER_MINIFOLD))
                .nvidia(buildProviderStatus(username, PROVIDER_NVIDIA))
                .build();
    }

    /**
     * 保存当前用户的 AI 配置（API Key / 可选 baseUrl）。
     *
     * <p>注意：这里不会把 key 写入数据库，而是写入用户私有 env 文件。</p>
     */
    public UserAiConfigStatusResponse save(String username, UserAiConfigSaveRequest request) {
        // provider 标准化：只允许 minifold / nvidia
        String provider = normalizeProvider(request.getProvider());
        if (request.getApiKey() == null || request.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("请先填写 API Key");
        }

        // updates 表示本次要写入/删除的键值对，最终会合并到用户的 env 文件中
        Map<String, String> updates = new LinkedHashMap<>();
        if (PROVIDER_MINIFOLD.equals(provider)) {
            // MiniFold 走 Ark：只需要保存 ARK_API_KEY
            updates.put(ARK_API_KEY, sanitizeEnvValue(request.getApiKey()));
            // Ark URL 由项目运行时固定，不允许用户覆盖；因此显式删除用户可能写入的 ARK_API_URL
            updates.put(ARK_API_URL, null);
        } else {
            // NVIDIA：保存 Key，并允许用户可选覆盖 baseUrl（例如代理/内网转发）
            updates.put(NVIDIA_API_KEY, sanitizeEnvValue(request.getApiKey()));
            updates.put(NVIDIA_API_URL, sanitizeOptionalEnvValue(request.getBaseUrl()));
        }

        // 写入用户私有 env 文件
        writeUserEnv(username, updates);
        // 保存成功后返回最新状态，方便前端立即刷新“已配置/未配置”的展示
        return getStatus(username);
    }

    /**
     * 读取某个配置项的值。
     *
     * <p>优先级：用户私有 env 文件 &gt; 系统环境变量。</p>
     * <p>特殊规则：ARK_API_URL 只允许从系统环境变量读取（避免用户绕过固定 URL 约束）。</p>
     */
    public Optional<String> resolveProviderValue(String username, String key) {
        if (ARK_API_URL.equals(key)) {
            return Optional.ofNullable(trimToNull(System.getenv(key)));
        }
        // 先读用户私有文件
        Map<String, String> userValues = readUserEnv(username);
        String userValue = trimToNull(userValues.get(key));
        if (userValue != null) {
            return Optional.of(userValue);
        }
        // 再读系统环境变量（用于服务端统一配置的兜底）
        return Optional.ofNullable(trimToNull(System.getenv(key)));
    }

    /**
     * 返回当前用户私有 env 文件路径（如果存在）。
     *
     * <p>用途：例如在启动 MiniFold Python 子进程时，将该路径通过环境变量传给 worker。</p>
     */
    public Optional<Path> resolveUserEnvFile(String username) {
        Path path = getUserEnvFile(username);
        return Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    private UserAiProviderStatus buildProviderStatus(String username, String provider) {
        List<String> requiredKeys = getRequiredKeys(provider);
        List<String> optionalKeys = getOptionalKeys(provider);
        List<String> configuredKeys = new ArrayList<>();
        List<String> missingKeys = new ArrayList<>();

        for (String key : requiredKeys) {
            if (resolveProviderValue(username, key).isPresent()) {
                configuredKeys.add(key);
            } else {
                missingKeys.add(key);
            }
        }

        for (String key : optionalKeys) {
            if (resolveProviderValue(username, key).isPresent()) {
                configuredKeys.add(key);
            }
        }

        return UserAiProviderStatus.builder()
                .configured(missingKeys.isEmpty())
                .userScopedFilePresent(Files.exists(getUserEnvFile(username)))
                .requiredKeys(requiredKeys)
                .optionalKeys(optionalKeys)
                .configuredKeys(configuredKeys)
                .missingKeys(missingKeys)
                .build();
    }

    private void writeUserEnv(String username, Map<String, String> updates) {
        try {
            Path envDir = getUserEnvRoot();
            // 确保目录存在：.freewillase/user-env
            Files.createDirectories(envDir);

            Path envFile = getUserEnvFile(username);
            // 合并“已有配置 + 本次 updates”，并支持通过 value=null 来删除某个 key
            Map<String, String> next = new LinkedHashMap<>(readUserEnv(username));
            for (Map.Entry<String, String> entry : updates.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    next.remove(entry.getKey());
                } else {
                    next.put(entry.getKey(), entry.getValue());
                }
            }

            List<String> lines = new ArrayList<>();
            lines.add("# FreeWillase per-user AI config");
            lines.add("# This file is generated and updated by the application.");
            for (Map.Entry<String, String> entry : next.entrySet()) {
                // 写成 KEY=VALUE 形式；必要时对 value 加引号（包含空格/# 的情况）
                lines.add(entry.getKey() + "=" + quoteIfNeeded(entry.getValue()));
            }
            Files.write(envFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写入用户 AI 配置失败: " + e.getMessage(), e);
        }
    }

    private Map<String, String> readUserEnv(String username) {
        Path envFile = getUserEnvFile(username);
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(envFile)) {
            return values;
        }

        try {
            // 逐行解析 env 文件，忽略空行、注释行，允许 VALUE 使用单双引号包裹
            for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim() : "";
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
            return values;
        } catch (IOException e) {
            throw new RuntimeException("读取用户 AI 配置失败: " + e.getMessage(), e);
        }
    }

    private Path getUserEnvRoot() {
        return getProjectRoot().resolve(".freewillase").resolve("user-env");
    }

    private Path getUserEnvFile(String username) {
        return getUserEnvRoot().resolve(sanitizeUsername(username) + ".env.local");
    }

    private Path getProjectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private String sanitizeUsername(String username) {
        String normalized = trimToNull(username);
        if (normalized == null) {
            return "anonymous";
        }
        // 防止用户名中出现路径分隔符等非法字符，避免写文件时路径穿越
        return normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String normalizeProvider(String provider) {
        String normalized = trimToNull(provider);
        if (PROVIDER_MINIFOLD.equalsIgnoreCase(normalized)) {
            return PROVIDER_MINIFOLD;
        }
        if (PROVIDER_NVIDIA.equalsIgnoreCase(normalized)) {
            return PROVIDER_NVIDIA;
        }
        throw new IllegalArgumentException("不支持的 AI 配置类型: " + provider);
    }

    private List<String> getRequiredKeys(String provider) {
        if (PROVIDER_MINIFOLD.equals(provider)) {
            return List.of(ARK_API_KEY);
        }
        return List.of(NVIDIA_API_KEY);
    }

    private List<String> getOptionalKeys(String provider) {
        if (PROVIDER_MINIFOLD.equals(provider)) {
            return List.of();
        }
        return List.of(NVIDIA_API_URL);
    }

    private String sanitizeEnvValue(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("配置值不能为空");
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            throw new IllegalArgumentException("配置值中不能包含换行");
        }
        return trimmed;
    }

    private String sanitizeOptionalEnvValue(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            throw new IllegalArgumentException("配置值中不能包含换行");
        }
        return trimmed;
    }

    private String quoteIfNeeded(String value) {
        if (value.contains(" ") || value.contains("#")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
