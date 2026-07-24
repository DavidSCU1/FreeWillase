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
public class MiniFoldPredictionResponse {
    private String taskId;
    private String status;
    private String pdb;
    private String analysis;
    private List<String> chains;
    private String error;
}
