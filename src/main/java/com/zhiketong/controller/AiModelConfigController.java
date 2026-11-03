package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.ai.AiModelConfigSaveRequest;
import com.zhiketong.dto.ai.AiModelConfigVO;
import com.zhiketong.dto.ai.AiModelTestVO;
import com.zhiketong.dto.ai.AiProviderPresetVO;
import com.zhiketong.service.AiModelConfigService;
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
import java.util.List;

/**
 * 大模型配置接口：平台预设、配置增删改查、默认切换与连通性测试，仅 ADMIN 可访问。
 *
 * <p>保存后对话链路立即使用新配置；列表接口不下发明文 API Key，
 * 只返回脱敏串，编辑时留空表示沿用原密钥。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "13. 大模型配置")
@Validated
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService aiModelConfigService;

    @Operation(summary = "配置列表")
    @GetMapping("/configs")
    public Result<List<AiModelConfigVO>> list() {
        return Result.success(aiModelConfigService.list());
    }

    @Operation(summary = "平台预设（国内 / 国际 / 中转站）")
    @GetMapping("/presets")
    public Result<List<AiProviderPresetVO>> presets() {
        return Result.success(aiModelConfigService.presets());
    }

    @Operation(summary = "新建配置")
    @PostMapping("/configs")
    public Result<AiModelConfigVO> create(@Valid @RequestBody AiModelConfigSaveRequest request) {
        return Result.success("配置已保存", aiModelConfigService.create(request));
    }

    @Operation(summary = "编辑配置（API Key 留空表示不修改）")
    @PutMapping("/configs/{id}")
    public Result<AiModelConfigVO> update(@PathVariable Long id,
                                          @Valid @RequestBody AiModelConfigSaveRequest request) {
        return Result.success("配置已更新", aiModelConfigService.update(id, request));
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/configs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.delete(id);
        return Result.success();
    }

    @Operation(summary = "测试连接（真实调用一次模型）")
    @PostMapping("/configs/{id}/test")
    public Result<AiModelTestVO> test(@PathVariable Long id) {
        return Result.success(aiModelConfigService.test(id));
    }

    @Operation(summary = "设为默认模型")
    @PutMapping("/configs/{id}/default")
    public Result<AiModelConfigVO> setDefault(@PathVariable Long id) {
        return Result.success("已设为默认模型", aiModelConfigService.setDefault(id));
    }

    @Operation(summary = "启用 / 停用")
    @PutMapping("/configs/{id}/status")
    public Result<AiModelConfigVO> toggleStatus(@PathVariable Long id,
                                                @RequestParam boolean enabled) {
        return Result.success(aiModelConfigService.toggleStatus(id, enabled));
    }
}
