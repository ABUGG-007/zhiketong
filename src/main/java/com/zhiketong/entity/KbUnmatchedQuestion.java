package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识库未命中问题实体，对应表 kb_unmatched_question。
 * <p>
 * 同一条问句在归一化后聚合为一行，{@code askCount} 累计出现次数；
 * 运营人员在「知识运营」页可一键转为知识词条或忽略。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kb_unmatched_question")
public class KbUnmatchedQuestion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 访客原始问法 */
    private String question;

    /** 归一化问法，聚合去重键 */
    private String normalizedQuestion;

    /** 来源渠道：SITE 站内会话 / EMBED 网页接入 */
    private String channel;

    /** 最近一次出现的会话 ID */
    private Long sessionId;

    /** 最近一次提问的用户 ID */
    private Long userId;

    /** 累计出现次数 */
    private Integer askCount;

    /** 最近一次最佳匹配得分 */
    private BigDecimal lastScore;

    /** 处理状态：PENDING / CONVERTED / IGNORED */
    private String status;

    /** 转成的知识条目 ID */
    private Long entryId;

    /** 归属的客户之声主题 ID（S6-C），null 表示尚未聚类 */
    private Long topicId;

    /** 归属相似度百分比 0-100，人工创建主题下为 null */
    private Integer clusterScore;

    /** 首次出现时间 */
    private LocalDateTime firstAskTime;

    /** 最近出现时间 */
    private LocalDateTime lastAskTime;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
