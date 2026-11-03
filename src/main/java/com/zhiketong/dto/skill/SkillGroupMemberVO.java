package com.zhiketong.dto.skill;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能组成员视图对象。
 *
 * @author 智客通开发组
 */
@Data
public class SkillGroupMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 坐席用户 ID */
    private Long userId;

    /** 坐席账号 */
    private String username;

    /** 坐席昵称 */
    private String nickname;

    /** 能力值 1-5，5 为最强 */
    private Integer level;
}
