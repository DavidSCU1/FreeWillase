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
@TableName("ncbi_import_task_item")
public class NcbiImportTaskItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String accession;
    private String status;
    private Long enzymeId;
    private String message;
    private LocalDateTime createdAt;
}
