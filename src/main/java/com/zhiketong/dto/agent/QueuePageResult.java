package com.zhiketong.dto.agent;

import com.zhiketong.common.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 排队会话分页结果：在通用分页结构上追加全局排队数。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueuePageResult extends PageResult<QueueSessionVO> {

    private static final long serialVersionUID = 1L;

    /** 全局排队数 */
    private Integer queueSize;
}
