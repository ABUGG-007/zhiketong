package com.zhiketong.dto.quality;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 质检规则新增/修改请求（R-162 / R-163）。
 *
 * <p>规则编码由服务端维护：内置规则编码固定（质检引擎按编码识别「回复超时」等特殊算法），
 * 自定义规则编码自动生成，因此请求体不接收 ruleCode。</p>
 *
 * @author 智客通开发组
 */
@Data
public class QualityRuleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则名称，同租户内唯一 */
    @NotBlank(message = "规则名称不能为空")
    @Size(min = 2, max = 64, message = "规则名称长度须为 2-64 个字符")
    private String ruleName;

    /** 规则类型：SEMANTIC/SENTIMENT/SERVICE/SENSITIVE */
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "SEMANTIC|SENTIMENT|SERVICE|SENSITIVE", message = "规则类型只能为 SEMANTIC/SENTIMENT/SERVICE/SENSITIVE")
    private String ruleType;

    /** 严重程度：LOW/MEDIUM/HIGH */
    @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "严重程度只能为 LOW/MEDIUM/HIGH")
    private String severity;

    /** 命中扣分 0-30，0 表示只提示不计分 */
    @Min(value = 0, message = "扣分不能为负数")
    @Max(value = 30, message = "单条规则扣分不能超过 30")
    private Integer deductScore;

    /** 关键词表，英文逗号分隔 */
    @Size(max = 500, message = "关键词表不能超过 500 个字符")
    private String keywords;

    /** 匹配方式：CONTAINS 出现即命中 / ABSENT 未出现即命中 */
    @Pattern(regexp = "CONTAINS|ABSENT", message = "匹配方式只能为 CONTAINS 或 ABSENT")
    private String matchMode;

    /** 阈值：SLOW_REPLY 为允许的最大回复间隔分钟数 */
    @Min(value = 0, message = "阈值不能为负数")
    @Max(value = 1440, message = "阈值不能超过 1440 分钟")
    private Integer thresholdValue;

    /** 规则说明与判定口径 */
    @Size(max = 255, message = "规则说明不能超过 255 个字符")
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
