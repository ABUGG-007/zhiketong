package com.zhiketong.dto.chat;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会话评分请求（R-39）。
 *
 * @author 智客通开发组
 */
@Data
public class RatingRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 满意度星级 1-5 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分须为 1-5")
    @Max(value = 5, message = "评分须为 1-5")
    private Integer rating;

    /** 评价内容 */
    @Size(max = 255, message = "评价内容不能超过 255 个字符")
    private String ratingComment;
}
