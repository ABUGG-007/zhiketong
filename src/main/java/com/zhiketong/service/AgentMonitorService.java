package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.dto.monitor.MonitorDetailVO;
import com.zhiketong.dto.monitor.MonitorSessionVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.websocket.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组长会话监控服务（S6-B，AS-10）：实时监听、强插发言、强制接管与强制派单。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>强插消息以 {@link SenderType#SUPERVISOR} 落库，访客与坐席双方可见，事后可追溯介入人；</li>
 *   <li>接管与派单复用坐席负载口径：原坐席负载 -1、新坐席负载 +1，接待并发满即转忙碌；</li>
 *   <li>所有介入动作写审计，并在会话内留系统消息，避免组长操作对坐席与访客不可见。</li>
 * </ol>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMonitorService {

    /** 监听列表默认只看进行中的会话 */
    private static final List<String> ACTIVE_STATUS = Arrays.asList(
            SessionStatus.AI.getCode(), SessionStatus.WAITING.getCode(), SessionStatus.HUMAN.getCode());

    /** 监听详情回看的消息条数 */
    private static final int DETAIL_MESSAGE_LIMIT = 50;

    /** 列表最后一条消息截断长度 */
    private static final int LAST_MESSAGE_MAX = 60;

    private final ChatService chatService;
    private final ChatMessageService chatMessageService;
    private final QueueService queueService;
    private final AlertService alertService;
    private final ChatPushService chatPushService;
    private final AuditLogService auditLogService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 监听列表（R-199）：进行中会话 + 责任坐席 + 待回复时长 + 未闭环告警数。
     *
     * @param status  会话状态过滤，为空时取 AI/WAITING/HUMAN
     * @param agentId 责任坐席过滤，为空不过滤
     * @param keyword 会话编号模糊匹配，为空不过滤
     */
    public PageResult<MonitorSessionVO> pageSessions(Integer pageNum, Integer pageSize, String status,
                                                     Long agentId, String keyword) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, Constants.MAX_PAGE_SIZE);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ChatSession::getStatus, status.trim());
        } else {
            wrapper.in(ChatSession::getStatus, ACTIVE_STATUS);
        }
        if (agentId != null) {
            wrapper.eq(ChatSession::getAgentId, agentId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ChatSession::getSessionNo, kw));
        }
        wrapper.orderByAsc(ChatSession::getStatus)
                .orderByAsc(ChatSession::getCreateTime);
        Page<ChatSession> page = chatSessionMapper.selectPage(new Page<>(num, size), wrapper);

        List<ChatSession> records = page.getRecords();
        Map<Long, ChatMessage> lastMessages = lastMessages(records);
        Map<Long, String> names = userNames(records);
        List<Long> sessionIds = new ArrayList<>(records.size());
        for (ChatSession session : records) {
            sessionIds.add(session.getId());
        }
        Map<Long, Long> alertCounts = alertService.countOpenBySessions(sessionIds);
        LocalDateTime now = LocalDateTime.now();

        List<MonitorSessionVO> list = new ArrayList<>(records.size());
        for (ChatSession session : records) {
            MonitorSessionVO vo = new MonitorSessionVO();
            vo.setSessionId(session.getId());
            vo.setSessionNo(session.getSessionNo());
            vo.setStatus(session.getStatus());
            vo.setUserId(session.getUserId());
            vo.setUserName(names.get(session.getUserId()));
            vo.setAgentId(session.getAgentId());
            vo.setAgentName(names.get(session.getAgentId()));
            vo.setMessageCount(session.getMessageCount());
            vo.setDurationSeconds(durationSeconds(session, now));
            ChatMessage last = lastMessages.get(session.getId());
            if (last != null) {
                vo.setLastMessage(truncate(last.getContent(), LAST_MESSAGE_MAX));
                vo.setLastSenderType(last.getSenderType());
                vo.setLastMessageTime(last.getCreateTime());
                if (SenderType.USER.getCode().equals(last.getSenderType()) && last.getCreateTime() != null) {
                    vo.setPendingSeconds(Duration.between(last.getCreateTime(), now).getSeconds());
                }
            }
            vo.setOpenAlertCount(alertCounts.getOrDefault(session.getId(), 0L));
            vo.setCreateTime(session.getCreateTime());
            list.add(vo);
        }
        return PageResult.of(page, list);
    }

    /**
     * 监听详情（R-200）：会话信息 + 最近消息（含内部备注）+ 未闭环告警。
     */
    public MonitorDetailVO detail(Long sessionId) {
        ChatSession session = chatService.requireSession(sessionId);
        MonitorDetailVO vo = new MonitorDetailVO();
        vo.setSession(chatService.toVO(session));
        Map<Long, String> names = userNames(Collections.singletonList(session));
        vo.setUserName(names.get(session.getUserId()));
        vo.setAgentName(names.get(session.getAgentId()));
        vo.setMessages(chatMessageService.recent(sessionId, DETAIL_MESSAGE_LIMIT));
        vo.setAlerts(alertService.openBySession(sessionId));
        vo.setPendingSeconds(pendingSeconds(sessionId));
        boolean closed = SessionStatus.CLOSED.getCode().equals(session.getStatus());
        vo.setWhisperAllowed(!closed);
        vo.setTakeoverAllowed(!closed && !SessionStatus.AI.getCode().equals(session.getStatus()));
        if (closed) {
            vo.setTakeoverHint("会话已结束，无需接管");
        } else if (SessionStatus.AI.getCode().equals(session.getStatus())) {
            vo.setTakeoverHint("机器人接待中的会话无需接管，可直接在访客端发起转人工");
        }
        vo.setServerTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 强插发言（R-201）：组长以 SUPERVISOR 身份对客发送，访客与坐席双方实时可见。
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageVO whisper(Long sessionId, String content, LoginUser operator) {
        ChatSession session = chatService.requireSession(sessionId);
        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话已结束，无法强插发言");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "强插内容不能为空");
        }
        ChatMessage message = chatMessageService.save(sessionId, SenderType.SUPERVISOR,
                operator.getUserId(), text, "TEXT", null, null, null, true);
        MessageVO vo = chatMessageService.toVO(message);
        chatPushService.pushSupervisorMessage(session.getUserId(), session.getAgentId(), vo);

        auditLogService.record(AuditModule.ALERT, AuditAction.UPDATE)
                .target("CHAT_SESSION", session.getId(), session.getSessionNo())
                .detail("组长强插发言：" + text)
                .save();
        log.info("组长强插发言：sessionId={}, operatorId={}", sessionId, operator.getUserId());
        return vo;
    }

    /**
     * 强制接管（R-202）：排队中由组长直接接入；人工接待中则从原坐席手中接管。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonitorDetailVO takeover(Long sessionId, LoginUser operator) {
        ChatSession session = chatService.requireSession(sessionId);
        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话已结束，无法接管");
        }
        if (SessionStatus.AI.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "机器人接待中的会话无需接管");
        }
        Long operatorId = operator.getUserId();
        Long previousAgentId = session.getAgentId();
        if (SessionStatus.HUMAN.getCode().equals(session.getStatus())
                && operatorId.equals(previousAgentId)) {
            return detail(sessionId);
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .in(ChatSession::getStatus, SessionStatus.WAITING.getCode(), SessionStatus.HUMAN.getCode())
                .set(ChatSession::getStatus, SessionStatus.HUMAN.getCode())
                .set(ChatSession::getAgentId, operatorId)
                .set(ChatSession::getAcceptTime, now)
                .set(ChatSession::getQueueEnterTime, null));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "会话状态已变化，请刷新后重试");
        }
        queueService.remove(sessionId);
        if (previousAgentId != null && !previousAgentId.equals(operatorId)) {
            decrementLoad(previousAgentId);
        }
        incrementLoad(operatorId);

        String operatorName = displayName(operator);
        String remark = previousAgentId == null
                ? "组长 " + operatorName + " 强制接管会话"
                : "组长 " + operatorName + " 从原坐席手中接管会话";
        chatMessageService.save(sessionId, SenderType.SYSTEM, 0L, remark, "TEXT",
                null, null, "SYSTEM", false);

        ChatSession latest = chatSessionMapper.selectById(sessionId);
        chatPushService.pushTransferAccepted(latest.getUserId(), latest, operatorName);
        chatPushService.pushSessionAssigned(operatorId, sessionId, sessionBrief(latest),
                userBrief(latest), chatMessageService.recent(sessionId, 20), true);
        if (previousAgentId != null && !previousAgentId.equals(operatorId)) {
            chatPushService.pushSystemNotice(previousAgentId, "WARN", "会话已被组长接管",
                    "会话 " + latest.getSessionNo() + " 已由组长 " + operatorName + " 接管",
                    "SESSION", sessionId, null);
        }

        auditLogService.record(AuditModule.ALERT, AuditAction.UPDATE)
                .target("CHAT_SESSION", sessionId, latest.getSessionNo())
                .detail(remark)
                .save();
        log.info("组长强制接管：sessionId={}, from={}, to={}", sessionId, previousAgentId, operatorId);
        return detail(sessionId);
    }

    /**
     * 强制派单（R-203）：把排队中或他人接待中的会话指派给指定坐席。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonitorDetailVO reassign(Long sessionId, Long targetAgentId, String reason, LoginUser operator) {
        ChatSession session = chatService.requireSession(sessionId);
        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话已结束，无法派单");
        }
        if (SessionStatus.AI.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "机器人接待中的会话无需派单");
        }
        SysUser target = requireAvailableAgent(targetAgentId);
        Long previousAgentId = session.getAgentId();
        if (targetAgentId.equals(previousAgentId)) {
            return detail(sessionId);
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .in(ChatSession::getStatus, SessionStatus.WAITING.getCode(), SessionStatus.HUMAN.getCode())
                .set(ChatSession::getStatus, SessionStatus.HUMAN.getCode())
                .set(ChatSession::getAgentId, targetAgentId)
                .set(ChatSession::getAcceptTime, now)
                .set(ChatSession::getQueueEnterTime, null));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "会话状态已变化，请刷新后重试");
        }
        queueService.remove(sessionId);
        if (previousAgentId != null && !previousAgentId.equals(targetAgentId)) {
            decrementLoad(previousAgentId);
        }
        incrementLoad(targetAgentId);

        String targetName = displayName(target);
        String remark = "组长 " + displayName(operator) + " 将会话指派给 " + targetName
                + (StringUtils.hasText(reason) ? "，原因：" + reason.trim() : "");
        chatMessageService.save(sessionId, SenderType.SYSTEM, 0L, remark, "TEXT",
                null, null, "SYSTEM", false);

        ChatSession latest = chatSessionMapper.selectById(sessionId);
        chatPushService.pushTransferAccepted(latest.getUserId(), latest, targetName);
        chatPushService.pushSessionAssigned(targetAgentId, sessionId, sessionBrief(latest),
                userBrief(latest), chatMessageService.recent(sessionId, 20), true);
        if (previousAgentId != null && !previousAgentId.equals(targetAgentId)) {
            chatPushService.pushSystemNotice(previousAgentId, "WARN", "会话已被组长改派",
                    "会话 " + latest.getSessionNo() + " 已改派给 " + targetName,
                    "SESSION", sessionId, null);
        }

        auditLogService.record(AuditModule.ALERT, AuditAction.UPDATE)
                .target("CHAT_SESSION", sessionId, latest.getSessionNo())
                .detail(remark)
                .save();
        log.info("组长强制派单：sessionId={}, from={}, to={}", sessionId, previousAgentId, targetAgentId);
        return detail(sessionId);
    }

    /**
     * 校验目标坐席：必须为启用状态的坐席账号且当前可接入。
     */
    private SysUser requireAvailableAgent(Long agentId) {
        if (agentId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标坐席不能为空");
        }
        SysUser agent = sysUserMapper.selectById(agentId);
        if (agent == null || !Constants.ROLE_AGENT.equals(agent.getRoleCode())
                || !Integer.valueOf(1).equals(agent.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "目标坐席不存在或不可用");
        }
        AgentStatus status = agentStatusMapper.selectOne(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getAgentId, agentId)
                .last("LIMIT 1"));
        if (status == null || "OFFLINE".equals(status.getOnlineStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "目标坐席未上线");
        }
        int current = status.getCurrentCount() == null ? 0 : status.getCurrentCount();
        int max = status.getMaxConcurrent() == null ? 0 : status.getMaxConcurrent();
        if (current >= max) {
            throw new BusinessException(ResultCode.CONFLICT, "目标坐席接待并发已满");
        }
        return agent;
    }

    private void incrementLoad(Long agentId) {
        agentStatusMapper.update(null, new LambdaUpdateWrapper<AgentStatus>()
                .setSql("online_status = CASE WHEN current_count + 1 >= max_concurrent THEN 'BUSY' ELSE online_status END")
                .setSql("current_count = current_count + 1")
                .eq(AgentStatus::getAgentId, agentId));
        queueService.cacheAgentStatus(agentId);
    }

    private void decrementLoad(Long agentId) {
        agentStatusMapper.update(null, new LambdaUpdateWrapper<AgentStatus>()
                .setSql("current_count = CASE WHEN current_count > 0 THEN current_count - 1 ELSE 0 END")
                .setSql("online_status = CASE WHEN online_status = 'BUSY' AND current_count < max_concurrent THEN 'ONLINE' ELSE online_status END")
                .eq(AgentStatus::getAgentId, agentId));
        queueService.cacheAgentStatus(agentId);
    }

    private Map<Long, ChatMessage> lastMessages(List<ChatSession> sessions) {
        Map<Long, ChatMessage> result = new HashMap<>();
        if (sessions.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>(sessions.size());
        for (ChatSession session : sessions) {
            ids.add(session.getId());
        }
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .in(ChatMessage::getSessionId, ids)
                .select(ChatMessage::getId, ChatMessage::getSessionId, ChatMessage::getSenderType,
                        ChatMessage::getContent, ChatMessage::getCreateTime)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT 3000"));
        for (ChatMessage message : messages) {
            result.put(message.getSessionId(), message);
        }
        return result;
    }

    private Map<Long, String> userNames(List<ChatSession> sessions) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ChatSession session : sessions) {
            if (session.getUserId() != null) {
                ids.add(session.getUserId());
            }
            if (session.getAgentId() != null) {
                ids.add(session.getAgentId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (SysUser user : sysUserMapper.selectBatchIds(ids)) {
            names.put(user.getId(), displayName(user));
        }
        return names;
    }

    private Long durationSeconds(ChatSession session, LocalDateTime now) {
        LocalDateTime from;
        if (SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
            from = session.getAcceptTime() != null ? session.getAcceptTime() : session.getCreateTime();
        } else if (SessionStatus.WAITING.getCode().equals(session.getStatus())) {
            from = session.getQueueEnterTime() != null ? session.getQueueEnterTime() : session.getCreateTime();
        } else {
            from = session.getCreateTime();
        }
        return from == null ? null : Duration.between(from, now).getSeconds();
    }

    private Long pendingSeconds(Long sessionId) {
        List<ChatMessage> recent = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        if (recent.isEmpty()) {
            return null;
        }
        ChatMessage last = recent.get(0);
        if (!SenderType.USER.getCode().equals(last.getSenderType()) || last.getCreateTime() == null) {
            return null;
        }
        return Duration.between(last.getCreateTime(), LocalDateTime.now()).getSeconds();
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String displayName(LoginUser loginUser) {
        return StringUtils.hasText(loginUser.getNickname())
                ? loginUser.getNickname() : loginUser.getUsername();
    }

    /**
     * 会话简报，用于坐席端 {@code SESSION_ASSIGNED} 推送。
     */
    private Map<String, Object> sessionBrief(ChatSession session) {
        Map<String, Object> data = new HashMap<>(6);
        data.put("sessionId", session.getId());
        data.put("sessionNo", session.getSessionNo());
        data.put("status", session.getStatus());
        data.put("userId", session.getUserId());
        data.put("agentId", session.getAgentId());
        data.put("acceptTime", session.getAcceptTime());
        return data;
    }

    /**
     * 访客简报，用于坐席端 {@code SESSION_ASSIGNED} 推送。
     */
    private Map<String, Object> userBrief(ChatSession session) {
        Map<String, Object> data = new HashMap<>(4);
        SysUser user = sysUserMapper.selectById(session.getUserId());
        data.put("id", session.getUserId());
        data.put("nickname", displayName(user));
        return data;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String single = value.replaceAll("\\s+", " ").trim();
        return single.length() > max ? single.substring(0, max) + "…" : single;
    }
}
