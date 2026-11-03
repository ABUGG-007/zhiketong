package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.agent.AgentAcceptResponse;
import com.zhiketong.dto.agent.AgentOnlineRequest;
import com.zhiketong.dto.agent.AgentStatusUpdateRequest;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.dto.agent.AgentTransferRequest;
import com.zhiketong.dto.agent.AgentWorkbenchStatsVO;
import com.zhiketong.dto.agent.QueuePageResult;
import com.zhiketong.dto.chat.CloseSessionRequest;
import com.zhiketong.dto.chat.SessionVO;
import com.zhiketong.dto.workorder.WorkOrderVO;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AgentWorkbenchService;
import com.zhiketong.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 坐席工作台接口（R-40 ~ R-48）：状态、待接入队列、接入、我的会话、结束、转接与统计。
 *
 * <p>坐席状态类接口仅坐席本人可操作；队列查询同时开放给管理员只读。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "04. 坐席工作台")
@Validated
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentWorkbenchService agentWorkbenchService;
    private final WorkOrderService workOrderService;

    @Operation(summary = "坐席上线（R-40）")
    @PostMapping("/online")
    @PreAuthorize("hasRole('AGENT')")
    public Result<AgentStatusVO> online(@Valid @RequestBody(required = false) AgentOnlineRequest request) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        Integer maxConcurrent = request == null ? null : request.getMaxConcurrent();
        return Result.success(agentWorkbenchService.online(agentId, maxConcurrent));
    }

    @Operation(summary = "坐席下线（R-41）")
    @PostMapping("/offline")
    @PreAuthorize("hasRole('AGENT')")
    public Result<AgentStatusVO> offline() {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentWorkbenchService.offline(agentId));
    }

    @Operation(summary = "设置在线/忙碌（R-42）")
    @PutMapping("/status")
    @PreAuthorize("hasRole('AGENT')")
    public Result<AgentStatusVO> updateStatus(@Valid @RequestBody AgentStatusUpdateRequest request) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentWorkbenchService.updateStatus(agentId, request.getOnlineStatus()));
    }

    @Operation(summary = "当前坐席状态")
    @GetMapping("/status")
    @PreAuthorize("hasRole('AGENT')")
    public Result<AgentStatusVO> status() {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentWorkbenchService.status(agentId));
    }

    @Operation(summary = "坐席状态列表（只读）")
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<List<AgentStatusVO>> list() {
        return Result.success(agentWorkbenchService.listAgents());
    }

    @Operation(summary = "待接入队列（R-43）")
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<QueuePageResult> queue(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        return Result.success(agentWorkbenchService.queue(pageNum, pageSize));
    }

    @Operation(summary = "接入排队会话（R-44）")
    @PostMapping("/sessions/{sessionId}/accept")
    @PreAuthorize("hasRole('AGENT')")
    public Result<AgentAcceptResponse> accept(@PathVariable Long sessionId) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentWorkbenchService.accept(sessionId, agentId));
    }

    @Operation(summary = "我的会话列表（R-45）")
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('AGENT')")
    public Result<PageResult<SessionVO>> mySessions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @Size(max = 50, message = "关键词长度不能超过 50") String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(agentWorkbenchService.mySessions(
                loginUser, pageNum, pageSize, status, keyword, startDate, endDate));
    }

    @Operation(summary = "结束坐席会话（R-46）")
    @PostMapping("/sessions/{sessionId}/close")
    @PreAuthorize("hasRole('AGENT')")
    public Result<Map<String, Object>> closeSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) CloseSessionRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(agentWorkbenchService.closeSession(sessionId, loginUser, request));
    }

    @Operation(summary = "会话转接（R-47）")
    @PostMapping("/sessions/{sessionId}/transfer")
    @PreAuthorize("hasRole('AGENT')")
    public Result<Map<String, Object>> transfer(@PathVariable Long sessionId,
                                                @Valid @RequestBody AgentTransferRequest request) {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentWorkbenchService.transfer(sessionId, agentId, request));
    }

    @Operation(summary = "坐席工作台统计（R-48）")
    @GetMapping("/workbench/stats")
    @PreAuthorize("hasRole('AGENT')")
    public Result<AgentWorkbenchStatsVO> workbenchStats() {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(agentWorkbenchService.stats(agentId));
    }

    @Operation(summary = "待分配工单池（坐席可自领）")
    @GetMapping("/orders/pool")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<PageResult<WorkOrderVO>> orderPool(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 50, message = "关键词长度不能超过 50") String keyword,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category) {
        return Result.success(workOrderService.pendingPool(pageNum, pageSize, keyword, priority, category));
    }

    @Operation(summary = "我的工单列表")
    @GetMapping("/orders")
    @PreAuthorize("hasRole('AGENT')")
    public Result<PageResult<WorkOrderVO>> myOrders(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 50, message = "关键词长度不能超过 50") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.myPage(
                loginUser, pageNum, pageSize, keyword, status, priority, startDate, endDate));
    }
}
