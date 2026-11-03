package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.EntryVersionVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.KbVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识词条版本接口（R-78 / R-79）。
 * <p>
 * 版本列表对 ADMIN、AGENT 开放；回滚会改写线上答案，仅 ADMIN 可操作。
 *
 * @author 智客通开发组
 */
@Tag(name = "07. 知识库 - 版本")
@Validated
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class KbVersionController {

    private final KbVersionService kbVersionService;

    @Operation(summary = "词条版本列表")
    @GetMapping("/entries/{entryId}/versions")
    public Result<List<EntryVersionVO>> list(@PathVariable Long entryId) {
        return Result.success(kbVersionService.list(entryId));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/versions/{versionId}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> rollback(@PathVariable Long versionId) {
        Long operatorId = SecurityUtils.requireLogin().getUserId();
        kbVersionService.rollback(versionId, operatorId);
        return Result.success();
    }
}
