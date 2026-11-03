package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.agent.AgentBriefVO;
import com.zhiketong.dto.agent.QueuePageResult;
import com.zhiketong.dto.agent.QueueSessionVO;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SkillGroup;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.websocket.ChatPushService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 转人工排队与分配服务：Redis ZSET 排队、坐席接入、会话转接。
 *
 * <p>队列成员为 sessionId，score 为进入排队时间戳；接入使用 Redis 短锁
 * {@code zk:lock:accept:{sessionId}} 保证并发安全，权威状态仍以 MySQL 为准。</p>
 *
 * <p>排队数据采用「MySQL 权威 + Redis 加速」：Redis 负责排位查询与实时推送，
 * 一旦 Redis 不可用或重启丢键，排位、排队长度、队首与排队列表全部回退到
 * {@code chat_session} 中 {@code status = WAITING} 的记录，保证转人工链路不中断。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    /** 排队元信息有效期（分钟） */
    private static final int META_TTL_MINUTES = 30;

    /** 接入/转接锁有效期（毫秒） */
    private static final long LOCK_TTL_MILLIS = 3000L;

    /** 坐席状态缓存有效期（秒） */
    private static final long AGENT_STATUS_TTL_SECONDS = 120L;

    /** 推送排队进度时最多处理的会话数 */
    private static final int MAX_QUEUE_NOTIFY = 50;

    /** 接入时返回的最近消息条数 */
    private static final int ACCEPT_LAST_MESSAGES = 10;

    /** 数据库回退计算排位时最多扫描的等待会话数 */
    private static final int MAX_QUEUE_SCAN = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatSessionMapper chatSessionMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatMessageService chatMessageService;
    private final ChatPushService chatPushService;
    private final SkillGroupService skillGroupService;

    /**
     * 会话入队：写入 ZSET 与排队元信息。
     */
    public void enqueue(ChatSession session, String reason) {
        if (session == null || session.getId() == null) {
            return;
        }
        try {
            long queueTime = session.getQueueEnterTime() == null
                    ? System.currentTimeMillis()
                    : session.getQueueEnterTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(
                    Constants.QUEUE_WAITING_KEY, String.valueOf(session.getId()), queueTime);
            Map<String, String> meta = new HashMap<>(4);
            meta.put("userId", String.valueOf(session.getUserId()));
            meta.put("sessionNo", session.getSessionNo() == null ? "" : session.getSessionNo());
            meta.put("queueEnterTime", String.valueOf(queueTime));
            meta.put("transferReason", reason == null ? "" : reason);
            String metaKey = Constants.QUEUE_META_PREFIX + session.getId();
            stringRedisTemplate.opsForHash().putAll(metaKey, meta);
            stringRedisTemplate.expire(metaKey, META_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // Redis 不可用时不影响会话进入 WAITING，坐席仍可通过 R-43 之外的列表接入
            log.warn("会话入队失败：sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    /**
     * 移出排队队列并清理元信息。
     */
    public void remove(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().remove(Constants.QUEUE_WAITING_KEY, String.valueOf(sessionId));
            stringRedisTemplate.delete(Constants.QUEUE_META_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("移除排队会话失败：sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 当前排位，从 1 开始；不在队列返回 0。
     * <p>Redis 未命中时回退到数据库按排队时间计算，避免 Redis 丢键后误报排位。</p>
     */
    public int position(Long sessionId) {
        if (sessionId == null) {
            return 0;
        }
        try {
            Long rank = stringRedisTemplate.opsForZSet()
                    .rank(Constants.QUEUE_WAITING_KEY, String.valueOf(sessionId));
            if (rank != null) {
                return rank.intValue() + 1;
            }
        } catch (Exception e) {
            log.debug("查询排队位次失败，回退数据库：{}", e.getMessage());
        }
        return positionFromDb(sessionId);
    }

    /**
     * 当前排队总数：Redis 计数与数据库等待会话数取较大值，保证 Redis 丢键时不丢排队人数。
     */
    public int size() {
        int redisSize = 0;
        try {
            Long size = stringRedisTemplate.opsForZSet().zCard(Constants.QUEUE_WAITING_KEY);
            redisSize = size == null ? 0 : size.intValue();
        } catch (Exception e) {
            log.debug("查询排队人数失败，回退数据库：{}", e.getMessage());
        }
        return Math.max(redisSize, countWaitingFromDb());
    }

    /**
     * 取最早排队的会话 ID，不出队。
     */
    public Long oldestSessionId() {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .rangeWithScores(Constants.QUEUE_WAITING_KEY, 0, 0);
            if (tuples != null && !tuples.isEmpty()) {
                Long sessionId = parseLong(tuples.iterator().next().getValue());
                if (sessionId != null) {
                    return sessionId;
                }
            }
        } catch (Exception e) {
            log.debug("读取队首会话失败，回退数据库：{}", e.getMessage());
        }
        return oldestSessionIdFromDb();
    }

    /**
     * 取排队前端若干会话（数据库权威），供自动分配按技能组筛选候选。
     *
     * @param limit 扫描条数，超过内部上限时按上限处理
     */
    public List<ChatSession> headWaiting(int limit) {
        int size = limit <= 0 ? 1 : Math.min(limit, MAX_QUEUE_SCAN);
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                .orderByAsc(ChatSession::getQueueEnterTime)
                .orderByAsc(ChatSession::getId)
                .last("LIMIT " + size));
    }

    /**
     * 排队列表（R-43）：按排队时间升序分页。
     */
    public QueuePageResult listQueue(Integer pageNum, Integer pageSize) {
        int num = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? Constants.DEFAULT_PAGE_SIZE : pageSize;
        if (num < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "pageNum 必须大于等于 1");
        }
        if (size < 1 || size > Constants.MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "pageSize 必须在 1-100 之间");
        }

        QueuePageResult result = new QueuePageResult();
        result.setPageNum(num);
        result.setPageSize(size);

        // 排队列表以 MySQL 为准：Redis 丢键或不可用时坐席仍能看到完整等待队列
        Page<ChatSession> waitingPage = chatSessionMapper.selectPage(new Page<>(num, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                        .orderByAsc(ChatSession::getQueueEnterTime)
                        .orderByAsc(ChatSession::getId));

        List<ChatSession> sessions = waitingPage.getRecords();
        int total = (int) waitingPage.getTotal();
        Map<Long, SysUser> users = userMap(sessions);
        Set<Long> groupIds = new HashSet<>();
        for (ChatSession session : sessions) {
            if (session.getSkillGroupId() != null) {
                groupIds.add(session.getSkillGroupId());
            }
        }
        Map<Long, String> groupNames = skillGroupService.nameMap(groupIds);
        LocalDateTime now = LocalDateTime.now();
        int offset = (num - 1) * size;
        List<QueueSessionVO> list = new ArrayList<>(sessions.size());
        for (int i = 0; i < sessions.size(); i++) {
            ChatSession session = sessions.get(i);
            QueueSessionVO vo = new QueueSessionVO();
            vo.setQueuePosition(offset + i + 1);
            vo.setSessionId(session.getId());
            vo.setSessionNo(session.getSessionNo());
            vo.setUserId(session.getUserId());
            vo.setUserName(displayName(users.get(session.getUserId())));
            vo.setSkillGroupId(session.getSkillGroupId());
            vo.setSkillGroupName(session.getSkillGroupId() == null
                    ? null : groupNames.get(session.getSkillGroupId()));
            vo.setQueueEnterTime(session.getQueueEnterTime());
            vo.setWaitSeconds(waitSeconds(session.getQueueEnterTime(), now));
            List<MessageVO> lastMessages = chatMessageService.recent(session.getId(), 1);
            if (!lastMessages.isEmpty()) {
                vo.setLastMessage(lastMessages.get(0).getContent());
            }
            vo.setTransferReason(queueReason(session.getId()));
            list.add(vo);
        }
        result.setList(list);
        result.setTotal(total);
        result.setQueueSize(total);
        result.setPages((total + size - 1) / size);
        return result;
    }

    /**
     * 接入排队会话（R-44）：WAITING → HUMAN，坐席负载 +1。
     */
    @Transactional(rollbackFor = Exception.class)
    public AcceptResult accept(Long sessionId, Long agentId) {
        String lockKey = Constants.LOCK_ACCEPT_PREFIX + sessionId;
        String token = UUID.randomUUID().toString().replace("-", "");
        if (!tryLock(lockKey, token)) {
            throw new BusinessException(ResultCode.CONFLICT, "会话正在被其他坐席接入，请稍后重试");
        }
        try {
            ChatSession session = chatSessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
            }
            if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "会话已结束");
            }
            if (SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
                if (agentId.equals(session.getAgentId())) {
                    return buildAcceptResult(session);
                }
                throw new BusinessException(ResultCode.CONFLICT, "会话已被其他坐席接入");
            }
            if (!SessionStatus.WAITING.getCode().equals(session.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "会话不在排队中，无法接入");
            }
            checkSkillGroup(session, agentId);
            requireAvailableAgent(agentId);

            LocalDateTime now = LocalDateTime.now();
            int rows = chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getId, sessionId)
                    .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                    .set(ChatSession::getStatus, SessionStatus.HUMAN.getCode())
                    .set(ChatSession::getAgentId, agentId)
                    .set(ChatSession::getAcceptTime, now));
            if (rows == 0) {
                throw new BusinessException(ResultCode.CONFLICT, "会话已被其他坐席接入");
            }
            incrementAgentLoad(agentId);
            remove(sessionId);

            ChatSession accepted = chatSessionMapper.selectById(sessionId);
            SysUser user = sysUserMapper.selectById(accepted.getUserId());
            SysUser agent = sysUserMapper.selectById(agentId);
            String agentName = displayName(agent);
            chatMessageService.save(sessionId, SenderType.SYSTEM, 0L,
                    "客服 " + agentName + " 已接入会话", "TEXT", null, null, "SYSTEM", false);
            List<MessageVO> lastMessages = chatMessageService.recent(sessionId, ACCEPT_LAST_MESSAGES);

            chatPushService.pushTransferAccepted(accepted.getUserId(), accepted, agentName);
            chatPushService.pushSessionAssigned(agentId, sessionId, sessionSummary(accepted),
                    userBrief(user), lastMessages, false);
            notifyQueuePositions();
            log.info("坐席接入会话：sessionId={}, agentId={}", sessionId, agentId);
            return new AcceptResult(accepted, user, lastMessages);
        } finally {
            unlock(lockKey, token);
        }
    }

    /**
     * 坐席转接（R-47）：原坐席负载 -1，目标坐席负载 +1。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferToAgent(Long sessionId, Long fromAgentId,
                                               Long targetAgentId, String reason) {
        if (targetAgentId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标坐席不能为空");
        }
        if (targetAgentId.equals(fromAgentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能转接给自己");
        }
        String lockKey = Constants.LOCK_TRANSFER_PREFIX + sessionId;
        String token = UUID.randomUUID().toString().replace("-", "");
        if (!tryLock(lockKey, token)) {
            throw new BusinessException(ResultCode.CONFLICT, "会话正在转接中，请稍后重试");
        }
        try {
            ChatSession session = chatSessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
            }
            if (!SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "仅人工接待中的会话可以转接");
            }
            if (!fromAgentId.equals(session.getAgentId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅可转接本人接待的会话");
            }
            SysUser target = sysUserMapper.selectById(targetAgentId);
            if (target == null || !Constants.ROLE_AGENT.equals(target.getRoleCode())
                    || !Integer.valueOf(1).equals(target.getStatus())) {
                throw new BusinessException(ResultCode.NOT_FOUND, "目标坐席不存在或不可用");
            }
            requireAvailableAgent(targetAgentId);

            LocalDateTime now = LocalDateTime.now();
            int rows = chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getId, sessionId)
                    .eq(ChatSession::getAgentId, fromAgentId)
                    .eq(ChatSession::getStatus, SessionStatus.HUMAN.getCode())
                    .set(ChatSession::getAgentId, targetAgentId)
                    .set(ChatSession::getAcceptTime, now));
            if (rows == 0) {
                throw new BusinessException(ResultCode.CONFLICT, "会话状态已变化，请刷新后重试");
            }
            decrementAgentLoad(fromAgentId);
            incrementAgentLoad(targetAgentId);

            String fromName = displayName(sysUserMapper.selectById(fromAgentId));
            String toName = displayName(target);
            String remark = "会话由 " + fromName + " 转接给 " + toName
                    + (StringUtils.hasText(reason) ? "，原因：" + reason.trim() : "");
            chatMessageService.save(sessionId, SenderType.SYSTEM, 0L, remark, "TEXT",
                    null, null, "SYSTEM", false);

            ChatSession transferred = chatSessionMapper.selectById(sessionId);
            SysUser user = sysUserMapper.selectById(transferred.getUserId());
            chatPushService.pushTransferAccepted(transferred.getUserId(), transferred, toName);
            chatPushService.pushSessionAssigned(targetAgentId, sessionId, sessionSummary(transferred),
                    userBrief(user), chatMessageService.recent(sessionId, ACCEPT_LAST_MESSAGES), true);
            chatPushService.pushSystemNotice(fromAgentId, "INFO", "会话已转接",
                    "会话 " + transferred.getSessionNo() + " 已转接给 " + toName, "SESSION", sessionId, null);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", sessionId);
            data.put("fromAgentId", fromAgentId);
            data.put("toAgentId", targetAgentId);
            data.put("toAgentName", toName);
            data.put("sessionStatus", SessionStatus.HUMAN.getCode());
            log.info("会话转接：sessionId={}, from={}, to={}", sessionId, fromAgentId, targetAgentId);
            return data;
        } finally {
            unlock(lockKey, token);
        }
    }

    /**
     * 判断坐席是否可接入新会话。
     */
    public boolean hasCapacity(Long agentId) {
        AgentStatus status = findAgentStatus(agentId);
        if (status == null || !"ONLINE".equals(status.getOnlineStatus())) {
            return false;
        }
        int current = status.getCurrentCount() == null ? 0 : status.getCurrentCount();
        int max = status.getMaxConcurrent() == null ? 0 : status.getMaxConcurrent();
        return current < max;
    }

    /**
     * 技能组校验：会话指定了咨询类型且组内已有坐席时，仅组内坐席可直接接入；
     * 组内尚无成员时视为不限，避免空组造成会话无人可接。
     */
    private void checkSkillGroup(ChatSession session, Long agentId) {
        Long groupId = session.getSkillGroupId();
        if (groupId == null || skillGroupService.isMember(groupId, agentId)) {
            return;
        }
        if (!skillGroupService.hasMembers(groupId)) {
            return;
        }
        SkillGroup group = skillGroupService.activeGroup(groupId);
        String name = group == null ? "指定" : group.getName();
        throw new BusinessException(ResultCode.CONFLICT,
                "该会话属于「" + name + "」技能组，请由组内坐席接入或改由自动分配");
    }

    private void requireAvailableAgent(Long agentId) {
        AgentStatus status = findAgentStatus(agentId);
        if (status == null || "OFFLINE".equals(status.getOnlineStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "坐席未上线，请先上线");
        }
        if ("BUSY".equals(status.getOnlineStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "坐席处于忙碌状态，暂不能接入新会话");
        }
        int current = status.getCurrentCount() == null ? 0 : status.getCurrentCount();
        int max = status.getMaxConcurrent() == null ? 0 : status.getMaxConcurrent();
        if (current >= max) {
            throw new BusinessException(ResultCode.CONFLICT, "坐席接待并发已满，请先结束会话");
        }
    }

    private AgentStatus findAgentStatus(Long agentId) {
        return agentStatusMapper.selectOne(new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getAgentId, agentId)
                .last("LIMIT 1"));
    }

    private void incrementAgentLoad(Long agentId) {
        agentStatusMapper.update(null, new LambdaUpdateWrapper<AgentStatus>()
                .setSql("online_status = CASE WHEN current_count + 1 >= max_concurrent THEN 'BUSY' ELSE online_status END")
                .setSql("current_count = current_count + 1")
                .eq(AgentStatus::getAgentId, agentId));
        cacheAgentStatus(agentId);
    }

    private void decrementAgentLoad(Long agentId) {
        agentStatusMapper.update(null, new LambdaUpdateWrapper<AgentStatus>()
                .setSql("current_count = CASE WHEN current_count > 0 THEN current_count - 1 ELSE 0 END")
                .setSql("online_status = CASE WHEN online_status = 'BUSY' AND current_count < max_concurrent THEN 'ONLINE' ELSE online_status END")
                .eq(AgentStatus::getAgentId, agentId));
        cacheAgentStatus(agentId);
    }

    /**
     * 把坐席最新状态写入 Redis，TTL 120 秒。
     */
    public void cacheAgentStatus(Long agentId) {
        if (agentId == null) {
            return;
        }
        AgentStatus status = findAgentStatus(agentId);
        if (status == null) {
            return;
        }
        try {
            String key = Constants.AGENT_STATUS_PREFIX + agentId;
            Map<String, String> data = new HashMap<>(4);
            data.put("onlineStatus", status.getOnlineStatus());
            data.put("currentCount", String.valueOf(status.getCurrentCount()));
            data.put("maxConcurrent", String.valueOf(status.getMaxConcurrent()));
            data.put("lastHeartbeat", String.valueOf(System.currentTimeMillis()));
            stringRedisTemplate.opsForHash().putAll(key, data);
            stringRedisTemplate.expire(key, AGENT_STATUS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("缓存坐席状态失败：agentId={}, error={}", agentId, e.getMessage());
        }
    }

    private AcceptResult buildAcceptResult(ChatSession session) {
        SysUser user = sysUserMapper.selectById(session.getUserId());
        return new AcceptResult(session, user, chatMessageService.recent(session.getId(), ACCEPT_LAST_MESSAGES));
    }

    /**
     * 数据库中处于等待状态的会话数。
     */
    private int countWaitingFromDb() {
        try {
            Long count = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                    .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode()));
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            log.warn("统计排队会话失败：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 数据库中的队首会话：排队时间升序、同时间按 ID 升序，与排队列表保持一致。
     */
    private Long oldestSessionIdFromDb() {
        try {
            List<ChatSession> waiting = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                    .select(ChatSession::getId)
                    .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                    .orderByAsc(ChatSession::getQueueEnterTime)
                    .orderByAsc(ChatSession::getId)
                    .last("LIMIT 1"));
            return waiting.isEmpty() ? null : waiting.get(0).getId();
        } catch (Exception e) {
            log.warn("读取队首排队会话失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 数据库回退排位：与排队列表保持同一排序口径，不在排队中返回 0。
     */
    private int positionFromDb(Long sessionId) {
        try {
            List<ChatSession> waiting = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                    .select(ChatSession::getId)
                    .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode())
                    .orderByAsc(ChatSession::getQueueEnterTime)
                    .orderByAsc(ChatSession::getId)
                    .last("LIMIT " + MAX_QUEUE_SCAN));
            for (int i = 0; i < waiting.size(); i++) {
                if (sessionId.equals(waiting.get(i).getId())) {
                    return i + 1;
                }
            }
            return 0;
        } catch (Exception e) {
            log.warn("计算排队位次失败：sessionId={}, error={}", sessionId, e.getMessage());
            return 0;
        }
    }

    private Map<Long, ChatSession> sessionMap(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChatSession> sessions = chatSessionMapper.selectBatchIds(sessionIds);
        Map<Long, ChatSession> map = new HashMap<>(sessions.size());
        for (ChatSession session : sessions) {
            map.put(session.getId(), session);
        }
        return map;
    }

    private Map<Long, SysUser> userMap(java.util.Collection<ChatSession> sessions) {
        Set<Long> userIds = new HashSet<>();
        for (ChatSession session : sessions) {
            if (session.getUserId() != null) {
                userIds.add(session.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, SysUser> map = new HashMap<>(users.size());
        for (SysUser user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private String queueReason(Long sessionId) {
        try {
            Object value = stringRedisTemplate.opsForHash()
                    .get(Constants.QUEUE_META_PREFIX + sessionId, "transferReason");
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> sessionSummary(ChatSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId());
        data.put("sessionNo", session.getSessionNo());
        data.put("userId", session.getUserId());
        data.put("status", session.getStatus());
        data.put("skillGroupId", session.getSkillGroupId());
        data.put("queueEnterTime", session.getQueueEnterTime());
        data.put("acceptTime", session.getAcceptTime());
        return data;
    }

    private Map<String, Object> userBrief(SysUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (user == null) {
            return data;
        }
        data.put("id", user.getId());
        data.put("nickname", displayName(user));
        data.put("avatar", user.getAvatar());
        return data;
    }

    private AgentBriefVO agentBrief(SysUser user) {
        AgentBriefVO vo = new AgentBriefVO();
        if (user == null) {
            return vo;
        }
        vo.setId(user.getId());
        vo.setNickname(displayName(user));
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    /**
     * 队列变化后向前 50 位用户推送最新排位。
     */
    private void notifyQueuePositions() {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .rangeWithScores(Constants.QUEUE_WAITING_KEY, 0, MAX_QUEUE_NOTIFY - 1);
            if (tuples == null || tuples.isEmpty()) {
                return;
            }
            List<Long> ids = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Long id = parseLong(tuple.getValue());
                if (id != null) {
                    ids.add(id);
                }
            }
            Map<Long, ChatSession> sessions = sessionMap(ids);
            int total = size();
            int position = 0;
            Iterator<ZSetOperations.TypedTuple<String>> iterator = tuples.iterator();
            while (iterator.hasNext()) {
                Long sessionId = parseLong(iterator.next().getValue());
                ChatSession session = sessionId == null ? null : sessions.get(sessionId);
                if (session == null || !SessionStatus.WAITING.getCode().equals(session.getStatus())) {
                    if (sessionId != null) {
                        remove(sessionId);
                    }
                    continue;
                }
                position++;
                chatPushService.pushQueueStatus(session.getUserId(), sessionId,
                        position, total, estimateWaitSeconds(position));
            }
        } catch (Exception e) {
            log.debug("推送排队进度失败：{}", e.getMessage());
        }
    }

    private int estimateWaitSeconds(int position) {
        return Math.max(30, position * 60);
    }

    /**
     * 清理坐席状态缓存（下线时调用）。
     */
    public void clearAgentStatus(Long agentId) {
        if (agentId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(Constants.AGENT_STATUS_PREFIX + agentId);
        } catch (Exception e) {
            log.debug("清理坐席状态缓存失败：agentId={}, error={}", agentId, e.getMessage());
        }
    }

    private Long waitSeconds(LocalDateTime queueEnterTime, LocalDateTime now) {
        if (queueEnterTime == null) {
            return 0L;
        }
        long seconds = Duration.between(queueEnterTime, now).getSeconds();
        return Math.max(0L, seconds);
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean tryLock(String key, String token) {
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, token, LOCK_TTL_MILLIS, TimeUnit.MILLISECONDS);
            if (success == null) {
                // 连接重建等异常态下可能返回 null，按“Redis 不可用”降级为无锁，
                // 否则会被误判成“会话正在被其他坐席接入”，掩盖真实故障
                log.warn("Redis 锁返回空结果，按无锁处理：{}", key);
                return true;
            }
            return success;
        } catch (Exception e) {
            // Redis 不可用时退化为无锁，保证单机主流程可用
            log.warn("获取 Redis 锁失败，按无锁处理：{}", e.getMessage());
            return true;
        }
    }

    private void unlock(String key, String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(key);
            if (token.equals(current)) {
                stringRedisTemplate.delete(key);
            }
        } catch (Exception ignored) {
            // 忽略释放锁异常
        }
    }

    /**
     * 接入结果：会话、用户与最近消息，供工作台组装响应。
     */
    @Data
    public static class AcceptResult {

        private ChatSession session;

        private SysUser user;

        private List<MessageVO> lastMessages;

        public AcceptResult(ChatSession session, SysUser user, List<MessageVO> lastMessages) {
            this.session = session;
            this.user = user;
            this.lastMessages = lastMessages;
        }
    }
}
