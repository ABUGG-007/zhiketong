package com.zhiketong.enums;

/**
 * 会话文本资产来源，对应 session_summary.source。
 * <p>
 * AI 建议只是草稿：进线摘要与推荐答案不对客发送，服务小结必须由坐席确认后才归档。
 *
 * @author 智客通开发组
 */
public enum SummarySource {

    /** 大模型生成 */
    AI("AI", "大模型生成"),

    /** 规则兜底：大模型未启用或调用失败时按会话事实拼装 */
    RULE("RULE", "规则兜底"),

    /** 坐席手工编写或修改后归档 */
    AGENT("AGENT", "坐席编写");

    private final String code;

    private final String description;

    SummarySource(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
