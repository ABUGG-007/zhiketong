package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 坐席状态实体，对应表 agent_status。
 * <p>
 * 在线状态实时值另存 Redis，本表用于持久化与统计；
 * onlineStatus 取值见 {@link com.zhiketong.enums.AgentOnlineStatus}（ONLINE/BUSY/OFFLINE）。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("agent_status")
public class AgentStatus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 坐席用户 ID */
    private Long agentId;

    /** 在线状态：ONLINE/BUSY/OFFLINE */
    private String onlineStatus;

    /** 最大同时接待数 */
    private Integer maxConcurrent;

    /** 当前接待数 */
    private Integer currentCount;

    /** 累计接待数 */
    private Integer totalServed;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;
}
