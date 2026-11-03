package com.zhiketong.task;

import com.zhiketong.common.Constants;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.security.TenantContext;
import com.zhiketong.service.OrderSlaService;
import com.zhiketong.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 工单 SLA 超时扫描任务（第四章 4.5）。
 *
 * <p>每轮先抢 Redis 分布式锁，再依次执行：新 SLA 引擎（{@link OrderSlaService#scan} 多级时限/预警/超时/升级），
 * 以及兼容历史工单的「首响超时」「解决超时」扫描；
 * 单条超时由 {@link WorkOrderService#markOverdue(Long, String)} 在独立事务中
 * 条件更新 overdue_flag=0，保证同一超时只处理一次。
 * 排队超时清理由 {@link QueueTimeoutTask} 独立负责。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaScanTask {

    /** 扫描锁过期时间（秒） */
    private static final long LOCK_TTL_SECONDS = 60L;

    private final WorkOrderService workOrderService;
    private final OrderSlaService orderSlaService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 定时扫描：默认每分钟一轮，可通过 zhiketong.sla.scan-cron 覆盖。
     */
    @Scheduled(cron = "${zhiketong.sla.scan-cron:0 * * * * ?}")
    public void scan() {
        String token = UUID.randomUUID().toString().replace("-", "");
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(Constants.SLA_SCAN_LOCK, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("SLA 扫描未获取到分布式锁，本轮跳过");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            // 定时任务无登录态与租户上下文，按系统模式跨租户扫描，逐条再切回工单所属租户
            TenantContext.runSystem(() -> scanAll(start));
        } finally {
            releaseLock(token);
        }
    }

    /**
     * 系统模式下的扫描主体：SLA 引擎 + 历史工单首响/解决超时。
     */
    private void scanAll(long start) {
        LocalDateTime now = LocalDateTime.now();
        // 新 SLA 引擎：按计时记录评估三级时限，触发预警/超时/升级
        try {
            orderSlaService.scan(now);
        } catch (Exception e) {
            log.error("SLA 引擎扫描失败", e);
        }

        List<WorkOrder> firstResponseCandidates = workOrderService.listFirstResponseOverdue(now);
        int firstResponseOverdue = 0;
        for (WorkOrder order : firstResponseCandidates) {
            try {
                Boolean marked = TenantContext.callAs(order.getTenantId(),
                        () -> workOrderService.markOverdue(order.getId(), WorkOrderService.OVERDUE_FIRST_RESPONSE));
                if (Boolean.TRUE.equals(marked)) {
                    firstResponseOverdue++;
                }
            } catch (Exception e) {
                log.error("首响超时处理失败：orderId={}, orderNo={}", order.getId(), order.getOrderNo(), e);
            }
        }

        List<WorkOrder> resolveCandidates = workOrderService.listResolveOverdue(now);
        int resolveOverdue = 0;
        for (WorkOrder order : resolveCandidates) {
            try {
                Boolean marked = TenantContext.callAs(order.getTenantId(),
                        () -> workOrderService.markOverdue(order.getId(), WorkOrderService.OVERDUE_RESOLVE));
                if (Boolean.TRUE.equals(marked)) {
                    resolveOverdue++;
                }
            } catch (Exception e) {
                log.error("解决超时处理失败：orderId={}, orderNo={}", order.getId(), order.getOrderNo(), e);
            }
        }

        log.info("SLA 扫描完成：首响候选 {} 条，新增首响超时 {} 条；解决候选 {} 条，新增解决超时 {} 条；耗时 {}ms",
                firstResponseCandidates.size(), firstResponseOverdue,
                resolveCandidates.size(), resolveOverdue, System.currentTimeMillis() - start);
    }

    /**
     * 仅释放当前实例持有的锁，避免误删其他实例的锁。
     */
    private void releaseLock(String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(Constants.SLA_SCAN_LOCK);
            if (token.equals(current)) {
                stringRedisTemplate.delete(Constants.SLA_SCAN_LOCK);
            }
        } catch (Exception e) {
            log.warn("释放 SLA 扫描锁失败：{}", e.getMessage());
        }
    }
}
