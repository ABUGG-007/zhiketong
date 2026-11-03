package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 网页接入配置实体，对应表 tenant_embed_config。
 *
 * <p>每个租户一行：保存悬浮球外观（名称、欢迎语、主题色、位置、红点）、
 * 来源白名单、单 IP 限流阈值与签名密钥。appSecret 只参与服务端 HMAC 校验，
 * 严禁通过公开接口或商家页面下发。</p>
 *
 * @author 智客通开发组
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tenant_embed_config")
public class TenantEmbedConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID，由多租户拦截器在写入时按当前上下文填充 */
    private Long tenantId;


    /** 租户接入标识 */
    private String tenantCode;

    /** 租户名称（控制台展示） */
    private String tenantName;

    /** 接入 Key（公开标识） */
    private String appKey;

    /** 签名密钥（仅服务端保存） */
    private String appSecret;

    /** 客服名称 */
    private String brandName;

    /** 欢迎语 */
    private String welcome;

    /** 主题色 #RRGGBB */
    private String themeColor;

    /** 悬浮位置：right / left */
    private String position;

    /** 未读红点：1 显示 0 隐藏 */
    private Integer badge;

    /** 允许的嵌入来源，英文逗号分隔；为空不限制 */
    private String allowedOrigins;

    /** 单 IP 每分钟提问上限 */
    private Integer rateLimitPerMinute;

    /** 接入状态：1 启用 0 停用 */
    private Integer status;

    /** 密钥最近轮换时间 */
    private LocalDateTime secretRotateTime;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
