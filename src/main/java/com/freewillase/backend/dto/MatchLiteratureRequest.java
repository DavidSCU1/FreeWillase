package com.freewillase.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class MatchLiteratureRequest {
    private String ncbiEmail;
    private String ncbiApiKey;
    private List<Long> enzymeIds;
}
