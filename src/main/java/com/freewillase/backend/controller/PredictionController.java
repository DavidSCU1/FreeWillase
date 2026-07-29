package com.freewillase.backend.controller;

import com.freewillase.backend.dto.*;
import com.freewillase.backend.service.PredictionService;
import com.freewillase.backend.service.TrRosettaRnaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 预测工作台的统一后端入口。
 *
 * <p>设计思路：</p>
 * <ul>
 *   <li>Controller 层只负责：接收请求、做最基础的参数校验、从登录态中拿到 username（用于按用户隔离配置）。</li>
 *   <li>具体预测逻辑全部下沉到 Service：包括调用上游云端接口、启动本地进程、轮询/查询结果等。</li>
 *   <li>前端工作台通过不同端点实现两类交互：同步（NVIDIA 直接返回结构）与异步（trRosettaRNA/MiniFold 返回 taskId 供轮询）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {

    private final PredictionService predictionService;
    private final TrRosettaRnaService trRosettaRnaService;

    /**
     * MiniFold（项目内置/本地运行）预测提交入口。
     *
     * <p>交互方式：提交后立即返回 taskId（running），前端随后通过 logs/result 端点轮询。</p>
     */
    @PostMapping("/minifold")
    public MiniFoldPredictionResponse predict(@RequestBody MiniFoldPredictionRequest request, Authentication authentication) {
        log.info("MiniFold request received: sequenceLength={}, targetChains={}, useIgpu={}, backend={}, condaEnvName={}",
                request.getSequence() != null ? request.getSequence().length() : 0,
                request.getTargetChains(),
                request.getUseIgpu(),
                request.getBackend(),
                request.getCondaEnvName());
        // 最基础的入参校验：避免把空序列传入底层进程，造成更难定位的错误
        if (request.getSequence() == null || request.getSequence().isEmpty()) {
            throw new IllegalArgumentException("序列不能为空");
        }
        // username 用于在子进程环境变量中注入当前用户的私有 env 文件路径（实现按用户隔离的配置）
        return predictionService.predictWithMiniFold(request, authentication.getName());
    }

    /**
     * NVIDIA ESMFold（云端）预测入口。
     *
     * <p>交互方式：同步请求，后端会直接调用 NVIDIA endpoint，并将响应 body 原样返回给前端。</p>
     * <p>为什么需要 Authentication：用于按用户读取 NVIDIA_API_KEY / NVIDIA_API_URL（用户私有配置）。</p>
     */
    @PostMapping("/nvidia/esmfold")
    public ResponseEntity<String> predictNvidia(@RequestBody NvidiaPredictionRequest request, Authentication authentication) {
        return predictionService.predictWithNvidia(request, authentication.getName());
    }

    /**
     * trRosettaRNA（网页服务适配）预测提交入口。
     *
     * <p>交互方式：异步。这里返回 RUNNING + taskId，前端用 taskId 轮询 result 端点获取最终 PDB。</p>
     */
    @PostMapping("/trrosettarna")
    public TrRosettaRnaPredictionResponse predictTrRosettaRna(@RequestBody TrRosettaRnaPredictionRequest request) {
        return trRosettaRnaService.predict(request.getName(), request.getSequence(), request.getEmail());
    }

    /**
     * trRosettaRNA 结果查询入口（轮询）。
     *
     * <p>前端会每隔数秒调用一次：未完成返回 RUNNING，完成返回 FINISHED + pdbContent，失败返回 ERROR。</p>
     */
    @GetMapping("/trrosettarna/result/{taskId}")
    public TrRosettaRnaPredictionResponse getTrRosettaRnaResult(@PathVariable String taskId) {
        return trRosettaRnaService.getResult(taskId);
    }

    /**
     * MiniFold 日志查询入口（轮询）。
     *
     * <p>日志来自 taskDir 下的 process.log 或 launcher.log。</p>
     */
    @GetMapping("/minifold/logs/{taskId}")
    public String getLogs(@PathVariable String taskId) {
        return predictionService.getMiniFoldLogs(taskId);
    }

    /**
     * MiniFold 结果查询入口（轮询）。
     *
     * <p>结果来自 taskDir 下的 result.json。</p>
     */
    @GetMapping("/minifold/result/{taskId}")
    public MiniFoldPredictionResponse getResult(@PathVariable String taskId) {
        return predictionService.getMiniFoldResult(taskId);
    }
}
