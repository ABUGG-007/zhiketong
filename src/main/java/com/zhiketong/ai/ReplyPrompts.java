package com.zhiketong.ai;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 应答编排共享提示词模板：站内会话（ChatService）与网页嵌入端（EmbedService）
 * 必须使用同一套编排与话术，禁止两端各自维护导致智能程度不一致。
 *
 * @author 智客通开发组
 */
public final class ReplyPrompts {

    private ReplyPrompts() {
    }

    /** 参考知识注入提示词中答案的最大长度 */
    private static final int MAX_REFERENCE_ANSWER_LENGTH = 800;

    /** 坐席辅助任务输入中单个字段的最大长度 */
    private static final int MAX_ASSIST_INPUT_LENGTH = 3000;

    /** 坐席辅助任务统一系统提示词：只产出草稿，不编造事实 */
    public static final String ASSIST_SYSTEM_PROMPT =
            "你是企业客服坐席的辅助助手。只输出要求的字段，不要寒暄、不要额外解释；"
                    + "不得编造订单、金额、时效等事实，不确定的信息写「待坐席确认」；"
                    + "你的输出只是给坐席参考的草稿，不会直接发给客户。";

    /**
     * 构造「参考知识 + 用户问题」提示词：知识库弱命中（0.35 ~ 直答阈值）时注入大模型。
     */
    public static String referencePrompt(String question, String answer, String content) {
        return "请优先参考以下知识库内容回答用户问题；若与问题无关，请忽略并按通用客服规范回答。\n"
                + "【标准问题】" + question + "\n"
                + "【参考答案】" + abbreviate(answer, MAX_REFERENCE_ANSWER_LENGTH) + "\n\n"
                + "用户问题：" + content;
    }

    /**
     * 构造「相似问法扩写」提示词：让大模型基于标准问题生成同义、口语化与错别字形态。
     * <p>扩写结果只作为候选返回，必须人工确认后才入库。</p>
     *
     * @param question 标准问题
     * @param answer   标准答案，可为空
     * @param existing 已有问法，用于避免重复生成
     * @return 提示词
     */
    public static String similarExpandPrompt(String question, String answer, List<String> existing) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是企业知识库运营助手。请为下面的标准问题扩写 8 条相似问法，")
                .append("覆盖同义替换、句式变换、口语化表达与常见错别字。\n")
                .append("要求：每行输出一条，不要编号、不要解释、不要改变业务事实。\n")
                .append("【标准问题】").append(question).append("\n");
        if (StringUtils.hasText(answer)) {
            builder.append("【标准答案】").append(abbreviate(answer, MAX_REFERENCE_ANSWER_LENGTH)).append("\n");
        }
        if (existing != null && !existing.isEmpty()) {
            builder.append("【已有问法】").append(String.join("；", existing)).append("\n");
        }
        return builder.toString();
    }

    private static String abbreviate(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.length() > maxLength ? content.substring(0, maxLength) + "…" : content;
    }

    /**
     * 构造「坐席实时推荐答案」提示词：知识库未命中时由大模型起草回复，坐席确认后才发送。
     *
     * @param question 访客当前诉求（最近一条消息）
     * @param dialogue 最近对话上下文，可为空
     * @return 提示词
     */
    public static String recommendPrompt(String question, String dialogue) {
        StringBuilder builder = new StringBuilder();
        builder.append("请为坐席起草一条可以直接发给客户的回复，")
                .append("要求语气礼貌、信息准确、控制在 200 字以内，不要使用 Markdown。\n");
        if (StringUtils.hasText(dialogue)) {
            builder.append("【最近对话】\n")
                    .append(abbreviate(dialogue, MAX_ASSIST_INPUT_LENGTH)).append("\n");
        }
        builder.append("【客户诉求】").append(question).append("\n");
        builder.append("只输出回复正文。");
        return builder.toString();
    }

    /**
     * 构造「进线摘要」提示词：转人工瞬间生成，帮助坐席接起即懂来意。
     *
     * @param dialogue     最近对话
     * @param orderSummary 客户名下近期工单摘要，可为空
     * @return 提示词
     */
    public static String briefPrompt(String dialogue, String orderSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据访客与智能客服的对话，生成供坐席快速了解的进线摘要。\n")
                .append("严格按下面四行输出，每行以方括号标签开头，不要输出其他内容：\n")
                .append("【客户诉求】一句话概括客户要解决的问题\n")
                .append("【服务过程】已确认的信息与已做的处理\n")
                .append("【处理结果】当前状态\n")
                .append("【待办事项】坐席需要跟进的动作\n")
                .append("篇幅要求：四行正文分别不超过 60、120、60、60 字。")
                .append("字数要求只约束篇幅，禁止把字数说明或括号注释写进正文。\n");
        if (StringUtils.hasText(orderSummary)) {
            builder.append("【客户近期工单】\n")
                    .append(abbreviate(orderSummary, MAX_ASSIST_INPUT_LENGTH)).append("\n");
        }
        builder.append("【对话记录】\n")
                .append(abbreviate(dialogue, MAX_ASSIST_INPUT_LENGTH));
        return builder.toString();
    }

    /**
     * 构造「服务小结」提示词：会话结束时生成结构化小结草稿，坐席确认后归档。
     *
     * @param dialogue     会话对话记录
     * @param orderSummary 客户名下近期工单摘要，可为空
     * @return 提示词
     */
    public static String summaryPrompt(String dialogue, String orderSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据完整会话记录生成结构化服务小结。\n")
                .append("严格按下面五行输出，每行以方括号标签开头，不要输出其他内容：\n")
                .append("【客户诉求】客户要解决的核心问题\n")
                .append("【服务过程】坐席与智能客服的处理动作\n")
                .append("【处理结果】最终结果，未解决则说明卡点\n")
                .append("【待办事项】后续需要跟进的事项，没有则写「无」\n")
                .append("【关键词】用英文逗号分隔，最多 5 个\n")
                .append("篇幅要求：前四行正文分别不超过 80、200、80、80 字。")
                .append("字数要求只约束篇幅，禁止把字数说明或括号注释写进正文。\n");
        if (StringUtils.hasText(orderSummary)) {
            builder.append("【客户近期工单】\n")
                    .append(abbreviate(orderSummary, MAX_ASSIST_INPUT_LENGTH)).append("\n");
        }
        builder.append("【会话记录】\n")
                .append(abbreviate(dialogue, MAX_ASSIST_INPUT_LENGTH));
        return builder.toString();
    }
}
