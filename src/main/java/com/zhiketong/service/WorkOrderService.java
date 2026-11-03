package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.config.properties.SlaProperties;
import com.zhiketong.dto.sla.OrderPauseRequest;
import com.zhiketong.dto.sla.OrderSlaVO;
import com.zhiketong.dto.sla.SlaScanRequest;
import com.zhiketong.dto.workorder.WorkOrderActionRequest;
import com.zhiketong.dto.workorder.WorkOrderAssignRequest;
import com.zhiketong.dto.workorder.WorkOrderCreateRequest;
import com.zhiketong.dto.workorder.WorkOrderDetailVO;
import com.zhiketong.dto.workorder.WorkOrderLogVO;
import com.zhiketong.dto.workorder.WorkOrderPriorityRequest;
import com.zhiketong.dto.workorder.WorkOrderRatingRequest;
import com.zhiketong.dto.workorder.WorkOrderReopenRequest;
import com.zhiketong.dto.workorder.WorkOrderUpdateRequest;
import com.zhiketong.dto.workorder.WorkOrderVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.OrderTemplate;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.entity.WorkOrderLog;
import com.zhiketong.enums.OrderAction;
import com.zhiketong.enums.OrderPriority;
import com.zhiketong.enums.OrderRuleEvent;
import com.zhiketong.enums.OrderSource;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.WorkOrderLogMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.websocket.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单业务服务（R-49 ~ R-63）：创建、查询、分配、状态流转、评分与 SLA 超时判定。
 *
 * <p>状态机见文档第四章：所有状态变更均先校验当前状态与操作人权限，
 * 再在同一事务内更新工单并写入 work_order_log，保证工单与流转日志一致。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    /** 非终态工单状态 */
    private static final List<String> OPEN_STATUSES = Arrays.asList(
            OrderStatus.PENDING.getCode(), OrderStatus.ASSIGNED.getCode(),
            OrderStatus.PROCESSING.getCode(), OrderStatus.WAITING_CUSTOMER.getCode(),
            OrderStatus.RESOLVED.getCode(), OrderStatus.REOPENED.getCode());

    /** 允许的问题分类 */
    private static final List<String> CATEGORIES = Constants.ORDER_CATEGORIES;

    /** 首响超时日志标识 */
    public static final String OVERDUE_FIRST_RESPONSE = "FIRST_RESPONSE_OVERDUE";

    /** 解决超时日志标识 */
    public static final String OVERDUE_RESOLVE = "RESOLVE_OVERDUE";

    /** 超时扫描单批上限 */
    private static final int MAX_SCAN_BATCH = 200;

    /** 工单号时间格式 */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 查询日期格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 系统操作人名称 */
    private static final String SYSTEM_OPERATOR = "系统";

    /** 通知业务类型 */
    private static final String BIZ_TYPE = "WORKORDER";

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final SlaProperties slaProperties;
    private final ChatPushService chatPushService;
    private final OrderFormService orderFormService;
    private final OrderSlaService orderSlaService;
    private final OrderRuleService orderRuleService;
    private final TenantUsageService tenantUsageService;

    /**
     * 工单分页列表（R-50）：ADMIN 可看全部；AGENT 强制仅返回分配给本人的工单。
     */
    public PageResult<WorkOrderVO> page(LoginUser loginUser, int pageNum, int pageSize, String keyword,
                                        String status, String priority, String category, Long assigneeId,
                                        Long creatorId, Boolean overdueOnly, String startDate, String endDate) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            // 坐席固定数据权限，忽略入参中的受理人/创建人筛选
            wrapper.eq(WorkOrder::getAssigneeId, loginUser.getUserId());
        } else {
            if (assigneeId != null) {
                wrapper.eq(WorkOrder::getAssigneeId, assigneeId);
            }
            if (creatorId != null) {
                wrapper.eq(WorkOrder::getCreatorId, creatorId);
            }
        }
        applyCommonFilters(wrapper, keyword, status, priority, category, startDate, endDate);
        if (Boolean.TRUE.equals(overdueOnly)) {
            wrapper.eq(WorkOrder::getOverdueFlag, 1);
        }
        wrapper.orderByDesc(WorkOrder::getCreateTime).orderByDesc(WorkOrder::getId);

        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 我的工单（R-51）：USER 按创建人、AGENT 按受理人、ADMIN 返回全部。
     */
    public PageResult<WorkOrderVO> myPage(LoginUser loginUser, int pageNum, int pageSize, String keyword,
                                          String status, String priority, String startDate, String endDate) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            wrapper.eq(WorkOrder::getAssigneeId, loginUser.getUserId());
        } else if (Constants.ROLE_USER.equals(loginUser.getRoleCode())) {
            wrapper.eq(WorkOrder::getCreatorId, loginUser.getUserId());
        }
        applyCommonFilters(wrapper, keyword, status, priority, null, startDate, endDate);
        wrapper.orderByDesc(WorkOrder::getUpdateTime).orderByDesc(WorkOrder::getId);

        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 待分配工单池：坐席可自领。仅返回未指定受理人且处于待分配/已重开的工单，
     * 按优先级（P0 在前）与 SLA 截止时间升序排列。
     */
    public PageResult<WorkOrderVO> pendingPool(int pageNum, int pageSize, String keyword,
                                               String priority, String category) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(WorkOrder::getAssigneeId)
                .in(WorkOrder::getStatus, OrderStatus.PENDING.getCode(), OrderStatus.REOPENED.getCode());
        applyCommonFilters(wrapper, keyword, null, priority, category, null, null);
        wrapper.orderByAsc(WorkOrder::getPriority).orderByAsc(WorkOrder::getSlaDeadline);
        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 超时工单列表（R-52）：overdue_flag=1 且未关闭，按 SLA 截止时间升序。
     */
    public PageResult<WorkOrderVO> overduePage(LoginUser loginUser, int pageNum, int pageSize,
                                               String priority, String category) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrder::getOverdueFlag, 1)
                .ne(WorkOrder::getStatus, OrderStatus.CLOSED.getCode());
        if (Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            wrapper.eq(WorkOrder::getAssigneeId, loginUser.getUserId());
        }
        checkPriority(priority);
        checkCategory(category);
        wrapper.eq(StringUtils.hasText(priority), WorkOrder::getPriority, priority)
                .eq(StringUtils.hasText(category), WorkOrder::getCategory, category)
                .orderByAsc(WorkOrder::getSlaDeadline);
        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 工单详情（R-53）：创建人、受理人或管理员可见，并返回可执行动作。
     */
    public WorkOrderDetailVO detail(Long id, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkVisible(order, loginUser);
        WorkOrderDetailVO detail = new WorkOrderDetailVO();
        WorkOrderVO vo = toVO(order, userNames(Collections.singletonList(order)));
        vo.setCustomFields(orderFormService.values(order.getId()));
        detail.setWorkOrder(vo);
        detail.setAllowedActions(allowedActions(order, loginUser));
        return detail;
    }

    /**
     * 创建工单（R-49）：未指定受理人进入 PENDING，管理员指定受理人直接 ASSIGNED。
     *
     * <p>指定模板时，分类与优先级可省略（取模板默认值），并按模板绑定字段校验必填；
     * 字段值、工单与流转日志在同一事务内落库，任意一步失败全部回滚。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO create(WorkOrderCreateRequest request, LoginUser loginUser) {
        // 工单额度按自然月计量，超额时直接拦截并提示升级套餐（S4-B PL-04）
        tenantUsageService.assertQuota(TenantPlanService.METRIC_ORDER);
        String source = StringUtils.hasText(request.getSource())
                ? request.getSource() : OrderSource.MANUAL.getCode();
        OrderTemplate template = request.getTemplateId() == null
                ? null : orderFormService.requireActiveTemplate(request.getTemplateId());
        String category = resolveCategory(request.getCategory(), template);
        String priority = StringUtils.hasText(request.getPriority())
                ? request.getPriority()
                : (template != null && StringUtils.hasText(template.getDefaultPriority())
                        ? template.getDefaultPriority() : OrderPriority.P2.getCode());
        ChatSession session = null;
        if (OrderSource.CHAT.getCode().equals(source)) {
            if (request.getSessionId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "来源为 CHAT 时必须关联会话");
            }
            session = validateSession(request.getSessionId(), loginUser);
        } else if (request.getSessionId() != null) {
            session = validateSession(request.getSessionId(), loginUser);
        }
        if (request.getAssigneeId() != null && !Constants.ROLE_ADMIN.equals(loginUser.getRoleCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可在创建时指定受理坐席");
        }
        SysUser assignee = request.getAssigneeId() == null ? null : validateAssignee(request.getAssigneeId());

        LocalDateTime now = LocalDateTime.now();
        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        order.setTitle(request.getTitle().trim());
        order.setContent(request.getContent().trim());
        order.setCategory(category);
        order.setPriority(priority);
        order.setSource(source);
        order.setTemplateId(template == null ? null : template.getId());
        order.setSessionId(session == null ? null : session.getId());
        order.setCreatorId(loginUser.getUserId());
        order.setAssigneeId(assignee == null ? null : assignee.getId());
        order.setStatus(assignee == null ? OrderStatus.PENDING.getCode() : OrderStatus.ASSIGNED.getCode());
        order.setSlaDeadline(now.plusMinutes(slaProperties.getResolveMinutes(priority)));
        order.setOverdueFlag(0);
        order.setRemark(trimToNull(request.getRemark()));
        workOrderMapper.insert(order);
        orderFormService.saveValues(order.getId(), template == null ? null : template.getId(),
                request.getCustomFields());
        // 建立 SLA 计时记录并按优先级策略计算三级时限，同步回工单
        orderSlaService.initRecord(order, now);

        String creatorName = displayName(loginUser.getNickname(), loginUser.getUsername());
        String createRemark = StringUtils.hasText(request.getRemark())
                ? request.getRemark().trim()
                : (session == null ? "手工创建工单" : "用户从会话提交工单");
        writeLog(order.getId(), OrderAction.CREATE, null, OrderStatus.PENDING.getCode(),
                loginUser.getUserId(), creatorName, createRemark);
        if (assignee != null) {
            writeLog(order.getId(), OrderAction.ASSIGN, OrderStatus.PENDING.getCode(),
                    OrderStatus.ASSIGNED.getCode(), loginUser.getUserId(), creatorName,
                    "创建时分配给坐席" + displayName(assignee.getNickname(), assignee.getUsername()));
            notifyUser(assignee.getId(), "INFO", "新工单待受理",
                    "工单 " + order.getOrderNo() + " 已分配给您，请及时受理", order.getId(), "ASSIGN");
        }
        log.info("创建工单：id={}, orderNo={}, creatorId={}, assigneeId={}",
                order.getId(), order.getOrderNo(), order.getCreatorId(), order.getAssigneeId());
        fireRule(OrderRuleEvent.CREATED, order);
        return toVO(reload(order.getId()), userNames(Collections.singletonList(order)));
    }

    /**
     * 编辑工单（R-54）：创建人仅能在 PENDING/REOPENED 编辑，管理员不限状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO update(Long id, WorkOrderUpdateRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        boolean admin = Constants.ROLE_ADMIN.equals(loginUser.getRoleCode());
        boolean creator = loginUser.getUserId().equals(order.getCreatorId());
        if (!admin) {
            if (!creator) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑该工单");
            }
            if (!OrderStatus.PENDING.getCode().equals(order.getStatus())
                    && !OrderStatus.REOPENED.getCode().equals(order.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "工单当前状态不允许编辑");
            }
        }
        List<String> changed = new ArrayList<>();
        if (StringUtils.hasText(request.getTitle())) {
            order.setTitle(request.getTitle().trim());
            changed.add("标题");
        }
        if (StringUtils.hasText(request.getContent())) {
            order.setContent(request.getContent().trim());
            changed.add("内容");
        }
        if (StringUtils.hasText(request.getCategory())) {
            order.setCategory(request.getCategory());
            changed.add("分类");
        }
        if (request.getRemark() != null) {
            order.setRemark(trimToNull(request.getRemark()));
            changed.add("备注");
        }
        if (!changed.isEmpty()) {
            updateOrder(order);
            String remark = "编辑了" + String.join("、", changed);
            if (StringUtils.hasText(request.getRemark())) {
                remark = remark + "：" + request.getRemark().trim();
            }
            writeLog(order.getId(), OrderAction.COMMENT, order.getStatus(), order.getStatus(),
                    loginUser.getUserId(), operatorName(loginUser), remark);
        }
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 分配工单（R-55）：PENDING/REOPENED 首次分配，ASSIGNED 视为管理员转派。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO assign(Long id, WorkOrderAssignRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        String status = order.getStatus();
        if (!OrderStatus.PENDING.getCode().equals(status)
                && !OrderStatus.REOPENED.getCode().equals(status)
                && !OrderStatus.ASSIGNED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.CONFLICT, "工单当前状态不允许分配");
        }
        if (request.getAssigneeId().equals(order.getAssigneeId())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已分配给该坐席，无需重复分配");
        }
        SysUser assignee = validateAssignee(request.getAssigneeId());
        String fromStatus = status;
        order.setAssigneeId(assignee.getId());
        order.setStatus(OrderStatus.ASSIGNED.getCode());
        updateOrder(order);
        writeLog(order.getId(), OrderAction.ASSIGN, fromStatus, OrderStatus.ASSIGNED.getCode(),
                loginUser.getUserId(), operatorName(loginUser),
                StringUtils.hasText(request.getRemark())
                        ? request.getRemark().trim()
                        : "分配给坐席" + displayName(assignee.getNickname(), assignee.getUsername()));
        notifyUser(assignee.getId(), "INFO", "新工单待受理",
                "工单 " + order.getOrderNo() + " 已分配给您，请及时受理", order.getId(), "ASSIGN");
        fireRule(OrderRuleEvent.ASSIGNED, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 受理工单（R-56）：已分配且为本人直接受理；PENDING/REOPENED 由坐席自领并受理。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO accept(Long id, WorkOrderActionRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        String status = order.getStatus();
        if (OrderStatus.CLOSED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已关闭，无法受理");
        }
        if (OrderStatus.RESOLVED.getCode().equals(status)
                || OrderStatus.PROCESSING.getCode().equals(status)) {
            throw new BusinessException(ResultCode.CONFLICT, "工单当前状态不允许受理");
        }
        boolean selfClaim = OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REOPENED.getCode().equals(status);
        if (!selfClaim) {
            if (order.getAssigneeId() == null) {
                // 数据异常时允许当前坐席直接接管
                order.setAssigneeId(loginUser.getUserId());
            } else if (!loginUser.getUserId().equals(order.getAssigneeId())) {
                throw new BusinessException(ResultCode.CONFLICT, "工单已分配给其他坐席");
            }
        }
        String fromStatus = status;
        if (selfClaim) {
            order.setAssigneeId(loginUser.getUserId());
            writeLog(order.getId(), OrderAction.ASSIGN, fromStatus, OrderStatus.ASSIGNED.getCode(),
                    loginUser.getUserId(), operatorName(loginUser), "坐席自领工单");
            fromStatus = OrderStatus.ASSIGNED.getCode();
        }
        order.setStatus(OrderStatus.PROCESSING.getCode());
        if (order.getFirstResponseTime() == null) {
            order.setFirstResponseTime(LocalDateTime.now());
        }
        updateOrder(order);
        orderSlaService.markFirstResponse(order, order.getFirstResponseTime());
        writeLog(order.getId(), OrderAction.ACCEPT, fromStatus, OrderStatus.PROCESSING.getCode(),
                loginUser.getUserId(), operatorName(loginUser),
                StringUtils.hasText(request == null ? null : request.getRemark())
                        ? request.getRemark().trim() : "坐席已受理工单");
        notifyUser(order.getCreatorId(), "INFO", "工单已受理",
                "工单 " + order.getOrderNo() + " 已由客服受理，正在处理中", order.getId(), "ACCEPT");
        fireRule(OrderRuleEvent.PROCESSING, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 添加处理进展（R-57）：PROCESSING 自环，仅追加日志并刷新更新时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO process(Long id, String remark, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkAssignee(order, loginUser);
        if (!OrderStatus.PROCESSING.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅处理中的工单可添加处理进展");
        }
        // 仅刷新 update_time，保证前端能看到进展更新时间
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, id).set(WorkOrder::getUpdateTime, LocalDateTime.now());
        workOrderMapper.update(null, update);
        writeLog(id, OrderAction.PROCESS, OrderStatus.PROCESSING.getCode(), OrderStatus.PROCESSING.getCode(),
                loginUser.getUserId(), operatorName(loginUser), remark.trim());
        // 坐席回复视为一次有效响应，重置回复间隔计时
        orderSlaService.resetReplyDue(order, LocalDateTime.now());
        fireRule(OrderRuleEvent.PROCESSING, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 解决工单（R-58）：PROCESSING → RESOLVED，写入解决时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO resolve(Long id, String remark, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkAssignee(order, loginUser);
        if (!OrderStatus.PROCESSING.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅处理中的工单可标记解决");
        }
        order.setStatus(OrderStatus.RESOLVED.getCode());
        order.setResolveTime(LocalDateTime.now());
        updateOrder(order);
        orderSlaService.finish(order, order.getResolveTime());
        writeLog(id, OrderAction.RESOLVE, OrderStatus.PROCESSING.getCode(), OrderStatus.RESOLVED.getCode(),
                loginUser.getUserId(), operatorName(loginUser), remark.trim());
        notifyUser(order.getCreatorId(), "INFO", "工单已解决",
                "工单 " + order.getOrderNo() + " 已解决，请确认后关闭并评价", id, "RESOLVE");
        fireRule(OrderRuleEvent.RESOLVED, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 关闭工单（R-59）：创建人仅能关闭 RESOLVED 工单；管理员可强制关闭非 CLOSED 工单。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO close(Long id, WorkOrderActionRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        String status = order.getStatus();
        if (OrderStatus.CLOSED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已关闭，请勿重复操作");
        }
        boolean admin = Constants.ROLE_ADMIN.equals(loginUser.getRoleCode());
        boolean creator = loginUser.getUserId().equals(order.getCreatorId());
        String remark = trimToNull(request == null ? null : request.getRemark());
        if (!admin) {
            if (!creator) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权关闭该工单");
            }
            if (!OrderStatus.RESOLVED.getCode().equals(status)) {
                throw new BusinessException(ResultCode.CONFLICT, "工单尚未解决，暂不能关闭");
            }
        } else if (!OrderStatus.RESOLVED.getCode().equals(status) && remark == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "管理员强制关闭工单必须填写关闭原因");
        }
        order.setStatus(OrderStatus.CLOSED.getCode());
        order.setCloseTime(LocalDateTime.now());
        updateOrder(order);
        orderSlaService.finish(order, order.getCloseTime());
        writeLog(id, OrderAction.CLOSE, status, OrderStatus.CLOSED.getCode(),
                loginUser.getUserId(), operatorName(loginUser),
                remark == null ? "确认关闭工单" : remark);
        if (order.getAssigneeId() != null && !order.getAssigneeId().equals(loginUser.getUserId())) {
            notifyUser(order.getAssigneeId(), "INFO", "工单已关闭",
                    "工单 " + order.getOrderNo() + " 已关闭", id, "CLOSE");
        }
        fireRule(OrderRuleEvent.CLOSED, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 重开工单（R-60）：RESOLVED/CLOSED → REOPENED，保留原解决与关闭时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO reopen(Long id, WorkOrderReopenRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        boolean admin = Constants.ROLE_ADMIN.equals(loginUser.getRoleCode());
        boolean creator = loginUser.getUserId().equals(order.getCreatorId());
        if (!admin && !creator) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权重开该工单");
        }
        String status = order.getStatus();
        if (!OrderStatus.RESOLVED.getCode().equals(status)
                && !OrderStatus.CLOSED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.CONFLICT, "仅已解决或已关闭的工单可重开");
        }
        order.setStatus(OrderStatus.REOPENED.getCode());
        updateOrder(order);
        orderSlaService.resetOnReopen(order, LocalDateTime.now());
        writeLog(id, OrderAction.REOPEN, status, OrderStatus.REOPENED.getCode(),
                loginUser.getUserId(), operatorName(loginUser), request.getReason().trim());
        if (order.getAssigneeId() != null) {
            notifyUser(order.getAssigneeId(), "WARN", "工单被重开",
                    "工单 " + order.getOrderNo() + " 已被重开，请重新处理", id, "REOPEN");
        }
        fireRule(OrderRuleEvent.REOPENED, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 调整优先级并重算 SLA（R-61）：sla_deadline = create_time + 新优先级解决时限。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO updatePriority(Long id, WorkOrderPriorityRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        if (OrderStatus.CLOSED.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已关闭，无法调整优先级");
        }
        if (request.getPriority().equals(order.getPriority())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单优先级未发生变化");
        }
        String oldPriority = order.getPriority();
        order.setPriority(request.getPriority());
        updateOrder(order);
        // 存在 SLA 计时记录时按新优先级策略重算三级时限，历史工单回退配置文件口径
        orderSlaService.recalcOnPriorityChange(order, request.getPriority(), LocalDateTime.now());
        String remark = "优先级 " + oldPriority + " → " + request.getPriority();
        if (StringUtils.hasText(request.getRemark())) {
            remark = remark + "：" + request.getRemark().trim();
        }
        writeLog(id, OrderAction.COMMENT, order.getStatus(), order.getStatus(),
                loginUser.getUserId(), operatorName(loginUser), remark);
        fireRule(OrderRuleEvent.PRIORITY_CHANGED, order);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 工单评分（R-62）：仅创建人可对已解决/已关闭且未评分的工单评分。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rating(Long id, WorkOrderRatingRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        if (!loginUser.getUserId().equals(order.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有工单创建人可以评分");
        }
        if (!OrderStatus.RESOLVED.getCode().equals(order.getStatus())
                && !OrderStatus.CLOSED.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "工单尚未解决，暂不能评价");
        }
        if (order.getRating() != null) {
            throw new BusinessException(ResultCode.CONFLICT, "工单已评价，请勿重复提交");
        }
        order.setRating(request.getRating());
        if (StringUtils.hasText(request.getRatingComment())) {
            order.setRemark(request.getRatingComment().trim());
        }
        updateOrder(order);
        writeLog(id, OrderAction.COMMENT, order.getStatus(), order.getStatus(),
                loginUser.getUserId(), operatorName(loginUser),
                "工单评分：" + request.getRating() + " 星"
                        + (StringUtils.hasText(request.getRatingComment())
                        ? "，评价：" + request.getRatingComment().trim() : ""));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("rating", request.getRating());
        return result;
    }

    /**
     * 工单流转日志分页（R-63）：按日志 ID 升序返回。
     */
    public PageResult<WorkOrderLogVO> pageLogs(Long id, int pageNum, int pageSize, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkVisible(order, loginUser);
        LambdaQueryWrapper<WorkOrderLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrderLog::getOrderId, id).orderByAsc(WorkOrderLog::getId);
        Page<WorkOrderLog> page = workOrderLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<WorkOrderLogVO> list = new ArrayList<>(page.getRecords().size());
        for (WorkOrderLog item : page.getRecords()) {
            WorkOrderLogVO vo = new WorkOrderLogVO();
            vo.setId(item.getId());
            vo.setOrderId(item.getOrderId());
            vo.setAction(item.getAction());
            vo.setFromStatus(item.getFromStatus());
            vo.setToStatus(item.getToStatus());
            vo.setOperatorId(item.getOperatorId());
            vo.setOperatorName(item.getOperatorName());
            vo.setRemark(item.getRemark());
            vo.setCreateTime(item.getCreateTime());
            list.add(vo);
        }
        return PageResult.of(page, list);
    }

    /**
     * 工单 SLA 详情（R-133）：创建人、受理人或管理员可见，返回三级时限进度与停表流水。
     */
    public OrderSlaVO slaDetail(Long id, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkVisible(order, loginUser);
        return orderSlaService.detail(order, LocalDateTime.now());
    }

    /**
     * 挂起停表（R-134）：PROCESSING → WAITING_CUSTOMER，暂停 SLA 计时并记录停表流水。
     *
     * <p>仅受理坐席本人或管理员可操作；等待客户反馈等场景下停止计时，解决时限按停表时长顺延。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO pause(Long id, OrderPauseRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkPausePermission(order, loginUser);
        if (!OrderStatus.PROCESSING.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅处理中的工单可挂起停表");
        }
        String pauseType = request == null || !StringUtils.hasText(request.getPauseType())
                ? OrderSlaService.PAUSE_TYPE_WAITING : request.getPauseType();
        String reason = request == null ? null : trimToNull(request.getReason());
        LocalDateTime now = LocalDateTime.now();
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.WAITING_CUSTOMER.getCode());
        updateOrder(order);
        orderSlaService.beginPause(order, pauseType, reason == null ? "等待客户反馈" : reason,
                loginUser.getUserId(), operatorName(loginUser), now);
        writeLog(id, OrderAction.PAUSE, fromStatus, OrderStatus.WAITING_CUSTOMER.getCode(),
                loginUser.getUserId(), operatorName(loginUser),
                reason == null ? "挂起工单，等待客户反馈" : reason);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 恢复计时（R-135）：WAITING_CUSTOMER → PROCESSING，累计停表时长并顺延各截止时刻。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO resume(Long id, OrderPauseRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkPausePermission(order, loginUser);
        if (!OrderStatus.WAITING_CUSTOMER.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅等待客户反馈的工单可恢复计时");
        }
        String reason = request == null ? null : trimToNull(request.getReason());
        LocalDateTime now = LocalDateTime.now();
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.PROCESSING.getCode());
        updateOrder(order);
        orderSlaService.endPause(order, loginUser.getUserId(), operatorName(loginUser), now);
        writeLog(id, OrderAction.RESUME, fromStatus, OrderStatus.PROCESSING.getCode(),
                loginUser.getUserId(), operatorName(loginUser),
                reason == null ? "恢复工单计时" : reason);
        return toVO(reload(id), userNames(Collections.singletonList(order)));
    }

    /**
     * 手动扫描 / 演练单个工单的 SLA（R-136，仅管理员）：按 simulateTime 或当前时间评估三级时限。
     */
    public OrderSlaVO scanSla(Long id, SlaScanRequest request, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        LocalDateTime now = request == null || request.getSimulateTime() == null
                ? LocalDateTime.now() : request.getSimulateTime();
        return orderSlaService.scanOrder(order, now);
    }

    /**
     * 停表 / 恢复操作权限：受理坐席本人或管理员。
     */
    private void checkPausePermission(WorkOrder order, LoginUser loginUser) {
        boolean admin = Constants.ROLE_ADMIN.equals(loginUser.getRoleCode());
        boolean assignee = loginUser.getUserId().equals(order.getAssigneeId());
        if (!admin && !(Constants.ROLE_AGENT.equals(loginUser.getRoleCode()) && assignee)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该工单的计时");
        }
    }

    /**
     * 首响超时候选：未响应且已超过该优先级首响时限的非终态工单。
     * 仅处理未启用新 SLA 引擎（sla_id 为空）的历史工单，新工单由 OrderSlaService 扫描。
     */
    public List<WorkOrder> listFirstResponseOverdue(LocalDateTime now) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WorkOrder::getStatus, OPEN_STATUSES)
                .isNull(WorkOrder::getSlaId)
                .isNull(WorkOrder::getFirstResponseTime)
                .eq(WorkOrder::getOverdueFlag, 0)
                .isNotNull(WorkOrder::getCreateTime)
                .le(WorkOrder::getCreateTime, now.minusMinutes(minFirstResponseMinutes()))
                .orderByAsc(WorkOrder::getCreateTime)
                .last("LIMIT " + MAX_SCAN_BATCH);
        List<WorkOrder> candidates = workOrderMapper.selectList(wrapper);
        List<WorkOrder> result = new ArrayList<>();
        for (WorkOrder order : candidates) {
            LocalDateTime deadline = order.getCreateTime()
                    .plusMinutes(slaProperties.getFirstResponseMinutes(order.getPriority()));
            if (deadline.isBefore(now)) {
                result.add(order);
            }
        }
        return result;
    }

    /**
     * 解决超时候选：未解决且已超过 sla_deadline 的非终态工单。
     */
    public List<WorkOrder> listResolveOverdue(LocalDateTime now) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WorkOrder::getStatus, OPEN_STATUSES)
                .isNull(WorkOrder::getSlaId)
                .isNull(WorkOrder::getResolveTime)
                .eq(WorkOrder::getOverdueFlag, 0)
                .isNotNull(WorkOrder::getSlaDeadline)
                .lt(WorkOrder::getSlaDeadline, now)
                .orderByAsc(WorkOrder::getSlaDeadline)
                .last("LIMIT " + MAX_SCAN_BATCH);
        return workOrderMapper.selectList(wrapper);
    }

    /**
     * 标记工单超时：条件更新 overdue_flag 保证幂等，同类型只写一条 OVERDUE 日志。
     *
     * @param orderId     工单 ID
     * @param overdueType 超时类型，FIRST_RESPONSE_OVERDUE / RESOLVE_OVERDUE
     * @return 本次是否首次标记
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markOverdue(Long orderId, String overdueType) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<>();
        update.eq(WorkOrder::getId, orderId)
                .eq(WorkOrder::getOverdueFlag, 0)
                .set(WorkOrder::getOverdueFlag, 1)
                .set(WorkOrder::getUpdateTime, LocalDateTime.now());
        int rows = workOrderMapper.update(null, update);
        if (rows != 1) {
            return false;
        }
        Long exists = workOrderLogMapper.selectCount(new LambdaQueryWrapper<WorkOrderLog>()
                .eq(WorkOrderLog::getOrderId, orderId)
                .eq(WorkOrderLog::getRemark, overdueType));
        if (exists == null || exists == 0) {
            writeLog(orderId, OrderAction.OVERDUE, order.getStatus(), order.getStatus(),
                    0L, SYSTEM_OPERATOR, overdueType);
        }
        String label = OVERDUE_FIRST_RESPONSE.equals(overdueType) ? "首次响应" : "问题解决";
        String content = "工单 " + order.getOrderNo() + " " + label + "已超时，请尽快处理";
        notifyUser(order.getAssigneeId(), "WARN", "工单SLA超时", content, orderId, "OVERDUE");
        // P0/P1 同时通知全体管理员
        if (OrderPriority.P0.getCode().equals(order.getPriority())
                || OrderPriority.P1.getCode().equals(order.getPriority())) {
            List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRoleCode, Constants.ROLE_ADMIN)
                    .eq(SysUser::getStatus, Constants.STATUS_ENABLED));
            for (SysUser admin : admins) {
                if (!admin.getId().equals(order.getAssigneeId())) {
                    notifyUser(admin.getId(), "WARN", "工单SLA超时", content, orderId, "OVERDUE");
                }
            }
        }
        log.warn("工单SLA超时：id={}, orderNo={}, type={}, priority={}",
                orderId, order.getOrderNo(), overdueType, order.getPriority());
        return true;
    }

    /**
     * 批量转换工单视图对象。
     */
    public List<WorkOrderVO> toVOs(List<WorkOrder> orders) {
        Map<Long, String> names = userNames(orders);
        Map<Long, String> templateNames = orderFormService.templateNames(templateIds(orders));
        List<WorkOrderVO> result = new ArrayList<>(orders.size());
        for (WorkOrder order : orders) {
            result.add(toVO(order, names, templateNames));
        }
        return result;
    }

    private WorkOrderVO toVO(WorkOrder order, Map<Long, String> names) {
        return toVO(order, names, orderFormService.templateNames(order.getTemplateId() == null
                ? Collections.emptyList() : Collections.singletonList(order.getTemplateId())));
    }

    private WorkOrderVO toVO(WorkOrder order, Map<Long, String> names, Map<Long, String> templateNames) {
        WorkOrderVO vo = new WorkOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTitle(order.getTitle());
        vo.setContent(order.getContent());
        vo.setCategory(order.getCategory());
        vo.setPriority(order.getPriority());
        vo.setStatus(order.getStatus());
        vo.setSource(order.getSource());
        vo.setSessionId(order.getSessionId());
        vo.setTemplateId(order.getTemplateId());
        vo.setTemplateName(templateNames.get(order.getTemplateId()));
        vo.setCreatorId(order.getCreatorId());
        vo.setCreatorName(names.get(order.getCreatorId()));
        vo.setAssigneeId(order.getAssigneeId());
        vo.setAssigneeName(names.get(order.getAssigneeId()));
        vo.setSlaDeadline(order.getSlaDeadline());
        vo.setSlaStatus(order.getSlaStatus());
        vo.setFirstResponseTime(order.getFirstResponseTime());
        vo.setResolveTime(order.getResolveTime());
        vo.setCloseTime(order.getCloseTime());
        vo.setRating(order.getRating());
        vo.setOverdueFlag(order.getOverdueFlag() == null ? 0 : order.getOverdueFlag());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());
        return vo;
    }

    /**
     * 计算当前登录人对工单可执行的动作，供前端渲染按钮。
     */
    private List<String> allowedActions(WorkOrder order, LoginUser loginUser) {
        Set<String> actions = new LinkedHashSet<>();
        String status = order.getStatus();
        boolean admin = Constants.ROLE_ADMIN.equals(loginUser.getRoleCode());
        boolean creator = loginUser.getUserId().equals(order.getCreatorId());
        boolean assignee = loginUser.getUserId().equals(order.getAssigneeId());
        if (admin) {
            actions.add("EDIT");
            if (!OrderStatus.CLOSED.getCode().equals(status)) {
                actions.add("CLOSE");
                actions.add("PRIORITY");
            }
            if (OrderStatus.RESOLVED.getCode().equals(status)
                    || OrderStatus.CLOSED.getCode().equals(status)) {
                actions.add("REOPEN");
            }
            if (OrderStatus.PENDING.getCode().equals(status)
                    || OrderStatus.REOPENED.getCode().equals(status)
                    || OrderStatus.ASSIGNED.getCode().equals(status)) {
                actions.add("ASSIGN");
            }
            if (OrderStatus.PROCESSING.getCode().equals(status)) {
                actions.add("PAUSE");
            } else if (OrderStatus.WAITING_CUSTOMER.getCode().equals(status)) {
                actions.add("RESUME");
            }
            return new ArrayList<>(actions);
        }
        if (creator) {
            if (OrderStatus.PENDING.getCode().equals(status)
                    || OrderStatus.REOPENED.getCode().equals(status)) {
                actions.add("EDIT");
            }
            if (OrderStatus.RESOLVED.getCode().equals(status)) {
                actions.add("CLOSE");
                actions.add("REOPEN");
            } else if (OrderStatus.CLOSED.getCode().equals(status)) {
                actions.add("REOPEN");
            }
            if ((OrderStatus.RESOLVED.getCode().equals(status)
                    || OrderStatus.CLOSED.getCode().equals(status)) && order.getRating() == null) {
                actions.add("RATING");
            }
        }
        if (Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            if (OrderStatus.PENDING.getCode().equals(status)
                    || OrderStatus.REOPENED.getCode().equals(status)) {
                actions.add("ACCEPT");
            } else if (OrderStatus.ASSIGNED.getCode().equals(status)
                    && (order.getAssigneeId() == null || assignee)) {
                actions.add("ACCEPT");
            } else if (OrderStatus.PROCESSING.getCode().equals(status) && assignee) {
                actions.add("PROCESS");
                actions.add("RESOLVE");
                actions.add("PAUSE");
            } else if (OrderStatus.WAITING_CUSTOMER.getCode().equals(status) && assignee) {
                actions.add("RESUME");
            }
        }
        return new ArrayList<>(actions);
    }

    private void applyCommonFilters(LambdaQueryWrapper<WorkOrder> wrapper, String keyword, String status,
                                    String priority, String category, String startDate, String endDate) {
        checkStatus(status);
        checkPriority(priority);
        checkCategory(category);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(WorkOrder::getOrderNo, value).or().like(WorkOrder::getTitle, value));
        }
        wrapper.eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
                .eq(StringUtils.hasText(priority), WorkOrder::getPriority, priority)
                .eq(StringUtils.hasText(category), WorkOrder::getCategory, category);
        applyDateRange(wrapper, startDate, endDate);
    }

    private void applyDateRange(LambdaQueryWrapper<WorkOrder> wrapper, String startDate, String endDate) {
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "startDate 不能晚于 endDate");
        }
        if (start != null) {
            wrapper.ge(WorkOrder::getCreateTime, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(WorkOrder::getCreateTime, end.plusDays(1).atStartOfDay());
        }
    }

    private LocalDate parseDate(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + " 格式必须为 yyyy-MM-dd");
        }
    }

    private void checkStatus(String status) {
        if (StringUtils.hasText(status) && OrderStatus.fromCode(status) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "工单状态只能为 " + OrderStatus.valuesDescription());
        }
    }

    private void checkPriority(String priority) {
        if (StringUtils.hasText(priority) && OrderPriority.fromCode(priority) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "优先级只能为 " + OrderPriority.valuesDescription());
        }
    }

    private void checkCategory(String category) {
        if (StringUtils.hasText(category) && !CATEGORIES.contains(category)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "问题分类只能为" + String.join("/", Constants.ORDER_CATEGORIES));
        }
    }

    /**
     * 解析问题分类：请求显式指定优先，其次取模板默认分类，均为空时拦截。
     */
    private String resolveCategory(String requested, OrderTemplate template) {
        String category = StringUtils.hasText(requested)
                ? requested.trim()
                : (template == null ? null : template.getCategory());
        if (!StringUtils.hasText(category)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "问题分类不能为空");
        }
        checkCategory(category);
        return category;
    }

    /**
     * 批量提取有效的模板 ID，供一次性加载模板名称。
     */
    private List<Long> templateIds(List<WorkOrder> orders) {
        List<Long> ids = new ArrayList<>();
        for (WorkOrder order : orders) {
            if (order.getTemplateId() != null) {
                ids.add(order.getTemplateId());
            }
        }
        return ids;
    }

    private WorkOrder requireOrder(Long id) {
        WorkOrder order = id == null ? null : workOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return order;
    }

    private WorkOrder reload(Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return order;
    }

    private void checkVisible(WorkOrder order, LoginUser loginUser) {
        if (Constants.ROLE_ADMIN.equals(loginUser.getRoleCode())) {
            return;
        }
        boolean creator = loginUser.getUserId().equals(order.getCreatorId());
        boolean assignee = loginUser.getUserId().equals(order.getAssigneeId());
        if (!creator && !assignee) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该工单");
        }
    }

    private void checkAssignee(WorkOrder order, LoginUser loginUser) {
        if (!Constants.ROLE_AGENT.equals(loginUser.getRoleCode())
                || !loginUser.getUserId().equals(order.getAssigneeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该工单");
        }
    }

    private ChatSession validateSession(Long sessionId, LoginUser loginUser) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关联会话不存在");
        }
        if (!Constants.ROLE_ADMIN.equals(loginUser.getRoleCode())
                && !loginUser.getUserId().equals(session.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "关联会话不属于当前用户");
        }
        return session;
    }

    private SysUser validateAssignee(Long assigneeId) {
        SysUser user = sysUserMapper.selectById(assigneeId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "受理坐席不存在");
        }
        if (!Constants.ROLE_AGENT.equals(user.getRoleCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "受理人必须是客服坐席");
        }
        if (!Integer.valueOf(Constants.STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "受理坐席已被禁用");
        }
        return user;
    }

    /**
     * 更新工单并刷新 update_time，避免 updateById 携带旧值覆盖数据库自动维护的时间。
     */
    private void updateOrder(WorkOrder order) {
        order.setUpdateTime(LocalDateTime.now());
        workOrderMapper.updateById(order);
    }

    private void writeLog(Long orderId, OrderAction action, String fromStatus, String toStatus,
                          Long operatorId, String operatorName, String remark) {
        WorkOrderLog item = new WorkOrderLog();
        item.setOrderId(orderId);
        item.setAction(action.getCode());
        item.setFromStatus(fromStatus);
        item.setToStatus(toStatus);
        item.setOperatorId(operatorId);
        item.setOperatorName(operatorName);
        item.setRemark(remark);
        workOrderLogMapper.insert(item);
    }

    private void notifyUser(Long userId, String level, String title, String content,
                            Long orderId, String action) {
        if (userId == null) {
            return;
        }
        chatPushService.pushSystemNotice(userId, level, title, content, BIZ_TYPE, orderId, action);
    }

    /**
     * 触发工单规则与自动化引擎：事件与主业务同事务；规则引擎的意外异常被隔离，不影响工单主流程。
     */
    private void fireRule(OrderRuleEvent event, WorkOrder order) {
        try {
            orderRuleService.fire(event.getCode(), order);
        } catch (Exception e) {
            log.error("工单规则触发失败：event={}, orderId={}", event.getCode(), order.getId(), e);
        }
    }

    /**
     * 工单规则执行流水（R-145）：创建人、受理人或管理员可见。
     */
    public java.util.List<com.zhiketong.dto.orderrule.OrderRuleRunVO> ruleRuns(Long id, LoginUser loginUser) {
        WorkOrder order = requireOrder(id);
        checkVisible(order, loginUser);
        return orderRuleService.runsOfOrder(id);
    }

    private String generateOrderNo() {
        for (int i = 0; i < 5; i++) {
            String candidate = "WO" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            Long count = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getOrderNo, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.ERROR, "工单号生成失败，请稍后重试");
    }

    private int minFirstResponseMinutes() {
        int min = Integer.MAX_VALUE;
        for (OrderPriority priority : OrderPriority.values()) {
            min = Math.min(min, slaProperties.getFirstResponseMinutes(priority.getCode()));
        }
        return min == Integer.MAX_VALUE ? 30 : min;
    }

    private Map<Long, String> userNames(Collection<WorkOrder> orders) {
        Set<Long> ids = new HashSet<>();
        for (WorkOrder order : orders) {
            if (order.getCreatorId() != null) {
                ids.add(order.getCreatorId());
            }
            if (order.getAssigneeId() != null) {
                ids.add(order.getAssigneeId());
            }
        }
        Map<Long, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        for (SysUser user : users) {
            result.put(user.getId(), displayName(user.getNickname(), user.getUsername()));
        }
        return result;
    }

    private String operatorName(LoginUser loginUser) {
        return displayName(loginUser.getNickname(), loginUser.getUsername());
    }

    private String displayName(String nickname, String username) {
        return StringUtils.hasText(nickname) ? nickname : username;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
