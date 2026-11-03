-- =============================================================
-- 10_s3a_order_form.sql  S3-A 工单表单引擎（自定义字段 + 工单模板）
-- 依据 docs/产品推进路线图.md 1.2.4（WO-05 自定义字段 / WO-06 工单模板）：
--   1) order_field         字段定义（文本 / 多行文本 / 下拉 / 数字 / 日期）
--   2) order_template      工单模板（绑定字段集合、默认分类与优先级）
--   3) order_field_value   工单字段值（含字段名称与类型快照）
--   4) work_order.template_id  工单来源模板（可追溯，模板删除不影响历史工单）
-- 可重复执行：建表用 IF NOT EXISTS，加列/加索引前先查 INFORMATION_SCHEMA
-- 新增表从建表起即带 tenant_id（当前固定 1，S4 多租户改造时统一迁移）
-- 说明：附件类型依赖文件上传与存储，随 S3-C 富媒体能力统一交付，本脚本不建附件字段
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 工单来源模板（可追溯；模板删除后历史工单保留 template_id）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND COLUMN_NAME = 'template_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `work_order` ADD COLUMN `template_id` BIGINT DEFAULT NULL COMMENT ''来源工单模板 ID，为空表示未使用模板'' AFTER `source`',
    'SELECT ''work_order.template_id already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND INDEX_NAME = 'idx_template');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `work_order` ADD KEY `idx_template` (`template_id`)',
    'SELECT ''idx_template already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 自定义字段定义
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_field` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `field_code`   VARCHAR(32)  NOT NULL COMMENT '字段编码（唯一，英文小写与下划线），接口提交字段值时使用',
    `field_name`   VARCHAR(64)  NOT NULL COMMENT '字段名称（表单标签）',
    `field_type`   VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT '字段类型：TEXT 文本 / MULTILINE 多行文本 / SELECT 下拉 / NUMBER 数字 / DATE 日期',
    `options`      VARCHAR(500)          DEFAULT NULL COMMENT '下拉选项，英文逗号分隔；仅 SELECT 使用',
    `required`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填：1是 0否',
    `default_value` VARCHAR(255)         DEFAULT NULL COMMENT '默认值（模板预填用）',
    `placeholder`  VARCHAR(100)          DEFAULT NULL COMMENT '输入提示',
    `description`  VARCHAR(255)          DEFAULT NULL COMMENT '字段说明，展示在表单下沿',
    `sort`         INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不再出现在新建表单）',
    `create_by`    BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`    BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `field_code`, `deleted`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单自定义字段定义表';

-- -------------------------------------------------------------
-- 3. 工单模板
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_template` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `template_name`  VARCHAR(64)  NOT NULL COMMENT '模板名称，如 退款工单',
    `category`       VARCHAR(32)           DEFAULT NULL COMMENT '默认问题分类（为空表示创建时由用户选择）',
    `default_priority` VARCHAR(10)         DEFAULT NULL COMMENT '默认优先级 P0-P3（为空表示按 P2）',
    `skill_group_id` BIGINT                DEFAULT NULL COMMENT '默认技能组 ID（为空表示不限，S3-C 自动分配使用）',
    `field_ids`      VARCHAR(500)          DEFAULT NULL COMMENT '绑定字段 ID，英文逗号分隔，顺序即表单顺序',
    `description`    VARCHAR(255)          DEFAULT NULL COMMENT '模板说明',
    `sort`           INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`      BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`      BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `template_name`, `deleted`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单模板表';

-- -------------------------------------------------------------
-- 4. 工单字段值（保存字段名称与类型快照，字段后续改名或停用不影响历史工单）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_field_value` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `order_id`    BIGINT       NOT NULL COMMENT '工单 ID',
    `field_id`    BIGINT       NOT NULL COMMENT '字段定义 ID',
    `field_code`  VARCHAR(32)  NOT NULL COMMENT '字段编码快照',
    `field_name`  VARCHAR(64)  NOT NULL COMMENT '字段名称快照',
    `field_type`  VARCHAR(16)  NOT NULL COMMENT '字段类型快照',
    `field_value` VARCHAR(2000)         DEFAULT NULL COMMENT '字段值（日期为 yyyy-MM-dd，数字保留原始输入）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '展示顺序快照',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_field` (`order_id`, `field_id`, `deleted`),
    KEY `idx_order_sort` (`order_id`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单自定义字段值表';

-- -------------------------------------------------------------
-- 5. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS order_field_cnt FROM `order_field` WHERE deleted = 0;
SELECT COUNT(*) AS order_template_cnt FROM `order_template` WHERE deleted = 0;
SELECT COUNT(*) AS order_field_value_cnt FROM `order_field_value` WHERE deleted = 0;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order' AND COLUMN_NAME = 'template_id';
