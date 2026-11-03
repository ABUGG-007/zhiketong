-- =============================================================
-- 05_s0_metrics.sql  S0 指标口径落库：会话首响时长字段
-- 依据 docs/产品推进路线图.md 第 3 章口径：
--   首次响应时长 = 访客首条有效消息时间 -> 坐席/AI 首次有效回复时间
--   系统欢迎语不计入回复；访客连续多条消息只按第一条计时
-- 可重复执行：已存在同名列时跳过
-- =============================================================

-- 1. 访客首条有效消息时间
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session'
      AND COLUMN_NAME = 'first_user_msg_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `chat_session` ADD COLUMN `first_user_msg_time` DATETIME DEFAULT NULL COMMENT ''访客首条有效消息时间（首响计时起点）'' AFTER `rating_comment`',
    'SELECT ''first_user_msg_time already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 首次有效回复时间（AI 或坐席，不含系统欢迎语）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session'
      AND COLUMN_NAME = 'first_reply_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `chat_session` ADD COLUMN `first_reply_time` DATETIME DEFAULT NULL COMMENT ''首次有效回复时间（AI/坐席，不含系统欢迎语）'' AFTER `first_user_msg_time`',
    'SELECT ''first_reply_time already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
