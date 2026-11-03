package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.ops.ScreenLayoutRequest;
import com.zhiketong.dto.ops.ScreenLayoutVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.ScreenLayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 运营大屏布局配置接口（R-215 ~ R-217，S6-C / DB-07）。
 *
 * <p>沿用 {@code /api/statistics/ops} 前缀，仅管理员可访问；只配置「怎么摆」，
 * 指标数据仍由 {@code /screen} 与 WebSocket 推送提供。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "13.2 运营监控 - 大屏布局")
@Validated
@RestController
@RequestMapping("/api/statistics/ops/layout")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ScreenLayoutController {

    private final ScreenLayoutService screenLayoutService;

    @Operation(summary = "查询大屏布局（R-215）")
    @GetMapping
    public Result<ScreenLayoutVO> layout() {
        return Result.success(screenLayoutService.layout());
    }

    @Operation(summary = "保存大屏布局（R-216）")
    @PutMapping
    public Result<ScreenLayoutVO> save(@Valid @RequestBody ScreenLayoutRequest request) {
        return Result.success(screenLayoutService.save(request, SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "重置为默认布局（R-217）")
    @PostMapping("/reset")
    public Result<ScreenLayoutVO> reset() {
        return Result.success(screenLayoutService.reset(SecurityUtils.requireLogin().getUserId()));
    }
}
