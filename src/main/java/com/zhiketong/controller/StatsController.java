package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.stats.AgentWorkloadVO;
import com.zhiketong.dto.stats.AiCallStatVO;
import com.zhiketong.dto.stats.KbHitStatVO;
import com.zhiketong.dto.stats.SessionTrendVO;
import com.zhiketong.dto.stats.StatisticsOverviewVO;
import com.zhiketong.dto.stats.WorkOrderDistributionVO;
import com.zhiketong.service.StatsService;
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
 * 统计分析接口（R-64 ~ R-69），全部仅管理员可访问。
 *
 * <p>查询区间默认最近 7 天（含今天），startDate / endDate 必须成对出现，
 * 跨度不超过 366 天；统计口径以 Asia/Shanghai 自然日为准。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "13. 统计分析")
@Validated
@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "数据总览（R-64）")
    @GetMapping("/overview")
    public Result<StatisticsOverviewVO> overview(@RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate) {
        return Result.success(statsService.overview(startDate, endDate));
    }

    @Operation(summary = "后台看板聚合数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statsService.dashboard());
    }

    @Operation(summary = "会话量趋势（R-65）")
    @GetMapping("/session-trend")
    public Result<SessionTrendVO> sessionTrend(@RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate,
                                               @RequestParam(required = false) String granularity) {
        return Result.success(statsService.sessionTrend(startDate, endDate, granularity));
    }

    @Operation(summary = "坐席工作量（R-66）")
    @GetMapping("/agent-workload")
    public Result<PageResult<AgentWorkloadVO>> agentWorkload(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        return Result.success(statsService.agentWorkload(startDate, endDate, pageNum, pageSize));
    }

    @Operation(summary = "知识库命中统计（R-67）")
    @GetMapping("/kb-hit")
    public Result<KbHitStatVO> kbHit(@RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate,
                                     @RequestParam(required = false)
                                     @Min(value = 1, message = "limit 必须大于等于 1")
                                     @Max(value = 50, message = "limit 不能超过 50") Integer limit,
                                     @RequestParam(required = false)
                                     @Min(value = 1, message = "categoryId 不合法") Long categoryId) {
        return Result.success(statsService.kbHit(startDate, endDate, limit, categoryId));
    }

    @Operation(summary = "工单分布统计（R-68）")
    @GetMapping("/workorder-distribution")
    public Result<WorkOrderDistributionVO> workOrderDistribution(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(statsService.workOrderDistribution(startDate, endDate));
    }

    @Operation(summary = "AI 调用统计（R-69）")
    @GetMapping("/ai-calls")
    public Result<AiCallStatVO> aiCalls(@RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate,
                                        @RequestParam(required = false) String granularity) {
        return Result.success(statsService.aiCalls(startDate, endDate, granularity));
    }
}
