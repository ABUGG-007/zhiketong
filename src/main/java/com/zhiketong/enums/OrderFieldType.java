package com.zhiketong.enums;

/**
 * 工单自定义字段类型枚举，对应 order_field.field_type。
 *
 * <p>本轮覆盖文本、多行文本、下拉、数字、日期五类；附件类型依赖文件上传与
 * 存储能力，随 S3-C 富媒体统一交付。</p>
 *
 * @author 智客通开发组
 */
public enum OrderFieldType {

    /** 单行文本 */
    TEXT("TEXT", "文本"),

    /** 多行文本 */
    MULTILINE("MULTILINE", "多行文本"),

    /** 下拉单选 */
    SELECT("SELECT", "下拉"),

    /** 数字 */
    NUMBER("NUMBER", "数字"),

    /** 日期（yyyy-MM-dd） */
    DATE("DATE", "日期");

    private final String code;

    private final String description;

    OrderFieldType(String code, String description) {
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
     * @param code 字段类型编码
     * @return 匹配的枚举，可能为 null
     */
    public static OrderFieldType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderFieldType value : values()) {
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
        for (OrderFieldType value : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.code).append('=').append(value.description);
        }
        return builder.toString();
    }
}
