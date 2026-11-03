package com.zhiketong.dto.quickreply;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 新增/修改快捷回复请求（R-91 / R-92）。
 * <p>
 * 坐席只能创建个人话术，{@code scope} 传 PUBLIC 会被服务端忽略并强制为 PERSONAL；
 * 管理员可创建公共话术。
 *
 * @author 智客通开发组
 */
@Data
public class QuickReplySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 范围：PUBLIC / PERSONAL，缺省按调用者角色决定 */
    private String scope;

    /** 话术分类，缺省为「通用」 */
    @Size(max = 32, message = "分类不能超过 32 个字符")
    private String category;

    /** 话术标题 */
    @NotBlank(message = "话术标题不能为空")
    @Size(max = 64, message = "话术标题不能超过 64 个字符")
    private String title;

    /** 话术正文 */
    @NotBlank(message = "话术内容不能为空")
    @Size(max = 1000, message = "话术内容不能超过 1000 个字符")
    private String content;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1 启用 0 停用，缺省启用 */
    private Integer status;
}
