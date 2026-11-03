package com.zhiketong.dto.voc;

import com.zhiketong.dto.kb.UnmatchedQuestionVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户之声主题详情（R-207）。
 *
 * <p>主题聚合指标 + 归属于该主题的未命中问法，运营据此判断「这组人在问什么、
 * 是否值得沉淀为知识词条」。</p>
 *
 * @author 智客通开发组
 */
@Data
public class VocTopicDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主题指标 */
    private VocTopicVO topic;

    /** 归属问法列表，按累计提问次数倒序 */
    private List<UnmatchedQuestionVO> questions = new ArrayList<>();
}
