package com.zhiketong.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 排队会话摘要（R-43）。
 *
 * @author 智客通开发组
 */
@Data
public class QueueSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排队序号，从 1 开始 */
    private Integer queuePosition;

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 用户 ID */
    private Long userId;

    /** 用户名称 */
    private String userName;

    /** 期望技能组 ID（访客选择的咨询类型） */
    private Long skillGroupId;

    /** 期望技能组名称 */
    private String skillGroupName;

    /** 进入排队时间 */
    private LocalDateTime queueEnterTime;

    /** 已等待秒数 */
    private Long waitSeconds;

    /** 最后一条消息内容 */
    private String lastMessage;

    /** 转人工原因 */
    private String transferReason;
}
