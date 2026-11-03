package com.zhiketong.enums;

/**
 * 质检记录复核状态，对应 quality_record.status。
 *
 * <p>S5-A 只产生 PENDING 记录；人工复核流转（REVIEWED / CONFIRMED）在 S5-B 交付。</p>
 *
 * @author 智客通开发组
 */
public enum QualityRecordStatus {

    /** 待复核：机器质检完成，等待人工复核 */
    PENDING("PENDING", "待复核"),

    /** 已复核：人工已核对并可能调整得分 */
    REVIEWED("REVIEWED", "已复核"),

    /** 已确认：复核结论归档，作为考核口径 */
    CONFIRMED("CONFIRMED", "已确认");

    private final String code;

    private final String description;

    QualityRecordStatus(String code, String description) {
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
    public static QualityRecordStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QualityRecordStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
