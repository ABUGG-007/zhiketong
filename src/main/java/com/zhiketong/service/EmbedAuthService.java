package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.config.properties.EmbedProperties;
import com.zhiketong.entity.TenantEmbedConfig;
import com.zhiketong.mapper.TenantEmbedConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 网页接入鉴权服务：浏览器短时令牌 + 服务端 HMAC 签名。
 *
 * <p>两条链路共用 appKey 识别租户、appSecret 校验身份：</p>
 * <ol>
 *   <li><b>浏览器悬浮球</b>：appKey + 来源白名单换取短时令牌（{@code X-ZKT-Embed-Token}），
 *       令牌无状态 HMAC 签名，轮换 appSecret 后全部立即失效；</li>
 *   <li><b>服务端 API 对接</b>：按
 *       {@code appKey\ntimestamp\nnonce\ncontent} 计算 HMAC-SHA256，请求头携带
 *       {@code X-ZKT-App-Key / X-ZKT-Timestamp / X-ZKT-Nonce / X-ZKT-Sign}，
 *       时间窗 ±5 分钟，nonce 在 Redis 中防重放。</li>
 * </ol>
 *
 * <p>appSecret 不出现在任何公开响应中；浏览器侧只能拿到短时令牌，
 * 因此“知道接口地址”不再足以调用问答接口。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedAuthService {

    /** 浏览器令牌请求头 */
    public static final String TOKEN_HEADER = "X-ZKT-Embed-Token";
    /** 签名 appKey 请求头 */
    public static final String APP_KEY_HEADER = "X-ZKT-App-Key";
    /** 签名时间戳请求头 */
    public static final String TIMESTAMP_HEADER = "X-ZKT-Timestamp";
    /** 签名随机串请求头 */
    public static final String NONCE_HEADER = "X-ZKT-Nonce";
    /** 签名请求头 */
    public static final String SIGN_HEADER = "X-ZKT-Sign";

    /** 令牌载荷分隔符 */
    private static final String PAYLOAD_SEPARATOR = "\\|";
    /** 令牌签名分隔符 */
    private static final char TOKEN_SEPARATOR = '.';
    /** HMAC 算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** nonce 防重放有效期（秒） */
    private static final long NONCE_TTL_SECONDS = 300L;
    /** nonce 允许长度 */
    private static final int NONCE_MIN_LENGTH = 8;
    private static final int NONCE_MAX_LENGTH = 64;

    private final TenantEmbedConfigMapper tenantEmbedConfigMapper;
    private final EmbedProperties embedProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 按 appKey 读取启用中的租户接入配置。
     */
    public TenantEmbedConfig requireByAppKey(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少接入 Key");
        }
        TenantEmbedConfig config = tenantEmbedConfigMapper.selectOne(
                new LambdaQueryWrapper<TenantEmbedConfig>()
                        .eq(TenantEmbedConfig::getAppKey, appKey.trim())
                        .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入 Key 无效");
        }
        requireEnabled(config);
        return config;
    }

    /**
     * 校验接入状态，停用后公开接口立即拒绝。
     */
    public void requireEnabled(TenantEmbedConfig config) {
        if (config == null || !Integer.valueOf(Constants.STATUS_ENABLED).equals(config.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该租户的网页接入已停用");
        }
    }

    /**
     * 签发浏览器短时令牌：appKey|expireAt|随机串 的 HMAC 签名。
     * <p>同一毫秒内的多次申请也互不相同，避免令牌被猜中后重复使用。</p>
     */
    public String issueToken(TenantEmbedConfig config) {
        long expireAt = System.currentTimeMillis() + embedProperties.getTokenTtlMinutes() * 60_000L;
        String payload = config.getAppKey() + "|" + expireAt + "|" + randomHex(8);
        return base64Url(payload.getBytes(StandardCharsets.UTF_8)) + TOKEN_SEPARATOR
                + hmacHex(config.getAppSecret(), payload);
    }

    /**
     * 校验令牌并返回对应租户配置。
     */
    public TenantEmbedConfig verifyToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少接入令牌");
        }
        int index = token.lastIndexOf(TOKEN_SEPARATOR);
        if (index <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入令牌格式错误");
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token.substring(0, index)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入令牌格式错误");
        }
        String[] parts = payload.split(PAYLOAD_SEPARATOR);
        if (parts.length != 3) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入令牌格式错误");
        }
        TenantEmbedConfig config = requireByAppKey(parts[0]);
        String expected = hmacHex(config.getAppSecret(), payload);
        String actual = token.substring(index + 1);
        if (!constantTimeEquals(expected, actual)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入令牌无效");
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入令牌无效");
        }
        if (System.currentTimeMillis() > expireAt) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "接入令牌已过期，请刷新页面");
        }
        return config;
    }

    /**
     * 校验服务端签名请求头。
     *
     * @param request HTTP 请求
     * @param content 参与签名的业务内容：answer 传问题文本，config 传空串
     */
    public TenantEmbedConfig verifySignature(HttpServletRequest request, String content) {
        String appKey = request.getHeader(APP_KEY_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String nonce = request.getHeader(NONCE_HEADER);
        String sign = request.getHeader(SIGN_HEADER);
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(sign)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少接入凭证");
        }
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "签名时间戳格式错误");
        }
        long tolerance = embedProperties.getTimestampToleranceSeconds() * 1000L;
        if (Math.abs(System.currentTimeMillis() - requestTime) > tolerance) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "签名已过期，请校准服务器时间");
        }
        String trimmedNonce = nonce.trim();
        if (trimmedNonce.length() < NONCE_MIN_LENGTH || trimmedNonce.length() > NONCE_MAX_LENGTH) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "nonce 长度必须为 8-64 位");
        }
        TenantEmbedConfig config = requireByAppKey(appKey);
        String canonical = appKey.trim() + "\n" + timestamp.trim() + "\n" + trimmedNonce
                + "\n" + (content == null ? "" : content);
        if (!constantTimeEquals(hmacHex(config.getAppSecret(), canonical), sign.trim())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "签名校验失败");
        }
        requireUniqueNonce(config.getAppKey(), trimmedNonce);
        return config;
    }

    /**
     * 统一鉴权入口：优先短时令牌，其次服务端签名。
     */
    public TenantEmbedConfig authenticate(HttpServletRequest request, String content) {
        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }
        if (StringUtils.hasText(token)) {
            return verifyToken(token);
        }
        if (StringUtils.hasText(request.getHeader(APP_KEY_HEADER))) {
            return verifySignature(request, content);
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少接入凭证");
    }

    /**
     * 来源白名单校验：未配置白名单时不限制；配置后仅允许平台自身页面与匹配的来源。
     */
    public boolean isOriginAllowed(TenantEmbedConfig config, HttpServletRequest request) {
        String allowed = config.getAllowedOrigins();
        if (!StringUtils.hasText(allowed)) {
            return true;
        }
        String origin = resolveOrigin(request);
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        String platformOrigin = platformOrigin(request);
        if (StringUtils.hasText(platformOrigin) && platformOrigin.equalsIgnoreCase(origin)) {
            return true;
        }
        for (String pattern : allowed.split(",")) {
            String trimmed = pattern.trim();
            if (!trimmed.isEmpty() && originMatches(trimmed, origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析请求来源：优先 Origin，其次 Referer 的 scheme://host。
     */
    public String resolveOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin) && !"null".equalsIgnoreCase(origin.trim())) {
            return trimTrailingSlash(origin.trim());
        }
        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 平台自身地址：用于放行 /embed/chat 独立对话页（同源请求不参与商家白名单）。
     */
    private String platformOrigin(HttpServletRequest request) {
        String host = request.getHeader("X-Forwarded-Host");
        if (!StringUtils.hasText(host)) {
            host = request.getHeader("Host");
        }
        if (!StringUtils.hasText(host)) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        return scheme + "://" + host;
    }

    /**
     * 匹配单条来源规则，支持 https://*.example.com 形式的子域通配。
     */
    private boolean originMatches(String pattern, String origin) {
        String normalizedPattern = trimTrailingSlash(pattern).toLowerCase(Locale.ROOT);
        String normalizedOrigin = trimTrailingSlash(origin).toLowerCase(Locale.ROOT);
        if ("*".equals(normalizedPattern)) {
            return true;
        }
        if (!normalizedPattern.contains("*")) {
            return normalizedPattern.equals(normalizedOrigin);
        }
        String regex = normalizedPattern.replace(".", "\\.").replace("*", "[a-z0-9-]+");
        if (!normalizedPattern.startsWith("http://") && !normalizedPattern.startsWith("https://")) {
            regex = "https?://" + regex;
        }
        return normalizedOrigin.matches(regex);
    }

    /**
     * Redis 记录 nonce，重复使用直接拒绝；Redis 不可用时仅记录日志放行。
     */
    private void requireUniqueNonce(String appKey, String nonce) {
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                    Constants.EMBED_NONCE_PREFIX + appKey + ":" + nonce, "1",
                    NONCE_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(success)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "签名已被使用，请更换 nonce");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 与项目其他 Redis 加速能力保持一致：Redis 不可用时不阻断鉴权主流程
            log.warn("接入签名防重放校验跳过（Redis 不可用）：{}", e.getMessage());
        }
    }

    private String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.ERROR, "接入签名计算失败");
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String randomHex(int bytes) {
        SecureRandom random = new SecureRandom();
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte value : buffer) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
