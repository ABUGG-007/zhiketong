package com.zhiketong.dto.voc;

import com.zhiketong.entity.VocTopic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户之声主题视图对象（R-206 / R-207 / R-211）。
 *
 * @author 智客通开发组
 */
@Data
public class VocTopicVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 主题名称 */
    private String topicName;

    /** 来源：AUTO 自动聚类 / MANUAL 人工创建 */
    private String topicType;

    /** 来源中文名 */
    private String topicTypeName;

    /** 代表关键词，英文逗号分隔 */
    private String keywords;

    /** 代表问法快照 */
    private String sampleQuestion;

    /** 关联问法条数 */
    private Integer questionCount;

    /** 累计提问次数 */
    private Integer askTotal;

    /** 仍待处理问法条数 */
    private Integer pendingCount;

    /** 已转词条问法条数 */
    private Integer convertedCount;

    /** 渠道分布快照，如 SITE:12,EMBED:3 */
    private String channels;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 排序号 */
    private Integer sort;

    /** 运营备注 */
    private String remark;

    /** 最近一次聚类时间 */
    private LocalDateTime lastClusterTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 实体转视图对象。
     *
     * @param topic 主题实体
     * @return 视图对象
     */
    public static VocTopicVO of(VocTopic topic) {
        VocTopicVO vo = new VocTopicVO();
        vo.setId(topic.getId());
        vo.setTopicName(topic.getTopicName());
        vo.setTopicType(topic.getTopicType());
        vo.setTopicTypeName(topicTypeName(topic.getTopicType()));
        vo.setKeywords(topic.getKeywords());
        vo.setSampleQuestion(topic.getSampleQuestion());
        vo.setQuestionCount(topic.getQuestionCount());
        vo.setAskTotal(topic.getAskTotal());
        vo.setPendingCount(topic.getPendingCount());
        vo.setConvertedCount(topic.getConvertedCount());
        vo.setChannels(topic.getChannels());
        vo.setStatus(topic.getStatus());
        vo.setSort(topic.getSort());
        vo.setRemark(topic.getRemark());
        vo.setLastClusterTime(topic.getLastClusterTime());
        vo.setUpdateTime(topic.getUpdateTime());
        return vo;
    }

    private static String topicTypeName(String topicType) {
        if ("MANUAL".equals(topicType)) {
            return "人工创建";
        }
        return "自动聚类";
    }
}
