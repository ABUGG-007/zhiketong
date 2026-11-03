package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 批量新增相似问法请求（R-74）。
 *
 * @author 智客通开发组
 */
@Data
public class SimilarQuestionCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 相似问法列表，单次最多 20 条 */
    @NotEmpty(message = "相似问法不能为空")
    @Size(max = 20, message = "单次最多新增 20 条相似问法")
    private List<@Size(min = 2, max = 255, message = "相似问法长度须为 2-255 个字符") String> questions;

    /** 来源：MANUAL 手工 / AI 大模型扩写，默认 MANUAL */
    private String source;
}
