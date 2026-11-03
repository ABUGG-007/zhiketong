package com.zhiketong.dto.quality;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交人工复核请求（R-172）。
 *
 * <p>复核分与逐条改判的口径：</p>
 * <ol>
 *   <li>只提交 {@code reviewScore} 不改判明细时，视为「整体调分」，复核分即生效分；</li>
 *   <li>提交了 {@code items} 改判时，复核分必须等于「100 - 改判后扣分合计」，
 *       即不传 {@code reviewScore} 由服务端按改判结果核算，传了但不一致直接拒绝，
 *       保证评分卡明细与总分始终自洽（与 S5-A 的评分口径一致）。</li>
 * </ol>
 *
 * @author 智客通开发组
 */
@Data
public class QualityReviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 人工复核得分 0-100；为空时按「100 - 改判后扣分合计」核算。
     */
    @Min(value = 0, message = "复核分最小为 0")
    @Max(value = 100, message = "复核分最大为 100")
    private Integer reviewScore;

    /** 复核意见，必填，用于回溯调分依据 */
    @NotBlank(message = "请填写复核意见")
    @Size(max = 500, message = "复核意见最多 500 字")
    private String reviewComment;

    /** 逐条改判，只传需要改动的规则行；为空表示不逐条改判 */
    @Valid
    private List<ItemReview> items = new ArrayList<>();

    /**
     * 单条规则的改判结论。
     */
    @Data
    public static class ItemReview implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 明细 ID */
        @NotNull(message = "改判明细缺少 itemId")
        private Long itemId;

        /** 改判后是否命中：1 命中 0 未命中 */
        @NotNull(message = "改判明细缺少命中结论")
        @Min(value = 0, message = "命中结论只能为 0 或 1")
        @Max(value = 1, message = "命中结论只能为 0 或 1")
        private Integer hit;

        /** 改判为命中时的扣分 0-30；改判为未命中时忽略并强制归零 */
        @Min(value = 0, message = "扣分最小为 0")
        @Max(value = 30, message = "扣分最大为 30")
        private Integer deductScore;
    }
}
