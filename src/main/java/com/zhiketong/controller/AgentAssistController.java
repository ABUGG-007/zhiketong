package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.assist.CustomerCardVO;
import com.zhiketong.dto.assist.InternalNoteRequest;
import com.zhiketong.dto.assist.RecommendationVO;
import com.zhiketong.dto.assist.SessionBriefVO;
import com.zhiketong.dto.assist.SessionSummaryVO;
import com.zhiketong.dto.assist.SuggestionVO;
import com.zhiketong.dto.assist.SummarySaveRequest;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.AgentAssistService;
import com.zhiketong.service.AgentCustomerService;
import com.zhiketong.service.AgentNoteService;
import com.zhiketong.service.SuggestionService;
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
import java.util.List;

/**
 * 坐席辅助接口（R-104 ~ R-114）：实时推荐答案、进线摘要、服务小结、客户信息卡、输入联想与内部备注。
 *
 * <p>所有 AI 产出均为草稿，必须由坐席确认后才对客生效；内部备注只对坐席与管理端可见。
 * 数据范围由各 Service 按角色收敛，坐席仅能访问本人接待中的会话。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "04. 坐席工作台")
@Validated
@RestController
@RequestMapping("/api/agent/sessions/{sessionId}")
@RequiredArgsConstructor
public class AgentAssistController {

    private final AgentAssistService agentAssistService;
    private final AgentCustomerService agentCustomerService;
    private final SuggestionService suggestionService;
    private final AgentNoteService agentNoteService;

    @Operation(summary = "实时推荐答案（R-104）：知识库命中优先，未命中 AI 起草草稿")
    @GetMapping("/recommendations")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<RecommendationVO> recommendations(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "3")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 5, message = "limit 不能超过 5") Integer limit) {
        return Result.success(agentAssistService.recommend(sessionId, SecurityUtils.requireLogin(), limit));
    }

    @Operation(summary = "进线摘要（R-105）：不存在时即时生成")
    @GetMapping("/brief")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<SessionBriefVO> brief(@PathVariable Long sessionId) {
        return Result.success(agentAssistService.brief(sessionId, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "重新生成进线摘要（R-106）")
    @PostMapping("/brief/regenerate")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<SessionBriefVO> regenerateBrief(@PathVariable Long sessionId) {
        return Result.success(agentAssistService.regenerateBrief(sessionId, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "查询服务小结（R-107）：未生成时 data 为 null")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<SessionSummaryVO> summary(@PathVariable Long sessionId) {
        return Result.success(agentAssistService.summary(sessionId, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "生成服务小结草稿（R-108）：需坐席确认后归档")
    @PostMapping("/summary/generate")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<SessionSummaryVO> generateSummary(@PathVariable Long sessionId) {
        return Result.success(agentAssistService.generateSummary(sessionId, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "保存并采纳服务小结（R-109）")
    @PutMapping("/summary")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<SessionSummaryVO> saveSummary(@PathVariable Long sessionId,
                                                @Valid @RequestBody SummarySaveRequest request) {
        return Result.success(agentAssistService.saveSummary(sessionId, SecurityUtils.requireLogin(), request));
    }

    @Operation(summary = "客户信息卡（R-110）：画像、历史会话、历史工单与满意度")
    @GetMapping("/customer")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<CustomerCardVO> customer(@PathVariable Long sessionId) {
        return Result.success(agentCustomerService.customerCard(sessionId, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "坐席端输入联想（R-112）：按输入召回知识库问法与答案摘要")
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<SuggestionVO> suggestions(
            @PathVariable Long sessionId,
            @RequestParam
            @Size(max = 50, message = "联想关键词长度不能超过 50") String query,
            @RequestParam(defaultValue = "5")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 8, message = "limit 不能超过 8") Integer limit) {
        return Result.success(suggestionService.suggestForAgent(
                sessionId, SecurityUtils.requireLogin(), query, limit));
    }

    @Operation(summary = "内部备注列表（R-113）：仅坐席与管理端可见")
    @GetMapping("/notes")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<List<MessageVO>> notes(@PathVariable Long sessionId) {
        return Result.success(agentNoteService.list(sessionId, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "新增内部备注（R-114）：不对客推送，不计入会话消息数")
    @PostMapping("/notes")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Result<MessageVO> addNote(@PathVariable Long sessionId,
                                     @Valid @RequestBody InternalNoteRequest request) {
        return Result.success(agentNoteService.add(sessionId, SecurityUtils.requireLogin(), request.getContent()));
    }
}
