package com.zhiketong.dto.audit;

import com.zhiketong.entity.AuditLog;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计列表视图对象（S4-C，R-158/R-159）。
 *
 * <p>列表不返回变更前后快照，避免大字段拖慢列表；详情接口另取
 * {@link AuditLogDetailVO}。</p>
 *
 * @author 智客通开发组
 */
@Data
public class AuditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String tenantName;
    private String module;
    private String moduleName;
    private String action;
    private String actionName;
    private String targetType;
    private Long targetId;
    private String targetName;
    private String detail;
    private String operatorName;
    private String operatorRole;
    private String ip;
    private LocalDateTime createTime;

    /**
     * 实体转 VO，模块与动作自动补中文描述。
     *
     * @param log 审计实体
     * @return 列表视图对象
     */
    public static AuditLogVO of(AuditLog log) {
        if (log == null) {
            return null;
        }
        AuditLogVO vo = new AuditLogVO();
        vo.setId(log.getId());
        vo.setTenantId(log.getTenantId());
        vo.setModule(log.getModule());
        AuditModule module = AuditModule.fromCode(log.getModule());
        vo.setModuleName(module == null ? log.getModule() : module.getDescription());
        vo.setAction(log.getAction());
        AuditAction action = AuditAction.fromCode(log.getAction());
        vo.setActionName(action == null ? log.getAction() : action.getDescription());
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setTargetName(log.getTargetName());
        vo.setDetail(log.getDetail());
        vo.setOperatorName(log.getOperatorName());
        vo.setOperatorRole(log.getOperatorRole());
        vo.setIp(log.getIp());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
