package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.plan.PlanUsageVO;
import com.zhiketong.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 套餐与用量接口（仅管理员）。
 *
 * <p>对应管理端"套餐与用量"页：展示当前套餐、自然月额度水位、
 * 近 30 天运营 KPI 与套餐对比，支撑试用转付费的价值呈现。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "06. 套餐与用量")
@RestController
@RequestMapping("/api/plan")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "当前套餐与实时用量")
    @GetMapping
    public Result<PlanUsageVO> current() {
        return Result.success(planService.current());
    }
}
