package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveCloudPredictionRequest {
    private String provider;
    private String name;
    private String sequence;
    private String pdb;
    private String taskId;
    private String moleculeType;
}