-- =============================================================
-- 07_s2a_quick_reply_skill.sql  S2-A 快捷回复与技能组分配
-- 依据 docs/产品推进路线图.md S2 与 Agents.md 5.4：
--   1) chat_session.skill_group_id   会话归属技能组（转人工时确定，供分配使用）
--   2) quick_reply                   快捷回复话术（PUBLIC 公共 / PERSONAL 个人）
--   3) skill_group                   技能组主数据
--   4) sys_user_skill                坐席与技能组的多对多关系（含能力值）
-- 可重复执行：建表用 IF NOT EXISTS，加列前先查 INFORMATION_SCHEMA
-- 新增表从建表起即带 tenant_id（当前固定 1，S4 多租户改造时统一迁移）
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 会话归属技能组
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'skill_group_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `chat_session` ADD COLUMN `skill_group_id` BIGINT DEFAULT NULL COMMENT ''期望技能组 ID（转人工时确定，空表示不限）'' AFTER `agent_id`',
    'SELECT ''chat_session.skill_group_id already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND INDEX_NAME = 'idx_skill_group');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `chat_session` ADD KEY `idx_skill_group` (`skill_group_id`, `status`)',
    'SELECT ''idx_skill_group already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 技能组主数据
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `skill_group` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `name`        VARCHAR(64)  NOT NULL COMMENT '技能组名称，如 订单售后',
    `description` VARCHAR(255)          DEFAULT NULL COMMENT '组职责说明，转人工时为访客展示',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不参与分配与展示）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '技能组表';

-- -------------------------------------------------------------
-- 3. 坐席与技能组关系（一个坐席可属多个组）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_skill` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      BIGINT   NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `user_id`        BIGINT   NOT NULL COMMENT '坐席用户 ID',
    `skill_group_id` BIGINT   NOT NULL COMMENT '技能组 ID',
    `level`          TINYINT  NOT NULL DEFAULT 3 COMMENT '能力值 1-5，5 为最强，分配时作为同负载下的次要排序',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_group` (`user_id`, `skill_group_id`),
    KEY `idx_group_user` (`skill_group_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '坐席技能组关系表';

-- -------------------------------------------------------------
-- 4. 快捷回复话术
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quick_reply` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `scope`       VARCHAR(16)   NOT NULL DEFAULT 'PERSONAL' COMMENT '范围：PUBLIC 公共话术（管理员维护，全体坐席可见）/ PERSONAL 个人话术（仅本人可见）',
    `owner_id`    BIGINT                 DEFAULT NULL COMMENT '所有人 ID：PERSONAL 必填，PUBLIC 为空',
    `category`    VARCHAR(32)   NOT NULL DEFAULT '通用' COMMENT '话术分类，如 开场语 / 订单问题 / 结束语',
    `title`       VARCHAR(64)   NOT NULL COMMENT '话术标题，坐席端列表展示',
    `content`     VARCHAR(1000) NOT NULL COMMENT '话术正文，点击后插入回复框',
    `sort`        INT           NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `use_count`   INT           NOT NULL DEFAULT 0 COMMENT '累计使用次数',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`   BIGINT                 DEFAULT NULL COMMENT '创建人 ID',
    `update_by`   BIGINT                 DEFAULT NULL COMMENT '更新人 ID',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_scope_owner` (`scope`, `owner_id`, `status`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '快捷回复话术表';

-- -------------------------------------------------------------
-- 5. 初始化技能组（已存在同名组则跳过；话术不预置，由管理员与坐席自行维护）
-- -------------------------------------------------------------
INSERT INTO `skill_group` (`tenant_id`, `name`, `description`, `sort`, `status`)
SELECT 1, '售前咨询', '产品能力、价格与方案咨询', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_group` WHERE `name` = '售前咨询' AND `deleted` = 0);

INSERT INTO `skill_group` (`tenant_id`, `name`, `description`, `sort`, `status`)
SELECT 1, '订单售后', '订单、支付、退款与物流问题', 2, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_group` WHERE `name` = '订单售后' AND `deleted` = 0);

INSERT INTO `skill_group` (`tenant_id`, `name`, `description`, `sort`, `status`)
SELECT 1, '技术支持', '账号、接口对接与故障排查', 3, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_group` WHERE `name` = '技术支持' AND `deleted` = 0);

-- -------------------------------------------------------------
-- 6. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS skill_group_cnt FROM `skill_group` WHERE deleted = 0;
SELECT COUNT(*) AS quick_reply_cnt FROM `quick_reply` WHERE deleted = 0;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'skill_group_id';
