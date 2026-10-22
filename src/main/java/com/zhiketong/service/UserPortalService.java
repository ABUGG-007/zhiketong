package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.UserVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * USER / AGENT 只读「我的数据」查询服务（M1.5）。
 *
 * <p>按当前登录人与角色做数据隔离：USER 只能看自己的会话与工单，
 * AGENT 只能看分配给自己的会话与工单，不返回密码等敏感字段。</p>
 */
@Service
@RequiredArgsConstructor
public class UserPortalService {

    /** 坐席我的会话可见状态 */
    private static final List<String> AGENT_VISIBLE_SESSION_STATUS =
            Arrays.asList(SessionStatus.HUMAN.getCode(), SessionStatus.CLOSED.getCode());

    private final ChatSessionMapper chatSessionMapper;
    private final WorkOrderMapper workOrderMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 我的会话分页：USER 按 user_id 过滤；AGENT 按 agent_id 过滤且仅 HUMAN/CLOSED。
     *
     * @param userId   当前登录用户 ID
     * @param roleCode 当前登录用户角色编码
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数，1~100
     * @return 会话分页数据
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> pageMySessions(Long userId, String roleCode,
                                                          int pageNum, int pageSize) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (Constants.ROLE_AGENT.equals(roleCode)) {
            wrapper.eq(ChatSession::getAgentId, userId)
                    .in(ChatSession::getStatus, AGENT_VISIBLE_SESSION_STATUS);
        } else {
            wrapper.eq(ChatSession::getUserId, userId);
        }
        wrapper.orderByDesc(ChatSession::getId);

        Page<ChatSession> page = chatSessionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = new ArrayList<>(page.getRecords().size());
        for (ChatSession session : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", session.getId());
            item.put("sessionNo", session.getSessionNo());
            item.put("status", session.getStatus());
            item.put("rating", session.getRating());
            item.put("messageCount", session.getMessageCount());
            item.put("createTime", session.getCreateTime());
            item.put("acceptTime", session.getAcceptTime());
            item.put("endTime", session.getEndTime());
            list.add(item);
        }
        return PageResult.of(page, list);
    }

    /**
     * 我的工单分页：USER 按 creator_id 过滤；AGENT 按 assignee_id 过滤。
     *
     * @param userId   当前登录用户 ID
     * @param roleCode 当前登录用户角色编码
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数，1~100
     * @return 工单分页数据
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> pageMyOrders(Long userId, String roleCode,
                                                        int pageNum, int pageSize) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (Constants.ROLE_AGENT.equals(roleCode)) {
            wrapper.eq(WorkOrder::getAssigneeId, userId);
        } else {
            wrapper.eq(WorkOrder::getCreatorId, userId);
        }
        wrapper.orderByDesc(WorkOrder::getId);

        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = new ArrayList<>(page.getRecords().size());
        for (WorkOrder order : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", order.getId());
            item.put("orderNo", order.getOrderNo());
            item.put("title", order.getTitle());
            item.put("status", order.getStatus());
            item.put("priority", order.getPriority());
            item.put("source", order.getSource());
            item.put("createTime", order.getCreateTime());
            item.put("slaDeadline", order.getSlaDeadline());
            item.put("overdueFlag", order.getOverdueFlag());
            list.add(item);
        }
        return PageResult.of(page, list);
    }

    /**
     * 当前用户资料（复用 UserVO，不返回 password）。
     *
     * @param userId 当前登录用户 ID
     * @return 用户视图对象
     */
    @Transactional(readOnly = true)
    public UserVO profile(Long userId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        return UserVO.of(user);
    }
}
