package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.KbImportRequest;
import com.zhiketong.dto.kb.KbImportResultVO;
import com.zhiketong.dto.kb.KbImportTaskVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.KbImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * 行业知识包导入接口（R-87 / R-88）。
 * <p>
 * 会批量新增分类与词条，仅 ADMIN 可操作。
 *
 * @author 智客通开发组
 */
@Tag(name = "07. 知识库 - 知识包导入")
@Validated
@RestController
@RequestMapping("/api/kb/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KbImportController {

    private final KbImportService kbImportService;

    @Operation(summary = "导入行业知识包")
    @PostMapping
    public Result<KbImportResultVO> importPackage(@Valid @RequestBody KbImportRequest request) {
        Long operatorId = SecurityUtils.requireLogin().getUserId();
        return Result.success(kbImportService.importPackage(request, operatorId));
    }

    @Operation(summary = "导入批次列表")
    @GetMapping("/tasks")
    public Result<List<KbImportTaskVO>> listTasks(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 100, message = "limit 不能超过 100") Integer limit) {
        return Result.success(kbImportService.listTasks(limit));
    }
}
