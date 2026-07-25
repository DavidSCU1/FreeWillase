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
public class TrRosettaRnaPredictionResponse {
    private String providerName;
    private String modelName;
    private String taskId;
    private String status;
    private String pdbContent;
    private String resultPageUrl;
    private String message;
    private List<String> logs;
}
