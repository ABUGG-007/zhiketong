package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.skill.SkillGroupBriefVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.SkillGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 技能组查询接口（R-102 / R-103）：坐席端展示「我的技能组」，访客端转人工选择「咨询类型」。
 *
 * @author 智客通开发组
 */
@Tag(name = "04. 坐席工作台 - 技能组")
@RestController
@RequiredArgsConstructor
public class SkillGroupQueryController {

    private final SkillGroupService skillGroupService;

    @Operation(summary = "本人所属技能组（坐席端）")
    @GetMapping("/api/agent/skill-groups/mine")
    @PreAuthorize("hasRole('AGENT')")
    public Result<List<SkillGroupBriefVO>> mine() {
        Long agentId = SecurityUtils.requireLogin().getUserId();
        return Result.success(skillGroupService.mine(agentId));
    }

    @Operation(summary = "转人工可选咨询类型（访客端）")
    @GetMapping("/api/chat/skill-groups")
    @PreAuthorize("hasAnyRole('USER','AGENT','ADMIN')")
    public Result<List<SkillGroupBriefVO>> enabledList() {
        return Result.success(skillGroupService.enabledList());
    }
}
