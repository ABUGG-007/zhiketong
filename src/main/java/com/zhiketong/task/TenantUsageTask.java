package com.zhiketong.task;

import com.zhiketong.service.TenantUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 租户用量快照任务（S4-B，PL-04）。
 *
 * <p>每日 00:10 聚合全部启用租户的当月用量写入 {@code tenant_usage}，
 * 供趋势展示与对账使用。快照不参与超额拦截（拦截读取实时 COUNT），
 * 因此重复执行是幂等的，单租户失败不影响其他租户。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantUsageTask {

    private final TenantUsageService tenantUsageService;

    /**
     * 每日用量聚合：默认 00:10 执行，可通过 zhiketong.tenant.usage-cron 覆盖。
     */
    @Scheduled(cron = "${zhiketong.tenant.usage-cron:0 10 0 * * ?}")
    public void snapshot() {
        long start = System.currentTimeMillis();
        try {
            int count = tenantUsageService.saveAllSnapshots();
            log.info("租户用量快照任务完成：{} 个租户，耗时 {}ms", count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("租户用量快照任务失败", e);
        }
    }
}
