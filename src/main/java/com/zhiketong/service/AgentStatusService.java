package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.AgentOnlineStatus;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 坐席状态服务：上线、下线、忙碌切换、心跳维护与工作台状态展示。
 *
 * <p>MySQL 为权威状态，Redis 缓存实时值（TTL 120 秒），
 * 心跳通过 Redis 节流，最多每 30 秒落库一次。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatusService {

    /** 默认最大并发接待数 */
    private static final int DEFAULT_MAX_CONCURRENT = 5;

    /** 最大并发接待数上限 */
    private static final int MAX_CONCURRENT_LIMIT = 20;

    /** 心跳落库节流（秒） */
    private static final long HEARTBEAT_THROTTLE_SECONDS = 30L;

    /** 统计时区 */
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final QueueService queueService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 坐席上线：已在线则只刷新心跳；达到并发上限自动置 BUSY。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentStatusVO online(Long agentId, Integer maxConcurrent) {
        AgentStatus status = getOrCreate(agentId);
        if (maxConcurrent != null) {
            if (maxConcurrent < 1 || maxConcurrent > MAX_CONCURRENT_LIMIT) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "最大并发接待数须在 1-20 之间");
            }
            status.setMaxConcurrent(maxConcurrent);
        }
        int current = status.getCurrentCount() == null ? 0 : status.getCurrentCount();
        int max = status.getMaxConcurrent() == null ? DEFAULT_MAX_CONCURRENT : status.getMaxConcurrent();
        status.setOnlineStatus(current >= max
                ? AgentOnlineStatus.BUSY.getCode() : AgentOnlineStatus.ONLINE.getCode());
        status.setLastHeartbeat(LocalDateTime.now());
        agentStatusMapper.updateById(status);
        queueService.cacheAgentStatus(agentId);
        return toVO(status);
    }

    /**
     * 坐席下线：已离线幂等返回；存在进行中的人工会话时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentStatusVO offline(Long agentId) {
        AgentStatus status = getOrCreate(agentId);
        if (AgentOnlineStatus.OFFLINE.getCode().equals(status.getOnlineStatus())) {
            return toVO(status);
        }
        Long humanSessions = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getAgentId, agentId)
                .eq(ChatSession::getStatus, SessionStatus.HUMAN.getCode()));
        if (humanSessions != null && humanSessions > 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "仍有 " + humanSessions + " 个进行中的会话，请先结束或转接后再下线");
        }
        status.setOnlineStatus(AgentOnlineStatus.OFFLINE.getCode());
        status.setLastHeartbeat(LocalDateTime.now());
        agentStatusMapper.updateById(status);
        queueService.clearAgentStatus(agentId);
        return toVO(status);
    }

    /**
     * 在线/忙碌切换：仅支持 ONLINE/BUSY，且必须处于在线状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentStatusVO updateOnlineStatus(Long agentId, String onlineStatus) {
        AgentOnlineStatus target = onlineStatus == null ? null
                : AgentOnlineStatus.fromCode(onlineStatus.trim().toUpperCase());
        if (target == null || target == AgentOnlineStatus.OFFLINE) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "在线状态仅支持 ONLINE/BUSY，下线请调用下线接口");
        }
        AgentStatus status = getOrCreate(agentId);
        if (AgentOnlineStatus.OFFLINE.getCode().equals(status.getOnlineStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "当前处于离线状态，请先上线");
        }
        status.setOnlineStatus(target.getCode());
        status.setLastHeartbeat(LocalDateTime.now());
        agentStatusMapper.updateById(status);
        queueService.cacheAgentStatus(agentId);
        return toVO(status);
    }

    /**
     * 坐席心跳：Redis 节流 30 秒，在线状态下刷新 last_heartbeat。
     */
    public void heartbeat(Long agentId) {
        if (agentId == null) {
            return;
        }
        try {
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                    Constants.AGENT_HEARTBEAT_PREFIX + agentId, "1",
                    HEARTBEAT_THROTTLE_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
        } catch (Exception e) {
            log.debug("坐席心跳节流失败，按需刷新：{}", e.getMessage());
        }
        try {
            agentStatusMapper.update(null, new LambdaUpdateWrapper<AgentStatus>()
                    .set(AgentStatus::getLastHeartbeat, LocalDateTime.now())
                    .eq(AgentStatus::getAgentId, agentId)
                    .ne(AgentStatus::getOnlineStatus, AgentOnlineStatus.OFFLINE.getCode()));
            queueService.cacheAgentStatus(agentId);
        } catch (Exception e) {
            log.warn("刷新坐席心跳失败：agentId={}, error={}", agentId, e.getMessage());
        }
    }

    /**
     * 查询坐席状态，不存在时创建初始离线记录。
     */
    public AgentStatusVO getStatus(Long agentId) {
        return toVO(getOrCreate(agentId));
    }

    /**
     * 查询全部坐席状态（管理端只读视图），按在线、忙碌、离线排序。
     */
    public List<AgentStatusVO> list() {
        List<AgentStatus> statuses = agentStatusMapper.selectList(
                new LambdaQueryWrapper<AgentStatus>().orderByAsc(AgentStatus::getAgentId));
        statuses.sort(Comparator.comparingInt((AgentStatus status) -> statusRank(status.getOnlineStatus()))
                .thenComparing(AgentStatus::getAgentId));
        List<AgentStatusVO> list = new ArrayList<>(statuses.size());
        for (AgentStatus status : statuses) {
            list.add(toVO(status));
        }
        return list;
    }

    /**
     * 在线状态排序权重：在线 0、忙碌 1、离线 2。
     */
    private int statusRank(String onlineStatus) {
        if (AgentOnlineStatus.ONLINE.getCode().equals(onlineStatus)) {
            return 0;
        }
        if (AgentOnlineStatus.BUSY.getCode().equals(onlineStatus)) {
            return 1;
        }
        return 2;
    }

    /**
     * 查询坐席状态实体，不存在时创建初始离线记录。
     */
    public AgentStatus getOrCreate(Long agentId) {
        AgentStatus status = agentStatusMapper.selectOne(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getAgentId, agentId)
                .last("LIMIT 1"));
        if (status != null) {
            return status;
        }
        status = new AgentStatus();
        status.setAgentId(agentId);
        status.setOnlineStatus(AgentOnlineStatus.OFFLINE.getCode());
        status.setMaxConcurrent(DEFAULT_MAX_CONCURRENT);
        status.setCurrentCount(0);
        status.setTotalServed(0);
        try {
            agentStatusMapper.insert(status);
        } catch (DuplicateKeyException e) {
            // 并发初始化时唯一键兜底，回读已存在记录
            status = agentStatusMapper.selectOne(new LambdaQueryWrapper<AgentStatus>()
                    .eq(AgentStatus::getAgentId, agentId)
                    .last("LIMIT 1"));
        }
        return status;
    }

    private AgentStatusVO toVO(AgentStatus status) {
        AgentStatusVO vo = new AgentStatusVO();
        vo.setId(status.getId());
        vo.setAgentId(status.getAgentId());
        vo.setOnlineStatus(status.getOnlineStatus());
        vo.setMaxConcurrent(status.getMaxConcurrent());
        vo.setCurrentCount(status.getCurrentCount());
        vo.setTotalServed(status.getTotalServed());
        vo.setLastHeartbeat(status.getLastHeartbeat());
        SysUser user = sysUserMapper.selectById(status.getAgentId());
        if (user != null) {
            vo.setAgentName(user.getNickname() == null ? user.getUsername() : user.getNickname());
        }
        LocalDateTime todayStart = LocalDate.now(STAT_ZONE).atStartOfDay();
        vo.setTodayServed(countSessions(status.getAgentId(), "accept_time", todayStart));
        vo.setAvgRating(avgRating(status.getAgentId(), todayStart));
        return vo;
    }

    /**
     * 统计坐席指定时间字段在今日的会话数。
     */
    private int countSessions(Long agentId, String timeColumn, LocalDateTime start) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("agent_id", agentId).ge(timeColumn, start);
        Long count = chatSessionMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 统计坐席今日结束会话的平均评分，无评分返回 null。
     */
    private BigDecimal avgRating(Long agentId, LocalDateTime start) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(rating) AS avg_rating")
                .eq("agent_id", agentId)
                .isNotNull("rating")
                .ge("end_time", start);
        List<Map<String, Object>> rows = chatSessionMapper.selectMaps(wrapper);
        // 聚合查询无匹配数据时，MyBatis 可能返回单个全 NULL 行（映射结果为 null）
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        Object value = rows.get(0).get("avg_rating");
        if (value == null) {
            return null;
        }
        return new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP);
    }
}
