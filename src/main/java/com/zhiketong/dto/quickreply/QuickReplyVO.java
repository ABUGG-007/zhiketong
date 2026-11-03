package com.zhiketong.dto.quickreply;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 快捷回复视图对象（R-89）。
 *
 * @author 智客通开发组
 */
@Data
public class QuickReplyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 话术 ID */
    private Long id;

    /** 范围：PUBLIC 公共 / PERSONAL 个人 */
    private String scope;

    /** 所有人 ID */
    private Long ownerId;

    /** 话术分类 */
    private String category;

    /** 话术标题 */
    private String title;

    /** 话术正文 */
    private String content;

    /** 排序号 */
    private Integer sort;

    /** 累计使用次数 */
    private Integer useCount;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 是否本人可编辑（个人话术本人、公共话术管理员） */
    private Boolean editable;

    /** 创建时间 */
    private LocalDateTime createTime;
}
