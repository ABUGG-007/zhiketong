package com.zhiketong.enums;

/**
 * 工单状态枚举，对应 work_order.status / work_order_log.from_status / to_status。
 *
 * @author 智客通开发组
 */
public enum OrderStatus {

    /** 待分配 */
    PENDING("PENDING", "待分配"),

    /** 已分配 */
    ASSIGNED("ASSIGNED", "已分配"),

    /** 处理中 */
    PROCESSING("PROCESSING", "处理中"),

    /** 已解决 */
    RESOLVED("RESOLVED", "已解决"),

    /** 已关闭 */
    CLOSED("CLOSED", "已关闭"),

    /** 已重开 */
    REOPENED("REOPENED", "已重开");

    private final String code;

    private final String description;

    OrderStatus(String code, String description) {
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
     * @param code 工单状态编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus value : values()) {
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
        for (OrderStatus value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
