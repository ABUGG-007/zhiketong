package com.zhiketong.dto.quality;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 可质检会话列表项（R-171）：供管理端「发起质检」时选择已关闭会话。
 *
 * <p>已产生质检记录的会话会带出得分与记录 ID，前端提示「重新质检」而非「发起质检」。</p>
 *
 * @author 智客通开发组
 */
@Data
public class QualitySessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long id;

    /** 会话编号 */
    private String sessionNo;

    /** 会话状态（固定为 CLOSED） */
    private String status;

    /** 访客用户 ID */
    private Long userId;

    /** 接待坐席 ID，空表示纯机器人会话 */
    private Long agentId;

    /** 接待坐席名称 */
    private String agentName;

    /** 会话结束时间 */
    private LocalDateTime endTime;

    /** 消息条数 */
    private Integer messageCount;

    /** 已存在的质检记录 ID，空表示尚未质检 */
    private Long recordId;

    /** 已存在的质检得分 */
    private Integer score;

    /** 已存在的质检记录复核状态，空表示尚未质检 */
    private String recordStatus;
}
