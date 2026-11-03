package com.zhiketong.dto.plan;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 套餐与用量响应对象。
 *
 * <p>同时承载首页/套餐页所需的四类信息：当前套餐、额度用量、运营 KPI、
 * 全部套餐对比。所有数值均来自实时统计，不做静态假数据。</p>
 *
 * @author 智客通开发组
 */
@Data
public class PlanUsageVO {

    /** 租户编码 */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 租户状态：ENABLED / DISABLED / EXPIRED */
    private String tenantStatus;

    /** 租户服务到期时间，为空表示不限 */
    private LocalDateTime expireTime;

    /** 当前套餐编码 */
    private String planCode;

    /** 当前套餐名称 */
    private String planName;

    /** 当前套餐定位 */
    private String tagline;

    /** 价格（元） */
    private int price;

    /** 计费周期 */
    private String period;

    /** 当前套餐能力清单 */
    private List<String> features = new ArrayList<>();

    /** 统计周期标签，如 2026-10 */
    private String periodLabel;

    /** 额度用量项 */
    private List<UsageItem> items = new ArrayList<>();

    /** 运营 KPI */
    private List<KpiItem> kpis = new ArrayList<>();

    /** 全部套餐（含当前与推荐标记） */
    private List<PlanOption> plans = new ArrayList<>();

    /** 计费说明与口径备注 */
    private String billingNote;

    /** 最近一次用量快照时间 */
    private LocalDateTime snapshotTime;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /**
     * 单项额度用量。
     */
    @Data
    public static class UsageItem {

        /** 额度编码 */
        private String code;

        /** 展示名称 */
        private String name;

        /** 已使用 */
        private long used;

        /** 上限，-1 表示不限 */
        private long limit;

        /** 单位 */
        private String unit;

        /** 使用百分比（不限时为 0） */
        private int percent;

        /** 水位：normal/warning/danger */
        private String level;

        /** 补充说明 */
        private String hint;
    }

    /**
     * 运营 KPI。value 为已格式化字符串，便于前端直接展示。
     */
    @Data
    public static class KpiItem {

        /** KPI 编码 */
        private String code;

        /** 展示名称 */
        private String name;

        /** 展示值 */
        private String value;

        /** 单位或后缀 */
        private String unit;

        /** 计算口径说明 */
        private String hint;

        /** 语气：teal/indigo/amber/red/slate */
        private String tone;
    }

    /**
     * 套餐对比项。
     */
    @Data
    public static class PlanOption {

        private String code;
        private String name;
        private String tagline;
        private int price;
        private String period;
        private boolean recommended;
        private boolean currentPlan;
        private int seatLimit;
        private int sessionLimit;
        private int orderLimit;
        private int kbLimit;
        private int aiResolutionLimit;
        private List<String> features = new ArrayList<>();
    }
}
