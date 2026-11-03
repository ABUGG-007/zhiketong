package com.zhiketong.dto.platform;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 平台端变更租户套餐请求（S4-B，PL-03）。
 *
 * <p>降级时若已有用量超过新套餐额度，接口直接拒绝并在提示中给出
 * 具体超限项，避免降级后租户业务当场不可用。</p>
 *
 * @author 智客通开发组
 */
@Data
public class TenantPlanChangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标套餐编码 */
    @NotBlank(message = "套餐不能为空")
    @Size(max = 32, message = "套餐编码长度不能超过 32")
    private String planCode;
}
