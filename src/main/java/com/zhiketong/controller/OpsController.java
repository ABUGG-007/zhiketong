package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.ops.AgentRealtimeVO;
import com.zhiketong.dto.ops.OpsMetricsVO;
import com.zhiketong.dto.ops.OpsScreenVO;
import com.zhiketong.service.OpsService;
import com.zhiketong.service.SessionMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营监控接口（R-176 ~ R-179，S6-A）。
 *
 * <p>沿用 {@code /api/statistics} 前缀，仅管理员可访问；指标口径见
 * docs/产品推进路线图.md 第 3 章。大屏数据同时支持 WebSocket 订阅推送（{@code SCREEN_SUBSCRIBE}），
 * HTTP 接口作为首次加载与推送不可用时的轮询兜底。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "13.1 运营监控")
@Validated
@RestController
@RequestMapping("/api/statistics/ops")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;
    private final SessionMetricService sessionMetricService;

    @Operation(summary = "实时大屏快照（R-176）")
    @GetMapping("/screen")
    public Result<OpsScreenVO> screen() {
        return Result.success(opsService.screen());
    }

    @Operation(summary = "运营指标区间报表（R-177）")
    @GetMapping("/metrics")
    public Result<OpsMetricsVO> metrics(@RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        return Result.success(opsService.metrics(startDate, endDate));
    }

    @Operation(summary = "轮次指标回填（R-178）")
    @PostMapping("/metrics/rebuild")
    public Result<Map<String, Object>> rebuild(@RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        int processed = sessionMetricService.rebuild(startDate, endDate);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processed", processed);
        data.put("startDate", startDate);
        data.put("endDate", endDate);
        return Result.success("回填完成", data);
    }

    @Operation(summary = "坐席实时表现（R-179）")
    @GetMapping("/agents")
    public Result<PageResult<AgentRealtimeVO>> agents(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        return Result.success(opsService.agentRealtime(pageNum, pageSize));
    }
}
