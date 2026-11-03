package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单 SLA 策略实体，对应表 order_sla_policy（S3-B）。
 *
 * <p>按优先级配置三级时限（首次响应 / 每次回复间隔 / 解决）、预警阈值与超时升级规则；
 * 新建工单按优先级匹配 {@code defaultFlag=1} 且启用的策略，策略改名或停用不影响已在跑的
 * 工单——计时记录 {@link OrderSlaRecord} 保存的是策略快照。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_sla_policy")
public class OrderSlaPolicy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，1 为平台默认 */
    private Long tenantId;

    /** 策略名称，如 P1 标准时效 */
    private String policyName;

    /** 适用优先级：P0/P1/P2/P3 */
    private String priority;

    /** 首次响应时限（分钟），0 表示不启用该级 */
    private Integer firstResponseMinutes;

    /** 每次回复间隔时限（分钟），0 表示不启用该级 */
    private Integer replyIntervalMinutes;

    /** 解决时限（分钟），0 表示不启用该级 */
    private Integer resolveMinutes;

    /** 已消耗时长达到时限的该百分比时预警（即剩余不足 (100-warnPercent)% 时预警），取值 1-99 */
    private Integer warnPercent;

    /** 超时后再经过多少分钟仍未解决则升级通知上级，0 表示超时即升级 */
    private Integer escalateMinutes;

    /** 升级通知对象角色，当前支持 ADMIN */
    private String escalateRole;

    /** 是否为该优先级的默认策略：1 是 0 否；同优先级同租户仅允许一条默认 */
    private Integer defaultFlag;

    /** 策略说明 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用（停用后新建工单不再使用该策略） */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
