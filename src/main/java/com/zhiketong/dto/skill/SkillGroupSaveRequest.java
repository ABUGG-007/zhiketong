package com.zhiketong.dto.skill;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 新增/修改技能组请求（R-98 / R-99）。
 * <p>
 * {@code memberIds} 为组内坐席全量列表，保存时以本次提交为准覆盖原成员关系；
 * {@code levels} 可选，按 userId 提供能力值，未提供时按默认 3 处理。
 *
 * @author 智客通开发组
 */
@Data
public class SkillGroupSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 技能组名称 */
    @NotBlank(message = "技能组名称不能为空")
    @Size(max = 64, message = "技能组名称不能超过 64 个字符")
    private String name;

    /** 组职责说明 */
    @Size(max = 255, message = "组说明不能超过 255 个字符")
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 组内坐席 ID 列表 */
    @Size(max = 100, message = "单个技能组最多 100 名坐席")
    private List<Long> memberIds;

    /** 能力值映射（可选）：userId -> level(1-5) */
    private List<MemberLevel> levels;

    /**
     * 成员能力值。
     */
    @Data
    public static class MemberLevel implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 坐席用户 ID */
        private Long userId;

        /** 能力值 1-5 */
        private Integer level;
    }
}
