package com.zhiketong.dto.ops;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 大屏布局保存请求（R-216）。
 *
 * <p>只接收「组件显隐 + 顺序 + 密度 + 刷新间隔」，组件编码必须是服务端白名单
 * 中的值；未提交的组件按默认布局回落，避免前端漏传导致组件永久消失。</p>
 *
 * @author 智客通开发组
 */
@Data
public class ScreenLayoutRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 布局名称 */
    @Size(max = 64, message = "布局名称不能超过 64 个字符")
    private String layoutName;

    /** 展示密度：COMFORTABLE / COMPACT */
    private String density;

    /** 轮询刷新间隔（秒），5-300 */
    @Min(value = 5, message = "刷新间隔最小为 5 秒")
    @Max(value = 300, message = "刷新间隔最大为 300 秒")
    private Integer refreshSeconds;

    /** 组件配置列表 */
    @Valid
    private List<WidgetRequest> widgets = new ArrayList<>();

    /** 组件配置项。 */
    @Data
    public static class WidgetRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 组件编码 */
        @NotBlank(message = "组件编码不能为空")
        private String code;

        /** 是否展示，为空按默认布局 */
        private Boolean visible;

        /** 展示顺序，从 1 开始 */
        @Min(value = 1, message = "组件顺序最小为 1")
        @Max(value = 99, message = "组件顺序最大为 99")
        private Integer order;
    }
}
