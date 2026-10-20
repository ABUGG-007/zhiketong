package com.zhiketong.common.util;

import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求来源 IP 工具。
 */
public final class IpUtils {

    private IpUtils() {
    }

    private static final String UNKNOWN = "unknown";
    private static final int MAX_LENGTH = 64;

    /**
     * 获取客户端真实 IP（兼容 Nginx 等反向代理）。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index > 0) {
                ip = ip.substring(0, index);
            }
        }
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip == null) {
            return UNKNOWN;
        }
        ip = ip.trim();
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }

    /**
     * 获取 User-Agent 并截断到数据库字段长度。
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
