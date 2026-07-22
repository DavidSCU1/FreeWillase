package com.freewillase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freewillase.backend.domain.EnzymeEntry;
import com.freewillase.backend.domain.LiteratureRelation;
import com.freewillase.backend.domain.NcbiImportTask;
import com.freewillase.backend.dto.DashboardStatsResponse;
import com.freewillase.backend.mapper.EnzymeEntryMapper;
import com.freewillase.backend.mapper.LiteratureRelationMapper;
import com.freewillase.backend.mapper.NcbiImportTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EnzymeEntryMapper enzymeMapper;
    private final LiteratureRelationMapper relationMapper;
    private final NcbiImportTaskMapper taskMapper;

    public DashboardStatsResponse getStats() {
        long enzymeCount = enzymeMapper.selectCount(null);
        
        NcbiImportTask latestTask = taskMapper.selectOne(new LambdaQueryWrapper<NcbiImportTask>()
                .orderByDesc(NcbiImportTask::getCreatedAt)
                .last("LIMIT 1"));
        
        String successRatio = "0%";
        if (latestTask != null && latestTask.getTotalCount() > 0) {
            successRatio = Math.round((double) latestTask.getSuccessCount() / latestTask.getTotalCount() * 100) + "%";
        }

        long enzymeWithLitCount = relationMapper.selectList(new LambdaQueryWrapper<LiteratureRelation>()
                .select(LiteratureRelation::getEnzymeId)
                .groupBy(LiteratureRelation::getEnzymeId))
                .size();
        
        String coverage = "0%";
        if (enzymeCount > 0) {
            coverage = Math.round((double) enzymeWithLitCount / enzymeCount * 100) + "%";
        }

        return DashboardStatsResponse.builder()
                .enzymeCount((int) enzymeCount)
                .successRatio(successRatio)
                .literatureCoverage(coverage)
                .systemStatus("Normal")
                .build();
    }
}
