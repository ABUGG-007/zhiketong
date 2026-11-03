-- =============================================================
-- 13_s4a_tenant.sql  S4-A 多租户隔离基座
-- 依据 docs/产品推进路线图.md 1.2.7 / 第 4 章 S4：
--   PL-01 多租户数据隔离（tenant_id 全量补齐 + 租户拦截器）
--   PL-02 租户管理（开通、状态、到期）
--   PL-03 套餐与功能开关（套餐权益表，租户绑定一个套餐）
-- 变更内容：
--   1) tenant       租户主数据（tenant.id 即业务表的 tenant_id 取值，平台管理员固定 tenant_id = 0）
--   2) tenant_plan  套餐权益字典（平台级系统表，不参与租户隔离）
--   3) 全量业务表补 tenant_id 列 + 索引（系统表 tenant / tenant_plan / sys_role 除外）
--   4) sys_user 唯一键由 (username) 调整为 (tenant_id, username, deleted)，允许不同租户同名账号
--   5) 种子：默认租户 demo（id=1，标准版）、三档套餐、平台管理员角色与账号 platform01
-- 说明：
--   - 可重复执行：建表用 IF NOT EXISTS，加列/加索引前查 information_schema，预置数据用 NOT EXISTS
--   - 存量数据（29 张表）全部归属默认租户 1，迁移不产生跨租户错配
--   - 平台管理员 tenant_id = 0，不做租户过滤；租户管理员与坐席按自身 tenant_id 隔离
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 租户主数据
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tenant` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，即业务表 tenant_id 取值',
    `tenant_code`   VARCHAR(32)  NOT NULL COMMENT '租户编码，嵌入接入等场景的对外标识',
    `tenant_name`   VARCHAR(64)  NOT NULL COMMENT '租户名称（企业名）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 正常 / DISABLED 已停用 / EXPIRED 已到期',
    `plan_code`     VARCHAR(32)  NOT NULL DEFAULT 'standard' COMMENT '套餐编码，关联 tenant_plan.plan_code',
    `expire_time`   DATETIME              DEFAULT NULL COMMENT '服务到期时间，NULL 表示不限（试用版按开通时间计算）',
    `contact_name`  VARCHAR(50)           DEFAULT NULL COMMENT '联系人姓名',
    `contact_phone` VARCHAR(20)           DEFAULT NULL COMMENT '联系人手机号',
    `contact_email` VARCHAR(100)          DEFAULT NULL COMMENT '联系人邮箱',
    `remark`        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`, `deleted`),
    KEY `idx_status_expire` (`status`, `expire_time`),
    KEY `idx_plan_code` (`plan_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户主数据表';

-- -------------------------------------------------------------
-- 2. 套餐权益字典（平台级系统表）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tenant_plan` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_code`       VARCHAR(32)   NOT NULL COMMENT '套餐编码：trial / standard / professional',
    `plan_name`       VARCHAR(64)   NOT NULL COMMENT '套餐名称',
    `tagline`         VARCHAR(255)           DEFAULT NULL COMMENT '一句话定位',
    `price`           DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '价格（元），0 表示免费',
    `period`          VARCHAR(16)   NOT NULL DEFAULT '月' COMMENT '计费周期：月 / 年 / 14 天',
    `seat_limit`      INT           NOT NULL DEFAULT 1 COMMENT '坐席账号上限，-1 表示不限',
    `session_limit`   INT           NOT NULL DEFAULT 0 COMMENT '每自然月会话上限，-1 表示不限',
    `order_limit`     INT           NOT NULL DEFAULT 0 COMMENT '每自然月工单上限，-1 表示不限',
    `kb_limit`        INT           NOT NULL DEFAULT 0 COMMENT '知识库条目上限，-1 表示不限',
    `ai_limit`        INT           NOT NULL DEFAULT 0 COMMENT '每自然月 AI 应答次数上限，-1 表示不限',
    `features`        VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '功能点清单，英文竖线 | 分隔',
    `recommended`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否推荐套餐：1是 0否',
    `sort`            INT           NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '套餐权益字典表';

-- -------------------------------------------------------------
-- 3. 全量业务表补 tenant_id 列与索引（系统表除外）
-- -------------------------------------------------------------
DROP PROCEDURE IF EXISTS `zk_add_tenant_id`;

DELIMITER $$
CREATE PROCEDURE `zk_add_tenant_id`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tname VARCHAR(64);
    DECLARE col_cnt INT DEFAULT 0;
    DECLARE idx_cnt INT DEFAULT 0;
    DECLARE cur CURSOR FOR
        SELECT TABLE_NAME
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME NOT IN ('tenant', 'tenant_plan', 'sys_role');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tname;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;

        SELECT COUNT(*) INTO col_cnt
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tname AND COLUMN_NAME = 'tenant_id';
        IF col_cnt = 0 THEN
            SET @ddl = CONCAT('ALTER TABLE `', tname,
                '` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''租户 ID，1 为平台默认租户'' AFTER `id`');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        -- 只补单列索引；已有以 tenant_id 开头的复合索引时跳过，避免重复索引
        SELECT COUNT(*) INTO idx_cnt
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tname
          AND COLUMN_NAME = 'tenant_id' AND SEQ_IN_INDEX = 1;
        IF idx_cnt = 0 THEN
            SET @ddl = CONCAT('ALTER TABLE `', tname, '` ADD KEY `idx_tenant_id` (`tenant_id`)');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL `zk_add_tenant_id`();
