package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.ai.AiChatService;
import com.zhiketong.ai.ReplyPrompts;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.config.properties.EmbedProperties;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.dto.embed.AdminEmbedConfigVO;
import com.zhiketong.dto.embed.EmbedAnswerResponse;
import com.zhiketong.dto.embed.EmbedConfigUpdateRequest;
import com.zhiketong.dto.embed.EmbedConfigVO;
import com.zhiketong.dto.embed.EmbedSecretVO;
import com.zhiketong.dto.embed.EmbedTokenVO;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.TenantEmbedConfig;
import com.zhiketong.enums.AnswerCategory;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.TenantEmbedConfigMapper;
import com.zhiketong.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 网页接入（悬浮球）服务：配置持久化、接入令牌签发与公开知识库问答。
 *
 * <p>配置保存在 {@code tenant_embed_config}：控制台修改后立即生效，
 * 商家页面只需携带 appKey，主题色/位置/欢迎语等由服务端下发，
 * 不再依赖每次手动维护的 {@code <script>} 属性。租户身份由
 * {@link EmbedAuthService} 通过令牌或 HMAC 签名确定，请求体中的 tenant 不再可信。</p>
 *
 * <p>该服务不创建会话、不写入会话表，只复用知识库匹配器返回标准答案；
 * 访客需要人工跟进时由前端引导登录/提交工单，避免匿名流量污染会话统计。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedService {

    /** 快捷问题条数 */
    private static final int QUICK_QUESTION_LIMIT = 4;

    /** 默认欢迎语（租户未配置时使用） */
    private static final String DEFAULT_WELCOME =
            "你好，我是智客通智能助手。发票、退款、物流等常见问题可以直接问我。";

    private static final String DEFAULT_BRAND = "智客通客服";

    private static final String DEFAULT_THEME_COLOR = "#4f46e5";

    /** 审计对象类型：网页接入配置 */
    private static final String TARGET_EMBED_CONFIG = "TENANT_EMBED_CONFIG";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KnowledgeMatcher knowledgeMatcher;
    private final AnswerSupport answerSupport;
    private final KbEntryMapper kbEntryMapper;
    private final TenantEmbedConfigMapper tenantEmbedConfigMapper;
    private final EmbedAuthService embedAuthService;
    private final EmbedRateLimiter embedRateLimiter;
    private final EmbedProperties embedProperties;
    private final AiChatService aiChatService;
    private final AuditLogService auditLogService;

    /**
     * 当前控制台维护的租户配置；首次访问时自动初始化一行，避免漏跑种子数据导致接入不可用。
     */
    public TenantEmbedConfig currentConfig() {
        TenantEmbedConfig config = findByTenantCode(embedProperties.getTenantCode());
        if (config != null) {
            return config;
        }
        synchronized (this) {
            config = findByTenantCode(embedProperties.getTenantCode());
            if (config == null) {
                config = buildDefaultConfig();
                try {
                    tenantEmbedConfigMapper.insert(config);
                    log.info("已初始化网页接入配置：tenantCode={}, appKey={}",
                            config.getTenantCode(), config.getAppKey());
                } catch (DuplicateKeyException e) {
                    config = findByTenantCode(embedProperties.getTenantCode());
                }
            }
        }
        return config;
    }

    /**
     * 管理端读取配置（含签名密钥，仅 ADMIN）。
     */
    public AdminEmbedConfigVO adminConfig() {
        return toAdminVO(currentConfig());
    }

    /**
     * 保存外观、来源白名单与限流阈值；appKey/appSecret 不在此接口修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminEmbedConfigVO updateConfig(EmbedConfigUpdateRequest request) {
        TenantEmbedConfig config = currentConfig();
        String welcome = StringUtils.hasText(request.getWelcome()) ? request.getWelcome().trim() : null;
        // 使用 UpdateWrapper 显式 set：欢迎语与来源白名单允许被清空，
        // 而全局 update-strategy=not_null 会忽略实体中的 null 字段
        tenantEmbedConfigMapper.update(null, new LambdaUpdateWrapper<TenantEmbedConfig>()
                .eq(TenantEmbedConfig::getId, config.getId())
                .set(TenantEmbedConfig::getBrandName, request.getBrandName().trim())
                .set(TenantEmbedConfig::getWelcome, welcome)
                .set(TenantEmbedConfig::getThemeColor, request.getThemeColor().toLowerCase())
                .set(TenantEmbedConfig::getPosition, request.getPosition())
                .set(TenantEmbedConfig::getBadge, Boolean.TRUE.equals(request.getBadge()) ? 1 : 0)
                .set(TenantEmbedConfig::getAllowedOrigins, normalizeOrigins(request.getAllowedOrigins()))
                .set(TenantEmbedConfig::getRateLimitPerMinute, request.getRateLimitPerMinute())
                .set(TenantEmbedConfig::getStatus, request.getStatus()));
        log.info("保存网页接入配置：tenantCode={}, status={}", config.getTenantCode(), request.getStatus());
        auditLogService.record(AuditModule.EMBED, AuditAction.UPDATE)
                .target(TARGET_EMBED_CONFIG, config.getId(), config.getTenantCode())
                .detail("保存网页接入配置：品牌 " + request.getBrandName().trim()
                        + "，状态 " + request.getStatus())
                .before(config)
                .after(tenantEmbedConfigMapper.selectById(config.getId()))
                .save();
        return toAdminVO(tenantEmbedConfigMapper.selectById(config.getId()));
    }

    /**
     * 轮换签名密钥：旧令牌与旧签名立即失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmbedSecretVO rotateSecret() {
        TenantEmbedConfig config = currentConfig();
        config.setAppSecret(randomHex(24));
        config.setSecretRotateTime(LocalDateTime.now());
        tenantEmbedConfigMapper.updateById(config);
        log.info("轮换网页接入密钥：tenantCode={}", config.getTenantCode());
        // 密钥本身属于敏感信息，不入审计快照，只记录轮换事实
        auditLogService.record(AuditModule.EMBED, AuditAction.ROTATE_SECRET)
                .target(TARGET_EMBED_CONFIG, config.getId(), config.getTenantCode())
                .detail("轮换网页接入密钥，旧令牌与旧签名立即失效")
                .save();
        EmbedSecretVO vo = new EmbedSecretVO();
        vo.setAppKey(config.getAppKey());
        vo.setAppSecret(config.getAppSecret());
        return vo;
    }

    /**
     * 浏览器申请短时令牌：appKey 有效 + 来源命中白名单才签发。
     */
    public EmbedTokenVO issueToken(String appKey, HttpServletRequest request) {
        TenantEmbedConfig config = embedAuthService.requireByAppKey(appKey);
        if (!embedAuthService.isOriginAllowed(config, request)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "来源站点未授权，请在控制台「接入方式」中配置允许的嵌入来源");
        }
        embedRateLimiter.checkToken(config, request.getRemoteAddr());
        EmbedTokenVO vo = new EmbedTokenVO();
        vo.setToken(embedAuthService.issueToken(config));
        vo.setExpiresIn(embedProperties.getTokenTtlMinutes() * 60L);
        vo.setConfig(publicConfig(config));
        return vo;
    }

    /**
     * 公开配置：按令牌/签名解析出的租户读取，外观与欢迎语来自数据库。
     */
    public EmbedConfigVO publicConfig(TenantEmbedConfig config) {
        EmbedConfigVO vo = new EmbedConfigVO();
        vo.setTenantCode(config.getTenantCode());
        vo.setBrandName(StringUtils.hasText(config.getBrandName())
                ? config.getBrandName() : DEFAULT_BRAND);
        vo.setWelcome(StringUtils.hasText(config.getWelcome())
                ? config.getWelcome() : DEFAULT_WELCOME);
        vo.setThemeColor(StringUtils.hasText(config.getThemeColor())
                ? config.getThemeColor() : DEFAULT_THEME_COLOR);
        vo.setPosition("left".equals(config.getPosition()) ? "left" : "right");
        vo.setBadge(Integer.valueOf(Constants.STATUS_ENABLED).equals(config.getBadge()));
        // 快捷问题取租户自己的知识库，必须切到该租户上下文
        vo.setQuickQuestions(TenantContext.callAs(config.getTenantId(), this::quickQuestions));
        vo.setHumanTransfer(true);
        return vo;
    }

    /**
     * 公开问答：与站内会话共用同一套应答编排（AGENTS.md 5.1.7 禁止两端智能程度不一致）：
     * <ol>
     *   <li>知识库精准命中（≥ 直答阈值）直答；</li>
     *   <li>0.35 ~ 阈值之间的候选作为参考知识注入提示词，大模型生成；</li>
     *   <li>低于 0.35 且无参考时大模型自由生成；</li>
     *   <li>大模型不可用或调用失败时降级：弱命中推荐 / 兜底话术。</li>
     * </ol>
     *
     * @param config 已通过令牌/签名校验的租户配置
     * @param content 访客问题
     * @param ip      来源 IP，用于 Redis 限流
     */
    public EmbedAnswerResponse answer(TenantEmbedConfig config, String content, String ip) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "问题不能为空");
        }
        // 公开接口没有登录态：鉴权确定租户后，切到该租户上下文再查知识库、写未命中与大模型调用日志
        return TenantContext.callAs(config.getTenantId(), () -> doAnswer(config, content, ip));
    }

    /**
     * 租户上下文内的问答主编排。
     */
    private EmbedAnswerResponse doAnswer(TenantEmbedConfig config, String content, String ip) {
        embedRateLimiter.checkAnswer(config, ip);
        String query = content.trim();
        if (query.length() > 300) {
            query = query.substring(0, 300);
        }

        List<KnowledgeMatcher.MatchResult> matches = knowledgeMatcher.match(query, null, 3);

        EmbedAnswerResponse response = new EmbedAnswerResponse();
        KnowledgeMatcher.MatchResult best = matches.isEmpty() ? null : matches.get(0);
        double bestScore = best == null ? 0D : best.getScore();
        AnswerCategory category = answerSupport.classify(query, bestScore);
        boolean matched = category == AnswerCategory.DIRECT && best != null;
        response.setMatched(matched);
        response.setScore(best == null ? 0D : Math.round(best.getScore() * 1000D) / 1000D);
        response.setAnswerCategory(category.name());
        // 闲聊与无效输入不属于知识缺口，不引导转人工
        response.setSuggestHuman(!matched
                && category != AnswerCategory.CHITCHAT && category != AnswerCategory.INVALID);

        // 1. 知识库直答：无需调用大模型
        if (matched) {
            response.setAnswer(best.getAnswer());
            response.setSourceQuestion(best.getQuestion());
            response.setAnswerSource("kb-direct");
            return response;
        }

        // 2. 闲聊/无效输入：轻量话术，与站内会话共用 AnswerSupport
        if (category == AnswerCategory.CHITCHAT || category == AnswerCategory.INVALID) {
            response.setAnswer(category == AnswerCategory.CHITCHAT
                    ? answerSupport.chitchatReply(query) : answerSupport.invalidReply());
            response.setAnswerSource(category == AnswerCategory.CHITCHAT ? "chitchat" : "invalid");
            return response;
        }

        // 3. 大模型生成：弱命中注入参考知识，与站内 ChatService.replyWithAi 同一编排
        boolean reference = category == AnswerCategory.SUGGEST;
        if (aiChatService.isAvailable()) {
            String prompt = reference
                    ? ReplyPrompts.referencePrompt(best.getQuestion(), best.getAnswer(), query)
                    : query;
            try {
                AiChatResponse aiResponse = aiChatService.chat(null, null, prompt, null);
                if (aiResponse != null && !aiResponse.isFallback()
                        && StringUtils.hasText(aiResponse.getContent())) {
                    response.setAnswer(truncate(aiResponse.getContent()));
                    response.setSourceQuestion(reference ? best.getQuestion() : null);
                    response.setAnswerSource(reference ? "ai-reference" : "ai-free");
                    response.setAnswerCategory(reference
                            ? AnswerCategory.REFERENCE.name() : AnswerCategory.FREE.name());
                    // 大模型已给出有效答案，本轮不算未命中，不引导转人工
                    response.setSuggestHuman(false);
                    return response;
                }
                if (aiResponse != null && aiResponse.isFallback()) {
                    log.warn("嵌入端大模型回复降级：tenantCode={}, reason={}",
                            config.getTenantCode(), aiResponse.getFallbackReason());
                }
            } catch (Exception e) {
                log.warn("嵌入端大模型调用异常，转入降级回复：tenantCode={}, error={}",
                        config.getTenantCode(), e.getMessage());
            }
        }

        // 4. 降级：弱命中推荐相近问法，否则兜底话术；两种情况都计入未命中采集
        if (reference) {
            response.setAnswer(answerSupport.suggestReply(suggestions(matches)));
            response.setSourceQuestion(best.getQuestion());
            response.setAnswerSource("kb-suggest");
        } else {
            response.setAnswer(answerSupport.fallbackReply());
            response.setAnswerSource("fallback");
        }
        answerSupport.collect(query, category, KbUnmatchedService.CHANNEL_EMBED, null, null, bestScore);
        return response;
    }

    /**
     * 从匹配结果中抽取推荐问法（优先实际命中的相似问法），去重后最多 3 条。
     */
    private List<String> suggestions(List<KnowledgeMatcher.MatchResult> matches) {
        List<String> questions = new ArrayList<>();
        for (KnowledgeMatcher.MatchResult match : matches) {
            String question = StringUtils.hasText(match.getMatchedQuestion())
                    ? match.getMatchedQuestion() : match.getQuestion();
            if (StringUtils.hasText(question) && !questions.contains(question)) {
                questions.add(question);
            }
            if (questions.size() >= 3) {
                break;
            }
        }
        return questions;
    }

    private String truncate(String content) {
        return content.length() > 1000 ? content.substring(0, 1000) : content;
    }

    /**
     * 读取命中次数最高的启用条目，作为快捷提问。
     */
    private List<String> quickQuestions() {
        LambdaQueryWrapper<KbEntry> wrapper = new LambdaQueryWrapper<KbEntry>()
                .select(KbEntry::getId, KbEntry::getQuestion)
                .eq(KbEntry::getStatus, 1)
                .orderByDesc(KbEntry::getHitCount)
                .orderByAsc(KbEntry::getId)
                .last("LIMIT " + QUICK_QUESTION_LIMIT);
        List<KbEntry> entries = kbEntryMapper.selectList(wrapper);
        List<String> questions = new ArrayList<>(entries.size());
        for (KbEntry entry : entries) {
            if (StringUtils.hasText(entry.getQuestion())) {
                questions.add(entry.getQuestion());
            }
        }
        return questions;
    }

    private TenantEmbedConfig findByTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return null;
        }
        return tenantEmbedConfigMapper.selectOne(new LambdaQueryWrapper<TenantEmbedConfig>()
                .eq(TenantEmbedConfig::getTenantCode, tenantCode.trim())
                .last("LIMIT 1"));
    }

    private TenantEmbedConfig buildDefaultConfig() {
        TenantEmbedConfig config = new TenantEmbedConfig();
        config.setTenantCode(embedProperties.getTenantCode());
        config.setTenantName("默认租户");
        config.setAppKey("zk_" + randomHex(6));
        config.setAppSecret(randomHex(24));
        config.setBrandName(DEFAULT_BRAND);
        config.setWelcome(DEFAULT_WELCOME);
        config.setThemeColor(DEFAULT_THEME_COLOR);
        config.setPosition("right");
        config.setBadge(1);
        config.setAllowedOrigins(null);
        config.setRateLimitPerMinute(embedProperties.getDefaultRateLimitPerMinute());
        config.setStatus(Constants.STATUS_ENABLED);
        return config;
    }

    private AdminEmbedConfigVO toAdminVO(TenantEmbedConfig config) {
        AdminEmbedConfigVO vo = new AdminEmbedConfigVO();
        vo.setTenantCode(config.getTenantCode());
        vo.setTenantName(config.getTenantName());
        vo.setAppKey(config.getAppKey());
        vo.setAppSecret(config.getAppSecret());
        vo.setBrandName(config.getBrandName());
        vo.setWelcome(config.getWelcome());
        vo.setThemeColor(config.getThemeColor());
        vo.setPosition(config.getPosition());
        vo.setBadge(Integer.valueOf(Constants.STATUS_ENABLED).equals(config.getBadge()));
        vo.setAllowedOrigins(config.getAllowedOrigins());
        vo.setRateLimitPerMinute(config.getRateLimitPerMinute());
        vo.setStatus(config.getStatus());
        vo.setSecretRotateTime(config.getSecretRotateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }

    /**
     * 来源白名单统一为英文逗号分隔，去重去空；留空表示不限制。
     */
    private String normalizeOrigins(String origins) {
        if (!StringUtils.hasText(origins)) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (String item : origins.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        return list.isEmpty() ? null : String.join(",", list);
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte value : buffer) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
