package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.plan.PlanUsageVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.Tenant;
import com.zhiketong.entity.TenantPlan;
import com.zhiketong.entity.TenantUsage;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 套餐与用量统计服务（S4-B）。
 *
 * <p>套餐额度统一来自 {@code tenant_plan} 表（{@link TenantPlanService}），
 * 当前租户来自登录上下文；所有额度与 KPI 均基于 MySQL 实时统计，口径写进 hint 字段，
 * 避免出现"看起来是数据、其实是写死"的演示陷阱。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /** KPI 统计窗口（天） */
    private static final int KPI_WINDOW_DAYS = 30;

    private final TenantService tenantService;
    private final TenantPlanService tenantPlanService;
    private final TenantUsageService tenantUsageService;
    private final ChatSessionMapper chatSessionMapper;
    private final WorkOrderMapper workOrderMapper;

    /**
     * 当前租户的套餐 + 本月用量 + 近 30 天 KPI + 全部套餐对比。
     */
    @Transactional(readOnly = true)
    public PlanUsageVO current() {
        Long contextTenantId = TenantContext.getTenantId();
        Long tenantId = contextTenantId == null ? TenantContext.DEFAULT_TENANT_ID : contextTenantId;
        Tenant tenant = tenantService.getById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "租户不存在，无法读取套餐信息");
        }
        TenantPlan plan = tenantPlanService.find(tenant.getPlanCode());
        if (plan == null) {
            throw new BusinessException("租户套餐配置缺失：" + tenant.getPlanCode());
        }

        LocalDateTime kpiStart = LocalDate.now().minusDays(KPI_WINDOW_DAYS).atStartOfDay();
        TenantUsageService.Usage usage = tenantUsageService.usage(tenant.getId());
        TenantUsage snapshot = tenantUsageService.latestSnapshot(tenant.getId());

        PlanUsageVO vo = new PlanUsageVO();
        vo.setTenantCode(tenant.getTenantCode());
        vo.setTenantName(tenant.getTenantName());
        vo.setTenantStatus(tenant.getStatus());
        vo.setExpireTime(tenant.getExpireTime());
        vo.setPlanCode(plan.getPlanCode());
        vo.setPlanName(plan.getPlanName());
        vo.setTagline(plan.getTagline());
        vo.setPrice(plan.getPrice() == null ? 0 : plan.getPrice().intValue());
        vo.setPeriod(plan.getPeriod());
        vo.setFeatures(tenantPlanService.features(plan));
        vo.setPeriodLabel(MONTH_FORMATTER.format(LocalDate.now()));
        vo.setSnapshotTime(snapshot == null ? null : snapshot.getSnapshotTime());
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setBillingNote("套餐额度由平台在「租户与套餐」中维护，变更后即时生效；"
                + "额度按自然月统计，KPI 按近 " + KPI_WINDOW_DAYS + " 天统计。"
                + "试用期结束或额度用尽时，对应功能会被拦截并提示升级。");
        vo.setItems(tenantUsageService.quotaItems(plan, usage));
        vo.setKpis(buildKpis(kpiStart));
        vo.setPlans(tenantPlanService.options(plan.getPlanCode()));
        return vo;
    }

    /**
     * 近 30 天运营 KPI：AI 自主解决率、SLA 达标率、满意度、平均首响。
     */
    private List<PlanUsageVO.KpiItem> buildKpis(LocalDateTime from) {
        List<PlanUsageVO.KpiItem> kpis = new ArrayList<>();

        long closedSessions = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, from)
                .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode()));
        long aiResolved = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, from)
                .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())
                .isNull(ChatSession::getAgentId));
        kpis.add(kpi("aiResolutionRate", "AI 自主解决率",
                closedSessions == 0 ? "—" : percent(aiResolved, closedSessions), "%",
                "近 " + KPI_WINDOW_DAYS + " 天已结束会话中，未接入坐席即完成的占比",
                closedSessions == 0 ? "slate" : "teal"));

        List<WorkOrder> finished = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .select(WorkOrder::getOverdueFlag, WorkOrder::getRating,
                        WorkOrder::getFirstResponseTime, WorkOrder::getCreateTime)
                .ge(WorkOrder::getCreateTime, from)
                .in(WorkOrder::getStatus,
                        OrderStatus.RESOLVED.getCode(), OrderStatus.CLOSED.getCode()));

        long onTime = 0;
        long rated = 0;
        long ratingSum = 0;
        long responded = 0;
        long responseMinutes = 0;
        for (WorkOrder order : finished) {
            if (Integer.valueOf(0).equals(order.getOverdueFlag())) {
                onTime++;
            }
            if (order.getRating() != null) {
                rated++;
                ratingSum += order.getRating();
            }
            if (order.getFirstResponseTime() != null && order.getCreateTime() != null) {
                long minutes = Duration.between(order.getCreateTime(), order.getFirstResponseTime()).toMinutes();
                if (minutes >= 0) {
                    responded++;
                    responseMinutes += minutes;
                }
            }
        }

        kpis.add(kpi("slaCompliance", "SLA 达标率",
                finished.isEmpty() ? "—" : percent(onTime, finished.size()), "%",
                "近 " + KPI_WINDOW_DAYS + " 天已解决/已关闭工单中未超时的占比",
                finished.isEmpty() ? "slate" : (onTime * 100 >= finished.size() * 90 ? "teal" : "amber")));

        kpis.add(kpi("rating", "平均满意度",
                rated == 0 ? "—" : String.format("%.1f", ratingSum * 1.0 / rated), "分",
                "近 " + KPI_WINDOW_DAYS + " 天已评价工单的平均星级（1-5）",
                rated == 0 ? "slate" : "indigo"));

        kpis.add(kpi("firstResponse", "平均首响",
                responded == 0 ? "—" : String.valueOf(Math.round(responseMinutes * 1.0 / responded)), "分钟",
                "近 " + KPI_WINDOW_DAYS + " 天工单从创建到首次被受理的平均耗时",
                responded == 0 ? "slate" : (responseMinutes <= responded * 60L ? "teal" : "amber")));
        return kpis;
    }

    private PlanUsageVO.KpiItem kpi(String code, String name, String value, String unit,
                                    String hint, String tone) {
        PlanUsageVO.KpiItem item = new PlanUsageVO.KpiItem();
        item.setCode(code);
        item.setName(name);
        item.setValue(value);
        item.setUnit(unit);
        item.setHint(hint);
        item.setTone(tone);
        return item;
    }

    private String percent(long part, long total) {
        return String.format("%.1f", part * 100.0 / total);
    }
}
