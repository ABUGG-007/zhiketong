package com.zhiketong.controller;

import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.UserVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.UserPortalService;
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
import java.util.Map;

/**
 * 用户中心只读接口（仅普通用户 USER）：我的会话、我的工单、个人资料。
 */
@Tag(name = "05. 用户中心")
@Validated
@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class UserPortalController {

    private final UserPortalService userPortalService;

    @Operation(summary = "我的会话列表")
    @GetMapping("/sessions")
    public Result<PageResult<Map<String, Object>>> sessions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(userPortalService.pageMySessions(
                userId, Constants.ROLE_USER, pageNum, pageSize));
    }

    @Operation(summary = "我的工单列表")
    @GetMapping("/orders")
    public Result<PageResult<Map<String, Object>>> orders(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(userPortalService.pageMyOrders(
                userId, Constants.ROLE_USER, pageNum, pageSize));
    }

    @Operation(summary = "我的资料")
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(userPortalService.profile(userId));
    }
}
