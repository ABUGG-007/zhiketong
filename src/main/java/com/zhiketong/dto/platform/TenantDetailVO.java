package com.zhiketong.dto.platform;

import com.zhiketong.dto.plan.PlanUsageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台端租户详情（S4-B，PL-02/PL-04）。
 *
 * <p>在列表信息之上补充：租户管理员账号、五项额度的实时用量水位、
 * 最近一次用量快照时间，支撑平台运营处理续费与扩容工单。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TenantDetailVO extends TenantVO {

    private static final long serialVersionUID = 1L;

    /** 租户管理员登录名 */
    private String adminUsername;

    /** 租户管理员昵称 */
    private String adminNickname;

    /** 五项额度实时用量 */
    private List<PlanUsageVO.UsageItem> quotas = new ArrayList<>();

    /** 最近一次用量快照时间 */
    private LocalDateTime snapshotTime;
}
