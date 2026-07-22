package com.freewillase.backend.dto;

import lombok.Data;

@Data
public class MatchLiteratureRequest {
    private String ncbiEmail;
    private String ncbiApiKey;
}
