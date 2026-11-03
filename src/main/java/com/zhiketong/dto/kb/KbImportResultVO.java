package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 行业知识包导入结果（R-85）。
 *
 * @author 智客通开发组
 */
@Data
public class KbImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 导入批次 ID */
    private Long taskId;

    /** 知识包名称 */
    private String packageName;

    /** 条目总数 */
    private int totalCount;

    /** 成功条数 */
    private int successCount;

    /** 失败条数 */
    private int failCount;

    /** 结果：SUCCESS / PARTIAL / FAILED */
    private String status;

    /** 成功新建的分类名称 */
    private List<String> createdCategories = new ArrayList<>();

    /** 失败明细（最多 20 条） */
    private List<String> errors = new ArrayList<>();
}
