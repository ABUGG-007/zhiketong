package com.zhiketong.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.dto.chat.CloseSessionRequest;
import com.zhiketong.dto.chat.RatingRequest;
import com.zhiketong.dto.chat.SendMessageRequest;
import com.zhiketong.dto.chat.SendMessageResponse;
import com.zhiketong.dto.chat.SessionVO;
import com.zhiketong.dto.chat.TransferRequest;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.enums.SenderType;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.TenantContext;
import com.zhiketong.service.AgentStatusService;
import com.zhiketong.service.AgentWorkbenchService;
import com.zhiketong.service.ChatService;
import com.zhiketong.service.OpsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 对话 WebSocket 处理器：握手回执、心跳、用户/坐席消息、评分、结束会话。
 *
 * <p>消息业务统一委托 {@link ChatService}，本类只负责协议编解码、角色校验与错误回执。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_LOGIN_USER = "loginUser";

    /** 建议客户端心跳间隔（秒） */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /** 输入状态转发节流（秒） */
    private static final long TYPING_THROTTLE_SECONDS = 1L;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatPushService chatPushService;
    private final AgentWorkbenchService agentWorkbenchService;
    private final AgentStatusService agentStatusService;
    private final OpsPushService opsPushService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        if (loginUser == null) {
            closeQuietly(session, "未认证");
            return;
        }
        sessionManager.register(loginUser.getUserId(), session);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("connectionId", requestId());
        data.put("userId", loginUser.getUserId());
        data.put("username", loginUser.getUsername());
        data.put("role", loginUser.getRoleCode());
        data.put("serverTime", now());
        data.put("heartbeatIntervalSeconds", HEARTBEAT_INTERVAL_SECONDS);
        if (Constants.ROLE_USER.equals(loginUser.getRoleCode())) {
            // WebSocket 线程不经过 HTTP 过滤器，查询前手动绑定租户上下文
            SessionVO current = TenantContext.callAs(loginUser.getTenantId(),
                    () -> chatService.currentSession(loginUser.getUserId()));
            if (current != null) {
                data.put("sessionId", current.getId());
            }
        }
        sessionManager.sendToUser(loginUser.getUserId(), serverMessage("CONNECTED", data));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        LoginUser loginUser = getLoginUser(session);
        if (loginUser == null) {
            closeQuietly(session, "未认证");
            return;
        }
        WsMessage request;
        try {
            request = objectMapper.readValue(message.getPayload(), WsMessage.class);
        } catch (Exception e) {
            sendError(loginUser, null, 400, "WS_8002", "消息格式不正确", null);
            return;
        }
        String type = request.getType();
        if (!StringUtils.hasText(type)) {
            sendError(loginUser, request, 400, "WS_8002", "消息类型不能为空", null);
            return;
        }

        try {
            // 绑定租户上下文：WebSocket 线程不经过 HTTP 过滤器，需手动设置并在 finally 中清理
            TenantContext.setTenantId(loginUser.getTenantId());
            switch (type.toUpperCase(Locale.ROOT)) {
                case "PING":
                case "HEARTBEAT":
                    handlePing(loginUser, request);
                    break;
                case "USER_MESSAGE":
                    handleMessage(loginUser, request, true);
                    break;
                case "AGENT_MESSAGE":
                    handleMessage(loginUser, request, false);
                    break;
                case "RATING":
                    handleRating(loginUser, request);
                    break;
                case "SESSION_CLOSE":
                    handleClose(loginUser, request);
                    break;
                case "ACK":
                    log.debug("收到客户端消息确认：userId={}, requestId={}",
                            loginUser.getUserId(), request.getRequestId());
                    break;
                case "TRANSFER_REQUEST":
                    handleTransfer(loginUser, request);
                    break;
                case "TYPING":
                    handleTyping(loginUser, request);
                    break;
                case "AGENT_ONLINE":
                    handleAgentOnline(loginUser, request);
                    break;
                case "AGENT_OFFLINE":
                    handleAgentOffline(loginUser, request);
                    break;
                case "SCREEN_SUBSCRIBE":
                    handleScreenSubscribe(loginUser, request);
                    break;
                case "SCREEN_UNSUBSCRIBE":
                    handleScreenUnsubscribe(loginUser, request);
                    break;
                default:
                    sendError(loginUser, request, 400, "WS_8003",
                            "不支持的消息类型：" + type, sessionId(request));
                    break;
            }
        } catch (BusinessException e) {
            sendError(loginUser, request, e.getCode(), bizCode(e.getCode()),
                    e.getMessage(), sessionId(request));
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败：type={}, error={}", type, e.getMessage(), e);
            sendError(loginUser, request, 500, "WS_8000", "服务器处理消息失败", sessionId(request));
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LoginUser loginUser = getLoginUser(session);
        if (loginUser != null) {
            sessionManager.unregister(loginUser.getUserId(), session);
            // 断连即退订大屏，避免订阅表随历史连接累积
            opsPushService.unsubscribeQuietly(loginUser.getUserId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常：{}", exception.getMessage());
        closeQuietly(session, "传输异常");
    }

    /**
     * 心跳：坐席顺带刷新在线心跳，并回填客户端 requestId。
     */
    private void handlePing(LoginUser loginUser, WsMessage request) {
        if (Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            agentStatusService.heartbeat(loginUser.getUserId());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverTime", now());
        data.put("serverTimestamp", System.currentTimeMillis());
        sessionManager.sendToUser(loginUser.getUserId(), new WsMessage(
                "PONG", request.getRequestId(), System.currentTimeMillis(), data));
    }

    /**
     * 用户/坐席消息：委托 ChatService 落库、AI 应答或对端推送，成功后回 MESSAGE_ACK。
     */
    private void handleMessage(LoginUser loginUser, WsMessage request, boolean userMessage) {
        String role = loginUser.getRoleCode();
        if (userMessage && !Constants.ROLE_USER.equals(role)) {
            sendError(loginUser, request, 403, "WS_8004", "当前角色无权发送用户消息", sessionId(request));
            return;
        }
        if (!userMessage && !Constants.ROLE_AGENT.equals(role)) {
            sendError(loginUser, request, 403, "WS_8004", "当前角色无权发送坐席消息", sessionId(request));
            return;
        }
        Map<String, Object> data = data(request);
        Long sessionId = longValue(data.get("sessionId"));
        if (sessionId == null || sessionId < 1) {
            sendError(loginUser, request, 400, "WS_8002", "sessionId 不能为空", null);
            return;
        }

        SendMessageRequest payload = new SendMessageRequest();
        payload.setContent(stringValue(data.get("content")));
        payload.setContentType(stringValue(data.get("contentType")));
        payload.setClientMsgId(stringValue(data.get("clientMsgId")));

        SendMessageResponse response = chatService.sendMessage(sessionId, loginUser, payload);

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("clientMsgId", payload.getClientMsgId());
        ack.put("messageId", response.getMessage().getId());
        ack.put("sessionId", sessionId);
        ack.put("fallbackCount", response.getFallbackCount());
        ack.put("suggestTransfer", response.isSuggestTransfer());
        ack.put("serverTime", now());
        sessionManager.sendToUser(loginUser.getUserId(), new WsMessage(
                "MESSAGE_ACK", request.getRequestId(), System.currentTimeMillis(), ack));
    }

    /**
     * 会话评分：仅用户本人、会话已结束且未评价时允许。
     */
    private void handleRating(LoginUser loginUser, WsMessage request) {
        if (!Constants.ROLE_USER.equals(loginUser.getRoleCode())) {
            sendError(loginUser, request, 403, "WS_8004", "仅用户可评价会话", sessionId(request));
            return;
        }
        Map<String, Object> data = data(request);
        Long sessionId = longValue(data.get("sessionId"));
        if (sessionId == null || sessionId < 1) {
            sendError(loginUser, request, 400, "WS_8002", "sessionId 不能为空", null);
            return;
        }
        RatingRequest payload = new RatingRequest();
        payload.setRating(intValue(data.get("rating")));
        payload.setRatingComment(stringValue(data.get("ratingComment")));

        chatService.rateSession(sessionId, loginUser.getUserId(), payload);
        chatPushService.pushSystemNotice(loginUser.getUserId(), "INFO", "评价成功",
                "感谢您的评价，我们会继续努力！", "SESSION", sessionId, null);
    }

    /**
     * 结束会话：用户与坐席均可发起，管理员走 REST 通道。
     */
    private void handleClose(LoginUser loginUser, WsMessage request) {
        String role = loginUser.getRoleCode();
        if (!Constants.ROLE_USER.equals(role) && !Constants.ROLE_AGENT.equals(role)) {
            sendError(loginUser, request, 403, "WS_8004", "当前角色无权结束会话", sessionId(request));
            return;
        }
        Map<String, Object> data = data(request);
        Long sessionId = longValue(data.get("sessionId"));
        if (sessionId == null || sessionId < 1) {
            sendError(loginUser, request, 400, "WS_8002", "sessionId 不能为空", null);
            return;
        }
        CloseSessionRequest payload = new CloseSessionRequest();
        payload.setCloseReason(stringValue(data.get("closeReason")));
        chatService.closeSession(sessionId, loginUser, payload);
    }

    /**
     * 请求转人工（C-04）：仅用户本人会话，排队结果由 TRANSFER_QUEUED 推送。
     */
    private void handleTransfer(LoginUser loginUser, WsMessage request) {
        if (!Constants.ROLE_USER.equals(loginUser.getRoleCode())) {
            sendError(loginUser, request, 403, "WS_8004", "仅用户可请求转人工", sessionId(request));
            return;
        }
        Map<String, Object> data = data(request);
        Long sessionId = longValue(data.get("sessionId"));
        if (sessionId == null || sessionId < 1) {
            sendError(loginUser, request, 400, "WS_8002", "sessionId 不能为空", null);
            return;
        }
        TransferRequest payload = new TransferRequest();
        payload.setReason(stringValue(data.get("reason")));
        // 咨询类型（技能组）与 REST 转人工保持一致，未传表示不限技能组
        payload.setSkillGroupId(longValue(data.get("skillGroupId")));
        chatService.transfer(sessionId, loginUser.getUserId(), payload);
    }

    /**
     * 输入状态（C-03）：校验会话权限后节流转发给对端。
     */
    private void handleTyping(LoginUser loginUser, WsMessage request) {
        String role = loginUser.getRoleCode();
        if (!Constants.ROLE_USER.equals(role) && !Constants.ROLE_AGENT.equals(role)) {
            sendError(loginUser, request, 403, "WS_8004", "当前角色不支持输入状态同步", sessionId(request));
            return;
        }
        Map<String, Object> data = data(request);
        Long sessionId = longValue(data.get("sessionId"));
        if (sessionId == null || sessionId < 1) {
            sendError(loginUser, request, 400, "WS_8002", "sessionId 不能为空", null);
            return;
        }
        ChatSession session = chatService.requireSession(sessionId);
        chatService.checkAccess(session, loginUser);
        if (!allowTyping(sessionId, role)) {
            return;
        }
        Object typingValue = data.get("typing");
        boolean typing = typingValue == null || Boolean.parseBoolean(String.valueOf(typingValue));
        if (Constants.ROLE_USER.equals(role)) {
            chatPushService.pushTyping(session.getAgentId(), sessionId, SenderType.USER.getCode(), typing);
        } else {
            chatPushService.pushTyping(session.getUserId(), sessionId, SenderType.AGENT.getCode(), typing);
        }
    }

    /**
     * 坐席上线（C-07）：上线后由分配器接管排队会话。
     */
    private void handleAgentOnline(LoginUser loginUser, WsMessage request) {
        if (!Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            sendError(loginUser, request, 403, "WS_8004", "仅坐席可上线", null);
            return;
        }
        Integer maxConcurrent = intValue(data(request).get("maxConcurrent"));
        AgentStatusVO status = agentWorkbenchService.online(loginUser.getUserId(), maxConcurrent);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", status.getAgentId());
        payload.put("agentName", status.getAgentName());
        payload.put("onlineStatus", status.getOnlineStatus());
        payload.put("currentCount", status.getCurrentCount());
        payload.put("maxConcurrent", status.getMaxConcurrent());
        payload.put("onlineTime", now());
        sessionManager.sendToUser(loginUser.getUserId(), new WsMessage(
                "AGENT_ONLINE", request.getRequestId(), System.currentTimeMillis(), payload));
    }

    /**
     * 坐席下线（C-08）：仍有进行中会话时返回 WS_8005，连接保持。
     */
    private void handleAgentOffline(LoginUser loginUser, WsMessage request) {
        if (!Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            sendError(loginUser, request, 403, "WS_8004", "仅坐席可下线", null);
            return;
        }
        AgentStatusVO status;
        try {
            status = agentWorkbenchService.offline(loginUser.getUserId());
        } catch (BusinessException e) {
            sendError(loginUser, request, e.getCode(), "WS_8005", e.getMessage(), null);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", status.getAgentId());
        payload.put("agentName", status.getAgentName());
        payload.put("onlineStatus", status.getOnlineStatus());
        payload.put("offlineTime", now());
        payload.put("reason", "MANUAL");
        sessionManager.sendToUser(loginUser.getUserId(), new WsMessage(
                "AGENT_OFFLINE", request.getRequestId(), System.currentTimeMillis(), payload));
    }

    /**
     * 订阅运营大屏（S6-A）：仅管理员可订阅，回执携带当前快照避免前端等下一轮推送。
     */
    private void handleScreenSubscribe(LoginUser loginUser, WsMessage request) {
        if (!Constants.ROLE_ADMIN.equals(loginUser.getRoleCode())) {
            sendError(loginUser, request, 403, "WS_8004", "仅管理员可订阅运营大屏", null);
            return;
        }
        sessionManager.sendToUser(loginUser.getUserId(), new WsMessage(
                "SCREEN_SUBSCRIBED", request.getRequestId(), System.currentTimeMillis(),
                opsPushService.subscribe(loginUser)));
    }

    /**
     * 退订运营大屏：关闭大屏页时主动调用，连接保持。
     */
    private void handleScreenUnsubscribe(LoginUser loginUser, WsMessage request) {
        opsPushService.unsubscribe(loginUser.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unsubscribed", true);
        data.put("serverTime", now());
        sessionManager.sendToUser(loginUser.getUserId(), new WsMessage(
                "SCREEN_UNSUBSCRIBED", request.getRequestId(), System.currentTimeMillis(), data));
    }

    /**
     * 输入状态节流：同一会话同一角色 1 秒内最多转发一次。
     */
    private boolean allowTyping(Long sessionId, String role) {
        try {
            String key = Constants.TYPING_THROTTLE_PREFIX + sessionId + ":" + role;
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", TYPING_THROTTLE_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            return true;
        }
    }

    private WsMessage serverMessage(String type, Map<String, Object> data) {
        return new WsMessage(type, requestId(), System.currentTimeMillis(), data);
    }

    /**
     * 错误回执：信封 requestId 由服务端生成，data.requestId 回填触发错误的客户端请求。
     */
    private void sendError(LoginUser loginUser, WsMessage request,
                           int code, String bizCode, String message, Long sessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("bizCode", bizCode);
        data.put("message", message);
        data.put("requestId", request == null ? null : request.getRequestId());
        if (sessionId != null) {
            data.put("sessionId", sessionId);
        }
        sessionManager.sendToUser(loginUser.getUserId(), serverMessage("ERROR", data));
    }

    private String bizCode(int code) {
        switch (code) {
            case 400:
                return "WS_8002";
            case 401:
                return "WS_8001";
            case 403:
                return "WS_8004";
            case 404:
                return "WS_8006";
            case 409:
                return "WS_8007";
            case 429:
                return "WS_8008";
            default:
                return "WS_8000";
        }
    }

    private LoginUser getLoginUser(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_LOGIN_USER);
        return value instanceof LoginUser ? (LoginUser) value : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(WsMessage request) {
        if (request == null || request.getData() == null) {
            return Collections.emptyMap();
        }
        return request.getData();
    }

    private Long sessionId(WsMessage request) {
        return longValue(data(request).get("sessionId"));
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.valueOf(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String requestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    private void closeQuietly(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }
}
