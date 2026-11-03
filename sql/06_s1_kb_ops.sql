-- =============================================================
-- 06_s1_kb_ops.sql  S1 知识库运营闭环
-- 依据 docs/产品推进路线图.md S1 与 Agents.md 5.2：
--   1) kb_entry.tags                 标签（与关键词分离，便于按业务线检索）
--   2) kb_similar_question           相似问法（独立成表，参与匹配打分）
--   3) kb_unmatched_question         未命中问题采集与闭环（按归一化问句聚合）
--   4) kb_entry_version              词条版本记录与回滚
--   5) kb_import_task                行业知识包导入批次
-- 可重复执行：建表用 IF NOT EXISTS，加列前先查 INFORMATION_SCHEMA
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 词条标签列
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_entry' AND COLUMN_NAME = 'tags');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `kb_entry` ADD COLUMN `tags` VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''标签，英文逗号分隔'' AFTER `keywords`',
    'SELECT ''kb_entry.tags already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 相似问法表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_similar_question` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entry_id`    BIGINT       NOT NULL COMMENT '所属知识条目 ID',
    `question`    VARCHAR(255) NOT NULL COMMENT '相似问法（同义改写、口语化、错别字形态）',
    `source`      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手工 / AI 大模型扩写 / IMPORT 知识包导入',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`   BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_entry_id` (`entry_id`, `deleted`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库相似问法表';

-- -------------------------------------------------------------
-- 3. 未命中问题表（按归一化问句聚合，ask_count 累计出现次数）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_unmatched_question` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question`            VARCHAR(255) NOT NULL COMMENT '访客原始问法',
    `normalized_question` VARCHAR(255) NOT NULL COMMENT '归一化问法（去标点停用词后），聚合去重键',
    `channel`             VARCHAR(16)  NOT NULL DEFAULT 'SITE' COMMENT '来源渠道：SITE 站内会话 / EMBED 网页接入',
    `session_id`          BIGINT                DEFAULT NULL COMMENT '最近一次出现的会话 ID',
    `user_id`             BIGINT                DEFAULT NULL COMMENT '最近一次提问的用户 ID',
    `ask_count`           INT          NOT NULL DEFAULT 1 COMMENT '累计出现次数',
    `last_score`          DECIMAL(5, 4)         DEFAULT NULL COMMENT '最近一次最佳匹配得分，用于判断覆盖缺口',
    `status`              VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING 待处理 / CONVERTED 已转词条 / IGNORED 已忽略',
    `entry_id`            BIGINT                DEFAULT NULL COMMENT '转成的知识条目 ID',
    `first_ask_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现时间',
    `last_ask_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近出现时间',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_normalized_question` (`normalized_question`),
    KEY `idx_status_ask` (`status`, `ask_count`),
    KEY `idx_last_ask_time` (`last_ask_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库未命中问题表';

-- -------------------------------------------------------------
-- 4. 词条版本表（只追加不改写，无 update_time / deleted）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_entry_version` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entry_id`        BIGINT      NOT NULL COMMENT '知识条目 ID',
    `version_no`      INT         NOT NULL COMMENT '版本号，从 1 递增',
    `change_type`     VARCHAR(16) NOT NULL COMMENT '变更类型：CREATE/UPDATE/STATUS/DELETE/ROLLBACK',
    `before_content`  MEDIUMTEXT           DEFAULT NULL COMMENT '变更前快照（JSON）',
    `after_content`   MEDIUMTEXT           DEFAULT NULL COMMENT '变更后快照（JSON）',
    `operator_id`     BIGINT               DEFAULT NULL COMMENT '操作人 ID',
    `operator_name`   VARCHAR(50)          DEFAULT NULL COMMENT '操作人名称（快照）',
    `remark`          VARCHAR(255)         DEFAULT NULL COMMENT '变更说明',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entry_version` (`entry_id`, `version_no`),
    KEY `idx_entry_time` (`entry_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库词条版本表';

-- -------------------------------------------------------------
-- 5. 行业知识包导入批次表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_import_task` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `package_name`  VARCHAR(128) NOT NULL COMMENT '知识包名称',
    `source`        VARCHAR(16)  NOT NULL DEFAULT 'JSON' COMMENT '导入来源：JSON / CSV',
    `total_count`   INT          NOT NULL DEFAULT 0 COMMENT '条目总数',
    `success_count` INT          NOT NULL DEFAULT 0 COMMENT '成功导入条数',
    `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '失败条数',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS 全部成功 / PARTIAL 部分成功 / FAILED 失败',
    `error_message` VARCHAR(500)          DEFAULT NULL COMMENT '失败原因摘要',
    `operator_id`   BIGINT                DEFAULT NULL COMMENT '导入人 ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识包导入批次表';
