package com.zhiketong.dto.quality;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 复核结论操作请求（R-173 确认归档 / R-174 驳回退回）。
 *
 * <p>确认归档时意见可空；驳回退回时意见必填，由服务层校验，
 * 保证「为什么退回」始终有据可查。</p>
 *
 * @author 智客通开发组
 */
@Data
public class QualityReviewActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作意见：确认备注或驳回原因 */
    @Size(max = 500, message = "意见最多 500 字")
    private String comment;
}
