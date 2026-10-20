package com.zhiketong.enums;

/**
 * 工单优先级枚举，对应 work_order.priority。
 * <p>
 * 优先级由高到低为 P0、P1、P2、P3，SLA 时限也按优先级区分。
 *
 * @author 智客通开发组
 */
public enum OrderPriority {

    /** P0 紧急 */
    P0("P0", "紧急"),

    /** P1 高 */
    P1("P1", "高"),

    /** P2 中 */
    P2("P2", "中"),

    /** P3 低 */
    P3("P3", "低");

    private final String code;

    private final String description;

    OrderPriority(String code, String description) {
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
     * @param code 优先级编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderPriority fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderPriority value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 全部取值的可读描述，如 "P0=紧急, P1=高, P2=中, P3=低"。
     *
     * @return 取值描述
     */
    public static String valuesDescription() {
        StringBuilder builder = new StringBuilder();
        for (OrderPriority value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
