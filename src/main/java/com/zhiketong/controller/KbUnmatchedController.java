package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.BatchIdsRequest;
import com.zhiketong.dto.kb.UnmatchedConvertRequest;
import com.zhiketong.dto.kb.UnmatchedQuestionVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.KbUnmatchedService;
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
import java.util.Collections;
import java.util.Map;

/**
 * 知识库未命中问题接口（R-80 ~ R-84）。
 * <p>
 * 列表与恢复对 ADMIN、AGENT 开放；忽略与转词条会改变知识库内容，仅 ADMIN 可操作。
 *
 * @author 智客通开发组
 */
@Tag(name = "07. 知识库 - 未命中闭环")
@Validated
@RestController
@RequestMapping("/api/kb/unmatched-questions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class KbUnmatchedController {

    private final KbUnmatchedService kbUnmatchedService;

    @Operation(summary = "未命中问题分页列表")
    @GetMapping
    public Result<PageResult<UnmatchedQuestionVO>> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false)
            @Size(max = 50, message = "搜索关键词长度不能超过 50") String keyword) {
        return Result.success(kbUnmatchedService.page(pageNum, pageSize, status, channel, keyword));
    }

    @Operation(summary = "忽略未命中问题")
    @PutMapping("/{id}/ignore")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> ignore(@PathVariable Long id) {
        kbUnmatchedService.ignore(id);
        return Result.success();
    }

    @Operation(summary = "批量忽略未命中问题")
    @PutMapping("/batch-ignore")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> batchIgnore(@Valid @RequestBody BatchIdsRequest request) {
        int count = kbUnmatchedService.batchIgnore(request.getIds());
        return Result.success(Collections.singletonMap("count", count));
    }

    @Operation(summary = "恢复为待处理")
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        kbUnmatchedService.restore(id);
        return Result.success();
    }

    @Operation(summary = "未命中问题转知识词条")
    @PostMapping("/{id}/convert")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> convert(@PathVariable Long id,
                                               @Valid @RequestBody UnmatchedConvertRequest request) {
        Long operatorId = SecurityUtils.requireLogin().getUserId();
        return Result.success(kbUnmatchedService.convert(id, request, operatorId));
    }
}
