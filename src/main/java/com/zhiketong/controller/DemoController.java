package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.service.DemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前端演示只读接口。
 *
 * <p>供前端演示页展示真实种子数据；正式业务接口按里程碑逐步替换。
 * 所有接口要求登录（JWT），只读、不返回密码。</p>
 */
@Tag(name = "03. 演示数据（只读）")
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @Operation(summary = "看板概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(demoService.overview());
    }

    @Operation(summary = "知识库条目分页")
    @GetMapping("/kb/entries")
    public Result<PageResult<Map<String, Object>>> kbEntries(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(demoService.kbEntries(keyword, categoryId,
                normalizePageNum(pageNum), normalizePageSize(pageSize)));
    }

    @Operation(summary = "知识库分类")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.success(demoService.categories());
    }

    @Operation(summary = "工单分页")
    @GetMapping("/workorders")
    public Result<PageResult<Map<String, Object>>> workOrders(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String priority,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(demoService.workOrders(status, priority, keyword,
                normalizePageNum(pageNum), normalizePageSize(pageSize)));
    }

    @Operation(summary = "会话分页")
    @GetMapping("/sessions")
    public Result<PageResult<Map<String, Object>>> sessions(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(demoService.sessions(status,
                normalizePageNum(pageNum), normalizePageSize(pageSize)));
    }

    @Operation(summary = "用户分页")
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "") String roleCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(demoService.users(roleCode,
                normalizePageNum(pageNum), normalizePageSize(pageSize)));
    }

    @Operation(summary = "坐席状态")
    @GetMapping("/agents")
    public Result<List<Map<String, Object>>> agents() {
        return Result.success(demoService.agents());
    }

    @Operation(summary = "等待接入队列")
    @GetMapping("/waiting")
    public Result<List<Map<String, Object>>> waiting() {
        return Result.success(demoService.waitingSessions());
    }

    private int normalizePageNum(int pageNum) {
        return Math.max(1, pageNum);
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(100, Math.max(1, pageSize));
    }
}
