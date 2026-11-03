package com.zhiketong.enums;

/**
 * 告警严重程度枚举，对应 alert_rule.severity / alert_event.severity（S6-B）。
 *
 * @author 智客通开发组
 */
public enum AlertSeverity {

    /** 提示：信息类，不要求立即处理 */
    INFO("INFO", "提示", 1),

    /** 警告：需要关注并在时限内处理 */
    WARN("WARN", "警告", 2),

    /** 严重：立即介入，通常伴随客户流失或合规风险 */
    CRITICAL("CRITICAL", "严重", 3);

    private final String code;

    private final String description;

    /** 排序权重，越大越紧急 */
    private final int weight;

    AlertSeverity(String code, String description, int weight) {
        this.code = code;
        this.description = description;
        this.weight = weight;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * 根据编码获取枚举，未匹配或入参为 null 时返回 null。
     *
     * @param code 严重程度编码
     * @return 匹配的枚举，可能为 null
     */
    public static AlertSeverity fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlertSeverity value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
