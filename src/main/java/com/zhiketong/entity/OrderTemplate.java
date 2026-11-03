package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单模板实体，对应表 order_template（S3-A）。
 *
 * <p>模板把一组自定义字段固化下来，创建工单时选择模板即可按业务场景
 * 收集结构化信息（如退款工单需要订单号、退款金额、到账方式）。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_template")
public class OrderTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 模板名称 */
    private String templateName;

    /** 默认问题分类，为空表示创建时由用户选择 */
    private String category;

    /** 默认优先级 P0-P3，为空表示按 P2 */
    private String defaultPriority;

    /** 默认技能组 ID，为空表示不限 */
    private Long skillGroupId;

    /** 绑定字段 ID，英文逗号分隔，顺序即表单顺序 */
    private String fieldIds;

    /** 模板说明 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
