package com.zhiketong.controller;

import com.zhiketong.ai.AiChatService;
import com.zhiketong.common.Result;
import com.zhiketong.dto.AiChatRequest;
import com.zhiketong.dto.AiChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;

/**
 * AI 对话调试接口（正式用户对话走 WebSocket，此接口用于联调与演示大模型接入）。
 */
@Tag(name = "02. AI 对话")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @Operation(summary = "AI 对话（调试）")
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(
                request.getSessionId(), null, request.getMessage(), Collections.emptyList()));
    }
}
