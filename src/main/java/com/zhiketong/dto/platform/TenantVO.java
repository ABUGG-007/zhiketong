package com.zhiketong.dto.platform;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台端租户列表项（S4-B，PL-02）。
 *
 * <p>携带租户当前套餐的额度上限，便于平台运营在一屏内判断
 * 「租户规模 + 套餐是否匹配」，用量明细见 {@link TenantDetailVO}。</p>
 *
 * @author 智客通开发组
 */
@Data
public class TenantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long id;

    /** 租户编码 */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 状态：ENABLED / DISABLED / EXPIRED */
    private String status;

    /** 套餐编码 */
    private String planCode;

    /** 套餐名称 */
    private String planName;

    /** 服务到期时间，为空表示不限 */
    private LocalDateTime expireTime;

    /** 是否已过到期时间 */
    private boolean expired;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人手机号 */
    private String contactPhone;

    /** 联系人邮箱 */
    private String contactEmail;

    /** 备注 */
    private String remark;

    /** 坐席账号上限，-1 不限 */
    private Integer seatLimit;

    /** 每自然月会话上限，-1 不限 */
    private Integer sessionLimit;

    /** 每自然月工单上限，-1 不限 */
    private Integer orderLimit;

    /** 知识库条目上限，-1 不限 */
    private Integer kbLimit;

    /** 每自然月 AI 应答上限，-1 不限 */
    private Integer aiLimit;

    /** 创建时间 */
    private LocalDateTime createTime;
}
