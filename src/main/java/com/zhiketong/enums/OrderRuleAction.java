package com.zhiketong.enums;

/**
 * 工单规则动作类型枚举，对应 order_rule.action_type 与 order_rule_run.action_type（S3-C）。
 *
 * <p>动作是规则命中后的执行单元。WEBHOOK 动作本期未开放（列预留），后续接入出站调用基建后扩展。</p>
 *
 * @author 智客通开发组
 */
public enum OrderRuleAction {

    /** 自动分配坐席（技能组 + 负载均衡择优） */
    AUTO_ASSIGN("AUTO_ASSIGN", "自动分配"),

    /** 指派指定坐席 */
    ASSIGN_AGENT("ASSIGN_AGENT", "指派坐席"),

    /** 调整优先级 */
    CHANGE_PRIORITY("CHANGE_PRIORITY", "调整优先级"),

    /** 变更状态 */
    CHANGE_STATUS("CHANGE_STATUS", "变更状态"),

    /** 通知角色（管理员/创建人/受理人） */
    NOTIFY_ROLE("NOTIFY_ROLE", "通知角色");

    private final String code;

    private final String description;

    OrderRuleAction(String code, String description) {
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
     * @param code 动作类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderRuleAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderRuleAction value : values()) {
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
        for (OrderRuleAction value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
