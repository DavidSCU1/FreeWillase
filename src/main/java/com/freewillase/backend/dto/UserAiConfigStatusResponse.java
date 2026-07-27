package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiConfigStatusResponse {
    private UserAiProviderStatus minifold;
    private UserAiProviderStatus nvidia;
}
