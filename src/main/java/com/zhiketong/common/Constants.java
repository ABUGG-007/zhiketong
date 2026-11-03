package com.zhiketong.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 全局常量：Redis 键前缀、角色编码等。
 */
public final class Constants {

    private Constants() {
    }

    /** 角色：管理员 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** 角色：客服坐席 */
    public static final String ROLE_AGENT = "AGENT";
    /** 角色：普通用户 */
    public static final String ROLE_USER = "USER";
    /** 角色：平台管理员（跨租户，管理租户/套餐/平台配置） */
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** JWT 在请求头中的字段名 */
    public static final String AUTH_HEADER = "Authorization";
    /** JWT 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Token 黑名单：zk:token:blacklist:{jti} */
    public static final String TOKEN_BLACKLIST_PREFIX = "zk:token:blacklist:";
    /** 坐席在线状态：zk:agent:online（Hash，agentId -> status） */
    public static final String AGENT_ONLINE_KEY = "zk:agent:online";
    /** 坐席当前负载：zk:agent:load:{agentId} */
    public static final String AGENT_LOAD_PREFIX = "zk:agent:load:";
    /** 等待接入队列：zk:queue:waiting（ZSet，sessionId -> 排队时间戳） */
    public static final String QUEUE_WAITING_KEY = "zk:queue:waiting";
    /** 排队元信息：zk:queue:meta:{sessionId}（Hash） */
    public static final String QUEUE_META_PREFIX = "zk:queue:meta:";
    /** 接入并发锁：zk:lock:accept:{sessionId} */
    public static final String LOCK_ACCEPT_PREFIX = "zk:lock:accept:";
    /** 转接并发锁：zk:lock:transfer:{sessionId} */
    public static final String LOCK_TRANSFER_PREFIX = "zk:lock:transfer:";
    /** 坐席实时状态：zk:agent:status:{agentId}（Hash，TTL 120s） */
    public static final String AGENT_STATUS_PREFIX = "zk:agent:status:";
    /** 坐席心跳节流：zk:agent:heartbeat:{agentId}（30s） */
    public static final String AGENT_HEARTBEAT_PREFIX = "zk:agent:heartbeat:";
    /** 输入状态节流：zk:typing:{sessionId}:{senderType} */
    public static final String TYPING_THROTTLE_PREFIX = "zk:typing:";
    /** 会话与坐席绑定：zk:session:agent:{sessionId} */
    public static final String SESSION_AGENT_PREFIX = "zk:session:agent:";
    /** 消息幂等：zk:msg:dedup:{sessionId}:{clientMsgId} */
    public static final String MSG_DEDUP_PREFIX = "zk:msg:dedup:";
    /** 会话连续降级计数：zk:session:fallback:{sessionId} */
    public static final String SESSION_FALLBACK_PREFIX = "zk:session:fallback:";
    /** 消息发送限流：zk:rate:chat:{userId}:{epochSecond} */
    public static final String RATE_CHAT_PREFIX = "zk:rate:chat:";
    /** 知识库热门排行缓存：zk:kb:hot */
    public static final String KB_HOT_KEY = "zk:kb:hot";
    /** 工单 SLA 扫描分布式锁：zk:sla:scan:lock */
    public static final String SLA_SCAN_LOCK = "zk:sla:scan:lock";
    /** 工单规则到期队列扫描分布式锁：zk:rule:scan:lock */
    public static final String RULE_SCAN_LOCK = "zk:rule:scan:lock";
    /** 智能质检扫描分布式锁：zk:quality:scan:lock */
    public static final String QUALITY_SCAN_LOCK = "zk:quality:scan:lock";
    /** 告警扫描分布式锁：zk:alert:scan:lock */
    public static final String ALERT_SCAN_LOCK = "zk:alert:scan:lock";
    /** 客户之声聚类分布式锁：zk:voc:cluster:lock */
    public static final String VOC_CLUSTER_LOCK = "zk:voc:cluster:lock";
    /** 网页接入签名防重放：zk:embed:nonce:{appKey}:{nonce} */
    public static final String EMBED_NONCE_PREFIX = "zk:embed:nonce:";
    /** 网页接入限流：zk:embed:rate:{appKey}:{ip}:{epochMinute} */
    public static final String EMBED_RATE_PREFIX = "zk:embed:rate:";
    /** 分配轮询游标：zk:agent:assign:rr:{scopeKey}（scopeKey 为技能组 ID 或 ALL） */
    public static final String ASSIGN_ROUND_ROBIN_PREFIX = "zk:agent:assign:rr:";

    /** 租户状态：正常 */
    public static final String TENANT_STATUS_ENABLED = "ENABLED";
    /** 租户状态：已停用 */
    public static final String TENANT_STATUS_DISABLED = "DISABLED";
    /** 租户状态：已到期 */
    public static final String TENANT_STATUS_EXPIRED = "EXPIRED";

    /** 通用状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 通用状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 工单可选问题分类（分类常量唯一来源，服务层与前端字典共用） */
    public static final List<String> ORDER_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "技术问题", "账号问题", "订单问题", "售后问题", "其他"));
}
