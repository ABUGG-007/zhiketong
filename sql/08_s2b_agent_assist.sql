-- =============================================================
-- 08_s2b_agent_assist.sql  S2-B 坐席辅助（进线摘要 + 服务小结）
-- 依据 docs/产品推进路线图.md 1.2.3（AS-03 / AS-04 / AS-05 / AS-09）：
--   1) session_summary  会话文本资产：BRIEF 进线摘要（转人工瞬间生成）/ SUMMARY 服务小结（结束前生成）
--   2) 推荐答案（AS-03）为实时计算，不落库，仅依赖既有知识库与 ai_call_log
-- 可重复执行：建表用 IF NOT EXISTS
-- 新增表从建表起即带 tenant_id（当前固定 1，S4 多租户改造时统一迁移）
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 会话摘要与服务小结（同一会话每种类型只保留一行，重复生成走覆盖更新）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `session_summary` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `session_id`       BIGINT        NOT NULL COMMENT '会话 ID',
    `summary_type`     VARCHAR(16)   NOT NULL COMMENT '类型：BRIEF 进线摘要（坐席接起即懂来意）/ SUMMARY 服务小结（结束归档）',
    `customer_demand`  VARCHAR(500)           DEFAULT NULL COMMENT '客户诉求',
    `service_process`  VARCHAR(1000)          DEFAULT NULL COMMENT '服务过程',
    `result`           VARCHAR(500)           DEFAULT NULL COMMENT '处理结果',
    `follow_up`        VARCHAR(500)           DEFAULT NULL COMMENT '待办事项',
    `keywords`         VARCHAR(255)           DEFAULT NULL COMMENT '关键词，英文逗号分隔',
    `raw_content`      TEXT                   DEFAULT NULL COMMENT '大模型原始输出，便于排查与二次整理',
    `model`            VARCHAR(64)            DEFAULT NULL COMMENT '生成所用模型；RULE 来源时为空',
    `source`           VARCHAR(16)   NOT NULL DEFAULT 'AI' COMMENT '来源：AI 大模型 / RULE 规则兜底 / AGENT 坐席编写',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / ADOPTED 已采纳归档（仅服务小结使用）',
    `create_by`        BIGINT                 DEFAULT NULL COMMENT '生成人 / 编写人 ID，异步生成时为空',
    `update_by`        BIGINT                 DEFAULT NULL COMMENT '最后一次修改人 ID',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_type` (`session_id`, `summary_type`, `deleted`),
    KEY `idx_type_status_time` (`summary_type`, `status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会话摘要与服务小结表';

-- -------------------------------------------------------------
-- 2. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS session_summary_cnt FROM `session_summary` WHERE deleted = 0;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'session_summary'
ORDER BY ORDINAL_POSITION;
