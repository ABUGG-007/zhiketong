package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.agent.AgentStatusVO;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 坐席状态服务：上线、下线、忙碌切换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatusService {

    private static final int DEFAULT_MAX_CONCURRENT = 5;

    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatSessionMapper chatSessionMapper;

    /**
     * 坐席上线：已在线则只刷新心跳；达到并发上限自动置 BUSY。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentStatusVO online(Long agentId, Integer maxConcurrent) {
        AgentStatus status = getOrCreate(agentId);
        if (maxConcurrent != null) {
            status.setMaxConcurrent(maxConcurrent);
        }
        int current = status.getCurrentCount() == null ? 0 : status.getCurrentCount();
        int max = status.getMaxConcurrent() == null ? DEFAULT_MAX_CONCURRENT : status.getMaxConcurrent();
        status.setOnlineStatus(current >= max ? "BUSY" : "ONLINE");
        status.setLastHeartbeat(LocalDateTime.now());
        agentStatusMapper.updateById(status);
        return toVO(status);
    }

    /**
     * 坐席下线：存在进行中的人工会话时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentStatusVO offline(Long agentId) {
        AgentStatus status = getOrCreate(agentId);
        Long humanSessions = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getAgentId, agentId)
                .eq(ChatSession::getStatus, "HUMAN"));
        if (humanSessions != null && humanSessions > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "仍有进行中的会话，请先结束会话再下线");
        }
        status.setOnlineStatus("OFFLINE");
        status.setLastHeartbeat(LocalDateTime.now());
        agentStatusMapper.updateById(status);
        return toVO(status);
    }

    /**
     * 在线/忙碌切换：必须处于在线状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentStatusVO updateOnlineStatus(Long agentId, String onlineStatus) {
        AgentStatus status = getOrCreate(agentId);
        if ("OFFLINE".equals(status.getOnlineStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "当前处于离线状态，请先上线");
        }
        status.setOnlineStatus(onlineStatus);
        status.setLastHeartbeat(LocalDateTime.now());
        agentStatusMapper.updateById(status);
        return toVO(status);
    }

    /**
     * 查询当前坐席状态，不存在时创建初始离线记录。
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
        status.setOnlineStatus("OFFLINE");
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
            vo.setAgentName(user.getNickname());
        }
        return vo;
    }
}
