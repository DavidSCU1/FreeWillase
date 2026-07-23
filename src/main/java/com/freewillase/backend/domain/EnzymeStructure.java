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
@TableName("enzyme_structure")
public class EnzymeStructure {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enzymeId;
    private String structureType;
    private String structureId;
    private String sourceDb;
    private String sourceUrl;
    private Integer isPrimary;
    private LocalDateTime createdAt;
}
