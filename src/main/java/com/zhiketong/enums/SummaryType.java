package com.zhiketong.enums;

/**
 * 会话文本资产类型，对应 session_summary.summary_type。
 *
 * @author 智客通开发组
 */
public enum SummaryType {

    /** 进线摘要：转人工瞬间生成，坐席接起即懂来意 */
    BRIEF("BRIEF", "进线摘要"),

    /** 服务小结：会话结束前生成并归档 */
    SUMMARY("SUMMARY", "服务小结");

    private final String code;

    private final String description;

    SummaryType(String code, String description) {
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
     * @param code 类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static SummaryType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SummaryType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
