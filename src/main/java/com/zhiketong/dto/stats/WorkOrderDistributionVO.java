package com.zhiketong.dto.stats;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单分布统计（R-68）。
 *
 * @author 智客通开发组
 */
@Data
public class WorkOrderDistributionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单总数 */
    private long total;

    /** 按状态分布 */
    private List<Item> byStatus = new ArrayList<>();

    /** 按优先级分布 */
    private List<Item> byPriority = new ArrayList<>();

    /** 按分类分布 */
    private List<Item> byCategory = new ArrayList<>();

    /** 超时工单数 */
    private long overdueCount;

    /** 平均解决耗时（分钟） */
    private long avgResolveMinutes;

    /**
     * 分布项：key 为编码/分类名，label 为中文标签。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 编码或分类名 */
        private String key;

        /** 中文标签 */
        private String label;

        /** 数量 */
        private long count;
    }
}
