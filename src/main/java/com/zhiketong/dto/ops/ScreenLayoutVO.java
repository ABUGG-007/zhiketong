package com.zhiketong.dto.ops;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 大屏布局配置视图对象（R-215 / R-217）。
 *
 * <p>组件清单由服务端白名单决定，前端只负责按 {@code widgets} 的顺序与显隐渲染，
 * 因此「能配置什么」在前后端只有一处事实来源。指标口径仍由
 * {@code /api/statistics/ops/**} 提供，本对象不参与计算。</p>
 *
 * @author 智客通开发组
 */
@Data
public class ScreenLayoutVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 布局编码 */
    private String layoutCode;

    /** 布局名称 */
    private String layoutName;

    /** 展示密度：COMFORTABLE / COMPACT */
    private String density;

    /** 展示密度中文名 */
    private String densityName;

    /** 轮询刷新间隔（秒），WS 不可用时生效 */
    private Integer refreshSeconds;

    /** 是否为默认布局（尚未保存过自定义配置） */
    private boolean defaultLayout;

    /** 最近更新时间 */
    private LocalDateTime updateTime;

    /** 组件列表，已按 order 升序排列 */
    private List<Widget> widgets = new ArrayList<>();

    /** 大屏组件。 */
    @Data
    public static class Widget implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 组件编码：REALTIME / TODAY / AGENTS / TREND / ALERTS */
        private String code;

        /** 组件名称 */
        private String name;

        /** 组件说明 */
        private String description;

        /** 是否展示 */
        private Boolean visible;

        /** 展示顺序，从 1 开始 */
        private Integer order;
    }
}
