package com.zhiketong.ai;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 质检提示词模板（S5-A）：语义类与情感类规则一次调用完成判定。
 *
 * <p>输出协议固定为「规则编码|是否命中|证据|建议」，由服务层解析；
 * 解析失败或大模型不可用时整组语义/情感规则跳过，绝不默认判为命中，
 * 避免「AI 不可用导致全量不合格」的误判。</p>
 *
 * @author 智客通开发组
 */
public final class QualityPrompts {

    private QualityPrompts() {
    }

    /** 质检任务统一系统提示词：只做判定，不做改写 */
    public static final String QUALITY_SYSTEM_PROMPT =
            "你是企业客服质检员，负责根据会话记录判定质检规则是否命中。"
                    + "只输出判定结果，不要寒暄、不要解释判定方法、不要复述规则清单。"
                    + "判定必须以会话原文为依据，不得凭空推断。";

    /** 单条证据的最大长度，避免长文本撑爆明细字段 */
    private static final int MAX_EVIDENCE_LENGTH = 60;

    /** 会话记录最大长度，超出截断 */
    private static final int MAX_DIALOGUE_LENGTH = 6000;

    /**
     * 构造语义/情感规则判定提示词。
     *
     * @param ruleLabels 规则清单，元素为「规则编码=规则名称：判定口径」
     * @param dialogue   会话记录（含说话人）
     * @return 提示词
     */
    public static String evaluatePrompt(List<String> ruleLabels, String dialogue) {
        StringBuilder builder = new StringBuilder();
        builder.append("请逐条判断下列质检规则是否命中，严格按下面格式输出，每条规则一行：\n")
                .append("规则编码|是否命中(1或0)|证据（引用会话原文，不超过 ").append(MAX_EVIDENCE_LENGTH)
                .append(" 字）|改进建议（不超过 40 字）\n")
                .append("要求：\n")
                .append("1. 只输出上面四段，用半角竖线分隔，不要输出表头、编号或额外说明；\n")
                .append("2. 未命中的规则，证据与建议留空，仍须输出「编码|0||」；\n")
                .append("3. 证据必须能在会话记录中找到原文，不要编造；\n")
                .append("4. 会话记录不足以判断时按未命中处理。\n")
                .append("【规则清单】\n");
        for (String label : ruleLabels) {
            builder.append(label).append('\n');
        }
        builder.append("【会话记录】\n").append(abbreviate(dialogue, MAX_DIALOGUE_LENGTH));
        return builder.toString();
    }

    /**
     * 把规则清单转成提示词行。
     *
     * @param ruleCode    规则编码
     * @param ruleName    规则名称
     * @param description 判定口径
     * @return 提示词行
     */
    public static String ruleLabel(String ruleCode, String ruleName, String description) {
        String label = new StringBuilder(ruleCode).append('=').append(ruleName).toString();
        return StringUtils.hasText(description) ? label + "：" + description : label;
    }

    /**
     * 按英文逗号切分关键词，去空去重，保留原顺序。
     *
     * @param keywords 关键词串
     * @return 关键词列表，入参为空时返回空列表
     */
    public static List<String> splitKeywords(String keywords) {
        List<String> list = new ArrayList<>();
        if (!StringUtils.hasText(keywords)) {
            return list;
        }
        for (String item : keywords.replace('，', ',').replace('、', ',').split(",")) {
            String keyword = item.trim();
            if (!keyword.isEmpty() && !list.contains(keyword)) {
                list.add(keyword);
            }
        }
        return list;
    }

    private static String abbreviate(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.length() > maxLength ? content.substring(0, maxLength) + "…" : content;
    }
}
