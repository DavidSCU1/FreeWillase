package com.freewillase.backend.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("literature_relation")
public class LiteratureRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long literatureId;
    private Long enzymeId;
    private String relationType;
    private String confidenceLevel;
    private BigDecimal confidenceScore;
    private String matchedFields;
    private String note;
    private LocalDateTime createdAt;
}
