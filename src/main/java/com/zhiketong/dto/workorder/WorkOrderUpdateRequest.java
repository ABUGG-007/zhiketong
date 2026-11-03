package com.zhiketong.dto.workorder;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 编辑工单请求（R-54）：字段传则更新。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单标题：4-100 个字符 */
    @Size(min = 4, max = 100, message = "工单标题长度须为 4-100 个字符")
    private String title;

    /** 问题描述：10-2000 个字符 */
    @Size(min = 10, max = 2000, message = "问题描述长度须为 10-2000 个字符")
    private String content;

    /** 问题分类 */
    @Pattern(regexp = "技术问题|账号问题|订单问题|售后问题|其他",
            message = "问题分类只能为技术问题/账号问题/订单问题/售后问题/其他")
    private String category;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
