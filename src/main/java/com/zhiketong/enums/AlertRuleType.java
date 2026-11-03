package com.zhiketong.enums;

/**
 * 告警规则类型枚举，对应 alert_rule.rule_type（S6-B，AS-11）。
 *
 * @author 智客通开发组
 */
public enum AlertRuleType {

    /** 排队类：排队人数、排队时长、无在线坐席 */
    QUEUE("QUEUE", "排队"),

    /** 会话类：首响偏慢、会话超长、差评、敏感词 */
    SESSION("SESSION", "会话"),

    /** 坐席类：访客消息待回复超时 */
    AGENT("AGENT", "坐席"),

    /** 工单类：工单超时 */
    ORDER("ORDER", "工单");

    private final String code;

    private final String description;

    AlertRuleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取枚举，未匹配或入参为 null 时返回 null。
     *
     * @param code 规则类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static AlertRuleType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlertRuleType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
