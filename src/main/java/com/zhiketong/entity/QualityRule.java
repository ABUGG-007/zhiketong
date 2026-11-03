package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 质检规则实体，对应表 quality_rule。
 * <p>
 * ruleType 取值见 {@link com.zhiketong.enums.QualityRuleType}，
 * severity 取值见 {@link com.zhiketong.enums.QualitySeverity}。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("quality_rule")
public class QualityRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;

    /** 规则编码，同租户内唯一；内置编码由质检引擎识别特殊算法 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：SEMANTIC/SENTIMENT/SERVICE/SENSITIVE */
    private String ruleType;

    /** 严重程度：LOW/MEDIUM/HIGH */
    private String severity;

    /** 命中扣分 0-30，0 表示只提示不计分 */
    private Integer deductScore;

    /** 关键词表，英文逗号分隔 */
    private String keywords;

    /** 匹配方式：CONTAINS 出现即命中 / ABSENT 未出现即命中 */
    private String matchMode;

    /** 阈值：SLOW_REPLY 为最大回复间隔分钟数，其余为 0 */
    private Integer thresholdValue;

    /** 规则说明与判定口径 */
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
