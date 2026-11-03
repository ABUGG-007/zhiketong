package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 套餐权益实体，对应表 tenant_plan（S4-A）。
 *
 * <p>平台级字典表，不参与租户隔离。额度字段为 {@code -1} 表示不限；
 * {@code features} 用英文竖线 {@code |} 分隔功能点，供管理端展示与套餐对比。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tenant_plan")
public class TenantPlan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 套餐编码：trial / standard / professional */
    private String planCode;

    /** 套餐名称 */
    private String planName;

    /** 一句话定位 */
    private String tagline;

    /** 价格（元），0 表示免费 */
    private BigDecimal price;

    /** 计费周期：月 / 年 / 14 天 */
    private String period;

    /** 坐席账号上限，-1 不限 */
    private Integer seatLimit;

    /** 每自然月会话上限，-1 不限 */
    private Integer sessionLimit;

    /** 每自然月工单上限，-1 不限 */
    private Integer orderLimit;

    /** 知识库条目上限，-1 不限 */
    private Integer kbLimit;

    /** 每自然月 AI 应答次数上限，-1 不限 */
    private Integer aiLimit;

    /** 功能点清单，英文竖线分隔 */
    private String features;

    /** 是否推荐：1 是 0 否 */
    private Integer recommended;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
