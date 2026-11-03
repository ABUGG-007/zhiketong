package com.zhiketong.enums;

/**
 * 质检规则严重程度，对应 quality_rule.severity。
 *
 * @author 智客通开发组
 */
public enum QualitySeverity {

    /** 一般：提示类问题，不影响合规 */
    LOW("LOW", "一般"),

    /** 中等：影响服务体验，需改进 */
    MEDIUM("MEDIUM", "中等"),

    /** 严重：合规风险或明显服务事故 */
    HIGH("HIGH", "严重");

    private final String code;

    private final String description;

    QualitySeverity(String code, String description) {
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
     * @param code 严重程度编码
     * @return 匹配的枚举，可能为 null
     */
    public static QualitySeverity fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QualitySeverity value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
