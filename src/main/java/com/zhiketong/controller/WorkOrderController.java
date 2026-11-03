package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.orderform.OrderFormVO;
import com.zhiketong.dto.orderrule.OrderRuleRunVO;
import com.zhiketong.dto.sla.OrderPauseRequest;
import com.zhiketong.dto.sla.OrderSlaVO;
import com.zhiketong.dto.sla.SlaScanRequest;
import com.zhiketong.dto.workorder.WorkOrderActionRequest;
import com.zhiketong.dto.workorder.WorkOrderAssignRequest;
import com.zhiketong.dto.workorder.WorkOrderCreateRequest;
import com.zhiketong.dto.workorder.WorkOrderDetailVO;
import com.zhiketong.dto.workorder.WorkOrderLogVO;
import com.zhiketong.dto.workorder.WorkOrderPriorityRequest;
import com.zhiketong.dto.workorder.WorkOrderRatingRequest;
import com.zhiketong.dto.workorder.WorkOrderReopenRequest;
import com.zhiketong.dto.workorder.WorkOrderRemarkRequest;
import com.zhiketong.dto.workorder.WorkOrderUpdateRequest;
import com.zhiketong.dto.workorder.WorkOrderVO;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.OrderFormService;
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
import java.util.Map;

/**
 * 工单接口（R-49 ~ R-63）：创建、列表、详情、流转、优先级、评分与流转日志。
 *
 * <p>控制层只做参数校验与登录人透传，状态机校验、数据权限与 SLA 计算均由
 * {@link WorkOrderService} 负责。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "09. 工单")
@Validated
@RestController
@RequestMapping("/api/workorders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final OrderFormService orderFormService;

    @Operation(summary = "创建工单（R-49）")
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<WorkOrderVO> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.create(request, loginUser));
    }

    @Operation(summary = "新建工单表单配置（R-126）")
    @GetMapping("/form")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<OrderFormVO> form() {
        return Result.success(orderFormService.form());
    }

    @Operation(summary = "工单分页列表（R-50）")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<PageResult<WorkOrderVO>> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 50, message = "关键词长度不能超过 50") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false)
            @Min(value = 1, message = "assigneeId 不合法") Long assigneeId,
            @RequestParam(required = false)
            @Min(value = 1, message = "creatorId 不合法") Long creatorId,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.page(loginUser, pageNum, pageSize, keyword, status,
                priority, category, assigneeId, creatorId, overdueOnly, startDate, endDate));
    }

    @Operation(summary = "我的工单（R-51）")
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<PageResult<WorkOrderVO>> my(
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

    @Operation(summary = "超时工单列表（R-52）")
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<PageResult<WorkOrderVO>> overdue(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.overduePage(loginUser, pageNum, pageSize, priority, category));
    }

    @Operation(summary = "工单详情（R-53）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<WorkOrderDetailVO> detail(@PathVariable Long id) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.detail(id, loginUser));
    }

    @Operation(summary = "编辑工单（R-54）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<WorkOrderVO> update(@PathVariable Long id,
                                      @Valid @RequestBody WorkOrderUpdateRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.update(id, request, loginUser));
    }

    @Operation(summary = "分配工单（R-55）")
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<WorkOrderVO> assign(@PathVariable Long id,
                                      @Valid @RequestBody WorkOrderAssignRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.assign(id, request, loginUser));
    }

    @Operation(summary = "受理工单（R-56）")
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('AGENT')")
    public Result<WorkOrderVO> accept(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) WorkOrderActionRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.accept(id, request, loginUser));
    }

    @Operation(summary = "添加处理进展（R-57）")
    @PostMapping("/{id}/process")
    @PreAuthorize("hasRole('AGENT')")
    public Result<WorkOrderVO> process(@PathVariable Long id,
                                       @Valid @RequestBody WorkOrderRemarkRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.process(id, request.getRemark(), loginUser));
    }

    @Operation(summary = "解决工单（R-58）")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('AGENT')")
    public Result<WorkOrderVO> resolve(@PathVariable Long id,
                                       @Valid @RequestBody WorkOrderRemarkRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.resolve(id, request.getRemark(), loginUser));
    }

    @Operation(summary = "关闭工单（R-59）")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<WorkOrderVO> close(@PathVariable Long id,
                                     @Valid @RequestBody(required = false) WorkOrderActionRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.close(id, request, loginUser));
    }

    @Operation(summary = "重开工单（R-60）")
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<WorkOrderVO> reopen(@PathVariable Long id,
                                      @Valid @RequestBody WorkOrderReopenRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.reopen(id, request, loginUser));
    }

    @Operation(summary = "调整优先级（R-61）")
    @PutMapping("/{id}/priority")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<WorkOrderVO> updatePriority(@PathVariable Long id,
                                              @Valid @RequestBody WorkOrderPriorityRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.updatePriority(id, request, loginUser));
    }

    @Operation(summary = "工单评分（R-62）")
    @PostMapping("/{id}/rating")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<Map<String, Object>> rating(@PathVariable Long id,
                                              @Valid @RequestBody WorkOrderRatingRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.rating(id, request, loginUser));
    }

    @Operation(summary = "工单流转日志（R-63）")
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<PageResult<WorkOrderLogVO>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.pageLogs(id, pageNum, pageSize, loginUser));
    }

    @Operation(summary = "工单 SLA 详情（R-133）")
    @GetMapping("/{id}/sla")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<OrderSlaVO> sla(@PathVariable Long id) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.slaDetail(id, loginUser));
    }

    @Operation(summary = "挂起停表（R-134）")
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<WorkOrderVO> pause(@PathVariable Long id,
                                     @Valid @RequestBody(required = false) OrderPauseRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.pause(id, request, loginUser));
    }

    @Operation(summary = "恢复计时（R-135）")
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<WorkOrderVO> resume(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) OrderPauseRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.resume(id, request, loginUser));
    }

    @Operation(summary = "手动扫描/演练工单 SLA（R-136）")
    @PostMapping("/{id}/sla/scan")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<OrderSlaVO> scanSla(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) SlaScanRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.scanSla(id, request, loginUser));
    }

    @Operation(summary = "工单规则执行流水（R-145）")
    @GetMapping("/{id}/rules")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<java.util.List<OrderRuleRunVO>> ruleRuns(@PathVariable Long id) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(workOrderService.ruleRuns(id, loginUser));
    }
}
