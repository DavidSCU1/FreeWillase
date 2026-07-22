package com.freewillase.backend.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("enzyme_entry")
public class EnzymeEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String proteinAccession;
    private String proteinVersion;
    private String geneId;
    private String geneSymbol;
    private String locusTag;
    private String taxId;
    private String name;
    private String ecNumber;
    private String organism;
    private String substrate;
    private String product;
    private Integer sequenceLength;
    private String sequenceHash;
    private String optimalPh;
    private String optimalTemperature;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
