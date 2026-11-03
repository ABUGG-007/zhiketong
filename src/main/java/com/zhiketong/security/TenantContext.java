package com.zhiketong.security;

import java.util.function.Supplier;

/**
 * 租户上下文：保存当前线程处理的租户 ID，供多租户拦截器自动注入 SQL 条件。
 *
 * <p>取值约定：</p>
 * <ul>
 *   <li>普通租户内用户：等于 {@code tenant.id}（当前默认租户为 1）；</li>
 *   <li>平台管理员：{@link #PLATFORM_TENANT_ID}（0），表示跨租户视角，不做租户过滤；</li>
 *   <li>定时任务等系统级批处理：通过 {@link #runSystem(Runnable)} 显式声明，逐条处理时再
 *       用 {@link #runAs(Long, Runnable)} 切到记录所属租户，保证写入的 tenant_id 正确。</li>
 * </ul>
 *
 * <p>上下文为空时视为「未进入租户作用域」（如登录、公开接入接口），
 * 拦截器此时不做租户过滤，由业务代码自行校验租户身份。</p>
 *
 * @author 智客通开发组
 */
public final class TenantContext {

    /** 平台管理员使用的租户 ID，语义为「跨租户 / 平台视角」 */
    public static final long PLATFORM_TENANT_ID = 0L;

    /** 初始化默认租户 ID，与 sql/13_s4a_tenant.sql 预置数据一致 */
    public static final long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> SYSTEM_MODE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setSystem(boolean system) {
        SYSTEM_MODE.set(system);
    }

    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM_MODE.get());
    }

    /**
     * 是否跳过租户过滤：系统批处理、上下文为空、平台管理员三者之一。
     */
    public static boolean isIgnore() {
        Long tenantId = TENANT_ID.get();
        return isSystem() || tenantId == null || tenantId == PLATFORM_TENANT_ID;
    }

    /**
     * 获取当前租户 ID，缺失或平台视角时抛异常，用于必须落到具体租户的写操作。
     */
    public static long requireTenantId() {
        Long tenantId = TENANT_ID.get();
        if (tenantId == null || tenantId == PLATFORM_TENANT_ID) {
            throw new IllegalStateException("当前上下文缺少有效的租户标识，无法执行租户级写操作");
        }
        return tenantId;
    }

    /**
     * 在指定租户作用域内执行，退出后恢复原上下文。
     */
    public static <T> T callAs(Long tenantId, Supplier<T> action) {
        Long previousTenant = TENANT_ID.get();
        Boolean previousSystem = SYSTEM_MODE.get();
        try {
            TENANT_ID.set(tenantId);
            SYSTEM_MODE.remove();
            return action.get();
        } finally {
            restore(previousTenant, previousSystem);
        }
    }

    /**
     * 在指定租户作用域内执行无返回值逻辑。
     */
    public static void runAs(Long tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 以系统模式执行跨租户批处理，退出后恢复原上下文。
     */
    public static void runSystem(Runnable action) {
        Long previousTenant = TENANT_ID.get();
        Boolean previousSystem = SYSTEM_MODE.get();
        try {
            TENANT_ID.remove();
            SYSTEM_MODE.set(true);
            action.run();
        } finally {
            restore(previousTenant, previousSystem);
        }
    }

    /**
     * 清理当前线程上下文，必须在请求 / 消息处理结束时调用，避免线程复用串租户。
     */
    public static void clear() {
        TENANT_ID.remove();
        SYSTEM_MODE.remove();
    }

    private static void restore(Long previousTenant, Boolean previousSystem) {
        if (previousTenant == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(previousTenant);
        }
        if (previousSystem == null) {
            SYSTEM_MODE.remove();
        } else {
            SYSTEM_MODE.set(previousSystem);
        }
    }
}
