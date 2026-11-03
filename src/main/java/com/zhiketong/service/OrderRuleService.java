package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.orderrule.OrderRuleRequest;
import com.zhiketong.dto.orderrule.OrderRuleRunVO;
import com.zhiketong.dto.orderrule.OrderRuleVO;
import com.zhiketong.entity.OrderRule;
import com.zhiketong.entity.OrderRuleRun;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.entity.WorkOrderLog;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.OrderAction;
import com.zhiketong.enums.OrderPriority;
import com.zhiketong.enums.OrderRuleAction;
import com.zhiketong.enums.OrderRuleEvent;
import com.zhiketong.enums.OrderRuleRunStatus;
import com.zhiketong.enums.OrderRuleType;
import com.zhiketong.enums.OrderSource;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.mapper.OrderRuleMapper;
import com.zhiketong.mapper.OrderRuleRunMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderLogMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.TenantContext;
import com.zhiketong.websocket.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单规则与自动化引擎服务（WO-04 / WO-15 / WO-16，R-137 ~ R-146）。
 *
 * <p>三类规则（自动分配 / 事件触发器 / 时间自动化）统一建模于 {@code order_rule}，命中后统一以
 * {@code actionType} 描述动作。事件由工单状态流转与 SLA 引擎通过 {@link #fire(String, WorkOrder)} 驱动：
 * {@code delayMinutes<=0} 在同一事务内联执行，{@code >0} 落 {@code order_rule_run} PENDING 由扫描任务到期执行，
 * 每条执行都写 {@code order_rule_run} 流水留痕（DONE/FAILED/SKIPPED）。</p>
 *
 * <p>依赖方向：本服务依赖 Mapper、{@link OrderAssignService}、{@link OrderSlaService}、
 * {@link SkillGroupService} 与 {@link ChatPushService}，不依赖 {@link WorkOrderService}；
 * 动作直接经 Mapper 修改工单并写流转日志，不回调解耦的 {@code WorkOrderService}，避免事件再入递归。
 * 与 {@link OrderSlaService} 的相互调用通过其侧 {@code ObjectProvider} 懒解析打破循环。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRuleService {

    /** 非终态工单状态 */
    private static final List<String> OPEN_STATUSES = Arrays.asList(
            OrderStatus.PENDING.getCode(), OrderStatus.ASSIGNED.getCode(),
            OrderStatus.PROCESSING.getCode(), OrderStatus.WAITING_CUSTOMER.getCode(),
            OrderStatus.RESOLVED.getCode(), OrderStatus.REOPENED.getCode());

    /** 系统操作人名称 */
    private static final String SYSTEM_OPERATOR = "系统";

    /** 通知业务类型 */
    private static final String BIZ_TYPE = "WORKORDER";

    /** 命中预览样本上限 */
    private static final int PREVIEW_SAMPLE_LIMIT = 10;

    /** 单轮扫描到期执行上限 */
    private static final int MAX_RUN_BATCH = 200;

    /** 通知角色：管理员 */
    private static final String ROLE_TARGET_ADMIN = "ADMIN";
    /** 通知角色：创建人 */
    private static final String ROLE_TARGET_CREATOR = "CREATOR";
    /** 通知角色：受理人 */
    private static final String ROLE_TARGET_ASSIGNEE = "ASSIGNEE";

    /** 合法状态流转表，供 CHANGE_STATUS 动作校验 */
    private static final Map<String, List<String>> TRANSITIONS = buildTransitions();

    /** 审计对象类型：工单规则 */
    private static final String TARGET_ORDER_RULE = "ORDER_RULE";

    private final OrderRuleMapper ruleMapper;
    private final OrderRuleRunMapper runMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final SysUserMapper sysUserMapper;
    private final OrderAssignService orderAssignService;
    private final OrderSlaService orderSlaService;
    private final SkillGroupService skillGroupService;
    private final ChatPushService chatPushService;
    private final PlatformTransactionManager transactionManager;
    private final AuditLogService auditLogService;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    public void init() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ============================================================
    // 规则配置 CRUD（R-137 ~ R-142）
    // ============================================================

    /**
     * 规则列表（R-137）：可按规则类型与状态过滤，按排序号升序。
     */
    public List<OrderRuleVO> listRules(String ruleType, Integer status) {
        LambdaQueryWrapper<OrderRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(ruleType), OrderRule::getRuleType, ruleType)
                .eq(status != null, OrderRule::getStatus, status)
                .orderByAsc(OrderRule::getSort).orderByAsc(OrderRule::getId);
        List<OrderRule> list = ruleMapper.selectList(wrapper);
        List<OrderRuleVO> result = new ArrayList<>(list.size());
        for (OrderRule rule : list) {
            result.add(toVO(rule));
        }
        return result;
    }

    /**
     * 规则详情（R-138）。
     */
    public OrderRuleVO ruleDetail(Long id) {
        return toVO(requireRule(id));
    }

    /**
     * 新增规则（R-139）：名称同租户唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderRuleVO createRule(OrderRuleRequest request, LoginUser loginUser) {
        validate(request, null);
        String name = request.getRuleName().trim();
        OrderRule rule = new OrderRule();
        applyRequest(rule, request, name);
        rule.setCreateBy(loginUser.getUserId());
        rule.setUpdateBy(loginUser.getUserId());
        ruleMapper.insert(rule);
        log.info("新增工单规则：id={}, name={}, type={}, action={}",
                rule.getId(), name, rule.getRuleType(), rule.getActionType());
        auditLogService.record(AuditModule.ORDER_RULE, AuditAction.CREATE)
                .target(TARGET_ORDER_RULE, rule.getId(), name)
                .detail("新增工单规则「" + name + "」（" + rule.getRuleType() + " -> " + rule.getActionType() + "）")
                .after(rule)
                .save();
        return toVO(rule);
    }

    /**
     * 修改规则（R-140）：名称唯一性排除自身。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderRuleVO updateRule(Long id, OrderRuleRequest request, LoginUser loginUser) {
        OrderRule rule = requireRule(id);
        OrderRuleVO before = toVO(rule);
        validate(request, id);
        String name = request.getRuleName().trim();
        applyRequest(rule, request, name);
        rule.setUpdateBy(loginUser.getUserId());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("修改工单规则：id={}, name={}", id, name);
        auditLogService.record(AuditModule.ORDER_RULE, AuditAction.UPDATE)
                .target(TARGET_ORDER_RULE, id, name)
                .detail("编辑工单规则「" + name + "」")
                .before(before)
                .after(toVO(rule))
                .save();
        return toVO(rule);
    }

    /**
     * 启用/停用规则（R-141）：停用后不再被事件匹配。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderRuleVO updateStatus(Long id, Integer status, LoginUser loginUser) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
        OrderRule rule = requireRule(id);
        rule.setStatus(status);
        rule.setUpdateBy(loginUser.getUserId());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        auditLogService.record(AuditModule.ORDER_RULE, status == 1 ? AuditAction.ENABLE : AuditAction.DISABLE)
                .target(TARGET_ORDER_RULE, id, rule.getRuleName())
                .detail("工单规则「" + rule.getRuleName() + "」" + (status == 1 ? "启用" : "停用"))
                .save();
        return toVO(rule);
    }

    /**
     * 删除规则（R-142）：逻辑删除，历史执行流水仍按快照保留。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id, LoginUser loginUser) {
        OrderRule rule = requireRule(id);
        OrderRuleVO before = toVO(rule);
        ruleMapper.deleteById(rule.getId());
        log.info("删除工单规则：id={}, name={}, operatorId={}", id, rule.getRuleName(), loginUser.getUserId());
        auditLogService.record(AuditModule.ORDER_RULE, AuditAction.DELETE)
                .target(TARGET_ORDER_RULE, id, rule.getRuleName())
                .detail("删除工单规则「" + rule.getRuleName() + "」")
                .before(before)
                .save();
    }

    /**
     * 规则执行流水分页（R-143）。
     */
    public PageResult<OrderRuleRunVO> pageRuns(Long ruleId, int pageNum, int pageSize) {
        requireRule(ruleId);
        Page<OrderRuleRun> page = runMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrderRuleRun>()
                        .eq(OrderRuleRun::getRuleId, ruleId)
                        .orderByDesc(OrderRuleRun::getCreateTime).orderByDesc(OrderRuleRun::getId));
        Map<Long, String> ruleNames = Collections.singletonMap(ruleId, ruleName(ruleId));
        List<OrderRuleRunVO> list = new ArrayList<>(page.getRecords().size());
        for (OrderRuleRun run : page.getRecords()) {
            list.add(toRunVO(run, ruleNames.get(run.getRuleId())));
        }
        return PageResult.of(page, list);
    }

    /**
     * 工单维度的规则执行流水（R-145）：返回该工单的全部规则执行记录，供工单详情抽屉展示。
     */
    public List<OrderRuleRunVO> runsOfOrder(Long orderId) {
        List<OrderRuleRun> runs = runMapper.selectList(new LambdaQueryWrapper<OrderRuleRun>()
                .eq(OrderRuleRun::getOrderId, orderId)
                .orderByDesc(OrderRuleRun::getCreateTime).orderByDesc(OrderRuleRun::getId));
        Map<Long, String> names = new HashMap<>();
        List<OrderRuleRunVO> list = new ArrayList<>(runs.size());
        for (OrderRuleRun run : runs) {
            String name = names.computeIfAbsent(run.getRuleId(), this::ruleName);
            list.add(toRunVO(run, name));
        }
        return list;
    }

    // ============================================================
    // 命中预览与手动试跑（R-144 / R-146）
    // ============================================================

    /**
     * 命中预览（R-146）：按草稿条件统计当前匹配的非终态工单数与样本，不落库。
     */
    public Map<String, Object> preview(OrderRuleRequest request) {
        List<WorkOrder> matched = matchOpenOrders(request.getMatchPriority(), request.getMatchCategory(),
                request.getMatchSource(), PREVIEW_SAMPLE_LIMIT);
        Long total = countOpenOrders(request.getMatchPriority(), request.getMatchCategory(), request.getMatchSource());
        List<Map<String, Object>> samples = new ArrayList<>();
        for (WorkOrder order : matched) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", order.getId());
            item.put("orderNo", order.getOrderNo());
            item.put("title", order.getTitle());
            item.put("priority", order.getPriority());
            item.put("status", order.getStatus());
            samples.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", total);
        result.put("sampleLimit", PREVIEW_SAMPLE_LIMIT);
        result.put("samples", samples);
        return result;
    }

    /**
     * 手动试跑（R-144）：对该规则当前匹配的非终态工单立即执行一次动作并写执行流水，返回处理统计。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runOnce(Long id, LoginUser loginUser) {
        OrderRule rule = requireRule(id);
        List<WorkOrder> matched = matchOpenOrders(rule.getMatchPriority(), rule.getMatchCategory(),
                rule.getMatchSource(), MAX_RUN_BATCH);
        int done = 0;
        int failed = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now();
        for (WorkOrder order : matched) {
            ActionOutcome outcome = executeAction(rule, order, rule.getTriggerEvent());
            writeRun(rule, order, rule.getTriggerEvent(), outcome, now, now);
            if (OrderRuleRunStatus.DONE.getCode().equals(outcome.status)) {
                done++;
            } else if (OrderRuleRunStatus.FAILED.getCode().equals(outcome.status)) {
                failed++;
            } else {
                skipped++;
            }
        }
        log.info("规则手动试跑：ruleId={}, operatorId={}, 命中 {}, 成功 {}, 失败 {}, 跳过 {}",
                id, loginUser.getUserId(), matched.size(), done, failed, skipped);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched.size());
        result.put("done", done);
        result.put("failed", failed);
        result.put("skipped", skipped);
        return result;
    }

    // ============================================================
    // 事件驱动（供 WorkOrderService / OrderSlaService 回调）
    // ============================================================

    /**
     * 触发事件：查启用的、触发事件匹配、命中条件满足的规则；延迟为 0 同事务内联执行，否则落 PENDING。
     *
     * <p>由工单状态流转与 SLA 扫描调用，运行在调用方事务内：内联动作与执行流水同事务，
     * 单个动作的意外异常被捕获并记 FAILED，不影响主业务事务（无跨事务代理边界）。</p>
     *
     * @param event 事件编码，取值见 {@link OrderRuleEvent}
     * @param order 触发事件的工单
     */
    @Transactional(rollbackFor = Exception.class)
    public void fire(String event, WorkOrder order) {
        if (event == null || order == null || order.getId() == null) {
            return;
        }
        List<OrderRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<OrderRule>()
                .eq(OrderRule::getStatus, 1)
                .eq(OrderRule::getTriggerEvent, event)
                .orderByAsc(OrderRule::getSort).orderByAsc(OrderRule::getId));
        if (rules.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (OrderRule rule : rules) {
            if (!matchCondition(rule, order)) {
                continue;
            }
            int delay = rule.getDelayMinutes() == null ? 0 : rule.getDelayMinutes();
            if (delay <= 0) {
                fireImmediate(rule, order, event, now);
            } else {
                enqueuePending(rule, order, event, now.plusMinutes(delay));
            }
        }
    }

    /**
     * 立即执行：同事务内联执行动作并就地写执行流水终态；异常隔离为 FAILED，不外抛。
     */
    private void fireImmediate(OrderRule rule, WorkOrder order, String event, LocalDateTime now) {
        ActionOutcome outcome;
        try {
            outcome = executeAction(rule, order, event);
        } catch (Exception e) {
            log.error("规则动作执行异常：ruleId={}, orderId={}, action={}",
                    rule.getId(), order.getId(), rule.getActionType(), e);
            outcome = ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "执行异常：" + rootMessage(e));
        }
        writeRun(rule, order, event, outcome, now, now);
    }

    /**
     * 延迟/时间驱动：落 PENDING 队列，按 (order, rule, planned_time) 幂等去重。
     */
    private void enqueuePending(OrderRule rule, WorkOrder order, String event, LocalDateTime plannedTime) {
        Long exists = runMapper.selectCount(new LambdaQueryWrapper<OrderRuleRun>()
                .eq(OrderRuleRun::getOrderId, order.getId())
                .eq(OrderRuleRun::getRuleId, rule.getId())
                .eq(OrderRuleRun::getPlannedTime, plannedTime));
        if (exists != null && exists > 0) {
            return;
        }
        OrderRuleRun run = new OrderRuleRun();
        run.setRuleId(rule.getId());
        run.setOrderId(order.getId());
        run.setEvent(event);
        run.setActionType(rule.getActionType());
        run.setStatus(OrderRuleRunStatus.PENDING.getCode());
        run.setPlannedTime(plannedTime);
        runMapper.insert(run);
        log.debug("规则延迟入队：ruleId={}, orderId={}, plannedTime={}", rule.getId(), order.getId(), plannedTime);
    }

    /**
     * 扫描到期 PENDING 队列并执行（由定时任务每分钟调用）。每个到期记录独立事务，互不影响。
     *
     * <p>外层以系统模式跨租户取到期队列，内层逐条切到记录所属租户执行，
     * 保证动作写入的 tenant_id 与记录一致。</p>
     *
     * @return 本轮执行记录数
     */
    public int runDue(LocalDateTime now) {
        int[] executed = new int[]{0};
        TenantContext.runSystem(() -> {
            List<OrderRuleRun> due = runMapper.selectList(new LambdaQueryWrapper<OrderRuleRun>()
                    .eq(OrderRuleRun::getStatus, OrderRuleRunStatus.PENDING.getCode())
                    .le(OrderRuleRun::getPlannedTime, now)
                    .orderByAsc(OrderRuleRun::getPlannedTime).orderByAsc(OrderRuleRun::getId)
                    .last("LIMIT " + MAX_RUN_BATCH));
            for (OrderRuleRun run : due) {
                boolean done = TenantContext.callAs(run.getTenantId(), () -> executeDueRun(run, now));
                if (done) {
                    executed[0]++;
                }
            }
            if (!due.isEmpty()) {
                log.info("规则引擎扫描到期队列：待执行 {} 条，本轮执行 {} 条", due.size(), executed[0]);
            }
        });
        return executed[0];
    }

    /**
     * 执行单条到期记录：在其所属规则仍启用、工单仍存在时执行动作并回写终态（独立事务）。
     */
    private boolean executeDueRun(OrderRuleRun run, LocalDateTime now) {
        // 抢占：仅当仍为 PENDING 时置为执行中，避免并发重复执行
        LambdaUpdateWrapper<OrderRuleRun> claim = new LambdaUpdateWrapper<>();
        claim.eq(OrderRuleRun::getId, run.getId())
                .eq(OrderRuleRun::getStatus, OrderRuleRunStatus.PENDING.getCode())
                .set(OrderRuleRun::getStatus, OrderRuleRunStatus.DONE.getCode())
                .set(OrderRuleRun::getUpdateTime, now);
        int claimed = runMapper.update(null, claim);
        if (claimed != 1) {
            return false;
        }
        try {
            transactionTemplate.execute(status -> {
                OrderRule rule = ruleMapper.selectById(run.getRuleId());
                WorkOrder order = workOrderMapper.selectById(run.getOrderId());
                ActionOutcome outcome;
                if (rule == null || !Integer.valueOf(1).equals(rule.getStatus())) {
                    outcome = ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), "规则已停用或删除，跳过");
                } else if (order == null) {
                    outcome = ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "工单不存在");
                } else {
                    outcome = executeAction(rule, order, run.getEvent());
                }
                finishRun(run.getId(), outcome, now);
                return null;
            });
            return true;
        } catch (Exception e) {
            log.error("到期规则执行失败：runId={}, ruleId={}, orderId={}",
                    run.getId(), run.getRuleId(), run.getOrderId(), e);
            markRunFailed(run.getId(), e, now);
            return true;
        }
    }

    /**
     * 立即/试跑执行后就地写一条终态流水（同事务），供执行流水与审计留痕。
     */
    private void writeRun(OrderRule rule, WorkOrder order, String event, ActionOutcome outcome,
                          LocalDateTime plannedTime, LocalDateTime executedTime) {
        OrderRuleRun run = new OrderRuleRun();
        run.setRuleId(rule.getId());
        run.setOrderId(order.getId());
        run.setEvent(event);
        run.setActionType(rule.getActionType());
        run.setStatus(outcome.status);
        run.setPlannedTime(plannedTime);
        run.setExecutedTime(executedTime);
        run.setResult(trim(outcome.message, 500));
        runMapper.insert(run);
    }

    private void finishRun(Long runId, ActionOutcome outcome, LocalDateTime now) {
        LambdaUpdateWrapper<OrderRuleRun> update = new LambdaUpdateWrapper<>();
        update.eq(OrderRuleRun::getId, runId)
                .set(OrderRuleRun::getStatus, outcome.status)
                .set(OrderRuleRun::getExecutedTime, now)
                .set(OrderRuleRun::getResult, trim(outcome.message, 500));
        runMapper.update(null, update);
    }

    private void markRunFailed(Long runId, Exception e, LocalDateTime now) {
        LambdaUpdateWrapper<OrderRuleRun> update = new LambdaUpdateWrapper<>();
        update.eq(OrderRuleRun::getId, runId)
                .set(OrderRuleRun::getStatus, OrderRuleRunStatus.FAILED.getCode())
                .set(OrderRuleRun::getExecutedTime, now)
                .set(OrderRuleRun::getResult, trim("执行异常：" + rootMessage(e), 500));
        runMapper.update(null, update);
    }

    // ============================================================
    // 动作执行
    // ============================================================

    /**
     * 执行规则动作，返回执行结果（业务性拒绝以 FAILED/SKIPPED 返回而非抛异常）。
     */
    private ActionOutcome executeAction(OrderRule rule, WorkOrder order, String event) {
        OrderRuleAction action = OrderRuleAction.fromCode(rule.getActionType());
        if (action == null) {
            return ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "未知的动作类型：" + rule.getActionType());
        }
        switch (action) {
            case AUTO_ASSIGN:
                return doAutoAssign(rule, order);
            case ASSIGN_AGENT:
                return doAssignAgent(rule, order);
            case CHANGE_PRIORITY:
                return doChangePriority(rule, order);
            case CHANGE_STATUS:
                return doChangeStatus(rule, order);
            case NOTIFY_ROLE:
                return doNotifyRole(rule, order);
            default:
                return ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "本期不支持的动作：" + action.getCode());
        }
    }

    private ActionOutcome doAutoAssign(OrderRule rule, WorkOrder order) {
        if (StringUtils.hasText(order.getStatus()) && !isAssignableStatus(order.getStatus())) {
            return ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), "工单状态 " + order.getStatus() + " 不允许自动分配");
        }
        OrderAssignService.AssignResult result = orderAssignService.autoAssign(order, rule.getAssignGroupId());
        return result.isAssigned()
                ? ActionOutcome.of(OrderRuleRunStatus.DONE.getCode(), result.getMessage())
                : ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), result.getMessage());
    }

    private ActionOutcome doAssignAgent(OrderRule rule, WorkOrder order) {
        Long agentId = parseLong(rule.getActionValue());
        if (agentId == null) {
            return ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "指派坐席 ID 非法");
        }
        if (!isAssignableStatus(order.getStatus())) {
            return ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), "工单状态 " + order.getStatus() + " 不允许指派");
        }
        OrderAssignService.AssignResult result = orderAssignService.assignTo(order, agentId);
        return result.isAssigned()
                ? ActionOutcome.of(OrderRuleRunStatus.DONE.getCode(), result.getMessage())
                : ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), result.getMessage());
    }

    private ActionOutcome doChangePriority(OrderRule rule, WorkOrder order) {
        String newPriority = rule.getActionValue();
        if (OrderPriority.fromCode(newPriority) == null) {
            return ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "目标优先级非法：" + newPriority);
        }
        if (newPriority.equals(order.getPriority())) {
            return ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), "优先级未变化");
        }
        String old = order.getPriority();
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getPriority, newPriority)
                .set(WorkOrder::getUpdateTime, LocalDateTime.now());
        workOrderMapper.update(null, update);
        order.setPriority(newPriority);
        orderSlaService.recalcOnPriorityChange(order, newPriority, LocalDateTime.now());
        writeLog(order.getId(), OrderAction.COMMENT, order.getStatus(), order.getStatus(),
                "规则调整优先级 " + old + " → " + newPriority);
        return ActionOutcome.of(OrderRuleRunStatus.DONE.getCode(), "优先级 " + old + " → " + newPriority);
    }

    private ActionOutcome doChangeStatus(OrderRule rule, WorkOrder order) {
        String target = rule.getActionValue();
        OrderStatus targetStatus = OrderStatus.fromCode(target);
        if (targetStatus == null) {
            return ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "目标状态非法：" + target);
        }
        String from = order.getStatus();
        if (target.equals(from)) {
            return ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), "状态未变化");
        }
        List<String> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(target)) {
            return ActionOutcome.of(OrderRuleRunStatus.FAILED.getCode(), "非法状态流转 " + from + " → " + target);
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getStatus, target)
                .set(WorkOrder::getUpdateTime, now);
        if (targetStatus == OrderStatus.RESOLVED) {
            update.set(WorkOrder::getResolveTime, now);
        } else if (targetStatus == OrderStatus.CLOSED) {
            update.set(WorkOrder::getCloseTime, now);
        }
        workOrderMapper.update(null, update);
        order.setStatus(target);
        if (targetStatus == OrderStatus.RESOLVED || targetStatus == OrderStatus.CLOSED) {
            orderSlaService.finish(order, now);
        } else if (targetStatus == OrderStatus.REOPENED) {
            orderSlaService.resetOnReopen(order, now);
        }
        writeLog(order.getId(), statusAction(targetStatus), from, target, "规则变更状态 " + from + " → " + target);
        return ActionOutcome.of(OrderRuleRunStatus.DONE.getCode(), "状态 " + from + " → " + target);
    }

    private ActionOutcome doNotifyRole(OrderRule rule, WorkOrder order) {
        String role = rule.getActionValue() == null ? ROLE_TARGET_ADMIN : rule.getActionValue().trim().toUpperCase();
        List<Long> targets = resolveNotifyTargets(role, order);
        if (targets.isEmpty()) {
            return ActionOutcome.of(OrderRuleRunStatus.SKIPPED.getCode(), "无匹配的通知对象");
        }
        String content = "工单 " + order.getOrderNo() + "：" + order.getTitle() + " 触发规则通知（"
                + safe(rule.getRuleName()) + "）";
        for (Long userId : targets) {
            notifyUser(userId, "WARN", "工单规则通知", content, order.getId());
        }
        return ActionOutcome.of(OrderRuleRunStatus.DONE.getCode(), "已通知 " + targets.size() + " 人（" + role + "）");
    }

    private List<Long> resolveNotifyTargets(String role, WorkOrder order) {
        List<Long> targets = new ArrayList<>();
        if (ROLE_TARGET_CREATOR.equals(role)) {
            if (order.getCreatorId() != null) {
                targets.add(order.getCreatorId());
            }
        } else if (ROLE_TARGET_ASSIGNEE.equals(role)) {
            if (order.getAssigneeId() != null) {
                targets.add(order.getAssigneeId());
            }
        } else if (ROLE_TARGET_ADMIN.equals(role)) {
            List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRoleCode, Constants.ROLE_ADMIN)
                    .eq(SysUser::getStatus, Constants.STATUS_ENABLED));
            for (SysUser admin : admins) {
                targets.add(admin.getId());
            }
        }
        return targets;
    }

    // ============================================================
    // 命中匹配
    // ============================================================

    private boolean matchCondition(OrderRule rule, WorkOrder order) {
        return matchValue(rule.getMatchPriority(), order.getPriority())
                && matchValue(rule.getMatchCategory(), order.getCategory())
                && matchValue(rule.getMatchSource(), order.getSource());
    }

    private boolean matchValue(String expect, String actual) {
        return !StringUtils.hasText(expect) || expect.equals(actual);
    }

    private List<WorkOrder> matchOpenOrders(String priority, String category, String source, int limit) {
        LambdaQueryWrapper<WorkOrder> wrapper = newOpenWrapper(priority, category, source);
        wrapper.orderByAsc(WorkOrder::getPriority).orderByAsc(WorkOrder::getSlaDeadline)
                .last("LIMIT " + limit);
        return workOrderMapper.selectList(wrapper);
    }

    private Long countOpenOrders(String priority, String category, String source) {
        return workOrderMapper.selectCount(newOpenWrapper(priority, category, source));
    }

    private LambdaQueryWrapper<WorkOrder> newOpenWrapper(String priority, String category, String source) {
        return new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, OPEN_STATUSES)
                .eq(StringUtils.hasText(priority), WorkOrder::getPriority, priority)
                .eq(StringUtils.hasText(category), WorkOrder::getCategory, category)
                .eq(StringUtils.hasText(source), WorkOrder::getSource, source);
    }

    // ============================================================
    // 校验与转换
    // ============================================================

    private void validate(OrderRuleRequest request, Long excludeId) {
        String name = request.getRuleName().trim();
        if (existsName(name, excludeId)) {
            throw new BusinessException(ResultCode.CONFLICT, "规则名称已存在");
        }
        if (OrderRuleType.fromCode(request.getRuleType()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "规则类型非法");
        }
        if (!StringUtils.hasText(request.getTriggerEvent())
                || OrderRuleEvent.fromCode(request.getTriggerEvent()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "触发事件不能为空且必须合法");
        }
        if (StringUtils.hasText(request.getMatchPriority())
                && OrderPriority.fromCode(request.getMatchPriority()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "匹配优先级只能为 P0-P3");
        }
        if (StringUtils.hasText(request.getMatchCategory())
                && !Constants.ORDER_CATEGORIES.contains(request.getMatchCategory())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "匹配分类非法");
        }
        if (StringUtils.hasText(request.getMatchSource())
                && OrderSource.fromCode(request.getMatchSource()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "匹配来源非法");
        }
        OrderRuleAction action = OrderRuleAction.fromCode(request.getActionType());
        if (action == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "动作类型非法");
        }
        validateAction(request, action);
    }

    private void validateAction(OrderRuleRequest request, OrderRuleAction action) {
        String value = request.getActionValue();
        switch (action) {
            case CHANGE_PRIORITY:
                if (OrderPriority.fromCode(value) == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "调整优先级动作须指定 P0-P3 目标值");
                }
                break;
            case CHANGE_STATUS:
                if (OrderStatus.fromCode(value) == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "变更状态动作须指定合法工单状态");
                }
                break;
            case ASSIGN_AGENT:
                Long agentId = parseLong(value);
                if (agentId == null || !orderAssignService.isActiveAgent(agentId)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "指派坐席须为存在的可用客服坐席 ID");
                }
                break;
            case NOTIFY_ROLE:
                String role = value == null ? ROLE_TARGET_ADMIN : value.trim().toUpperCase();
                if (!ROLE_TARGET_ADMIN.equals(role) && !ROLE_TARGET_CREATOR.equals(role)
                        && !ROLE_TARGET_ASSIGNEE.equals(role)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "通知角色只能为 ADMIN/CREATOR/ASSIGNEE");
                }
                break;
            case AUTO_ASSIGN:
                if (request.getAssignGroupId() != null
                        && skillGroupService.activeGroup(request.getAssignGroupId()) == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "自动分配技能组不存在或已停用");
                }
                break;
            default:
                break;
        }
    }

    private void applyRequest(OrderRule rule, OrderRuleRequest request, String name) {
        rule.setRuleName(name);
        rule.setRuleType(request.getRuleType());
        rule.setTriggerEvent(request.getTriggerEvent());
        rule.setDelayMinutes(request.getDelayMinutes() == null ? 0 : request.getDelayMinutes());
        rule.setMatchPriority(trimToNull(request.getMatchPriority()));
        rule.setMatchCategory(trimToNull(request.getMatchCategory()));
        rule.setMatchSource(trimToNull(request.getMatchSource()));
        rule.setActionType(request.getActionType());
        rule.setActionValue(trimToNull(request.getActionValue()));
        rule.setAssignGroupId(request.getAssignGroupId());
        rule.setWebhookUrl(trimToNull(request.getWebhookUrl()));
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setSort(request.getSort() == null ? 0 : request.getSort());
        rule.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private OrderRule requireRule(Long id) {
        // selectById 经多租户拦截器自动附加 tenant_id 条件，跨租户访问直接查不到
        OrderRule rule = id == null ? null : ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单规则不存在");
        }
        return rule;
    }

    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<OrderRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRule::getRuleName, name)
                .ne(excludeId != null, OrderRule::getId, excludeId);
        Long count = ruleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String ruleName(Long ruleId) {
        OrderRule rule = ruleId == null ? null : ruleMapper.selectById(ruleId);
        return rule == null ? null : rule.getRuleName();
    }

    private OrderRuleVO toVO(OrderRule rule) {
        OrderRuleVO vo = new OrderRuleVO();
        vo.setId(rule.getId());
        vo.setRuleName(rule.getRuleName());
        vo.setRuleType(rule.getRuleType());
        vo.setRuleTypeLabel(label(OrderRuleType.fromCode(rule.getRuleType())));
        vo.setTriggerEvent(rule.getTriggerEvent());
        OrderRuleEvent event = OrderRuleEvent.fromCode(rule.getTriggerEvent());
        vo.setTriggerEventLabel(event == null ? null : event.getDescription());
        vo.setDelayMinutes(rule.getDelayMinutes());
        vo.setMatchPriority(rule.getMatchPriority());
        vo.setMatchCategory(rule.getMatchCategory());
        vo.setMatchSource(rule.getMatchSource());
        vo.setActionType(rule.getActionType());
        OrderRuleAction action = OrderRuleAction.fromCode(rule.getActionType());
        vo.setActionTypeLabel(action == null ? null : action.getDescription());
        vo.setActionValue(rule.getActionValue());
        vo.setAssignGroupId(rule.getAssignGroupId());
        if (rule.getAssignGroupId() != null) {
            Map<Long, String> names = skillGroupService.nameMap(
                    Collections.singletonList(rule.getAssignGroupId()));
            vo.setAssignGroupName(names.get(rule.getAssignGroupId()));
        }
        vo.setDescription(rule.getDescription());
        vo.setSort(rule.getSort());
        vo.setStatus(rule.getStatus());
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    private OrderRuleRunVO toRunVO(OrderRuleRun run, String ruleName) {
        OrderRuleRunVO vo = new OrderRuleRunVO();
        vo.setId(run.getId());
        vo.setRuleId(run.getRuleId());
        vo.setRuleName(ruleName);
        vo.setOrderId(run.getOrderId());
        vo.setEvent(run.getEvent());
        OrderRuleEvent event = OrderRuleEvent.fromCode(run.getEvent());
        vo.setEventLabel(event == null ? null : event.getDescription());
        vo.setActionType(run.getActionType());
        OrderRuleAction action = OrderRuleAction.fromCode(run.getActionType());
        vo.setActionTypeLabel(action == null ? null : action.getDescription());
        vo.setStatus(run.getStatus());
        vo.setStatusLabel(OrderRuleRunStatus.labelOf(run.getStatus()));
        vo.setPlannedTime(run.getPlannedTime());
        vo.setExecutedTime(run.getExecutedTime());
        vo.setResult(run.getResult());
        vo.setCreateTime(run.getCreateTime());
        return vo;
    }

    // ============================================================
    // 工具
    // ============================================================

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

    private void notifyUser(Long userId, String level, String title, String content, Long orderId) {
        if (userId == null) {
            return;
        }
        chatPushService.pushSystemNotice(userId, level, title, content, BIZ_TYPE, orderId, "RULE");
    }

    private OrderAction statusAction(OrderStatus target) {
        switch (target) {
            case RESOLVED:
                return OrderAction.RESOLVE;
            case CLOSED:
                return OrderAction.CLOSE;
            case REOPENED:
                return OrderAction.REOPEN;
            case PROCESSING:
                return OrderAction.PROCESS;
            case ASSIGNED:
                return OrderAction.ASSIGN;
            default:
                return OrderAction.COMMENT;
        }
    }

    private boolean isAssignableStatus(String status) {
        return OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.ASSIGNED.getCode().equals(status)
                || OrderStatus.REOPENED.getCode().equals(status);
    }

    private static Map<String, List<String>> buildTransitions() {
        Map<String, List<String>> map = new HashMap<>();
        map.put(OrderStatus.PENDING.getCode(), Arrays.asList(
                OrderStatus.ASSIGNED.getCode(), OrderStatus.PROCESSING.getCode(), OrderStatus.CLOSED.getCode()));
        map.put(OrderStatus.ASSIGNED.getCode(), Arrays.asList(
                OrderStatus.PROCESSING.getCode(), OrderStatus.CLOSED.getCode()));
        map.put(OrderStatus.PROCESSING.getCode(), Arrays.asList(
                OrderStatus.WAITING_CUSTOMER.getCode(), OrderStatus.RESOLVED.getCode(), OrderStatus.CLOSED.getCode()));
        map.put(OrderStatus.WAITING_CUSTOMER.getCode(), Arrays.asList(
                OrderStatus.PROCESSING.getCode(), OrderStatus.RESOLVED.getCode(), OrderStatus.CLOSED.getCode()));
        map.put(OrderStatus.RESOLVED.getCode(), Arrays.asList(
                OrderStatus.CLOSED.getCode(), OrderStatus.REOPENED.getCode()));
        map.put(OrderStatus.REOPENED.getCode(), Arrays.asList(
                OrderStatus.ASSIGNED.getCode(), OrderStatus.PROCESSING.getCode(), OrderStatus.CLOSED.getCode()));
        map.put(OrderStatus.CLOSED.getCode(), Collections.singletonList(OrderStatus.REOPENED.getCode()));
        return map;
    }

    private static String label(OrderRuleType type) {
        return type == null ? null : type.getDescription();
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return StringUtils.hasText(msg) ? msg : cur.getClass().getSimpleName();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 动作执行结果。
     */
    private static final class ActionOutcome {
        private final String status;
        private final String message;

        private ActionOutcome(String status, String message) {
            this.status = status;
            this.message = message;
        }

        static ActionOutcome of(String status, String message) {
            return new ActionOutcome(status, message);
        }
    }

    /**
     * 供扫描任务与自测引用非终态状态集合。
     */
    public static Collection<String> openStatuses() {
        return OPEN_STATUSES;
    }
}
