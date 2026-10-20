package com.zhiketong.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工单 SLA 配置项（application.yml 中 zhiketong.sla）。
 *
 * <p>firstResponseMinutes / resolveMinutes 的 key 为优先级 P0-P3，value 为时限（分钟）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhiketong.sla")
public class SlaProperties {

    /** 超时扫描 cron 表达式 */
    private String scanCron = "0 */5 * * * ?";

    /** 首次响应时限（分钟） */
    private Map<String, Integer> firstResponseMinutes = new HashMap<>();

    /** 解决时限（分钟） */
    private Map<String, Integer> resolveMinutes = new HashMap<>();

    public int getFirstResponseMinutes(String priority) {
        Integer value = firstResponseMinutes.get(priority);
        return value == null ? 480 : value;
    }

    public int getResolveMinutes(String priority) {
        Integer value = resolveMinutes.get(priority);
        return value == null ? 1440 : value;
    }
}
