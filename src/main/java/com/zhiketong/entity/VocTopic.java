package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客户之声主题实体，对应表 voc_topic（S6-C，DB-06）。
 *
 * <p>主题是一组语义相近的未命中问法的集合：{@code topicType = AUTO} 由聚类任务按
 * 关键词重合度自动归并，{@code MANUAL} 由运营人工创建后再把问法挂上来。
 * 聚合字段（条数、提问次数、渠道分布）在每次聚类或归并后统一重算，
 * 保证「热点话题榜」与主题详情看到的都是同一份快照。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("voc_topic")
public class VocTopic extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;

    /** 主题名称，自动聚类取代表关键词，可人工改名 */
    private String topicName;

    /** 来源：AUTO 自动聚类 / MANUAL 人工创建 */
    private String topicType;

    /** 代表关键词，英文逗号分隔 */
    private String keywords;

    /** 代表问法快照 */
    private String sampleQuestion;

    /** 关联未命中问法条数 */
    private Integer questionCount;

    /** 关联问法累计提问次数 */
    private Integer askTotal;

    /** 其中仍待处理的问法条数 */
    private Integer pendingCount;

    /** 其中已转为知识词条的问法条数 */
    private Integer convertedCount;

    /** 渠道分布快照，如 SITE:12,EMBED:3 */
    private String channels;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 运营备注 */
    private String remark;

    /** 最近一次参与聚类的结束时间 */
    private LocalDateTime lastClusterTime;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
