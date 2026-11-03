package com.zhiketong.enums;

/**
 * 工单 SLA 状态枚举，对应 work_order.sla_status / order_sla_record.sla_status（S3-B）。
 *
 * <p>由 SLA 扫描任务与停表 / 恢复操作维护：预警命中置 WARN，任一级超时置 OVERDUE，
 * 停表中置 PAUSED，工单解决或关闭后置 FINISHED，其余为 NORMAL。</p>
 *
 * @author 智客通开发组
 */
public enum SlaStatus {

    /** 正常计时中 */
    NORMAL("NORMAL", "正常"),

    /** 临近超时预警 */
    WARN("WARN", "预警"),

    /** 已超时 */
    OVERDUE("OVERDUE", "超时"),

    /** 停表中（等待客户反馈） */
    PAUSED("PAUSED", "停表中"),

    /** 已完结（工单已解决或关闭） */
    FINISHED("FINISHED", "已完结");

    private final String code;

    private final String description;

    SlaStatus(String code, String description) {
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
     * @param code SLA 状态编码
     * @return 匹配的枚举，可能为 null
     */
    public static SlaStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SlaStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取编码的可读名称，未匹配时原样返回编码，供视图对象展示。
     *
     * @param code SLA 状态编码
     * @return 可读名称
     */
    public static String labelOf(String code) {
        SlaStatus status = fromCode(code);
        return status == null ? code : status.description;
    }

    /**
     * 全部取值的可读描述。
     *
     * @return 取值描述
     */
    public static String valuesDescription() {
        StringBuilder builder = new StringBuilder();
        for (SlaStatus value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
