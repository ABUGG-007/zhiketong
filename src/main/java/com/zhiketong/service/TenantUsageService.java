package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.plan.PlanUsageVO;
import com.zhiketong.entity.AiCallLog;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.Tenant;
import com.zhiketong.entity.TenantPlan;
import com.zhiketong.entity.TenantUsage;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.mapper.AiCallLogMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.TenantMapper;
import com.zhiketong.mapper.TenantUsageMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户用量计量服务（S4-B，PL-04）。
 *
 * <p>计量口径：</p>
 * <ul>
 *   <li>坐席：本租户已启用的 AGENT 账号数；</li>
 *   <li>会话 / 工单 / AI 应答：按自然月统计当期创建量；</li>
 *   <li>知识库：当前未删除词条数。</li>
 * </ul>
 *
 * <p>超额拦截一律实时 COUNT 业务表，不读 {@code tenant_usage} 快照，
 * 避免快照延迟造成误放行；快照只用于趋势展示与对账，由
 * {@code TenantUsageTask} 每日聚合。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUsageService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SysUserMapper sysUserMapper;
    private final KbEntryMapper kbEntryMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final WorkOrderMapper workOrderMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final TenantUsageMapper tenantUsageMapper;
    private final TenantMapper tenantMapper;
    private final TenantService tenantService;
    private final TenantPlanService tenantPlanService;

    /**
     * 指定租户的实时用量：内部会切到该租户上下文统计，平台视角也能正确取值。
     */
    public Usage usage(Long tenantId) {
        if (tenantId == null) {
            return new Usage();
        }
        return TenantContext.callAs(tenantId, this::countInCurrentTenant);
    }

    /**
     * 当前租户上下文的实时用量（必须已进入租户作用域）。
     */
    public Usage currentUsage() {
        return countInCurrentTenant();
    }

    /**
     * 额度校验：超出当前套餐上限时抛出 403，并给出具体用量与处理建议。
     *
     * <p>未进入租户作用域（登录、公开接口前的预处理）或平台视角不做拦截，
     * 平台管理员自身不消耗租户额度。</p>
     *
     * @param metric 额度编码，见 {@link TenantPlanService} 的 METRIC_* 常量
     */
    public void assertQuota(String metric) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            return;
        }
        Tenant tenant = tenantService.getById(tenantId);
        if (tenant == null) {
            return;
        }
        TenantPlan plan = tenantService.planOf(tenant);
        long limit = tenantPlanService.limitOf(plan, metric);
        if (limit < 0) {
            return;
        }
        long used = currentUsage().get(metric);
        if (used >= limit) {
            throw new BusinessException(ResultCode.FORBIDDEN, quotaMessage(plan, metric, used, limit));
        }
    }

    /**
     * 当前租户指定额度是否已用尽（用于大模型调用降级）。
     */
    public boolean exhausted(String metric) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            return false;
        }
        try {
            Tenant tenant = tenantService.getById(tenantId);
            if (tenant == null) {
                return false;
            }
            TenantPlan plan = tenantService.planOf(tenant);
            long limit = tenantPlanService.limitOf(plan, metric);
            return limit >= 0 && currentUsage().get(metric) >= limit;
        } catch (Exception e) {
            // 计量异常不能阻断对客应答
            log.warn("额度余量判断失败，按未超限处理：metric={}, reason={}", metric, e.getMessage());
            return false;
        }
    }

    /**
     * 生成超额提示文案。
     */
    public String quotaMessage(TenantPlan plan, String metric, long used, long limit) {
        return "当前套餐「" + plan.getPlanName() + "」的" + tenantPlanService.metricName(metric)
                + "额度已用尽（" + used + "/" + limit + "），请联系平台管理员升级套餐后再试";
    }

    /**
     * 采集指定租户的用量并写入当月快照（存在则更新），返回快照记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantUsage saveSnapshot(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        Usage usage = usage(tenantId);
        String period = MONTH_FORMATTER.format(LocalDate.now());
        return TenantContext.callAs(tenantId, () -> {
            TenantUsage snapshot = tenantUsageMapper.selectOne(new LambdaQueryWrapper<TenantUsage>()
                    .eq(TenantUsage::getTenantId, tenantId)
                    .eq(TenantUsage::getPeriod, period)
                    .last("LIMIT 1"));
            boolean insert = snapshot == null;
            if (insert) {
                snapshot = new TenantUsage();
                snapshot.setTenantId(tenantId);
                snapshot.setPeriod(period);
            }
            snapshot.setSeatUsed((int) usage.getSeat());
            snapshot.setSessionUsed((int) usage.getSession());
            snapshot.setOrderUsed((int) usage.getOrder());
            snapshot.setKbUsed((int) usage.getKb());
            snapshot.setAiUsed((int) usage.getAi());
            snapshot.setSnapshotTime(LocalDateTime.now());
            if (insert) {
                tenantUsageMapper.insert(snapshot);
            } else {
                tenantUsageMapper.updateById(snapshot);
            }
            return snapshot;
        });
    }

    /**
     * 全租户用量快照（定时任务 / 平台端手动刷新调用），返回成功写入的租户数。
     */
    public int saveAllSnapshots() {
        int[] count = new int[]{0};
        TenantContext.runSystem(() -> {
            List<Tenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                    .eq(Tenant::getStatus, Constants.TENANT_STATUS_ENABLED));
            for (Tenant tenant : tenants) {
                try {
                    saveSnapshot(tenant.getId());
                    count[0]++;
                } catch (Exception e) {
                    log.error("租户用量快照写入失败：tenantId={}", tenant.getId(), e);
                }
            }
        });
        log.info("租户用量快照完成：成功 {} 个租户", count[0]);
        return count[0];
    }

    /**
     * 最近一次用量快照，无记录返回 null。
     */
    public TenantUsage latestSnapshot(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return TenantContext.callAs(tenantId, () -> tenantUsageMapper.selectOne(
                new LambdaQueryWrapper<TenantUsage>()
                        .eq(TenantUsage::getTenantId, tenantId)
                        .orderByDesc(TenantUsage::getPeriod)
                        .last("LIMIT 1")));
    }

    /**
     * 把套餐上限与实时用量组装为额度水位项，租户端套餐页与平台端租户详情共用。
     *
     * @param plan  租户当前套餐，为空时上限按「不限」展示
     * @param usage 实时用量
     */
    public List<PlanUsageVO.UsageItem> quotaItems(TenantPlan plan, Usage usage) {
        String period = MONTH_FORMATTER.format(LocalDate.now());
        Usage real = usage == null ? new Usage() : usage;
        List<PlanUsageVO.UsageItem> items = new ArrayList<>();
        items.add(quotaItem(TenantPlanService.METRIC_SEAT, "坐席账号",
                real.getSeat(), tenantPlanService.limitOf(plan, TenantPlanService.METRIC_SEAT), "个",
                "已启用的客服坐席（AGENT 角色）账号数"));
        items.add(quotaItem(TenantPlanService.METRIC_SESSION, "本月会话",
                real.getSession(), tenantPlanService.limitOf(plan, TenantPlanService.METRIC_SESSION), "次",
                period + " 自然月内创建的会话数"));
        items.add(quotaItem(TenantPlanService.METRIC_ORDER, "本月工单",
                real.getOrder(), tenantPlanService.limitOf(plan, TenantPlanService.METRIC_ORDER), "单",
                period + " 自然月内创建的工单数"));
        items.add(quotaItem(TenantPlanService.METRIC_KB, "知识库条目",
                real.getKb(), tenantPlanService.limitOf(plan, TenantPlanService.METRIC_KB), "条",
                "当前未删除的知识库问答条目数"));
        items.add(quotaItem(TenantPlanService.METRIC_AI, "本月 AI 应答",
                real.getAi(), tenantPlanService.limitOf(plan, TenantPlanService.METRIC_AI), "次",
                period + " 自然月内大模型应答调用次数"));
        return items;
    }

    /**
     * 单项额度水位：上限为 -1 时按不限展示，达到 100% 标记 danger。
     */
    private PlanUsageVO.UsageItem quotaItem(String code, String name, long used, long limit,
                                            String unit, String hint) {
        PlanUsageVO.UsageItem item = new PlanUsageVO.UsageItem();
        item.setCode(code);
        item.setName(name);
        item.setUsed(used);
        item.setLimit(limit);
        item.setUnit(unit);
        item.setHint(hint);
        if (limit < 0) {
            item.setPercent(0);
            item.setLevel("normal");
        } else {
            int percent = limit == 0 ? 100 : (int) Math.min(100, Math.round(used * 100.0 / limit));
            item.setPercent(percent);
            item.setLevel(percent >= 100 ? "danger" : percent >= 80 ? "warning" : "normal");
        }
        return item;
    }

    /**
     * 当前租户内的实时用量统计（依赖拦截器注入 tenant_id）。
     */
    private Usage countInCurrentTenant() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Usage usage = new Usage();
        usage.setSeat(count(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleCode, Constants.ROLE_AGENT)
                .eq(SysUser::getStatus, Constants.STATUS_ENABLED))));
        usage.setSession(count(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, monthStart))));
        usage.setOrder(count(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getCreateTime, monthStart))));
        usage.setKb(count(kbEntryMapper.selectCount(null)));
        usage.setAi(count(aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreateTime, monthStart))));
        return usage;
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 五项额度用量。
     */
    @Data
    public static class Usage {

        /** 已启用坐席账号数 */
        private long seat;

        /** 本月会话数 */
        private long session;

        /** 本月工单数 */
        private long order;

        /** 知识库条目数 */
        private long kb;

        /** 本月 AI 应答调用次数 */
        private long ai;

        /**
         * 按额度编码取值，未知编码返回 0。
         */
        public long get(String metric) {
            if (TenantPlanService.METRIC_SEAT.equals(metric)) {
                return seat;
            }
            if (TenantPlanService.METRIC_SESSION.equals(metric)) {
                return session;
            }
            if (TenantPlanService.METRIC_ORDER.equals(metric)) {
                return order;
            }
            if (TenantPlanService.METRIC_KB.equals(metric)) {
                return kb;
            }
            if (TenantPlanService.METRIC_AI.equals(metric)) {
                return ai;
            }
            return 0L;
        }
    }
}
