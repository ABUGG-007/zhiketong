package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.audit.AuditLogDetailVO;
import com.zhiketong.dto.audit.AuditLogVO;
import com.zhiketong.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * 租户端操作审计查询接口（S4-C，PL-06 / R-159），仅管理员可访问。
 *
 * <p>不提供 tenantId 参数：多租户拦截器自动限定本租户，
 * 管理员只能看到本租户成员的操作记录。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "14. 系统管理 - 操作审计")
@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "本租户操作审计分页列表")
    @GetMapping
    public Result<PageResult<AuditLogVO>> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 32, message = "module 长度不能超过 32") String module,
            @RequestParam(required = false)
            @Size(max = 24, message = "action 长度不能超过 24") String action,
            @RequestParam(required = false)
            @Size(max = 50, message = "keyword 长度不能超过 50") String keyword,
            @RequestParam(required = false)
            @Size(max = 10, message = "startDate 格式须为 yyyy-MM-dd") String startDate,
            @RequestParam(required = false)
            @Size(max = 10, message = "endDate 格式须为 yyyy-MM-dd") String endDate) {
        return Result.success(auditLogService.page(pageNum, pageSize, null, module, action,
                keyword, startDate, endDate));
    }

    @Operation(summary = "本租户操作审计详情（含变更前后快照）")
    @GetMapping("/{id}")
    public Result<AuditLogDetailVO> detail(@PathVariable Long id) {
        return Result.success(auditLogService.detail(id));
    }
}
