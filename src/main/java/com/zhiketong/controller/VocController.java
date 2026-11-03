package com.zhiketong.controller;

import com.zhiketong.common.PageResult;
import com.zhiketong.common.Result;
import com.zhiketong.dto.voc.VocClusterResultVO;
import com.zhiketong.dto.voc.VocOverviewVO;
import com.zhiketong.dto.voc.VocTopicDetailVO;
import com.zhiketong.dto.voc.VocTopicMergeRequest;
import com.zhiketong.dto.voc.VocTopicRequest;
import com.zhiketong.dto.voc.VocTopicVO;
import com.zhiketong.dto.voc.VocWordCloudVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.VocService;
import com.zhiketong.task.VocClusterTask;
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

/**
 * 客户之声接口（R-205 ~ R-214，S6-C / DB-06）。
 *
 * <p>控制层只做参数接收与登录人透传；聚类算法、主题聚合与词云统计
 * 分别在 {@link VocService} 与 {@link VocClusterTask} 中完成。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "14.1 客户之声 - 未命中聚类与热点话题")
@Validated
@RestController
@RequestMapping("/api/kb/voc")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class VocController {

    private final VocService vocService;
    private final VocClusterTask vocClusterTask;

    @Operation(summary = "客户之声概览（R-205）")
    @GetMapping("/overview")
    public Result<VocOverviewVO> overview() {
        return Result.success(vocService.overview());
    }

    @Operation(summary = "主题分页（R-206）")
    @GetMapping("/topics")
    public Result<PageResult<VocTopicVO>> topics(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNum 最小为 1") int pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 最小为 1")
            @Max(value = 100, message = "pageSize 最大为 100") int pageSize,
            @RequestParam(required = false) String topicType,
            @RequestParam(required = false)
            @Min(value = 0, message = "status 只能为 0 或 1")
            @Max(value = 1, message = "status 只能为 0 或 1") Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(vocService.pageTopics(pageNum, pageSize, topicType, status, keyword));
    }

    @Operation(summary = "主题详情（R-207）")
    @GetMapping("/topics/{id}")
    public Result<VocTopicDetailVO> topicDetail(@PathVariable Long id) {
        return Result.success(vocService.topicDetail(id));
    }

    @Operation(summary = "新增人工主题（R-208）")
    @PostMapping("/topics")
    public Result<VocTopicVO> createTopic(@Valid @RequestBody VocTopicRequest request) {
        return Result.success(vocService.createTopic(request, SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "修改主题（R-209）")
    @PutMapping("/topics/{id}")
    public Result<VocTopicVO> updateTopic(@PathVariable Long id,
                                          @Valid @RequestBody VocTopicRequest request) {
        return Result.success(vocService.updateTopic(id, request, SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "删除主题（R-210）：归属问法解除后在下一轮聚类重新归集")
    @DeleteMapping("/topics/{id}")
    public Result<Void> deleteTopic(@PathVariable Long id) {
        vocService.deleteTopic(id, SecurityUtils.requireLogin().getUserId());
        return Result.success();
    }

    @Operation(summary = "启用/停用主题（R-212）：停用后不再参与自动归类")
    @PutMapping("/topics/{id}/status")
    public Result<VocTopicVO> updateStatus(
            @PathVariable Long id,
            @RequestParam
            @Min(value = 0, message = "status 只能为 0 或 1")
            @Max(value = 1, message = "status 只能为 0 或 1") Integer status) {
        return Result.success(vocService.updateStatus(id, status, SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "合并主题（R-211）")
    @PostMapping("/topics/merge")
    public Result<VocTopicVO> mergeTopics(@Valid @RequestBody VocTopicMergeRequest request) {
        return Result.success(vocService.mergeTopics(request, SecurityUtils.requireLogin().getUserId()));
    }

    @Operation(summary = "立即执行一轮聚类（R-213）")
    @PostMapping("/cluster")
    public Result<VocClusterResultVO> cluster() {
        return Result.success(vocClusterTask.clusterNow());
    }

    @Operation(summary = "词云（R-214）")
    @GetMapping("/word-cloud")
    public Result<VocWordCloudVO> wordCloud(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false)
            @Min(value = 10, message = "词条数最小为 10")
            @Max(value = 200, message = "词条数最大为 200") Integer limit) {
        return Result.success(vocService.wordCloud(topicId, limit));
    }
}
