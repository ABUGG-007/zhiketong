-- =============================================================
-- 09_s2c_assist_customer.sql  S2-C 坐席辅助增强（客户信息卡 / 输入联想 / 内部备注 / 排队机器人）
-- 依据 docs/产品推进路线图.md 1.2.3（AS-01 / AS-02 / AS-06 / AS-07）与 RB-15：
--   1) 内部备注复用既有 chat_message 表，sender_type 新增 INTERNAL_NOTE
--      —— 不新增表，不新增索引（沿用 idx_session_id）；备注不写入 chat_session.message_count，
--         不进入访客侧消息分页，也不进入大模型上下文
--   2) 客户信息卡（AS-06）与输入联想（AS-01/AS-02）为实时聚合与匹配，不落库
--   3) 排队中机器人接待（RB-15）为会话编排行为增强，无表结构变更
-- 可重复执行：仅同步字段注释，不改变类型与约束
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. chat_message.sender_type 枚举补充 INTERNAL_NOTE（内部备注，仅坐席可见）
-- -------------------------------------------------------------
ALTER TABLE `chat_message`
    MODIFY COLUMN `sender_type` VARCHAR(16) NOT NULL
        COMMENT '发送方类型：USER/AI/AGENT/SYSTEM/INTERNAL_NOTE（内部备注仅坐席可见，不对客推送）';

-- -------------------------------------------------------------
-- 2. 自检
-- -------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message' AND COLUMN_NAME = 'sender_type';

SELECT COUNT(*) AS internal_note_cnt FROM `chat_message` WHERE sender_type = 'INTERNAL_NOTE';
