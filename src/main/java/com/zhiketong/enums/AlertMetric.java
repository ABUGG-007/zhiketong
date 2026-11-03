package com.zhiketong.enums;

/**
 * 告警指标枚举，对应 alert_rule.metric_code（S6-B，AS-11）。
 *
 * <p>{@code rule_code} 标识规则身份，{@code metric_code} 决定扫描引擎执行哪套判定算法。
 * 内置规则的 {@code rule_code} 与 {@code metric_code} 同名；坐席自定义规则可复用内置指标，
 * 只在阈值、冷却与通知对象上做个性化配置，因此同一指标允许存在多条规则。</p>
 *
 * @author 智客通开发组
 */
public enum AlertMetric {

    /** 排队无人接待：当前排队人数大于阈值且无在线坐席 */
    NO_AGENT_ONLINE("NO_AGENT_ONLINE", "排队无在线坐席", AlertRuleType.QUEUE, "COUNT"),

    /** 排队积压：当前排队人数达到阈值 */
    QUEUE_BACKLOG("QUEUE_BACKLOG", "排队积压", AlertRuleType.QUEUE, "COUNT"),

    /** 排队时长过长：队首等待秒数达到阈值 */
    QUEUE_SLOW("QUEUE_SLOW", "排队时长过长", AlertRuleType.QUEUE, "SECONDS"),

    /** 首响偏慢：坐席已接入但仍未回复，等待时长达到阈值 */
    FIRST_RESPONSE_SLOW("FIRST_RESPONSE_SLOW", "首响偏慢", AlertRuleType.SESSION, "SECONDS"),

    /** 工单超时：存在已超时且未关闭的工单数达到阈值 */
    ORDER_OVERDUE("ORDER_OVERDUE", "工单超时", AlertRuleType.ORDER, "COUNT"),

    /** 访客消息待回复超时：会话内访客最后发言距今达到阈值且无坐席回复 */
    AGENT_RESPONSE_TIMEOUT("AGENT_RESPONSE_TIMEOUT", "访客消息待回复超时", AlertRuleType.AGENT, "SECONDS"),

    /** 会话时长超长：人工会话持续分钟数达到阈值 */
    SESSION_OVERLONG("SESSION_OVERLONG", "会话时长超长", AlertRuleType.SESSION, "MINUTES"),

    /** 客户差评：会话满意度评分不高于阈值 */
    LOW_RATING("LOW_RATING", "客户差评", AlertRuleType.SESSION, "SCORE"),

    /** 会话命中敏感词：会话消息包含规则关键词 */
    SENSITIVE_WORD("SENSITIVE_WORD", "会话命中敏感词", AlertRuleType.SESSION, "COUNT");

    private final String code;

    private final String description;

    /** 指标所属规则类型 */
    private final AlertRuleType ruleType;

    /** 阈值单位：COUNT/SECONDS/MINUTES/SCORE */
    private final String unit;

    AlertMetric(String code, String description, AlertRuleType ruleType, String unit) {
        this.code = code;
        this.description = description;
        this.ruleType = ruleType;
        this.unit = unit;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public AlertRuleType getRuleType() {
        return ruleType;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * 根据编码获取枚举，未匹配或入参为 null 时返回 null。
     *
     * @param code 指标编码
     * @return 匹配的枚举，可能为 null
     */
    public static AlertMetric fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlertMetric value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
