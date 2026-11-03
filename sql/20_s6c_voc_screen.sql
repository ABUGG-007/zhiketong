-- =============================================================
-- 20_s6c_voc_screen.sql  S6-C VOC 客户之声与大屏配置化
-- 依据 docs/产品推进路线图.md 1.2.6：
--   DB-06 VOC 客户之声（未命中聚类、热点话题、词云）
--   DB-07 行业模板与个性化定制大屏（组件布局 / 指标可选）
-- 变更内容：
--   1) voc_topic                      客户之声主题（自动聚类 / 人工创建统一建模）
--   2) kb_unmatched_question.topic_id 未命中问法归属的主题
--      kb_unmatched_question.cluster_score 归属相似度（0-100）
--      kb_unmatched_question.idx_tenant_topic 按主题回溯问法的支撑索引
--   3) screen_layout                   租户级大屏布局配置（组件显隐、顺序、密度）
-- 说明：
--   - 新增表从建表起即带 tenant_id，由多租户拦截器自动注入条件
--   - 可重复执行：建表用 IF NOT EXISTS，加列与加索引先查 INFORMATION_SCHEMA
--   - 聚类只写「归属」，不改变 kb_unmatched_question 的处理状态（PENDING / CONVERTED / IGNORED）
--   - screen_layout 只存布局意图，指标口径仍由 /api/statistics/ops/** 统一提供
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 客户之声主题表（DB-06）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `voc_topic` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `topic_name`        VARCHAR(64)  NOT NULL COMMENT '主题名称，自动聚类取代表关键词，可人工改名',
    `topic_type`        VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT '来源：AUTO 自动聚类 / MANUAL 人工创建',
    `keywords`          VARCHAR(500)          DEFAULT NULL COMMENT '代表关键词，英文逗号分隔，用于后续问法归类',
    `sample_question`   VARCHAR(255)          DEFAULT NULL COMMENT '代表问法快照（聚类种子或人工示例）',
    `question_count`    INT          NOT NULL DEFAULT 0 COMMENT '关联未命中问法条数',
    `ask_total`         INT          NOT NULL DEFAULT 0 COMMENT '关联问法累计提问次数',
    `pending_count`     INT          NOT NULL DEFAULT 0 COMMENT '其中仍待处理的问法条数',
    `converted_count`   INT          NOT NULL DEFAULT 0 COMMENT '其中已转为知识词条的问法条数',
    `channels`          VARCHAR(64)           DEFAULT NULL COMMENT '渠道分布快照，如 SITE:12,EMBED:3',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不再参与自动归类）',
    `sort`              INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `remark`            VARCHAR(255)          DEFAULT NULL COMMENT '运营备注：结论、跟进人与处理策略',
    `last_cluster_time` DATETIME              DEFAULT NULL COMMENT '最近一次参与聚类的结束时间',
    `create_by`         BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`         BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `topic_name`, `deleted`),
    KEY `idx_tenant_type_ask` (`tenant_id`, `topic_type`, `ask_total`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户之声主题表';

-- -------------------------------------------------------------
-- 2. kb_unmatched_question.topic_id / cluster_score
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'kb_unmatched_question'
                  AND COLUMN_NAME = 'topic_id');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `kb_unmatched_question`
            ADD COLUMN `topic_id` BIGINT DEFAULT NULL COMMENT ''归属的客户之声主题 ID，NULL 表示尚未聚类''',
        'SELECT ''kb_unmatched_question.topic_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'kb_unmatched_question'
                  AND COLUMN_NAME = 'cluster_score');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `kb_unmatched_question`
            ADD COLUMN `cluster_score` INT DEFAULT NULL COMMENT ''归属相似度百分比 0-100，人工创建主题下为 NULL''
                AFTER `topic_id`',
        'SELECT ''kb_unmatched_question.cluster_score 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 3. 主题回溯索引
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'kb_unmatched_question'
                  AND INDEX_NAME = 'idx_tenant_topic');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `kb_unmatched_question`
            ADD KEY `idx_tenant_topic` (`tenant_id`, `topic_id`, `ask_count`)',
        'SELECT ''idx_tenant_topic 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 4. 大屏布局配置表（DB-07）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `screen_layout` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `layout_code` VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '布局编码，default 为租户默认大屏',
    `layout_name` VARCHAR(64)  NOT NULL DEFAULT '默认大屏' COMMENT '布局名称',
    `widgets`     VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '组件配置 JSON 数组：[{code,visible,order}]',
    `density`     VARCHAR(16)  NOT NULL DEFAULT 'COMFORTABLE' COMMENT '展示密度：COMFORTABLE 舒适 / COMPACT 紧凑',
    `refresh_seconds` INT      NOT NULL DEFAULT 15 COMMENT '轮询刷新间隔（秒），WS 不可用时生效，5-300',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `update_by`   BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `layout_code`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '大屏布局配置表';

-- -------------------------------------------------------------
-- 5. 自检
-- -------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_unmatched_question'
  AND COLUMN_NAME IN ('topic_id', 'cluster_score')
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS topic_cnt,
       SUM(topic_type = 'AUTO')   AS auto_cnt,
       SUM(topic_type = 'MANUAL') AS manual_cnt
FROM `voc_topic`
WHERE deleted = 0;

SELECT COUNT(*) AS unmatched_cnt,
       SUM(topic_id IS NOT NULL) AS clustered_cnt
FROM `kb_unmatched_question`
WHERE deleted = 0;

SELECT COUNT(*) AS layout_cnt FROM `screen_layout` WHERE deleted = 0;
