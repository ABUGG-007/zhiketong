package com.zhiketong.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话 WebSocket 处理器（骨架阶段）。
 *
 * <p>当前已具备：JWT 握手鉴权、连接注册、心跳、统一消息信封、错误回执。
 * 用户消息、转人工、坐席接入等业务逻辑将在后续里程碑接入 ChatService。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_LOGIN_USER = "loginUser";

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        if (loginUser == null) {
            closeQuietly(session, "未认证");
            return;
        }
        sessionManager.register(loginUser.getUserId(), session);
        sessionManager.sendToUser(loginUser.getUserId(), notice("连接成功，智客通智能客服为您服务。"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        LoginUser loginUser = getLoginUser(session);
        if (loginUser == null) {
            closeQuietly(session, "未认证");
            return;
        }
        try {
            WsMessage request = objectMapper.readValue(message.getPayload(), WsMessage.class);
            String type = request.getType();
            if ("PING".equalsIgnoreCase(type) || "HEARTBEAT".equalsIgnoreCase(type)) {
                Map<String, Object> data = new HashMap<>(2);
                data.put("serverTime", System.currentTimeMillis());
                sessionManager.sendToUser(loginUser.getUserId(),
                        new WsMessage("PONG", request.getRequestId(), System.currentTimeMillis(), data));
                return;
            }
            if ("USER_MESSAGE".equalsIgnoreCase(type) || "AGENT_MESSAGE".equalsIgnoreCase(type)) {
                sessionManager.sendToUser(loginUser.getUserId(),
                        notice("消息已收到；对话业务将在 M3 里程碑接入，当前为骨架阶段。"));
                return;
            }
            if ("TRANSFER_REQUEST".equalsIgnoreCase(type)) {
                sessionManager.sendToUser(loginUser.getUserId(),
                        notice("转人工排队将在 M4 里程碑接入，当前为骨架阶段。"));
                return;
            }
            sessionManager.sendToUser(loginUser.getUserId(),
                    error(request.getRequestId(), "不支持的消息类型：" + type));
        } catch (Exception e) {
            log.warn("处理 WebSocket 消息失败：{}", e.getMessage());
            sessionManager.sendToUser(loginUser.getUserId(), error(null, "消息格式不正确"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LoginUser loginUser = getLoginUser(session);
        if (loginUser != null) {
            sessionManager.unregister(loginUser.getUserId(), session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常：{}", exception.getMessage());
        closeQuietly(session, "传输异常");
    }

    private LoginUser getLoginUser(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_LOGIN_USER);
        return value instanceof LoginUser ? (LoginUser) value : null;
    }

    private WsMessage notice(String content) {
        Map<String, Object> data = new HashMap<>(2);
        data.put("content", content);
        data.put("senderType", "SYSTEM");
        return new WsMessage("SYSTEM_NOTICE", null, System.currentTimeMillis(), data);
    }

    private WsMessage error(String requestId, String content) {
        Map<String, Object> data = new HashMap<>(2);
        data.put("message", content);
        return new WsMessage("ERROR", requestId, System.currentTimeMillis(), data);
    }

    private void closeQuietly(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }
}
