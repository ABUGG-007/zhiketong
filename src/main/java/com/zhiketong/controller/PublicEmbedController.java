package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.embed.EmbedAnswerRequest;
import com.zhiketong.dto.embed.EmbedAnswerResponse;
import com.zhiketong.dto.embed.EmbedConfigVO;
import com.zhiketong.dto.embed.EmbedTokenRequest;
import com.zhiketong.dto.embed.EmbedTokenVO;
import com.zhiketong.entity.TenantEmbedConfig;
import com.zhiketong.service.EmbedAuthService;
import com.zhiketong.service.EmbedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 网页接入公开接口：供商家官网的悬浮球与 API 对接调用，无需登录。
 *
 * <p>租户身份不再从请求体的 tenant 字段读取，而是由短时令牌
 * （{@code X-ZKT-Embed-Token}）或服务端 HMAC 签名请求头确定，
 * 因此仅知道接口地址无法调用问答能力。限流按「租户 + IP」在 Redis 计数，
 * 多实例部署共享同一份额度。</p>
 */
@Tag(name = "网页接入", description = "悬浮球公开配置、令牌与问答接口")
@RestController
@RequestMapping("/api/public/embed")
@RequiredArgsConstructor
public class PublicEmbedController {

    private final EmbedService embedService;
    private final EmbedAuthService embedAuthService;

    /**
     * 申请浏览器短时令牌：appKey + 来源白名单校验通过后签发，并返回悬浮球配置。
     */
    @Operation(summary = "申请接入令牌")
    @PostMapping("/token")
    public Result<EmbedTokenVO> token(@Valid @RequestBody EmbedTokenRequest request,
                                      HttpServletRequest servletRequest) {
        return Result.success(embedService.issueToken(request.getAppKey(), servletRequest));
    }

    /**
     * 悬浮球初始化配置：外观与快捷问题来自 tenant_embed_config。
     */
    @Operation(summary = "获取悬浮球配置")
    @GetMapping("/config")
    public Result<EmbedConfigVO> config(HttpServletRequest servletRequest) {
        TenantEmbedConfig config = embedAuthService.authenticate(servletRequest, "");
        return Result.success(embedService.publicConfig(config));
    }

    /**
     * 访客提问：知识库命中直接返回标准答案。
     */
    @Operation(summary = "悬浮球提问")
    @PostMapping("/answer")
    public Result<EmbedAnswerResponse> answer(@Valid @RequestBody EmbedAnswerRequest request,
                                              HttpServletRequest servletRequest) {
        // 服务端签名需覆盖问题内容，先完成鉴权再进入知识库匹配
        TenantEmbedConfig config = embedAuthService.authenticate(servletRequest, request.getContent());
        return Result.success(embedService.answer(config, request.getContent(),
                servletRequest.getRemoteAddr()));
    }
}
