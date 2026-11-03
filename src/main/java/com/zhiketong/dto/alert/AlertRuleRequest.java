package com.zhiketong.dto.alert;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 告警规则新增/修改请求（R-181 / R-182 / R-191 / R-192）。
 *
 * @author 智客通开发组
 */
@Data
public class AlertRuleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(min = 2, max = 64, message = "规则名称长度须为 2-64 个字符")
    private String ruleName;

    /** 指标编码：决定扫描引擎判定算法，取值见 AlertMetric */
    @NotBlank(message = "指标编码不能为空")
    private String metricCode;

    /** 规则类型：由指标编码推导（传入值仅作兼容，不参与入库） */
    private String ruleType;

    /** 严重程度：INFO/WARN/CRITICAL */
    private String severity;

    /** 触发阈值 */
    @NotNull(message = "阈值不能为空")
    @Min(value = 0, message = "阈值不能小于 0")
    @Max(value = 604800, message = "阈值不能超过 604800")
    private Integer thresholdValue;

    /** 阈值单位：COUNT/SECONDS/MINUTES/SCORE */
    private String thresholdUnit;

    /** 冷却分钟数 */
    @Min(value = 0, message = "冷却分钟数不能小于 0")
    @Max(value = 1440, message = "冷却分钟数不能超过 1440")
    private Integer cooldownMinutes;

    /** 通知对象：ADMIN/AGENT/ALL */
    private String notifyRole;

    /** 敏感词表，英文逗号分隔，仅 SENSITIVE_WORD 使用 */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keywords;

    /** 规则说明与判定口径 */
    @Size(max = 255, message = "规则说明不能超过 255 个字符")
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
