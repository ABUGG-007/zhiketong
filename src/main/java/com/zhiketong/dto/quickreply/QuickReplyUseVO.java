package com.zhiketong.dto.quickreply;

import lombok.Data;

import java.io.Serializable;

/**
 * 快捷回复使用结果（R-95）。
 * <p>
 * 坐席端点击话术后把 {@code content} 插入回复框，同时后端累加使用次数，
 * 便于按热度排序推荐常用话术。
 *
 * @author 智客通开发组
 */
@Data
public class QuickReplyUseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 话术 ID */
    private Long id;

    /** 话术正文 */
    private String content;

    /** 累加后的使用次数 */
    private Integer useCount;
}
