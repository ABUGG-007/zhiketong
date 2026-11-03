package com.zhiketong.enums;

/**
 * 质检评估引擎，对应 quality_record.engine。
 *
 * <p>用于人机质检对比：AI/MIX 记录表示语义与情感规则由大模型参与判定，
 * RULE 记录表示本次全部由本地规则完成（未启用大模型或调用失败降级）。</p>
 *
 * @author 智客通开发组
 */
public enum QualityEngine {

    /** 仅大模型：规则型规则全部停用，全部由 AI 判定 */
    AI("AI", "仅大模型"),

    /** 仅规则：未启用大模型或调用失败，语义/情感规则跳过 */
    RULE("RULE", "仅规则"),

    /** 规则 + 大模型：服务规范/敏感词本地判定，语义/情感由大模型判定 */
    MIX("MIX", "规则+大模型");

    private final String code;

    private final String description;

    QualityEngine(String code, String description) {
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
     * @param code 引擎编码
     * @return 匹配的枚举，可能为 null
     */
    public static QualityEngine fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QualityEngine value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
