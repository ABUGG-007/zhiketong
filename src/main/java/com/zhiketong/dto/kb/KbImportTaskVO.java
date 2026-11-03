package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识包导入批次视图对象（R-86）。
 *
 * @author 智客通开发组
 */
@Data
public class KbImportTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次 ID */
    private Long id;

    /** 知识包名称 */
    private String packageName;

    /** 导入来源 */
    private String source;

    /** 条目总数 */
    private Integer totalCount;

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failCount;

    /** 结果状态 */
    private String status;

    /** 失败原因摘要 */
    private String errorMessage;

    /** 导入时间 */
    private LocalDateTime createTime;
}
