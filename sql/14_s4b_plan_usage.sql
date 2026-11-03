-- =============================================================
-- 智客通 S4-B 多租户商业化基座：用量快照表（可重复执行）
-- 对应 docs/产品推进路线图.md PL-03 套餐与功能开关、PL-04 用量计量与超额拦截
--
-- 说明：
--   1. 实时用量一律按业务表 COUNT 口径计算（见 TenantQuotaService），
--      本表只保存「每日聚合快照」，用于趋势展示与对账，不参与拦截判断；
--   2. 快照由 TenantUsageTask 每日 00:10 聚合写入，也可由平台端手动触发；
--   3. 新增表从建表起即带 tenant_id，符合 AGENTS.md 4.1.2。
-- =============================================================

USE `zhiketong`;

CREATE TABLE IF NOT EXISTS `tenant_usage` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    BIGINT       NOT NULL COMMENT '租户 ID',
    `period`       VARCHAR(7)   NOT NULL COMMENT '统计周期，格式 yyyy-MM',
    `seat_used`    INT          NOT NULL DEFAULT 0 COMMENT '已启用坐席账号数',
    `session_used` INT          NOT NULL DEFAULT 0 COMMENT '周期内创建会话数',
    `order_used`   INT          NOT NULL DEFAULT 0 COMMENT '周期内创建工单数',
    `kb_used`      INT          NOT NULL DEFAULT 0 COMMENT '知识库条目数',
    `ai_used`      INT          NOT NULL DEFAULT 0 COMMENT '周期内 AI 应答调用次数',
    `snapshot_time` DATETIME    NOT NULL COMMENT '快照生成时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_period` (`tenant_id`, `period`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period` (`period`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户用量快照（按自然月）';

-- -------------------------------------------------------------
-- 初始化全部租户的当月快照（已存在则跳过）
-- -------------------------------------------------------------
INSERT INTO `tenant_usage`
(`tenant_id`, `period`, `seat_used`, `session_used`, `order_used`, `kb_used`, `ai_used`, `snapshot_time`)
SELECT t.`id`,
       DATE_FORMAT(NOW(), '%Y-%m'),
       (SELECT COUNT(*) FROM `sys_user` u
         WHERE u.`tenant_id` = t.`id` AND u.`role_code` = 'AGENT' AND u.`status` = 1 AND u.`deleted` = 0),
       (SELECT COUNT(*) FROM `chat_session` s
         WHERE s.`tenant_id` = t.`id` AND s.`deleted` = 0
           AND s.`create_time` >= DATE_FORMAT(NOW(), '%Y-%m-01')),
       (SELECT COUNT(*) FROM `work_order` o
         WHERE o.`tenant_id` = t.`id` AND o.`deleted` = 0
           AND o.`create_time` >= DATE_FORMAT(NOW(), '%Y-%m-01')),
       (SELECT COUNT(*) FROM `kb_entry` k
         WHERE k.`tenant_id` = t.`id` AND k.`deleted` = 0),
       (SELECT COUNT(*) FROM `ai_call_log` a
         WHERE a.`tenant_id` = t.`id` AND a.`create_time` >= DATE_FORMAT(NOW(), '%Y-%m-01')),
       NOW()
FROM `tenant` t
WHERE t.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `tenant_usage` x
                   WHERE x.`tenant_id` = t.`id` AND x.`period` = DATE_FORMAT(NOW(), '%Y-%m'));

-- -------------------------------------------------------------
-- 自检
-- -------------------------------------------------------------
SELECT `tenant_id`, `period`, `seat_used`, `session_used`, `order_used`, `kb_used`, `ai_used`
FROM `tenant_usage` ORDER BY `tenant_id`, `period` DESC;
