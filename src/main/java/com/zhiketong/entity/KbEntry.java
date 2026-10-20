package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库条目实体，对应表 kb_entry。
 * <p>
 * keywords 为英文逗号分隔的关键词串，供 AI 对话时做关键词匹配；
 * hitCount 为累计命中次数，用于知识库命中排行。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kb_entry")
public class KbEntry extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属分类 ID */
    private Long categoryId;

    /** 标准问题 */
    private String question;

    /** 标准答案 */
    private String answer;

    /** 关键词，英文逗号分隔 */
    private String keywords;

    /** 累计命中次数 */
    private Integer hitCount;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
