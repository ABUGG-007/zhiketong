package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识词条版本实体，对应表 kb_entry_version。
 * <p>
 * 只追加不改写：记录变更前后完整快照（JSON），供审计与一键回滚；
 * 因此不继承 {@link BaseEntity}，也没有逻辑删除列。
 *
 * @author 智客通开发组
 */
@Data
@TableName("kb_entry_version")
public class KbEntryVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识条目 ID */
    private Long entryId;

    /** 版本号，从 1 递增 */
    private Integer versionNo;

    /** 变更类型：CREATE/UPDATE/STATUS/DELETE/ROLLBACK */
    private String changeType;

    /** 变更前快照（JSON） */
    private String beforeContent;

    /** 变更后快照（JSON） */
    private String afterContent;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称（快照） */
    private String operatorName;

    /** 变更说明 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
