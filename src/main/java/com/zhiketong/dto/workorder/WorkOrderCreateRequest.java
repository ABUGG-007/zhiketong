package com.zhiketong.dto.workorder;

import com.zhiketong.dto.orderform.OrderFieldValueRequest;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 创建工单请求（R-49）。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单标题：4-100 个字符 */
    @NotBlank(message = "工单标题不能为空")
    @Size(min = 4, max = 100, message = "工单标题长度须为 4-100 个字符")
    private String title;

    /** 问题描述：10-2000 个字符 */
    @NotBlank(message = "问题描述不能为空")
    @Size(min = 10, max = 2000, message = "问题描述长度须为 10-2000 个字符")
    private String content;

    /**
     * 问题分类：技术问题/账号问题/订单问题/售后问题/其他。
     *
     * <p>可选：未指定时取工单模板的默认分类，两者都为空则由服务层拦截。</p>
     */
    @Size(max = 32, message = "问题分类不能超过 32 个字符")
    private String category;

    /** 优先级：P0/P1/P2/P3，默认 P2 */
    @Pattern(regexp = "P0|P1|P2|P3", message = "优先级只能为 P0/P1/P2/P3")
    private String priority;

    /** 来源：CHAT/MANUAL/PHONE，默认 MANUAL */
    @Pattern(regexp = "CHAT|MANUAL|PHONE", message = "来源只能为 CHAT/MANUAL/PHONE")
    private String source;

    /** 关联会话 ID：source=CHAT 时必填 */
    @Min(value = 1, message = "关联会话 ID 不合法")
    private Long sessionId;

    /** 创建即分配：仅 ADMIN 可传，且必须为启用状态的坐席 */
    @Min(value = 1, message = "受理坐席 ID 不合法")
    private Long assigneeId;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    /** 工单模板 ID：使用模板建单时，分类/优先级可省略，必填字段按模板校验 */
    @Min(value = 1, message = "工单模板 ID 不合法")
    private Long templateId;

    /** 自定义字段值：仅在指定模板时允许提交，字段须属于该模板 */
    @Valid
    @Size(max = 30, message = "单个工单最多提交 30 个自定义字段")
    private List<OrderFieldValueRequest> customFields;
}
