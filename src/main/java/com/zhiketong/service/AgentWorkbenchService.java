package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.agent.AgentAcceptResponse;
import com.zhiketong.dto.agent.AgentBriefVO;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.dto.agent.AgentTransferRequest;
import com.zhiketong.dto.agent.AgentWorkbenchStatsVO;
import com.zhiketong.dto.agent.QueuePageResult;
import com.zhiketong.dto.chat.CloseSessionRequest;
import com.zhiketong.dto.chat.SessionVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.AgentOnlineStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 坐席工作台服务（R-43 ~ R-48）：队列查看、接入、我的会话、结束、转接与统计。
 *
 * <p>核心状态变更委托 {@link QueueService} 与 {@link ChatService}，
 * 本类只做坐席视角的编排与响应组装。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWorkbenchService {

    /** 统计时区 */
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private final AgentStatusService agentStatusService;
    private final QueueService queueService;
    private final AgentDispatcherService agentDispatcherService;
    private final ChatService chatService;
    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 坐席上线（R-40）：上线成功后立即尝试分配排队会话。
     */
    public AgentStatusVO online(Long agentId, Integer maxConcurrent) {
        agentStatusService.online(agentId, maxConcurrent);
        agentDispatcherService.dispatchNext();
        return agentStatusService.getStatus(agentId);
    }

    /**
     * 查询坐席当前状态。
     */
    public AgentStatusVO status(Long agentId) {
        return agentStatusService.getStatus(agentId);
    }

    /**
     * 全部坐席状态列表（管理端只读视图）。
     */
    public List<AgentStatusVO> listAgents() {
        return agentStatusService.list();
    }

    /**
     * 坐席下线（R-41）。
     */
    public AgentStatusVO offline(Long agentId) {
        return agentStatusService.offline(agentId);
    }

    /**
     * 在线/忙碌切换（R-42）：切换回 ONLINE 时补分配排队会话。
     */
    public AgentStatusVO updateStatus(Long agentId, String onlineStatus) {
        AgentStatusVO vo = agentStatusService.updateOnlineStatus(agentId, onlineStatus);
        if (AgentOnlineStatus.ONLINE.getCode().equals(vo.getOnlineStatus())) {
            agentDispatcherService.dispatchNext();
            return agentStatusService.getStatus(agentId);
        }
        return vo;
    }

    /**
     * 待接入队列（R-43）。
     */
    public QueuePageResult queue(Integer pageNum, Integer pageSize) {
        return queueService.listQueue(pageNum, pageSize);
    }

    /**
     * 接入排队会话（R-44）。
     */
    public AgentAcceptResponse accept(Long sessionId, Long agentId) {
        QueueService.AcceptResult result = queueService.accept(sessionId, agentId);
        AgentAcceptResponse response = new AgentAcceptResponse();
        response.setSession(chatService.toVO(result.getSession()));
        AgentBriefVO user = new AgentBriefVO();
        SysUser sysUser = result.getUser();
        if (sysUser != null) {
            user.setId(sysUser.getId());
            user.setNickname(displayName(sysUser));
            user.setAvatar(sysUser.getAvatar());
        }
        response.setUser(user);
        response.setLastMessages(result.getLastMessages());
        return response;
    }

    /**
     * 我的会话列表（R-45）：数据范围由 ChatService 按 AGENT 角色收敛。
     */
    public PageResult<SessionVO> mySessions(LoginUser loginUser, Integer pageNum, Integer pageSize,
                                            String status, String keyword, String startDate, String endDate) {
        if (loginUser == null || !Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅坐席可查看我的会话");
        }
        return chatService.pageSessions(loginUser, pageNum, pageSize, status, keyword, startDate, endDate);
    }

    /**
     * 结束坐席会话（R-46）：返回会话信息并附带坐席最新状态。
     */
    public Map<String, Object> closeSession(Long sessionId, LoginUser loginUser, CloseSessionRequest request) {
        SessionVO session = chatService.closeSession(sessionId, loginUser, request);
        Map<String, Object> data = objectMapper.convertValue(session,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        data.put("agentStatus", agentStatusService.getStatus(loginUser.getUserId()));
        return data;
    }

    /**
     * 会话转接（R-47）：转接完成后原坐席负载下降，尝试补分配排队会话。
     */
    public Map<String, Object> transfer(Long sessionId, Long agentId, AgentTransferRequest request) {
        Map<String, Object> data = queueService.transferToAgent(
                sessionId, agentId, request.getTargetAgentId(), request.getReason());
        agentDispatcherService.dispatchNext();
        return data;
    }

    /**
     * 坐席工作台统计（R-48）：仅统计本人数据，今日按 Asia/Shanghai 自然日。
     */
    public AgentWorkbenchStatsVO stats(Long agentId) {
        AgentStatus status = agentStatusService.getOrCreate(agentId);
        AgentWorkbenchStatsVO vo = new AgentWorkbenchStatsVO();
        vo.setOnlineStatus(status.getOnlineStatus());
        vo.setCurrentCount(nvl(status.getCurrentCount()));
        vo.setMaxConcurrent(nvl(status.getMaxConcurrent()));
        vo.setTotalServed(nvl(status.getTotalServed()));
        vo.setQueueSize(queueService.size());

        LocalDateTime todayStart = LocalDate.now(STAT_ZONE).atStartOfDay();
        vo.setTodayServed(countSessions(agentId, "accept_time", todayStart));
        vo.setTodayClosed(countSessions(agentId, "end_time", todayStart));
        vo.setTodayAvgRating(avgRating(agentId, todayStart));
        vo.setAvgAcceptSeconds(avgSeconds("AVG(TIMESTAMPDIFF(SECOND, transfer_time, accept_time))",
                agentId, "accept_time", todayStart));
        vo.setAvgDurationSeconds(avgSeconds("AVG(TIMESTAMPDIFF(SECOND, accept_time, end_time))",
                agentId, "end_time", todayStart));
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
        Object value = firstValue(chatSessionMapper.selectMaps(wrapper), "avg_rating");
        if (value == null) {
            return null;
        }
        return new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 统计坐席今日平均耗时（秒），无数据返回 0。
     */
    private Integer avgSeconds(String avgExpression, Long agentId, String timeColumn, LocalDateTime start) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.select(avgExpression + " AS avg_seconds")
                .eq("agent_id", agentId)
                .isNotNull(timeColumn)
                .ge(timeColumn, start);
        Object value = firstValue(chatSessionMapper.selectMaps(wrapper), "avg_seconds");
        if (value == null) {
            return 0;
        }
        return new BigDecimal(value.toString()).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private Object firstValue(List<Map<String, Object>> rows, String column) {
        // 聚合查询无匹配数据时，MyBatis 可能返回单个全 NULL 行（映射结果为 null）
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        return rows.get(0).get(column);
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return user.getNickname() == null ? user.getUsername() : user.getNickname();
    }
}
