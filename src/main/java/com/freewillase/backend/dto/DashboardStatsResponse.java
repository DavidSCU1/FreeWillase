package com.freewillase.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private int enzymeCount;
    private String successRatio;
    private String literatureCoverage;
    private String systemStatus;
}
