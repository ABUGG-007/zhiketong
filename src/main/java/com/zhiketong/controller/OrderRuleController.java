package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.orderrule.OrderRuleRequest;
import com.zhiketong.dto.orderrule.OrderRuleRunVO;
import com.zhiketong.dto.orderrule.OrderRuleStatusRequest;
import com.zhiketong.dto.orderrule.OrderRuleVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.OrderRuleService;
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
import java.util.Map;

/**
 * 工单规则与自动化配置接口（R-137 ~ R-144 / R-146），仅管理员可访问。
 *
 * <p>控制层只做参数接收与登录人透传，唯一性、动作合法性校验与事件驱动执行均由
 * {@link OrderRuleService} 负责。工单维度的执行流水查询（R-145）在 {@code WorkOrderController}。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "09. 工单 - 规则与自动化")
@Validated
@RestController
@RequestMapping("/api/admin/order-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrderRuleController {

    private final OrderRuleService orderRuleService;

    @Operation(summary = "工单规则列表（R-137）")
    @GetMapping
    public Result<List<OrderRuleVO>> list(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false)
            @Min(value = 0, message = "status 只能为 0 或 1")
            @Max(value = 1, message = "status 只能为 0 或 1") Integer status) {
        return Result.success(orderRuleService.listRules(ruleType, status));
    }

    @Operation(summary = "工单规则详情（R-138）")
    @GetMapping("/{id}")
    public Result<OrderRuleVO> detail(@PathVariable Long id) {
        return Result.success(orderRuleService.ruleDetail(id));
    }

    @Operation(summary = "新增工单规则（R-139）")
    @PostMapping
    public Result<OrderRuleVO> create(@Valid @RequestBody OrderRuleRequest request) {
        return Result.success(orderRuleService.createRule(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "修改工单规则（R-140）")
    @PutMapping("/{id}")
    public Result<OrderRuleVO> update(@PathVariable Long id,
                                      @Valid @RequestBody OrderRuleRequest request) {
        return Result.success(orderRuleService.updateRule(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用工单规则（R-141）")
    @PutMapping("/{id}/status")
    public Result<OrderRuleVO> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody OrderRuleStatusRequest request) {
        return Result.success(orderRuleService.updateStatus(id, request.getStatus(),
                SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除工单规则（R-142）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orderRuleService.deleteRule(id, SecurityUtils.requireLogin());
        return Result.success();
    }

    @Operation(summary = "规则执行流水分页（R-143）")
    @GetMapping("/{id}/runs")
    public Result<PageResult<OrderRuleRunVO>> runs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        return Result.success(orderRuleService.pageRuns(id, pageNum, pageSize));
    }

    @Operation(summary = "规则手动试跑（R-144）")
    @PostMapping("/{id}/run")
    public Result<Map<String, Object>> runOnce(@PathVariable Long id) {
        return Result.success(orderRuleService.runOnce(id, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "规则命中预览（R-146）")
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(@Valid @RequestBody OrderRuleRequest request) {
        return Result.success(orderRuleService.preview(request));
    }
}
