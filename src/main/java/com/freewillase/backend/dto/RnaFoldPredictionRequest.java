package com.freewillase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RnaFoldPredictionRequest {

    @NotBlank(message = "请填写名称")
    private String name;

    @NotBlank(message = "请填写 RNA 序列")
    private String sequence;
}
