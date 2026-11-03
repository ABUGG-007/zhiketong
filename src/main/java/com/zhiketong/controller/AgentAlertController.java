package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.alert.AlertEventVO;
import com.zhiketong.dto.alert.AlertHandleRequest;
import com.zhiketong.dto.alert.AlertRuleRequest;
import com.zhiketong.dto.alert.AlertRuleStatusRequest;
import com.zhiketong.dto.alert.AlertRuleVO;
import com.zhiketong.dto.alert.AlertSummaryVO;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AlertRuleService;
import com.zhiketong.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.List;

/**
 * 坐席告警接口（R-190 ~ R-198）：个人告警规则与本人会话告警处理。
 *
 * <p>坐席只能维护 {@code owner_id = 本人} 的规则，只能处理责任坐席为本人
 * （或未指派）的告警；租户级规则只读，由管理员统一调整。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "17. 告警与监控 - 坐席端")
@Validated
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
public class AgentAlertController {

    private final AlertRuleService alertRuleService;
    private final AlertService alertService;

    @Operation(summary = "本人告警规则列表（R-190）：含租户级规则，只读")
    @GetMapping("/alert-rules")
    public Result<List<AlertRuleVO>> listRules() {
        return Result.success(alertRuleService.listAgentRules(SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "新增个人告警规则（R-191）")
    @PostMapping("/alert-rules")
    public Result<AlertRuleVO> createRule(@Valid @RequestBody AlertRuleRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(alertRuleService.createRule(request, loginUser, loginUser.getUserId()));
    }

    @Operation(summary = "修改个人告警规则（R-192）")
    @PutMapping("/alert-rules/{id}")
    public Result<AlertRuleVO> updateRule(@PathVariable Long id,
                                          @Valid @RequestBody AlertRuleRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(alertRuleService.updateRule(id, request, loginUser, loginUser.getUserId()));
    }

    @Operation(summary = "启用/停用个人告警规则（R-193）")
    @PutMapping("/alert-rules/{id}/status")
    public Result<AlertRuleVO> updateRuleStatus(@PathVariable Long id,
                                                @Valid @RequestBody AlertRuleStatusRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(alertRuleService.updateRuleStatus(id, request.getStatus(), loginUser,
                loginUser.getUserId()));
    }

    @Operation(summary = "删除个人告警规则（R-194）")
    @DeleteMapping("/alert-rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        alertRuleService.deleteRule(id, loginUser, loginUser.getUserId());
        return Result.success();
    }

    @Operation(summary = "本人告警分页（R-195）")
    @GetMapping("/alerts")
    public Result<PageResult<AlertEventVO>> alerts(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 最小为 1") int pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 最小为 1")
            @Max(value = 100, message = "pageSize 最大为 100") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String keyword) {
        return Result.success(alertService.page(pageNum, pageSize, status, severity, ruleType, keyword,
                SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "本人告警概览（R-196）")
    @GetMapping("/alerts/summary")
    public Result<AlertSummaryVO> summary() {
        return Result.success(alertService.summary(SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "确认本人告警（R-197）")
    @PutMapping("/alerts/{id}/ack")
    public Result<AlertEventVO> ack(@PathVariable Long id,
                                    @RequestBody(required = false) AlertHandleRequest request) {
        String note = request == null ? null : request.getNote();
        return Result.success(alertService.ack(id, note, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "关闭本人告警（R-198）")
    @PutMapping("/alerts/{id}/close")
    public Result<AlertEventVO> close(@PathVariable Long id) {
        return Result.success(alertService.close(id, SecurityUtils.requireLogin()));
    }
}
