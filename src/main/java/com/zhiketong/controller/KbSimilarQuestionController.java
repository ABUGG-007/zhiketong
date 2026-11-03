package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.EntryStatusRequest;
import com.zhiketong.dto.kb.SimilarExpandVO;
import com.zhiketong.dto.kb.SimilarQuestionCreateRequest;
import com.zhiketong.dto.kb.SimilarQuestionVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.KbSimilarQuestionService;
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
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 知识库相似问法接口（R-73 ~ R-77）。
 * <p>
 * 查询、新增与 AI 扩写对 ADMIN、AGENT 开放；删除与启停会改变线上匹配结果，仅 ADMIN。
 *
 * @author 智客通开发组
 */
@Tag(name = "07. 知识库 - 相似问法")
@Validated
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class KbSimilarQuestionController {

    private final KbSimilarQuestionService kbSimilarQuestionService;

    @Operation(summary = "相似问法列表")
    @GetMapping("/entries/{entryId}/similar-questions")
    public Result<List<SimilarQuestionVO>> list(@PathVariable Long entryId) {
        return Result.success(kbSimilarQuestionService.list(entryId));
    }

    @Operation(summary = "批量新增相似问法")
    @PostMapping("/entries/{entryId}/similar-questions")
    public Result<List<SimilarQuestionVO>> createBatch(@PathVariable Long entryId,
                                                       @Valid @RequestBody SimilarQuestionCreateRequest request) {
        Long operatorId = SecurityUtils.requireLogin().getUserId();
        return Result.success(kbSimilarQuestionService.createBatch(
                entryId, request.getQuestions(), request.getSource(), operatorId));
    }

    @Operation(summary = "AI 扩写相似问法（仅返回候选）")
    @PostMapping("/entries/{entryId}/similar-questions/expand")
    public Result<SimilarExpandVO> expand(@PathVariable Long entryId) {
        return Result.success(kbSimilarQuestionService.expand(entryId));
    }

    @Operation(summary = "删除相似问法")
    @DeleteMapping("/similar-questions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        kbSimilarQuestionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/停用相似问法")
    @PutMapping("/similar-questions/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody EntryStatusRequest request) {
        return Result.success(kbSimilarQuestionService.updateStatus(id, request.getStatus()));
    }
}
