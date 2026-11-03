package com.zhiketong.enums;

/**
 * 质检规则类型，对应 quality_rule.rule_type。
 *
 * <p>SEMANTIC 与 SENTIMENT 由大模型判定（不可用时跳过而非误判），
 * SERVICE 与 SENSITIVE 为关键词/时序规则，完全本地计算。</p>
 *
 * @author 智客通开发组
 */
public enum QualityRuleType {

    /** 语义类：答非所问、问题未解决、客户重复提问 */
    SEMANTIC("SEMANTIC", "语义类", true),

    /** 情感类：客户负面情绪、坐席情绪不当 */
    SENTIMENT("SENTIMENT", "情感类", true),

    /** 服务规范类：未问候、未确认、未使用结束语、回复超时、禁语 */
    SERVICE("SERVICE", "服务规范类", false),

    /** 敏感词类：客户过激用语、坐席违规承诺 */
    SENSITIVE("SENSITIVE", "敏感词类", false);

    private final String code;

    private final String description;

    /** 是否依赖大模型判定 */
    private final boolean aiEvaluated;

    QualityRuleType(String code, String description, boolean aiEvaluated) {
        this.code = code;
        this.description = description;
        this.aiEvaluated = aiEvaluated;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAiEvaluated() {
        return aiEvaluated;
    }

    /**
     * 根据编码获取枚举，未匹配或入参为 null 时返回 null。
     *
     * @param code 类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static QualityRuleType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QualityRuleType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
