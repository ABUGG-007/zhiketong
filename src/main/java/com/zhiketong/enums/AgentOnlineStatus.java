package com.zhiketong.enums;

/**
 * 坐席在线状态枚举，对应 agent_status.online_status。
 *
 * @author 智客通开发组
 */
public enum AgentOnlineStatus {

    /** 在线空闲 */
    ONLINE("ONLINE", "在线空闲"),

    /** 忙碌（接待数已达上限） */
    BUSY("BUSY", "忙碌"),

    /** 离线 */
    OFFLINE("OFFLINE", "离线");

    private final String code;

    private final String description;

    AgentOnlineStatus(String code, String description) {
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
     * @param code 在线状态编码
     * @return 匹配的枚举，可能为 null
     */
    public static AgentOnlineStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AgentOnlineStatus value : values()) {
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
        for (AgentOnlineStatus value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
