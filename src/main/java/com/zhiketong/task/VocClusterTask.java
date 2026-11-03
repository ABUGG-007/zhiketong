package com.zhiketong.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.Constants;
import com.zhiketong.dto.voc.VocClusterResultVO;
import com.zhiketong.entity.Tenant;
import com.zhiketong.mapper.TenantMapper;
import com.zhiketong.security.TenantContext;
import com.zhiketong.service.VocService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 客户之声聚类任务（S6-C，DB-06）。
 *
 * <p>默认每 30 分钟一轮（{@code zhiketong.voc.cluster-cron} 可覆盖）：
 * 抢 Redis 分布式锁 → 系统模式遍历启用租户 → 逐租户切回租户作用域执行聚类。
 * 锁过期时间 10 分钟，短于默认周期且长于单轮耗时，避免多实例重复聚类。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VocClusterTask {

    /** 聚类锁过期时间（秒） */
    private static final long LOCK_TTL_SECONDS = 600L;

    private final VocService vocService;
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 是否启用定时聚类 */
    @Value("${zhiketong.voc.enabled:true}")
    private boolean enabled;

    /**
     * 定时聚类：默认每 30 分钟一轮。
     */
    @Scheduled(cron = "${zhiketong.voc.cluster-cron:0 */30 * * * ?}")
    public void scan() {
        if (!enabled) {
            return;
        }
        VocClusterResultVO result = clusterNow();
        if (!result.isLocked()) {
            log.debug("客户之声聚类未获取到分布式锁，本轮跳过");
            return;
        }
        log.info("客户之声聚类完成：扫描 {} 条，归入 {} 条，新建主题 {} 个，耗时 {}ms",
                result.getScannedCount(), result.getAssignedCount(),
                result.getCreatedCount(), result.getDurationMs());
    }

    /**
     * 立即执行一轮聚类（R-213 管理端手动触发复用同一入口）。
     *
     * @return 本轮执行结果
     */
    public VocClusterResultVO clusterNow() {
        VocClusterResultVO result = new VocClusterResultVO();
        String token = UUID.randomUUID().toString().replace("-", "");
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(Constants.VOC_CLUSTER_LOCK, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            result.setLocked(false);
            result.setClusterTime(LocalDateTime.now());
            return result;
        }
        try {
            Long tenantId = TenantContext.getTenantId();
            boolean scoped = tenantId != null
                    && tenantId != TenantContext.PLATFORM_TENANT_ID
                    && !TenantContext.isSystem();
            if (scoped) {
                // 管理端手动触发：只处理当前租户，避免越权写入其他租户数据
                result = vocService.cluster(0L);
            } else {
                // 定时任务无登录态与租户上下文，按系统模式跨租户遍历
                AtomicReference<VocClusterResultVO> holder = new AtomicReference<>();
                TenantContext.runSystem(() -> holder.set(clusterAll()));
                result = holder.get() == null ? result : holder.get();
            }
            result.setLocked(true);
            return result;
        } finally {
            releaseLock(token);
        }
    }

    /**
     * 系统模式下的聚类主体：遍历启用租户，逐个租户聚类。
     */
    private VocClusterResultVO clusterAll() {
        VocClusterResultVO total = new VocClusterResultVO();
        total.setClusterTime(LocalDateTime.now());
        List<Tenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getStatus, Constants.TENANT_STATUS_ENABLED)
                .select(Tenant::getId));
        for (Tenant tenant : tenants) {
            try {
                VocClusterResultVO one = TenantContext.callAs(tenant.getId(), () -> vocService.cluster(0L));
                total.setScannedCount(total.getScannedCount() + one.getScannedCount());
                total.setAssignedCount(total.getAssignedCount() + one.getAssignedCount());
                total.setCreatedCount(total.getCreatedCount() + one.getCreatedCount());
                total.setSkippedCount(total.getSkippedCount() + one.getSkippedCount());
                total.setTopicCount(one.getTopicCount());
                total.setDurationMs(total.getDurationMs() + one.getDurationMs());
            } catch (Exception e) {
                log.error("租户客户之声聚类失败：tenantId={}, error={}", tenant.getId(), e.getMessage());
            }
        }
        return total;
    }

    /**
     * 仅释放当前实例持有的锁，避免误删其他实例的锁。
     */
    private void releaseLock(String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(Constants.VOC_CLUSTER_LOCK);
            if (token.equals(current)) {
                stringRedisTemplate.delete(Constants.VOC_CLUSTER_LOCK);
            }
        } catch (Exception e) {
            log.warn("释放客户之声聚类锁失败：{}", e.getMessage());
        }
    }
}
