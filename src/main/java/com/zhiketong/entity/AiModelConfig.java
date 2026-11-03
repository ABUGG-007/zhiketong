package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 大模型配置实体，对应表 ai_model_config。
 *
 * <p>一行代表一个「平台 + 模型」接入配置：base_url 指向各平台的 OpenAI 兼容根地址，
 * api_key 仅服务端保存且列表接口返回脱敏值。所有平台共用同一套
 * {@code POST {baseUrl}/chat/completions} 协议，因此新增平台只需要新增一行配置，
 * 不需要新增代码实现。</p>
 *
 * <p>同一租户内 enabled=1 且 is_default=1 的记录是对话实际使用的配置；
 * 没有可用数据库配置时回退到 application.yml 的 zhiketong.ai。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，1 为平台默认租户 */
    private Long tenantId;

    /** 配置名称，如「主力-DeepSeek」 */
    private String name;

    /** 平台标识：deepseek/openai/qwen/... */
    private String provider;

    /** OpenAI 兼容接口根地址（不含 /chat/completions） */
    private String baseUrl;

    /** API Key（仅服务端保存） */
    private String apiKey;

    /** 模型名称，如 deepseek-chat */
    private String model;

    /** 采样温度 0.00-2.00 */
    private BigDecimal temperature;

    /** 单次最大输出 token */
    private Integer maxTokens;

    /** 请求超时（毫秒） */
    private Integer timeoutMs;

    /** 附加请求头 JSON，兼容需额外鉴权头的中转站 */
    private String extraHeaders;

    /** 启用状态：1 启用 0 停用 */
    private Integer enabled;

    /** 是否默认模型：1 是 0 否 */
    private Integer isDefault;

    /** 最近连通性测试时间 */
    private LocalDateTime lastTestTime;

    /** 最近测试结果：1 成功 0 失败 */
    private Integer lastTestOk;

    /** 最近测试耗时（毫秒） */
    private Integer lastTestLatencyMs;

    /** 最近测试结果说明 */
    private String lastTestMessage;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
