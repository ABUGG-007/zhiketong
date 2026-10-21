package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.agent.AgentOnlineRequest;
import com.zhiketong.dto.agent.AgentStatusUpdateRequest;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AgentStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 坐席工作台状态接口（仅坐席本人可操作）。
 */
@Tag(name = "04. 坐席状态")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
public class AgentController {

    private final AgentStatusService agentStatusService;

    @Operation(summary = "坐席上线")
    @PostMapping("/online")
    public Result<AgentStatusVO> online(@Valid @RequestBody(required = false) AgentOnlineRequest request) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        Integer maxConcurrent = request == null ? null : request.getMaxConcurrent();
        return Result.success(agentStatusService.online(agentId, maxConcurrent));
    }

    @Operation(summary = "坐席下线")
    @PostMapping("/offline")
    public Result<AgentStatusVO> offline() {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentStatusService.offline(agentId));
    }

    @Operation(summary = "设置在线/忙碌")
    @PutMapping("/status")
    public Result<AgentStatusVO> updateStatus(@Valid @RequestBody AgentStatusUpdateRequest request) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentStatusService.updateOnlineStatus(agentId, request.getOnlineStatus()));
    }
}
