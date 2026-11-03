package com.zhiketong.enums;

/**
 * 告警目标类型枚举，对应 alert_event.target_type（S6-B）。
 *
 * <p>目标决定去重粒度：队列与工单类告警按租户聚合为一个目标，
 * 会话类按会话去重，坐席类按坐席去重。</p>
 *
 * @author 智客通开发组
 */
public enum AlertTargetType {

    /** 排队：targetId 为租户 ID（全租户排队状态） */
    QUEUE("QUEUE", "排队"),

    /** 会话：targetId 为会话 ID */
    SESSION("SESSION", "会话"),

    /** 坐席：targetId 为坐席用户 ID */
    AGENT("AGENT", "坐席"),

    /** 工单：targetId 为租户 ID（全租户工单超时汇总） */
    ORDER("ORDER", "工单");

    private final String code;

    private final String description;

    AlertTargetType(String code, String description) {
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
     * @param code 目标类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static AlertTargetType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlertTargetType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
