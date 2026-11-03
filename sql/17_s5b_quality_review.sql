-- =============================================================
-- 17_s5b_quality_review.sql  S5-B 人工复核流转与人机质检对比
-- 依据 docs/产品推进路线图.md 1.2.5：
--   QC-06 质检评分卡与人工复核（PENDING → REVIEWED → CONFIRMED + 驳回退回）
--   QC-07 人机质检对比报表（自动分 vs 复核分、逐规则命中改判）
-- 变更内容（全部为增量字段，不改动已有列语义）：
--   1) quality_record.review_action
--      最近一次复核动作：REVIEW 提交复核 / CONFIRM 确认归档 / REJECT 驳回退回
--      为空表示记录尚未被人复核；reviewer_* / review_score / review_time 沿用 S5-A 预留列
--   2) quality_item.review_hit
--      人工改判后的命中结论：1 命中 / 0 未命中 / NULL 未改判（沿用自动结论）
--   3) quality_item.review_deduct
--      人工改判后的扣分，与 review_hit 同时写入；NULL 表示未改判
--   4) quality_record.idx_tenant_review_time
--      复核时间区间统计与「待复核」列表的支撑索引
-- 说明：
--   - 自动质检结论（score / grade / pass_flag / hit / deduct_score）一律不被复核改写，
--     生效口径由「复核分优先」在服务端计算，历史快照因此可完整追溯
--   - 可重复执行：字段与索引存在时跳过，重复执行不会报错
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. quality_record.review_action
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'quality_record'
                  AND COLUMN_NAME = 'review_action');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `quality_record`
            ADD COLUMN `review_action` VARCHAR(16) DEFAULT NULL
                COMMENT ''最近复核动作：REVIEW 提交复核 / CONFIRM 确认归档 / REJECT 驳回退回，空为未复核''
                AFTER `review_time`',
        'SELECT ''quality_record.review_action 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. quality_item.review_hit
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'quality_item'
                  AND COLUMN_NAME = 'review_hit');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `quality_item`
            ADD COLUMN `review_hit` TINYINT DEFAULT NULL
                COMMENT ''人工改判命中结论：1命中 0未命中，NULL 未改判（沿用 hit）''
                AFTER `source`',
        'SELECT ''quality_item.review_hit 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 3. quality_item.review_deduct
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'quality_item'
                  AND COLUMN_NAME = 'review_deduct');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `quality_item`
            ADD COLUMN `review_deduct` INT DEFAULT NULL
                COMMENT ''人工改判后扣分，未改判为 NULL''
                AFTER `review_hit`',
        'SELECT ''quality_item.review_deduct 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 4. 复核时间索引
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'quality_record'
                  AND INDEX_NAME = 'idx_tenant_review_time');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `quality_record`
            ADD KEY `idx_tenant_review_time` (`tenant_id`, `status`, `review_time`)',
        'SELECT ''idx_tenant_review_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 5. 自检
-- -------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quality_record'
  AND COLUMN_NAME IN ('reviewer_id', 'reviewer_name', 'review_comment', 'review_score',
                      'review_time', 'review_action')
ORDER BY ORDINAL_POSITION;

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quality_item'
  AND COLUMN_NAME IN ('hit', 'deduct_score', 'review_hit', 'review_deduct')
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS quality_record_cnt,
       SUM(status = 'PENDING')   AS pending_cnt,
       SUM(status = 'REVIEWED')  AS reviewed_cnt,
       SUM(status = 'CONFIRMED') AS confirmed_cnt
FROM `quality_record`
WHERE deleted = 0;
