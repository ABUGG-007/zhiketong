package com.zhiketong.task;

import com.zhiketong.common.Constants;
import com.zhiketong.dto.quality.QualityScanResultVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.security.TenantContext;
import com.zhiketong.service.QualityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全量自动质检任务（S5-A，QC-01）。
 *
 * <p>默认每 10 分钟一轮（{@code zhiketong.quality.scan-cron} 可覆盖）：
 * 抢 Redis 分布式锁 → 系统模式跨租户捞出「已关闭且尚未质检」的会话 →
 * 逐条切回会话所属租户执行质检。已产生记录的会话不会重复入集，
 * 因此重复触发不会重复消耗大模型额度。</p>
 *
 * <p>单轮最多处理 {@value #BATCH_LIMIT} 条，避免长事务式占用与锁过期；
 * 积压会话会在后续轮次继续消化。质检失败只记日志并继续下一条，不阻塞整轮。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityScanTask {

    /** 扫描锁过期时间（秒），需大于单轮处理耗时 */
    private static final long LOCK_TTL_SECONDS = 300L;

    /** 单轮最多处理的会话条数 */
    private static final int BATCH_LIMIT = 20;

    private final QualityService qualityService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 定时扫描：默认每 10 分钟一轮。
     */
    @Scheduled(cron = "${zhiketong.quality.scan-cron:0 */10 * * * ?}")
    public void scan() {
        QualityScanResultVO result = scanNow();
        if (!result.isLocked()) {
            log.debug("质检扫描未获取到分布式锁，本轮跳过");
            return;
        }
        log.info("质检扫描完成：候选 {} 条，成功 {} 条，失败 {} 条",
                result.getCandidateCount(), result.getProcessedCount(), result.getFailedCount());
    }

    /**
     * 立即执行一轮质检（R-170 手动触发复用同一入口，保证与定时任务口径一致）。
     *
     * @return 本轮执行结果
     */
    public QualityScanResultVO scanNow() {
        QualityScanResultVO result = new QualityScanResultVO();
        String token = UUID.randomUUID().toString().replace("-", "");
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(Constants.QUALITY_SCAN_LOCK, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            result.setLocked(false);
            result.setMessage("已有质检任务在执行，请稍后再试");
            return result;
        }
        long start = System.currentTimeMillis();
        try {
            AtomicReference<QualityScanResultVO> holder = new AtomicReference<>();
            // 定时任务无登录态与租户上下文，按系统模式跨租户捞取，逐条再切回所属租户
            TenantContext.runSystem(() -> holder.set(scanAll()));
            QualityScanResultVO scanned = holder.get();
            if (scanned != null) {
                result = scanned;
            }
            result.setLocked(true);
            log.info("质检扫描耗时 {}ms", System.currentTimeMillis() - start);
            return result;
        } finally {
            releaseLock(token);
        }
    }

    /**
     * 系统模式下的扫描主体：捞候选 + 逐条切租户质检。
     */
    private QualityScanResultVO scanAll() {
        QualityScanResultVO result = new QualityScanResultVO();
        List<ChatSession> sessions = qualityService.listPendingQualitySessions(BATCH_LIMIT);
        result.setCandidateCount(sessions.size());
        int processed = 0;
        int failed = 0;
        for (ChatSession session : sessions) {
            try {
                TenantContext.callAs(session.getTenantId(),
                        () -> qualityService.evaluate(session.getId(), null));
                processed++;
            } catch (Exception e) {
                failed++;
                log.error("质检失败：sessionId={}, tenantId={}, error={}",
                        session.getId(), session.getTenantId(), e.getMessage());
            }
        }
        result.setProcessedCount(processed);
        result.setFailedCount(failed);
        result.setMessage("候选 " + sessions.size() + " 条，成功 " + processed + " 条，失败 " + failed + " 条");
        return result;
    }

    /**
     * 仅释放当前实例持有的锁，避免误删其他实例的锁。
     */
    private void releaseLock(String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(Constants.QUALITY_SCAN_LOCK);
            if (token.equals(current)) {
                stringRedisTemplate.delete(Constants.QUALITY_SCAN_LOCK);
            }
        } catch (Exception e) {
            log.warn("释放质检扫描锁失败：{}", e.getMessage());
        }
    }
}
