package com.zhiketong.service;

import com.zhiketong.enums.AnswerCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 应答分类与未命中采集的共享支撑（站内会话与网页嵌入端共用）。
 * <p>
 * 站内访客端与网页嵌入端必须使用同一套编排口径：分类规则、闲聊/无效话术、
 * 未命中采集条件都集中在此，避免两端智能程度不一致。
 *
 * @author 智客通开发组
 */
@Service
@RequiredArgsConstructor
public class AnswerSupport {

    /** 弱命中推荐最多展示的问法数 */
    private static final int MAX_SUGGESTIONS = 3;

    private final KnowledgeMatcher knowledgeMatcher;
    private final KbUnmatchedService unmatchedService;

    /**
     * 按最佳匹配得分对用户问句做应答分类。
     */
    public AnswerCategory classify(String question, double bestScore) {
        return knowledgeMatcher.classify(question, bestScore);
    }

    /**
     * 是否计入未命中采集：只有弱命中推荐与兜底才采集。
     */
    public boolean shouldCollect(AnswerCategory category) {
        return category == AnswerCategory.SUGGEST || category == AnswerCategory.FALLBACK;
    }

    /**
     * 采集未命中问法；闲聊与无效输入不采集。
     *
     * @param question  访客原始问法
     * @param category  本轮应答分类
     * @param channel   来源渠道 SITE / EMBED
     * @param sessionId 会话 ID，可为空
     * @param userId    用户 ID，可为空
     * @param score     最佳匹配得分
     */
    public void collect(String question, AnswerCategory category, String channel,
                        Long sessionId, Long userId, double score) {
        if (!shouldCollect(category)) {
            return;
        }
        unmatchedService.record(question, channel, sessionId, userId, score);
    }

    /**
     * 闲聊话术：问候、致谢、告别分别给出轻量回复。
     */
    public String chitchatReply(String question) {
        String content = question == null ? "" : question.trim().toLowerCase();
        if (content.contains("谢谢") || content.contains("感谢") || content.contains("多谢")
                || content.contains("thanks") || content.contains("thankyou")) {
            return "不客气～还有其他问题随时找我。";
        }
        if (content.contains("再见") || content.contains("拜拜") || content.contains("bye")) {
            return "好的，祝您生活愉快，有需要随时回来找我。";
        }
        return "您好，我是智能客服助手～关于产品功能、订单进度、售后处理等问题，可以直接描述，我来帮您处理。";
    }

    /**
     * 无效输入话术：引导访客补充有效信息。
     */
    public String invalidReply() {
        return "抱歉，我暂时没理解您的意思。请补充一下具体问题，例如「如何申请退款」「订单多久发货」，我再为您解答。";
    }

    /**
     * 弱命中推荐话术：把相近问法推荐给访客，便于一键点选。
     */
    public String suggestReply(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return fallbackReply();
        }
        StringBuilder builder = new StringBuilder("我没有找到完全匹配的答案，您是不是想问：\n");
        int index = 1;
        for (String suggestion : suggestions) {
            if (!StringUtils.hasText(suggestion)) {
                continue;
            }
            builder.append(index++).append(". ").append(suggestion.trim()).append("\n");
            if (index > MAX_SUGGESTIONS) {
                break;
            }
        }
        builder.append("也可以补充更多细节，比如订单号或具体场景，我继续为您查找。");
        return builder.toString();
    }

    /**
     * 兜底话术。
     */
    public String fallbackReply() {
        return "抱歉，我暂时没有找到合适的答案。您可以补充更多细节，或回复「转人工」由人工客服为您跟进。";
    }
}
