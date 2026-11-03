package com.zhiketong.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.config.properties.ChatProperties;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.security.TenantContext;
import com.zhiketong.service.ChatService;
import com.zhiketong.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 排队超时清理任务：每分钟扫描排队超过 {@code zhiketong.chat.queue-timeout-minutes} 的会话，
 * 状态置 CLOSED（close_reason=TIMEOUT）、写系统消息、移出队列并推送 SESSION_CLOSED。
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTimeoutTask {

    /** 单批处理条数，避免单次执行时间过长 */
    private static final int BATCH_SIZE = 50;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatService chatService;
    private final QueueService queueService;
    private final ChatProperties chatProperties;

    /**
     * 每分钟执行一次排队超时扫描。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void closeTimeoutSessions() {
        // 定时任务无登录态与租户上下文，按系统模式跨租户扫描，逐条再切回会话所属租户
        TenantContext.runSystem(this::doCloseTimeout);
    }

    /**
     * 系统模式下的实际关闭逻辑。
     */
    private void doCloseTimeout() {
        int timeoutMinutes = Math.max(1, chatProperties.getQueueTimeoutMinutes());
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                .isNotNull(ChatSession::getQueueEnterTime)
                .le(ChatSession::getQueueEnterTime, deadline)
                .orderByAsc(ChatSession::getId)
                .last("LIMIT " + BATCH_SIZE));
        if (sessions.isEmpty()) {
            return;
        }
        int closed = 0;
        for (ChatSession session : sessions) {
            try {
                TenantContext.runAs(session.getTenantId(), () -> {
                    chatService.timeoutClose(session);
                    queueService.remove(session.getId());
                });
                closed++;
            } catch (Exception e) {
                log.warn("排队超时关闭失败：sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
        log.info("排队超时扫描完成：阈值 {} 分钟，扫描 {} 个，关闭 {} 个", timeoutMinutes, sessions.size(), closed);
    }
}
