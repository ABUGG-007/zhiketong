package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户用量快照实体，对应表 tenant_usage（S4-B）。
 *
 * <p>只保存按自然月聚合的历史快照，用于趋势展示与对账；
 * 套餐超额拦截一律以业务表实时 COUNT 为准，不读取本表，
 * 避免快照延迟导致误放行或误拦截。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tenant_usage")
public class TenantUsage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 统计周期，格式 yyyy-MM */
    private String period;

    /** 已启用坐席账号数 */
    private Integer seatUsed;

    /** 周期内创建会话数 */
    private Integer sessionUsed;

    /** 周期内创建工单数 */
    private Integer orderUsed;

    /** 知识库条目数 */
    private Integer kbUsed;

    /** 周期内 AI 应答调用次数 */
    private Integer aiUsed;

    /** 快照生成时间 */
    private LocalDateTime snapshotTime;
}
