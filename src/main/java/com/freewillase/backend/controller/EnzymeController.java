package com.freewillase.backend.controller;

import com.freewillase.backend.domain.LiteratureRecord;
import com.freewillase.backend.dto.MatchLiteratureRequest;
import com.freewillase.backend.dto.EnzymeEntryResponse;
import com.freewillase.backend.dto.ImportLiteratureFileRequest;
import com.freewillase.backend.dto.SaveMiniFoldEnzymeRequest;
import com.freewillase.backend.service.LiteratureMatchService;
import com.freewillase.backend.service.NcbiImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enzymes")
@RequiredArgsConstructor
public class EnzymeController {

    private final NcbiImportService ncbiImportService;
    private final LiteratureMatchService literatureMatchService;

    @GetMapping
    public List<EnzymeEntryResponse> listEnzymes(@RequestParam(required = false) String sourceType) {
        return ncbiImportService.listEnzymes(sourceType);
    }

    @PostMapping("/predicted/minifold")
    public EnzymeEntryResponse saveMiniFoldPrediction(@RequestBody SaveMiniFoldEnzymeRequest request) {
        return ncbiImportService.saveMiniFoldResult(request);
    }

    @GetMapping("/{id}/structure")
    public ResponseEntity<String> getStructure(@PathVariable Long id) {
        String structure = ncbiImportService.getStructureContent(id);
        if (structure == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(structure);
    }

    @PostMapping("/{id}/match")
    public void matchLiterature(@PathVariable Long id, @RequestBody(required = false) MatchLiteratureRequest request) {
        String email = request != null ? request.getNcbiEmail() : null;
        String apiKey = request != null ? request.getNcbiApiKey() : null;
        literatureMatchService.matchLiteratureForEnzyme(id, email, apiKey);
    }

    @GetMapping("/{id}/literatures")
    public List<LiteratureRecord> getLiteratures(@PathVariable Long id) {
        return literatureMatchService.getLiteratureForEnzyme(id);
    }

    @PostMapping("/{id}/literatures/import")
    public LiteratureRecord importLiterature(@PathVariable Long id, @RequestBody ImportLiteratureFileRequest request) {
        return literatureMatchService.importLiteratureFromLocalFile(id, request != null ? request.getFilePath() : null);
    }

    @PostMapping(path = "/{id}/literatures/upload", consumes = "multipart/form-data")
    public LiteratureRecord uploadLiterature(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return literatureMatchService.importLiteratureFromUpload(id, file);
    }

    @DeleteMapping("/{id}")
    public void deleteEnzyme(@PathVariable Long id) {
        ncbiImportService.deleteEnzyme(id);
    }
}
