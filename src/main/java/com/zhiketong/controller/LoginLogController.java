package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.admin.LoginLogVO;
import com.zhiketong.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * 登录日志查询接口（M1，R-70），仅 ADMIN 可访问。
 */
@Tag(name = "12. 系统管理 - 登录日志")
@Validated
@RestController
@RequestMapping("/api/admin/login-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @Operation(summary = "登录日志分页列表")
    @GetMapping
    public Result<PageResult<LoginLogVO>> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 50, message = "keyword 长度不能超过 50") String keyword,
            @RequestParam(required = false)
            @Min(value = 0, message = "success 只能为 0 或 1")
            @Max(value = 1, message = "success 只能为 0 或 1") Integer success,
            @RequestParam(required = false)
            @Size(max = 10, message = "startDate 格式须为 yyyy-MM-dd") String startDate,
            @RequestParam(required = false)
            @Size(max = 10, message = "endDate 格式须为 yyyy-MM-dd") String endDate) {
        return Result.success(loginLogService.page(pageNum, pageSize, keyword, success, startDate, endDate));
    }
}
