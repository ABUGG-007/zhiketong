package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.ai.AiRuntimeConfig;
import com.zhiketong.ai.OpenAiCompatibleClient;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.ai.AiModelConfigSaveRequest;
import com.zhiketong.dto.ai.AiModelConfigVO;
import com.zhiketong.dto.ai.AiModelTestVO;
import com.zhiketong.dto.ai.AiProviderPresetVO;
import com.zhiketong.entity.AiModelConfig;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 大模型配置服务：管理端增删改查、默认配置切换与连通性测试。
 *
 * <p>对话链路通过 {@link #resolveRuntime()} 实时读取「启用 + 默认」配置，
 * 因此管理端保存后立即生效，无需重启服务；数据库没有可用配置时由
 * {@code DeepSeekAiChatService} 回退到 application.yml 的 zhiketong.ai。</p>
 *
 * <p>所有平台共用 OpenAI 兼容协议，新增平台只需新增一行配置，
 * {@link #presets()} 中的预设仅用于减少手填成本。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    /** 连通性测试的输出上限，避免测试消耗过多 token */
    private static final int TEST_MAX_TOKENS = 32;

    private static final String TEST_SYSTEM_PROMPT = "你是连通性测试助手，只回复「连接正常」四个字。";

    /** 平台预设：覆盖国内主流、国际主流与中转/自建三类接入方式 */
    private static final List<AiProviderPresetVO> PRESETS = buildPresets();

    /** 本地推理平台：服务不校验 Key，允许留空 */
    private static final List<String> KEYLESS_PROVIDERS = Arrays.asList("ollama", "lmstudio", "vllm");

    /** 自建网关：地址指向本机/内网时允许留空 Key */
    private static final List<String> SELF_HOSTED_PROVIDERS = Arrays.asList("relay", "custom");

    /** 审计对象类型：大模型配置 */
    private static final String TARGET_AI_MODEL_CONFIG = "AI_MODEL_CONFIG";

    private final AiModelConfigMapper aiModelConfigMapper;
    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final AuditLogService auditLogService;

    /**
     * 配置列表，默认配置与启用配置排在最前。
     */
    public List<AiModelConfigVO> list() {
        List<AiModelConfig> configs = aiModelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .orderByDesc(AiModelConfig::getIsDefault)
                .orderByDesc(AiModelConfig::getEnabled)
                .orderByAsc(AiModelConfig::getId));
        Map<String, String> labels = providerLabels();
        return configs.stream()
                .map(config -> toVO(config, labels))
                .collect(Collectors.toList());
    }

    /**
     * 平台预设清单。
     */
    public List<AiProviderPresetVO> presets() {
        return PRESETS;
    }

    /**
     * 新建配置：第一条启用的配置会自动成为默认，避免建完却不生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigVO create(AiModelConfigSaveRequest request) {
        ensureNameAvailable(request.getName(), null);

        AiModelConfig entity = new AiModelConfig();
        apply(entity, request);
        entity.setApiKey(request.getApiKey() == null ? "" : request.getApiKey().trim());

        boolean enabled = !Boolean.FALSE.equals(request.getEnabled());
        boolean asDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (enabled && !asDefault && findDefault() == null) {
            asDefault = true;
        }
        if (!enabled) {
            asDefault = false;
        }
        entity.setEnabled(enabled ? 1 : 0);
        entity.setIsDefault(asDefault ? 1 : 0);
        if (asDefault) {
            clearDefault();
        }
        aiModelConfigMapper.insert(entity);
        log.info("新增大模型配置：id={}, name={}, provider={}, model={}",
                entity.getId(), entity.getName(), entity.getProvider(), entity.getModel());
        auditLogService.record(AuditModule.AI, AuditAction.CREATE)
                .target(TARGET_AI_MODEL_CONFIG, entity.getId(), entity.getName())
                .detail("新增大模型配置「" + entity.getName() + "」（" + entity.getModel() + "）")
                .after(toVO(requireById(entity.getId())))
                .save();
        return toVO(requireById(entity.getId()));
    }

    /**
     * 编辑配置：apiKey 留空表示沿用原密钥。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigVO update(Long id, AiModelConfigSaveRequest request) {
        AiModelConfig entity = requireById(id);
        // 审计快照取 VO（已脱敏 apiKey），且必须在 apply 之前取
        AiModelConfigVO before = toVO(entity);
        ensureNameAvailable(request.getName(), id);
        apply(entity, request);
        if (StringUtils.hasText(request.getApiKey())) {
            entity.setApiKey(request.getApiKey().trim());
        }

        boolean enabled = request.getEnabled() == null
                ? isEnabled(entity) : Boolean.TRUE.equals(request.getEnabled());
        boolean asDefault = request.getIsDefault() == null
                ? isDefault(entity) : Boolean.TRUE.equals(request.getIsDefault());
        if (!enabled) {
            asDefault = false;
        }
        entity.setEnabled(enabled ? 1 : 0);
        entity.setIsDefault(asDefault ? 1 : 0);
        if (asDefault) {
            clearDefault();
        }
        aiModelConfigMapper.updateById(entity);
        // 备注与附加请求头允许被清空，update-strategy=not_null 会忽略 null，需显式 set
        aiModelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getExtraHeaders, entity.getExtraHeaders())
                .set(AiModelConfig::getRemark, entity.getRemark()));
        log.info("更新大模型配置：id={}, name={}, enabled={}, default={}",
                id, entity.getName(), entity.getEnabled(), entity.getIsDefault());
        auditLogService.record(AuditModule.AI, AuditAction.UPDATE)
                .target(TARGET_AI_MODEL_CONFIG, id, entity.getName())
                .detail("编辑大模型配置「" + entity.getName() + "」")
                .before(before)
                .after(toVO(requireById(id)))
                .save();
        return toVO(requireById(id));
    }

    /**
     * 删除配置：默认配置被删除后自动顺延给下一条可用配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiModelConfig entity = requireById(id);
        boolean wasDefault = isDefault(entity);
        aiModelConfigMapper.deleteById(id);
        if (wasDefault) {
            promoteFirstEnabled();
        }
        log.info("删除大模型配置：id={}, name={}", id, entity.getName());
        auditLogService.record(AuditModule.AI, AuditAction.DELETE)
                .target(TARGET_AI_MODEL_CONFIG, id, entity.getName())
                .detail("删除大模型配置「" + entity.getName() + "」（" + entity.getModel() + "）")
                .before(toVO(entity))
                .save();
    }

    /**
     * 设为默认：同时强制启用，保证「默认即生效」。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigVO setDefault(Long id) {
        AiModelConfig entity = requireById(id);
        if (needsApiKey(entity) && !StringUtils.hasText(entity.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该平台需要 API Key，请先填写后再设为默认");
        }
        clearDefault();
        aiModelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getEnabled, 1)
                .set(AiModelConfig::getIsDefault, 1));
        log.info("切换默认大模型：id={}, name={}", id, entity.getName());
        auditLogService.record(AuditModule.AI, AuditAction.UPDATE)
                .target(TARGET_AI_MODEL_CONFIG, id, entity.getName())
                .detail("将「" + entity.getName() + "」设为默认大模型")
                .save();
        return toVO(requireById(id));
    }

    /**
     * 启用 / 停用：停用默认配置后自动顺延给其他可用配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigVO toggleStatus(Long id, boolean enabled) {
        AiModelConfig entity = requireById(id);
        boolean wasDefault = isDefault(entity);
        aiModelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getEnabled, enabled ? 1 : 0)
                .set(AiModelConfig::getIsDefault, enabled ? (wasDefault ? 1 : 0) : 0));
        if (!enabled && wasDefault) {
            promoteFirstEnabled();
        }
        auditLogService.record(AuditModule.AI, enabled ? AuditAction.ENABLE : AuditAction.DISABLE)
                .target(TARGET_AI_MODEL_CONFIG, id, entity.getName())
                .detail("大模型配置「" + entity.getName() + "」" + (enabled ? "启用" : "停用"))
                .save();
        return toVO(requireById(id));
    }

    /**
     * 真实调用一次极短对话，验证地址 / Key / 模型是否匹配。
     */
    public AiModelTestVO test(Long id) {
        AiModelConfig entity = requireById(id);
        AiModelTestVO result = new AiModelTestVO();
        result.setModel(entity.getModel());

        if (needsApiKey(entity) && !StringUtils.hasText(entity.getApiKey())) {
            result.setSuccess(false);
            result.setMessage("该平台需要 API Key，请先填写后再测试");
            recordTest(entity.getId(), false, 0, result.getMessage());
            return result;
        }

        AiRuntimeConfig runtime = toRuntime(entity);
        runtime.setMaxTokens(TEST_MAX_TOKENS);
        runtime.setTemperature(0);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", TEST_SYSTEM_PROMPT));
        messages.add(message("user", "ping"));

        try {
            OpenAiCompatibleClient.Completion completion = openAiCompatibleClient.complete(runtime, messages);
            result.setSuccess(true);
            result.setLatencyMs(completion.getLatencyMs());
            result.setModel(completion.getModel());
            result.setSample(completion.getContent());
            result.setMessage("连接成功，模型已正常返回");
            recordTest(entity.getId(), true, completion.getLatencyMs(), result.getMessage());
        } catch (Exception e) {
            String reason = StringUtils.hasText(e.getMessage()) ? e.getMessage() : "未知错误";
            result.setSuccess(false);
            result.setMessage(reason);
            recordTest(entity.getId(), false, 0, reason);
            log.warn("大模型连通性测试失败：id={}, name={}, reason={}", id, entity.getName(), reason);
        }
        return result;
    }

    /**
     * 解析当前对话应使用的运行时配置；没有可用数据库配置时返回 null，由调用方回退 yml。
     */
    public AiRuntimeConfig resolveRuntime() {
        AiModelConfig config = findDefault();
        if (config == null) {
            config = firstEnabled();
        }
        if (config == null || !isUsable(config)) {
            return null;
        }
        return toRuntime(config);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private AiModelConfig requireById(Long id) {
        AiModelConfig entity = id == null ? null : aiModelConfigMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "大模型配置不存在或已删除");
        }
        return entity;
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        String trimmed = name == null ? "" : name.trim();
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getName, trimmed);
        if (excludeId != null) {
            wrapper.ne(AiModelConfig::getId, excludeId);
        }
        if (aiModelConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "已存在同名配置，请换一个名称");
        }
    }

    private void apply(AiModelConfig entity, AiModelConfigSaveRequest request) {
        entity.setName(request.getName().trim());
        entity.setProvider(request.getProvider().trim());
        entity.setBaseUrl(trimTrailingSlash(request.getBaseUrl()));
        entity.setModel(request.getModel().trim());
        entity.setTemperature(request.getTemperature() == null
                ? new BigDecimal("0.70") : request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens() == null ? 800 : request.getMaxTokens());
        entity.setTimeoutMs(request.getTimeoutMs() == null ? 15000 : request.getTimeoutMs());
        entity.setExtraHeaders(StringUtils.hasText(request.getExtraHeaders())
                ? request.getExtraHeaders().trim() : null);
        entity.setRemark(StringUtils.hasText(request.getRemark())
                ? request.getRemark().trim() : null);
    }

    private String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private AiModelConfig findDefault() {
        return aiModelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1)
                .eq(AiModelConfig::getIsDefault, 1)
                .orderByAsc(AiModelConfig::getId)
                .last("LIMIT 1"));
    }

    private AiModelConfig firstEnabled() {
        return aiModelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1)
                .orderByAsc(AiModelConfig::getId)
                .last("LIMIT 1"));
    }

    private void clearDefault() {
        aiModelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsDefault, 1)
                .set(AiModelConfig::getIsDefault, 0));
    }

    /**
     * 原默认配置不可用后顺延：优先选择已填 Key 的启用配置。
     */
    private void promoteFirstEnabled() {
        List<AiModelConfig> candidates = aiModelConfigMapper.selectList(
                new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getEnabled, 1)
                        .orderByAsc(AiModelConfig::getId));
        AiModelConfig target = null;
        for (AiModelConfig candidate : candidates) {
            if (isUsable(candidate)) {
                target = candidate;
                break;
            }
        }
        if (target != null) {
            aiModelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getId, target.getId())
                    .set(AiModelConfig::getIsDefault, 1));
            log.info("默认大模型顺延为：id={}, name={}", target.getId(), target.getName());
        }
    }

    private void recordTest(Long id, boolean success, long latencyMs, String message) {
        aiModelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getLastTestTime, LocalDateTime.now())
                .set(AiModelConfig::getLastTestOk, success ? 1 : 0)
                .set(AiModelConfig::getLastTestLatencyMs, (int) Math.min(latencyMs, Integer.MAX_VALUE))
                .set(AiModelConfig::getLastTestMessage, abbreviate(message)));
    }

    private AiRuntimeConfig toRuntime(AiModelConfig entity) {
        AiRuntimeConfig runtime = new AiRuntimeConfig();
        runtime.setConfigId(entity.getId());
        runtime.setName(entity.getName());
        runtime.setProvider(entity.getProvider());
        runtime.setBaseUrl(entity.getBaseUrl());
        runtime.setApiKey(entity.getApiKey());
        runtime.setModel(entity.getModel());
        runtime.setTemperature(entity.getTemperature() == null
                ? 0.7 : entity.getTemperature().doubleValue());
        runtime.setMaxTokens(entity.getMaxTokens() == null ? 800 : entity.getMaxTokens());
        runtime.setTimeoutMs(entity.getTimeoutMs() == null ? 15000 : entity.getTimeoutMs());
        runtime.setExtraHeaders(entity.getExtraHeaders());
        runtime.setFromDatabase(true);
        runtime.setKeyOptional(!needsApiKey(entity));
        return runtime;
    }

    private AiModelConfigVO toVO(AiModelConfig entity) {
        return toVO(entity, providerLabels());
    }

    private AiModelConfigVO toVO(AiModelConfig entity, Map<String, String> labels) {
        AiModelConfigVO vo = new AiModelConfigVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setProvider(entity.getProvider());
        vo.setProviderLabel(labels.getOrDefault(entity.getProvider(), entity.getProvider()));
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setApiKeyMasked(maskKey(entity.getApiKey()));
        vo.setApiKeyConfigured(StringUtils.hasText(entity.getApiKey()));
        vo.setModel(entity.getModel());
        vo.setTemperature(entity.getTemperature());
        vo.setMaxTokens(entity.getMaxTokens());
        vo.setTimeoutMs(entity.getTimeoutMs());
        vo.setExtraHeaders(entity.getExtraHeaders());
        vo.setEnabled(entity.getEnabled());
        vo.setIsDefault(entity.getIsDefault());
        vo.setLastTestTime(entity.getLastTestTime());
        vo.setLastTestOk(entity.getLastTestOk());
        vo.setLastTestLatencyMs(entity.getLastTestLatencyMs());
        vo.setLastTestMessage(entity.getLastTestMessage());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private Map<String, String> providerLabels() {
        Map<String, String> labels = new HashMap<>(PRESETS.size());
        for (AiProviderPresetVO preset : PRESETS) {
            labels.put(preset.getProvider(), preset.getLabel());
        }
        return labels;
    }

    /**
     * 密钥脱敏：保留前 4 位与后 4 位，长度不足时整体隐藏。
     */
    private String maskKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private boolean isEnabled(AiModelConfig entity) {
        return Integer.valueOf(1).equals(entity.getEnabled());
    }

    /**
     * 是否具备真实调用条件：有 Key，或平台本身无需鉴权（如本地 Ollama）。
     */
    private boolean isUsable(AiModelConfig entity) {
        return StringUtils.hasText(entity.getApiKey()) || !needsApiKey(entity);
    }

    /**
     * 是否需要 API Key：本地推理平台无需鉴权；自建网关指向本机/内网时也允许不填。
     */
    private boolean needsApiKey(AiModelConfig entity) {
        String provider = entity.getProvider() == null ? "" : entity.getProvider().toLowerCase();
        if (KEYLESS_PROVIDERS.contains(provider)) {
            return false;
        }
        if (SELF_HOSTED_PROVIDERS.contains(provider)) {
            return !isLocalAddress(entity.getBaseUrl());
        }
        return true;
    }

    /**
     * 判断接口地址是否指向本机或内网（自建推理服务通常不鉴权）。
     */
    private boolean isLocalAddress(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        String host;
        try {
            host = URI.create(baseUrl.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!StringUtils.hasText(host)) {
            return false;
        }
        host = host.toLowerCase();
        if ("localhost".equals(host) || "127.0.0.1".equals(host)
                || "0.0.0.0".equals(host) || host.endsWith(".local")) {
            return true;
        }
        if (host.startsWith("192.168.") || host.startsWith("10.")) {
            return true;
        }
        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            if (parts.length == 4) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean isDefault(AiModelConfig entity) {
        return Integer.valueOf(1).equals(entity.getIsDefault());
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<>(4);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static List<AiProviderPresetVO> buildPresets() {
        List<AiProviderPresetVO> presets = new ArrayList<>();

        // ---------------- 国内平台 ----------------
        presets.add(new AiProviderPresetVO("deepseek", "DeepSeek 深度求索", "国内平台",
                "https://api.deepseek.com/v1", "deepseek-chat",
                Arrays.asList("deepseek-chat", "deepseek-reasoner"),
                "https://platform.deepseek.com/api_keys",
                "官方 OpenAI 兼容接口，国内直连稳定，性价比高，适合作为客服主力模型。", null));
        presets.add(new AiProviderPresetVO("qwen", "阿里云通义千问（百炼）", "国内平台",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                Arrays.asList("qwen-plus", "qwen-max", "qwen-turbo", "qwen-long"),
                "https://bailian.console.aliyun.com/",
                "使用百炼平台的 OpenAI 兼容模式，Key 与 DashScope 通用。", null));
        presets.add(new AiProviderPresetVO("zhipu", "智谱 GLM", "国内平台",
                "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus",
                Arrays.asList("glm-4-plus", "glm-4-air", "glm-4-flash"),
                "https://open.bigmodel.cn/usercenter/apikeys",
                "智谱开放平台 v4 接口，兼容 OpenAI 协议。", null));
        presets.add(new AiProviderPresetVO("moonshot", "月之暗面 Kimi", "国内平台",
                "https://api.moonshot.cn/v1", "moonshot-v1-8k",
                Arrays.asList("moonshot-v1-8k", "moonshot-v1-32k", "kimi-k2-0711-preview"),
                "https://platform.moonshot.cn/console/api-keys",
                "长文本能力突出，适合处理较长的售后描述。", null));
        presets.add(new AiProviderPresetVO("volcengine", "火山方舟（豆包）", "国内平台",
                "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-6-250615",
                Arrays.asList("doubao-seed-1-6-250615", "doubao-1-5-pro-32k-250115"),
                "https://console.volcengine.com/ark",
                "模型名可直接填推理接入点 ID（ep-xxxx），接入前需在方舟创建推理接入点。", null));
        presets.add(new AiProviderPresetVO("qianfan", "百度千帆（文心）", "国内平台",
                "https://qianfan.baidubce.com/v2", "ernie-4.5-turbo-128k",
                Arrays.asList("ernie-4.5-turbo-128k", "ernie-4.0-turbo-8k", "ernie-speed-128k"),
                "https://console.bce.baidu.com/qianfan/",
                "千帆 V2 版本提供 OpenAI 兼容接口，使用平台 API Key 鉴权。", null));
        presets.add(new AiProviderPresetVO("hunyuan", "腾讯混元", "国内平台",
                "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbos-latest",
                Arrays.asList("hunyuan-turbos-latest", "hunyuan-turbo-latest", "hunyuan-lite-latest"),
                "https://console.cloud.tencent.com/hunyuan/api-key",
                "混元 OpenAI 兼容接口，使用控制台创建的 API Key。", null));
        presets.add(new AiProviderPresetVO("minimax", "MiniMax", "国内平台",
                "https://api.minimax.chat/v1", "MiniMax-Text-01",
                Arrays.asList("MiniMax-Text-01", "abab6.5s-chat"),
                "https://platform.minimaxi.com/user-center/basic-information/interface-key",
                "MiniMax 开放平台 OpenAI 兼容接口。", null));
        presets.add(new AiProviderPresetVO("stepfun", "阶跃星辰 StepFun", "国内平台",
                "https://api.stepfun.com/v1", "step-2-16k",
                Arrays.asList("step-2-16k", "step-1-8k"),
                "https://platform.stepfun.com/interface-key",
                "阶跃星辰开放平台，兼容 OpenAI 协议。", null));
        presets.add(new AiProviderPresetVO("lingyiwanwu", "零一万物 Yi", "国内平台",
                "https://api.lingyiwanwu.com/v1", "yi-lightning",
                Arrays.asList("yi-lightning", "yi-large"),
                "https://platform.lingyiwanwu.com/apikeys",
                "零一万物开放平台，兼容 OpenAI 协议。", null));
        presets.add(new AiProviderPresetVO("baichuan", "百川智能", "国内平台",
                "https://api.baichuan-ai.com/v1", "Baichuan4-Turbo",
                Arrays.asList("Baichuan4-Turbo", "Baichuan3-Turbo"),
                "https://platform.baichuan-ai.com/console/apikey",
                "百川大模型开放平台，兼容 OpenAI 协议。", null));
        presets.add(new AiProviderPresetVO("siliconflow", "硅基流动 SiliconFlow", "国内平台",
                "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3",
                Arrays.asList("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct",
                        "THUDM/glm-4-9b-chat"),
                "https://cloud.siliconflow.cn/account/ak",
                "聚合型平台，一个 Key 可调用多家开源模型，模型名需带命名空间。", null));
        presets.add(new AiProviderPresetVO("spark", "讯飞星火", "国内平台",
                "https://spark-api-open.xf-yun.com/v1", "generalv3.5",
                Arrays.asList("generalv3.5", "4.0Ultra", "pro-128k", "lite"),
                "https://console.xfyun.cn/services/bmx1a",
                "星火开放平台的 OpenAI 兼容端点，Key 取自控制台「服务接口认证信息」。", null));
        presets.add(new AiProviderPresetVO("sensenova", "商汤日日新", "国内平台",
                "https://api.sensenova.cn/compatible-mode/v1", "SenseChat-5",
                Arrays.asList("SenseChat-5", "SenseChat-Turbo", "SenseChat-5-Cantonese"),
                "https://console.sensecore.cn/",
                "商汤日日新兼容模式，需先在控制台开通模型并创建 API Key。", null));

        // ---------------- 国际平台 ----------------
        presets.add(new AiProviderPresetVO("openai", "OpenAI", "国际平台",
                "https://api.openai.com/v1", "gpt-4o-mini",
                Arrays.asList("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
                "https://platform.openai.com/api-keys",
                "官方接口，国内访问需要代理；若走代理请把接口地址改为代理域名。", null));
        presets.add(new AiProviderPresetVO("gemini", "Google Gemini", "国际平台",
                "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash",
                Arrays.asList("gemini-2.0-flash", "gemini-1.5-pro"),
                "https://aistudio.google.com/app/apikey",
                "Gemini 官方的 OpenAI 兼容端点，可直接复用 OpenAI 协议。", null));
        presets.add(new AiProviderPresetVO("groq", "Groq", "国际平台",
                "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
                Arrays.asList("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
                "https://console.groq.com/keys",
                "推理速度快，适合对首字延迟敏感的客服场景。", null));
        presets.add(new AiProviderPresetVO("mistral", "Mistral AI", "国际平台",
                "https://api.mistral.ai/v1", "mistral-small-latest",
                Arrays.asList("mistral-small-latest", "mistral-large-latest", "open-mistral-nemo"),
                "https://console.mistral.ai/api-keys/",
                "欧洲主流模型平台，兼容 OpenAI 协议，小模型延迟低。", null));
        presets.add(new AiProviderPresetVO("together", "Together AI", "国际平台",
                "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                Arrays.asList("meta-llama/Llama-3.3-70B-Instruct-Turbo",
                        "Qwen/Qwen2.5-72B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"),
                "https://api.together.xyz/settings/api-keys",
                "开源模型托管平台，模型名需带厂商命名空间。", null));
        presets.add(new AiProviderPresetVO("fireworks", "Fireworks AI", "国际平台",
                "https://api.fireworks.ai/inference/v1", "accounts/fireworks/models/llama-v3p3-70b-instruct",
                Arrays.asList("accounts/fireworks/models/llama-v3p3-70b-instruct",
                        "accounts/fireworks/models/deepseek-v3"),
                "https://fireworks.ai/account/api-keys",
                "高吞吐推理平台，模型名以 accounts/fireworks/models/ 为前缀。", null));

        // ---------------- 中转与自建 ----------------
        presets.add(new AiProviderPresetVO("openrouter", "OpenRouter（聚合中转）", "中转与自建",
                "https://openrouter.ai/api/v1", "openai/gpt-4o-mini",
                Arrays.asList("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet",
                        "google/gemini-2.0-flash-001"),
                "https://openrouter.ai/keys",
                "一个 Key 聚合多家模型，模型名需带厂商前缀；建议附带来源请求头。",
                "{\"HTTP-Referer\":\"http://localhost\",\"X-Title\":\"Zhiketong\"}"));
        presets.add(new AiProviderPresetVO("relay", "OneAPI / NewAPI 自建中转", "中转与自建",
                "http://127.0.0.1:3000/v1", "gpt-4o-mini",
                Arrays.asList("gpt-4o-mini", "deepseek-chat", "qwen-plus"),
                "",
                "自建网关的统一入口，地址与模型按渠道实际配置填写；支持自定义鉴权头。", null));
        presets.add(new AiProviderPresetVO("302ai", "302.AI 中转站", "中转与自建",
                "https://api.302.ai/v1", "gpt-4o-mini",
                Arrays.asList("gpt-4o-mini", "claude-3-5-sonnet-20241022", "deepseek-chat"),
                "https://dash.302.ai/apis/list",
                "国内可直连的聚合中转，按量计费，一个 Key 覆盖多家模型。", null));
        presets.add(new AiProviderPresetVO("aihubmix", "AiHubMix 中转站", "中转与自建",
                "https://aihubmix.com/v1", "gpt-4o-mini",
                Arrays.asList("gpt-4o-mini", "gpt-4o", "gemini-2.0-flash"),
                "https://console.aihubmix.com/token",
                "国内常用聚合中转，模型名与官方保持一致，无需额外前缀。", null));
        presets.add(new AiProviderPresetVO("ohmygpt", "OhMyGPT 中转站", "中转与自建",
                "https://api.ohmygpt.com/v1", "gpt-4o-mini",
                Arrays.asList("gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet-20241022"),
                "https://www.ohmygpt.com/panel/tokens",
                "老牌 OpenAI 中转，支持与官方同名的模型别名。", null));
        presets.add(new AiProviderPresetVO("ollama", "本地 Ollama", "中转与自建",
                "http://127.0.0.1:11434/v1", "qwen2.5:7b",
                Arrays.asList("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:7b"),
                "https://ollama.com/download",
                "本地推理无需 API Key，适合内网部署、数据不出域或离线环境；测试连接前请先 ollama serve。", null));
        presets.add(new AiProviderPresetVO("lmstudio", "本地 LM Studio", "中转与自建",
                "http://127.0.0.1:1234/v1", "qwen2.5-7b-instruct",
                Arrays.asList("qwen2.5-7b-instruct", "llama-3.2-3b-instruct"),
                "https://lmstudio.ai/",
                "桌面端本地推理，无需 API Key；需在 LM Studio 开启 Local Server 并加载模型。", null));
        presets.add(new AiProviderPresetVO("vllm", "自建 vLLM / Xinference", "中转与自建",
                "http://127.0.0.1:8000/v1", "Qwen2.5-7B-Instruct",
                Arrays.asList("Qwen2.5-7B-Instruct", "Llama-3.1-8B-Instruct"),
                "",
                "自建推理服务，默认不校验 Key；模型名填服务端启动时注册的名称。", null));
        presets.add(new AiProviderPresetVO("custom", "自定义（OpenAI 兼容）", "中转与自建",
                "https://your-gateway.example.com/v1", "gpt-4o-mini",
                Collections.emptyList(),
                "",
                "任意兼容 /chat/completions 的服务均可接入，包括各类商业中转站。", null));

        return Collections.unmodifiableList(presets);
    }
}
