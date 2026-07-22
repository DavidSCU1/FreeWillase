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
@TableName("literature_record")
public class LiteratureRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String authors;
    private String journal;
    private Integer publishYear;
    private String doi;
    private String pmid;
    private String keywords;
    private String abstractText;
    private String sourceDb;
    private String sourceUrl;
    private LocalDateTime createdAt;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.math.BigDecimal confidenceScore;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String confidenceLevel;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String matchedEnzymeName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String matchedEnzymeAccession;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String matchedFields;
}
