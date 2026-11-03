package com.zhiketong.enums;

/**
 * 审计动作枚举，对应 audit_log.action（S4-C，PL-06）。
 *
 * <p>统一动作集，避免各模块自造动词导致审计检索口径分裂。</p>
 *
 * @author 智客通开发组
 */
public enum AuditAction {

    /** 新增 */
    CREATE("CREATE", "新增"),

    /** 编辑 */
    UPDATE("UPDATE", "编辑"),

    /** 删除（逻辑删除） */
    DELETE("DELETE", "删除"),

    /** 启用 */
    ENABLE("ENABLE", "启用"),

    /** 停用 */
    DISABLE("DISABLE", "停用"),

    /** 变更套餐 */
    CHANGE_PLAN("CHANGE_PLAN", "变更套餐"),

    /** 续期或调整到期时间 */
    RENEW("RENEW", "续期"),

    /** 重置密码 */
    RESET_PASSWORD("RESET_PASSWORD", "重置密码"),

    /** 轮换接入密钥 */
    ROTATE_SECRET("ROTATE_SECRET", "轮换密钥"),

    /** 提交人工复核（S5-B，质检记录） */
    REVIEW("REVIEW", "复核"),

    /** 确认归档（S5-B，质检记录复核结论归档） */
    CONFIRM("CONFIRM", "确认归档"),

    /** 驳回退回（S5-B，复核结论被驳回后重新进入待复核） */
    REJECT("REJECT", "驳回");

    private final String code;

    private final String description;

    AuditAction(String code, String description) {
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
    public static AuditAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AuditAction value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
