package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 大屏布局配置实体，对应表 screen_layout（S6-C，DB-07）。
 *
 * <p>只保存「怎么摆」不保存「算什么」：组件显隐、顺序与展示密度以 JSON 数组
 * 存在 {@code widgets} 列中，指标口径仍由 {@code /api/statistics/ops/**} 统一提供，
 * 避免配置化之后大屏与统计页出现两套口径。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("screen_layout")
public class ScreenLayout extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;

    /** 布局编码，default 为租户默认大屏 */
    private String layoutCode;

    /** 布局名称 */
    private String layoutName;

    /** 组件配置 JSON 数组：[{code,visible,order}] */
    private String widgets;

    /** 展示密度：COMFORTABLE 舒适 / COMPACT 紧凑 */
    private String density;

    /** 轮询刷新间隔（秒），WS 不可用时生效 */
    private Integer refreshSeconds;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
