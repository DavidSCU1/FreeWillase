package com.freewillase.backend.controller;

import com.freewillase.backend.dto.ImportAccessionsRequest;
import com.freewillase.backend.dto.ImportTaskResponse;
import com.freewillase.backend.service.NcbiImportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/imports/ncbi")
public class NcbiImportController {

    private final NcbiImportService ncbiImportService;

    public NcbiImportController(NcbiImportService ncbiImportService) {
        this.ncbiImportService = ncbiImportService;
    }

    @PostMapping("/accessions")
    public ImportTaskResponse importAccessions(@Valid @RequestBody ImportAccessionsRequest request) {
        return ncbiImportService.importAccessions(
                request.getTaskName(), 
                request.getAccessions(),
                request.getNcbiEmail(),
                request.getNcbiApiKey()
        );
    }

    @GetMapping("/tasks/{taskId}")
    public ImportTaskResponse getTask(@PathVariable Long taskId) {
        return ncbiImportService.getTask(taskId);
    }

    @GetMapping("/tasks/latest")
    public ImportTaskResponse getLatestTask() {
        return ncbiImportService.getLatestTask();
    }
}
