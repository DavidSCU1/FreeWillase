package com.freewillase.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RnaFoldPredictionResponse {
    private String providerName;
    private String modelName;
    private String format;
    private String structure;
    private String sequence;
    private String resultPageUrl;
    private String mfeStructure;
    private Double mfeEnergy;
    private Double ensembleFreeEnergy;
    private Double mfeFrequency;
    private Double ensembleDiversity;
    private String centroidStructure;
    private Double centroidEnergy;
}
