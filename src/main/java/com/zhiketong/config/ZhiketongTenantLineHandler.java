package com.zhiketong.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.zhiketong.security.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 多租户 SQL 处理器：自动为业务表 SQL 追加 {@code tenant_id} 条件，并在 INSERT 时补租户列。
 *
 * <p>系统级表（租户主数据、套餐字典、角色字典）与未进入租户作用域的上下文不做过滤。
 * 业务代码禁止手写 {@code tenant_id} 条件，统一由本处理器注入。</p>
 *
 * @author 智客通开发组
 */
@Component
public class ZhiketongTenantLineHandler implements TenantLineHandler {

    /** 系统级表：不参与租户隔离 */
    private static final Set<String> IGNORED_TABLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("tenant", "tenant_plan", "sys_role")));

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.requireTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContext.isIgnore()) {
            return true;
        }
        return tableName != null && IGNORED_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
