-- =============================================================
-- 智客通 —— 增量升级 04：AI 大模型配置表
-- 适用：已经建过库、不想重跑 01_schema.sql（会清空数据）的环境
-- 全新初始化直接执行 01_schema.sql → 02_data.sql 即可，本脚本可重复执行
-- 执行方式：
--   mysql -uroot -p < sql/04_ai_config.sql
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- AI 大模型配置表：管理端可视化维护各平台 / 中转站的 OpenAI 兼容接入参数
-- 说明：api_key 仅服务端保存，管理端列表接口返回时做脱敏处理
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model_config` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`            BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `name`                 VARCHAR(64)  NOT NULL COMMENT '配置名称，如 主力-DeepSeek',
    `provider`             VARCHAR(32)  NOT NULL DEFAULT 'custom' COMMENT '平台标识：deepseek/openai/qwen/zhipu/moonshot/volcengine/qianfan/hunyuan/minimax/stepfun/lingyiwanwu/baichuan/siliconflow/openrouter/relay/ollama/custom',
    `base_url`             VARCHAR(255) NOT NULL COMMENT 'OpenAI 兼容接口地址（不含 /chat/completions）',
    `api_key`              VARCHAR(255) NOT NULL COMMENT 'API Key（仅服务端保存，接口返回脱敏）',
    `model`                VARCHAR(128) NOT NULL COMMENT '模型名称，如 deepseek-chat',
    `temperature`          DECIMAL(3, 2) NOT NULL DEFAULT 0.70 COMMENT '采样温度 0.00-2.00',
    `max_tokens`           INT          NOT NULL DEFAULT 800 COMMENT '单次最大输出 token',
    `timeout_ms`           INT          NOT NULL DEFAULT 15000 COMMENT '请求超时（毫秒）',
    `extra_headers`        VARCHAR(500)          DEFAULT NULL COMMENT '附加请求头 JSON，兼容需额外鉴权头的中转站',
    `enabled`              TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态：1启用 0停用',
    `is_default`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模型：1是 0否',
    `last_test_time`       DATETIME              DEFAULT NULL COMMENT '最近连通性测试时间',
    `last_test_ok`         TINYINT               DEFAULT NULL COMMENT '最近测试结果：1成功 0失败',
    `last_test_latency_ms` INT                   DEFAULT NULL COMMENT '最近测试耗时（毫秒）',
    `last_test_message`    VARCHAR(255)          DEFAULT NULL COMMENT '最近测试结果说明',
    `remark`               VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_enabled` (`tenant_id`, `enabled`, `deleted`),
    KEY `idx_tenant_default` (`tenant_id`, `is_default`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 大模型配置表';

-- 初始模板：未填 Key、未启用，仅作为新建配置的参照（已存在同名配置则跳过）
INSERT INTO `ai_model_config`
(`tenant_id`, `name`, `provider`, `base_url`, `api_key`, `model`,
 `temperature`, `max_tokens`, `timeout_ms`, `enabled`, `is_default`, `remark`)
SELECT 1, 'DeepSeek 官方', 'deepseek', 'https://api.deepseek.com/v1', '', 'deepseek-chat',
       0.70, 800, 15000, 0, 0, '初始化模板：填入 API Key 后启用并可设为默认'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `name` = 'DeepSeek 官方' AND `deleted` = 0);
