package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用操作审计实体，对应表 audit_log（S4-C，PL-06）。
 *
 * <p>只追加、不改写：无 update_time 与 deleted 字段，因此不继承 {@link BaseEntity}。
 * tenant_id 由多租户拦截器按当前上下文自动写入，平台管理员操作写入被操作租户。</p>
 *
 * @author 智客通开发组
 */
@Data
@TableName("audit_log")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID，1 为平台默认租户 */
    private Long tenantId;

    /** 业务模块编码：TENANT/USER/KB/EMBED/AI/ORDER_RULE */
    private String module;

    /** 动作编码：CREATE/UPDATE/DELETE/ENABLE/DISABLE/CHANGE_PLAN/RENEW/RESET_PASSWORD/ROTATE_SECRET */
    private String action;

    /** 对象类型编码，如 SYS_USER、KB_ENTRY */
    private String targetType;

    /** 对象 ID */
    private Long targetId;

    /** 对象名称快照（租户名、账号、词条问题等） */
    private String targetName;

    /** 变更摘要，可直接展示的中文描述 */
    private String detail;

    /** 变更前快照（JSON，敏感字段已脱敏） */
    private String beforeData;

    /** 变更后快照（JSON，敏感字段已脱敏） */
    private String afterData;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称快照 */
    private String operatorName;

    /** 操作人角色编码 */
    private String operatorRole;

    /** 操作来源 IP */
    private String ip;

    /** 浏览器 UA */
    private String userAgent;

    /** HTTP 方法 */
    private String requestMethod;

    /** 请求路径 */
    private String requestUri;

    /** 操作时间 */
    private LocalDateTime createTime;
}
