package com.freewillase.backend.controller;

import com.freewillase.backend.domain.LiteratureRecord;
import com.freewillase.backend.dto.LiteratureScanStatusResponse;
import com.freewillase.backend.dto.MatchLiteratureRequest;
import com.freewillase.backend.service.LiteratureMatchService;
import com.freewillase.backend.service.LiteratureScanMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/literatures")
@RequiredArgsConstructor
public class LiteratureController {

    private final LiteratureMatchService literatureMatchService;
    private final LiteratureScanMonitorService literatureScanMonitorService;

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

    @GetMapping("/scan-status")
    public LiteratureScanStatusResponse getScanStatus() {
        return literatureScanMonitorService.snapshot();
    }

    @PostMapping("/relations/{relationId}/download")
    public LiteratureRecord download(@PathVariable Long relationId) {
        return literatureMatchService.downloadLiterature(relationId);
    }

    @GetMapping("/{literatureId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long literatureId) throws Exception {
        LiteratureRecord record = literatureMatchService.getLiteratureById(literatureId);
        if (record == null || record.getAttachmentPath() == null || record.getAttachmentPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Path path = Path.of(record.getAttachmentPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (record.getAttachmentContentType() != null && !record.getAttachmentContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(record.getAttachmentContentType());
        }
        if (MediaType.APPLICATION_OCTET_STREAM.equals(mediaType) && record.getAttachmentFileName() != null) {
            mediaType = MediaTypeFactory.getMediaType(record.getAttachmentFileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getAttachmentFileName() + "\"")
                .body(new FileSystemResource(path));
    }
}
