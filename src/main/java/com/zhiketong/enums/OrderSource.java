package com.zhiketong.enums;

/**
 * 工单来源枚举，对应 work_order.source。
 *
 * @author 智客通开发组
 */
public enum OrderSource {

    /** 会话转工单 */
    CHAT("CHAT", "会话转工单"),

    /** 手工创建 */
    MANUAL("MANUAL", "手工创建"),

    /** 电话工单 */
    PHONE("PHONE", "电话工单");

    private final String code;

    private final String description;

    OrderSource(String code, String description) {
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
     * @param code 来源编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderSource fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderSource value : values()) {
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
        for (OrderSource value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
