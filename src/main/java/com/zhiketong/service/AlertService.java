package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.alert.AlertEventVO;
import com.zhiketong.dto.alert.AlertScanResultVO;
import com.zhiketong.dto.alert.AlertSummaryVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.AlertEvent;
import com.zhiketong.entity.AlertRule;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.enums.AlertMetric;
import com.zhiketong.enums.AlertSeverity;
import com.zhiketong.enums.AlertStatus;
import com.zhiketong.enums.AlertTargetType;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.AlertEventMapper;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.TenantContext;
import com.zhiketong.websocket.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 告警事件服务（S6-B，AS-11）：规则扫描、事件去重、确认关闭与告警中心查询。
 *
 * <p>扫描引擎按 {@code metric_code} 分派 9 类判定，事件去重键为
 * 「规则 + 目标 + 未关闭状态」：冷却期内重复触发只抑制不落新事件；
 * 冷却期外同一事件累加 {@code occur_count} 并重新推送。</p>
 *
 * <p>通知对象由规则 {@code notify_role} 决定，在租户内解析为在线管理员、
 * 规则归属坐席与会话责任坐席，通过 WebSocket 的 {@code ALERT_EVENT} 报文实时下发。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    /** 单轮最多评估的接待中会话数，避免大租户扫描超时 */
    private static final int MAX_ACTIVE_SESSIONS = 200;

    /** 待回复判定回溯的消息窗口（小时） */
    private static final int MESSAGE_WINDOW_HOURS = 6;

    /** 敏感词扫描窗口（分钟），按扫描周期滚动，避免每次全量比对历史消息 */
    private static final int SENSITIVE_WINDOW_MINUTES = 15;

    /** 差评回溯窗口（小时） */
    private static final int LOW_RATING_WINDOW_HOURS = 24;

    /** 单轮自动关闭上限 */
    private static final int AUTO_CLOSE_LIMIT = 200;

    /** 未关闭的告警状态 */
    private static final List<String> UNCLOSED_STATUS = Arrays.asList(
            AlertStatus.OPEN.getCode(), AlertStatus.ACKED.getCode());

    /** 未关闭工单状态 */
    private static final List<String> OPEN_ORDER_STATUS = Arrays.asList(
            OrderStatus.PENDING.getCode(), OrderStatus.ASSIGNED.getCode(),
            OrderStatus.PROCESSING.getCode(), OrderStatus.WAITING_CUSTOMER.getCode(),
            OrderStatus.REOPENED.getCode());

    private final AlertRuleService alertRuleService;
    private final AlertEventMapper alertEventMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final WorkOrderMapper workOrderMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final AuditLogService auditLogService;
    private final ChatPushService chatPushService;

    /** 未关闭告警超过该小时数自动关闭，防止历史告警长期堆积 */
    @Value("${zhiketong.alert.auto-close-hours:24}")
    private int autoCloseHours;

    /**
     * 执行一轮告警扫描（R-187），需在具体租户作用域内调用。
     *
     * @return 本轮触发、复发、抑制与自动关闭统计
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertScanResultVO scan() {
        long start = System.currentTimeMillis();
        AlertScanResultVO result = new AlertScanResultVO();
        result.setScanTime(LocalDateTime.now());
        List<AlertRule> rules = alertRuleService.listEnabledRules();
        result.setRuleCount(rules.size());
        if (rules.isEmpty()) {
            result.setAutoClosedCount(autoClose());
            result.setCostMillis(System.currentTimeMillis() - start);
            result.setMessage("当前租户没有启用的告警规则");
            return result;
        }

        ScanContext context = loadContext(rules);
        for (AlertRule rule : rules) {
            try {
                evaluate(rule, context, result);
            } catch (Exception e) {
                log.warn("告警规则评估失败：ruleId={}, metric={}, error={}",
                        rule.getId(), rule.getMetricCode(), e.getMessage());
            }
        }
        result.setAutoClosedCount(autoClose());
        result.setCostMillis(System.currentTimeMillis() - start);
        result.setMessage("规则 " + rules.size() + " 条，新增 " + result.getCreatedCount()
                + " 条，复发 " + result.getRepeatedCount() + " 条，冷却抑制 "
                + result.getSuppressedCount() + " 条，自动关闭 " + result.getAutoClosedCount() + " 条");
        log.info("告警扫描完成：{}", result.getMessage());
        return result;
    }

    /**
     * 告警分页（R-185 管理端 / R-195 坐席端）。
     *
     * @param agentId 坐席视角时只看责任坐席为本人或本人工单的告警，管理端传 null
     */
    public PageResult<AlertEventVO> page(Integer pageNum, Integer pageSize, String status, String severity,
                                         String ruleType, String keyword, Long agentId) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, Constants.MAX_PAGE_SIZE);
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), AlertEvent::getStatus, status)
                .eq(StringUtils.hasText(severity), AlertEvent::getSeverity, severity)
                .eq(StringUtils.hasText(ruleType), AlertEvent::getRuleType, ruleType)
                .eq(agentId != null, AlertEvent::getAgentId, agentId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AlertEvent::getTitle, kw)
                    .or().like(AlertEvent::getDetail, kw)
                    .or().like(AlertEvent::getRuleName, kw));
        }
        wrapper.orderByAsc(AlertEvent::getStatus)
                .orderByDesc(AlertEvent::getLastTime)
                .orderByDesc(AlertEvent::getId);
        Page<AlertEvent> page = alertEventMapper.selectPage(new Page<>(num, size), wrapper);
        List<AlertEventVO> list = new ArrayList<>(page.getRecords().size());
        for (AlertEvent event : page.getRecords()) {
            list.add(AlertEventVO.of(event));
        }
        return PageResult.of(page, list);
    }

    /**
     * 告警概览（R-186）：告警中心顶部指标。
     *
     * @param agentId 坐席视角时只统计本人告警，管理端传 null
     */
    public AlertSummaryVO summary(Long agentId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        AlertSummaryVO vo = new AlertSummaryVO();
        vo.setServerTime(now);
        vo.setOpenCount(countByStatus(AlertStatus.OPEN.getCode(), agentId, null));
        vo.setAckedCount(countByStatus(AlertStatus.ACKED.getCode(), agentId, null));
        LambdaQueryWrapper<AlertEvent> last24hWrapper = new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getLastTime, last24h)
                .eq(agentId != null, AlertEvent::getAgentId, agentId);
        vo.setLast24hCount(safeLong(alertEventMapper.selectCount(last24hWrapper)));
        vo.setCriticalCount(safeLong(alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getLastTime, last24h)
                .eq(AlertEvent::getSeverity, AlertSeverity.CRITICAL.getCode())
                .eq(agentId != null, AlertEvent::getAgentId, agentId))));

        List<AlertEvent> closed = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getStatus, AlertStatus.CLOSED.getCode())
                .ge(AlertEvent::getCloseTime, last24h)
                .eq(agentId != null, AlertEvent::getAgentId, agentId)
                .select(AlertEvent::getId, AlertEvent::getHandleSeconds)
                .last("LIMIT 500"));
        long handleSum = 0;
        for (AlertEvent event : closed) {
            handleSum += event.getHandleSeconds() == null ? 0 : event.getHandleSeconds();
        }
        vo.setAvgHandleSeconds(closed.isEmpty() ? 0 : handleSum / closed.size());
        vo.setEnabledRuleCount(safeLong(alertRuleService.listEnabledRules().stream()
                .filter(rule -> rule.getOwnerId() != null && rule.getOwnerId() == 0L).count()));

        List<AlertEvent> latest = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getStatus, AlertStatus.OPEN.getCode())
                .eq(agentId != null, AlertEvent::getAgentId, agentId)
                .orderByDesc(AlertEvent::getLastTime)
                .last("LIMIT 1"));
        if (!latest.isEmpty()) {
            vo.setLatestTitle(latest.get(0).getTitle());
            vo.setLatestTime(latest.get(0).getLastTime());
        }
        return vo;
    }

    /**
     * 确认告警（R-188 管理端 / R-197 坐席端）：OPEN → ACKED，记录接手人。
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertEventVO ack(Long id, String note, LoginUser operator) {
        AlertEvent event = requireEvent(id);
        assertHandledBy(event, operator);
        if (AlertStatus.CLOSED.getCode().equals(event.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "告警已关闭，无需确认");
        }
        if (AlertStatus.OPEN.getCode().equals(event.getStatus())) {
            event.setStatus(AlertStatus.ACKED.getCode());
        }
        event.setAckBy(operator.getUserId());
        event.setAckName(displayName(operator));
        event.setAckTime(LocalDateTime.now());
        event.setAckNote(truncate(note, 255));
        alertEventMapper.updateById(event);

        auditLogService.record(AuditModule.ALERT, AuditAction.UPDATE)
                .target("ALERT_EVENT", event.getId(), event.getTitle())
                .detail("确认告警：" + event.getTitle())
                .after(event)
                .save();
        return AlertEventVO.of(event);
    }

    /**
     * 关闭告警（R-189 管理端 / R-198 坐席端）：写入关闭人与处理耗时。
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertEventVO close(Long id, LoginUser operator) {
        AlertEvent event = requireEvent(id);
        assertHandledBy(event, operator);
        if (AlertStatus.CLOSED.getCode().equals(event.getStatus())) {
            return AlertEventVO.of(event);
        }
        LocalDateTime now = LocalDateTime.now();
        event.setStatus(AlertStatus.CLOSED.getCode());
        event.setCloseBy(operator.getUserId());
        event.setCloseTime(now);
        event.setHandleSeconds(handleSeconds(event.getFirstTime(), now));
        alertEventMapper.updateById(event);

        auditLogService.record(AuditModule.ALERT, AuditAction.UPDATE)
                .target("ALERT_EVENT", event.getId(), event.getTitle())
                .detail("关闭告警：" + event.getTitle())
                .after(event)
                .save();
        return AlertEventVO.of(event);
    }

    /**
     * 查询某会话当前未关闭的告警，供组长监听面板直接展示（R-200）。
     */
    public List<AlertEventVO> openBySession(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<AlertEvent> events = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getSessionId, sessionId)
                .in(AlertEvent::getStatus, UNCLOSED_STATUS)
                .orderByDesc(AlertEvent::getLastTime)
                .last("LIMIT 20"));
        List<AlertEventVO> list = new ArrayList<>(events.size());
        for (AlertEvent event : events) {
            list.add(AlertEventVO.of(event));
        }
        return list;
    }

    /**
     * 统计某会话未关闭告警数，供监控列表角标使用。
     */
    public Map<Long, Long> countOpenBySessions(List<Long> sessionIds) {
        Map<Long, Long> result = new HashMap<>();
        if (sessionIds == null || sessionIds.isEmpty()) {
            return result;
        }
        List<AlertEvent> events = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .in(AlertEvent::getSessionId, sessionIds)
                .in(AlertEvent::getStatus, UNCLOSED_STATUS)
                .select(AlertEvent::getId, AlertEvent::getSessionId));
        for (AlertEvent event : events) {
            result.merge(event.getSessionId(), 1L, Long::sum);
        }
        return result;
    }

    /**
     * 超时自动关闭：未关闭告警超过配置小时数未再触发即视为条件已消失。
     *
     * @return 本轮关闭条数
     */
    private int autoClose() {
        LocalDateTime limit = LocalDateTime.now().minusHours(Math.max(1, autoCloseHours));
        List<AlertEvent> stale = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .in(AlertEvent::getStatus, UNCLOSED_STATUS)
                .lt(AlertEvent::getLastTime, limit)
                .orderByAsc(AlertEvent::getId)
                .last("LIMIT " + AUTO_CLOSE_LIMIT));
        if (stale.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AlertEvent event : stale) {
            event.setStatus(AlertStatus.CLOSED.getCode());
            event.setCloseBy(null);
            event.setCloseTime(now);
            event.setHandleSeconds(handleSeconds(event.getFirstTime(), now));
            alertEventMapper.updateById(event);
        }
        log.info("告警超时自动关闭 {} 条，阈值 {} 小时", stale.size(), autoCloseHours);
        return stale.size();
    }

    // ------------------------------------------------------------------
    // 扫描上下文
    // ------------------------------------------------------------------

    /**
     * 一次性加载本轮扫描所需的聚合数据，避免每条规则重复查库。
     */
    private ScanContext loadContext(List<AlertRule> rules) {
        ScanContext ctx = new ScanContext();
        ctx.now = LocalDateTime.now();
        ctx.tenantId = TenantContext.getTenantId();

        ctx.waitingSessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .select(ChatSession::getId, ChatSession::getSessionNo, ChatSession::getUserId,
                        ChatSession::getQueueEnterTime)
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                .orderByAsc(ChatSession::getQueueEnterTime)
                .last("LIMIT " + MAX_ACTIVE_SESSIONS));
        ctx.onlineAgentCount = safeLong(agentStatusMapper.selectCount(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getOnlineStatus, "ONLINE")));
        ctx.overdueOrderCount = safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOverdueFlag, 1)
                .in(WorkOrder::getStatus, OPEN_ORDER_STATUS)));

        ctx.humanSessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.HUMAN.getCode())
                .orderByAsc(ChatSession::getAcceptTime)
                .last("LIMIT " + MAX_ACTIVE_SESSIONS));
        ctx.sessionMessages = loadMessages(ctx.humanSessions, ctx.now.minusHours(MESSAGE_WINDOW_HOURS));
        ctx.recentClosedSessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())
                .isNotNull(ChatSession::getRating)
                .ge(ChatSession::getEndTime, ctx.now.minusHours(LOW_RATING_WINDOW_HOURS))
                .select(ChatSession::getId, ChatSession::getSessionNo, ChatSession::getAgentId,
                        ChatSession::getRating, ChatSession::getEndTime)
                .orderByDesc(ChatSession::getEndTime)
                .last("LIMIT " + MAX_ACTIVE_SESSIONS));
        ctx.recentMessages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .in(ChatMessage::getSenderType, SenderType.USER.getCode(),
                        SenderType.AGENT.getCode(), SenderType.SUPERVISOR.getCode())
                .ge(ChatMessage::getCreateTime, ctx.now.minusMinutes(SENSITIVE_WINDOW_MINUTES))
                .select(ChatMessage::getId, ChatMessage::getSessionId, ChatMessage::getContent)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 500"));
        ctx.sessionCache = new HashMap<>();
        return ctx;
    }

    private Map<Long, List<ChatMessage>> loadMessages(List<ChatSession> sessions, LocalDateTime from) {
        if (sessions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>(sessions.size());
        for (ChatSession session : sessions) {
            ids.add(session.getId());
        }
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .in(ChatMessage::getSessionId, ids)
                .in(ChatMessage::getSenderType, SenderType.USER.getCode(),
                        SenderType.AI.getCode(), SenderType.AGENT.getCode(),
                        SenderType.SUPERVISOR.getCode(), SenderType.SYSTEM.getCode())
                .ge(ChatMessage::getCreateTime, from)
                .select(ChatMessage::getId, ChatMessage::getSessionId,
                        ChatMessage::getSenderType, ChatMessage::getCreateTime)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT 5000"));
        Map<Long, List<ChatMessage>> map = new HashMap<>();
        for (ChatMessage message : messages) {
            map.computeIfAbsent(message.getSessionId(), key -> new ArrayList<>()).add(message);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 规则评估
    // ------------------------------------------------------------------

    private void evaluate(AlertRule rule, ScanContext ctx, AlertScanResultVO result) {
        AlertMetric metric = AlertMetric.fromCode(rule.getMetricCode());
        if (metric == null) {
            return;
        }
        int threshold = rule.getThresholdValue() == null ? 0 : rule.getThresholdValue();
        switch (metric) {
            case NO_AGENT_ONLINE:
                evaluateNoAgentOnline(rule, ctx, result, threshold);
                break;
            case QUEUE_BACKLOG:
                evaluateQueueBacklog(rule, ctx, result, threshold);
                break;
            case QUEUE_SLOW:
                evaluateQueueSlow(rule, ctx, result, threshold);
                break;
            case ORDER_OVERDUE:
                evaluateOrderOverdue(rule, ctx, result, threshold);
                break;
            case FIRST_RESPONSE_SLOW:
                evaluateFirstResponseSlow(rule, ctx, result, threshold);
                break;
            case AGENT_RESPONSE_TIMEOUT:
                evaluateAgentResponseTimeout(rule, ctx, result, threshold);
                break;
            case SESSION_OVERLONG:
                evaluateSessionOverlong(rule, ctx, result, threshold);
                break;
            case LOW_RATING:
                evaluateLowRating(rule, ctx, result, threshold);
                break;
            case SENSITIVE_WORD:
                evaluateSensitiveWord(rule, ctx, result);
                break;
            default:
                break;
        }
    }

    private void evaluateNoAgentOnline(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        int waiting = ctx.waitingSessions.size();
        if (waiting <= 0 || waiting < Math.max(1, threshold) || ctx.onlineAgentCount > 0) {
            return;
        }
        emit(rule, AlertTargetType.QUEUE, ctx.tenantId, null, null, waiting,
                "排队无人接待",
                "当前排队 " + waiting + " 人，在线坐席 0 人，请立即安排人员上线", result);
    }

    private void evaluateQueueBacklog(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        int waiting = ctx.waitingSessions.size();
        if (waiting < Math.max(1, threshold)) {
            return;
        }
        emit(rule, AlertTargetType.QUEUE, ctx.tenantId, null, null, waiting,
                "排队积压",
                "当前排队 " + waiting + " 人，达到阈值 " + threshold + " 人", result);
    }

    private void evaluateQueueSlow(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        if (ctx.waitingSessions.isEmpty()) {
            return;
        }
        ChatSession head = ctx.waitingSessions.get(0);
        long longest = head.getQueueEnterTime() == null
                ? 0 : Duration.between(head.getQueueEnterTime(), ctx.now).getSeconds();
        if (longest < Math.max(1, threshold)) {
            return;
        }
        emit(rule, AlertTargetType.QUEUE, ctx.tenantId, null, null, longest,
                "排队时长过长",
                "会话 " + safeNo(head) + " 已排队 " + longest + " 秒，超过阈值 " + threshold + " 秒", result);
    }

    private void evaluateOrderOverdue(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        long overdue = ctx.overdueOrderCount;
        if (overdue < Math.max(1, threshold)) {
            return;
        }
        emit(rule, AlertTargetType.ORDER, ctx.tenantId, null, null, overdue,
                "工单超时",
                "当前有 " + overdue + " 个工单已超时未关闭，达到阈值 " + threshold + " 个", result);
    }

    /**
     * 首响偏慢：坐席已接入但尚未发出任何坐席消息，且等待超过阈值秒数。
     */
    private void evaluateFirstResponseSlow(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        for (ChatSession session : ctx.humanSessions) {
            if (!ownsSession(rule, session) || session.getAcceptTime() == null) {
                continue;
            }
            List<ChatMessage> messages = ctx.sessionMessages.getOrDefault(session.getId(),
                    Collections.emptyList());
            boolean agentReplied = false;
            for (ChatMessage message : messages) {
                if (SenderType.AGENT.getCode().equals(message.getSenderType())
                        || SenderType.SUPERVISOR.getCode().equals(message.getSenderType())) {
                    agentReplied = true;
                    break;
                }
            }
            if (agentReplied) {
                continue;
            }
            long waited = Duration.between(session.getAcceptTime(), ctx.now).getSeconds();
            if (waited < Math.max(1, threshold)) {
                continue;
            }
            emit(rule, AlertTargetType.SESSION, session.getId(), session.getId(), session.getAgentId(), waited,
                    "首响偏慢",
                    "会话 " + safeNo(session) + " 坐席接入后 " + waited + " 秒仍未回复，超过阈值 "
                            + threshold + " 秒", result);
        }
    }

    /**
     * 访客消息待回复超时：人工接待中最后一条为访客消息，且等待超过阈值秒数。
     */
    private void evaluateAgentResponseTimeout(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        for (ChatSession session : ctx.humanSessions) {
            if (!ownsSession(rule, session)) {
                continue;
            }
            List<ChatMessage> messages = ctx.sessionMessages.getOrDefault(session.getId(),
                    Collections.emptyList());
            if (messages.isEmpty()) {
                continue;
            }
            ChatMessage last = messages.get(messages.size() - 1);
            if (!SenderType.USER.getCode().equals(last.getSenderType()) || last.getCreateTime() == null) {
                continue;
            }
            long waited = Duration.between(last.getCreateTime(), ctx.now).getSeconds();
            if (waited < Math.max(1, threshold)) {
                continue;
            }
            emit(rule, AlertTargetType.SESSION, session.getId(), session.getId(), session.getAgentId(), waited,
                    "访客消息待回复超时",
                    "会话 " + safeNo(session) + " 访客已等待 " + waited + " 秒未获回复，超过阈值 "
                            + threshold + " 秒", result);
        }
    }

    private void evaluateSessionOverlong(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        for (ChatSession session : ctx.humanSessions) {
            if (!ownsSession(rule, session) || session.getAcceptTime() == null) {
                continue;
            }
            long minutes = Duration.between(session.getAcceptTime(), ctx.now).toMinutes();
            if (minutes < Math.max(1, threshold)) {
                continue;
            }
            emit(rule, AlertTargetType.SESSION, session.getId(), session.getId(), session.getAgentId(), minutes,
                    "会话时长超长",
                    "会话 " + safeNo(session) + " 已接待 " + minutes + " 分钟，超过阈值 "
                            + threshold + " 分钟", result);
        }
    }

    private void evaluateLowRating(AlertRule rule, ScanContext ctx, AlertScanResultVO result, int threshold) {
        for (ChatSession session : ctx.recentClosedSessions) {
            if (!ownsSession(rule, session) || session.getRating() == null) {
                continue;
            }
            if (session.getRating() > threshold) {
                continue;
            }
            emit(rule, AlertTargetType.SESSION, session.getId(), session.getId(), session.getAgentId(),
                    session.getRating(),
                    "客户差评",
                    "会话 " + safeNo(session) + " 评分 " + session.getRating() + " 星，达到差评阈值 "
                            + threshold + " 星", result);
        }
    }

    private void evaluateSensitiveWord(AlertRule rule, ScanContext ctx, AlertScanResultVO result) {
        List<String> keywords = splitKeywords(rule.getKeywords());
        if (keywords.isEmpty() || ctx.recentMessages.isEmpty()) {
            return;
        }
        Set<Long> handled = new LinkedHashSet<>();
        for (ChatMessage message : ctx.recentMessages) {
            String content = message.getContent();
            if (!StringUtils.hasText(content) || handled.contains(message.getSessionId())) {
                continue;
            }
            String lower = content.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (!lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                ChatSession session = ctx.sessionCache.computeIfAbsent(message.getSessionId(),
                        id -> chatSessionMapper.selectById(id));
                if (session == null || !ownsSession(rule, session)) {
                    continue;
                }
                // 同一会话多条消息命中只保留一条告警，避免刷屏
                emit(rule, AlertTargetType.SESSION, session.getId(), session.getId(), session.getAgentId(), 1,
                        "会话命中敏感词",
                        "会话 " + safeNo(session) + " 命中敏感词「" + keyword + "」，请立即介入", result);
                handled.add(session.getId());
                break;
            }
        }
    }

    /**
     * 坐席个人规则只对本人接待的会话生效；租户级规则不限制。
     */
    private boolean ownsSession(AlertRule rule, ChatSession session) {
        if (rule.getOwnerId() == null || rule.getOwnerId() == 0L) {
            return true;
        }
        return rule.getOwnerId().equals(session.getAgentId());
    }

    // ------------------------------------------------------------------
    // 事件落库与推送
    // ------------------------------------------------------------------

    /**
     * 写入或累加一条告警事件，并推送给规则声明的人群。
     */
    private void emit(AlertRule rule, AlertTargetType targetType, Long targetId, Long sessionId, Long agentId,
                      long metricValue, String title, String detail, AlertScanResultVO result) {
        LocalDateTime now = LocalDateTime.now();
        AlertEvent existing = alertEventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getRuleId, rule.getId())
                .eq(AlertEvent::getTargetType, targetType.getCode())
                .eq(AlertEvent::getTargetId, targetId)
                .in(AlertEvent::getStatus, UNCLOSED_STATUS)
                .orderByDesc(AlertEvent::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            LocalDateTime last = existing.getLastTime() == null ? existing.getFirstTime() : existing.getLastTime();
            int cooldown = rule.getCooldownMinutes() == null ? 0 : rule.getCooldownMinutes();
            if (last != null && last.plusMinutes(cooldown).isAfter(now)) {
                result.setSuppressedCount(result.getSuppressedCount() + 1);
                return;
            }
            existing.setOccurCount((existing.getOccurCount() == null ? 0 : existing.getOccurCount()) + 1);
            existing.setLastTime(now);
            existing.setMetricValue(metricValue);
            existing.setThresholdValue(rule.getThresholdValue());
            existing.setDetail(truncate(detail, 500));
            existing.setSeverity(rule.getSeverity());
            alertEventMapper.updateById(existing);
            result.setRepeatedCount(result.getRepeatedCount() + 1);
            push(rule, existing);
            return;
        }

        AlertEvent event = new AlertEvent();
        event.setRuleId(rule.getId());
        event.setRuleCode(rule.getRuleCode());
        event.setMetricCode(rule.getMetricCode());
        event.setRuleName(rule.getRuleName());
        event.setRuleType(rule.getRuleType());
        event.setSeverity(rule.getSeverity());
        event.setTargetType(targetType.getCode());
        event.setTargetId(targetId);
        event.setSessionId(sessionId);
        event.setAgentId(agentId);
        event.setTitle(truncate(title, 128));
        event.setDetail(truncate(detail, 500));
        event.setMetricValue(metricValue);
        event.setThresholdValue(rule.getThresholdValue());
        event.setStatus(AlertStatus.OPEN.getCode());
        event.setOccurCount(1);
        event.setFirstTime(now);
        event.setLastTime(now);
        alertEventMapper.insert(event);
        result.setCreatedCount(result.getCreatedCount() + 1);
        push(rule, event);
    }

    /**
     * 按规则通知对象推送告警：ADMIN 管理员、AGENT 责任坐席、ALL 两者。
     */
    private void push(AlertRule rule, AlertEvent event) {
        Set<Long> recipients = new LinkedHashSet<>();
        String notifyRole = rule.getNotifyRole() == null ? "ADMIN" : rule.getNotifyRole();
        if ("ADMIN".equals(notifyRole) || "ALL".equals(notifyRole)) {
            recipients.addAll(adminUserIds());
        }
        if ("AGENT".equals(notifyRole) || "ALL".equals(notifyRole)) {
            if (rule.getOwnerId() != null && rule.getOwnerId() > 0) {
                recipients.add(rule.getOwnerId());
            }
            if (event.getAgentId() != null) {
                recipients.add(event.getAgentId());
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", event.getId());
        data.put("ruleCode", event.getRuleCode());
        data.put("ruleName", event.getRuleName());
        data.put("ruleType", event.getRuleType());
        data.put("severity", event.getSeverity());
        data.put("targetType", event.getTargetType());
        data.put("targetId", event.getTargetId());
        data.put("sessionId", event.getSessionId());
        data.put("agentId", event.getAgentId());
        data.put("title", event.getTitle());
        data.put("detail", event.getDetail());
        data.put("metricValue", event.getMetricValue());
        data.put("thresholdValue", event.getThresholdValue());
        data.put("status", event.getStatus());
        data.put("occurCount", event.getOccurCount());
        data.put("lastTime", event.getLastTime());
        for (Long userId : recipients) {
            chatPushService.pushAlert(userId, data);
        }
    }

    private List<Long> adminUserIds() {
        List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleCode, Constants.ROLE_ADMIN)
                .eq(SysUser::getStatus, 1)
                .select(SysUser::getId));
        List<Long> ids = new ArrayList<>(admins.size());
        for (SysUser admin : admins) {
            ids.add(admin.getId());
        }
        return ids;
    }

    private AlertEvent requireEvent(Long id) {
        AlertEvent event = id == null ? null : alertEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "告警不存在");
        }
        return event;
    }

    /**
     * 坐席只能处理责任坐席为本人（或未指派）的告警，管理员不限制。
     */
    private void assertHandledBy(AlertEvent event, LoginUser operator) {
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (Constants.ROLE_ADMIN.equals(operator.getRoleCode())
                || Constants.ROLE_PLATFORM_ADMIN.equals(operator.getRoleCode())) {
            return;
        }
        if (event.getAgentId() != null && !event.getAgentId().equals(operator.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能处理本人会话产生的告警");
        }
    }

    private long countByStatus(String status, Long agentId, String severity) {
        return safeLong(alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getStatus, status)
                .eq(StringUtils.hasText(severity), AlertEvent::getSeverity, severity)
                .eq(agentId != null, AlertEvent::getAgentId, agentId)));
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : keywords.split("[,，;；|]")) {
            String word = part.trim();
            if (!word.isEmpty()) {
                unique.add(word);
            }
        }
        return new ArrayList<>(unique);
    }

    private String safeNo(ChatSession session) {
        return StringUtils.hasText(session.getSessionNo())
                ? session.getSessionNo() : String.valueOf(session.getId());
    }

    private String displayName(LoginUser loginUser) {
        return StringUtils.hasText(loginUser.getNickname())
                ? loginUser.getNickname() : loginUser.getUsername();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 处理耗时（秒）：首次触发到关闭。
     *
     * <p>首次触发时间落库时只保留到秒，同一秒内确认并关闭时读回值可能略早于当前时刻，
     * 因此下限收敛为 0，避免历史报表出现负耗时。</p>
     */
    private long handleSeconds(LocalDateTime firstTime, LocalDateTime now) {
        if (firstTime == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(firstTime, now).getSeconds());
    }

    /**
     * 单轮扫描上下文：聚合队列、坐席、工单与会话消息，供各规则共享。
     */
    private static class ScanContext {

        private LocalDateTime now;

        private Long tenantId;

        private List<ChatSession> waitingSessions = Collections.emptyList();

        private long onlineAgentCount;

        private long overdueOrderCount;

        private List<ChatSession> humanSessions = Collections.emptyList();

        private List<ChatSession> recentClosedSessions = Collections.emptyList();

        private Map<Long, List<ChatMessage>> sessionMessages = Collections.emptyMap();

        private List<ChatMessage> recentMessages = Collections.emptyList();

        private Map<Long, ChatSession> sessionCache = new HashMap<>();
    }
}
