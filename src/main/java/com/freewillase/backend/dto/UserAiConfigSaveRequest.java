package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAiConfigSaveRequest {
    private String provider;
    private String apiKey;
    private String baseUrl;
}
