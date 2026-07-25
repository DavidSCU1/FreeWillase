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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("enzyme_annotation")
public class EnzymeAnnotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enzymeId;
    private String annotationType;
    private String title;
    private Integer startResidue;
    private Integer endResidue;
    private String chainLabel;
    private String mutationLabel;
    private String colorHex;
    private String description;
    private String sourceDb;
    private String sourceRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
