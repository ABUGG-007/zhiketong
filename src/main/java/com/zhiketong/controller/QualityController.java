package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.quality.QualityComparisonVO;
import com.zhiketong.dto.quality.QualityOverviewVO;
import com.zhiketong.dto.quality.QualityRecordDetailVO;
import com.zhiketong.dto.quality.QualityRecordVO;
import com.zhiketong.dto.quality.QualityReviewActionRequest;
import com.zhiketong.dto.quality.QualityReviewRequest;
import com.zhiketong.dto.quality.QualityRuleRequest;
import com.zhiketong.dto.quality.QualityRuleStatusRequest;
import com.zhiketong.dto.quality.QualityRuleVO;
import com.zhiketong.dto.quality.QualityScanResultVO;
import com.zhiketong.dto.quality.QualitySessionVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.QualityReviewService;
import com.zhiketong.service.QualityRuleService;
import com.zhiketong.service.QualityService;
import com.zhiketong.task.QualityScanTask;
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

/**
 * 智能质检接口（R-160 ~ R-175）：质检规则配置、质检记录与评分卡、人工复核流转、
 * 人机对比报表、手动质检与概览统计。
 *
 * <p>控制层只做参数接收与登录人透传；判定口径、评分计算、复核状态机与统计聚合均在
 * {@link QualityService} / {@link QualityRuleService} / {@link QualityReviewService} 中完成。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "15. 智能质检 - 规则与记录")
@Validated
@RestController
@RequestMapping("/api/admin/quality")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class QualityController {

    private final QualityRuleService qualityRuleService;
    private final QualityService qualityService;
    private final QualityReviewService qualityReviewService;
    private final QualityScanTask qualityScanTask;

    // ------------------------------------------------------------------
    // 质检规则
    // ------------------------------------------------------------------

    @Operation(summary = "质检规则列表（R-160）")
    @GetMapping("/rules")
    public Result<List<QualityRuleVO>> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false)
            @Min(value = 0, message = "status 只能为 0 或 1")
            @Max(value = 1, message = "status 只能为 0 或 1") Integer status) {
        return Result.success(qualityRuleService.listRules(ruleType, status));
    }

    @Operation(summary = "质检规则详情（R-161）")
    @GetMapping("/rules/{id}")
    public Result<QualityRuleVO> ruleDetail(@PathVariable Long id) {
        return Result.success(qualityRuleService.ruleDetail(id));
    }

    @Operation(summary = "新增质检规则（R-162）")
    @PostMapping("/rules")
    public Result<QualityRuleVO> createRule(@Valid @RequestBody QualityRuleRequest request) {
        return Result.success(qualityRuleService.createRule(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "修改质检规则（R-163）")
    @PutMapping("/rules/{id}")
    public Result<QualityRuleVO> updateRule(@PathVariable Long id,
                                            @Valid @RequestBody QualityRuleRequest request) {
        return Result.success(qualityRuleService.updateRule(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用质检规则（R-164）")
    @PutMapping("/rules/{id}/status")
    public Result<QualityRuleVO> updateRuleStatus(@PathVariable Long id,
                                                  @Valid @RequestBody QualityRuleStatusRequest request) {
        return Result.success(qualityRuleService.updateRuleStatus(id, request.getStatus(),
                SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除质检规则（R-165）")
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        qualityRuleService.deleteRule(id, SecurityUtils.requireLogin());
        return Result.success();
    }

    // ------------------------------------------------------------------
    // 质检记录与报表
    // ------------------------------------------------------------------

    @Operation(summary = "质检记录分页（R-166）")
    @GetMapping("/records")
    public Result<PageResult<QualityRecordVO>> records(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 最小为 1") int pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 最小为 1")
            @Max(value = 100, message = "pageSize 最大为 100") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(qualityService.pageRecords(pageNum, pageSize, keyword, grade, status,
                agentId, startDate, endDate));
    }

    @Operation(summary = "质检记录详情与评分卡（R-167）")
    @GetMapping("/records/{id}")
    public Result<QualityRecordDetailVO> recordDetail(@PathVariable Long id) {
        return Result.success(qualityService.recordDetail(id));
    }

    @Operation(summary = "手动质检指定会话（R-168）")
    @PostMapping("/sessions/{sessionId}/evaluate")
    public Result<QualityRecordDetailVO> evaluate(@PathVariable Long sessionId) {
        return Result.success(qualityService.evaluate(sessionId, SecurityUtils.getUserId()));
    }

    @Operation(summary = "质检概览统计（R-169）")
    @GetMapping("/overview")
    public Result<QualityOverviewVO> overview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(qualityService.overview(startDate, endDate));
    }

    @Operation(summary = "立即执行一轮全量质检（R-170）")
    @PostMapping("/scan")
    public Result<QualityScanResultVO> scan() {
        return Result.success(qualityScanTask.scanNow());
    }

    @Operation(summary = "可质检会话列表（R-171）")
    @GetMapping("/sessions")
    public Result<PageResult<QualitySessionVO>> sessions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 最小为 1") int pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 最小为 1")
            @Max(value = 100, message = "pageSize 最大为 100") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(qualityService.pageClosedSessions(pageNum, pageSize, keyword));
    }

    // ------------------------------------------------------------------
    // 人工复核与人机对比（S5-B，QC-06 / QC-07）
    // ------------------------------------------------------------------

    @Operation(summary = "提交人工复核（R-172）")
    @PutMapping("/records/{id}/review")
    public Result<QualityRecordDetailVO> review(@PathVariable Long id,
                                                @Valid @RequestBody QualityReviewRequest request) {
        return Result.success(qualityReviewService.review(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "确认复核结论归档（R-173）")
    @PutMapping("/records/{id}/confirm")
    public Result<QualityRecordDetailVO> confirm(@PathVariable Long id,
                                                 @RequestBody(required = false)
                                                 QualityReviewActionRequest request) {
        String comment = request == null ? null : request.getComment();
        return Result.success(qualityReviewService.confirm(id, comment, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "驳回复核结论并退回待复核（R-174）")
    @PutMapping("/records/{id}/reject")
    public Result<QualityRecordDetailVO> reject(@PathVariable Long id,
                                                @Valid @RequestBody QualityReviewActionRequest request) {
        return Result.success(qualityReviewService.reject(id, request.getComment(), SecurityUtils.requireLogin()));
    }

    @Operation(summary = "人机质检对比报表（R-175）")
    @GetMapping("/comparison")
    public Result<QualityComparisonVO> comparison(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(qualityService.comparison(startDate, endDate));
    }
}
