-- =============================================================
-- 19_s6b_alert_monitor.sql  S6-B 告警规则化与监听强插
-- 依据 docs/产品推进路线图.md 1.2.2 / 1.2.3：
--   AS-10 组长监听 / 强插 / 强接 / 强派
--   AS-11 坐席自定义告警（响应超时、会话超长、差评、敏感词）
--   DB-05 一次性解决率（72 小时窗口回溯，不新增表）
-- 变更内容：
--   1) alert_rule   告警规则配置（租户级 + 坐席个人级统一建模）
--   2) alert_event  告警事件（按「规则 + 目标」去重，冷却期内不重复通知）
-- 说明：
--   - 新增表从建表起即带 tenant_id，由多租户拦截器自动注入条件
--   - 可重复执行：建表用 IF NOT EXISTS，预置数据按 (tenant_id, owner_id, rule_code) 判重
--   - owner_id = 0 表示租户级规则（管理员维护，全租户生效）；
--     owner_id > 0 表示坐席个人规则，只对该坐席接待的会话生效
--   - threshold_unit 语义：COUNT 数量 / SECONDS 秒 / MINUTES 分钟 / SCORE 评分（差评阈值）
--   - DB-05 不新增表：chat_session 已有 user_id、end_time 与 idx_user_id，
--     一次性解决率按「同一访客 72 小时内是否二次进线」回溯计算
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 告警规则配置表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `alert_rule` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_code`        VARCHAR(48)  NOT NULL COMMENT '规则编码，同租户同归属内唯一；内置规则与指标编码一致',
    `metric_code`      VARCHAR(48)  NOT NULL COMMENT '指标编码，扫描引擎据此选择判定算法（9 类内置指标）',
    `rule_name`        VARCHAR(64)  NOT NULL COMMENT '规则名称',
    `rule_type`        VARCHAR(16)  NOT NULL COMMENT '规则类型：QUEUE 排队 / SESSION 会话 / AGENT 坐席 / ORDER 工单',
    `severity`         VARCHAR(16)  NOT NULL DEFAULT 'WARN' COMMENT '严重程度：INFO 提示 / WARN 警告 / CRITICAL 严重',
    `threshold_value`  INT          NOT NULL DEFAULT 0 COMMENT '触发阈值，单位见 threshold_unit',
    `threshold_unit`   VARCHAR(16)  NOT NULL DEFAULT 'COUNT' COMMENT '阈值单位：COUNT 数量 / SECONDS 秒 / MINUTES 分钟 / SCORE 评分',
    `cooldown_minutes` INT          NOT NULL DEFAULT 10 COMMENT '冷却分钟数，同规则同目标在冷却期内只告警一次',
    `notify_role`      VARCHAR(16)  NOT NULL DEFAULT 'ADMIN' COMMENT '通知对象：ADMIN 管理员 / AGENT 责任坐席 / ALL 两者',
    `owner_id`         BIGINT       NOT NULL DEFAULT 0 COMMENT '规则归属坐席 ID，0 表示租户级规则',
    `keywords`         VARCHAR(500)          DEFAULT NULL COMMENT '敏感词表，英文逗号分隔，仅 SENSITIVE_WORD 使用',
    `description`      VARCHAR(255)          DEFAULT NULL COMMENT '规则说明与判定口径',
    `sort`             INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不参与扫描）',
    `built_in`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否内置规则：1内置（编码与类型不可改）0自定义',
    `create_by`        BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`        BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_owner_code` (`tenant_id`, `owner_id`, `rule_code`, `deleted`),
    KEY `idx_tenant_type_status` (`tenant_id`, `rule_type`, `status`),
    KEY `idx_tenant_owner_status` (`tenant_id`, `owner_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '告警规则配置表';

-- -------------------------------------------------------------
-- 2. 告警事件表（按「规则 + 目标」去重；确认与关闭留痕）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `alert_event` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_id`         BIGINT                DEFAULT NULL COMMENT '规则 ID，规则删除后保留快照',
    `rule_code`       VARCHAR(48)  NOT NULL COMMENT '规则编码快照',
    `metric_code`     VARCHAR(48)  NOT NULL COMMENT '指标编码快照',
    `rule_name`       VARCHAR(64)  NOT NULL COMMENT '规则名称快照',
    `rule_type`       VARCHAR(16)  NOT NULL COMMENT '规则类型快照',
    `severity`        VARCHAR(16)  NOT NULL COMMENT '严重程度快照',
    `target_type`     VARCHAR(16)  NOT NULL COMMENT '告警目标类型：QUEUE 排队 / SESSION 会话 / AGENT 坐席 / ORDER 工单',
    `target_id`       BIGINT                DEFAULT NULL COMMENT '目标 ID：队列与工单为租户 ID，会话为会话 ID，坐席为坐席 ID',
    `session_id`      BIGINT                DEFAULT NULL COMMENT '关联会话 ID，便于直接跳转监听',
    `agent_id`        BIGINT                DEFAULT NULL COMMENT '关联坐席 ID，为空表示无责任坐席',
    `title`           VARCHAR(128) NOT NULL COMMENT '告警标题',
    `detail`          VARCHAR(500)          DEFAULT NULL COMMENT '告警详情，写入触发时的指标快照',
    `metric_value`    BIGINT       NOT NULL DEFAULT 0 COMMENT '触发时指标实际值',
    `threshold_value` INT          NOT NULL DEFAULT 0 COMMENT '触发时阈值快照',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '处理状态：OPEN 待处理 / ACKED 已确认 / CLOSED 已关闭',
    `occur_count`     INT          NOT NULL DEFAULT 1 COMMENT '同一规则同一目标的累计触发次数',
    `first_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次触发时间',
    `last_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近触发时间',
    `ack_by`          BIGINT                DEFAULT NULL COMMENT '确认人 ID',
    `ack_name`        VARCHAR(50)           DEFAULT NULL COMMENT '确认人名称快照',
    `ack_time`        DATETIME              DEFAULT NULL COMMENT '确认时间',
    `ack_note`        VARCHAR(255)          DEFAULT NULL COMMENT '确认说明',
    `close_by`        BIGINT                DEFAULT NULL COMMENT '关闭人 ID，为空表示系统自动关闭',
    `close_time`      DATETIME              DEFAULT NULL COMMENT '关闭时间',
    `handle_seconds`  BIGINT                DEFAULT NULL COMMENT '处理耗时（秒），首次触发到关闭',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status_time` (`tenant_id`, `status`, `last_time`),
    KEY `idx_tenant_rule_target` (`tenant_id`, `rule_code`, `target_type`, `target_id`, `status`),
    KEY `idx_tenant_agent` (`tenant_id`, `agent_id`, `status`),
    KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '告警事件表';

