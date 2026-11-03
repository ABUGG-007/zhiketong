package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.sla.SlaPolicyRequest;
import com.zhiketong.dto.sla.SlaPolicyStatusRequest;
import com.zhiketong.dto.sla.SlaPolicyVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.SlaPolicyService;
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
 * 工单 SLA 策略配置接口（R-127 ~ R-132）：按优先级维护三级时限、预警阈值与超时升级规则，仅管理员可访问。
 *
 * <p>控制层只做参数接收与登录人透传，唯一性、默认策略互斥与业务校验均由
 * {@link SlaPolicyService} 负责。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "09. 工单 - SLA 策略")
@Validated
@RestController
@RequestMapping("/api/admin/sla-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;

    @Operation(summary = "SLA 策略列表（R-127）")
    @GetMapping
    public Result<List<SlaPolicyVO>> list(
            @RequestParam(required = false) String priority,
            @RequestParam(required = false)
            @Min(value = 0, message = "status 只能为 0 或 1")
            @Max(value = 1, message = "status 只能为 0 或 1") Integer status) {
        return Result.success(slaPolicyService.listPolicies(priority, status));
    }

    @Operation(summary = "SLA 策略详情（R-128）")
    @GetMapping("/{id}")
    public Result<SlaPolicyVO> detail(@PathVariable Long id) {
        return Result.success(slaPolicyService.policyDetail(id));
    }

    @Operation(summary = "新增 SLA 策略（R-129）")
    @PostMapping
    public Result<SlaPolicyVO> create(@Valid @RequestBody SlaPolicyRequest request) {
        return Result.success(slaPolicyService.createPolicy(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "修改 SLA 策略（R-130）")
    @PutMapping("/{id}")
    public Result<SlaPolicyVO> update(@PathVariable Long id,
                                      @Valid @RequestBody SlaPolicyRequest request) {
        return Result.success(slaPolicyService.updatePolicy(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用 SLA 策略（R-131）")
    @PutMapping("/{id}/status")
    public Result<SlaPolicyVO> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody SlaPolicyStatusRequest request) {
        return Result.success(slaPolicyService.updatePolicyStatus(id, request.getStatus(),
                SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除 SLA 策略（R-132）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        slaPolicyService.deletePolicy(id, SecurityUtils.requireLogin());
        return Result.success();
    }
}
