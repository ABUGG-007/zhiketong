package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.plan.PlanUsageVO;
import com.zhiketong.dto.platform.TenantCreateRequest;
import com.zhiketong.dto.platform.TenantDetailVO;
import com.zhiketong.dto.platform.TenantExpireRequest;
import com.zhiketong.dto.platform.TenantPlanChangeRequest;
import com.zhiketong.dto.platform.TenantStatusRequest;
import com.zhiketong.dto.platform.TenantUpdateRequest;
import com.zhiketong.dto.platform.TenantVO;
import com.zhiketong.entity.TenantPlan;
import com.zhiketong.entity.TenantUsage;
import com.zhiketong.service.TenantAdminService;
import com.zhiketong.service.TenantPlanService;
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
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 平台端租户管理接口（S4-B，PL-02/PL-03/PL-04），仅平台管理员可访问。
 *
 * <p>聚焦 SaaS 运营闭环：开通租户、启停与续期、套餐变更、用量查看与快照刷新。
 * 平台管理员故意不进入租户业务接口（知识库、工单等），避免角色越界。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "01. 平台管理 - 租户与套餐")
@Validated
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantAdminService tenantAdminService;
    private final TenantPlanService tenantPlanService;

    @Operation(summary = "租户分页列表")
    @GetMapping("/tenants")
    public Result<PageResult<TenantVO>> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 50, message = "keyword 长度不能超过 50") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String planCode) {
        return Result.success(tenantAdminService.page(pageNum, pageSize, keyword, status, planCode));
    }

    @Operation(summary = "租户详情（含额度水位）")
    @GetMapping("/tenants/{id}")
    public Result<TenantDetailVO> detail(@PathVariable Long id) {
        return Result.success(tenantAdminService.detail(id));
    }

    @Operation(summary = "开通租户（含商户管理员与接入配置）")
    @PostMapping("/tenants")
    public Result<TenantDetailVO> create(@Valid @RequestBody TenantCreateRequest request) {
        return Result.success(tenantAdminService.create(request));
    }

    @Operation(summary = "编辑租户资料")
    @PutMapping("/tenants/{id}")
    public Result<TenantDetailVO> update(@PathVariable Long id,
                                         @Valid @RequestBody TenantUpdateRequest request) {
        return Result.success(tenantAdminService.update(id, request));
    }

    @Operation(summary = "启用/停用租户")
    @PutMapping("/tenants/{id}/status")
    public Result<TenantDetailVO> updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody TenantStatusRequest request) {
        return Result.success(tenantAdminService.updateStatus(id, request));
    }

    @Operation(summary = "变更租户套餐")
    @PutMapping("/tenants/{id}/plan")
    public Result<TenantDetailVO> changePlan(@PathVariable Long id,
                                             @Valid @RequestBody TenantPlanChangeRequest request) {
        return Result.success(tenantAdminService.changePlan(id, request));
    }

    @Operation(summary = "设置到期时间（续期）")
    @PutMapping("/tenants/{id}/expire")
    public Result<TenantDetailVO> renew(@PathVariable Long id,
                                        @Valid @RequestBody TenantExpireRequest request) {
        return Result.success(tenantAdminService.renew(id, request));
    }

    @Operation(summary = "删除租户（逻辑删除）")
    @DeleteMapping("/tenants/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantAdminService.delete(id);
        return Result.success();
    }

    @Operation(summary = "刷新租户当月用量快照")
    @PostMapping("/tenants/{id}/usage/snapshot")
    public Result<TenantUsage> refreshSnapshot(@PathVariable Long id) {
        return Result.success(tenantAdminService.refreshSnapshot(id));
    }

    @Operation(summary = "套餐字典（含额度与功能点）")
    @GetMapping("/plans")
    public Result<List<PlanUsageVO.PlanOption>> plans() {
        return Result.success(tenantPlanService.options(null));
    }

    @Operation(summary = "套餐原始配置（平台配置页使用）")
    @GetMapping("/plans/config")
    public Result<List<TenantPlan>> planConfigs() {
        return Result.success(tenantPlanService.listAll());
    }
}