-- -------------------------------------------------------------
-- 3. 预置默认规则（按现有租户各一套，已存在则跳过）
-- -------------------------------------------------------------
INSERT INTO `alert_rule` (`tenant_id`, `rule_code`, `metric_code`, `rule_name`, `rule_type`, `severity`,
                          `threshold_value`, `threshold_unit`, `cooldown_minutes`, `notify_role`,
                          `owner_id`, `keywords`, `description`, `sort`, `built_in`)
SELECT t.id, d.rule_code, d.rule_code, d.rule_name, d.rule_type, d.severity,
       d.threshold_value, d.threshold_unit, d.cooldown_minutes, d.notify_role,
       0, d.keywords, d.description, d.sort, 1
FROM `tenant` t
         JOIN (SELECT 'NO_AGENT_ONLINE'        AS rule_code,
                      '排队无在线坐席'              AS rule_name,
                      'QUEUE'                    AS rule_type,
                      'CRITICAL'                 AS severity,
                      1                          AS threshold_value,
                      'COUNT'                    AS threshold_unit,
                      5                          AS cooldown_minutes,
                      'ADMIN'                    AS notify_role,
                      NULL                       AS keywords,
                      '有访客在排队但没有任何在线坐席时触发，按排队人数告警' AS description,
                      10                         AS sort
               UNION ALL
               SELECT 'QUEUE_BACKLOG', '排队积压', 'QUEUE', 'WARN', 5, 'COUNT', 10, 'ADMIN', NULL,
                      '排队人数超过阈值时触发，用于提前扩容坐席', 20
               UNION ALL
               SELECT 'QUEUE_SLOW', '排队时长过长', 'QUEUE', 'WARN', 300, 'SECONDS', 10, 'ADMIN', NULL,
                      '最长排队时长超过阈值秒数时触发', 30
               UNION ALL
               SELECT 'FIRST_RESPONSE_SLOW', '首响偏慢', 'SESSION', 'WARN', 60, 'SECONDS', 30, 'ADMIN', NULL,
                      '坐席接入后尚未回复且等待超过阈值秒数时触发，用于组长实时介入', 40
               UNION ALL
               SELECT 'ORDER_OVERDUE', '工单超时', 'ORDER', 'WARN', 1, 'COUNT', 30, 'ADMIN', NULL,
                      '存在已超时且未关闭的工单时触发', 50
               UNION ALL
               SELECT 'AGENT_RESPONSE_TIMEOUT', '访客消息待回复超时', 'AGENT', 'WARN', 180, 'SECONDS', 10, 'ALL', NULL,
                      '人工接待中最后一条为访客消息且等待超过阈值秒数时触发，按会话归属坐席告警', 60
               UNION ALL
               SELECT 'SESSION_OVERLONG', '会话时长超长', 'SESSION', 'INFO', 30, 'MINUTES', 60, 'ALL', NULL,
                      '人工接待时长超过阈值分钟数时触发，便于组长介入收尾', 70
               UNION ALL
               SELECT 'LOW_RATING', '客户差评', 'SESSION', 'WARN', 2, 'SCORE', 60, 'ALL', NULL,
                      '会话评分小于等于阈值（1-5 分制）时触发，用于复盘服务问题', 80
               UNION ALL
               SELECT 'SENSITIVE_WORD', '会话命中敏感词', 'SESSION', 'CRITICAL', 1, 'COUNT', 30, 'ALL',
                      '投诉,曝光,12315,起诉,退款无门,举报',
                      '会话消息命中敏感词时触发，命中即高优先处理', 90) d
WHERE t.deleted = 0
  AND NOT EXISTS (SELECT 1
                  FROM `alert_rule` r
                  WHERE r.tenant_id = t.id
                    AND r.owner_id = 0
                    AND r.rule_code = d.rule_code
                    AND r.deleted = 0);

-- -------------------------------------------------------------
-- 4. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS alert_rule_cnt FROM `alert_rule` WHERE deleted = 0;
SELECT COUNT(*) AS alert_event_cnt FROM `alert_event` WHERE deleted = 0;
SELECT rule_code, rule_name, rule_type, severity, threshold_value, threshold_unit, notify_role
FROM `alert_rule`
WHERE deleted = 0
  AND owner_id = 0
ORDER BY sort;
SELECT TABLE_NAME, TABLE_COMMENT
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('alert_rule', 'alert_event');
