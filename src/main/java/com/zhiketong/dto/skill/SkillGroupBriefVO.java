package com.zhiketong.dto.skill;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能组简要信息（R-102 / R-103）。
 * <p>
 * 坐席端展示「我的技能组」，访客端转人工时作为「咨询类型」下拉选项。
 *
 * @author 智客通开发组
 */
@Data
public class SkillGroupBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 技能组 ID */
    private Long id;

    /** 技能组名称 */
    private String name;

    /** 组职责说明 */
    private String description;
}
