package com.zhiketong.dto.workorder;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 分配工单请求（R-55），同时用于管理员转派。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderAssignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 受理坐席 ID：必须为启用状态的 AGENT */
    @NotNull(message = "受理坐席不能为空")
    @Min(value = 1, message = "受理坐席 ID 不合法")
    private Long assigneeId;

    /** 分配说明 */
    @Size(max = 255, message = "分配说明不能超过 255 个字符")
    private String remark;
}
