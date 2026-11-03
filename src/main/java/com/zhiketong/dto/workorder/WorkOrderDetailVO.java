package com.zhiketong.dto.workorder;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单详情（R-53）：工单信息与当前登录人可执行动作。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单信息 */
    private WorkOrderVO workOrder;

    /** 当前登录人可执行的动作列表，如 ACCEPT、PROCESS、RESOLVE */
    private List<String> allowedActions = new ArrayList<>();
}
