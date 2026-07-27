package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteratureScanStatusResponse {
    private String status;
    private String message;
    private String scope;
    private boolean apiKeyEnabled;
    private int totalEnzymes;
    private int processedEnzymes;
    private int discoveredCandidates;
    private int failedEnzymes;
    private Long currentEnzymeId;
    private String currentAccession;
    private String currentEnzymeName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime lastHeartbeatAt;
}
