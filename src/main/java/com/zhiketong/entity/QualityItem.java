package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 质检明细实体，对应表 quality_item。
 * <p>
 * 每条规则一行，命中与未命中都记录，便于人工复核时回看「哪些规则过、哪些没过」。
 * 规则名称与类型为快照，规则被删除后明细仍可读。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("quality_item")
public class QualityItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;

    /** 质检记录 ID */
    private Long recordId;

    /** 会话 ID，便于按会话回溯 */
    private Long sessionId;

    /** 规则 ID，规则删除后保留快照 */
    private Long ruleId;

    /** 规则编码快照 */
    private String ruleCode;

    /** 规则名称快照 */
    private String ruleName;

    /** 规则类型快照 */
    private String ruleType;

    /** 严重程度快照 */
    private String severity;

    /** 是否命中：1 命中 0 未命中 */
    private Integer hit;

    /** 实际扣分，未命中为 0 */
    private Integer deductScore;

    /** 命中证据（引用会话原文或轮次描述） */
    private String evidence;

    /** 改进建议 */
    private String suggestion;

    /** 判定来源：RULE/AI */
    private String source;

    /** 人工改判后的命中结论：1 命中 0 未命中，为空表示未改判 */
    private Integer reviewHit;

    /** 人工改判后的扣分，为空表示未改判 */
    private Integer reviewDeduct;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;

    /**
     * 生效命中结论：人工改判优先，未改判时取自动判定。
     *
     * @return 1 命中 0 未命中
     */
    public int effectiveHit() {
        if (reviewHit != null) {
            return reviewHit;
        }
        return hit == null ? 0 : hit;
    }

    /**
     * 生效扣分：人工改判优先，未改判时取自动扣分。
     *
     * @return 实际扣分
     */
    public int effectiveDeduct() {
        if (reviewDeduct != null) {
            return reviewDeduct;
        }
        return deductScore == null ? 0 : deductScore;
    }
}
