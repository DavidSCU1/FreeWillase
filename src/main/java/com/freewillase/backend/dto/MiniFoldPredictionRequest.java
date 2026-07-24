package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniFoldPredictionRequest {
    private String sequence;
    private String envText;
    private Integer targetChains;
    private Boolean useIgpu;
    private String backend;
}
