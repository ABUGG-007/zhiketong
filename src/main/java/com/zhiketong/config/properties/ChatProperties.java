package com.zhiketong.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 会话与转人工配置项（application.yml 中 zhiketong.chat）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhiketong.chat")
public class ChatProperties {

    /** 排队超时时间（分钟），超时自动关闭并提示 */
    private int queueTimeoutMinutes = 10;

    /** 单坐席最大同时接待会话数 */
    private int maxAgentConcurrent = 5;

    /** 知识库命中直接回答的置信度阈值 */
    private double aiConfidenceThreshold = 0.60;

    /** 连续未命中（降级回复）达到该次数后建议转人工 */
    private int fallbackTransferThreshold = 3;

    /**
     * 排队中机器人接待（RB-15）：开启后坐席未接入前仍由机器人应答，避免访客等待期完全静默。
     * <p>排队期应答不计入连续未命中次数，也不会写入未命中问题表。</p>
     */
    private boolean waitingBotEnabled = true;

    /** 触发转人工的关键词 */
    private List<String> transferKeywords = new ArrayList<>(Arrays.asList("转人工", "人工客服", "真人", "投诉"));
}
