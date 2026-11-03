package com.zhiketong.enums;

/**
 * 告警事件处理状态枚举，对应 alert_event.status（S6-B）。
 *
 * <p>状态机：{@code OPEN → ACKED → CLOSED}，允许 {@code OPEN → CLOSED} 直接关闭；
 * 已关闭为终态，重复触发会新建事件而不是复活旧事件。</p>
 *
 * @author 智客通开发组
 */
public enum AlertStatus {

    /** 待处理 */
    OPEN("OPEN", "待处理"),

    /** 已确认：有人接手，仍未闭环 */
    ACKED("ACKED", "已确认"),

    /** 已关闭：问题已处理或条件已消失 */
    CLOSED("CLOSED", "已关闭");

    private final String code;

    private final String description;

    AlertStatus(String code, String description) {
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
     * @param code 状态编码
     * @return 匹配的枚举，可能为 null
     */
    public static AlertStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlertStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
