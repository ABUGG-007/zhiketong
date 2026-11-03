package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.config.properties.SlaProperties;
import com.zhiketong.dto.sla.OrderSlaPauseVO;
import com.zhiketong.dto.sla.OrderSlaVO;
import com.zhiketong.entity.OrderSlaPause;
import com.zhiketong.entity.OrderSlaPolicy;
import com.zhiketong.entity.OrderSlaRecord;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.entity.WorkOrderLog;
import com.zhiketong.enums.OrderAction;
import com.zhiketong.enums.OrderPriority;
import com.zhiketong.enums.OrderRuleEvent;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SlaStatus;
import com.zhiketong.mapper.OrderSlaPauseMapper;
import com.zhiketong.mapper.OrderSlaRecordMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderLogMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.TenantContext;
import com.zhiketong.websocket.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单多级 SLA 计时服务（S3-B，R-133 ~ R-136 及内部计时编排）。
 *
 * <p>职责：为工单建立计时记录并按策略计算三级时限（首次响应 / 每次回复间隔 / 解决）、
 * 处理停表与恢复（等待客户反馈时暂停计时并顺延截止时刻）、按预警阈值触发预警、超时标记与升级通知。
 * 计时记录保存策略快照，策略改名或停用不影响在跑工单。</p>
 *
 * <p>依赖方向：本服务只依赖 Mapper、{@link SlaPolicyService}、{@link SlaProperties} 与
 * {@link ChatPushService}，不依赖 {@link WorkOrderService}，避免与其形成循环依赖；
 * {@link WorkOrderService} 在状态流转时反向调用本服务。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSlaService {

    /** 计时状态：计时中 */
    public static final String REC_RUNNING = "RUNNING";
    /** 计时状态：停表中 */
    public static final String REC_PAUSED = "PAUSED";
    /** 计时状态：已完结 */
    public static final String REC_FINISHED = "FINISHED";

    /** 停表类型：等待客户反馈 */
    public static final String PAUSE_TYPE_WAITING = "WAITING_CUSTOMER";
    /** 停表类型：其他原因 */
    public static final String PAUSE_TYPE_OTHER = "OTHER";

    /** 首响超时日志标识（与 WorkOrderService 保持一致，供去重） */
    public static final String OVERDUE_FIRST_RESPONSE = "FIRST_RESPONSE_OVERDUE";
    /** 回复间隔超时日志标识 */
    public static final String OVERDUE_REPLY = "REPLY_OVERDUE";
    /** 解决超时日志标识（与 WorkOrderService 保持一致，供去重） */
    public static final String OVERDUE_RESOLVE = "RESOLVE_OVERDUE";
    /** 升级通知日志标识 */
    public static final String ESCALATE_LOG = "RESOLVE_ESCALATED";

    /** 单轮扫描记录上限 */
    private static final int MAX_SCAN_BATCH = 500;

    /** 系统操作人名称 */
    private static final String SYSTEM_OPERATOR = "系统";

    /** 通知业务类型 */
    private static final String BIZ_TYPE = "WORKORDER";

    private final OrderSlaRecordMapper recordMapper;
    private final OrderSlaPauseMapper pauseMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final SysUserMapper sysUserMapper;
    private final SlaPolicyService slaPolicyService;
    private final SlaProperties slaProperties;
    private final ChatPushService chatPushService;
    /** 规则引擎懒引用：与本服务存在双向调用（SLA 事件触发规则，规则动作回调 SLA），用 ObjectProvider 打破循环 */
    private final ObjectProvider<OrderRuleService> orderRuleServiceProvider;

    // ============================================================
    // 计时记录生命周期
    // ============================================================

    /**
     * 为新建工单初始化计时记录：匹配默认策略（无匹配回退配置文件兜底时限），
     * 计算首响 / 解决截止时刻，并把 sla_id、sla_status、reply_due_time、sla_deadline 同步回工单。
     *
     * @param order     已落库的工单（含 id）
     * @param startTime 计时起点，取工单创建时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void initRecord(WorkOrder order, LocalDateTime startTime) {
        OrderSlaPolicy policy = slaPolicyService.matchDefaultPolicy(order.getPriority());
        int first = policy != null ? nz(policy.getFirstResponseMinutes())
                : slaProperties.getFirstResponseMinutes(order.getPriority());
        int reply = policy != null ? nz(policy.getReplyIntervalMinutes()) : 0;
        int resolve = policy != null ? nz(policy.getResolveMinutes())
                : slaProperties.getResolveMinutes(order.getPriority());
        int warnPercent = policy != null && policy.getWarnPercent() != null ? policy.getWarnPercent() : 80;
        int escalate = policy != null ? nz(policy.getEscalateMinutes()) : 0;

        OrderSlaRecord record = new OrderSlaRecord();
        record.setOrderId(order.getId());
        record.setPolicyId(policy == null ? null : policy.getId());
        record.setPolicyName(policy == null ? null : policy.getPolicyName());
        record.setPriority(order.getPriority());
        record.setFirstResponseMinutes(first);
        record.setReplyIntervalMinutes(reply);
        record.setResolveMinutes(resolve);
        record.setWarnPercent(warnPercent);
        record.setEscalateMinutes(escalate);
        record.setStartTime(startTime);
        record.setFirstResponseDeadline(first > 0 ? startTime.plusMinutes(first) : null);
        record.setResolveDeadline(resolve > 0 ? startTime.plusMinutes(resolve) : null);
        record.setReplyDueTime(null);
        record.setPauseMinutes(0);
        record.setPaused(0);
        record.setFirstResponseWarn(0);
        record.setFirstResponseOverdue(0);
        record.setReplyWarn(0);
        record.setReplyOverdue(0);
        record.setResolveWarn(0);
        record.setResolveOverdue(0);
        record.setEscalated(0);
        record.setStatus(REC_RUNNING);
        record.setSlaStatus(SlaStatus.NORMAL.getCode());
        recordMapper.insert(record);

        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getSlaId, record.getId())
                .set(WorkOrder::getSlaStatus, SlaStatus.NORMAL.getCode())
                .set(WorkOrder::getPauseMinutes, 0)
                .set(WorkOrder::getReplyDueTime, null)
                .set(WorkOrder::getSlaDeadline, record.getResolveDeadline());
        workOrderMapper.update(null, update);

        order.setSlaId(record.getId());
        order.setSlaStatus(SlaStatus.NORMAL.getCode());
        order.setPauseMinutes(0);
        order.setReplyDueTime(null);
        order.setSlaDeadline(record.getResolveDeadline());
        log.info("初始化 SLA 计时：orderId={}, recordId={}, policy={}, first={}m, resolve={}m",
                order.getId(), record.getId(), record.getPolicyName(), first, resolve);
    }

    /**
     * 标记首次响应（受理工单时调用）：记录首响时间，若启用回复间隔则开启回复计时。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFirstResponse(WorkOrder order, LocalDateTime responseTime) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null || record.getFirstResponseTime() != null || REC_FINISHED.equals(record.getStatus())) {
            return;
        }
        record.setFirstResponseTime(responseTime);
        if (nz(record.getReplyIntervalMinutes()) > 0) {
            record.setReplyDueTime(responseTime.plusMinutes(record.getReplyIntervalMinutes()));
            record.setLastReplyTime(responseTime);
        }
        record.setSlaStatus(computeStatus(record));
        recordMapper.updateById(record);
        syncOrder(order.getId(), record, false);
    }

    /**
     * 坐席添加处理进展时重置回复间隔计时，并清除回复级预警/超时标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetReplyDue(WorkOrder order, LocalDateTime replyTime) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null || REC_FINISHED.equals(record.getStatus())) {
            return;
        }
        record.setLastReplyTime(replyTime);
        if (nz(record.getReplyIntervalMinutes()) > 0) {
            record.setReplyDueTime(replyTime.plusMinutes(record.getReplyIntervalMinutes()));
            record.setReplyWarn(0);
            record.setReplyOverdue(0);
        }
        record.setSlaStatus(computeStatus(record));
        recordMapper.updateById(record);
        syncOrder(order.getId(), record, false);
    }

    /**
     * 优先级变更后重算时限：更新策略快照，按「起点 + 累计停表」重算首响/解决截止，
     * 清零解决/回复/升级标记交由下轮扫描重新评估。已完结记录仅更新优先级快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalcOnPriorityChange(WorkOrder order, String newPriority, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null) {
            // 历史工单（无计时记录）：沿用配置文件口径重算解决截止
            LocalDateTime base = order.getCreateTime() == null ? now : order.getCreateTime();
            LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
            update.eq(WorkOrder::getId, order.getId())
                    .set(WorkOrder::getSlaDeadline, base.plusMinutes(slaProperties.getResolveMinutes(newPriority)));
            workOrderMapper.update(null, update);
            return;
        }
        if (REC_FINISHED.equals(record.getStatus())) {
            record.setPriority(newPriority);
            recordMapper.updateById(record);
            return;
        }
        OrderSlaPolicy policy = slaPolicyService.matchDefaultPolicy(newPriority);
        int first = policy != null ? nz(policy.getFirstResponseMinutes())
                : slaProperties.getFirstResponseMinutes(newPriority);
        int reply = policy != null ? nz(policy.getReplyIntervalMinutes()) : 0;
        int resolve = policy != null ? nz(policy.getResolveMinutes())
                : slaProperties.getResolveMinutes(newPriority);
        record.setPolicyId(policy == null ? null : policy.getId());
        record.setPolicyName(policy == null ? null : policy.getPolicyName());
        record.setPriority(newPriority);
        record.setFirstResponseMinutes(first);
        record.setReplyIntervalMinutes(reply);
        record.setResolveMinutes(resolve);
        record.setWarnPercent(policy != null && policy.getWarnPercent() != null ? policy.getWarnPercent() : 80);
        record.setEscalateMinutes(policy != null ? nz(policy.getEscalateMinutes()) : 0);

        LocalDateTime origin = record.getStartTime() == null ? now : record.getStartTime();
        LocalDateTime base = origin.plusMinutes(nz(record.getPauseMinutes()));
        if (record.getFirstResponseTime() == null) {
            record.setFirstResponseDeadline(first > 0 ? base.plusMinutes(first) : null);
            record.setFirstResponseWarn(0);
            record.setFirstResponseOverdue(0);
        }
        record.setResolveDeadline(resolve > 0 ? base.plusMinutes(resolve) : null);
        record.setReplyDueTime(null);
        record.setReplyWarn(0);
        record.setReplyOverdue(0);
        record.setResolveWarn(0);
        record.setResolveOverdue(0);
        record.setEscalated(0);
        record.setSlaStatus(computeStatus(record));
        recordMapper.updateById(record);

        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getSlaDeadline, record.getResolveDeadline())
                .set(WorkOrder::getReplyDueTime, null)
                .set(WorkOrder::getSlaStatus, record.getSlaStatus())
                .set(WorkOrder::getOverdueFlag, 0);
        workOrderMapper.update(null, update);
    }

    /**
     * 工单重开：启动新的解决周期，清零停表累计与解决/回复/升级计时，保留首响历史。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetOnReopen(WorkOrder order, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null) {
            return;
        }
        closeOpenPause(record.getId(), now);
        int resolve = nz(record.getResolveMinutes());
        record.setStatus(REC_RUNNING);
        record.setStartTime(now);
        record.setResolveDeadline(resolve > 0 ? now.plusMinutes(resolve) : null);
        record.setReplyDueTime(null);
        record.setLastReplyTime(null);
        record.setPauseMinutes(0);
        record.setPaused(0);
        record.setPauseStart(null);
        record.setReplyWarn(0);
        record.setReplyOverdue(0);
        record.setResolveWarn(0);
        record.setResolveOverdue(0);
        record.setEscalated(0);
        record.setSlaStatus(computeStatus(record));
        recordMapper.updateById(record);

        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getSlaDeadline, record.getResolveDeadline())
                .set(WorkOrder::getReplyDueTime, null)
                .set(WorkOrder::getPauseMinutes, 0)
                .set(WorkOrder::getSlaStatus, record.getSlaStatus())
                .set(WorkOrder::getOverdueFlag, 0);
        workOrderMapper.update(null, update);
    }

    /**
     * 工单解决或关闭时完结计时：停止预警与超时判定，关闭未结束的停表流水。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finish(WorkOrder order, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null || REC_FINISHED.equals(record.getStatus())) {
            return;
        }
        if (isOne(record.getPaused())) {
            closeOpenPause(record.getId(), now);
            record.setPaused(0);
            record.setPauseStart(null);
        }
        record.setStatus(REC_FINISHED);
        record.setSlaStatus(SlaStatus.FINISHED.getCode());
        recordMapper.updateById(record);
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId()).set(WorkOrder::getSlaStatus, SlaStatus.FINISHED.getCode());
        workOrderMapper.update(null, update);
    }

    /**
     * 开始停表（PROCESSING → WAITING_CUSTOMER 时调用）：记录置为停表中并写入停表流水。
     */
    @Transactional(rollbackFor = Exception.class)
    public void beginPause(WorkOrder order, String pauseType, String reason,
                           Long operatorId, String operatorName, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null) {
            return;
        }
        if (REC_FINISHED.equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已完结，无法停表");
        }
        if (isOne(record.getPaused())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已处于停表中");
        }
        record.setStatus(REC_PAUSED);
        record.setPaused(1);
        record.setPauseStart(now);
        record.setSlaStatus(SlaStatus.PAUSED.getCode());
        recordMapper.updateById(record);

        OrderSlaPause pause = new OrderSlaPause();
        pause.setOrderId(order.getId());
        pause.setRecordId(record.getId());
        pause.setPauseType(PAUSE_TYPE_OTHER.equals(pauseType) ? PAUSE_TYPE_OTHER : PAUSE_TYPE_WAITING);
        pause.setReason(reason);
        pause.setPauseTime(now);
        pause.setOperatorId(operatorId);
        pause.setOperatorName(operatorName);
        pauseMapper.insert(pause);

        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId()).set(WorkOrder::getSlaStatus, SlaStatus.PAUSED.getCode());
        workOrderMapper.update(null, update);
    }

    /**
     * 结束停表（WAITING_CUSTOMER → PROCESSING 时调用）：累计停表时长并顺延各截止时刻。
     */
    @Transactional(rollbackFor = Exception.class)
    public void endPause(WorkOrder order, Long operatorId, String operatorName, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null) {
            return;
        }
        if (!isOne(record.getPaused())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单未处于停表中");
        }
        long duration = minutesBetween(record.getPauseStart(), now);
        closeOpenPause(record.getId(), now);
        record.setPauseMinutes(nz(record.getPauseMinutes()) + (int) duration);
        if (record.getFirstResponseTime() == null && record.getFirstResponseDeadline() != null) {
            record.setFirstResponseDeadline(record.getFirstResponseDeadline().plusMinutes(duration));
        }
        if (record.getReplyDueTime() != null) {
            record.setReplyDueTime(record.getReplyDueTime().plusMinutes(duration));
        }
        if (record.getResolveDeadline() != null) {
            record.setResolveDeadline(record.getResolveDeadline().plusMinutes(duration));
        }
        record.setPaused(0);
        record.setPauseStart(null);
        record.setStatus(REC_RUNNING);
        record.setSlaStatus(computeStatus(record));
        recordMapper.updateById(record);
        syncOrder(order.getId(), record, true);
        log.info("工单恢复计时：orderId={}, 本次停表 {} 分钟, 累计 {} 分钟",
                order.getId(), duration, record.getPauseMinutes());
    }

    // ============================================================
    // 扫描：预警 → 超时 → 升级
    // ============================================================

    /**
     * 全量扫描计时中的记录（由定时任务每分钟调用）。
     *
     * <p>外层以系统模式跨租户扫出待处理记录，内层逐条切到记录所属租户执行，
     * 保证预警、超时与升级写入的 tenant_id 正确。</p>
     */
    public void scan(LocalDateTime now) {
        TenantContext.runSystem(() -> doScan(now));
    }

    /**
     * 系统模式下的实际扫描逻辑，单条记录的异常不影响其余记录。
     */
    private void doScan(LocalDateTime now) {
        List<OrderSlaRecord> records = recordMapper.selectList(new LambdaQueryWrapper<OrderSlaRecord>()
                .eq(OrderSlaRecord::getStatus, REC_RUNNING)
                .orderByAsc(OrderSlaRecord::getResolveDeadline)
                .last("LIMIT " + MAX_SCAN_BATCH));
        int overdue = 0;
        int escalated = 0;
        for (OrderSlaRecord record : records) {
            try {
                int[] result = TenantContext.callAs(record.getTenantId(), () -> {
                    WorkOrder order = workOrderMapper.selectById(record.getOrderId());
                    if (order == null) {
                        return new int[]{0, 0};
                    }
                    return scanRecord(record, order, now);
                });
                overdue += result[0];
                escalated += result[1];
            } catch (Exception e) {
                log.error("SLA 记录扫描失败：recordId={}, orderId={}", record.getId(), record.getOrderId(), e);
            }
        }
        if (!records.isEmpty()) {
            log.info("SLA 引擎扫描完成：计时中 {} 条，新增超时 {} 处，新增升级 {} 处", records.size(), overdue, escalated);
        }
    }

    /**
     * 扫描单个工单的计时记录（R-136 手动扫描 / SLA 演练），返回扫描后的 SLA 详情。
     *
     * @param order 工单
     * @param now   扫描时刻，可传入未来时间用于演练
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderSlaVO scanOrder(WorkOrder order, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该工单未启用 SLA 计时");
        }
        scanRecord(record, order, now);
        WorkOrder fresh = workOrderMapper.selectById(order.getId());
        return detail(fresh == null ? order : fresh, now);
    }

    /**
     * 评估单条记录的三级时限，触发预警/超时/升级。
     *
     * @return int[2]{新增超时数, 新增升级数}
     */
    private int[] scanRecord(OrderSlaRecord record, WorkOrder order, LocalDateTime now) {
        int overdueCount = 0;
        int escalateCount = 0;
        String status = order.getStatus();
        // 防御：工单已进入终态但记录未完结，直接完结
        if (OrderStatus.RESOLVED.getCode().equals(status) || OrderStatus.CLOSED.getCode().equals(status)) {
            finish(order, now);
            return new int[]{0, 0};
        }
        boolean changed = false;

        // 一级：首次响应
        if (nz(record.getFirstResponseMinutes()) > 0 && record.getFirstResponseTime() == null) {
            int state = levelState(now, record.getFirstResponseDeadline(),
                    record.getFirstResponseMinutes(), record.getWarnPercent());
            if (state == 2 && !isOne(record.getFirstResponseOverdue())) {
                record.setFirstResponseOverdue(1);
                markOrderOverdue(order, OVERDUE_FIRST_RESPONSE, "首次响应", now);
                overdueCount++;
                changed = true;
            } else if (state == 1 && !isOne(record.getFirstResponseWarn()) && !isOne(record.getFirstResponseOverdue())) {
                record.setFirstResponseWarn(1);
                notifyWarn(order, "首次响应", record.getFirstResponseDeadline(), now);
                changed = true;
            }
        }

        // 二级：回复间隔
        if (nz(record.getReplyIntervalMinutes()) > 0 && record.getReplyDueTime() != null
                && record.getFirstResponseTime() != null && order.getResolveTime() == null) {
            int state = levelState(now, record.getReplyDueTime(),
                    record.getReplyIntervalMinutes(), record.getWarnPercent());
            if (state == 2 && !isOne(record.getReplyOverdue())) {
                record.setReplyOverdue(1);
                markOrderOverdue(order, OVERDUE_REPLY, "回复间隔", now);
                overdueCount++;
                changed = true;
            } else if (state == 1 && !isOne(record.getReplyWarn()) && !isOne(record.getReplyOverdue())) {
                record.setReplyWarn(1);
                notifyWarn(order, "回复", record.getReplyDueTime(), now);
                changed = true;
            }
        }

        // 三级：解决 + 超时升级
        if (nz(record.getResolveMinutes()) > 0 && record.getResolveDeadline() != null && order.getResolveTime() == null) {
            int state = levelState(now, record.getResolveDeadline(),
                    record.getResolveMinutes(), record.getWarnPercent());
            if (state == 2 && !isOne(record.getResolveOverdue())) {
                record.setResolveOverdue(1);
                markOrderOverdue(order, OVERDUE_RESOLVE, "问题解决", now);
                overdueCount++;
                changed = true;
            } else if (state == 1 && !isOne(record.getResolveWarn()) && !isOne(record.getResolveOverdue())) {
                record.setResolveWarn(1);
                notifyWarn(order, "解决", record.getResolveDeadline(), now);
                changed = true;
            }
            if (isOne(record.getResolveOverdue()) && !isOne(record.getEscalated())) {
                LocalDateTime escalateAt = record.getResolveDeadline().plusMinutes(nz(record.getEscalateMinutes()));
                if (!now.isBefore(escalateAt)) {
                    escalate(record, order, now);
                    escalateCount++;
                    changed = true;
                }
            }
        }

        String newStatus = computeStatus(record);
        if (!newStatus.equals(record.getSlaStatus())) {
            record.setSlaStatus(newStatus);
            changed = true;
        }
        if (changed) {
            recordMapper.updateById(record);
            LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
            update.eq(WorkOrder::getId, order.getId()).set(WorkOrder::getSlaStatus, record.getSlaStatus());
            workOrderMapper.update(null, update);
        }
        return new int[]{overdueCount, escalateCount};
    }

    /**
     * 计算某一级时限的当前状态。
     *
     * @return 2 已超时 / 1 处于预警区 / 0 正常
     */
    private int levelState(LocalDateTime now, LocalDateTime deadline, int limitMinutes, Integer warnPercent) {
        if (deadline == null) {
            return 0;
        }
        if (!now.isBefore(deadline)) {
            return 2;
        }
        int percent = warnPercent == null ? 80 : warnPercent;
        long remaining = Duration.between(now, deadline).toMinutes();
        long threshold = (long) limitMinutes * (100 - percent) / 100;
        return remaining <= threshold ? 1 : 0;
    }

    // ============================================================
    // 详情（R-133）
    // ============================================================

    /**
     * 构建工单 SLA 详情，无计时记录返回 null。
     */
    public OrderSlaVO detail(WorkOrder order, LocalDateTime now) {
        OrderSlaRecord record = loadRecord(order.getId());
        if (record == null) {
            return null;
        }
        OrderSlaVO vo = new OrderSlaVO();
        vo.setOrderId(order.getId());
        vo.setSlaId(record.getId());
        vo.setPolicyName(record.getPolicyName());
        vo.setPriority(record.getPriority());
        vo.setFirstResponseMinutes(record.getFirstResponseMinutes());
        vo.setReplyIntervalMinutes(record.getReplyIntervalMinutes());
        vo.setResolveMinutes(record.getResolveMinutes());
        vo.setWarnPercent(record.getWarnPercent());
        vo.setEscalateMinutes(record.getEscalateMinutes());
        vo.setStartTime(record.getStartTime());
        vo.setFirstResponseDeadline(record.getFirstResponseDeadline());
        vo.setFirstResponseTime(record.getFirstResponseTime());
        vo.setLastReplyTime(record.getLastReplyTime());
        vo.setReplyDueTime(record.getReplyDueTime());
        vo.setResolveDeadline(record.getResolveDeadline());
        vo.setPauseMinutes(record.getPauseMinutes());
        vo.setPaused(record.getPaused());
        vo.setPauseStart(record.getPauseStart());
        vo.setStatus(record.getStatus());
        vo.setSlaStatus(record.getSlaStatus());
        vo.setSlaStatusLabel(SlaStatus.labelOf(record.getSlaStatus()));
        vo.setOverdueFlag(order.getOverdueFlag() == null ? 0 : order.getOverdueFlag());
        vo.setServerTime(now);

        List<OrderSlaPause> pauses = pauseMapper.selectList(new LambdaQueryWrapper<OrderSlaPause>()
                .eq(OrderSlaPause::getOrderId, order.getId())
                .orderByDesc(OrderSlaPause::getPauseTime).orderByDesc(OrderSlaPause::getId));
        List<OrderSlaPauseVO> pauseVOs = new ArrayList<>(pauses.size());
        for (OrderSlaPause pause : pauses) {
            OrderSlaPauseVO pvo = new OrderSlaPauseVO();
            pvo.setId(pause.getId());
            pvo.setPauseType(pause.getPauseType());
            pvo.setPauseTypeLabel(PAUSE_TYPE_OTHER.equals(pause.getPauseType()) ? "其他原因" : "等待客户反馈");
            pvo.setReason(pause.getReason());
            pvo.setPauseTime(pause.getPauseTime());
            pvo.setResumeTime(pause.getResumeTime());
            pvo.setDurationMinutes(pause.getDurationMinutes());
            pvo.setOperatorId(pause.getOperatorId());
            pvo.setOperatorName(pause.getOperatorName());
            pauseVOs.add(pvo);
        }
        vo.setPauses(pauseVOs);
        return vo;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private OrderSlaRecord loadRecord(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return recordMapper.selectOne(new LambdaQueryWrapper<OrderSlaRecord>()
                .eq(OrderSlaRecord::getOrderId, orderId).last("LIMIT 1"));
    }

    private String computeStatus(OrderSlaRecord record) {
        if (REC_PAUSED.equals(record.getStatus())) {
            return SlaStatus.PAUSED.getCode();
        }
        if (REC_FINISHED.equals(record.getStatus())) {
            return SlaStatus.FINISHED.getCode();
        }
        if (isOne(record.getFirstResponseOverdue()) || isOne(record.getReplyOverdue())
                || isOne(record.getResolveOverdue())) {
            return SlaStatus.OVERDUE.getCode();
        }
        if (isOne(record.getFirstResponseWarn()) || isOne(record.getReplyWarn())
                || isOne(record.getResolveWarn())) {
            return SlaStatus.WARN.getCode();
        }
        return SlaStatus.NORMAL.getCode();
    }

    /**
     * 把计时记录的关键字段同步回工单。
     *
     * @param syncDeadline 是否同步 sla_deadline（停表恢复/首响等需要顺延截止时间时为 true）
     */
    private void syncOrder(Long orderId, OrderSlaRecord record, boolean syncDeadline) {
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, orderId)
                .set(WorkOrder::getSlaStatus, record.getSlaStatus())
                .set(WorkOrder::getPauseMinutes, record.getPauseMinutes())
                .set(WorkOrder::getReplyDueTime, record.getReplyDueTime());
        if (syncDeadline) {
            update.set(WorkOrder::getSlaDeadline, record.getResolveDeadline());
        }
        workOrderMapper.update(null, update);
    }

    /**
     * 标记工单超时：条件更新 overdue_flag 保证幂等，同类型只写一条 OVERDUE 日志，并通知坐席与（P0/P1）管理员。
     */
    private void markOrderOverdue(WorkOrder order, String overdueType, String label, LocalDateTime now) {
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .eq(WorkOrder::getOverdueFlag, 0)
                .set(WorkOrder::getOverdueFlag, 1);
        workOrderMapper.update(null, update);

        Long exists = workOrderLogMapper.selectCount(new LambdaQueryWrapper<WorkOrderLog>()
                .eq(WorkOrderLog::getOrderId, order.getId())
                .eq(WorkOrderLog::getRemark, overdueType));
        if (exists == null || exists == 0) {
            writeLog(order.getId(), OrderAction.OVERDUE, order.getStatus(), order.getStatus(), overdueType);
        }
        String content = "工单 " + order.getOrderNo() + " " + label + "已超时，请尽快处理";
        notify(order.getAssigneeId(), "WARN", "工单SLA超时", content, order.getId(), "OVERDUE");
        if (isHighPriority(order.getPriority())) {
            notifyAdmins(order, content, "OVERDUE");
        }
        log.warn("工单SLA超时：orderId={}, orderNo={}, type={}, priority={}",
                order.getId(), order.getOrderNo(), overdueType, order.getPriority());
        fireRule(OrderRuleEvent.SLA_OVERDUE, order);
    }

    /**
     * 超时后升级：通知全体管理员，同一工单只升级一次。
     */
    private void escalate(OrderSlaRecord record, WorkOrder order, LocalDateTime now) {
        record.setEscalated(1);
        Long exists = workOrderLogMapper.selectCount(new LambdaQueryWrapper<WorkOrderLog>()
                .eq(WorkOrderLog::getOrderId, order.getId())
                .eq(WorkOrderLog::getRemark, ESCALATE_LOG));
        if (exists == null || exists == 0) {
            writeLog(order.getId(), OrderAction.ESCALATE, order.getStatus(), order.getStatus(), ESCALATE_LOG);
        }
        String content = "工单 " + order.getOrderNo() + " 超时未解决，已升级至管理员，请立即介入";
        notifyAdmins(order, content, "ESCALATE");
        log.warn("工单SLA升级：orderId={}, orderNo={}, priority={}", order.getId(), order.getOrderNo(), order.getPriority());
        fireRule(OrderRuleEvent.SLA_ESCALATE, order);
    }

    private void notifyWarn(WorkOrder order, String label, LocalDateTime deadline, LocalDateTime now) {
        long remaining = deadline == null ? 0 : Math.max(0, Duration.between(now, deadline).toMinutes());
        String content = "工单 " + order.getOrderNo() + " " + label + "即将超时，剩余约 " + remaining + " 分钟，请尽快处理";
        notify(order.getAssigneeId(), "WARN", "工单SLA预警", content, order.getId(), "WARN");
        if (isHighPriority(order.getPriority())) {
            notifyAdmins(order, content, "WARN");
        }
        fireRule(OrderRuleEvent.SLA_WARN, order);
    }

    private void notifyAdmins(WorkOrder order, String content, String action) {
        List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleCode, Constants.ROLE_ADMIN)
                .eq(SysUser::getStatus, Constants.STATUS_ENABLED));
        for (SysUser admin : admins) {
            if (!admin.getId().equals(order.getAssigneeId())) {
                notify(admin.getId(), "WARN", "工单SLA提醒", content, order.getId(), action);
            }
        }
    }

    private void notify(Long userId, String level, String title, String content, Long orderId, String action) {
        if (userId == null) {
            return;
        }
        chatPushService.pushSystemNotice(userId, level, title, content, BIZ_TYPE, orderId, action);
    }

    /**
     * 触发工单规则引擎的 SLA 事件：懒引用解析失败或规则执行异常均不影响 SLA 扫描主流程。
     */
    private void fireRule(OrderRuleEvent event, WorkOrder order) {
        try {
            OrderRuleService ruleService = orderRuleServiceProvider.getIfAvailable();
            if (ruleService != null) {
                ruleService.fire(event.getCode(), order);
            }
        } catch (Exception e) {
            log.error("SLA 事件触发规则失败：event={}, orderId={}", event.getCode(), order.getId(), e);
        }
    }

    private void writeLog(Long orderId, OrderAction action, String fromStatus, String toStatus, String remark) {
        WorkOrderLog item = new WorkOrderLog();
        item.setOrderId(orderId);
        item.setAction(action.getCode());
        item.setFromStatus(fromStatus);
        item.setToStatus(toStatus);
        item.setOperatorId(0L);
        item.setOperatorName(SYSTEM_OPERATOR);
        item.setRemark(remark);
        workOrderLogMapper.insert(item);
    }

    private void closeOpenPause(Long recordId, LocalDateTime now) {
        OrderSlaPause open = pauseMapper.selectOne(new LambdaQueryWrapper<OrderSlaPause>()
                .eq(OrderSlaPause::getRecordId, recordId)
                .isNull(OrderSlaPause::getResumeTime)
                .orderByDesc(OrderSlaPause::getId).last("LIMIT 1"));
        if (open == null) {
            return;
        }
        open.setResumeTime(now);
        open.setDurationMinutes((int) minutesBetween(open.getPauseTime(), now));
        pauseMapper.updateById(open);
    }

    private boolean isHighPriority(String priority) {
        return OrderPriority.P0.getCode().equals(priority) || OrderPriority.P1.getCode().equals(priority);
    }

    private long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0L;
        }
        long minutes = Duration.between(from, to).toMinutes();
        return Math.max(0L, minutes);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean isOne(Integer value) {
        return value != null && value == 1;
    }
}
