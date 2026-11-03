package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.ops.AgentRealtimeVO;
import com.zhiketong.dto.ops.OpsMetricsVO;
import com.zhiketong.dto.ops.OpsScreenVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运营监控服务（S6-A）：实时大屏、运营指标区间报表与坐席实时表现。
 *
 * <p>指标口径全部以 docs/产品推进路线图.md 第 3 章为准，且与 {@code docs/数据库设计.md}
 * 的字段语义保持一致：</p>
 * <ul>
 *   <li>首响 = {@code first_reply_time - first_user_msg_time}，AI 与坐席首响同口径；</li>
 *   <li>3 分钟回复率按轮次统计，轮次来自会话关闭时预聚合的 {@code reply_round_count} /
 *       {@code reply_3min_count}，未计算的会话单独披露覆盖率，不计入分母；</li>
 *   <li>AI 自主解决率 = 未接人工即关闭的会话 / 已关闭会话；</li>
 *   <li>满意度必须同时披露回收率。</li>
 * </ul>
 *
 * <p>大屏数据全部实时查询业务库，确保与工作台、统计页同一口径；不做 Redis 缓存，
 * 避免运营看到与实际不一致的数字。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpsService {

    /** 统计时区 */
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter HOUR_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:00");

    private static final DateTimeFormatter HOUR_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    /** 大屏坐席行上限 */
    private static final int MAX_AGENT_ROWS = 20;

    /** 大屏趋势小时数 */
    private static final int HOURLY_HOURS = 12;

    /** 指标区间最大跨度（天） */
    private static final long MAX_RANGE_DAYS = 366L;

    /** 一次性解决率观察窗口（小时） */
    private static final int ONE_TIME_RESOLVE_WINDOW_HOURS = 72;

    /** 未关闭工单状态 */
    private static final List<String> OPEN_ORDER_STATUS = Arrays.asList(
            OrderStatus.PENDING.getCode(), OrderStatus.ASSIGNED.getCode(),
            OrderStatus.PROCESSING.getCode(), OrderStatus.WAITING_CUSTOMER.getCode(),
            OrderStatus.REOPENED.getCode());

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final WorkOrderMapper workOrderMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final SessionMetricService sessionMetricService;

    /** 排队人数预警阈值 */
    @Value("${zhiketong.ops.waiting-warn-count:5}")
    private int waitingWarnCount;

    /** 最长排队时长预警阈值（秒） */
    @Value("${zhiketong.ops.waiting-warn-seconds:300}")
    private long waitingWarnSeconds;

    /** 首响均值预警阈值（秒） */
    @Value("${zhiketong.ops.first-response-warn-seconds:60}")
    private long firstResponseWarnSeconds;

    /**
     * 实时大屏快照（R-176，DB-02 / DB-03）。
     */
    public OpsScreenVO screen() {
        LocalDateTime now = LocalDateTime.now(STAT_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        List<ChatSession> todaySessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, todayStart)
                .lt(ChatSession::getCreateTime, tomorrowStart));

        OpsScreenVO vo = new OpsScreenVO();
        vo.setServerTime(now.format(TS_FORMATTER));
        vo.setRealtime(buildRealtime(now));
        vo.setToday(buildToday(todayStart, tomorrowStart, todaySessions));
        vo.setAgents(buildAgentRows(todayStart, tomorrowStart));
        vo.setHourly(buildHourly(now));
        vo.setAlerts(buildAlerts(vo));
        return vo;
    }

    /**
     * 运营指标区间报表（R-177，DB-01 / DB-04）。
     *
     * @param startDate 起始日期（yyyy-MM-dd，含当天），与 endDate 同时提供或同时为空
     * @param endDate   结束日期（yyyy-MM-dd，含当天）
     */
    public OpsMetricsVO metrics(String startDate, String endDate) {
        DateRange range = resolveRange(startDate, endDate);
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, range.from)
                .lt(ChatSession::getCreateTime, range.to));

        OpsMetricsVO vo = new OpsMetricsVO();
        vo.setStartDate(range.start.format(DATE_FORMATTER));
        vo.setEndDate(range.end.format(DATE_FORMATTER));
        vo.setSessionTotal(sessions.size());

        long closed = 0;
        long human = 0;
        long transferred = 0;
        long aiOnly = 0;
        long durationSum = 0;
        long durationCount = 0;
        long ratingSum = 0;
        long ratingCount = 0;
        long roundCount = 0;
        long withinCount = 0;
        long metricSessions = 0;
        long pendingMetric = 0;
        List<Long> firstResponseSeconds = new ArrayList<>();
        for (ChatSession session : sessions) {
            boolean isClosed = SessionStatus.CLOSED.getCode().equals(session.getStatus());
            if (isClosed) {
                closed++;
                if (session.getAgentId() == null) {
                    aiOnly++;
                }
            }
            if (session.getAcceptTime() != null) {
                human++;
            }
            if (session.getTransferTime() != null) {
                transferred++;
            }
            if (session.getAcceptTime() != null && session.getEndTime() != null) {
                durationSum += secondsBetween(session.getAcceptTime(), session.getEndTime());
                durationCount++;
            }
            if (session.getRating() != null) {
                ratingSum += session.getRating();
                ratingCount++;
            }
            if (session.getFirstUserMsgTime() != null && session.getFirstReplyTime() != null) {
                firstResponseSeconds.add(secondsBetween(session.getFirstUserMsgTime(), session.getFirstReplyTime()));
            }
            if (session.getAcceptTime() != null && session.getOpsMetricTime() != null) {
                metricSessions++;
                roundCount += session.getReplyRoundCount() == null ? 0 : session.getReplyRoundCount();
                withinCount += session.getReply3minCount() == null ? 0 : session.getReply3minCount();
            } else if (isClosed && session.getAcceptTime() != null) {
                // 只把「已关闭但未计算」算作待回填；仍在接待中的会话关闭时会自动计算
                pendingMetric++;
            }
        }

        vo.setClosedCount(closed);
        vo.setHumanSessionCount(human);
        vo.setTransferredCount(transferred);
        vo.setTransferRate(ratio(transferred, sessions.size()));
        vo.setAvgSessionDurationSeconds(durationCount == 0 ? 0 : durationSum / durationCount);
        vo.setFirstResponseSample(firstResponseSeconds.size());
        vo.setAvgFirstResponseSeconds(averageOf(firstResponseSeconds));
        vo.setP90FirstResponseSeconds(p90Of(firstResponseSeconds));
        vo.setRoundCount(roundCount);
        vo.setWithinCount(withinCount);
        vo.setThreeMinuteReplyRate(ratio(withinCount, roundCount));
        vo.setMetricSessionCount(metricSessions);
        vo.setMetricCoverageRate(ratio(metricSessions, human));
        vo.setPendingMetricSessionCount(pendingMetric);
        vo.setAiOnlyCount(aiOnly);
        vo.setAiResolveRate(ratio(aiOnly, closed));
        vo.setRatingCount(ratingCount);
        vo.setAvgRating(average(ratingSum, ratingCount));
        vo.setSatisfactionRecoveryRate(ratio(ratingCount, closed));
        computeOneTimeResolve(sessions, vo);

        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getCreateTime, range.from)
                .lt(WorkOrder::getCreateTime, range.to));
        long overdue = 0;
        for (WorkOrder order : orders) {
            if (Integer.valueOf(1).equals(order.getOverdueFlag())
                    && !OrderStatus.CLOSED.getCode().equals(order.getStatus())) {
                overdue++;
            }
        }
        vo.setOrderTotal(orders.size());
        vo.setOrderOverdueCount(overdue);
        vo.setOrderOverdueRate(ratio(overdue, orders.size()));
        return vo;
    }

    /**
     * 坐席实时表现分页（R-179，DB-03）：在线状态 + 今日接入量 + 平均响应时长。
     */
    public PageResult<AgentRealtimeVO> agentRealtime(Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<AgentStatus> page = agentStatusMapper.selectPage(new Page<>(num, size),
                new LambdaQueryWrapper<AgentStatus>()
                        .orderByAsc(AgentStatus::getAgentId));
        List<AgentStatus> statuses = page.getRecords();
        if (statuses.isEmpty()) {
            return PageResult.of(page, Collections.emptyList());
        }
        List<Long> agentIds = new ArrayList<>(statuses.size());
        for (AgentStatus status : statuses) {
            agentIds.add(status.getAgentId());
        }
        LocalDate today = LocalDate.now(STAT_ZONE);
        Map<Long, AgentToday> todayMap = loadAgentToday(agentIds, today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        Map<Long, String> names = agentNames(agentIds);
        List<AgentRealtimeVO> list = new ArrayList<>(statuses.size());
        for (AgentStatus status : statuses) {
            AgentToday stat = todayMap.getOrDefault(status.getAgentId(), new AgentToday());
            AgentRealtimeVO vo = new AgentRealtimeVO();
            vo.setAgentId(status.getAgentId());
            vo.setAgentName(names.get(status.getAgentId()));
            vo.setOnlineStatus(status.getOnlineStatus());
            vo.setCurrentCount(status.getCurrentCount() == null ? 0 : status.getCurrentCount());
            vo.setMaxConcurrent(status.getMaxConcurrent() == null ? 0 : status.getMaxConcurrent());
            vo.setTotalServed(status.getTotalServed());
            vo.setLastHeartbeat(status.getLastHeartbeat());
            vo.setTodayServed(stat.served);
            vo.setTodayClosed(stat.closed);
            vo.setAvgAcceptSeconds(stat.acceptCount == 0 ? 0 : stat.acceptSum / stat.acceptCount);
            vo.setAvgFirstReplySeconds(stat.firstReplyCount == 0
                    ? 0 : stat.firstReplySum / stat.firstReplyCount);
            vo.setAvgSessionSeconds(stat.durationCount == 0 ? 0 : stat.durationSum / stat.durationCount);
            list.add(vo);
        }
        return PageResult.of(page, list);
    }

    /**
     * 供 WebSocket 推送复用的快照入口，异常时不阻断推送线程。
     */
    public Map<String, Object> screenPayload() {
        OpsScreenVO vo = screen();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverTime", vo.getServerTime());
        data.put("realtime", vo.getRealtime());
        data.put("today", vo.getToday());
        data.put("agents", vo.getAgents());
        data.put("hourly", vo.getHourly());
        data.put("alerts", vo.getAlerts());
        return data;
    }

    private OpsScreenVO.Realtime buildRealtime(LocalDateTime now) {
        OpsScreenVO.Realtime realtime = new OpsScreenVO.Realtime();
        List<ChatSession> waiting = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .select(ChatSession::getId, ChatSession::getQueueEnterTime)
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                .orderByAsc(ChatSession::getQueueEnterTime));
        long longest = 0;
        long waitSum = 0;
        for (ChatSession session : waiting) {
            long seconds = session.getQueueEnterTime() == null
                    ? 0 : secondsBetween(session.getQueueEnterTime(), now);
            waitSum += seconds;
            longest = Math.max(longest, seconds);
        }
        realtime.setWaitingCount(waiting.size());
        realtime.setLongestWaitSeconds(longest);
        realtime.setAvgWaitSeconds(waiting.isEmpty() ? 0 : waitSum / waiting.size());
        realtime.setOnlineAgents(countAgentStatus("ONLINE"));
        realtime.setBusyAgents(countAgentStatus("BUSY"));
        realtime.setServingSessions(countSessions(SessionStatus.HUMAN.getCode()));
        realtime.setAiSessions(countSessions(SessionStatus.AI.getCode()));
        return realtime;
    }

    private OpsScreenVO.Today buildToday(LocalDateTime todayStart, LocalDateTime tomorrowStart,
                                         List<ChatSession> todaySessions) {
        OpsScreenVO.Today today = new OpsScreenVO.Today();
        long human = 0;
        long closed = 0;
        long aiResolved = 0;
        long ratingSum = 0;
        long ratingCount = 0;
        long roundCount = 0;
        long withinCount = 0;
        List<Long> firstResponseSeconds = new ArrayList<>();
        for (ChatSession session : todaySessions) {
            if (session.getAcceptTime() != null) {
                human++;
            }
            if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
                closed++;
                if (session.getAgentId() == null) {
                    aiResolved++;
                }
            }
            if (session.getRating() != null) {
                ratingSum += session.getRating();
                ratingCount++;
            }
            if (session.getFirstUserMsgTime() != null && session.getFirstReplyTime() != null) {
                firstResponseSeconds.add(secondsBetween(session.getFirstUserMsgTime(), session.getFirstReplyTime()));
            }
            if (session.getOpsMetricTime() != null) {
                roundCount += session.getReplyRoundCount() == null ? 0 : session.getReplyRoundCount();
                withinCount += session.getReply3minCount() == null ? 0 : session.getReply3minCount();
            }
        }
        today.setSessions(todaySessions.size());
        today.setHumanSessions(human);
        today.setClosedSessions(closed);
        today.setAiResolved(aiResolved);
        today.setAiResolveRate(ratio(aiResolved, closed));
        today.setMessages(safeLong(chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .ge(ChatMessage::getCreateTime, todayStart)
                .lt(ChatMessage::getCreateTime, tomorrowStart))));
        today.setOrders(safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getCreateTime, todayStart)
                .lt(WorkOrder::getCreateTime, tomorrowStart))));
        today.setPendingOrders(safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, OPEN_ORDER_STATUS))));
        today.setOverdueOrders(safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOverdueFlag, 1)
                .ne(WorkOrder::getStatus, OrderStatus.CLOSED.getCode()))));
        today.setRatings(ratingCount);
        today.setAvgRating(average(ratingSum, ratingCount));
        today.setRecoveryRate(ratio(ratingCount, closed));
        today.setAvgFirstResponseSeconds(averageOf(firstResponseSeconds));
        today.setRoundCount(roundCount);
        today.setWithinCount(withinCount);
        today.setThreeMinuteReplyRate(ratio(withinCount, roundCount));
        return today;
    }

    private List<OpsScreenVO.AgentRow> buildAgentRows(LocalDateTime todayStart, LocalDateTime tomorrowStart) {
        List<AgentStatus> statuses = agentStatusMapper.selectList(new LambdaQueryWrapper<AgentStatus>()
                .orderByAsc(AgentStatus::getAgentId)
                .last("LIMIT " + MAX_AGENT_ROWS));
        if (statuses.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> agentIds = new ArrayList<>(statuses.size());
        for (AgentStatus status : statuses) {
            agentIds.add(status.getAgentId());
        }
        Map<Long, AgentToday> todayMap = loadAgentToday(agentIds, todayStart, tomorrowStart);
        Map<Long, String> names = agentNames(agentIds);
        List<OpsScreenVO.AgentRow> rows = new ArrayList<>(statuses.size());
        for (AgentStatus status : statuses) {
            AgentToday stat = todayMap.getOrDefault(status.getAgentId(), new AgentToday());
            OpsScreenVO.AgentRow row = new OpsScreenVO.AgentRow();
            row.setAgentId(status.getAgentId());
            row.setAgentName(names.get(status.getAgentId()));
            row.setOnlineStatus(status.getOnlineStatus());
            row.setCurrentCount(status.getCurrentCount() == null ? 0 : status.getCurrentCount());
            row.setMaxConcurrent(status.getMaxConcurrent() == null ? 0 : status.getMaxConcurrent());
            row.setTotalServed(status.getTotalServed());
            row.setTodayServed(stat.served);
            row.setAvgAcceptSeconds(stat.acceptCount == 0 ? 0 : stat.acceptSum / stat.acceptCount);
            row.setAvgFirstReplySeconds(stat.firstReplyCount == 0
                    ? 0 : stat.firstReplySum / stat.firstReplyCount);
            row.setLastHeartbeat(status.getLastHeartbeat() == null
                    ? null : status.getLastHeartbeat().format(TS_FORMATTER));
            rows.add(row);
        }
        // 在岗优先、负载升序：运营一眼看到谁还能接
        rows.sort((a, b) -> {
            int byStatus = Integer.compare(statusRank(b.getOnlineStatus()), statusRank(a.getOnlineStatus()));
            if (byStatus != 0) {
                return byStatus;
            }
            return Integer.compare(a.getCurrentCount(), b.getCurrentCount());
        });
        return rows;
    }

    private List<OpsScreenVO.HourRow> buildHourly(LocalDateTime now) {
        LocalDateTime from = now.minusHours(HOURLY_HOURS - 1L).truncatedTo(ChronoUnit.HOURS);
        Map<String, Long> sessionBuckets = new LinkedHashMap<>();
        Map<String, Long> messageBuckets = new LinkedHashMap<>();
        for (int i = 0; i < HOURLY_HOURS; i++) {
            String key = from.plusHours(i).format(HOUR_DATE_FORMATTER);
            sessionBuckets.put(key, 0L);
            messageBuckets.put(key, 0L);
        }
        for (ChatSession session : chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .select(ChatSession::getId, ChatSession::getCreateTime)
                .ge(ChatSession::getCreateTime, from))) {
            if (session.getCreateTime() == null) {
                continue;
            }
            String key = session.getCreateTime().format(HOUR_DATE_FORMATTER);
            if (sessionBuckets.containsKey(key)) {
                sessionBuckets.merge(key, 1L, Long::sum);
            }
        }
        for (ChatMessage message : chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .select(ChatMessage::getId, ChatMessage::getCreateTime)
                .ge(ChatMessage::getCreateTime, from))) {
            if (message.getCreateTime() == null) {
                continue;
            }
            String key = message.getCreateTime().format(HOUR_DATE_FORMATTER);
            if (messageBuckets.containsKey(key)) {
                messageBuckets.merge(key, 1L, Long::sum);
            }
        }
        List<OpsScreenVO.HourRow> rows = new ArrayList<>(HOURLY_HOURS);
        for (Map.Entry<String, Long> entry : sessionBuckets.entrySet()) {
            OpsScreenVO.HourRow row = new OpsScreenVO.HourRow();
            row.setHour(LocalDateTime.parse(entry.getKey() + ":00:00",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).format(HOUR_LABEL_FORMATTER));
            row.setSessions(entry.getValue());
            row.setMessages(messageBuckets.getOrDefault(entry.getKey(), 0L));
            rows.add(row);
        }
        return rows;
    }

    private List<OpsScreenVO.AlertRow> buildAlerts(OpsScreenVO vo) {
        List<OpsScreenVO.AlertRow> alerts = new ArrayList<>(3);
        OpsScreenVO.Realtime realtime = vo.getRealtime();
        if (realtime != null) {
            if (realtime.getWaitingCount() > 0 && realtime.getOnlineAgents() == 0) {
                alerts.add(alert("CRITICAL", "NO_AGENT_ONLINE", "无在线坐席",
                        "当前排队 " + realtime.getWaitingCount() + " 人，但没有任何在线坐席，请立即安排人员上线"));
            }
            if (realtime.getWaitingCount() > waitingWarnCount) {
                alerts.add(alert("WARN", "QUEUE_BACKLOG", "排队积压",
                        "排队人数 " + realtime.getWaitingCount() + " 人，超过阈值 " + waitingWarnCount + " 人"));
            }
            if (realtime.getLongestWaitSeconds() > waitingWarnSeconds) {
                alerts.add(alert("WARN", "QUEUE_SLOW", "排队时长过长",
                        "最长排队 " + realtime.getLongestWaitSeconds() + " 秒，超过阈值 " + waitingWarnSeconds + " 秒"));
            }
        }
        OpsScreenVO.Today today = vo.getToday();
        if (today != null) {
            if (today.getOverdueOrders() > 0) {
                alerts.add(alert("WARN", "ORDER_OVERDUE", "工单超时",
                        "当前有 " + today.getOverdueOrders() + " 个工单已超时未关闭"));
            }
            if (today.getAvgFirstResponseSeconds() > firstResponseWarnSeconds) {
                alerts.add(alert("WARN", "FIRST_RESPONSE_SLOW", "首响偏慢",
                        "今日首响均值 " + today.getAvgFirstResponseSeconds()
                                + " 秒，超过阈值 " + firstResponseWarnSeconds + " 秒"));
            }
        }
        return alerts;
    }

    private OpsScreenVO.AlertRow alert(String level, String code, String title, String detail) {
        OpsScreenVO.AlertRow row = new OpsScreenVO.AlertRow();
        row.setLevel(level);
        row.setCode(code);
        row.setTitle(title);
        row.setDetail(detail);
        return row;
    }

    /**
     * 批量聚合坐席今日表现：接入量、关闭量、平均接入等待、平均首响、平均会话时长。
     */
    private Map<Long, AgentToday> loadAgentToday(List<Long> agentIds, LocalDateTime from, LocalDateTime to) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .in(ChatSession::getAgentId, agentIds)
                .ge(ChatSession::getAcceptTime, from)
                .lt(ChatSession::getAcceptTime, to));
        Map<Long, AgentToday> result = new HashMap<>();
        for (ChatSession session : sessions) {
            AgentToday stat = result.computeIfAbsent(session.getAgentId(), id -> new AgentToday());
            stat.served++;
            if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
                stat.closed++;
            }
            if (session.getQueueEnterTime() != null && session.getAcceptTime() != null) {
                stat.acceptSum += secondsBetween(session.getQueueEnterTime(), session.getAcceptTime());
                stat.acceptCount++;
            }
            if (session.getFirstUserMsgTime() != null && session.getFirstReplyTime() != null) {
                stat.firstReplySum += secondsBetween(session.getFirstUserMsgTime(), session.getFirstReplyTime());
                stat.firstReplyCount++;
            }
            if (session.getAcceptTime() != null && session.getEndTime() != null) {
                stat.durationSum += secondsBetween(session.getAcceptTime(), session.getEndTime());
                stat.durationCount++;
            }
        }
        return result;
    }

    private Map<Long, String> agentNames(List<Long> agentIds) {
        Map<Long, String> result = new HashMap<>();
        Set<Long> ids = new LinkedHashSet<>(agentIds);
        if (ids.isEmpty()) {
            return result;
        }
        for (SysUser user : sysUserMapper.selectBatchIds(ids)) {
            result.put(user.getId(),
                    StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        }
        return result;
    }

    private long countAgentStatus(String onlineStatus) {
        return safeLong(agentStatusMapper.selectCount(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getOnlineStatus, onlineStatus)));
    }

    private long countSessions(String status) {
        return safeLong(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, status)));
    }

    private int statusRank(String onlineStatus) {
        if ("ONLINE".equals(onlineStatus)) {
            return 3;
        }
        if ("BUSY".equals(onlineStatus)) {
            return 2;
        }
        if ("AWAY".equals(onlineStatus)) {
            return 1;
        }
        return 0;
    }

    private DateRange resolveRange(String startDate, String endDate) {
        boolean hasStart = StringUtils.hasText(startDate);
        boolean hasEnd = StringUtils.hasText(endDate);
        if (hasStart != hasEnd) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "startDate 与 endDate 必须同时提供");
        }
        LocalDate today = LocalDate.now(STAT_ZONE);
        LocalDate end = hasEnd ? parseDate(endDate, "endDate") : today;
        LocalDate start = hasStart ? parseDate(startDate, "startDate") : end.minusDays(6);
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "统计时间跨度不能超过 366 天");
        }
        return new DateRange(start, end, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + " 格式必须为 yyyy-MM-dd");
        }
    }

    /**
     * 一次性解决率（DB-05）：
     * 分母取区间内「已人工接待、已关闭、结束时间距今已满观察窗口」的会话；
     * 分子为其中「同一访客在观察窗口内未再次发起会话」的会话。
     * 观察窗口未满的会话不计入分母，避免新会话拉高或拉低指标。
     */
    private void computeOneTimeResolve(List<ChatSession> sessions, OpsMetricsVO vo) {
        vo.setOneTimeResolveWindowHours(ONE_TIME_RESOLVE_WINDOW_HOURS);
        LocalDateTime deadline = LocalDateTime.now(STAT_ZONE)
                .minusHours(ONE_TIME_RESOLVE_WINDOW_HOURS);
        List<ChatSession> baseSessions = new ArrayList<>();
        Set<Long> userIds = new LinkedHashSet<>();
        LocalDateTime minEnd = null;
        LocalDateTime maxEnd = null;
        for (ChatSession session : sessions) {
            boolean observed = SessionStatus.CLOSED.getCode().equals(session.getStatus())
                    && session.getAgentId() != null
                    && session.getEndTime() != null
                    && !session.getEndTime().isAfter(deadline);
            if (!observed) {
                continue;
            }
            baseSessions.add(session);
            if (session.getUserId() != null) {
                userIds.add(session.getUserId());
            }
            if (minEnd == null || session.getEndTime().isBefore(minEnd)) {
                minEnd = session.getEndTime();
            }
            if (maxEnd == null || session.getEndTime().isAfter(maxEnd)) {
                maxEnd = session.getEndTime();
            }
        }
        vo.setOneTimeResolveBase(baseSessions.size());
        if (baseSessions.isEmpty() || userIds.isEmpty()) {
            vo.setOneTimeResolveCount(0);
            vo.setRevisitCount(0);
            vo.setOneTimeResolveRate(ratio(0, 0));
            return;
        }

        // 一次批量查询同批访客在观察窗口内的后续会话，避免逐条 N+1
        List<ChatSession> laterSessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .in(ChatSession::getUserId, userIds)
                .ge(ChatSession::getCreateTime, minEnd)
                .le(ChatSession::getCreateTime, maxEnd.plusHours(ONE_TIME_RESOLVE_WINDOW_HOURS))
                .select(ChatSession::getId, ChatSession::getUserId, ChatSession::getCreateTime));
        Map<Long, List<LocalDateTime>> laterByUser = new HashMap<>();
        for (ChatSession session : laterSessions) {
            if (session.getUserId() == null || session.getCreateTime() == null) {
                continue;
            }
            laterByUser.computeIfAbsent(session.getUserId(), key -> new ArrayList<>())
                    .add(session.getCreateTime());
        }

        long oneTime = 0;
        for (ChatSession session : baseSessions) {
            if (session.getUserId() == null) {
                continue;
            }
            List<LocalDateTime> later = laterByUser.get(session.getUserId());
            if (later == null) {
                oneTime++;
                continue;
            }
            LocalDateTime windowEnd = session.getEndTime().plusHours(ONE_TIME_RESOLVE_WINDOW_HOURS);
            boolean revisited = false;
            for (LocalDateTime createTime : later) {
                if (createTime.isAfter(session.getEndTime()) && !createTime.isAfter(windowEnd)) {
                    revisited = true;
                    break;
                }
            }
            if (!revisited) {
                oneTime++;
            }
        }
        vo.setOneTimeResolveCount(oneTime);
        vo.setRevisitCount(baseSessions.size() - oneTime);
        vo.setOneTimeResolveRate(ratio(oneTime, baseSessions.size()));
    }

    private long secondsBetween(LocalDateTime from, LocalDateTime to) {
        long seconds = ChronoUnit.SECONDS.between(from, to);
        return Math.max(0L, seconds);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private long averageOf(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long sum = 0;
        for (Long value : values) {
            sum += value == null ? 0 : value;
        }
        return sum / values.size();
    }

    /**
     * P90：按升序取第 ceil(0.9n) 个样本，空集合返回 0。
     */
    private long p90Of(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * 0.9D) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.size()) {
            index = sorted.size() - 1;
        }
        return sorted.get(index);
    }

    private BigDecimal average(long sum, long count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    /** 坐席当日累加桶。 */
    private static class AgentToday {

        private long served;
        private long closed;
        private long acceptSum;
        private long acceptCount;
        private long firstReplySum;
        private long firstReplyCount;
        private long durationSum;
        private long durationCount;
    }

    /** 统计区间：自然日边界与半开时间区间。 */
    private static class DateRange {

        private final LocalDate start;
        private final LocalDate end;
        private final LocalDateTime from;
        private final LocalDateTime to;

        DateRange(LocalDate start, LocalDate end, LocalDateTime from, LocalDateTime to) {
            this.start = start;
            this.end = end;
            this.from = from;
            this.to = to;
        }
    }
}
