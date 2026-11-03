-- =============================================================
-- 11_s3b_order_sla.sql  S3-B 多级 SLA、停表与超时升级
-- 依据 docs/产品推进路线图.md 1.2.4：
--   WO-12 挂起 / 等待客户反馈（SLA 停表）
--   WO-13 多级 SLA（首次响应 / 每次回复间隔 / 解决）
--   WO-14 SLA 预警与超时升级通知上级
-- 变更内容：
--   1) order_sla_policy    SLA 策略（按优先级配置三级时限、预警阈值与升级规则）
--   2) order_sla_record    工单 SLA 计时记录（每工单一条，含时限快照与停表累计）
--   3) order_sla_pause     停表流水（等待客户反馈 / 其他原因）
--   4) work_order 增 sla_id、sla_status、pause_minutes、reply_due_time
--   5) 预置 P0-P3 四条默认策略，与 application.yml 的 zhiketong.sla 口径一致
-- 可重复执行：建表用 IF NOT EXISTS，加列/加索引前先查 INFORMATION_SCHEMA，
--             预置数据用 NOT EXISTS 判断，重复执行不会产生脏数据
-- 新增表从建表起即带 tenant_id（当前固定 1，S4 多租户改造时统一迁移）
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 工单表增列：SLA 记录关联、SLA 状态、累计停表分钟、下次回复截止
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND COLUMN_NAME = 'sla_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `work_order` ADD COLUMN `sla_id` BIGINT DEFAULT NULL COMMENT ''SLA 计时记录 ID，指向 order_sla_record.id'' AFTER `template_id`',
    'SELECT ''work_order.sla_id already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND COLUMN_NAME = 'sla_status');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `work_order` ADD COLUMN `sla_status` VARCHAR(16) NOT NULL DEFAULT ''NORMAL'' COMMENT ''SLA 状态：NORMAL 正常 / WARN 预警 / OVERDUE 超时 / PAUSED 停表中 / FINISHED 已完结'' AFTER `overdue_flag`',
    'SELECT ''work_order.sla_status already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND COLUMN_NAME = 'pause_minutes');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `work_order` ADD COLUMN `pause_minutes` INT NOT NULL DEFAULT 0 COMMENT ''累计停表分钟数，解决时限按此时长顺延'' AFTER `sla_status`',
    'SELECT ''work_order.pause_minutes already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND COLUMN_NAME = 'reply_due_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `work_order` ADD COLUMN `reply_due_time` DATETIME DEFAULT NULL COMMENT ''下次回复截止时间（回复间隔 SLA），为空表示未启用该级时限'' AFTER `pause_minutes`',
    'SELECT ''work_order.reply_due_time already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND INDEX_NAME = 'idx_sla_status');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `work_order` ADD KEY `idx_sla_status` (`sla_status`, `overdue_flag`)',
    'SELECT ''idx_sla_status already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND INDEX_NAME = 'idx_reply_due');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `work_order` ADD KEY `idx_reply_due` (`reply_due_time`)',
    'SELECT ''idx_reply_due already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. SLA 策略表：按优先级配置三级时限、预警阈值与升级规则
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_sla_policy` (
    `id`                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `policy_name`            VARCHAR(64) NOT NULL COMMENT '策略名称，如 P1 标准时效',
    `priority`               VARCHAR(10) NOT NULL COMMENT '适用优先级：P0/P1/P2/P3',
    `first_response_minutes` INT         NOT NULL DEFAULT 30 COMMENT '首次响应时限（分钟），0 表示不启用该级',
    `reply_interval_minutes` INT         NOT NULL DEFAULT 0 COMMENT '每次回复间隔时限（分钟），0 表示不启用该级',
    `resolve_minutes`        INT         NOT NULL DEFAULT 120 COMMENT '解决时限（分钟），0 表示不启用该级',
    `warn_percent`           INT         NOT NULL DEFAULT 80 COMMENT '已消耗时长达到时限的该百分比时预警，取值 1-99',
    `escalate_minutes`       INT         NOT NULL DEFAULT 0 COMMENT '超时后再经过多少分钟仍未解决则升级通知上级，0 表示超时即升级',
    `escalate_role`          VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '升级通知对象角色，当前支持 ADMIN',
    `default_flag`           TINYINT     NOT NULL DEFAULT 0 COMMENT '是否为该优先级的默认策略：1是 0否；同优先级同租户仅允许一条默认',
    `description`            VARCHAR(255)         DEFAULT NULL COMMENT '策略说明',
    `sort`                   INT         NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`                 TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后新建工单不再使用该策略）',
    `create_by`              BIGINT               DEFAULT NULL COMMENT '创建人 ID',
    `update_by`              BIGINT               DEFAULT NULL COMMENT '更新人 ID',
    `create_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_policy` (`tenant_id`, `policy_name`, `deleted`),
    KEY `idx_tenant_priority` (`tenant_id`, `priority`, `status`, `default_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单 SLA 策略表';

-- -------------------------------------------------------------
-- 3. 工单 SLA 计时记录：每工单一条，保存策略快照与三级时限
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_sla_record` (
    `id`                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `order_id`               BIGINT      NOT NULL COMMENT '工单 ID',
    `policy_id`              BIGINT               DEFAULT NULL COMMENT '来源策略 ID，为空表示使用配置文件兜底时限',
    `policy_name`            VARCHAR(64)          DEFAULT NULL COMMENT '策略名称快照',
    `priority`               VARCHAR(10) NOT NULL COMMENT '优先级快照，优先级变更时重算时限并更新快照',
    `first_response_minutes` INT         NOT NULL DEFAULT 0 COMMENT '首次响应时限快照（分钟）',
    `reply_interval_minutes` INT         NOT NULL DEFAULT 0 COMMENT '回复间隔时限快照（分钟）',
    `resolve_minutes`        INT         NOT NULL DEFAULT 0 COMMENT '解决时限快照（分钟）',
    `warn_percent`           INT         NOT NULL DEFAULT 80 COMMENT '预警阈值快照（百分比）',
    `escalate_minutes`       INT         NOT NULL DEFAULT 0 COMMENT '升级延迟快照（分钟）',
    `start_time`             DATETIME    NOT NULL COMMENT '计时起点，取工单创建时间；重开后重置为当前时间',
    `first_response_deadline` DATETIME            DEFAULT NULL COMMENT '首次响应截止时间，含停表顺延',
    `first_response_time`    DATETIME             DEFAULT NULL COMMENT '实际首次响应时间，与 work_order.first_response_time 同步',
    `last_reply_time`        DATETIME             DEFAULT NULL COMMENT '最近一次坐席回复时间（添加处理进展）',
    `reply_due_time`         DATETIME             DEFAULT NULL COMMENT '下次回复截止时间，含停表顺延；未启用回复间隔时为空',
    `resolve_deadline`       DATETIME             DEFAULT NULL COMMENT '解决截止时间，含停表顺延；与 work_order.sla_deadline 同步',
    `pause_minutes`          INT         NOT NULL DEFAULT 0 COMMENT '累计停表分钟数',
    `paused`                 TINYINT     NOT NULL DEFAULT 0 COMMENT '当前是否停表中：1是 0否',
    `pause_start`            DATETIME             DEFAULT NULL COMMENT '本次停表开始时间',
    `first_response_warn`    TINYINT     NOT NULL DEFAULT 0 COMMENT '首次响应是否已预警：1是 0否',
    `first_response_overdue` TINYINT     NOT NULL DEFAULT 0 COMMENT '首次响应是否已超时：1是 0否',
    `reply_warn`             TINYINT     NOT NULL DEFAULT 0 COMMENT '回复间隔是否已预警：1是 0否',
    `reply_overdue`          TINYINT     NOT NULL DEFAULT 0 COMMENT '回复间隔是否已超时：1是 0否',
    `resolve_warn`           TINYINT     NOT NULL DEFAULT 0 COMMENT '解决时限是否已预警：1是 0否',
    `resolve_overdue`        TINYINT     NOT NULL DEFAULT 0 COMMENT '解决时限是否已超时：1是 0否',
    `escalated`              TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已升级通知上级：1是 0否',
    `status`                 VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '计时状态：RUNNING 计时中 / PAUSED 停表中 / FINISHED 已完结',
    `sla_status`             VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'SLA 状态：NORMAL/WARN/OVERDUE/PAUSED/FINISHED，与 work_order.sla_status 同步',
    `create_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order` (`order_id`, `deleted`),
    KEY `idx_status_resolve` (`status`, `resolve_deadline`),
    KEY `idx_status_first_response` (`status`, `first_response_deadline`),
    KEY `idx_status_reply` (`status`, `reply_due_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单 SLA 计时记录表';

-- -------------------------------------------------------------
-- 4. 停表流水：记录每次挂起与恢复，用于时长核对与工单详情展示
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_sla_pause` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `order_id`         BIGINT       NOT NULL COMMENT '工单 ID',
    `record_id`        BIGINT       NOT NULL COMMENT 'SLA 计时记录 ID',
    `pause_type`       VARCHAR(32)  NOT NULL DEFAULT 'WAITING_CUSTOMER' COMMENT '停表类型：WAITING_CUSTOMER 等待客户反馈 / OTHER 其他原因',
    `reason`           VARCHAR(255)          DEFAULT NULL COMMENT '停表原因',
    `pause_time`       DATETIME     NOT NULL COMMENT '停表开始时间',
    `resume_time`      DATETIME              DEFAULT NULL COMMENT '恢复计时时间，为空表示仍在停表中',
    `duration_minutes` INT                   DEFAULT NULL COMMENT '本次停表时长（分钟），恢复时写入',
    `operator_id`      BIGINT                DEFAULT NULL COMMENT '操作人 ID',
    `operator_name`    VARCHAR(50)           DEFAULT NULL COMMENT '操作人名称（快照）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_order_time` (`order_id`, `pause_time`),
    KEY `idx_record` (`record_id`, `resume_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单 SLA 停表流水表';

-- -------------------------------------------------------------
-- 5. 预置 P0-P3 默认策略（与 application.yml 的 zhiketong.sla 一致，并补回复间隔时限）
-- -------------------------------------------------------------
INSERT INTO `order_sla_policy`
(`tenant_id`, `policy_name`, `priority`, `first_response_minutes`, `reply_interval_minutes`,
 `resolve_minutes`, `warn_percent`, `escalate_minutes`, `escalate_role`, `default_flag`,
 `description`, `sort`, `status`)
SELECT 1, 'P0 紧急时效', 'P0', 30, 30, 120, 80, 0, 'ADMIN', 1,
       '紧急工单：30 分钟首响、每 30 分钟回复一次、2 小时内解决，超时立即升级', 1, 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `order_sla_policy` WHERE `tenant_id` = 1 AND `policy_name` = 'P0 紧急时效' AND `deleted` = 0);

INSERT INTO `order_sla_policy`
(`tenant_id`, `policy_name`, `priority`, `first_response_minutes`, `reply_interval_minutes`,
 `resolve_minutes`, `warn_percent`, `escalate_minutes`, `escalate_role`, `default_flag`,
 `description`, `sort`, `status`)
SELECT 1, 'P1 高优时效', 'P1', 120, 120, 480, 80, 30, 'ADMIN', 1,
       '高优工单：2 小时首响、每 2 小时回复一次、8 小时内解决，超时 30 分钟后升级', 2, 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `order_sla_policy` WHERE `tenant_id` = 1 AND `policy_name` = 'P1 高优时效' AND `deleted` = 0);

INSERT INTO `order_sla_policy`
(`tenant_id`, `policy_name`, `priority`, `first_response_minutes`, `reply_interval_minutes`,
 `resolve_minutes`, `warn_percent`, `escalate_minutes`, `escalate_role`, `default_flag`,
 `description`, `sort`, `status`)
SELECT 1, 'P2 标准时效', 'P2', 480, 480, 1440, 80, 60, 'ADMIN', 1,
       '标准工单：8 小时首响、每 8 小时回复一次、24 小时内解决，超时 1 小时后升级', 3, 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `order_sla_policy` WHERE `tenant_id` = 1 AND `policy_name` = 'P2 标准时效' AND `deleted` = 0);

INSERT INTO `order_sla_policy`
(`tenant_id`, `policy_name`, `priority`, `first_response_minutes`, `reply_interval_minutes`,
 `resolve_minutes`, `warn_percent`, `escalate_minutes`, `escalate_role`, `default_flag`,
 `description`, `sort`, `status`)
SELECT 1, 'P3 低优时效', 'P3', 1440, 1440, 4320, 80, 240, 'ADMIN', 1,
       '低优工单：24 小时首响、每 24 小时回复一次、3 天内解决，超时 4 小时后升级', 4, 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `order_sla_policy` WHERE `tenant_id` = 1 AND `policy_name` = 'P3 低优时效' AND `deleted` = 0);

-- -------------------------------------------------------------
-- 6. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS order_sla_policy_cnt FROM `order_sla_policy` WHERE deleted = 0;
SELECT COUNT(*) AS order_sla_record_cnt FROM `order_sla_record` WHERE deleted = 0;
SELECT COUNT(*) AS order_sla_pause_cnt FROM `order_sla_pause` WHERE deleted = 0;
SELECT `priority`, `policy_name`, `first_response_minutes`, `reply_interval_minutes`,
       `resolve_minutes`, `warn_percent`, `escalate_minutes`, `default_flag`
FROM `order_sla_policy` WHERE `deleted` = 0 AND `tenant_id` = 1 ORDER BY `sort`;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order'
  AND COLUMN_NAME IN ('sla_id', 'sla_status', 'pause_minutes', 'reply_due_time')
ORDER BY ORDINAL_POSITION;
