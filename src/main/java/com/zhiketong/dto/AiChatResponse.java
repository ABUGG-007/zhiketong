package com.zhiketong.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 对话响应。
 */
@Data
@Builder
public class AiChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回复内容 */
    private String content;

    /** 实际使用的模型 */
    private String model;

    /** 调用耗时（毫秒） */
    private long elapsedMs;

    /** 是否走了降级回复 */
    private boolean fallback;

    /** 降级原因 */
    private String fallbackReason;
}
