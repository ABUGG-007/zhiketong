package com.zhiketong.task;

import com.zhiketong.common.Constants;
import com.zhiketong.service.OrderRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 工单规则到期队列扫描任务（WO-16 时间驱动，S3-C）。
 *
 * <p>每分钟抢 Redis 分布式锁后，取 {@code order_rule_run} 中 {@code planned_time} 到期的 PENDING 记录，
 * 委托 {@link OrderRuleService#runDue(LocalDateTime)} 逐条在其独立事务中执行并回写终态。
 * 单条失败不影响其余记录；锁语义与 {@link SlaScanTask} 保持一致。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRuleTask {

    /** 扫描锁过期时间（秒） */
    private static final long LOCK_TTL_SECONDS = 60L;

    private final OrderRuleService orderRuleService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 规则引擎开关：关闭时不扫描到期队列 */
    @Value("${zhiketong.rule.enabled:true}")
    private boolean enabled;

    /**
     * 定时扫描：默认每分钟一轮，可通过 zhiketong.rule.scan-cron 覆盖。
     */
    @Scheduled(cron = "${zhiketong.rule.scan-cron:0 * * * * ?}")
    public void scan() {
        if (!enabled) {
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(Constants.RULE_SCAN_LOCK, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("工单规则扫描未获取到分布式锁，本轮跳过");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            int executed = orderRuleService.runDue(LocalDateTime.now());
            if (executed > 0) {
                log.info("工单规则到期队列扫描完成：执行 {} 条，耗时 {}ms",
                        executed, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            log.error("工单规则到期队列扫描失败", e);
        } finally {
            releaseLock(token);
        }
    }

    /**
     * 仅释放当前实例持有的锁，避免误删其他实例的锁。
     */
    private void releaseLock(String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(Constants.RULE_SCAN_LOCK);
            if (token.equals(current)) {
                stringRedisTemplate.delete(Constants.RULE_SCAN_LOCK);
            }
        } catch (Exception e) {
            log.warn("释放工单规则扫描锁失败：{}", e.getMessage());
        }
    }
}
