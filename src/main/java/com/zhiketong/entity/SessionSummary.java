package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话摘要与服务小结实体，对应表 session_summary。
 * <p>
 * 同一会话每种类型（BRIEF / SUMMARY）只保留一行，重复生成走覆盖更新：
 * 进线摘要用于坐席接起即懂来意，服务小结用于结束前归档客户诉求、服务过程、处理结果与待办。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("session_summary")
public class SessionSummary extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，当前固定 1 */
    private Long tenantId;

    /** 会话 ID */
    private Long sessionId;

    /** 类型：BRIEF 进线摘要 / SUMMARY 服务小结 */
    private String summaryType;

    /** 客户诉求 */
    private String customerDemand;

    /** 服务过程 */
    private String serviceProcess;

    /** 处理结果 */
    private String result;

    /** 待办事项 */
    private String followUp;

    /** 关键词，英文逗号分隔 */
    private String keywords;

    /** 大模型原始输出，便于排查与二次整理 */
    private String rawContent;

    /** 生成所用模型；RULE 来源时为空 */
    private String model;

    /** 来源：AI 大模型 / RULE 规则兜底 / AGENT 坐席编写 */
    private String source;

    /** 状态：DRAFT 草稿 / ADOPTED 已采纳归档 */
    private String status;

    /** 生成人 / 编写人 ID，异步生成时为空 */
    private Long createBy;

    /** 最后一次修改人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
