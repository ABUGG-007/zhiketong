package com.zhiketong.service;

import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 坐席侧会话数据范围校验：ADMIN 不限，AGENT 仅本人接待的会话。
 *
 * <p>坐席辅助、客户信息卡、内部备注等接口共用同一套口径，避免各服务各写一份。</p>
 *
 * @author 智客通开发组
 */
@Component
@RequiredArgsConstructor
public class AgentSessionGuard {

    private final ChatSessionMapper chatSessionMapper;

    /**
     * 校验登录人可访问该会话。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 会话实体
     */
    public ChatSession requireOwned(Long sessionId, LoginUser loginUser) {
        ChatSession session = sessionId == null ? null : chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String role = loginUser.getRoleCode();
        if (Constants.ROLE_ADMIN.equals(role)) {
            return session;
        }
        if (Constants.ROLE_AGENT.equals(role) && loginUser.getUserId().equals(session.getAgentId())) {
            return session;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅可查看本人接待会话的坐席辅助信息");
    }
}
