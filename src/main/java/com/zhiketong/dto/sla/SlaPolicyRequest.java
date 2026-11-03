package com.zhiketong.dto.sla;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 工单 SLA 策略新增/修改请求（R-129 / R-130）。
 *
 * <p>三级时限均以「分钟」为单位，取 0 表示不启用该级：首响、回复间隔、解决可独立开关。
 * 预警阈值 {@code warnPercent} 表示已消耗时长达到时限的该百分比时触发预警（即剩余不足 (100-warnPercent)% 时预警）。</p>
 *
 * @author 智客通开发组
 */
@Data
public class SlaPolicyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 策略名称，同租户内唯一 */
    @NotBlank(message = "策略名称不能为空")
    @Size(min = 2, max = 64, message = "策略名称长度须为 2-64 个字符")
    private String policyName;

    /** 适用优先级：P0/P1/P2/P3 */
    @NotBlank(message = "适用优先级不能为空")
    @Pattern(regexp = "P0|P1|P2|P3", message = "适用优先级只能为 P0/P1/P2/P3")
    private String priority;

    /** 首次响应时限（分钟），0 表示不启用该级 */
    @Min(value = 0, message = "首次响应时限不能为负数")
    @Max(value = 525600, message = "首次响应时限不能超过一年（525600 分钟）")
    private Integer firstResponseMinutes;

    /** 每次回复间隔时限（分钟），0 表示不启用该级 */
    @Min(value = 0, message = "回复间隔时限不能为负数")
    @Max(value = 525600, message = "回复间隔时限不能超过一年（525600 分钟）")
    private Integer replyIntervalMinutes;

    /** 解决时限（分钟），0 表示不启用该级 */
    @Min(value = 0, message = "解决时限不能为负数")
    @Max(value = 525600, message = "解决时限不能超过一年（525600 分钟）")
    private Integer resolveMinutes;

    /** 预警阈值（百分比）：已消耗时长达到时限的该百分比时预警，取值 1-99 */
    @Min(value = 1, message = "预警阈值取值范围为 1-99")
    @Max(value = 99, message = "预警阈值取值范围为 1-99")
    private Integer warnPercent;

    /** 超时后再经过多少分钟仍未解决则升级通知上级，0 表示超时即升级 */
    @Min(value = 0, message = "升级延迟不能为负数")
    @Max(value = 525600, message = "升级延迟不能超过一年（525600 分钟）")
    private Integer escalateMinutes;

    /** 升级通知对象角色，当前支持 ADMIN */
    @Pattern(regexp = "ADMIN", message = "升级通知角色当前仅支持 ADMIN")
    private String escalateRole;

    /** 是否为该优先级的默认策略：1 是 0 否，默认 0 */
    @Min(value = 0, message = "默认标记只能为 0 或 1")
    @Max(value = 1, message = "默认标记只能为 0 或 1")
    private Integer defaultFlag;

    /** 策略说明 */
    @Size(max = 255, message = "策略说明不能超过 255 个字符")
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用，默认 1 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
