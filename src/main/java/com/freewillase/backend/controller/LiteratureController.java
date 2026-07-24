package com.freewillase.backend.controller;

import com.freewillase.backend.domain.LiteratureRecord;
import com.freewillase.backend.dto.MatchLiteratureRequest;
import com.freewillase.backend.service.LiteratureMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/literatures")
@RequiredArgsConstructor
public class LiteratureController {

    private final LiteratureMatchService literatureMatchService;

    @GetMapping
    public List<LiteratureRecord> listAll() {
        return literatureMatchService.listAll();
    }

    @PostMapping("/scan")
    public void scan(@RequestBody(required = false) MatchLiteratureRequest request) {
        String email = request != null ? request.getNcbiEmail() : null;
        String apiKey = request != null ? request.getNcbiApiKey() : null;
        literatureMatchService.matchLiteratureForEnzymes(request != null ? request.getEnzymeIds() : null, email, apiKey);
    }

    @PostMapping("/match-all")
    public void matchAll(@RequestBody(required = false) MatchLiteratureRequest request) {
        String email = request != null ? request.getNcbiEmail() : null;
        String apiKey = request != null ? request.getNcbiApiKey() : null;
        literatureMatchService.matchLiteratureForEnzymes(null, email, apiKey);
    }

    @PostMapping("/relations/{relationId}/download")
    public void download(@PathVariable Long relationId) {
        literatureMatchService.downloadLiterature(relationId);
    }
}
