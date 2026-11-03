package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 进线摘要（R-105 / R-106）：坐席接起即懂来意，并带出客户名下近期工单。
 *
 * @author 智客通开发组
 */
@Data
public class SessionBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 摘要记录 ID */
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 客户诉求 */
    private String customerDemand;

    /** 服务过程 */
    private String serviceProcess;

    /** 处理结果 */
    private String result;

    /** 待办事项 */
    private String followUp;

    /** 关键词 */
    private List<String> keywords = new ArrayList<>();

    /** 来源：AI / RULE / AGENT */
    private String source;

    /** 状态：DRAFT / ADOPTED */
    private String status;

    /** 使用的模型 */
    private String model;

    /** 客户名下近期工单 */
    private List<RecentOrder> recentOrders = new ArrayList<>();

    /** 提示信息 */
    private String note;

    /** 生成时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 客户名下近期工单摘要。
     */
    @Data
    public static class RecentOrder implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 工单 ID */
        private Long id;

        /** 工单号 */
        private String orderNo;

        /** 工单标题 */
        private String title;

        /** 状态编码 */
        private String status;

        /** 状态名称 */
        private String statusName;

        /** 优先级 */
        private String priority;

        /** 创建时间 */
        private LocalDateTime createTime;
    }
}
