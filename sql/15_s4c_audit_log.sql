-- =============================================================
-- 15_s4c_audit_log.sql  S4-C 通用操作审计（PL-06）
-- 依据 docs/产品推进路线图.md PL-06 与 AGENTS 6.4.4「所有写操作必须可审计」
-- 变更内容：
--   1) audit_log 通用操作审计表：模块 / 动作 / 对象 / 变更前后快照 /
--      操作人 / 来源 IP 与 UA / 请求方法路径 / 操作时间
-- 设计口径：
--   - 只追加，不设 update_time 与 deleted，历史行永不改写
--   - 只记录成功提交的变更（与业务同事务）；失败请求由 sys_login_log
--     与应用日志覆盖，避免审计表被拒绕请求污染
--   - tenant_id 由多租户拦截器按当前上下文写入；平台管理员操作写入被操作租户，
--     平台视角查询使用系统模式跨租户读取
--   - before_data / after_data 存 JSON 字符串，密码、API Key、appSecret 等
--     敏感字段在 Service 层脱敏后再落库
--   - 可重复执行：CREATE TABLE IF NOT EXISTS
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `audit_log` (
    `tenant_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `module`         VARCHAR(32)  NOT NULL COMMENT '业务模块：TENANT/USER/KB/EMBED/AI/ORDER_RULE',
    `action`         VARCHAR(24)  NOT NULL COMMENT '动作：CREATE/UPDATE/DELETE/ENABLE/DISABLE/CHANGE_PLAN/RENEW/RESET_PASSWORD/ROTATE_SECRET',
    `target_type`    VARCHAR(32)  NOT NULL COMMENT '对象类型：TENANT/SYS_USER/KB_ENTRY/TENANT_EMBED_CONFIG/AI_MODEL_CONFIG/ORDER_RULE',
    `target_id`      BIGINT                DEFAULT NULL COMMENT '对象 ID',
    `target_name`    VARCHAR(128)          DEFAULT NULL COMMENT '对象名称快照（租户名/账号/词条问题等）',
    `detail`         VARCHAR(1000)         DEFAULT NULL COMMENT '变更摘要，可直接展示的中文描述',
    `before_data`    TEXT                  DEFAULT NULL COMMENT '变更前快照（JSON，敏感字段已脱敏）',
    `after_data`     TEXT                  DEFAULT NULL COMMENT '变更后快照（JSON，敏感字段已脱敏）',
    `operator_id`    BIGINT                DEFAULT NULL COMMENT '操作人 ID',
    `operator_name`  VARCHAR(50)           DEFAULT NULL COMMENT '操作人名称快照',
    `operator_role`  VARCHAR(32)           DEFAULT NULL COMMENT '操作人角色编码',
    `ip`             VARCHAR(64)           DEFAULT NULL COMMENT '操作来源 IP',
    `user_agent`     VARCHAR(255)          DEFAULT NULL COMMENT '浏览器 UA',
    `request_method` VARCHAR(8)            DEFAULT NULL COMMENT 'HTTP 方法',
    `request_uri`    VARCHAR(255)          DEFAULT NULL COMMENT '请求路径',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_time` (`tenant_id`, `create_time`),
    KEY `idx_module_action` (`tenant_id`, `module`, `action`),
    KEY `idx_operator` (`tenant_id`, `operator_id`, `create_time`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通用操作审计日志（只追加）';

-- 自检
SELECT COUNT(*) AS audit_log_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log';
