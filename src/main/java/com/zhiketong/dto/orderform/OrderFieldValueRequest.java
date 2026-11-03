package com.zhiketong.dto.orderform;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 工单自定义字段值（创建工单时随请求提交）。
 *
 * <p>只提交「字段 ID + 值」：字段编码、名称与类型由服务端按字段定义快照落库，
 * 避免前端伪造字段名称污染历史数据。</p>
 *
 * @author 智客通开发组
 */
@Data
public class OrderFieldValueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段定义 ID */
    @NotNull(message = "字段 ID 不能为空")
    @Min(value = 1, message = "字段 ID 不合法")
    private Long fieldId;

    /** 字段值 */
    @Size(max = 2000, message = "字段值不能超过 2000 个字符")
    private String value;
}
