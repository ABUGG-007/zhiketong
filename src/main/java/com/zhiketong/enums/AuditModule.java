package com.zhiketong.enums;

/**
 * 审计业务模块枚举，对应 audit_log.module（S4-C，PL-06）。
 *
 * @author 智客通开发组
 */
public enum AuditModule {

    /** 租户与套餐（平台端开通、编辑、启停、换套餐、续期、删除） */
    TENANT("TENANT", "租户与套餐"),

    /** 账号与角色 */
    USER("USER", "账号管理"),

    /** 知识库词条 */
    KB("KB", "知识库"),

    /** 网页接入配置 */
    EMBED("EMBED", "网页接入"),

    /** 大模型配置 */
    AI("AI", "大模型配置"),

    /** 工单规则与自动化 */
    ORDER_RULE("ORDER_RULE", "工单规则"),

    /** 智能质检规则 */
    QUALITY("QUALITY", "智能质检"),

    /** 告警规则与会话监控（S6-B） */
    ALERT("ALERT", "告警与监控"),

    /** 客户之声主题与大屏布局（S6-C） */
    VOC("VOC", "客户之声");

    private final String code;

    private final String description;

    AuditModule(String code, String description) {
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
     * @param code 模块编码
     * @return 匹配的枚举，可能为 null
     */
    public static AuditModule fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AuditModule value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
