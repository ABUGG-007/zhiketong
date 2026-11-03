package com.zhiketong.enums;

/**
 * 会话文本资产状态，对应 session_summary.status。
 * <p>
 * AI 生成的进线摘要与服务小结一律先落草稿，服务小结必须由坐席确认后置为 ADOPTED 归档，
 * 禁止未经确认自动归档。
 *
 * @author 智客通开发组
 */
public enum SummaryStatus {

    /** 草稿：AI 或规则生成，尚未经坐席确认 */
    DRAFT("DRAFT", "草稿"),

    /** 已采纳归档：坐席确认或手工编写后保存 */
    ADOPTED("ADOPTED", "已采纳归档");

    private final String code;

    private final String description;

    SummaryStatus(String code, String description) {
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
    public static SummaryStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SummaryStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
