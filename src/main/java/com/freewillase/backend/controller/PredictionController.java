package com.freewillase.backend.controller;

import com.freewillase.backend.dto.MiniFoldPredictionRequest;
import com.freewillase.backend.dto.MiniFoldPredictionResponse;
import com.freewillase.backend.dto.RnaFoldPredictionRequest;
import com.freewillase.backend.dto.RnaFoldPredictionResponse;
import com.freewillase.backend.service.PredictionService;
import com.freewillase.backend.service.RnaFoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {

    private final PredictionService predictionService;
    private final RnaFoldService rnaFoldService;

    @PostMapping("/minifold")
    public MiniFoldPredictionResponse predict(@RequestBody MiniFoldPredictionRequest request) {
        log.info("MiniFold request received: sequenceLength={}, targetChains={}, useIgpu={}, backend={}, condaEnvName={}",
                request.getSequence() != null ? request.getSequence().length() : 0,
                request.getTargetChains(),
                request.getUseIgpu(),
                request.getBackend(),
                request.getCondaEnvName());
        if (request.getSequence() == null || request.getSequence().isEmpty()) {
            throw new IllegalArgumentException("序列不能为空");
        }
        return predictionService.predictWithMiniFold(request);
    }

    @PostMapping("/rnafold")
    public RnaFoldPredictionResponse predictRnaFold(@RequestBody RnaFoldPredictionRequest request) {
        return rnaFoldService.predict(request.getName(), request.getSequence());
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
