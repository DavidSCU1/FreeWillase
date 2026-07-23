package com.freewillase.backend.service;

import com.freewillase.backend.dto.MiniFoldPredictionRequest;
import com.freewillase.backend.dto.MiniFoldPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final RestTemplate restTemplate;
    private final String pythonEngineUrl = "http://localhost:8001/predict";

    public MiniFoldPredictionResponse predictWithMiniFold(MiniFoldPredictionRequest request) {
        log.info("Requesting MiniFold prediction for sequence of length: {}", request.getSequence().length());
        try {
            return restTemplate.postForObject(pythonEngineUrl, request, MiniFoldPredictionResponse.class);
        } catch (Exception e) {
            log.error("Failed to call Python prediction engine: {}", e.getMessage());
            throw new RuntimeException("AI 预测引擎连接失败，请确保 python_engine 已启动: " + e.getMessage());
        }
    }
}
