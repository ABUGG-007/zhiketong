package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiketong.dto.assist.CustomerCardVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SessionSummary;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.enums.SummaryType;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SessionSummaryMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户信息卡服务（R-110，对应 AS-06）：聚合客户画像、历史会话、历史工单与满意度。
 *
 * <p>只读取既有业务表，不新增存储；数据范围由 {@link AgentSessionGuard} 统一收敛。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCustomerService {

    /** 历史会话展示条数 */
    private static final int RECENT_SESSION_LIMIT = 5;

    /** 历史工单展示条数 */
    private static final int RECENT_ORDER_LIMIT = 5;

    /** 处理中工单状态 */
    private static final List<String> OPEN_ORDER_STATUSES = Collections.unmodifiableList(
            java.util.Arrays.asList(OrderStatus.PENDING.getCode(), OrderStatus.ASSIGNED.getCode(),
                    OrderStatus.PROCESSING.getCode(), OrderStatus.REOPENED.getCode()));

    /** 已完结工单状态 */
    private static final List<String> RESOLVED_ORDER_STATUSES = Collections.unmodifiableList(
            java.util.Arrays.asList(OrderStatus.RESOLVED.getCode(), OrderStatus.CLOSED.getCode()));

    private final ChatSessionMapper chatSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkOrderMapper workOrderMapper;
    private final SessionSummaryMapper sessionSummaryMapper;
    private final AgentSessionGuard agentSessionGuard;

    /**
     * 客户信息卡（R-110）。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 客户信息卡
     */
    public CustomerCardVO customerCard(Long sessionId, LoginUser loginUser) {
        ChatSession session = agentSessionGuard.requireOwned(sessionId, loginUser);
        CustomerCardVO vo = new CustomerCardVO();
        vo.setSessionId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setProfile(new CustomerCardVO.Profile());
        vo.setCurrent(currentInfo(session));
        vo.setOrderStats(new CustomerCardVO.OrderStats());
        vo.setNote("客户资料来自历史会话与工单，仅供服务参考");

        SysUser user = session.getUserId() == null ? null : sysUserMapper.selectById(session.getUserId());
        if (user != null) {
            vo.setUserName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setRegisteredAt(user.getCreateTime());
        }
        vo.setProfile(profile(session));
        vo.setRecentSessions(recentSessions(session));
        vo.setRecentOrders(recentOrders(session));
        vo.setOrderStats(orderStats(session));
        return vo;
    }

    /**
     * 本次会话信息：状态、诉求与满意度。
     */
    private CustomerCardVO.Current currentInfo(ChatSession session) {
        CustomerCardVO.Current current = new CustomerCardVO.Current();
        current.setStatus(session.getStatus());
        SessionStatus status = SessionStatus.fromCode(session.getStatus());
        current.setStatusName(status == null ? session.getStatus() : status.getDescription());
        current.setRating(session.getRating());
        current.setRatingComment(session.getRatingComment());
        SessionSummary brief = sessionSummaryMapper.selectOne(new LambdaQueryWrapper<SessionSummary>()
                .eq(SessionSummary::getSessionId, session.getId())
                .eq(SessionSummary::getSummaryType, SummaryType.BRIEF.getCode())
                .orderByDesc(SessionSummary::getId)
                .last("LIMIT 1"));
        if (brief != null) {
            current.setDemand(brief.getCustomerDemand());
            current.setKeywords(splitKeywords(brief.getKeywords()));
        }
        return current;
    }

    /**
     * 客户画像：会话量、人工接待量、满意度与活跃时间。
     */
    private CustomerCardVO.Profile profile(ChatSession session) {
        CustomerCardVO.Profile profile = new CustomerCardVO.Profile();
        Long userId = session.getUserId();
        if (userId == null) {
            profile.setTotalSessions(1L);
            profile.setClosedSessions(SessionStatus.CLOSED.getCode().equals(session.getStatus()) ? 1L : 0L);
            profile.setHumanSessions(SessionStatus.HUMAN.getCode().equals(session.getStatus())
                    || session.getAgentId() != null ? 1L : 0L);
            profile.setFirstSessionTime(session.getCreateTime());
            profile.setLastSessionTime(session.getCreateTime());
            profile.setRatingCount(0L);
            profile.setReturning(false);
            return profile;
        }

        profile.setTotalSessions(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)));
        profile.setClosedSessions(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())));
        profile.setHumanSessions(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .isNotNull(ChatSession::getAgentId)));

        ChatSession first = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByAsc(ChatSession::getCreateTime)
                .last("LIMIT 1"));
        ChatSession last = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getCreateTime)
                .last("LIMIT 1"));
        profile.setFirstSessionTime(first == null ? null : first.getCreateTime());
        profile.setLastSessionTime(last == null ? null : last.getCreateTime());

        // 满意度聚合：只统计已评价会话，口径与数据看板一致（平均分保留两位）
        List<Map<String, Object>> rows = chatSessionMapper.selectMaps(new QueryWrapper<ChatSession>()
                .select("COUNT(*) AS rating_count", "AVG(rating) AS avg_rating")
                .eq("user_id", userId)
                .isNotNull("rating"));
        long ratingCount = 0L;
        BigDecimal avgRating = null;
        if (rows != null && !rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Object count = row.get("rating_count");
            if (count instanceof Number) {
                ratingCount = ((Number) count).longValue();
            }
            Object avg = row.get("avg_rating");
            if (avg != null) {
                avgRating = new BigDecimal(avg.toString()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        profile.setRatingCount(ratingCount);
        profile.setAvgRating(avgRating);
        profile.setReturning(profile.getTotalSessions() != null && profile.getTotalSessions() > 1);
        return profile;
    }

    /**
     * 历史会话：最近 5 条，含本次会话。
     */
    private List<CustomerCardVO.SessionItem> recentSessions(ChatSession session) {
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, session.getUserId())
                .orderByDesc(ChatSession::getCreateTime)
                .last("LIMIT " + RECENT_SESSION_LIMIT));
        if (sessions.isEmpty()) {
            sessions = Collections.singletonList(session);
        }
        Map<Long, String> agentNames = agentNames(sessions);
        List<CustomerCardVO.SessionItem> items = new ArrayList<>(sessions.size());
        for (ChatSession item : sessions) {
            CustomerCardVO.SessionItem vo = new CustomerCardVO.SessionItem();
            vo.setId(item.getId());
            vo.setSessionNo(item.getSessionNo());
            vo.setStatus(item.getStatus());
            SessionStatus status = SessionStatus.fromCode(item.getStatus());
            vo.setStatusName(status == null ? item.getStatus() : status.getDescription());
            vo.setAgentName(agentNames.get(item.getAgentId()));
            vo.setMessageCount(item.getMessageCount());
            vo.setRating(item.getRating());
            vo.setCreateTime(item.getCreateTime());
            vo.setEndTime(item.getEndTime());
            items.add(vo);
        }
        return items;
    }

    /**
     * 历史工单：优先按客户名下，其次按会话关联，最近 5 条。
     */
    private List<CustomerCardVO.OrderItem> recentOrders(ChatSession session) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .orderByDesc(WorkOrder::getId)
                .last("LIMIT " + RECENT_ORDER_LIMIT);
        if (session.getUserId() == null) {
            wrapper.eq(WorkOrder::getSessionId, session.getId());
        } else {
            Long sessionId = session.getId();
            Long userId = session.getUserId();
            wrapper.and(w -> w.eq(WorkOrder::getSessionId, sessionId)
                    .or().eq(WorkOrder::getCreatorId, userId));
        }
        List<CustomerCardVO.OrderItem> items = new ArrayList<>();
        for (WorkOrder order : workOrderMapper.selectList(wrapper)) {
            CustomerCardVO.OrderItem vo = new CustomerCardVO.OrderItem();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTitle(order.getTitle());
            vo.setStatus(order.getStatus());
            OrderStatus status = OrderStatus.fromCode(order.getStatus());
            vo.setStatusName(status == null ? order.getStatus() : status.getDescription());
            vo.setPriority(order.getPriority());
            vo.setCreateTime(order.getCreateTime());
            items.add(vo);
        }
        return items;
    }

    /**
     * 工单状态分布：总数、处理中、已完结。
     */
    private CustomerCardVO.OrderStats orderStats(ChatSession session) {
        CustomerCardVO.OrderStats stats = new CustomerCardVO.OrderStats();
        stats.setTotal(orderCount(session, null));
        stats.setOpen(orderCount(session, OPEN_ORDER_STATUSES));
        stats.setResolved(orderCount(session, RESOLVED_ORDER_STATUSES));
        return stats;
    }

    private long orderCount(ChatSession session, List<String> statuses) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (session.getUserId() == null) {
            wrapper.eq(WorkOrder::getSessionId, session.getId());
        } else {
            Long sessionId = session.getId();
            Long userId = session.getUserId();
            wrapper.and(w -> w.eq(WorkOrder::getSessionId, sessionId)
                    .or().eq(WorkOrder::getCreatorId, userId));
        }
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(WorkOrder::getStatus, statuses);
        }
        return workOrderMapper.selectCount(wrapper);
    }

    private Map<Long, String> agentNames(List<ChatSession> sessions) {
        Set<Long> ids = new HashSet<>();
        for (ChatSession session : sessions) {
            if (session.getAgentId() != null) {
                ids.add(session.getAgentId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(ids)) {
            names.put(user.getId(), StringUtils.hasText(user.getNickname())
                    ? user.getNickname() : user.getUsername());
        }
        return names;
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String item : keywords.replace('，', ',').split(",")) {
            String keyword = item.trim();
            if (!keyword.isEmpty() && !list.contains(keyword)) {
                list.add(keyword);
            }
            if (list.size() >= 8) {
                break;
            }
        }
        return list;
    }
}
