package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Size;

/**
 * 保存 / 采纳服务小结请求（R-109）。
 * <p>
 * 提交即视为坐席确认，服务小结置为 ADOPTED 归档。
 *
 * @author 智客通开发组
 */
@Data
public class SummarySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户诉求 */
    @Size(max = 500, message = "客户诉求不能超过 500 个字符")
    private String customerDemand;

    /** 服务过程 */
    @Size(max = 1000, message = "服务过程不能超过 1000 个字符")
    private String serviceProcess;

    /** 处理结果 */
    @Size(max = 500, message = "处理结果不能超过 500 个字符")
    private String result;

    /** 待办事项 */
    @Size(max = 500, message = "待办事项不能超过 500 个字符")
    private String followUp;

    /** 关键词，最多 8 个 */
    private List<String> keywords = new ArrayList<>();
}
