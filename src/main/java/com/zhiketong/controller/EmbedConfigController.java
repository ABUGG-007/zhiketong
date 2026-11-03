package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.embed.AdminEmbedConfigVO;
import com.zhiketong.dto.embed.EmbedConfigUpdateRequest;
import com.zhiketong.dto.embed.EmbedSecretVO;
import com.zhiketong.service.EmbedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 网页接入配置接口（M8），仅 ADMIN 可访问。
 *
 * <p>配置持久化在 tenant_embed_config，控制台保存后刷新页面仍会记住；
 * appSecret 仅在此接口返回，用于服务端签名对接，轮换后旧令牌与旧签名立即失效。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "12. 网页接入 - 配置")
@Validated
@RestController
@RequestMapping("/api/admin/embed")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmbedConfigController {

    private final EmbedService embedService;

    @Operation(summary = "读取接入配置")
    @GetMapping("/config")
    public Result<AdminEmbedConfigVO> config() {
        return Result.success(embedService.adminConfig());
    }

    @Operation(summary = "保存接入配置")
    @PutMapping("/config")
    public Result<AdminEmbedConfigVO> update(@Valid @RequestBody EmbedConfigUpdateRequest request) {
        return Result.success(embedService.updateConfig(request));
    }

    @Operation(summary = "轮换签名密钥")
    @PostMapping("/config/secret")
    public Result<EmbedSecretVO> rotateSecret() {
        return Result.success(embedService.rotateSecret());
    }
}
