package com.zhiketong.dto.quality;

import lombok.Data;

import java.io.Serializable;

/**
 * 质检任务执行结果（R-170 手动触发 / 定时任务日志）。
 *
 * @author 智客通开发组
 */
@Data
public class QualityScanResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功获取分布式锁，false 表示已有实例在跑 */
    private boolean locked;

    /** 本轮检出的待质检会话数 */
    private int candidateCount;

    /** 质检成功条数 */
    private int processedCount;

    /** 质检失败条数 */
    private int failedCount;

    /** 结果说明 */
    private String message;
}
