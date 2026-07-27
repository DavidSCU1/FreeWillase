package com.freewillase.backend.service;

import com.freewillase.backend.domain.EnzymeEntry;
import com.freewillase.backend.dto.LiteratureScanStatusResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LiteratureScanMonitorService {

    private LiteratureScanStatusResponse status = idleStatus();

    public synchronized void start(int totalEnzymes, boolean partialScope, boolean apiKeyEnabled) {
        LocalDateTime now = LocalDateTime.now();
        status = LiteratureScanStatusResponse.builder()
                .status("RUNNING")
                .message(totalEnzymes > 0 ? "文献扫描任务已启动，正在连接 PubMed。" : "当前没有可扫描的酶条目。")
                .scope(partialScope ? "PARTIAL" : "ALL")
                .apiKeyEnabled(apiKeyEnabled)
                .totalEnzymes(totalEnzymes)
                .processedEnzymes(0)
                .discoveredCandidates(0)
                .failedEnzymes(0)
                .startedAt(now)
                .finishedAt(null)
                .lastHeartbeatAt(now)
                .build();
    }

    public synchronized void heartbeat(EnzymeEntry enzyme,
                                       int processedEnzymes,
                                       int totalEnzymes,
                                       int discoveredCandidates,
                                       int failedEnzymes,
                                       String message) {
        status = LiteratureScanStatusResponse.builder()
                .status("RUNNING")
                .message(message)
                .scope(status.getScope())
                .apiKeyEnabled(status.isApiKeyEnabled())
                .totalEnzymes(totalEnzymes)
                .processedEnzymes(processedEnzymes)
                .discoveredCandidates(discoveredCandidates)
                .failedEnzymes(failedEnzymes)
                .currentEnzymeId(enzyme != null ? enzyme.getId() : null)
                .currentAccession(enzyme != null ? enzyme.getProteinAccession() : null)
                .currentEnzymeName(enzyme != null ? enzyme.getName() : null)
                .startedAt(status.getStartedAt())
                .finishedAt(null)
                .lastHeartbeatAt(LocalDateTime.now())
                .build();
    }

    public synchronized void complete(int processedEnzymes, int totalEnzymes, int discoveredCandidates, int failedEnzymes) {
        LocalDateTime now = LocalDateTime.now();
        status = LiteratureScanStatusResponse.builder()
                .status("COMPLETED")
                .message(totalEnzymes > 0 ? "文献扫描已完成，候选结果已刷新。" : "没有可扫描的酶条目。")
                .scope(status.getScope())
                .apiKeyEnabled(status.isApiKeyEnabled())
                .totalEnzymes(totalEnzymes)
                .processedEnzymes(processedEnzymes)
                .discoveredCandidates(discoveredCandidates)
                .failedEnzymes(failedEnzymes)
                .currentEnzymeId(status.getCurrentEnzymeId())
                .currentAccession(status.getCurrentAccession())
                .currentEnzymeName(status.getCurrentEnzymeName())
                .startedAt(status.getStartedAt())
                .finishedAt(now)
                .lastHeartbeatAt(now)
                .build();
    }

    public synchronized void fail(String message) {
        LocalDateTime now = LocalDateTime.now();
        status = LiteratureScanStatusResponse.builder()
                .status("FAILED")
                .message(message)
                .scope(status.getScope())
                .apiKeyEnabled(status.isApiKeyEnabled())
                .totalEnzymes(status.getTotalEnzymes())
                .processedEnzymes(status.getProcessedEnzymes())
                .discoveredCandidates(status.getDiscoveredCandidates())
                .failedEnzymes(status.getFailedEnzymes())
                .currentEnzymeId(status.getCurrentEnzymeId())
                .currentAccession(status.getCurrentAccession())
                .currentEnzymeName(status.getCurrentEnzymeName())
                .startedAt(status.getStartedAt())
                .finishedAt(now)
                .lastHeartbeatAt(now)
                .build();
    }

    public synchronized LiteratureScanStatusResponse snapshot() {
        return LiteratureScanStatusResponse.builder()
                .status(status.getStatus())
                .message(status.getMessage())
                .scope(status.getScope())
                .apiKeyEnabled(status.isApiKeyEnabled())
                .totalEnzymes(status.getTotalEnzymes())
                .processedEnzymes(status.getProcessedEnzymes())
                .discoveredCandidates(status.getDiscoveredCandidates())
                .failedEnzymes(status.getFailedEnzymes())
                .currentEnzymeId(status.getCurrentEnzymeId())
                .currentAccession(status.getCurrentAccession())
                .currentEnzymeName(status.getCurrentEnzymeName())
                .startedAt(status.getStartedAt())
                .finishedAt(status.getFinishedAt())
                .lastHeartbeatAt(status.getLastHeartbeatAt())
                .build();
    }

    private LiteratureScanStatusResponse idleStatus() {
        return LiteratureScanStatusResponse.builder()
                .status("IDLE")
                .message("等待发起新的文献扫描。")
                .scope("ALL")
                .apiKeyEnabled(false)
                .totalEnzymes(0)
                .processedEnzymes(0)
                .discoveredCandidates(0)
                .failedEnzymes(0)
                .build();
    }
}
