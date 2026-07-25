package com.freewillase.backend.controller;

import com.freewillase.backend.dto.*;
import com.freewillase.backend.service.PredictionService;
import com.freewillase.backend.service.RnaFoldService;
import com.freewillase.backend.service.TrRosettaRnaService;
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
    private final TrRosettaRnaService trRosettaRnaService;

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

    @PostMapping("/trrosettarna")
    public TrRosettaRnaPredictionResponse predictTrRosettaRna(@RequestBody TrRosettaRnaPredictionRequest request) {
        return trRosettaRnaService.predict(request.getName(), request.getSequence(), request.getEmail());
    }

    @GetMapping("/trrosettarna/result/{taskId}")
    public TrRosettaRnaPredictionResponse getTrRosettaRnaResult(@PathVariable String taskId) {
        return trRosettaRnaService.getResult(taskId);
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
