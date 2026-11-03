package com.zhiketong.dto.assist;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务小结（R-107 / R-108 / R-109）。
 *
 * @author 智客通开发组
 */
@Data
public class SessionSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 小结记录 ID */
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 客户诉求 */
    private String customerDemand;

    /** 服务过程 */
    private String serviceProcess;

    /** 处理结果 */
    private String result;

    /** 待办事项 */
    private String followUp;

    /** 关键词 */
    private List<String> keywords = new ArrayList<>();

    /** 来源：AI / RULE / AGENT */
    private String source;

    /** 状态：DRAFT 草稿 / ADOPTED 已采纳归档 */
    private String status;

    /** 使用的模型 */
    private String model;

    /** 编写人 ID */
    private Long createBy;

    /** 最近修改人 ID */
    private Long updateBy;

    /** 当前是否允许编辑：会话进入人工接待或已结束后可编辑 */
    private Boolean editable;

    /** 生成时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
