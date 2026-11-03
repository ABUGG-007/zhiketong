package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.chat.CloseSessionRequest;
import com.zhiketong.dto.chat.CreateSessionResponse;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.dto.chat.RatingRequest;
import com.zhiketong.dto.chat.SendMessageRequest;
import com.zhiketong.dto.chat.SendMessageResponse;
import com.zhiketong.dto.chat.SessionVO;
import com.zhiketong.dto.chat.TransferRequest;
import com.zhiketong.dto.chat.TransferResponse;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 会话与消息接口（R-31 ~ R-39）。
 *
 * <p>状态流转：AI → WAITING → HUMAN → CLOSED；REST 发送消息为 WebSocket 的兜底通道。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "08. 会话与消息")
@Validated
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "创建会话（R-31）")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Result<CreateSessionResponse> createSession() {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(chatService.createSession(userId));
    }

    @Operation(summary = "会话分页列表（R-32）")
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<PageResult<SessionVO>> pageSessions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @Size(max = 50, message = "关键词长度不能超过 50") String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(chatService.pageSessions(
                loginUser, pageNum, pageSize, status, keyword, startDate, endDate));
    }

    @Operation(summary = "查询本人进行中的会话（R-33）")
    @GetMapping("/current")
    @PreAuthorize("hasRole('USER')")
    public Result<SessionVO> currentSession() {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(chatService.currentSession(userId));
    }

    @Operation(summary = "会话详情（R-34）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<SessionVO> detail(@PathVariable Long id) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(chatService.detail(id, loginUser));
    }

    @Operation(summary = "会话消息分页（R-35）")
    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<PageResult<MessageVO>> pageMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于等于 1") Integer pageNum,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize,
            @RequestParam(required = false)
            @Min(value = 0, message = "afterId 不能小于 0") Long afterId,
            @RequestParam(defaultValue = "true") Boolean markRead) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(chatService.pageMessages(
                id, loginUser, pageNum, pageSize, afterId, Boolean.TRUE.equals(markRead)));
    }

    @Operation(summary = "发送消息（R-36，WebSocket 兜底）")
    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('USER','AGENT')")
    public Result<SendMessageResponse> sendMessage(@PathVariable Long id,
                                                   @Valid @RequestBody SendMessageRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(chatService.sendMessage(id, loginUser, request));
    }

    @Operation(summary = "请求转人工（R-37）")
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('USER')")
    public Result<TransferResponse> transfer(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) TransferRequest request) {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(chatService.transfer(id, userId, request));
    }

    @Operation(summary = "结束会话（R-38）")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<SessionVO> closeSession(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) CloseSessionRequest request) {
        LoginUser loginUser = SecurityUtils.requireLogin();
        return Result.success(chatService.closeSession(id, loginUser, request));
    }

    @Operation(summary = "会话评分（R-39）")
    @PostMapping("/{id}/rating")
    @PreAuthorize("hasRole('USER')")
    public Result<Map<String, Object>> rateSession(@PathVariable Long id,
                                                   @Valid @RequestBody RatingRequest request) {
        Long userId = SecurityUtils.requireLogin().getUserId();
        return Result.success(chatService.rateSession(id, userId, request));
    }
}
