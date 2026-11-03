package com.zhiketong.dto.voc;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 客户之声主题新增/修改请求（R-208 / R-209）。
 *
 * @author 智客通开发组
 */
@Data
public class VocTopicRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主题名称 */
    @NotBlank(message = "主题名称不能为空")
    @Size(min = 2, max = 64, message = "主题名称长度须为 2-64 个字符")
    private String topicName;

    /** 代表关键词，英文逗号分隔，最多 10 个 */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keywords;

    /** 代表问法示例 */
    @Size(max = 255, message = "代表问法不能超过 255 个字符")
    private String sampleQuestion;

    /** 运营备注 */
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
}
