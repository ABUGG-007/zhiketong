package com.zhiketong.dto.orderform;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单模板视图对象（R-120 ~ R-125）。
 *
 * @author 智客通开发组
 */
@Data
public class OrderTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板 ID */
    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 默认问题分类 */
    private String category;

    /** 默认优先级 */
    private String defaultPriority;

    /** 默认受理技能组 ID */
    private Long skillGroupId;

    /** 默认受理技能组名称 */
    private String skillGroupName;

    /** 绑定字段 ID 列表 */
    private List<Long> fieldIds = new ArrayList<>();

    /** 绑定字段明细（按绑定顺序） */
    private List<OrderFieldVO> fields = new ArrayList<>();

    /** 模板说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
