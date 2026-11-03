package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.entity.Tenant;
import com.zhiketong.entity.TenantPlan;
import com.zhiketong.mapper.TenantMapper;
import com.zhiketong.mapper.TenantPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 租户与套餐服务：租户可用性校验、套餐权益读取。
 *
 * <p>租户状态与到期时间共同决定可用性：状态非 {@code ENABLED} 或到期时间已过一律拦截，
 * 平台管理员（tenant_id=0）不参与租户校验。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;
    private final TenantPlanMapper tenantPlanMapper;

    /**
     * 按 ID 查询租户，不做可用性校验。
     */
    public Tenant getById(Long tenantId) {
        return tenantId == null ? null : tenantMapper.selectById(tenantId);
    }

    /**
     * 校验租户可用：存在、已开通、未到期。不可用时抛出业务异常。
     */
    public Tenant requireUsable(Long tenantId) {
        Tenant tenant = getById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "租户不存在或已被删除，请联系平台管理员");
        }
        if (!Constants.TENANT_STATUS_ENABLED.equalsIgnoreCase(tenant.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    Constants.TENANT_STATUS_EXPIRED.equalsIgnoreCase(tenant.getStatus())
                            ? "租户服务已到期，请联系平台管理员续费"
                            : "租户已被停用，请联系平台管理员");
        }
        if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "租户服务已到期，请联系平台管理员续费");
        }
        return tenant;
    }

    /**
     * 按租户编码查询（网页接入等公开入口使用）。
     */
    public Tenant findByCode(String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            return null;
        }
        return tenantMapper.selectOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenantCode.trim())
                .last("LIMIT 1"));
    }

    /**
     * 读取租户当前套餐，套餐缺失时抛出业务异常。
     */
    public TenantPlan planOf(Tenant tenant) {
        if (tenant == null) {
            throw new BusinessException("租户不存在，无法读取套餐权益");
        }
        TenantPlan plan = tenantPlanMapper.selectOne(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getPlanCode, tenant.getPlanCode())
                .last("LIMIT 1"));
        if (plan == null) {
            throw new BusinessException("租户套餐配置缺失：" + tenant.getPlanCode());
        }
        return plan;
    }
}
