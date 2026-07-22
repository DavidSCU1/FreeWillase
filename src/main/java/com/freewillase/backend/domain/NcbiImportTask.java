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
@TableName("ncbi_import_task")
public class NcbiImportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String sourceType;
    private int totalCount;
    private int successCount;
    private int failedCount;
    private int duplicateCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
