package com.zhiketong.enums;

/**
 * 工单规则触发事件枚举，对应 order_rule.trigger_event 与 order_rule_run.event（S3-C）。
 *
 * <p>事件由工单状态流转与 SLA 引擎发出，是触发器（{@link OrderRuleType#TRIGGER}）与
 * 自动分配（{@link OrderRuleType#AUTO_ASSIGN}）的匹配依据；纯时间自动化规则事件为空，由扫描任务驱动。</p>
 *
 * @author 智客通开发组
 */
public enum OrderRuleEvent {

    /** 工单创建 */
    CREATED("CREATED", "工单创建"),

    /** 工单分配 */
    ASSIGNED("ASSIGNED", "工单分配"),

    /** 进入处理中 */
    PROCESSING("PROCESSING", "开始处理"),

    /** 工单解决 */
    RESOLVED("RESOLVED", "工单解决"),

    /** 工单关闭 */
    CLOSED("CLOSED", "工单关闭"),

    /** 工单重开 */
    REOPENED("REOPENED", "工单重开"),

    /** 优先级变更 */
    PRIORITY_CHANGED("PRIORITY_CHANGED", "优先级变更"),

    /** SLA 预警 */
    SLA_WARN("SLA_WARN", "SLA 预警"),

    /** SLA 超时 */
    SLA_OVERDUE("SLA_OVERDUE", "SLA 超时"),

    /** SLA 升级 */
    SLA_ESCALATE("SLA_ESCALATE", "SLA 升级");

    private final String code;

    private final String description;

    OrderRuleEvent(String code, String description) {
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
     * @param code 事件编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderRuleEvent fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderRuleEvent value : values()) {
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
        for (OrderRuleEvent value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
