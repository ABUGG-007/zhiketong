package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 快捷回复话术实体，对应表 quick_reply。
 * <p>
 * 公共话术（{@code scope = PUBLIC}）由管理员维护、全体坐席可见；
 * 个人话术（{@code scope = PERSONAL}）由坐席自建，仅本人可见。
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("quick_reply")
public class QuickReply extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 范围：PUBLIC 公共 / PERSONAL 个人 */
    private String scope;

    /** 所有人 ID：PERSONAL 必填，PUBLIC 为空 */
    private Long ownerId;

    /** 话术分类 */
    private String category;

    /** 话术标题 */
    private String title;

    /** 话术正文 */
    private String content;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 累计使用次数 */
    private Integer useCount;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createBy;

    /** 更新人 ID */
    private Long updateBy;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
