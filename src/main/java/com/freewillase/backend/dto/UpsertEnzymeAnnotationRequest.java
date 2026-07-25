package com.freewillase.backend.dto;

import lombok.Data;

@Data
public class UpsertEnzymeAnnotationRequest {
    private String annotationType;
    private String title;
    private Integer startResidue;
    private Integer endResidue;
    private String chainLabel;
    private String mutationLabel;
    private String colorHex;
    private String description;
}
