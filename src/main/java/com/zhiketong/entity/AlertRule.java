package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警规则实体，对应表 alert_rule（S6-B，AS-11）。
 *
 * <p>{@code ownerId = 0} 表示租户级规则，由管理员维护并对全租户生效；
 * {@code ownerId > 0} 表示坐席个人规则，只对该坐席接待的会话生效。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;

    /** 规则编码，同租户同归属内唯一；内置编码与指标编码同名 */
    private String ruleCode;

    /** 指标编码，扫描引擎据此选择判定算法，取值见 AlertMetric */
    private String metricCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：QUEUE/SESSION/AGENT/ORDER */
    private String ruleType;

    /** 严重程度：INFO/WARN/CRITICAL */
    private String severity;

    /** 触发阈值 */
    private Integer thresholdValue;

    /** 阈值单位：COUNT/SECONDS/MINUTES/SCORE */
    private String thresholdUnit;

    /** 冷却分钟数，同规则同目标在冷却期内只告警一次 */
    private Integer cooldownMinutes;

    /** 通知对象：ADMIN 管理员 / AGENT 责任坐席 / ALL 两者 */
    private String notifyRole;

    /** 规则归属坐席 ID，0 表示租户级规则 */
    private Long ownerId;

    /** 敏感词表，英文逗号分隔，仅 SENSITIVE_WORD 使用 */
    private String keywords;

    /** 规则说明与判定口径 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 是否内置规则：1 内置 0 自定义 */
    private Integer builtIn;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
