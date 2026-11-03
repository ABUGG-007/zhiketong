package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 未命中问题视图对象（R-79）。
 *
 * @author 智客通开发组
 */
@Data
public class UnmatchedQuestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 访客原始问法 */
    private String question;

    /** 来源渠道：SITE / EMBED */
    private String channel;

    /** 累计出现次数 */
    private Integer askCount;

    /** 最近一次最佳匹配得分 */
    private BigDecimal lastScore;

    /** 处理状态：PENDING / CONVERTED / IGNORED */
    private String status;

    /** 转成的知识条目 ID */
    private Long entryId;

    /** 转成的知识条目标准问题 */
    private String entryQuestion;

    /** 归属的客户之声主题 ID（S6-C） */
    private Long topicId;

    /** 归属的客户之声主题名称 */
    private String topicName;

    /** 归属相似度百分比 0-100 */
    private Integer clusterScore;

    /** 首次出现时间 */
    private LocalDateTime firstAskTime;

    /** 最近出现时间 */
    private LocalDateTime lastAskTime;
}
