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

@Service
public class UserAiConfigService {

    private static final String PROVIDER_MINIFOLD = "minifold";
    private static final String PROVIDER_NVIDIA = "nvidia";
    private static final String ARK_API_KEY = "ARK_API_KEY";
    private static final String ARK_API_URL = "ARK_API_URL";
    private static final String NVIDIA_API_KEY = "NVIDIA_API_KEY";
    private static final String NVIDIA_API_URL = "NVIDIA_API_URL";

    public UserAiConfigStatusResponse getStatus(String username) {
        return UserAiConfigStatusResponse.builder()
                .minifold(buildProviderStatus(username, PROVIDER_MINIFOLD))
                .nvidia(buildProviderStatus(username, PROVIDER_NVIDIA))
                .build();
    }

    public UserAiConfigStatusResponse save(String username, UserAiConfigSaveRequest request) {
        String provider = normalizeProvider(request.getProvider());
        if (request.getApiKey() == null || request.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("请先填写 API Key");
        }

        Map<String, String> updates = new LinkedHashMap<>();
        if (PROVIDER_MINIFOLD.equals(provider)) {
            updates.put(ARK_API_KEY, sanitizeEnvValue(request.getApiKey()));
            // Ark URL is fixed by the project runtime, so we explicitly remove any user override.
            updates.put(ARK_API_URL, null);
        } else {
            updates.put(NVIDIA_API_KEY, sanitizeEnvValue(request.getApiKey()));
            updates.put(NVIDIA_API_URL, sanitizeOptionalEnvValue(request.getBaseUrl()));
        }

        writeUserEnv(username, updates);
        return getStatus(username);
    }

    public Optional<String> resolveProviderValue(String username, String key) {
        if (ARK_API_URL.equals(key)) {
            return Optional.ofNullable(trimToNull(System.getenv(key)));
        }
        Map<String, String> userValues = readUserEnv(username);
        String userValue = trimToNull(userValues.get(key));
        if (userValue != null) {
            return Optional.of(userValue);
        }
        return Optional.ofNullable(trimToNull(System.getenv(key)));
    }

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
            Files.createDirectories(envDir);

            Path envFile = getUserEnvFile(username);
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
