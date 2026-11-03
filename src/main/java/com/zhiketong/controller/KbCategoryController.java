package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.CategoryCreateRequest;
import com.zhiketong.dto.kb.CategoryStatusRequest;
import com.zhiketong.dto.kb.CategoryUpdateRequest;
import com.zhiketong.dto.kb.CategoryVO;
import com.zhiketong.service.KbCategoryService;
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
import java.util.Map;

/**
 * 知识库分类接口（R-18 ~ R-22）。
 * <p>
 * 读接口对 ADMIN、AGENT 开放；写操作仅 ADMIN。
 *
 * @author 智客通开发组
 */
@Tag(name = "06. 知识库 - 分类")
@Validated
@RestController
@RequestMapping("/api/kb/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class KbCategoryController {

    private final KbCategoryService kbCategoryService;

    @Operation(summary = "知识分类列表")
    @GetMapping
    public Result<List<CategoryVO>> list(
            @RequestParam(required = false)
            @Min(value = 0, message = "状态只能为 0 或 1")
            @Max(value = 1, message = "状态只能为 0 或 1") Integer status,
            @RequestParam(defaultValue = "true") boolean tree) {
        return Result.success(kbCategoryService.list(status, tree));
    }

    @Operation(summary = "新增知识分类")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CategoryVO> create(@Valid @RequestBody CategoryCreateRequest request) {
        return Result.success(kbCategoryService.create(request));
    }

    @Operation(summary = "编辑知识分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CategoryVO> update(@PathVariable Long id,
                                     @Valid @RequestBody CategoryUpdateRequest request) {
        return Result.success(kbCategoryService.update(id, request));
    }

    @Operation(summary = "删除知识分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        kbCategoryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用知识分类")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody CategoryStatusRequest request) {
        return Result.success(kbCategoryService.updateStatus(id, request.getStatus()));
    }
}
