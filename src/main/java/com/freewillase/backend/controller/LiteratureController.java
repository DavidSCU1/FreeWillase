package com.freewillase.backend.controller;

import com.freewillase.backend.domain.LiteratureRecord;
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

    @PostMapping("/match-all")
    public void matchAll(@RequestBody(required = false) com.freewillase.backend.dto.MatchLiteratureRequest request) {
        String email = request != null ? request.getNcbiEmail() : null;
        String apiKey = request != null ? request.getNcbiApiKey() : null;
        literatureMatchService.matchLiteratureForAll(email, apiKey);
    }
}
