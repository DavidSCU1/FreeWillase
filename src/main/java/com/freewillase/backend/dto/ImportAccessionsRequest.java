package com.freewillase.backend.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class ImportAccessionsRequest {
    private String taskName;

    @NotEmpty(message = "accessions 不能为空")
    private List<String> accessions;

    private String ncbiEmail;
    private String ncbiApiKey;
}
