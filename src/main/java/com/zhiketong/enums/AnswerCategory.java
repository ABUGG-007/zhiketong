package com.zhiketong.enums;

/**
 * 机器人应答分类（对标七鱼「问答分类兜底策略」）。
 * <p>
 * 用于区分本轮回复的来路，前端据此展示不同标签与差异化引导：
 * <ul>
 *   <li>{@link #DIRECT}：知识库直答；</li>
 *   <li>{@link #REFERENCE}：参考知识 + 大模型生成；</li>
 *   <li>{@link #FREE}：大模型自由生成；</li>
 *   <li>{@link #SUGGEST}：弱命中，把候选问法推荐给访客；</li>
 *   <li>{@link #CHITCHAT}：问候/感谢等闲聊，走轻量话术；</li>
 *   <li>{@link #INVALID}：无意义输入，引导补充信息；</li>
 *   <li>{@link #FALLBACK}：兜底话术。</li>
 * </ul>
 * 只有 {@link #SUGGEST} 与 {@link #FALLBACK} 计入未命中采集。
 *
 * @author 智客通开发组
 */
public enum AnswerCategory {

    /** 知识库直答 */
    DIRECT("直接命中"),

    /** 参考知识 + 大模型生成 */
    REFERENCE("参考生成"),

    /** 大模型自由生成 */
    FREE("自由生成"),

    /** 弱命中推荐 */
    SUGGEST("推荐问法"),

    /** 闲聊 */
    CHITCHAT("闲聊"),

    /** 无效输入 */
    INVALID("无效输入"),

    /** 兜底 */
    FALLBACK("兜底");

    private final String description;

    AnswerCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否需要落库到未命中问题表。
     */
    public boolean shouldCollectUnmatched() {
        return this == SUGGEST || this == FALLBACK;
    }
}
