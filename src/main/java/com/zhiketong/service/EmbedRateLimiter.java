package com.zhiketong.service;

import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.entity.TenantEmbedConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 网页接入限流：按「租户 + 来源 IP」计数。
 *
 * <p>生产路径使用 Redis 固定窗口计数（{@code INCR + EXPIRE}），多实例部署共享同一份计数；
 * Redis 不可用时退化为进程内滑动窗口，保证单机演示环境仍能限制匿名流量。</p>
 *
 * <p>令牌申请与知识库问答分别计数：令牌申请阈值固定 60 次/分钟，
 * 问答阈值取租户配置（默认 30 次/分钟）。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbedRateLimiter {

    /** 令牌申请每分钟上限（与问答阈值解耦，避免刷新页面被误伤） */
    private static final int TOKEN_LIMIT_PER_MINUTE = 60;

    /** 限流窗口（毫秒） */
    private static final long WINDOW_MILLIS = 60_000L;

    /** 本地降级最多跟踪的 key 数，防止内存无限增长 */
    private static final int LOCAL_TRACK_LIMIT = 5000;

    private final StringRedisTemplate stringRedisTemplate;

    /** 本地降级用：key → 请求时间窗口 */
    private final Map<String, Deque<Long>> localBuckets = new ConcurrentHashMap<>();

    /**
     * 校验问答频率。
     */
    public void checkAnswer(TenantEmbedConfig config, String ip) {
        int limit = config.getRateLimitPerMinute() == null
                ? 30 : Math.max(1, config.getRateLimitPerMinute());
        check("answer", config.getAppKey(), ip, limit, "提问过于频繁，请稍后再试");
    }

    /**
     * 校验令牌申请频率。
     */
    public void checkToken(TenantEmbedConfig config, String ip) {
        check("token", config.getAppKey(), ip, TOKEN_LIMIT_PER_MINUTE, "接入令牌申请过于频繁，请稍后再试");
    }

    private void check(String scope, String appKey, String ip, int limit, String message) {
        String clientIp = StringUtils.hasText(ip) ? ip.trim() : "unknown";
        long now = System.currentTimeMillis();
        long minute = now / WINDOW_MILLIS;
        String key = Constants.EMBED_RATE_PREFIX + scope + ":" + appKey + ":" + clientIp + ":" + minute;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, 65L, TimeUnit.SECONDS);
            }
            if (count != null && count > limit) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, message);
            }
            return;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis 限流不可用，降级为进程内计数：{}", e.getMessage());
        }
        checkLocal(key, limit, now, message);
    }

    /**
     * 进程内滑动窗口：仅在 Redis 不可用时兜底。
     */
    private void checkLocal(String key, int limit, long now, String message) {
        if (localBuckets.size() > LOCAL_TRACK_LIMIT) {
            localBuckets.clear();
        }
        Deque<Long> bucket = localBuckets.computeIfAbsent(key, item -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > WINDOW_MILLIS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= limit) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, message);
            }
            bucket.addLast(now);
        }
    }
}
