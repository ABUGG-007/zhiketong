-- =============================================================
-- 智客通 —— 增量升级 03：网页接入配置表 + 默认租户
-- 适用：已经建过库、不想重跑 01_schema.sql（会清空数据）的环境
-- 全新初始化直接执行 01_schema.sql → 02_data.sql 即可，本脚本可重复执行
-- 执行方式：
--   mysql -uroot -p < sql/03_embed_config.sql
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 网页接入配置表：按租户保存悬浮球外观、来源白名单与签名密钥
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tenant_embed_config` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_code`           VARCHAR(32)  NOT NULL COMMENT '租户接入标识',
    `tenant_name`           VARCHAR(64)  NOT NULL COMMENT '租户名称（控制台展示）',
    `app_key`               VARCHAR(40)  NOT NULL COMMENT '接入 Key（公开标识，可出现在商家页面）',
    `app_secret`            VARCHAR(64)  NOT NULL COMMENT '签名密钥（仅服务端保存，禁止下发浏览器）',
    `brand_name`            VARCHAR(64)  NOT NULL DEFAULT '智客通客服' COMMENT '客服名称',
    `welcome`               VARCHAR(255)          DEFAULT NULL COMMENT '欢迎语',
    `theme_color`           CHAR(7)      NOT NULL DEFAULT '#4f46e5' COMMENT '主题色 #RRGGBB',
    `position`              VARCHAR(8)   NOT NULL DEFAULT 'right' COMMENT '悬浮位置：right/left',
    `badge`                 TINYINT      NOT NULL DEFAULT 1 COMMENT '未读红点：1显示 0隐藏',
    `allowed_origins`       VARCHAR(500)          DEFAULT NULL COMMENT '允许的嵌入来源，英文逗号分隔；为空不限制',
    `rate_limit_per_minute` INT          NOT NULL DEFAULT 30 COMMENT '单 IP 每分钟提问上限',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '接入状态：1启用 0停用',
    `secret_rotate_time`    DATETIME              DEFAULT NULL COMMENT '密钥最近轮换时间',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`),
    UNIQUE KEY `uk_app_key` (`app_key`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '网页接入配置表';

-- 默认租户（已存在则跳过，不覆盖控制台里改过的配置）
INSERT INTO `tenant_embed_config`
(`tenant_code`, `tenant_name`, `app_key`, `app_secret`, `brand_name`, `welcome`,
 `theme_color`, `position`, `badge`, `allowed_origins`, `rate_limit_per_minute`, `status`)
SELECT 'demo', '智客通演示租户', 'zk_demo_5f2a1c9e', '8f4c2d7e1a6b9c3f5e0d4a7b2c8f1e6d',
       '智客通客服', '你好，我是智客通智能助手。发票、退款、物流等常见问题可以直接问我。',
       '#4f46e5', 'right', 1, NULL, 30, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tenant_embed_config` WHERE `tenant_code` = 'demo');
