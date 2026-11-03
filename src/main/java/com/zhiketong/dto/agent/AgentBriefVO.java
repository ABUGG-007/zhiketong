package com.zhiketong.dto.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 坐席简要信息。
 *
 * @author 智客通开发组
 */
@Data
public class AgentBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 坐席用户 ID */
    private Long id;

    /** 坐席昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatar;
}
