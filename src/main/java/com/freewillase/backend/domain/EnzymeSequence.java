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
@TableName("enzyme_sequence")
public class EnzymeSequence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enzymeId;
    private Integer versionNo;
    private String sequenceText;
    private Integer sequenceLength;
    private String sequenceHash;
    private Integer isPrimary;
    private String sourceType;
    private LocalDateTime createdAt;
}
