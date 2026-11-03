package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库相似问法实体，对应表 kb_similar_question。
 * <p>
 * 同一标准问题下的同义改写、口语化表达与常见错别字形态独立成表，
 * 供 {@link com.zhiketong.service.KnowledgeMatcher} 参与相似度打分，
 * 也便于单独运营、批量导入导出与 AI 扩写。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kb_similar_question")
public class KbSimilarQuestion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 所属知识条目 ID */
    private Long entryId;

    /** 相似问法 */
    private String question;

    /** 来源：MANUAL 手工 / AI 大模型扩写 / IMPORT 知识包导入 */
    private String source;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
