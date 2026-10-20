package com.zhiketong.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器：维护 userId -> WebSocketSession，支持单发与广播。
 *
 * <p>会话按 sys_user.id 唯一，因此用户端与坐席端共用同一张在线表。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionManager {

    private final ObjectMapper objectMapper;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册连接。
     */
    public void register(Long userId, WebSocketSession session) {
        WebSocketSession old = sessions.put(userId, session);
        if (old != null && old.isOpen()) {
            try {
                old.close(CloseStatus.NORMAL.withReason("重复登录"));
            } catch (Exception e) {
                log.debug("关闭旧连接失败：{}", e.getMessage());
            }
        }
        log.info("WebSocket 已连接：userId={}, 当前在线={}", userId, sessions.size());
    }

    /**
     * 移除连接（仅当当前连接与注册连接一致时，避免重复登录覆盖误删）。
     */
    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null) {
            return;
        }
        sessions.remove(userId, session);
        log.info("WebSocket 已断开：userId={}, 当前在线={}", userId, sessions.size());
    }

    /**
     * 发送消息给指定用户。
     */
    public boolean sendToUser(Long userId, WsMessage message) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (Exception e) {
            log.warn("发送 WebSocket 消息失败：userId={}, {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 广播消息给所有在线连接。
     */
    public void broadcast(WsMessage message) {
        for (Map.Entry<Long, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    String payload = objectMapper.writeValueAsString(message);
                    synchronized (session) {
                        session.sendMessage(new TextMessage(payload));
                    }
                } catch (Exception e) {
                    log.warn("广播 WebSocket 消息失败：userId={}, {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    public boolean isOnline(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public int onlineCount() {
        return sessions.size();
    }

    public Set<Long> onlineUserIds() {
        return Collections.unmodifiableSet(sessions.keySet());
    }
}
