package com.zhiketong.service;

import com.zhiketong.security.LoginUser;
import com.zhiketong.security.TenantContext;
import com.zhiketong.websocket.WebSocketSessionManager;
import com.zhiketong.websocket.WsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大屏实时推送服务（S6-A，DB-02）。
 *
 * <p>订阅关系存放在内存（用户 ID → 租户 ID）。每次推送按租户聚类，只在对应租户上下文中
 * 计算一次快照再分发给该租户的管理员，避免跨租户泄露与重复查询。</p>
 *
 * <p>连接断开由推送线程惰性清理（{@code isOnline} 判定），历史连接不会导致无限增长；
 * 未订阅的在线用户完全收不到大屏消息，与聊天消息互不干扰。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsPushService {

    /** 订阅者：userId → tenantId */
    private final Map<Long, Long> subscribers = new ConcurrentHashMap<>();

    private final WebSocketSessionManager sessionManager;
    private final OpsService opsService;

    /** 是否开启定时推送 */
    @Value("${zhiketong.ops.push-enabled:true}")
    private boolean pushEnabled;

    /**
     * 订阅大屏快照，返回当前快照作为订阅回执（避免前端等下一轮推送）。
     */
    public Map<String, Object> subscribe(LoginUser loginUser) {
        if (loginUser == null) {
            return new LinkedHashMap<>();
        }
        Long tenantId = loginUser.getTenantId() == null
                ? TenantContext.DEFAULT_TENANT_ID : loginUser.getTenantId();
        subscribers.put(loginUser.getUserId(), tenantId);
        log.info("大屏订阅：userId={}, 当前订阅数={}", loginUser.getUserId(), subscribers.size());
        return TenantContext.callAs(tenantId, opsService::screenPayload);
    }

    /**
     * 取消订阅（关闭大屏页时由前端主动调用）。
     */
    public void unsubscribe(Long userId) {
        if (userId == null) {
            return;
        }
        subscribers.remove(userId);
    }

    /**
     * 连接断开时清理订阅关系。
     */
    public void unsubscribeQuietly(Long userId) {
        subscribers.remove(userId);
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    /**
     * 定时推送：按租户聚合后一次性分发，间隔由 {@code zhiketong.ops.push-interval-ms} 控制。
     */
    @Scheduled(fixedDelayString = "${zhiketong.ops.push-interval-ms:5000}")
    public void pushSnapshot() {
        if (!pushEnabled || subscribers.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> byTenant = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> entry : subscribers.entrySet()) {
            Long userId = entry.getKey();
            if (!sessionManager.isOnline(userId)) {
                // 惰性清理：连接已断开却未走显式退订
                subscribers.remove(userId, entry.getValue());
                continue;
            }
            byTenant.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(userId);
        }
        for (Map.Entry<Long, List<Long>> entry : byTenant.entrySet()) {
            Map<String, Object> payload;
            try {
                payload = TenantContext.callAs(entry.getKey(), opsService::screenPayload);
            } catch (Exception e) {
                log.warn("生成大屏快照失败：tenantId={}, error={}", entry.getKey(), e.getMessage());
                continue;
            }
            for (Long userId : entry.getValue()) {
                sessionManager.sendToUser(userId, new WsMessage("SCREEN_UPDATE", requestId(),
                        System.currentTimeMillis(), payload));
            }
        }
    }

    /**
     * 当前订阅者快照（仅用于自测与运维排查，不含业务数据）。
     */
    public Set<Long> subscriberIds() {
        return subscribers.keySet();
    }

    private String requestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
