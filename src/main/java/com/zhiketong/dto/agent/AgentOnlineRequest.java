package com.zhiketong.dto.agent;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serializable;

/**
 * 坐席上线请求。
 */
@Data
public class AgentOnlineRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 并发接待上限，留空使用系统默认值 */
    @Min(value = 1, message = "并发上限不能小于 1")
    @Max(value = 20, message = "并发上限不能大于 20")
    private Integer maxConcurrent;
}
