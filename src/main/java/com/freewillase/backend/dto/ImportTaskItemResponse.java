package com.freewillase.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImportTaskItemResponse {
    String accession;
    String status;
    String message;
    Long enzymeId;
}
