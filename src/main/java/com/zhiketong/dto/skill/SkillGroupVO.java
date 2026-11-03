package com.zhiketong.dto.skill;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能组视图对象（R-96 ~ R-100）。
 *
 * @author 智客通开发组
 */
@Data
public class SkillGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 技能组 ID */
    private Long id;

    /** 技能组名称 */
    private String name;

    /** 组职责说明 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 组内坐席数 */
    private Integer memberCount;

    /** 组内坐席明细 */
    private List<SkillGroupMemberVO> members;

    /** 创建时间 */
    private LocalDateTime createTime;
}
