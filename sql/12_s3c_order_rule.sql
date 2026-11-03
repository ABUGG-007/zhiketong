-- =============================================================
-- 12_s3c_order_rule.sql  S3-C 工单规则与自动化引擎
-- 依据 docs/产品推进路线图.md 1.2.4：
--   WO-04 自动分配（技能组 + 负载均衡）
--   WO-15 触发器（事件驱动：创建/分配/状态变更/优先级变更/SLA 预警·超时·升级）
--   WO-16 自动化（时间驱动：事件后延迟执行、周期性扫描在跑工单）
-- 变更内容：
--   1) order_rule       工单规则配置（自动分配 / 事件触发器 / 时间自动化统一建模）
--   2) order_rule_run   规则待执行队列与执行流水合一（延迟/时间驱动落 PENDING，扫描到期执行回写 DONE/FAILED/SKIPPED）
-- 说明：
--   - 新增表从建表起即带 tenant_id（当前固定 1，S4 多租户改造时统一迁移）
--   - 可重复执行：建表用 IF NOT EXISTS，预置数据用 NOT EXISTS 判断
--   - 默认不预置规则（功能开箱即用但默认关闭），由管理端「工单规则与自动化」页按需配置
--   - webhook_url 列为后续 WEBHOOK 动作预留，本期不写入不生效
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 工单规则配置表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_rule` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_name`       VARCHAR(64) NOT NULL COMMENT '规则名称，同租户内唯一',
    `rule_type`       VARCHAR(24) NOT NULL COMMENT '规则类型：AUTO_ASSIGN 自动分配 / TRIGGER 事件触发器 / AUTOMATION 时间自动化',
    `trigger_event`   VARCHAR(32)          DEFAULT NULL COMMENT '触发事件：CREATED/ASSIGNED/PROCESSING/RESOLVED/CLOSED/REOPENED/PRIORITY_CHANGED/SLA_WARN/SLA_OVERDUE/SLA_ESCALATE；纯时间自动化可为空',
    `delay_minutes`   INT         NOT NULL DEFAULT 0 COMMENT '事件后延迟执行分钟数，0 表示立即执行，>0 由扫描任务到期执行',
    `match_priority`  VARCHAR(10)          DEFAULT NULL COMMENT '匹配条件：工单优先级 P0-P3，为空表示不限',
    `match_category`  VARCHAR(32)          DEFAULT NULL COMMENT '匹配条件：问题分类，为空表示不限',
    `match_source`    VARCHAR(24)          DEFAULT NULL COMMENT '匹配条件：来源渠道 CHAT/MANUAL/PHONE，为空表示不限',
    `action_type`     VARCHAR(32) NOT NULL COMMENT '动作类型：AUTO_ASSIGN/ASSIGN_AGENT/CHANGE_PRIORITY/CHANGE_STATUS/NOTIFY_ROLE',
    `action_value`    VARCHAR(255)         DEFAULT NULL COMMENT '动作目标值：新优先级 / 新状态 / 通知角色(ADMIN|CREATOR|ASSIGNEE) / 指定坐席 ID',
    `assign_group_id` BIGINT               DEFAULT NULL COMMENT '自动分配取该技能组在岗坐席，为空表示全体在岗',
    `webhook_url`     VARCHAR(255)         DEFAULT NULL COMMENT 'WEBHOOK 回调地址（本期预留，暂不生效）',
    `description`     VARCHAR(255)         DEFAULT NULL COMMENT '规则说明',
    `sort`            INT         NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不再匹配执行）',
    `create_by`       BIGINT               DEFAULT NULL COMMENT '创建人 ID',
    `update_by`       BIGINT               DEFAULT NULL COMMENT '更新人 ID',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `rule_name`, `deleted`),
    KEY `idx_tenant_type_status` (`tenant_id`, `rule_type`, `status`),
    KEY `idx_trigger` (`tenant_id`, `trigger_event`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单规则与自动化配置表';

-- -------------------------------------------------------------
-- 2. 规则待执行队列与执行流水（合一）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_rule_run` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_id`       BIGINT      NOT NULL COMMENT '规则 ID',
    `order_id`      BIGINT      NOT NULL COMMENT '工单 ID',
    `event`         VARCHAR(32)          DEFAULT NULL COMMENT '触发该次执行的事件编码',
    `action_type`   VARCHAR(32) NOT NULL COMMENT '执行的动作类型快照',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '执行状态：PENDING 待执行 / DONE 成功 / FAILED 失败 / SKIPPED 跳过（无可用坐席等）',
    `planned_time`  DATETIME    NOT NULL COMMENT '计划执行时间，延迟/时间驱动为到期时刻，立即执行为当前时刻',
    `executed_time` DATETIME             DEFAULT NULL COMMENT '实际执行时间，执行后写入',
    `result`        VARCHAR(500)         DEFAULT NULL COMMENT '执行结果说明（成功动作描述或失败/跳过原因）',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dedup` (`order_id`, `rule_id`, `planned_time`, `deleted`),
    KEY `idx_order_time` (`order_id`, `create_time`),
    KEY `idx_rule_time` (`rule_id`, `create_time`),
    KEY `idx_status_plan` (`status`, `planned_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单规则待执行队列与执行流水表';

-- -------------------------------------------------------------
-- 3. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS order_rule_cnt FROM `order_rule` WHERE deleted = 0;
SELECT COUNT(*) AS order_rule_run_cnt FROM `order_rule_run` WHERE deleted = 0;
SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('order_rule', 'order_rule_run');
