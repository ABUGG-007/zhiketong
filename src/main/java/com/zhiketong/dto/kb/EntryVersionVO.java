package com.zhiketong.dto.kb;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识词条版本视图对象（R-77）。
 *
 * @author 智客通开发组
 */
@Data
public class EntryVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本记录 ID */
    private Long id;

    /** 词条 ID */
    private Long entryId;

    /** 版本号 */
    private Integer versionNo;

    /** 变更类型 */
    private String changeType;

    /** 变更类型中文说明 */
    private String changeTypeDesc;

    /** 变更前快照（JSON） */
    private String beforeContent;

    /** 变更后快照（JSON） */
    private String afterContent;

    /** 操作人名称 */
    private String operatorName;

    /** 变更说明 */
    private String remark;

    /** 变更时间 */
    private LocalDateTime createTime;
}
