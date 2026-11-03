package com.zhiketong.enums;

/**
 * 消息发送方类型枚举，对应 chat_message.sender_type。
 *
 * @author 智客通开发组
 */
public enum SenderType {

    /** 用户 */
    USER("USER", "用户"),

    /** AI 助手 */
    AI("AI", "AI 助手"),

    /** 客服坐席 */
    AGENT("AGENT", "客服坐席"),

    /** 系统 */
    SYSTEM("SYSTEM", "系统"),

    /**
     * 内部备注：坐席记录的处理信息，仅坐席与管理端可见。
     * <p>不进入访客侧消息分页、不进入大模型上下文，也不计入会话消息数。</p>
     */
    INTERNAL_NOTE("INTERNAL_NOTE", "内部备注"),

    /**
     * 组长强插：管理员在监听会话时直接对客发言（S6-B，AS-10）。
     * <p>访客与坐席双方可见，与坐席本人发言区分展示，便于事后追溯介入人。</p>
     */
    SUPERVISOR("SUPERVISOR", "组长");

    private final String code;

    private final String description;

    SenderType(String code, String description) {
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
     * @param code 发送方类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static SenderType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SenderType value : values()) {
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
        for (SenderType value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
