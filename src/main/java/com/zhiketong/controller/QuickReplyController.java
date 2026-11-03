package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.quickreply.QuickReplySaveRequest;
import com.zhiketong.dto.quickreply.QuickReplyStatusRequest;
import com.zhiketong.dto.quickreply.QuickReplyUseVO;
import com.zhiketong.dto.quickreply.QuickReplyVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.QuickReplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 快捷回复话术接口（R-89 ~ R-95）。
 * <p>
 * 坐席可见公共话术与本人个人话术，仅能维护个人话术；管理员维护公共话术。
 *
 * @author 智客通开发组
 */
@Tag(name = "04. 坐席工作台 - 快捷回复")
@Validated
@RestController
@RequestMapping("/api/quick-replies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class QuickReplyController {

    private final QuickReplyService quickReplyService;

    @Operation(summary = "快捷回复列表")
    @GetMapping
    public Result<List<QuickReplyVO>> list(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false)
            @Size(max = 32, message = "分类长度不能超过 32") String category,
            @RequestParam(required = false)
            @Size(max = 50, message = "搜索关键词长度不能超过 50") String keyword,
            @RequestParam(required = false)
            @Min(value = 0, message = "状态只能为 0 或 1")
            @Max(value = 1, message = "状态只能为 0 或 1") Integer status) {
        return Result.success(quickReplyService.list(scope, category, keyword, status, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "快捷回复分类列表")
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(quickReplyService.categories(SecurityUtils.requireLogin()));
    }

    @Operation(summary = "新增快捷回复")
    @PostMapping
    public Result<QuickReplyVO> create(@Valid @RequestBody QuickReplySaveRequest request) {
        return Result.success(quickReplyService.create(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "编辑快捷回复")
    @PutMapping("/{id}")
    public Result<QuickReplyVO> update(@PathVariable Long id,
                                       @Valid @RequestBody QuickReplySaveRequest request) {
        return Result.success(quickReplyService.update(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用快捷回复")
    @PutMapping("/{id}/status")
    public Result<QuickReplyVO> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody QuickReplyStatusRequest request) {
        return Result.success(quickReplyService.updateStatus(id, request.getStatus(), SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除快捷回复")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        quickReplyService.delete(id, SecurityUtils.requireLogin());
        return Result.success();
    }

    @Operation(summary = "引用快捷回复（累加使用次数）")
    @PostMapping("/{id}/use")
    public Result<QuickReplyUseVO> use(@PathVariable Long id) {
        return Result.success(quickReplyService.use(id, SecurityUtils.requireLogin()));
    }
}
