-- =============================================================
-- 18_s6a_ops_metrics.sql  S6-A 运营指标与大屏（DB-01 / DB-02 / DB-04）
-- 依据 docs/产品推进路线图.md 1.2.6 与第 3 章指标口径：
--   DB-02 实时大屏（当前会话量、排队人数、排队时长）
--   DB-04 首响时长、3 分钟回复率（需消息级轮次聚合）
-- 变更内容（全部为增量列与索引，不改动已有列语义）：
--   1) chat_session.reply_round_count
--      人工接待期间（accept_time 之后）访客有效提问轮次
--   2) chat_session.reply_3min_count
--      上述轮次中坐席在 180 秒内首次回复的轮次
--   3) chat_session.ops_metric_time
--      轮次指标计算时间，NULL 表示尚未计算（供回填任务与覆盖率口径识别）
--   4) chat_session.idx_tenant_metric
--      指标回填与区间统计的支撑索引
-- 说明：
--   - 首响时长不新增列：直接由 first_reply_time - first_user_msg_time 计算（S0 已落库）
--   - 3 分钟回复率按「轮次」统计而非「会话」，因此必须会话级预聚合，避免区间内
--     全量扫描 chat_message；口径见 docs/产品推进路线图.md 第 3 章
--   - 可重复执行：字段与索引存在时跳过，重复执行不会报错
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. chat_session.reply_round_count
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_session'
                  AND COLUMN_NAME = 'reply_round_count');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `chat_session`
            ADD COLUMN `reply_round_count` INT NOT NULL DEFAULT 0
                COMMENT ''人工接待期间访客有效提问轮次，会话关闭时计算''
                AFTER `message_count`',
        'SELECT ''chat_session.reply_round_count 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. chat_session.reply_3min_count
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_session'
                  AND COLUMN_NAME = 'reply_3min_count');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `chat_session`
            ADD COLUMN `reply_3min_count` INT NOT NULL DEFAULT 0
                COMMENT ''其中坐席 180 秒内首次回复的轮次，用于 3 分钟回复率''
                AFTER `reply_round_count`',
        'SELECT ''chat_session.reply_3min_count 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 3. chat_session.ops_metric_time
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_session'
                  AND COLUMN_NAME = 'ops_metric_time');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `chat_session`
            ADD COLUMN `ops_metric_time` DATETIME DEFAULT NULL
                COMMENT ''轮次指标计算时间，NULL 表示尚未计算''
                AFTER `reply_3min_count`',
        'SELECT ''chat_session.ops_metric_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 4. 指标索引
-- -------------------------------------------------------------
SET @exists := (SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_session'
                  AND INDEX_NAME = 'idx_tenant_metric');
SET @ddl := IF(@exists = 0,
        'ALTER TABLE `chat_session`
            ADD KEY `idx_tenant_metric` (`tenant_id`, `status`, `ops_metric_time`)',
        'SELECT ''idx_tenant_metric 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 5. 自检
-- -------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'chat_session'
  AND COLUMN_NAME IN ('first_user_msg_time', 'first_reply_time', 'reply_round_count',
                      'reply_3min_count', 'ops_metric_time')
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS session_cnt,
       SUM(status = 'CLOSED')                    AS closed_cnt,
       SUM(status = 'CLOSED' AND ops_metric_time IS NULL) AS pending_metric_cnt
FROM `chat_session`
WHERE deleted = 0;
