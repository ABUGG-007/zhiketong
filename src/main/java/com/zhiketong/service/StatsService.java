package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.stats.AgentWorkloadVO;
import com.zhiketong.dto.stats.AiCallStatVO;
import com.zhiketong.dto.stats.KbHitStatVO;
import com.zhiketong.dto.stats.SessionTrendVO;
import com.zhiketong.dto.stats.StatisticsOverviewVO;
import com.zhiketong.dto.stats.WorkOrderDistributionVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.AiCallLog;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.KbCategory;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.entity.WorkOrderLog;
import com.zhiketong.enums.AgentOnlineStatus;
import com.zhiketong.enums.OrderAction;
import com.zhiketong.enums.OrderPriority;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.AiCallLogMapper;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.KbCategoryMapper;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderLogMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计分析服务（R-64 ~ R-69）。
 *
 * <p>全部指标直接查询业务库（V1.0 不做预聚合），统计口径以 Asia/Shanghai
 * 自然日为准；查询区间默认最近 7 天（含今天），跨度不超过 366 天。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    /** 统计时区 */
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 小时桶格式 */
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    /** 最大统计跨度（天） */
    private static final long MAX_RANGE_DAYS = 365L;

    /** 活跃会话状态 */
    private static final List<String> ACTIVE_SESSION_STATUS = Arrays.asList(
            SessionStatus.AI.getCode(), SessionStatus.WAITING.getCode(), SessionStatus.HUMAN.getCode());

    /** 工单分类固定顺序 */
    private static final List<String> WORK_ORDER_CATEGORIES = Arrays.asList(
            "技术问题", "账号问题", "订单问题", "售后问题", "其他");

    /** 降级日志前缀 */
    private static final String FALLBACK_PREFIX = "FALLBACK_";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final KbEntryMapper kbEntryMapper;
    private final KbCategoryMapper kbCategoryMapper;
    private final SysUserMapper sysUserMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final KbCategoryService kbCategoryService;

    /**
     * 数据总览（R-64）。
     */
    public StatisticsOverviewVO overview(String startDate, String endDate) {
        StatsRange range = resolveRange(startDate, endDate);
        List<ChatSession> sessions = loadSessions(range);

        long aiOnly = 0L;
        long transferred = 0L;
        long durationSum = 0L;
        long durationCount = 0L;
        long ratingSum = 0L;
        long ratingCount = 0L;
        for (ChatSession session : sessions) {
            if (session.getTransferTime() == null) {
                aiOnly++;
            } else {
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
        }

        List<WorkOrder> orders = loadWorkOrders(range);
        long pending = 0L;
        long processing = 0L;
        long resolved = 0L;
        long overdue = 0L;
        for (WorkOrder order : orders) {
            if (OrderStatus.PENDING.getCode().equals(order.getStatus())) {
                pending++;
            } else if (OrderStatus.PROCESSING.getCode().equals(order.getStatus())) {
                processing++;
            } else if (OrderStatus.RESOLVED.getCode().equals(order.getStatus())) {
                resolved++;
            }
            if (Integer.valueOf(1).equals(order.getOverdueFlag())
                    && !OrderStatus.CLOSED.getCode().equals(order.getStatus())) {
                overdue++;
            }
        }

        List<AiCallLog> aiLogs = loadAiCalls(range);
        long aiSuccess = 0L;
        for (AiCallLog item : aiLogs) {
            if (Integer.valueOf(1).equals(item.getSuccess())) {
                aiSuccess++;
            }
        }

        StatisticsOverviewVO vo = new StatisticsOverviewVO();
        vo.setStartDate(range.start.toString());
        vo.setEndDate(range.end.toString());
        vo.setSessionTotal(sessions.size());
        vo.setSessionAiOnly(aiOnly);
        vo.setSessionTransferred(transferred);
        vo.setSessionWaiting(safeLong(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode()))));
        vo.setSessionActive(safeLong(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .in(ChatSession::getStatus, ACTIVE_SESSION_STATUS))));
        vo.setAvgSessionDurationSeconds(durationCount == 0 ? 0L : durationSum / durationCount);
        vo.setAvgRating(average(ratingSum, ratingCount, 2));
        vo.setUserTotal(safeLong(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>())));
        vo.setAgentTotal(safeLong(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleCode, Constants.ROLE_AGENT)
                .eq(SysUser::getStatus, Constants.STATUS_ENABLED))));
        vo.setAgentOnline(safeLong(agentStatusMapper.selectCount(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getOnlineStatus, AgentOnlineStatus.ONLINE.getCode()))));
        vo.setWorkOrderTotal(orders.size());
        vo.setWorkOrderPending(pending);
        vo.setWorkOrderProcessing(processing);
        vo.setWorkOrderResolved(resolved);
        vo.setWorkOrderOverdue(overdue);
        vo.setAiCalls(aiLogs.size());
        vo.setAiSuccessRate(ratio(aiSuccess, aiLogs.size(), 2));
        return vo;
    }

    /**
     * 管理端看板聚合数据：今日指标、评分、近 7 日趋势、最近工单/会话与坐席状态。
     *
     * <p>只读聚合接口，供后台首页一次请求完成指标卡、趋势图与列表渲染。</p>
     */
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now(STAT_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("users", safeLong(sysUserMapper.selectCount(null)));
        counts.put("knowledge", safeLong(kbEntryMapper.selectCount(null)));
        counts.put("categories", safeLong(kbCategoryMapper.selectCount(null)));
        counts.put("sessionsToday", safeLong(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, todayStart)
                .lt(ChatSession::getCreateTime, tomorrowStart))));
        counts.put("messagesToday", safeLong(chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .ge(ChatMessage::getCreateTime, todayStart)
                .lt(ChatMessage::getCreateTime, tomorrowStart))));
        counts.put("ordersToday", safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getCreateTime, todayStart)
                .lt(WorkOrder::getCreateTime, tomorrowStart))));
        counts.put("pendingOrders", safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, Arrays.asList(OrderStatus.PENDING.getCode(),
                        OrderStatus.ASSIGNED.getCode(), OrderStatus.PROCESSING.getCode(),
                        OrderStatus.REOPENED.getCode())))));
        counts.put("waitingSessions", safeLong(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode()))));
        counts.put("overdueOrders", safeLong(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOverdueFlag, 1)
                .ne(WorkOrder::getStatus, OrderStatus.CLOSED.getCode()))));
        counts.put("onlineAgents", safeLong(agentStatusMapper.selectCount(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getOnlineStatus, AgentOnlineStatus.ONLINE.getCode()))));

        QueryWrapper<ChatSession> sessionRating = new QueryWrapper<>();
        sessionRating.select("AVG(rating) AS avg_rating").isNotNull("rating");
        QueryWrapper<WorkOrder> orderRating = new QueryWrapper<>();
        orderRating.select("AVG(rating) AS avg_rating").isNotNull("rating");
        Map<String, Object> ratings = new LinkedHashMap<>();
        ratings.put("avgSessionRating",
                averageValue(chatSessionMapper.selectMaps(sessionRating), "avg_rating"));
        ratings.put("avgOrderRating",
                averageValue(workOrderMapper.selectMaps(orderRating), "avg_rating"));

        StatsRange week = resolveRange(null, null);
        Map<String, Long> sessionsByDay = new HashMap<>();
        for (ChatSession session : loadSessions(week)) {
            if (session.getCreateTime() != null) {
                increase(sessionsByDay, session.getCreateTime().format(DATE_FORMATTER));
            }
        }
        Map<String, Long> ordersByDay = new HashMap<>();
        for (WorkOrder order : loadWorkOrders(week)) {
            if (order.getCreateTime() != null) {
                increase(ordersByDay, order.getCreateTime().format(DATE_FORMATTER));
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate date = week.start; !date.isAfter(week.end); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMATTER);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("sessions", sessionsByDay.getOrDefault(key, 0L));
            point.put("orders", ordersByDay.getOrDefault(key, 0L));
            trend.add(point);
        }

        Page<WorkOrder> orderPage = workOrderMapper.selectPage(new Page<>(1, 5),
                new LambdaQueryWrapper<WorkOrder>()
                        .orderByDesc(WorkOrder::getCreateTime)
                        .orderByDesc(WorkOrder::getId));
        Page<ChatSession> sessionPage = chatSessionMapper.selectPage(new Page<>(1, 5),
                new LambdaQueryWrapper<ChatSession>()
                        .orderByDesc(ChatSession::getCreateTime)
                        .orderByDesc(ChatSession::getId));
        List<AgentStatus> agentStatuses = agentStatusMapper.selectList(
                new LambdaQueryWrapper<AgentStatus>().orderByAsc(AgentStatus::getAgentId));

        Set<Long> userIds = new LinkedHashSet<>();
        for (WorkOrder order : orderPage.getRecords()) {
            if (order.getCreatorId() != null) {
                userIds.add(order.getCreatorId());
            }
            if (order.getAssigneeId() != null) {
                userIds.add(order.getAssigneeId());
            }
        }
        for (ChatSession session : sessionPage.getRecords()) {
            if (session.getUserId() != null) {
                userIds.add(session.getUserId());
            }
            if (session.getAgentId() != null) {
                userIds.add(session.getAgentId());
            }
        }
        for (AgentStatus status : agentStatuses) {
            if (status.getAgentId() != null) {
                userIds.add(status.getAgentId());
            }
        }
        Map<Long, String> nameMap = userNames(userIds);

        List<Map<String, Object>> recentOrders = new ArrayList<>(orderPage.getRecords().size());
        for (WorkOrder order : orderPage.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", order.getId());
            item.put("orderNo", order.getOrderNo());
            item.put("title", order.getTitle());
            item.put("priority", order.getPriority());
            item.put("status", order.getStatus());
            item.put("category", order.getCategory());
            item.put("creatorName", nameMap.get(order.getCreatorId()));
            item.put("assigneeName", nameMap.get(order.getAssigneeId()));
            item.put("createTime", order.getCreateTime());
            // SLA 真实数据：供看板渲染剩余时长进度条，不做前端推测
            item.put("slaDeadline", order.getSlaDeadline());
            item.put("overdueFlag", order.getOverdueFlag());
            recentOrders.add(item);
        }

        List<Map<String, Object>> recentSessions = new ArrayList<>(sessionPage.getRecords().size());
        for (ChatSession session : sessionPage.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", session.getId());
            item.put("sessionNo", session.getSessionNo());
            item.put("userName", nameMap.get(session.getUserId()));
            item.put("agentName", nameMap.get(session.getAgentId()));
            item.put("status", session.getStatus());
            item.put("rating", session.getRating());
            item.put("messageCount", session.getMessageCount());
            item.put("createTime", session.getCreateTime());
            recentSessions.add(item);
        }

        List<Map<String, Object>> agents = new ArrayList<>(agentStatuses.size());
        for (AgentStatus status : agentStatuses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentId", status.getAgentId());
            item.put("nickname", nameMap.get(status.getAgentId()));
            item.put("onlineStatus", status.getOnlineStatus());
            item.put("currentCount", status.getCurrentCount());
            item.put("maxConcurrent", status.getMaxConcurrent());
            item.put("totalServed", status.getTotalServed());
            item.put("lastHeartbeat", status.getLastHeartbeat());
            agents.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("counts", counts);
        data.put("ratings", ratings);
        data.put("trend", trend);
        data.put("recentOrders", recentOrders);
        data.put("recentSessions", recentSessions);
        data.put("agents", agents);
        return data;
    }

    /**
     * 会话量趋势（R-65）：day / hour 粒度，无数据区间补 0。
     */
    public SessionTrendVO sessionTrend(String startDate, String endDate, String granularity) {
        StatsRange range = resolveRange(startDate, endDate);
        String unit = normalizeGranularity(granularity);
        checkHourRange(range, unit);
        Map<String, SessionBucket> buckets = initSessionBuckets(range, unit);
        for (ChatSession session : loadSessions(range)) {
            String key = bucketKey(session.getCreateTime(), unit);
            SessionBucket bucket = buckets.get(key);
            if (bucket == null) {
                continue;
            }
            bucket.sessionCount++;
            if (session.getAgentId() != null) {
                bucket.humanCount++;
            }
            if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
                bucket.closedCount++;
            }
            if (session.getAcceptTime() != null && session.getEndTime() != null) {
                bucket.durationSum += secondsBetween(session.getAcceptTime(), session.getEndTime());
                bucket.durationCount++;
            }
            if (session.getRating() != null) {
                bucket.ratingSum += session.getRating();
                bucket.ratingCount++;
            }
        }
        SessionTrendVO vo = new SessionTrendVO();
        vo.setGranularity(unit);
        List<SessionTrendVO.Point> points = new ArrayList<>(buckets.size());
        for (Map.Entry<String, SessionBucket> entry : buckets.entrySet()) {
            SessionBucket bucket = entry.getValue();
            SessionTrendVO.Point point = new SessionTrendVO.Point();
            point.setTime(entry.getKey());
            point.setSessionCount(bucket.sessionCount);
            point.setHumanSessionCount(bucket.humanCount);
            point.setClosedCount(bucket.closedCount);
            point.setAvgDurationSeconds(bucket.durationCount == 0
                    ? 0L : bucket.durationSum / bucket.durationCount);
            point.setAvgRating(average(bucket.ratingSum, bucket.ratingCount, 2));
            points.add(point);
        }
        vo.setPoints(points);
        return vo;
    }

    /**
     * 坐席工作量（R-66）：分页返回每个坐席的会话与工单处理量。
     */
    public PageResult<AgentWorkloadVO> agentWorkload(String startDate, String endDate,
                                                     Integer pageNum, Integer pageSize) {
        StatsRange range = resolveRange(startDate, endDate);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRoleCode, Constants.ROLE_AGENT)
                        .orderByAsc(SysUser::getId));
        List<SysUser> agents = page.getRecords();
        if (agents.isEmpty()) {
            return PageResult.of(page, Collections.emptyList());
        }
        List<Long> agentIds = new ArrayList<>(agents.size());
        for (SysUser agent : agents) {
            agentIds.add(agent.getId());
        }

        Map<Long, WorkloadBucket> buckets = new HashMap<>();
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .in(ChatSession::getAgentId, agentIds)
                .ge(ChatSession::getCreateTime, range.from)
                .lt(ChatSession::getCreateTime, range.to));
        for (ChatSession session : sessions) {
            WorkloadBucket bucket = bucketOf(buckets, session.getAgentId());
            bucket.servedCount++;
            if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
                bucket.closedCount++;
            }
            if (session.getRating() != null) {
                bucket.ratingSum += session.getRating();
                bucket.ratingCount++;
            }
            if (session.getAcceptTime() != null && session.getEndTime() != null) {
                bucket.durationSum += secondsBetween(session.getAcceptTime(), session.getEndTime());
                bucket.durationCount++;
            }
            if (session.getQueueEnterTime() != null && session.getAcceptTime() != null) {
                bucket.acceptSum += secondsBetween(session.getQueueEnterTime(), session.getAcceptTime());
                bucket.acceptCount++;
            }
        }

        List<WorkOrderLog> resolveLogs = workOrderLogMapper.selectList(new LambdaQueryWrapper<WorkOrderLog>()
                .eq(WorkOrderLog::getAction, OrderAction.RESOLVE.getCode())
                .in(WorkOrderLog::getOperatorId, agentIds)
                .ge(WorkOrderLog::getCreateTime, range.from)
                .lt(WorkOrderLog::getCreateTime, range.to));
        for (WorkOrderLog item : resolveLogs) {
            WorkloadBucket bucket = bucketOf(buckets, item.getOperatorId());
            bucket.workOrderResolved++;
        }

        List<AgentWorkloadVO> list = new ArrayList<>(agents.size());
        for (SysUser agent : agents) {
            WorkloadBucket bucket = bucketOf(buckets, agent.getId());
            AgentWorkloadVO vo = new AgentWorkloadVO();
            vo.setAgentId(agent.getId());
            vo.setAgentName(displayName(agent.getNickname(), agent.getUsername()));
            vo.setServedCount(bucket.servedCount);
            vo.setClosedCount(bucket.closedCount);
            vo.setAvgRating(average(bucket.ratingSum, bucket.ratingCount, 2));
            vo.setAvgDurationSeconds(bucket.durationCount == 0
                    ? 0L : bucket.durationSum / bucket.durationCount);
            vo.setAvgAcceptSeconds(bucket.acceptCount == 0
                    ? 0L : bucket.acceptSum / bucket.acceptCount);
            vo.setWorkOrderResolved(bucket.workOrderResolved);
            list.add(vo);
        }
        return PageResult.of(page, list);
    }

    /**
     * 知识库命中统计（R-67）：累计 hit_count + 时间窗口内直接命中消息数。
     */
    public KbHitStatVO kbHit(String startDate, String endDate, Integer limit, Long categoryId) {
        StatsRange range = resolveRange(startDate, endDate);
        int topN = limit == null ? 10 : limit;
        if (topN < 1 || topN > 50) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "limit 取值范围为 1-50");
        }
        KbHitStatVO vo = new KbHitStatVO();
        Set<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = kbCategoryService.resolveCategoryIds(categoryId);
            if (categoryIds == null || categoryIds.isEmpty()) {
                return vo;
            }
        }
        LambdaQueryWrapper<KbEntry> wrapper = new LambdaQueryWrapper<>();
        if (categoryIds != null) {
            wrapper.in(KbEntry::getCategoryId, categoryIds);
        }
        wrapper.orderByDesc(KbEntry::getHitCount).orderByAsc(KbEntry::getId);
        List<KbEntry> entries = kbEntryMapper.selectList(wrapper);
        long totalHits = 0L;
        for (KbEntry entry : entries) {
            totalHits += entry.getHitCount() == null ? 0 : entry.getHitCount();
        }
        List<KbEntry> topEntries = entries.size() > topN ? entries.subList(0, topN) : entries;

        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .isNotNull(ChatMessage::getMatchedKbId)
                .ge(ChatMessage::getCreateTime, range.from)
                .lt(ChatMessage::getCreateTime, range.to));
        Map<Long, Long> directByEntry = new HashMap<>();
        for (ChatMessage message : messages) {
            Long entryId = message.getMatchedKbId();
            Long value = directByEntry.get(entryId);
            directByEntry.put(entryId, value == null ? 1L : value + 1L);
        }

        Map<Long, String> categoryNames = categoryNames(topEntries);
        List<KbHitStatVO.TopEntry> top = new ArrayList<>(topEntries.size());
        for (KbEntry entry : topEntries) {
            KbHitStatVO.TopEntry item = new KbHitStatVO.TopEntry();
            item.setEntryId(entry.getId());
            item.setQuestion(entry.getQuestion());
            item.setCategoryName(categoryNames.get(entry.getCategoryId()));
            item.setHitCount(entry.getHitCount() == null ? 0 : entry.getHitCount());
            Long direct = directByEntry.get(entry.getId());
            item.setDirectAnswerCount(direct == null ? 0L : direct);
            top.add(item);
        }
        vo.setTotalHits(totalHits);
        vo.setDirectAnswerCount(messages.size());
        vo.setDirectAnswerRate(ratio(messages.size(), totalHits, 3));
        vo.setTopEntries(top);
        return vo;
    }

    /**
     * 工单分布统计（R-68）：状态、优先级、分类分布与平均解决耗时。
     */
    public WorkOrderDistributionVO workOrderDistribution(String startDate, String endDate) {
        StatsRange range = resolveRange(startDate, endDate);
        List<WorkOrder> orders = loadWorkOrders(range);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusCounts.put(status.getCode(), 0L);
        }
        Map<String, Long> priorityCounts = new LinkedHashMap<>();
        for (OrderPriority priority : OrderPriority.values()) {
            priorityCounts.put(priority.getCode(), 0L);
        }
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String category : WORK_ORDER_CATEGORIES) {
            categoryCounts.put(category, 0L);
        }

        long overdue = 0L;
        long resolveSum = 0L;
        long resolveCount = 0L;
        for (WorkOrder order : orders) {
            increase(statusCounts, order.getStatus());
            increase(priorityCounts, order.getPriority());
            increase(categoryCounts, order.getCategory());
            if (Integer.valueOf(1).equals(order.getOverdueFlag())
                    && !OrderStatus.CLOSED.getCode().equals(order.getStatus())) {
                overdue++;
            }
            if (order.getCreateTime() != null && order.getResolveTime() != null) {
                long minutes = ChronoUnit.MINUTES.between(order.getCreateTime(), order.getResolveTime());
                if (minutes >= 0) {
                    resolveSum += minutes;
                    resolveCount++;
                }
            }
        }

        WorkOrderDistributionVO vo = new WorkOrderDistributionVO();
        vo.setTotal(orders.size());
        for (OrderStatus status : OrderStatus.values()) {
            vo.getByStatus().add(item(status.getCode(), status.getDescription(),
                    statusCounts.get(status.getCode())));
        }
        for (OrderPriority priority : OrderPriority.values()) {
            vo.getByPriority().add(item(priority.getCode(), priority.getDescription(),
                    priorityCounts.get(priority.getCode())));
        }
        for (Map.Entry<String, Long> entry : categoryCounts.entrySet()) {
            vo.getByCategory().add(item(entry.getKey(), null, entry.getValue()));
        }
        vo.setOverdueCount(overdue);
        vo.setAvgResolveMinutes(resolveCount == 0 ? 0L : resolveSum / resolveCount);
        return vo;
    }

    /**
     * AI 调用统计（R-69）：成功率、耗时、token、降级与模型/趋势维度。
     */
    public AiCallStatVO aiCalls(String startDate, String endDate, String granularity) {
        StatsRange range = resolveRange(startDate, endDate);
        String unit = normalizeGranularity(granularity);
        checkHourRange(range, unit);
        List<AiCallLog> logs = loadAiCalls(range);

        long successCalls = 0L;
        long latencySum = 0L;
        long latencyCount = 0L;
        long promptTokens = 0L;
        long completionTokens = 0L;
        long fallbackCount = 0L;
        Map<String, ModelBucket> modelBuckets = new LinkedHashMap<>();
        Map<String, AiTrendBucket> trendBuckets = initAiTrendBuckets(range, unit);
        for (AiCallLog item : logs) {
            boolean success = Integer.valueOf(1).equals(item.getSuccess());
            if (success) {
                successCalls++;
            } else if (item.getErrorMsg() != null && item.getErrorMsg().startsWith(FALLBACK_PREFIX)) {
                fallbackCount++;
            }
            if (item.getLatencyMs() != null) {
                latencySum += item.getLatencyMs();
                latencyCount++;
            }
            if (item.getPromptTokens() != null) {
                promptTokens += item.getPromptTokens();
            }
            if (item.getCompletionTokens() != null) {
                completionTokens += item.getCompletionTokens();
            }
            String model = StringUtils.hasText(item.getModel()) ? item.getModel() : "unknown";
            String modelKey = model + "#" + success;
            ModelBucket modelBucket = modelBuckets.get(modelKey);
            if (modelBucket == null) {
                modelBucket = new ModelBucket(model, success);
                modelBuckets.put(modelKey, modelBucket);
            }
            modelBucket.calls++;
            if (item.getLatencyMs() != null) {
                modelBucket.latencySum += item.getLatencyMs();
                modelBucket.latencyCount++;
            }
            String key = bucketKey(item.getCreateTime(), unit);
            AiTrendBucket trendBucket = trendBuckets.get(key);
            if (trendBucket != null) {
                trendBucket.calls++;
                if (success) {
                    trendBucket.successCalls++;
                }
                if (item.getLatencyMs() != null) {
                    trendBucket.latencySum += item.getLatencyMs();
                    trendBucket.latencyCount++;
                }
            }
        }

        AiCallStatVO vo = new AiCallStatVO();
        vo.setTotalCalls(logs.size());
        vo.setSuccessCalls(successCalls);
        vo.setFailCalls(logs.size() - successCalls);
        vo.setSuccessRate(ratio(successCalls, logs.size(), 3));
        vo.setAvgLatencyMs(latencyCount == 0 ? 0L : latencySum / latencyCount);
        vo.setTotalPromptTokens(promptTokens);
        vo.setTotalCompletionTokens(completionTokens);
        vo.setFallbackCount(fallbackCount);
        for (ModelBucket bucket : modelBuckets.values()) {
            AiCallStatVO.ModelStat stat = new AiCallStatVO.ModelStat();
            stat.setModel(bucket.model);
            stat.setCalls(bucket.calls);
            stat.setAvgLatencyMs(bucket.latencyCount == 0 ? 0L : bucket.latencySum / bucket.latencyCount);
            if (!bucket.success) {
                stat.setFailed(Boolean.TRUE);
            }
            vo.getByModel().add(stat);
        }
        for (Map.Entry<String, AiTrendBucket> entry : trendBuckets.entrySet()) {
            AiTrendBucket bucket = entry.getValue();
            AiCallStatVO.TrendPoint point = new AiCallStatVO.TrendPoint();
            point.setTime(entry.getKey());
            point.setCalls(bucket.calls);
            point.setSuccessCalls(bucket.successCalls);
            point.setAvgLatencyMs(bucket.latencyCount == 0 ? 0L : bucket.latencySum / bucket.latencyCount);
            vo.getTrend().add(point);
        }
        return vo;
    }

    private List<ChatSession> loadSessions(StatsRange range) {
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, range.from)
                .lt(ChatSession::getCreateTime, range.to));
    }

    private List<WorkOrder> loadWorkOrders(StatsRange range) {
        return workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getCreateTime, range.from)
                .lt(WorkOrder::getCreateTime, range.to));
    }

    private List<AiCallLog> loadAiCalls(StatsRange range) {
        return aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreateTime, range.from)
                .lt(AiCallLog::getCreateTime, range.to));
    }

    private Map<String, SessionBucket> initSessionBuckets(StatsRange range, String unit) {
        Map<String, SessionBucket> buckets = new LinkedHashMap<>();
        if ("hour".equals(unit)) {
            for (LocalDateTime time = range.from; time.isBefore(range.to); time = time.plusHours(1)) {
                buckets.put(time.format(HOUR_FORMATTER), new SessionBucket());
            }
        } else {
            for (LocalDate date = range.start; !date.isAfter(range.end); date = date.plusDays(1)) {
                buckets.put(date.format(DATE_FORMATTER), new SessionBucket());
            }
        }
        return buckets;
    }

    private Map<String, AiTrendBucket> initAiTrendBuckets(StatsRange range, String unit) {
        Map<String, AiTrendBucket> buckets = new LinkedHashMap<>();
        if ("hour".equals(unit)) {
            for (LocalDateTime time = range.from; time.isBefore(range.to); time = time.plusHours(1)) {
                buckets.put(time.format(HOUR_FORMATTER), new AiTrendBucket());
            }
        } else {
            for (LocalDate date = range.start; !date.isAfter(range.end); date = date.plusDays(1)) {
                buckets.put(date.format(DATE_FORMATTER), new AiTrendBucket());
            }
        }
        return buckets;
    }

    private String bucketKey(LocalDateTime time, String unit) {
        if (time == null) {
            return null;
        }
        return "hour".equals(unit) ? time.format(HOUR_FORMATTER) : time.format(DATE_FORMATTER);
    }

    private String normalizeGranularity(String granularity) {
        if (!StringUtils.hasText(granularity)) {
            return "day";
        }
        String value = granularity.trim().toLowerCase();
        if (!"day".equals(value) && !"hour".equals(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "granularity 只能为 day 或 hour");
        }
        return value;
    }

    private void checkHourRange(StatsRange range, String unit) {
        if ("hour".equals(unit) && ChronoUnit.DAYS.between(range.start, range.end) > 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "小时粒度仅支持查询不超过 2 天的数据");
        }
    }

    private StatsRange resolveRange(String startDate, String endDate) {
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
        return new StatsRange(start, end, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + " 格式必须为 yyyy-MM-dd");
        }
    }

    private Map<Long, String> categoryNames(Collection<KbEntry> entries) {
        Set<Long> ids = new LinkedHashSet<>();
        for (KbEntry entry : entries) {
            if (entry.getCategoryId() != null) {
                ids.add(entry.getCategoryId());
            }
        }
        Map<Long, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<KbCategory> categories = kbCategoryMapper.selectBatchIds(ids);
        for (KbCategory category : categories) {
            result.put(category.getId(), category.getName());
        }
        return result;
    }

    private WorkloadBucket bucketOf(Map<Long, WorkloadBucket> buckets, Long agentId) {
        WorkloadBucket bucket = buckets.get(agentId);
        if (bucket == null) {
            bucket = new WorkloadBucket();
            buckets.put(agentId, bucket);
        }
        return bucket;
    }

    private WorkOrderDistributionVO.Item item(String key, String label, Long count) {
        WorkOrderDistributionVO.Item item = new WorkOrderDistributionVO.Item();
        item.setKey(key);
        item.setLabel(label);
        item.setCount(count == null ? 0L : count);
        return item;
    }

    private void increase(Map<String, Long> counts, String key) {
        if (key == null) {
            return;
        }
        Long value = counts.get(key);
        counts.put(key, value == null ? 1L : value + 1L);
    }

    private long secondsBetween(LocalDateTime from, LocalDateTime to) {
        long seconds = ChronoUnit.SECONDS.between(from, to);
        return Math.max(0L, seconds);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 读取聚合查询的平均值，无数据时返回 0.00。
     */
    private BigDecimal averageValue(List<Map<String, Object>> rows, String column) {
        // 聚合查询无匹配数据时，MyBatis 可能返回单个全 NULL 行（映射结果为 null）
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Object value = rows.get(0).get(column);
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 批量查询用户显示名称：优先昵称，缺省用户名。
     */
    private Map<Long, String> userNames(Collection<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        for (SysUser user : sysUserMapper.selectBatchIds(distinct)) {
            result.put(user.getId(), displayName(user.getNickname(), user.getUsername()));
        }
        return result;
    }

    private BigDecimal ratio(long numerator, long denominator, int scale) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    private BigDecimal average(long sum, long count, int scale) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), scale, RoundingMode.HALF_UP);
    }

    private String displayName(String nickname, String username) {
        return StringUtils.hasText(nickname) ? nickname : username;
    }

    /** 统计区间：自然日边界与半开时间区间。 */
    private static class StatsRange {

        private final LocalDate start;
        private final LocalDate end;
        private final LocalDateTime from;
        private final LocalDateTime to;

        StatsRange(LocalDate start, LocalDate end, LocalDateTime from, LocalDateTime to) {
            this.start = start;
            this.end = end;
            this.from = from;
            this.to = to;
        }
    }

    /** 会话趋势累加桶。 */
    private static class SessionBucket {

        private long sessionCount;
        private long humanCount;
        private long closedCount;
        private long durationSum;
        private long durationCount;
        private long ratingSum;
        private long ratingCount;
    }

    /** 坐席工作量累加桶。 */
    private static class WorkloadBucket {

        private long servedCount;
        private long closedCount;
        private long ratingSum;
        private long ratingCount;
        private long durationSum;
        private long durationCount;
        private long acceptSum;
        private long acceptCount;
        private long workOrderResolved;
    }

    /** AI 模型维度累加桶。 */
    private static class ModelBucket {

        private final String model;
        private final boolean success;
        private long calls;
        private long latencySum;
        private long latencyCount;

        ModelBucket(String model, boolean success) {
            this.model = model;
            this.success = success;
        }
    }

    /** AI 调用趋势累加桶。 */
    private static class AiTrendBucket {

        private long calls;
        private long successCalls;
        private long latencySum;
        private long latencyCount;
    }
}
