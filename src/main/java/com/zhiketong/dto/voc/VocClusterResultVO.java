package com.zhiketong.dto.voc;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 未命中聚类执行结果（R-213）。
 *
 * @author 智客通开发组
 */
@Data
public class VocClusterResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否拿到执行锁，false 表示有一轮聚类正在执行 */
    private boolean locked;

    /** 本轮参与归类的问法条数 */
    private int scannedCount;

    /** 命中已有主题的条数 */
    private int assignedCount;

    /** 新建主题的条数 */
    private int createdCount;

    /** 未参与归类的问法条数（已聚类且本轮未重建） */
    private int skippedCount;

    /** 本轮结束后租户内的主题总数 */
    private int topicCount;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /** 执行时间 */
    private LocalDateTime clusterTime;
}
