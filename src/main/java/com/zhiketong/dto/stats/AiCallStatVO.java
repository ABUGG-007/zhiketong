package com.zhiketong.dto.stats;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 调用统计（R-69）。
 *
 * @author 智客通开发组
 */
@Data
public class AiCallStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 调用总数 */
    private long totalCalls;

    /** 成功次数 */
    private long successCalls;

    /** 失败次数 */
    private long failCalls;

    /** 成功率 */
    private BigDecimal successRate;

    /** 平均耗时（毫秒） */
    private long avgLatencyMs;

    /** 输入 token 总数 */
    private long totalPromptTokens;

    /** 输出 token 总数 */
    private long totalCompletionTokens;

    /** 降级次数 */
    private long fallbackCount;

    /** 按模型统计 */
    private List<ModelStat> byModel = new ArrayList<>();

    /** 调用趋势 */
    private List<TrendPoint> trend = new ArrayList<>();

    /**
     * 模型维度统计。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelStat implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 模型名称 */
        private String model;

        /** 调用次数 */
        private long calls;

        /** 平均耗时（毫秒） */
        private long avgLatencyMs;

        /** 是否为失败分组，成功分组不返回该字段 */
        private Boolean failed;
    }

    /**
     * 调用趋势点。
     */
    @Data
    public static class TrendPoint implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 时间标签 */
        private String time;

        /** 调用次数 */
        private long calls;

        /** 成功次数 */
        private long successCalls;

        /** 平均耗时（毫秒） */
        private long avgLatencyMs;
    }
}
