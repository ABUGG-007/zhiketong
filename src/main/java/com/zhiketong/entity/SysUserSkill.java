package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 坐席技能组关系实体，对应表 sys_user_skill。
 * <p>
 * 一个坐席可加入多个技能组，{@code level} 为能力值（1-5，5 最强），
 * 分配时在同负载候选之间作为次要排序依据。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user_skill")
public class SysUserSkill extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 坐席用户 ID */
    private Long userId;

    /** 技能组 ID */
    private Long skillGroupId;

    /** 能力值 1-5，5 为最强 */
    private Integer level;
}
