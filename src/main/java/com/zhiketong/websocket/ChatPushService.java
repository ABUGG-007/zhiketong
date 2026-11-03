package com.zhiketong.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.enums.CloseReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话消息 WebSocket 推送服务：统一构建 S2C 报文，避免业务层重复拼装信封。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPushService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * 推送用户消息给接入坐席（S-03）。
     */
    public void pushUserMessageToAgent(Long agentId, MessageVO message) {
        if (agentId == null) {
            return;
        }
        sessionManager.sendToUser(agentId, serverMessage("USER_MESSAGE", toMap(message)));
    }

    /**
     * 推送坐席消息给用户（S-05）。
     */
    public void pushAgentMessageToUser(Long userId, MessageVO message) {
        if (userId == null) {
            return;
        }
        sessionManager.sendToUser(userId, serverMessage("AGENT_MESSAGE", toMap(message)));
    }

    /**
     * 推送对端正在输入（S-06）。
     */
    public void pushTyping(Long targetUserId, Long sessionId, String senderType, boolean typing) {
        if (targetUserId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("senderType", senderType);
        data.put("typing", typing);
        sessionManager.sendToUser(targetUserId, serverMessage("TYPING", data));
    }

    /**
     * 推送 AI 回复（S-04），附带降级与转人工建议标记。
     */
    public void pushAiMessage(Long userId, MessageVO message) {
        if (userId == null) {
            return;
        }
        Map<String, Object> data = toMap(message);
        boolean fallback = message.getAiModel() != null && message.getAiModel().startsWith("fallback");
        data.put("fallback", fallback);
        data.put("suggestTransfer", fallback);
        sessionManager.sendToUser(userId, serverMessage("AI_MESSAGE", data));
    }

    /**
     * 推送会话结束（S-11）给用户与坐席。
     */
    public void pushSessionClosed(ChatSession session, String closedBy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId());
        data.put("closeReason", session.getCloseReason());
        data.put("endTime", session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
        data.put("closedBy", closedBy);
        data.put("ratingAllowed", true);
        data.put("summary", "会话已结束，感谢您的咨询，请对本次服务进行评价。");
        WsMessage message = serverMessage("SESSION_CLOSED", data);
        sessionManager.sendToUser(session.getUserId(), message);
        if (session.getAgentId() != null) {
            sessionManager.sendToUser(session.getAgentId(), message);
        }
    }

    /**
     * 推送系统通知（S-12）。
     */
    public void pushSystemNotice(Long userId, String level, String title, String content,
                                 String bizType, Long bizId, String action) {
        if (userId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", level);
        data.put("title", title);
        data.put("content", content);
        data.put("bizType", bizType);
        if (bizId != null) {
            data.put("bizId", bizId);
        }
        if (action != null) {
            data.put("action", action);
        }
        data.put("createTime", LocalDateTime.now());
        sessionManager.sendToUser(userId, serverMessage("SYSTEM_NOTICE", data));
    }

    /**
     * 推送已进入排队（S-07）。
     */
    public void pushTransferQueued(Long userId, Long sessionId, int queuePosition, int queueSize,
                                   int estimatedWaitSeconds, boolean agentAvailable) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("queuePosition", queuePosition);
        data.put("queueSize", queueSize);
        data.put("estimatedWaitSeconds", estimatedWaitSeconds);
        data.put("agentAvailable", agentAvailable);
        data.put("queuedAt", LocalDateTime.now());
        sessionManager.sendToUser(userId, serverMessage("TRANSFER_QUEUED", data));
    }

    /**
     * 推送坐席已接入（S-09）。
     */
    public void pushTransferAccepted(Long userId, ChatSession session, String agentName) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("id", session.getAgentId());
        agent.put("nickname", agentName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId());
        data.put("agent", agent);
        data.put("acceptTime", session.getAcceptTime());
        data.put("welcomeMessage", "您好，我是" + agentName + "，很高兴为您服务。");
        sessionManager.sendToUser(userId, serverMessage("TRANSFER_ACCEPTED", data));
    }

    /**
     * 推送排队进度（S-08）。
     */
    public void pushQueueStatus(Long userId, Long sessionId, int queuePosition, int queueSize,
                                int estimatedWaitSeconds) {
        if (userId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("queuePosition", queuePosition);
        data.put("queueSize", queueSize);
        data.put("estimatedWaitSeconds", estimatedWaitSeconds);
        data.put("updatedAt", LocalDateTime.now());
        sessionManager.sendToUser(userId, serverMessage("QUEUE_STATUS", data));
    }

    /**
     * 推送告警事件（S6-B）：管理员与责任坐席实时收到，前端据此弹提醒与刷新告警列表。
     *
     * @param userId 接收人用户 ID
     * @param data   告警事件报文（与 REST 返回结构一致）
     */
    public void pushAlert(Long userId, Map<String, Object> data) {
        if (userId == null) {
            return;
        }
        sessionManager.sendToUser(userId, serverMessage("ALERT_EVENT", data));
    }

    /**
     * 推送组长强插消息（S6-B，AS-10）：访客与接待坐席双方可见。
     *
     * @param userId  访客用户 ID
     * @param agentId 接待坐席 ID，可为空
     * @param message 已落库的组长消息
     */
    public void pushSupervisorMessage(Long userId, Long agentId, MessageVO message) {
        Map<String, Object> data = toMap(message);
        sessionManager.sendToUser(userId, serverMessage("SUPERVISOR_MESSAGE", data));
        if (agentId != null) {
            sessionManager.sendToUser(agentId, serverMessage("SUPERVISOR_MESSAGE", data));
        }
    }

    /**
     * 推送会话已分配给坐席（S-10），附带最近消息与用户信息。
     */
    public void pushSessionAssigned(Long agentId, Long sessionId, Map<String, Object> session,
                                    Map<String, Object> user, List<MessageVO> lastMessages, boolean transfer) {
        if (agentId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("session", session);
        data.put("user", user);
        data.put("lastMessages", lastMessages == null ? new java.util.ArrayList<>() : lastMessages);
        data.put("transfer", transfer);
        sessionManager.sendToUser(agentId, serverMessage("SESSION_ASSIGNED", data));
    }

    /**
     * 构建服务端主动推送信封。
     */
    public WsMessage serverMessage(String type, Map<String, Object> data) {
        return new WsMessage(type, UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis(), data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(MessageVO message) {
        return objectMapper.convertValue(message, LinkedHashMap.class);
    }

    /**
     * 获取关闭原因的默认系统提示。
     */
    public String closeSummary(String closeReason) {
        CloseReason reason = CloseReason.fromCode(closeReason);
        return reason == null ? "会话已结束" : reason.getDescription();
    }
}
