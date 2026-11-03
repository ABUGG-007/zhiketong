package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.alert.AlertEventVO;
import com.zhiketong.dto.alert.AlertHandleRequest;
import com.zhiketong.dto.alert.AlertRuleRequest;
import com.zhiketong.dto.alert.AlertRuleStatusRequest;
import com.zhiketong.dto.alert.AlertRuleVO;
import com.zhiketong.dto.alert.AlertScanResultVO;
import com.zhiketong.dto.alert.AlertSummaryVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AlertRuleService;
import com.zhiketong.service.AlertService;
import com.zhiketong.task.AlertScanTask;
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
 * 告警与监控接口（R-180 ~ R-189）：告警规则配置、告警中心与手动扫描。
 *
 * <p>控制层只做参数接收与登录人透传；规则归一化、判定算法、事件去重与推送
 * 分别在 {@link AlertRuleService} / {@link AlertService} 中完成。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "16. 告警与监控 - 规则与告警中心")
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AlertController {

    private final AlertRuleService alertRuleService;
    private final AlertService alertService;
    private final AlertScanTask alertScanTask;

    // ------------------------------------------------------------------
    // 告警规则
    // ------------------------------------------------------------------

    @Operation(summary = "告警规则列表（R-180）")
    @GetMapping("/alert-rules")
    public Result<List<AlertRuleVO>> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false)
            @Min(value = 0, message = "status 只能为 0 或 1")
            @Max(value = 1, message = "status 只能为 0 或 1") Integer status) {
        return Result.success(alertRuleService.listRules(ruleType, status));
    }

    @Operation(summary = "新增告警规则（R-181）")
    @PostMapping("/alert-rules")
    public Result<AlertRuleVO> createRule(@Valid @RequestBody AlertRuleRequest request) {
        return Result.success(alertRuleService.createRule(request, SecurityUtils.requireLogin(), 0L));
    }

    @Operation(summary = "修改告警规则（R-182）")
    @PutMapping("/alert-rules/{id}")
    public Result<AlertRuleVO> updateRule(@PathVariable Long id,
                                          @Valid @RequestBody AlertRuleRequest request) {
        return Result.success(alertRuleService.updateRule(id, request, SecurityUtils.requireLogin(), null));
    }

    @Operation(summary = "启用/停用告警规则（R-183）")
    @PutMapping("/alert-rules/{id}/status")
    public Result<AlertRuleVO> updateRuleStatus(@PathVariable Long id,
                                                @Valid @RequestBody AlertRuleStatusRequest request) {
        return Result.success(alertRuleService.updateRuleStatus(id, request.getStatus(),
                SecurityUtils.requireLogin(), null));
    }

    @Operation(summary = "删除告警规则（R-184）：内置规则不可删除")
    @DeleteMapping("/alert-rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alertRuleService.deleteRule(id, SecurityUtils.requireLogin(), null);
        return Result.success();
    }

    // ------------------------------------------------------------------
    // 告警中心
    // ------------------------------------------------------------------

    @Operation(summary = "告警分页（R-185）")
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
        return Result.success(alertService.page(pageNum, pageSize, status, severity, ruleType, keyword, null));
    }

    @Operation(summary = "告警概览（R-186）")
    @GetMapping("/alerts/summary")
    public Result<AlertSummaryVO> summary() {
        return Result.success(alertService.summary(null));
    }

    @Operation(summary = "立即执行一轮告警扫描（R-187）")
    @PostMapping("/alerts/scan")
    public Result<AlertScanResultVO> scan() {
        return Result.success(alertScanTask.scanNow());
    }

    @Operation(summary = "确认告警（R-188）")
    @PutMapping("/alerts/{id}/ack")
    public Result<AlertEventVO> ack(@PathVariable Long id,
                                    @RequestBody(required = false) AlertHandleRequest request) {
        String note = request == null ? null : request.getNote();
        return Result.success(alertService.ack(id, note, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "关闭告警（R-189）")
    @PutMapping("/alerts/{id}/close")
    public Result<AlertEventVO> close(@PathVariable Long id) {
        return Result.success(alertService.close(id, SecurityUtils.requireLogin()));
    }
}
