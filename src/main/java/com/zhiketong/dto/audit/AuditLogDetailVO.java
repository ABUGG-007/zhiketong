package com.zhiketong.dto.audit;

import com.zhiketong.entity.AuditLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作审计详情视图对象（S4-C，R-158/R-159）：在列表字段基础上补充变更前后快照。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogDetailVO extends AuditLogVO {

    private static final long serialVersionUID = 1L;

    /** 变更前快照（JSON，敏感字段已脱敏） */
    private String beforeData;

    /** 变更后快照（JSON，敏感字段已脱敏） */
    private String afterData;

    /** 浏览器 UA */
    private String userAgent;

    /** HTTP 方法 */
    private String requestMethod;

    /** 请求路径 */
    private String requestUri;

    /**
     * 实体转详情 VO。
     *
     * @param log 审计实体
     * @return 详情视图对象
     */
    public static AuditLogDetailVO of(AuditLog log) {
        if (log == null) {
            return null;
        }
        AuditLogVO base = AuditLogVO.of(log);
        AuditLogDetailVO vo = new AuditLogDetailVO();
        vo.setId(base.getId());
        vo.setTenantId(base.getTenantId());
        vo.setModule(base.getModule());
        vo.setModuleName(base.getModuleName());
        vo.setAction(base.getAction());
        vo.setActionName(base.getActionName());
        vo.setTargetType(base.getTargetType());
        vo.setTargetId(base.getTargetId());
        vo.setTargetName(base.getTargetName());
        vo.setDetail(base.getDetail());
        vo.setOperatorName(base.getOperatorName());
        vo.setOperatorRole(base.getOperatorRole());
        vo.setIp(base.getIp());
        vo.setCreateTime(base.getCreateTime());
        vo.setBeforeData(log.getBeforeData());
        vo.setAfterData(log.getAfterData());
        vo.setUserAgent(log.getUserAgent());
        vo.setRequestMethod(log.getRequestMethod());
        vo.setRequestUri(log.getRequestUri());
        return vo;
    }
}
