package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.config.properties.EmbedProperties;
import com.zhiketong.dto.platform.TenantCreateRequest;
import com.zhiketong.dto.platform.TenantDetailVO;
import com.zhiketong.dto.platform.TenantExpireRequest;
import com.zhiketong.dto.platform.TenantPlanChangeRequest;
import com.zhiketong.dto.platform.TenantStatusRequest;
import com.zhiketong.dto.platform.TenantUpdateRequest;
import com.zhiketong.dto.platform.TenantVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.Tenant;
import com.zhiketong.entity.TenantEmbedConfig;
import com.zhiketong.entity.TenantPlan;
import com.zhiketong.entity.TenantUsage;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.RoleCode;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.TenantEmbedConfigMapper;
import com.zhiketong.mapper.TenantMapper;
import com.zhiketong.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台端租户管理服务（S4-B，PL-02/PL-03）。
 *
 * <p>定位：平台管理员（tenant_id=0）面向所有租户的开通、停复、套餐变更、
 * 续期与用量查看。开通动作一次性完成「租户 + 商户管理员 + 网页接入配置 +
 * 当月用量快照」四件事，保证新租户立即可用。</p>
 *
 * <p>所有写入都在平台视角执行；涉及租户内数据（账号、接入配置、快照）时
 * 显式切到目标租户上下文，确保 tenant_id 由拦截器正确落库。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    /** 商户管理员与接入配置的默认值 */
    private static final String DEFAULT_THEME_COLOR = "#4f46e5";

    /** 默认试用期（天） */
    private static final long TRIAL_DAYS = 14L;

    /** 默认付费周期（天），与月付套餐对齐 */
    private static final long PAID_CYCLE_DAYS = 365L;

    /** 试用套餐编码 */
    private static final String PLAN_TRIAL = "trial";

    /** 审计对象类型：租户 */
    private static final String TARGET_TENANT = "TENANT";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantMapper tenantMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final TenantEmbedConfigMapper tenantEmbedConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final TenantPlanService tenantPlanService;
    private final TenantUsageService tenantUsageService;
    private final EmbedProperties embedProperties;
    private final AuditLogService auditLogService;

    /**
     * 租户分页列表：keyword 匹配租户名称/编码/联系人，支持状态与套餐过滤。
     */
    @Transactional(readOnly = true)
    public PageResult<TenantVO> page(int pageNum, int pageSize, String keyword, String status, String planCode) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Tenant::getTenantName, kw)
                    .or().like(Tenant::getTenantCode, kw)
                    .or().like(Tenant::getContactName, kw)
                    .or().like(Tenant::getContactPhone, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Tenant::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(planCode)) {
            wrapper.eq(Tenant::getPlanCode, planCode.trim());
        }
        wrapper.orderByDesc(Tenant::getId);

        Page<Tenant> page = tenantMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, TenantPlan> plans = planMap();
        List<TenantVO> list = new ArrayList<>(page.getRecords().size());
        for (Tenant tenant : page.getRecords()) {
            list.add(toVO(tenant, plans.get(tenant.getPlanCode())));
        }
        return PageResult.of(page, list);
    }

    /**
     * 租户详情：资料 + 管理员账号 + 五项额度实时水位 + 最近快照时间。
     */
    @Transactional(readOnly = true)
    public TenantDetailVO detail(Long id) {
        Tenant tenant = requireTenant(id);
        TenantPlan plan = tenantPlanService.find(tenant.getPlanCode());
        TenantDetailVO vo = new TenantDetailVO();
        fill(vo, tenant, plan);

        TenantUsageService.Usage usage = tenantUsageService.usage(id);
        vo.setQuotas(tenantUsageService.quotaItems(plan, usage));
        TenantUsage snapshot = tenantUsageService.latestSnapshot(id);
        vo.setSnapshotTime(snapshot == null ? null : snapshot.getSnapshotTime());

        SysUser admin = TenantContext.callAs(id, () -> sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRoleCode, Constants.ROLE_ADMIN)
                        .orderByAsc(SysUser::getId)
                        .last("LIMIT 1")));
        if (admin != null) {
            vo.setAdminUsername(admin.getUsername());
            vo.setAdminNickname(admin.getNickname());
        }
        return vo;
    }

    /**
     * 开通租户：租户 + 商户管理员 + 网页接入配置 + 当月用量快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDetailVO create(TenantCreateRequest request) {
        TenantPlan plan = tenantPlanService.requireEnabled(request.getPlanCode());
        String tenantCode = StringUtils.hasText(request.getTenantCode())
                ? request.getTenantCode().trim() : nextTenantCode();
        if (tenantMapper.selectCount(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenantCode)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "租户编码已存在：" + tenantCode);
        }

        String adminUsername = request.getAdminUsername().trim();
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, adminUsername)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "登录名已被占用：" + adminUsername);
        }

        Tenant tenant = new Tenant();
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(request.getTenantName().trim());
        tenant.setStatus(Constants.TENANT_STATUS_ENABLED);
        tenant.setPlanCode(plan.getPlanCode());
        tenant.setExpireTime(request.getExpireTime() == null
                ? defaultExpireTime(plan) : request.getExpireTime());
        tenant.setContactName(trimToNull(request.getContactName()));
        tenant.setContactPhone(trimToNull(request.getContactPhone()));
        tenant.setContactEmail(trimToNull(request.getContactEmail()));
        tenant.setRemark(trimToNull(request.getRemark()));
        try {
            tenantMapper.insert(tenant);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "租户编码已存在：" + tenantCode);
        }

        Long tenantId = tenant.getId();
        // 账号、接入配置、用量快照都属于业务表，必须切到新租户上下文写入
        TenantContext.runAs(tenantId, () -> {
            SysUser admin = new SysUser();
            admin.setTenantId(tenantId);
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
            admin.setNickname(StringUtils.hasText(request.getAdminNickname())
                    ? request.getAdminNickname().trim() : adminUsername);
            admin.setRoleCode(RoleCode.ADMIN.getCode());
            admin.setStatus(Constants.STATUS_ENABLED);
            sysUserMapper.insert(admin);
            initEmbedConfig(tenantId, tenantCode, tenant.getTenantName());
        });
        tenantUsageService.saveSnapshot(tenantId);

        log.info("开通租户成功：tenantId={}, tenantCode={}, planCode={}, admin={}",
                tenantId, tenantCode, plan.getPlanCode(), adminUsername);

        auditLogService.record(AuditModule.TENANT, AuditAction.CREATE)
                .tenant(tenantId)
                .target(TARGET_TENANT, tenantId, tenant.getTenantName())
                .detail("开通租户「" + tenant.getTenantName() + "」（编码 " + tenantCode
                        + "，套餐 " + plan.getPlanName() + "，管理员 " + adminUsername + "）")
                .after(tenant)
                .save();

        TenantDetailVO vo = new TenantDetailVO();
        fill(vo, tenant, plan);
        vo.setAdminUsername(adminUsername);
        vo.setAdminNickname(StringUtils.hasText(request.getAdminNickname())
                ? request.getAdminNickname().trim() : adminUsername);
        vo.setQuotas(tenantUsageService.quotaItems(plan, tenantUsageService.usage(tenantId)));
        return vo;
    }

    /**
     * 编辑租户资料与到期时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDetailVO update(Long id, TenantUpdateRequest request) {
        Tenant tenant = requireTenant(id);
        LambdaUpdateWrapper<Tenant> wrapper = new LambdaUpdateWrapper<Tenant>().eq(Tenant::getId, id);
        boolean changed = false;
        if (StringUtils.hasText(request.getTenantName())) {
            wrapper.set(Tenant::getTenantName, request.getTenantName().trim());
            changed = true;
        }
        if (request.getContactName() != null) {
            wrapper.set(Tenant::getContactName, trimToNull(request.getContactName()));
            changed = true;
        }
        if (request.getContactPhone() != null) {
            wrapper.set(Tenant::getContactPhone, trimToNull(request.getContactPhone()));
            changed = true;
        }
        if (request.getContactEmail() != null) {
            wrapper.set(Tenant::getContactEmail, trimToNull(request.getContactEmail()));
            changed = true;
        }
        if (request.getRemark() != null) {
            wrapper.set(Tenant::getRemark, trimToNull(request.getRemark()));
            changed = true;
        }
        if (request.getExpireTime() != null) {
            wrapper.set(Tenant::getExpireTime, request.getExpireTime());
            changed = true;
        }
        if (changed) {
            tenantMapper.update(null, wrapper);
            log.info("编辑租户成功：tenantId={}", id);
            auditLogService.record(AuditModule.TENANT, AuditAction.UPDATE)
                    .tenant(id)
                    .target(TARGET_TENANT, id, tenant.getTenantName())
                    .detail("编辑租户资料：" + tenant.getTenantName())
                    .before(tenant)
                    .after(tenantMapper.selectById(id))
                    .save();
        }
        return detail(id);
    }

    /**
     * 变更租户状态：停用/到期后租户内账号立即无法登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDetailVO updateStatus(Long id, TenantStatusRequest request) {
        Tenant tenant = requireTenant(id);
        String status = request.getStatus().trim().toUpperCase();
        if (status.equals(tenant.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "租户已处于该状态，无需重复操作");
        }
        tenantMapper.update(null, new LambdaUpdateWrapper<Tenant>()
                .eq(Tenant::getId, id)
                .set(Tenant::getStatus, status));
        log.info("变更租户状态成功：tenantId={}, {} -> {}", id, tenant.getStatus(), status);
        auditLogService.record(AuditModule.TENANT,
                        Constants.TENANT_STATUS_ENABLED.equals(status) ? AuditAction.ENABLE : AuditAction.DISABLE)
                .tenant(id)
                .target(TARGET_TENANT, id, tenant.getTenantName())
                .detail("租户「" + tenant.getTenantName() + "」状态 " + tenant.getStatus() + " -> " + status)
                .before(tenant)
                .after(tenantMapper.selectById(id))
                .save();
        return detail(id);
    }

    /**
     * 变更租户套餐：降级时校验坐席与知识库存量不超新额度。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDetailVO changePlan(Long id, TenantPlanChangeRequest request) {
        Tenant tenant = requireTenant(id);
        TenantPlan target = tenantPlanService.requireEnabled(request.getPlanCode());
        if (target.getPlanCode().equals(tenant.getPlanCode())) {
            throw new BusinessException(ResultCode.CONFLICT, "租户已使用该套餐，无需重复变更");
        }
        TenantUsageService.Usage usage = tenantUsageService.usage(id);
        assertDowngrade(target, usage, TenantPlanService.METRIC_SEAT);
        assertDowngrade(target, usage, TenantPlanService.METRIC_KB);

        tenantMapper.update(null, new LambdaUpdateWrapper<Tenant>()
                .eq(Tenant::getId, id)
                .set(Tenant::getPlanCode, target.getPlanCode()));
        log.info("变更租户套餐成功：tenantId={}, {} -> {}", id, tenant.getPlanCode(), target.getPlanCode());
        auditLogService.record(AuditModule.TENANT, AuditAction.CHANGE_PLAN)
                .tenant(id)
                .target(TARGET_TENANT, id, tenant.getTenantName())
                .detail("租户「" + tenant.getTenantName() + "」套餐 " + tenant.getPlanCode()
                        + " -> " + target.getPlanCode())
                .before(tenant)
                .after(tenantMapper.selectById(id))
                .save();
        return detail(id);
    }

    /**
     * 续期：设置新的到期时间，已到期租户自动恢复为正常状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDetailVO renew(Long id, TenantExpireRequest request) {
        Tenant tenant = requireTenant(id);
        LocalDateTime expireTime = request.getExpireTime();
        if (!expireTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "到期时间必须晚于当前时间");
        }
        LambdaUpdateWrapper<Tenant> wrapper = new LambdaUpdateWrapper<Tenant>()
                .eq(Tenant::getId, id)
                .set(Tenant::getExpireTime, expireTime);
        if (Constants.TENANT_STATUS_EXPIRED.equalsIgnoreCase(tenant.getStatus())) {
            wrapper.set(Tenant::getStatus, Constants.TENANT_STATUS_ENABLED);
        }
        tenantMapper.update(null, wrapper);
        log.info("租户续期成功：tenantId={}, expireTime={}", id, expireTime);
        auditLogService.record(AuditModule.TENANT, AuditAction.RENEW)
                .tenant(id)
                .target(TARGET_TENANT, id, tenant.getTenantName())
                .detail("租户「" + tenant.getTenantName() + "」续期至 " + expireTime)
                .before(tenant)
                .after(tenantMapper.selectById(id))
                .save();
        return detail(id);
    }

    /**
     * 逻辑删除租户：默认演示租户与存在进行中会话的租户不允许删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Tenant tenant = requireTenant(id);
        if (id == TenantContext.DEFAULT_TENANT_ID) {
            throw new BusinessException(ResultCode.FORBIDDEN, "默认演示租户不允许删除");
        }
        Long active = TenantContext.callAs(id, () -> chatSessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .in(ChatSession::getStatus,
                                Arrays.asList(SessionStatus.AI.getCode(),
                                        SessionStatus.WAITING.getCode(),
                                        SessionStatus.HUMAN.getCode()))));
        if (active != null && active > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该租户存在进行中的会话，请先结束会话后再删除");
        }
        // 接入配置随租户一起停用，避免删除后仍可通过公开接口提问
        TenantContext.runAs(id, () -> tenantEmbedConfigMapper.update(null,
                new LambdaUpdateWrapper<TenantEmbedConfig>()
                        .set(TenantEmbedConfig::getStatus, Constants.STATUS_DISABLED)));
        tenantMapper.deleteById(id);
        log.info("删除租户成功：tenantId={}", id);
        auditLogService.record(AuditModule.TENANT, AuditAction.DELETE)
                .tenant(id)
                .target(TARGET_TENANT, id, tenant.getTenantName())
                .detail("删除租户「" + tenant.getTenantName() + "」（编码 " + tenant.getTenantCode() + "）")
                .before(tenant)
                .save();
    }

    /**
     * 手动刷新指定租户的当月用量快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantUsage refreshSnapshot(Long id) {
        requireTenant(id);
        return tenantUsageService.saveSnapshot(id);
    }

    /**
     * 租户主数据查询，不存在抛 404。
     */
    private Tenant requireTenant(Long id) {
        Tenant tenant = id == null ? null : tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "租户不存在");
        }
        return tenant;
    }

    /**
     * 降级校验：仅对存量指标（坐席、知识库）拦截，月度累计指标允许自然归零。
     */
    private void assertDowngrade(TenantPlan target, TenantUsageService.Usage usage, String metric) {
        long limit = tenantPlanService.limitOf(target, metric);
        if (limit < 0) {
            return;
        }
        long used = usage.get(metric);
        if (used > limit) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "租户当前" + tenantPlanService.metricName(metric) + "用量 " + used
                            + " 已超过「" + target.getPlanName() + "」上限 " + limit
                            + "，请先清理后再降级");
        }
    }

    /**
     * 初始化租户网页接入配置，保证开通后即可对外接入悬浮球。
     */
    private void initEmbedConfig(Long tenantId, String tenantCode, String tenantName) {
        TenantEmbedConfig config = new TenantEmbedConfig();
        config.setTenantId(tenantId);
        config.setTenantCode(tenantCode);
        config.setTenantName(tenantName);
        config.setAppKey("zk_" + randomHex(6));
        config.setAppSecret(randomHex(24));
        config.setBrandName(tenantName);
        config.setThemeColor(DEFAULT_THEME_COLOR);
        config.setPosition("right");
        config.setBadge(1);
        config.setRateLimitPerMinute(embedProperties.getDefaultRateLimitPerMinute());
        config.setStatus(Constants.STATUS_ENABLED);
        tenantEmbedConfigMapper.insert(config);
    }

    /**
     * 按套餐周期推导默认到期时间：试用版 14 天，其余按 1 年。
     */
    private LocalDateTime defaultExpireTime(TenantPlan plan) {
        long days = PLAN_TRIAL.equalsIgnoreCase(plan.getPlanCode()) ? TRIAL_DAYS : PAID_CYCLE_DAYS;
        return LocalDateTime.now().plusDays(days);
    }

    /**
     * 生成不重复的租户编码：zk + 6 位十六进制。
     */
    private String nextTenantCode() {
        for (int i = 0; i < 5; i++) {
            String code = "zk" + randomHex(3);
            if (tenantMapper.selectCount(new LambdaQueryWrapper<Tenant>()
                    .eq(Tenant::getTenantCode, code)) == 0) {
                return code;
            }
        }
        throw new BusinessException("租户编码生成失败，请手动指定");
    }

    private Map<String, TenantPlan> planMap() {
        Map<String, TenantPlan> map = new HashMap<>();
        for (TenantPlan plan : tenantPlanService.listAll()) {
            map.put(plan.getPlanCode(), plan);
        }
        return map;
    }

    private TenantVO toVO(Tenant tenant, TenantPlan plan) {
        TenantVO vo = new TenantVO();
        fill(vo, tenant, plan);
        return vo;
    }

    /**
     * 租户主数据 + 套餐额度上限填充到 VO。
     */
    private void fill(TenantVO vo, Tenant tenant, TenantPlan plan) {
        vo.setId(tenant.getId());
        vo.setTenantCode(tenant.getTenantCode());
        vo.setTenantName(tenant.getTenantName());
        vo.setStatus(tenant.getStatus());
        vo.setPlanCode(tenant.getPlanCode());
        vo.setPlanName(plan == null ? tenant.getPlanCode() : plan.getPlanName());
        vo.setExpireTime(tenant.getExpireTime());
        vo.setExpired(tenant.getExpireTime() != null
                && tenant.getExpireTime().isBefore(LocalDateTime.now()));
        vo.setContactName(tenant.getContactName());
        vo.setContactPhone(tenant.getContactPhone());
        vo.setContactEmail(tenant.getContactEmail());
        vo.setRemark(tenant.getRemark());
        vo.setCreateTime(tenant.getCreateTime());
        if (plan != null) {
            vo.setSeatLimit(plan.getSeatLimit());
            vo.setSessionLimit(plan.getSessionLimit());
            vo.setOrderLimit(plan.getOrderLimit());
            vo.setKbLimit(plan.getKbLimit());
            vo.setAiLimit(plan.getAiLimit());
        }
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte value : buffer) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
