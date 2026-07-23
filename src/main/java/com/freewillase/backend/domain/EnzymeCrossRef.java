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
@TableName("enzyme_cross_ref")
public class EnzymeCrossRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enzymeId;
    private String refDb;
    private String refType;
    private String refValue;
    private String refUrl;
    private Integer isPrimary;
    private LocalDateTime createdAt;
}
