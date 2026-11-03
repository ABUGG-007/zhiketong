package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.assist.SuggestionVO;
import com.zhiketong.service.SuggestionService;
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
import javax.validation.constraints.Size;

/**
 * 访客端输入联想（R-111，对应 AS-01）。
 *
 * <p>联想只返回知识库问法与可信度，不返回答案内容，也不落库；
 * 访客仅能访问本人登录态下的联想接口，不涉及会话数据。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "08. 会话与消息")
@Validated
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatSuggestionController {

    private final SuggestionService suggestionService;

    @Operation(summary = "访客端输入联想（R-111）")
    @GetMapping("/suggestions")
    @PreAuthorize("hasRole('USER')")
    public Result<SuggestionVO> suggestions(
            @RequestParam
            @Size(max = 50, message = "联想关键词长度不能超过 50") String query,
            @RequestParam(defaultValue = "5")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 8, message = "limit 不能超过 8") Integer limit) {
        return Result.success(suggestionService.suggestForVisitor(query, limit));
    }
}
