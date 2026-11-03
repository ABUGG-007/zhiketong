package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.kb.KbDuplicateVO;
import com.zhiketong.dto.kb.KbHealthVO;
import com.zhiketong.service.KbDiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * 知识库诊断接口：健康度看板（R-85）与查重冲突检测（R-86）。
 * <p>
 * 面向知识库运营，仅 ADMIN 可见。
 *
 * @author 智客通开发组
 */
@Tag(name = "07. 知识库 - 诊断")
@Validated
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KbDiagnosisController {

    private final KbDiagnosisService kbDiagnosisService;

    @Operation(summary = "知识健康度看板")
    @GetMapping("/health")
    public Result<KbHealthVO> health() {
        return Result.success(kbDiagnosisService.health());
    }

    @Operation(summary = "知识查重与冲突检测")
    @GetMapping("/duplicates")
    public Result<List<KbDuplicateVO>> duplicates(
            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "limit 必须大于等于 1")
            @Max(value = 500, message = "limit 不能超过 500") Integer limit) {
        return Result.success(kbDiagnosisService.duplicates(limit));
    }
}
