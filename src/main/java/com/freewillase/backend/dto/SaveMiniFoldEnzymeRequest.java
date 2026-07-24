package com.freewillase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMiniFoldEnzymeRequest {
    private String name;
    private String sequence;
    private String pdb;
    private String taskId;
    private String envText;
    private Integer targetChains;
    private String backend;
    private Boolean useAcceleration;
}
