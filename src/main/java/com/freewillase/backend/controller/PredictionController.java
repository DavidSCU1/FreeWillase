package com.freewillase.backend.controller;

import com.freewillase.backend.dto.MiniFoldPredictionRequest;
import com.freewillase.backend.dto.MiniFoldPredictionResponse;
import com.freewillase.backend.service.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping("/minifold")
    public MiniFoldPredictionResponse predict(@RequestBody MiniFoldPredictionRequest request) {
        if (request.getSequence() == null || request.getSequence().isEmpty()) {
            throw new IllegalArgumentException("序列不能为空");
        }
        return predictionService.predictWithMiniFold(request);
    }

    @GetMapping("/minifold/logs/{taskId}")
    public String getLogs(@PathVariable String taskId) {
        return predictionService.getMiniFoldLogs(taskId);
    }

    @GetMapping("/minifold/result/{taskId}")
    public MiniFoldPredictionResponse getResult(@PathVariable String taskId) {
        return predictionService.getMiniFoldResult(taskId);
    }
}
