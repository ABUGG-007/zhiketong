package com.zhiketong.common;

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
    /** 会话与坐席绑定：zk:session:agent:{sessionId} */
    public static final String SESSION_AGENT_PREFIX = "zk:session:agent:";

    /** 通用状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 通用状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;
}
