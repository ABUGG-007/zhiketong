package com.zhiketong.enums;

/**
 * 工单规则类型枚举，对应 order_rule.rule_type（S3-C）。
 *
 * <p>三类规则共用同一张配置表，用类型区分语义：自动分配在建单/待分配时按技能组与负载择优坐席；
 * 触发器由工单事件（创建、状态变更、SLA 超时等）驱动执行动作；自动化按时间驱动（延迟或周期）执行动作。</p>
 *
 * @author 智客通开发组
 */
public enum OrderRuleType {

    /** 自动分配 */
    AUTO_ASSIGN("AUTO_ASSIGN", "自动分配"),

    /** 事件触发器 */
    TRIGGER("TRIGGER", "事件触发器"),

    /** 时间驱动自动化 */
    AUTOMATION("AUTOMATION", "时间自动化");

    private final String code;

    private final String description;

    OrderRuleType(String code, String description) {
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
    public static OrderRuleType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderRuleType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 全部取值的可读描述。
     *
     * @return 取值描述
     */
    public static String valuesDescription() {
        StringBuilder builder = new StringBuilder();
        for (OrderRuleType value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
