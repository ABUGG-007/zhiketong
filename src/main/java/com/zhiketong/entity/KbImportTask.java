package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识包导入批次实体，对应表 kb_import_task。
 * <p>
 * 每次导入行业知识包（JSON）记录一条批次，便于运营复核导入结果。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kb_import_task")
public class KbImportTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 知识包名称 */
    private String packageName;

    /** 导入来源：JSON / CSV */
    private String source;

    /** 条目总数 */
    private Integer totalCount;

    /** 成功导入条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failCount;

    /** 结果：SUCCESS / PARTIAL / FAILED */
    private String status;

    /** 失败原因摘要 */
    private String errorMessage;

    /** 导入人 ID */
    private Long operatorId;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
