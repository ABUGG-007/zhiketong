package com.zhiketong.dto.monitor;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 组长监听列表行（R-199，AS-10）。
 *
 * <p>只暴露监听决策需要的字段：会话状态、责任坐席、待回复时长与最后一条消息，
 * 不携带访客身份等敏感信息，需要看全文时再进入详情接口。</p>
 *
 * @author 智客通开发组
 */
@Data
public class MonitorSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 会话状态：AI/WAITING/HUMAN/CLOSED */
    private String status;

    /** 访客用户 ID */
    private Long userId;

    /** 访客名称 */
    private String userName;

    /** 接待坐席 ID */
    private Long agentId;

    /** 接待坐席名称 */
    private String agentName;

    /** 消息条数 */
    private Integer messageCount;

    /** 排队或接待时长（秒） */
    private Long durationSeconds;

    /** 距最后一条访客消息的等待秒数，最后一条为访客消息时有值 */
    private Long pendingSeconds;

    /** 最后一条消息内容（截断展示） */
    private String lastMessage;

    /** 最后一条消息发送方类型 */
    private String lastSenderType;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;

    /** 当前未关闭的告警数 */
    private Long openAlertCount;

    /** 会话创建时间 */
    private LocalDateTime createTime;
}