DROP PROCEDURE IF EXISTS `zk_add_tenant_id`;

-- -------------------------------------------------------------
-- 4. sys_user 唯一键调整为租户内唯一
-- -------------------------------------------------------------
SET @has_new := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                   AND INDEX_NAME = 'uk_tenant_username');
SET @has_old := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                   AND INDEX_NAME = 'uk_username');
SET @ddl := IF(@has_new = 0 AND @has_old > 0,
    'ALTER TABLE `sys_user` DROP INDEX `uk_username`, ADD UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `deleted`)',
    IF(@has_new = 0,
        'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `deleted`)',
        'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 5. 种子数据
-- -------------------------------------------------------------
-- 5.1 套餐权益（与历史 application.yml 的 zhiketong.plan 口径一致，迁移为库表驱动）
INSERT INTO `tenant_plan`
(`plan_code`, `plan_name`, `tagline`, `price`, `period`, `seat_limit`, `session_limit`,
 `order_limit`, `kb_limit`, `ai_limit`, `features`, `recommended`, `sort`, `status`)
SELECT 'trial', '试用版', '14 天完整体验，适合验证 AI 首答与转人工闭环', 0.00, '14 天', 2, 500,
       100, 50, 300,
       'AI 知识库首答|一键转人工（带完整上下文）|基础工单与 SLA 提醒|满意度评价', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `tenant_plan` WHERE `plan_code` = 'trial');

INSERT INTO `tenant_plan`
(`plan_code`, `plan_name`, `tagline`, `price`, `period`, `seat_limit`, `session_limit`,
 `order_limit`, `kb_limit`, `ai_limit`, `features`, `recommended`, `sort`, `status`)
SELECT 'standard', '标准版', '中小客服团队日常使用，覆盖咨询到工单的完整闭环', 399.00, '月', 5, 3000,
       1000, 300, 2000,
       '试用版全部能力|坐席排队与负载均衡|工单状态机与流转日志|知识库命中排行与未命中分析|AI 自主解决率与 SLA 看板',
       1, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM `tenant_plan` WHERE `plan_code` = 'standard');

INSERT INTO `tenant_plan`
(`plan_code`, `plan_name`, `tagline`, `price`, `period`, `seat_limit`, `session_limit`,
 `order_limit`, `kb_limit`, `ai_limit`, `features`, `recommended`, `sort`, `status`)
SELECT 'professional', '专业版', '多班次、高并发团队，强调履约与数据沉淀', 899.00, '月', 20, 20000,
       -1, 2000, 10000,
       '标准版全部能力|20 坐席并发接待|工单数量不限|大模型调用明细与失败告警|优先技术支持',
       0, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM `tenant_plan` WHERE `plan_code` = 'professional');

-- 5.2 默认租户（存量 29 张表的数据全部归属该租户）
INSERT INTO `tenant`
(`id`, `tenant_code`, `tenant_name`, `status`, `plan_code`, `expire_time`, `remark`)
SELECT 1, 'demo', '智客通演示租户', 'ENABLED', 'standard', NULL, '初始化默认租户，承接历史存量数据'
WHERE NOT EXISTS (SELECT 1 FROM `tenant` WHERE `id` = 1);

-- 5.3 平台管理员角色与账号（密码 123456，与初始化账号同 BCrypt）
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`, `sort`, `status`)
SELECT 'PLATFORM_ADMIN', '平台管理员', '管理所有租户、套餐、订单与平台配置', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'PLATFORM_ADMIN');

INSERT INTO `sys_user` (`tenant_id`, `username`, `password`, `nickname`, `role_code`, `status`)
SELECT 0, 'platform01', '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy',
       '平台管理员', 'PLATFORM_ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'platform01' AND `tenant_id` = 0);

-- 5.4 嵌入接入配置按 tenant_code 关联到租户
UPDATE `tenant_embed_config` c
    JOIN `tenant` t ON t.`tenant_code` = c.`tenant_code`
SET c.`tenant_id` = t.`id`
WHERE c.`tenant_id` IS NULL OR c.`tenant_id` <> t.`id`;

-- -------------------------------------------------------------
-- 6. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS tenant_cnt FROM `tenant` WHERE deleted = 0;
SELECT plan_code, plan_name, seat_limit, session_limit, kb_limit FROM `tenant_plan` WHERE status = 1 ORDER BY sort;
SELECT COUNT(DISTINCT TABLE_NAME) AS tenant_col_table_cnt
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'tenant_id';
SELECT username, role_code, tenant_id FROM `sys_user` WHERE deleted = 0 ORDER BY id;
