package com.zhiketong.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.Constants;
import com.zhiketong.dto.alert.AlertScanResultVO;
import com.zhiketong.entity.Tenant;
import com.zhiketong.mapper.TenantMapper;
import com.zhiketong.security.TenantContext;
import com.zhiketong.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 告警扫描任务（S6-B，AS-11）。
 *
 * <p>默认每分钟一轮（{@code zhiketong.alert.scan-cron} 可覆盖）：
 * 抢 Redis 分布式锁 → 系统模式跨租户遍历启用租户 → 逐租户切回租户作用域执行规则评估。
 * 锁过期时间短于扫描周期，单轮内完成，不会与下一轮重叠。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScanTask {

    /** 扫描锁过期时间（秒） */
    private static final long LOCK_TTL_SECONDS = 120L;

    private final AlertService alertService;
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 定时扫描：默认每分钟一轮。
     */
    @Scheduled(cron = "${zhiketong.alert.scan-cron:0 * * * * ?}")
    public void scan() {
        AlertScanResultVO result = scanNow();
        if (!result.isLocked()) {
            log.debug("告警扫描未获取到分布式锁，本轮跳过");
            return;
        }
        log.info("告警扫描完成：{}", result.getMessage());
    }

    /**
     * 立即执行一轮扫描（R-187 管理端手动触发复用同一入口）。
     *
     * @return 本轮执行结果
     */
    public AlertScanResultVO scanNow() {
        AlertScanResultVO result = new AlertScanResultVO();
        String token = UUID.randomUUID().toString().replace("-", "");
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(Constants.ALERT_SCAN_LOCK, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            result.setLocked(false);
            result.setMessage("已有告警扫描任务在执行，请稍后再试");
            return result;
        }
        long start = System.currentTimeMillis();
        try {
            AtomicReference<AlertScanResultVO> holder = new AtomicReference<>();
            // 定时任务无登录态与租户上下文，按系统模式跨租户遍历，逐租户再切回作用域
            TenantContext.runSystem(() -> holder.set(scanAll()));
            AlertScanResultVO scanned = holder.get();
            if (scanned != null) {
                result = scanned;
            }
            result.setLocked(true);
            result.setCostMillis(System.currentTimeMillis() - start);
            return result;
        } finally {
            releaseLock(token);
        }
    }

    /**
     * 系统模式下的扫描主体：遍历启用租户，逐个租户评估规则。
     */
    private AlertScanResultVO scanAll() {
        AlertScanResultVO total = new AlertScanResultVO();
        total.setScanTime(LocalDateTime.now());
        List<Tenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getStatus, Constants.TENANT_STATUS_ENABLED)
                .select(Tenant::getId));
        for (Tenant tenant : tenants) {
            try {
                AlertScanResultVO one = TenantContext.callAs(tenant.getId(), alertService::scan);
                total.setRuleCount(total.getRuleCount() + one.getRuleCount());
                total.setCreatedCount(total.getCreatedCount() + one.getCreatedCount());
                total.setRepeatedCount(total.getRepeatedCount() + one.getRepeatedCount());
                total.setSuppressedCount(total.getSuppressedCount() + one.getSuppressedCount());
                total.setAutoClosedCount(total.getAutoClosedCount() + one.getAutoClosedCount());
            } catch (Exception e) {
                log.error("租户告警扫描失败：tenantId={}, error={}", tenant.getId(), e.getMessage());
            }
        }
        total.setMessage("扫描租户 " + tenants.size() + " 个，规则 " + total.getRuleCount()
                + " 条，新增 " + total.getCreatedCount() + " 条，复发 " + total.getRepeatedCount()
                + " 条，冷却抑制 " + total.getSuppressedCount() + " 条，自动关闭 "
                + total.getAutoClosedCount() + " 条");
        return total;
    }

    /**
     * 仅释放当前实例持有的锁，避免误删其他实例的锁。
     */
    private void releaseLock(String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(Constants.ALERT_SCAN_LOCK);
            if (token.equals(current)) {
                stringRedisTemplate.delete(Constants.ALERT_SCAN_LOCK);
            }
        } catch (Exception e) {
            log.warn("释放告警扫描锁失败：{}", e.getMessage());
        }
    }
}
