package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.Constants;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.entity.WorkOrderLog;
import com.zhiketong.enums.AgentOnlineStatus;
import com.zhiketong.enums.OrderAction;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderLogMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.websocket.ChatPushService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单自动分配服务（WO-04）：为工单按「技能组 + 负载均衡」择优挑选在岗坐席。
 *
 * <p>与会话分配（{@link AgentDispatcherService}）区分：会话看 {@code agent_status.current_count}（并发接待数），
 * 工单看「该坐席未关闭工单数」，两套负载语义互不复用。候选范围优先取指定技能组成员，
 * 组内无在岗坐席时降级为全体在岗，保证工单不被单一技能组堵死。择优口径为
 * 「未关闭工单数升序 → 技能组能力值 level 降序」取一人。</p>
 *
 * <p>本服务只依赖 Mapper、{@link SkillGroupService} 与 {@link ChatPushService}，不依赖
 * {@link WorkOrderService}，供规则引擎在同一事务内直接调用完成指派。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAssignService {

    /** 非终态工单状态，用于统计坐席在手工单负载 */
    private static final List<String> OPEN_STATUSES = Arrays.asList(
            OrderStatus.PENDING.getCode(), OrderStatus.ASSIGNED.getCode(),
            OrderStatus.PROCESSING.getCode(), OrderStatus.WAITING_CUSTOMER.getCode(),
            OrderStatus.RESOLVED.getCode(), OrderStatus.REOPENED.getCode());

    /** 坐席心跳有效时长（秒），超时视为离线不参与分配 */
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 120;

    /** 系统操作人名称 */
    private static final String SYSTEM_OPERATOR = "系统";

    /** 通知业务类型 */
    private static final String BIZ_TYPE = "WORKORDER";

    private final AgentStatusMapper agentStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final SkillGroupService skillGroupService;
    private final ChatPushService chatPushService;

    /**
     * 自动分配结果。
     */
    @Getter
    public static class AssignResult {
        /** 是否成功指派 */
        private final boolean assigned;
        /** 命中的坐席 ID（未指派为 null） */
        private final Long agentId;
        /** 结果说明（成功描述坐席，失败/跳过描述原因） */
        private final String message;

        AssignResult(boolean assigned, Long agentId, String message) {
            this.assigned = assigned;
            this.agentId = agentId;
            this.message = message;
        }
    }

    /**
     * 技能组 + 负载均衡自动分配：组内无在岗坐席时降级全体在岗；无可用坐席时返回未指派。
     *
     * @param order   待分配工单（调用方保证处于可分配状态）
     * @param groupId 指定技能组，null 表示全体在岗
     */
    public AssignResult autoAssign(WorkOrder order, Long groupId) {
        Map<Long, Integer> levels = groupId == null
                ? java.util.Collections.emptyMap() : skillGroupService.levelMapOf(groupId);
        Set<Long> members = groupId == null ? java.util.Collections.emptySet() : levels.keySet();
        Long picked = pickBestAgent(members, levels);
        if (picked == null && groupId != null && !members.isEmpty()) {
            // 组内无可用在岗坐席，降级为全体在岗
            log.debug("工单自动分配组内无可用坐席，降级全体：orderId={}, groupId={}", order.getId(), groupId);
            picked = pickBestAgent(java.util.Collections.emptySet(), java.util.Collections.emptyMap());
        }
        if (picked == null) {
            return new AssignResult(false, null, "无可用在岗坐席，已跳过自动分配");
        }
        SysUser agent = loadActiveAgent(picked);
        if (agent == null) {
            return new AssignResult(false, null, "候选坐席不可用，已跳过自动分配");
        }
        return applyAssign(order, agent, "规则自动分配给坐席" + displayName(agent));
    }

    /**
     * 指派指定坐席（ASSIGN_AGENT 动作）：校验目标为启用坐席后指派。
     *
     * @param order   工单
     * @param agentId 指定坐席 ID
     */
    public AssignResult assignTo(WorkOrder order, Long agentId) {
        SysUser agent = loadActiveAgent(agentId);
        if (agent == null) {
            return new AssignResult(false, null, "指定坐席不存在或不是可用客服坐席");
        }
        return applyAssign(order, agent, "规则指派给坐席" + displayName(agent));
    }

    /**
     * 判断坐席是否为启用状态的客服坐席。
     */
    public boolean isActiveAgent(Long agentId) {
        return loadActiveAgent(agentId) != null;
    }

    /**
     * 执行指派：置受理人并按状态机流转到已分配，写系统流转日志并通知坐席。
     */
    private AssignResult applyAssign(WorkOrder order, SysUser agent, String remark) {
        String status = order.getStatus();
        String toStatus;
        if (OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REOPENED.getCode().equals(status)) {
            toStatus = OrderStatus.ASSIGNED.getCode();
        } else if (OrderStatus.ASSIGNED.getCode().equals(status)) {
            // 已分配视为转派，状态保持已分配
            toStatus = OrderStatus.ASSIGNED.getCode();
        } else {
            return new AssignResult(false, null, "工单当前状态不允许自动分配（" + status + "）");
        }
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getAssigneeId, agent.getId())
                .set(WorkOrder::getStatus, toStatus)
                .set(WorkOrder::getUpdateTime, LocalDateTime.now());
        workOrderMapper.update(null, update);
        writeAssignLog(order.getId(), status, toStatus, remark);
        notifyUser(agent.getId(), "INFO", "新工单待受理",
                "工单 " + order.getOrderNo() + " 已分配给您，请及时受理", order.getId());
        log.info("工单自动分配：orderId={}, agentId={}, {} → {}", order.getId(), agent.getId(), status, toStatus);
        return new AssignResult(true, agent.getId(), remark);
    }

    /**
     * 在候选范围内按「未关闭工单数升序 → 能力值 level 降序」择优一名在岗坐席。
     *
     * @param memberIds 技能组坐席范围，空集合表示不限（全体在岗）
     * @param levels    能力值映射，用于同负载时择优
     * @return 最优坐席 ID，无在岗坐席返回 null
     */
    private Long pickBestAgent(Collection<Long> memberIds, Map<Long, Integer> levels) {
        LocalDateTime heartbeatThreshold = LocalDateTime.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        LambdaQueryWrapper<AgentStatus> wrapper = new LambdaQueryWrapper<AgentStatus>()
                .eq(AgentStatus::getOnlineStatus, AgentOnlineStatus.ONLINE.getCode())
                .ge(AgentStatus::getLastHeartbeat, heartbeatThreshold);
        if (memberIds != null && !memberIds.isEmpty()) {
            wrapper.in(AgentStatus::getAgentId, memberIds);
        }
        List<AgentStatus> candidates = agentStatusMapper.selectList(wrapper);
        List<Long> agentIds = new ArrayList<>(candidates.size());
        for (AgentStatus candidate : candidates) {
            if (candidate.getAgentId() != null) {
                agentIds.add(candidate.getAgentId());
            }
        }
        if (agentIds.isEmpty()) {
            return null;
        }
        // 负载 + 能力值排序：未关闭工单数升序，同负载取能力值高者
        agentIds.sort(Comparator
                .comparingInt(this::countOpenOrders)
                .thenComparing(id -> -levelOf(id, levels)));
        return agentIds.get(0);
    }

    /**
     * 统计某坐席当前未关闭工单数，作为负载均衡度量。
     */
    private int countOpenOrders(Long agentId) {
        if (agentId == null) {
            return 0;
        }
        Long count = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getAssigneeId, agentId)
                .in(WorkOrder::getStatus, OPEN_STATUSES));
        return count == null ? 0 : count.intValue();
    }

    private int levelOf(Long agentId, Map<Long, Integer> levels) {
        Integer level = levels.get(agentId);
        return level == null ? 0 : level;
    }

    private SysUser loadActiveAgent(Long agentId) {
        if (agentId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(agentId);
        if (user == null || !Constants.ROLE_AGENT.equals(user.getRoleCode())
                || !Integer.valueOf(Constants.STATUS_ENABLED).equals(user.getStatus())) {
            return null;
        }
        return user;
    }

    private void writeAssignLog(Long orderId, String fromStatus, String toStatus, String remark) {
        WorkOrderLog item = new WorkOrderLog();
        item.setOrderId(orderId);
        item.setAction(OrderAction.ASSIGN.getCode());
        item.setFromStatus(fromStatus);
        item.setToStatus(toStatus);
        item.setOperatorId(0L);
        item.setOperatorName(SYSTEM_OPERATOR);
        item.setRemark(StringUtils.hasText(remark) ? remark : "系统自动分配");
        workOrderLogMapper.insert(item);
    }

    private void notifyUser(Long userId, String level, String title, String content, Long orderId) {
        if (userId == null) {
            return;
        }
        chatPushService.pushSystemNotice(userId, level, title, content, BIZ_TYPE, orderId, "ASSIGN");
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
