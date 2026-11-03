package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.enums.AgentOnlineStatus;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 排队会话自动分配服务。
 *
 * <p>分配顺序：先按会话的期望技能组{@code chat_session.skill_group_id}筛选组内在岗坐席，
 * 组内无可用坐席时降级为全体在岗坐席，避免会话被单一技能组长期堵住。可用坐席条件为
 * ONLINE、{@code current_count < max_concurrent}、心跳未超过 120 秒。</p>
 *
 * <p>候选挑选策略由 {@code zhiketong.assign.strategy} 控制：
 * {@code MIN_LOAD}（默认）先比当前负载，同负载下再比技能组能力值（{@code sys_user_skill.level}）；
 * {@code ROUND_ROBIN} 按 Redis 游标在候选间轮询。接入动作仍委托 {@link QueueService#accept(Long, Long)} 完成，
 * 事务与并发锁语义不变。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDispatcherService {

    /** 单轮最多分配会话数，防止异常队列长时间占用线程 */
    private static final int MAX_DISPATCH_PER_ROUND = 20;

    /** 每轮从排队前端扫描的会话数，用于跳过技能组暂无人接待的会话 */
    private static final int SCAN_BATCH = 50;

    /** 坐席心跳有效时长（秒），超时视为离线不参与分配 */
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 120;

    /** 最小负载策略的候选坐席查询条数 */
    private static final int CANDIDATE_LIMIT = 5;

    /** 轮询策略的候选坐席查询条数 */
    private static final int ROUND_ROBIN_CANDIDATE_LIMIT = 50;

    /** 轮询策略标识 */
    private static final String STRATEGY_ROUND_ROBIN = "ROUND_ROBIN";

    /** 全体坐席的轮询游标标识 */
    private static final String SCOPE_ALL = "ALL";

    private final QueueService queueService;
    private final SkillGroupService skillGroupService;
    private final AgentStatusMapper agentStatusMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 分配策略：MIN_LOAD 最小负载优先（默认）/ ROUND_ROBIN 轮询 */
    @Value("${zhiketong.assign.strategy:MIN_LOAD}")
    private String assignStrategy;

    /**
     * 依次为排队会话分配可用坐席，直到没有可分配的会话或坐席。
     *
     * @return 本轮成功接入的会话数
     */
    public int dispatchNext() {
        int assigned = 0;
        for (int round = 0; round < MAX_DISPATCH_PER_ROUND; round++) {
            List<ChatSession> waiting = queueService.headWaiting(SCAN_BATCH);
            if (waiting.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (ChatSession session : waiting) {
                AgentStatus agent = findAvailableAgent(session.getSkillGroupId());
                if (agent == null) {
                    // 该会话对应技能组暂无可用坐席，继续看后续会话，避免队首阻塞
                    continue;
                }
                try {
                    queueService.accept(session.getId(), agent.getAgentId());
                    assigned++;
                    progressed = true;
                    break;
                } catch (BusinessException e) {
                    if (e.getCode() == ResultCode.NOT_FOUND.getCode()
                            || e.getCode() == ResultCode.CONFLICT.getCode()) {
                        progressed = cleanStaleQueueItem(session, e) || progressed;
                        continue;
                    }
                    log.warn("自动分配失败：sessionId={}, agentId={}, reason={}",
                            session.getId(), agent.getAgentId(), e.getMessage());
                    return assigned;
                } catch (Exception e) {
                    log.warn("自动分配异常：sessionId={}, agentId={}, error={}",
                            session.getId(), agent.getAgentId(), e.getMessage());
                    return assigned;
                }
            }
            if (!progressed) {
                break;
            }
        }
        if (assigned > 0) {
            log.info("自动分配完成：本轮接入 {} 个会话", assigned);
        }
        return assigned;
    }

    /**
     * 是否存在可立即接入的坐席（不限技能组）。
     */
    public boolean hasAvailableAgent() {
        return findAvailableAgent(null) != null;
    }

    /**
     * 挑选指定技能组下最优可用坐席；组内无可用坐席时降级为全体在岗坐席。
     *
     * @param skillGroupId 会话期望技能组，null 表示不限
     */
    private AgentStatus findAvailableAgent(Long skillGroupId) {
        if (skillGroupId != null) {
            Map<Long, Integer> levels = skillGroupService.levelMapOf(skillGroupId);
            if (!levels.isEmpty()) {
                AgentStatus agent = pickAgent(levels.keySet(), String.valueOf(skillGroupId), levels);
                if (agent != null) {
                    return agent;
                }
                log.debug("技能组内无可用坐席，降级为全体分配：skillGroupId={}", skillGroupId);
            }
        }
        return pickAgent(Collections.emptySet(), SCOPE_ALL, Collections.emptyMap());
    }

    /**
     * 在候选坐席中按策略挑选一人，没有可用坐席时返回 null。
     *
     * @param memberIds 技能组坐席范围，空集合表示不限
     * @param scopeKey  轮询游标作用域：技能组 ID 或 ALL
     * @param levels    技能组能力值映射，空表示不限（全体分配时无能力值概念）
     */
    private AgentStatus pickAgent(Collection<Long> memberIds, String scopeKey, Map<Long, Integer> levels) {
        LocalDateTime heartbeatThreshold = LocalDateTime.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        boolean roundRobin = STRATEGY_ROUND_ROBIN.equalsIgnoreCase(assignStrategy);

        LambdaQueryWrapper<AgentStatus> wrapper = new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getOnlineStatus, AgentOnlineStatus.ONLINE.getCode())
                .apply("current_count < max_concurrent")
                .ge(AgentStatus::getLastHeartbeat, heartbeatThreshold);
        if (!memberIds.isEmpty()) {
            wrapper.in(AgentStatus::getAgentId, memberIds);
        }
        if (roundRobin) {
            wrapper.orderByAsc(AgentStatus::getAgentId)
                    .last("LIMIT " + ROUND_ROBIN_CANDIDATE_LIMIT);
        } else {
            wrapper.orderByAsc(AgentStatus::getCurrentCount)
                    .orderByAsc(AgentStatus::getLastHeartbeat)
                    .last("LIMIT " + CANDIDATE_LIMIT);
        }

        List<AgentStatus> candidates = agentStatusMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return null;
        }
        if (!roundRobin) {
            // 已在 SQL 层按负载升序，同负载段内再按能力值取高者，避免新人抢走熟手的会话
            AgentStatus best = candidates.get(0);
            int minCount = countOf(best);
            int bestLevel = levelOf(best.getAgentId(), levels);
            for (int i = 1; i < candidates.size(); i++) {
                AgentStatus candidate = candidates.get(i);
                if (countOf(candidate) != minCount) {
                    break;
                }
                int level = levelOf(candidate.getAgentId(), levels);
                if (level > bestLevel) {
                    best = candidate;
                    bestLevel = level;
                }
            }
            return best;
        }
        return candidates.get(nextCursor(scopeKey, candidates.size()));
    }

    private int countOf(AgentStatus agent) {
        return agent.getCurrentCount() == null ? 0 : agent.getCurrentCount();
    }

    private int levelOf(Long agentId, Map<Long, Integer> levels) {
        Integer level = levels.get(agentId);
        return level == null ? 0 : level;
    }

    /**
     * 轮询游标：Redis 自增取模，Redis 不可用时退化为按秒轮转。
     */
    private int nextCursor(String scopeKey, int size) {
        if (size <= 1) {
            return 0;
        }
        try {
            Long value = stringRedisTemplate.opsForValue()
                    .increment(Constants.ASSIGN_ROUND_ROBIN_PREFIX + scopeKey);
            if (value != null) {
                return (int) ((value - 1) % size);
            }
        } catch (Exception e) {
            log.debug("读取分配轮询游标失败，退化为时间轮转：{}", e.getMessage());
        }
        return (int) ((System.currentTimeMillis() / 1000L) % size);
    }

    /**
     * 入库状态已变化的会话移出 Redis 队列，避免脏队列项长期堵住队首。
     *
     * @return 是否清理了队列项
     */
    private boolean cleanStaleQueueItem(ChatSession session, BusinessException e) {
        ChatSession latest = chatSessionMapper.selectById(session.getId());
        if (latest != null && SessionStatus.WAITING.getCode().equals(latest.getStatus())) {
            log.info("自动分配跳过会话：sessionId={}, reason={}", session.getId(), e.getMessage());
            return false;
        }
        log.info("自动分配清理失效排队会话：sessionId={}, reason={}", session.getId(), e.getMessage());
        queueService.remove(session.getId());
        return true;
    }
}
