package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户信息卡（R-110）：画像、历史会话、历史工单与满意度，供坐席接起即了解客户。
 *
 * @author 智客通开发组
 */
@Data
public class CustomerCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long sessionId;

    /** 客户 ID */
    private Long userId;

    /** 客户展示名称 */
    private String userName;

    /** 手机号（脱敏展示由前端按需处理，接口按原值返回，仅坐席可见） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 注册时间 */
    private LocalDateTime registeredAt;

    /** 客户画像 */
    private Profile profile;

    /** 本次会话信息 */
    private Current current;

    /** 历史会话（最近 5 条，含本次） */
    private List<SessionItem> recentSessions = new ArrayList<>();

    /** 历史工单（最近 5 条） */
    private List<OrderItem> recentOrders = new ArrayList<>();

    /** 工单状态分布 */
    private OrderStats orderStats;

    /** 使用提示 */
    private String note;

    /**
     * 客户画像：累计咨询、满意度与活跃时间。
     */
    @Data
    public static class Profile implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 累计会话数 */
        private Long totalSessions;

        /** 已结束会话数 */
        private Long closedSessions;

        /** 人工接待会话数 */
        private Long humanSessions;

        /** 首次咨询时间 */
        private LocalDateTime firstSessionTime;

        /** 最近咨询时间 */
        private LocalDateTime lastSessionTime;

        /** 平均满意度（无评价时为 null） */
        private BigDecimal avgRating;

        /** 有效评价数 */
        private Long ratingCount;

        /** 是否为老客户（累计会话数大于 1） */
        private Boolean returning;
    }

    /**
     * 本次会话摘要：诉求与满意度。
     */
    @Data
    public static class Current implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 会话状态 */
        private String status;

        /** 状态名称 */
        private String statusName;

        /** 本次诉求（取进线摘要，未生成时为空） */
        private String demand;

        /** 本次诉求关键词 */
        private List<String> keywords = new ArrayList<>();

        /** 本次满意度 */
        private Integer rating;

        /** 本次评价内容 */
        private String ratingComment;
    }

    /**
     * 历史会话条目。
     */
    @Data
    public static class SessionItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String sessionNo;

        private String status;

        private String statusName;

        private String agentName;

        /** 消息条数 */
        private Integer messageCount;

        /** 满意度 */
        private Integer rating;

        private LocalDateTime createTime;

        private LocalDateTime endTime;
    }

    /**
     * 历史工单条目。
     */
    @Data
    public static class OrderItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String orderNo;

        private String title;

        private String status;

        private String statusName;

        private String priority;

        private LocalDateTime createTime;
    }

    /**
     * 工单状态分布。
     */
    @Data
    public static class OrderStats implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 工单总数 */
        private Long total;

        /** 处理中（待分配/待处理/处理中/重开） */
        private Long open;

        /** 已解决或已关闭 */
        private Long resolved;
    }
}
