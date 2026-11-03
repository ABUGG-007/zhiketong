package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.plan.PlanUsageVO;
import com.zhiketong.entity.TenantPlan;
import com.zhiketong.mapper.TenantPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 套餐权益服务（S4-B，PL-03）。
 *
 * <p>套餐额度的唯一事实来源是 tenant_plan 表，替代历史 application.yml 的
 * {@code zhiketong.plan} 静态配置；额度 {@code -1} 统一表示不限。
 * 平台调整套餐、租户换套餐都在运行时即时生效，不需要重启服务。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPlanService {

    /** 额度编码：坐席账号 */
    public static final String METRIC_SEAT = "seat";
    /** 额度编码：自然月会话 */
    public static final String METRIC_SESSION = "session";
    /** 额度编码：自然月工单 */
    public static final String METRIC_ORDER = "order";
    /** 额度编码：知识库条目 */
    public static final String METRIC_KB = "kb";
    /** 额度编码：自然月 AI 应答 */
    public static final String METRIC_AI = "ai";

    /** features 字段分隔符 */
    public static final String FEATURE_SEPARATOR = "|";

    private final TenantPlanMapper tenantPlanMapper;

    /**
     * 启用中的套餐，按 sort 升序。
     */
    @Transactional(readOnly = true)
    public List<TenantPlan> listEnabled() {
        return tenantPlanMapper.selectList(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getStatus, Constants.STATUS_ENABLED)
                .orderByAsc(TenantPlan::getSort)
                .orderByAsc(TenantPlan::getId));
    }

    /**
     * 按编码读取启用中的套餐，不存在或已停用时抛 400。
     */
    @Transactional(readOnly = true)
    public TenantPlan requireEnabled(String planCode) {
        TenantPlan plan = find(planCode);
        if (plan == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "套餐不存在或已停用：" + planCode);
        }
        return plan;
    }

    /**
     * 按编码查询套餐，不存在返回 null（不区分启停）。
     */
    @Transactional(readOnly = true)
    public TenantPlan find(String planCode) {
        if (!StringUtils.hasText(planCode)) {
            return null;
        }
        return tenantPlanMapper.selectOne(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getPlanCode, planCode.trim())
                .last("LIMIT 1"));
    }

    /**
     * 解析套餐功能点：features 以英文竖线分隔，空值返回空列表。
     */
    public List<String> features(TenantPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getFeatures())) {
            return new ArrayList<>();
        }
        List<String> features = new ArrayList<>();
        for (String item : plan.getFeatures().split("\\" + FEATURE_SEPARATOR)) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                features.add(trimmed);
            }
        }
        return features;
    }

    /**
     * 读取指定额度项的套餐上限，{@code -1} 表示不限。
     */
    public long limitOf(TenantPlan plan, String metric) {
        if (plan == null) {
            return -1L;
        }
        Integer limit;
        switch (metric) {
            case METRIC_SEAT:
                limit = plan.getSeatLimit();
                break;
            case METRIC_SESSION:
                limit = plan.getSessionLimit();
                break;
            case METRIC_ORDER:
                limit = plan.getOrderLimit();
                break;
            case METRIC_KB:
                limit = plan.getKbLimit();
                break;
            case METRIC_AI:
                limit = plan.getAiLimit();
                break;
            default:
                return -1L;
        }
        return limit == null ? -1L : limit;
    }

    /**
     * 额度项中文名，用于超额提示。
     */
    public String metricName(String metric) {
        switch (metric) {
            case METRIC_SEAT:
                return "坐席账号";
            case METRIC_SESSION:
                return "本月会话";
            case METRIC_ORDER:
                return "本月工单";
            case METRIC_KB:
                return "知识库条目";
            case METRIC_AI:
                return "本月 AI 应答";
            default:
                return metric;
        }
    }

    /**
     * 套餐字典（平台端下拉与租户端套餐对比共用）。
     */
    @Transactional(readOnly = true)
    public List<TenantPlan> listAll() {
        return tenantPlanMapper.selectList(new LambdaQueryWrapper<TenantPlan>()
                .orderByAsc(TenantPlan::getSort)
                .orderByAsc(TenantPlan::getId));
    }

    /**
     * 转换为套餐对比项，标记当前套餐与推荐套餐。
     */
    public List<PlanUsageVO.PlanOption> options(String currentCode) {
        List<PlanUsageVO.PlanOption> options = new ArrayList<>();
        for (TenantPlan plan : listEnabled()) {
            options.add(toOption(plan, currentCode));
        }
        return options;
    }

    /**
     * 单个套餐转套餐对比项。
     */
    public PlanUsageVO.PlanOption toOption(TenantPlan plan, String currentCode) {
        PlanUsageVO.PlanOption option = new PlanUsageVO.PlanOption();
        option.setCode(plan.getPlanCode());
        option.setName(plan.getPlanName());
        option.setTagline(plan.getTagline());
        option.setPrice(plan.getPrice() == null ? 0 : plan.getPrice().intValue());
        option.setPeriod(plan.getPeriod());
        option.setRecommended(Integer.valueOf(1).equals(plan.getRecommended()));
        option.setCurrentPlan(plan.getPlanCode() != null && plan.getPlanCode().equalsIgnoreCase(currentCode));
        option.setSeatLimit(intOrZero(plan.getSeatLimit()));
        option.setSessionLimit(intOrZero(plan.getSessionLimit()));
        option.setOrderLimit(intOrZero(plan.getOrderLimit()));
        option.setKbLimit(intOrZero(plan.getKbLimit()));
        option.setAiResolutionLimit(intOrZero(plan.getAiLimit()));
        option.setFeatures(Collections.unmodifiableList(features(plan)));
        return option;
    }

    private int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
