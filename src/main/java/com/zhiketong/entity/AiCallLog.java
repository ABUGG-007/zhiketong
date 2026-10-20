package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 调用日志实体，对应表 ai_call_log。
 * <p>
 * 该表只有 create_time、没有 update_time，因此不继承 BaseEntity；
 * 记录每次大模型调用的 token 消耗、耗时与成功与否，供 AI 调用统计使用。
 *
 * @author 智客通开发组
 */
@Data
@TableName("ai_call_log")
public class AiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 对应消息 ID */
    private Long messageId;

    /** 模型名称 */
    private String model;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 调用耗时（毫秒） */
    private Integer latencyMs;

    /** 是否成功：1 是 0 否 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;
}
