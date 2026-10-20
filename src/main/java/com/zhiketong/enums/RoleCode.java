package com.zhiketong.enums;

/**
 * 角色编码枚举，对应 sys_role.role_code / sys_user.role_code。
 *
 * @author 智客通开发组
 */
public enum RoleCode {

    /** 管理员 */
    ADMIN("ADMIN", "管理员"),

    /** 客服坐席 */
    AGENT("AGENT", "客服坐席"),

    /** 普通用户 */
    USER("USER", "普通用户");

    private final String code;

    private final String description;

    RoleCode(String code, String description) {
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
     * @param code 角色编码
     * @return 匹配的枚举，可能为 null
     */
    public static RoleCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RoleCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 全部取值的可读描述，如 "ADMIN=管理员, AGENT=客服坐席, USER=普通用户"。
     *
     * @return 取值描述
     */
    public static String valuesDescription() {
        StringBuilder builder = new StringBuilder();
        for (RoleCode value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
