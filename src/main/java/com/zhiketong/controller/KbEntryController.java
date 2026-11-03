package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.EntryCreateRequest;
import com.zhiketong.dto.kb.EntryRankingVO;
import com.zhiketong.dto.kb.EntrySearchResultVO;
import com.zhiketong.dto.kb.EntryStatusRequest;
import com.zhiketong.dto.kb.EntryUpdateRequest;
import com.zhiketong.dto.kb.EntryVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.KbEntryService;
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
import java.util.Map;

/**
 * 知识库条目接口（R-23 ~ R-30）。
 * <p>
 * 查询与新增/编辑对 ADMIN、AGENT 开放；删除与启停仅 ADMIN。
 *
 * @author 智客通开发组
 */
@Tag(name = "07. 知识库 - 条目")
@Validated
@RestController
@RequestMapping("/api/kb/entries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class KbEntryController {

    private final KbEntryService kbEntryService;

    @Operation(summary = "知识条目分页列表")
    @GetMapping
    public Result<PageResult<EntryVO>> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Min(value = 1, message = "分类 ID 不合法") Long categoryId,
            @RequestParam(required = false)
            @Size(max = 50, message = "搜索关键词长度不能超过 50") String keyword,
            @RequestParam(required = false)
            @Min(value = 0, message = "状态只能为 0 或 1")
            @Max(value = 1, message = "状态只能为 0 或 1") Integer status) {
        return Result.success(kbEntryService.page(pageNum, pageSize, categoryId, keyword, status));
    }

    @Operation(summary = "知识条目详情")
    @GetMapping("/{id}")
    public Result<EntryVO> detail(@PathVariable Long id) {
        return Result.success(kbEntryService.detail(id));
    }

    @Operation(summary = "新增知识条目")
    @PostMapping
    public Result<EntryVO> create(@Valid @RequestBody EntryCreateRequest request) {
        Long operatorId = SecurityUtils.requireLogin().getUserId();
        return Result.success(kbEntryService.create(request, operatorId));
    }

    @Operation(summary = "编辑知识条目")
    @PutMapping("/{id}")
    public Result<EntryVO> update(@PathVariable Long id,
                                  @Valid @RequestBody EntryUpdateRequest request) {
        Long operatorId = SecurityUtils.requireLogin().getUserId();
        return Result.success(kbEntryService.update(id, request, operatorId));
    }

    @Operation(summary = "删除知识条目")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        kbEntryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用知识条目")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody EntryStatusRequest request) {
        return Result.success(kbEntryService.updateStatus(id, request.getStatus()));
    }

    @Operation(summary = "知识条目检索（匹配预览）")
    @GetMapping("/search")
    public Result<EntrySearchResultVO> search(
            @RequestParam
            @Size(min = 1, max = 200, message = "待匹配问句长度须为 1-200 个字符") String keyword,
            @RequestParam(required = false)
            @Min(value = 1, message = "分类 ID 不合法") Long categoryId,
            @RequestParam(defaultValue = "5")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 10, message = "limit 不能超过 10") Integer limit) {
        return Result.success(kbEntryService.search(keyword, categoryId, limit));
    }

    @Operation(summary = "知识条目命中排行")
    @GetMapping("/ranking")
    public Result<List<EntryRankingVO>> ranking(
            @RequestParam(required = false)
            @Min(value = 1, message = "分类 ID 不合法") Long categoryId,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 50, message = "limit 不能超过 50") Integer limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(kbEntryService.ranking(categoryId, limit, startDate, endDate));
    }
}
