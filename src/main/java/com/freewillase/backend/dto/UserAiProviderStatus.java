package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiProviderStatus {
    private boolean configured;
    private boolean userScopedFilePresent;
    private List<String> requiredKeys;
    private List<String> optionalKeys;
    private List<String> configuredKeys;
    private List<String> missingKeys;
}
