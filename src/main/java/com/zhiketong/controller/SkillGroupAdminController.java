package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.skill.SkillGroupSaveRequest;
import com.zhiketong.dto.skill.SkillGroupStatusRequest;
import com.zhiketong.dto.skill.SkillGroupVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.SkillGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 技能组管理接口（R-96 ~ R-101），仅管理员可访问。
 *
 * @author 智客通开发组
 */
@Tag(name = "12. 系统管理 - 技能组")
@RestController
@RequestMapping("/api/admin/skill-groups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SkillGroupAdminController {

    private final SkillGroupService skillGroupService;

    @Operation(summary = "技能组列表")
    @GetMapping
    public Result<List<SkillGroupVO>> list(
            @RequestParam(defaultValue = "true") Boolean withMembers) {
        return Result.success(skillGroupService.list(withMembers));
    }

    @Operation(summary = "技能组详情")
    @GetMapping("/{id}")
    public Result<SkillGroupVO> detail(@PathVariable Long id) {
        return Result.success(skillGroupService.detail(id));
    }

    @Operation(summary = "新增技能组")
    @PostMapping
    public Result<SkillGroupVO> create(@Valid @RequestBody SkillGroupSaveRequest request) {
        return Result.success(skillGroupService.create(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "修改技能组")
    @PutMapping("/{id}")
    public Result<SkillGroupVO> update(@PathVariable Long id,
                                       @Valid @RequestBody SkillGroupSaveRequest request) {
        return Result.success(skillGroupService.update(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用技能组")
    @PutMapping("/{id}/status")
    public Result<SkillGroupVO> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody SkillGroupStatusRequest request) {
        return Result.success(skillGroupService.updateStatus(id, request.getStatus(), SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除技能组")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        skillGroupService.delete(id, SecurityUtils.requireLogin());
        return Result.success();
    }
}
