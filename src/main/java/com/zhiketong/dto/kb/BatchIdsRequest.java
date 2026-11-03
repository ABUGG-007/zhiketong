package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 批量操作 ID 列表请求（R-81）。
 *
 * @author 智客通开发组
 */
@Data
public class BatchIdsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标记录 ID 列表，单次最多 200 条 */
    @NotEmpty(message = "请选择要操作的记录")
    @Size(max = 200, message = "单次最多处理 200 条")
    private List<Long> ids;
}
