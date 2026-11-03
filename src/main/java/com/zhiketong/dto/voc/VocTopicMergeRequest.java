package com.zhiketong.dto.voc;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 客户之声主题合并请求（R-212）。
 *
 * <p>把若干主题下的未命中问法统一挂到目标主题，源主题随后逻辑删除；
 * 用于自动聚类把同一诉求拆成多个主题时的人工收敛。</p>
 *
 * @author 智客通开发组
 */
@Data
public class VocTopicMergeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标主题 ID（保留） */
    @NotNull(message = "目标主题不能为空")
    private Long targetTopicId;

    /** 待合并的源主题 ID 列表 */
    @NotEmpty(message = "请选择要合并的主题")
    @Size(max = 50, message = "单次最多合并 50 个主题")
    private List<Long> sourceTopicIds;
}
