package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.dto.monitor.MonitorDetailVO;
import com.zhiketong.dto.monitor.MonitorSessionVO;
import com.zhiketong.dto.monitor.SupervisorReassignRequest;
import com.zhiketong.dto.monitor.SupervisorWhisperRequest;
import com.zhiketong.dto.ops.AgentRealtimeVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AgentMonitorService;
import com.zhiketong.service.OpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 组长监控接口（R-199 ~ R-204，AS-10）：实时监听、强插发言、强制接管与强制派单。
 *
 * <p>控制层只做参数接收与登录人透传；会话状态校验、负载增减与消息落库
 * 全部在 {@link AgentMonitorService} 中完成。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "18. 告警与监控 - 组长监听")
@Validated
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMonitorController {

    private final AgentMonitorService agentMonitorService;
    private final OpsService opsService;

    @Operation(summary = "监听会话列表（R-199）：进行中的会话与待回复时长")
    @GetMapping("/sessions")
    public Result<PageResult<MonitorSessionVO>> sessions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 最小为 1") int pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 最小为 1")
            @Max(value = 100, message = "pageSize 最大为 100") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String keyword) {
        return Result.success(agentMonitorService.pageSessions(pageNum, pageSize, status, agentId, keyword));
    }

    @Operation(summary = "监听会话详情（R-200）：最近消息与未闭环告警")
    @GetMapping("/sessions/{id}")
    public Result<MonitorDetailVO> detail(@PathVariable Long id) {
        return Result.success(agentMonitorService.detail(id));
    }

    @Operation(summary = "组长强插发言（R-201）：访客与坐席双方可见")
    @PostMapping("/sessions/{id}/whisper")
    public Result<MessageVO> whisper(@PathVariable Long id,
                                     @Valid @RequestBody SupervisorWhisperRequest request) {
        return Result.success(agentMonitorService.whisper(id, request.getContent(),
                SecurityUtils.requireLogin()));
    }

    @Operation(summary = "强制接管（R-202）：排队中直接接入，人工中从原坐席接管")
    @PostMapping("/sessions/{id}/takeover")
    public Result<MonitorDetailVO> takeover(@PathVariable Long id) {
        return Result.success(agentMonitorService.takeover(id, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "强制派单（R-203）：指派给指定在线坐席")
    @PostMapping("/sessions/{id}/reassign")
    public Result<MonitorDetailVO> reassign(@PathVariable Long id,
                                            @Valid @RequestBody SupervisorReassignRequest request) {
        return Result.success(agentMonitorService.reassign(id, request.getTargetAgentId(),
                request.getReason(), SecurityUtils.requireLogin()));
    }

    @Operation(summary = "派单候选坐席（R-204）：在线状态与当前负载")
    @GetMapping("/agents")
    public Result<PageResult<AgentRealtimeVO>> agents() {
        return Result.success(opsService.agentRealtime(1, 100));
    }
}
