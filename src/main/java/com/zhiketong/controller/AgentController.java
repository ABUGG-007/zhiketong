package com.zhiketong.controller;

import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.agent.AgentOnlineRequest;
import com.zhiketong.dto.agent.AgentStatusUpdateRequest;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AgentStatusService;
import com.zhiketong.service.UserPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.Map;

/**
 * 坐席工作台状态接口（仅坐席本人可操作）。
 */
@Tag(name = "04. 坐席状态")
@Validated
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
public class AgentController {

    private final AgentStatusService agentStatusService;
    private final UserPortalService userPortalService;

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

    @Operation(summary = "我的会话列表")
    @GetMapping("/sessions")
    public Result<PageResult<Map<String, Object>>> mySessions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(userPortalService.pageMySessions(
                agentId, Constants.ROLE_AGENT, pageNum, pageSize));
    }

    @Operation(summary = "我的工单列表")
    @GetMapping("/orders")
    public Result<PageResult<Map<String, Object>>> myOrders(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(userPortalService.pageMyOrders(
                agentId, Constants.ROLE_AGENT, pageNum, pageSize));
    }
}
