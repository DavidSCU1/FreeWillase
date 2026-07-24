package com.freewillase.backend.service;

import com.freewillase.backend.dto.MiniFoldPredictionRequest;
import com.freewillase.backend.dto.MiniFoldPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final RestTemplate restTemplate;
    private final String pythonEngineUrl = "http://localhost:9001/predict";
    private final String pythonLogsUrl = "http://localhost:9001/logs/";
    private final String pythonResultUrl = "http://localhost:9001/result/";

    public MiniFoldPredictionResponse predictWithMiniFold(MiniFoldPredictionRequest request) {
        log.info("Requesting MiniFold prediction for sequence length: {}", 
            request.getSequence() != null ? request.getSequence().length() : 0);
        log.info("Params: ssn={}, threshold={}, envText={}", request.getSsn(), request.getThreshold(), request.getEnvText());
        
        try {
            return restTemplate.postForObject(pythonEngineUrl, request, MiniFoldPredictionResponse.class);
        } catch (RestClientResponseException e) {
            log.error("MiniFold API error ({}): {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("MiniFold 引擎返回错误: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to connect to MiniFold API at {}: {}", pythonEngineUrl, e.getMessage());
            throw new RuntimeException("无法连接到 MiniFold 预测引擎 (9001)。详细错误: " + e.getMessage());
        }
    }

    public String getMiniFoldLogs(String taskId) {
        try {
            return restTemplate.getForObject(pythonLogsUrl + taskId, String.class);
        } catch (Exception e) {
            log.error("Failed to fetch logs for task {}: {}", taskId, e.getMessage());
            return "无法获取日志: " + e.getMessage();
        }
    }

    public MiniFoldPredictionResponse getMiniFoldResult(String taskId) {
        try {
            return restTemplate.getForObject(pythonResultUrl + taskId, MiniFoldPredictionResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch result for task {}: {}", taskId, e.getMessage());
            return MiniFoldPredictionResponse.builder()
                    .status("failed")
                    .error("获取结果失败: " + e.getMessage())
                    .build();
        }
    }
}
