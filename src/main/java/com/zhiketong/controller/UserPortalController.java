package com.zhiketong.controller;

import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.UserVO;
import com.zhiketong.dto.kb.CategoryVO;
import com.zhiketong.dto.kb.EntryVO;
import com.zhiketong.dto.workorder.WorkOrderVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.KbCategoryService;
import com.zhiketong.service.KbEntryService;
import com.zhiketong.service.UserPortalService;
import com.zhiketong.service.WorkOrderService;
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
import java.util.List;
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
    private final WorkOrderService workOrderService;
    private final KbEntryService kbEntryService;
    private final KbCategoryService kbCategoryService;

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
    public Result<PageResult<WorkOrderVO>> orders(
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
        return Result.success(workOrderService.myPage(
                SecurityUtils.requireLogin(), pageNum, pageSize, keyword, status, priority, startDate, endDate));
    }

    @Operation(summary = "我的资料")
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(userPortalService.profile(userId));
    }

    @Operation(summary = "常见问题分页（仅启用条目）")
    @GetMapping("/knowledge/entries")
    public Result<PageResult<EntryVO>> knowledgeEntries(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Size(max = 50, message = "搜索关键词长度不能超过 50") String keyword) {
        return Result.success(kbEntryService.page(pageNum, pageSize, null, keyword, 1));
    }

    @Operation(summary = "常见问题分类（仅启用分类）")
    @GetMapping("/knowledge/categories")
    public Result<List<CategoryVO>> knowledgeCategories() {
        return Result.success(kbCategoryService.list(1, false));
    }
}
