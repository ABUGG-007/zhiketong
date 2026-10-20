package com.zhiketong.enums;

/**
 * 工单操作动作枚举，对应 work_order_log.action。
 *
 * @author 智客通开发组
 */
public enum OrderAction {

    /** 创建工单 */
    CREATE("CREATE", "创建工单"),

    /** 分配工单 */
    ASSIGN("ASSIGN", "分配工单"),

    /** 受理工单 */
    ACCEPT("ACCEPT", "受理工单"),

    /** 处理中 */
    PROCESS("PROCESS", "处理中"),

    /** 解决工单 */
    RESOLVE("RESOLVE", "解决工单"),

    /** 关闭工单 */
    CLOSE("CLOSE", "关闭工单"),

    /** 重开工单 */
    REOPEN("REOPEN", "重开工单"),

    /** 添加备注 */
    COMMENT("COMMENT", "添加备注"),

    /** SLA 超时 */
    OVERDUE("OVERDUE", "SLA 超时");

    private final String code;

    private final String description;

    OrderAction(String code, String description) {
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
     * @param code 动作编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderAction value : values()) {
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
        for (OrderAction value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
