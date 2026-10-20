package com.zhiketong.enums;

/**
 * 会话状态枚举，对应 chat_session.status。
 *
 * @author 智客通开发组
 */
public enum SessionStatus {

    /** AI 接待中 */
    AI("AI", "AI 接待中"),

    /** 排队等待人工 */
    WAITING("WAITING", "排队等待人工"),

    /** 人工接待中 */
    HUMAN("HUMAN", "人工接待中"),

    /** 已关闭 */
    CLOSED("CLOSED", "已关闭");

    private final String code;

    private final String description;

    SessionStatus(String code, String description) {
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
     * @param code 会话状态编码
     * @return 匹配的枚举，可能为 null
     */
    public static SessionStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SessionStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 全部取值的可读描述，如 "AI=AI 接待中, WAITING=排队等待人工, ..."。
     *
     * @return 取值描述
     */
    public static String valuesDescription() {
        StringBuilder builder = new StringBuilder();
        for (SessionStatus value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
