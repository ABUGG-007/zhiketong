package com.zhiketong.enums;

/**
 * 会话关闭原因枚举，对应 chat_session.close_reason。
 *
 * @author 智客通开发组
 */
public enum CloseReason {

    /** 用户主动结束 */
    USER_CLOSED("USER_CLOSED", "用户主动结束"),

    /** 坐席结束会话 */
    AGENT_CLOSED("AGENT_CLOSED", "坐席结束会话"),

    /** 超时自动结束 */
    TIMEOUT("TIMEOUT", "超时自动结束"),

    /** 系统关闭 */
    SYSTEM("SYSTEM", "系统关闭");

    private final String code;

    private final String description;

    CloseReason(String code, String description) {
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
     * @param code 关闭原因编码
     * @return 匹配的枚举，可能为 null
     */
    public static CloseReason fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CloseReason value : values()) {
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
        for (CloseReason value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
