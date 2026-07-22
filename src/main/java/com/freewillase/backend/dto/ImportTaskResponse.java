package com.freewillase.backend.dto;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ImportTaskResponse {
    Long id;
    String taskName;
    String status;
    int totalCount;
    int successCount;
    int failedCount;
    int duplicateCount;
    LocalDateTime createdAt;
    LocalDateTime finishedAt;
    @Singular
    List<ImportTaskItemResponse> items;
}
