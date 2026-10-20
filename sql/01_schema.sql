-- =============================================================
-- 智客通 —— 智能对话客服与工单管理系统
-- 数据库初始化脚本 01：建库 + 建表
-- 环境：MySQL 8.0（最低兼容 5.7；字符集 utf8mb4）
-- 说明：教学项目不建物理外键，由应用层（Service 事务）保证一致性
-- 执行方式：
--   mysql -uroot -p < sql/01_schema.sql
-- 注意：脚本会删除同名表后重建，仅用于开发/演示环境
-- =============================================================

CREATE DATABASE IF NOT EXISTS `zhiketong`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `zhiketong`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `ai_call_log`;
DROP TABLE IF EXISTS `agent_status`;
DROP TABLE IF EXISTS `work_order_log`;
DROP TABLE IF EXISTS `work_order`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `kb_entry`;
DROP TABLE IF EXISTS `kb_category`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_role`;

-- -------------------------------------------------------------
-- 1. 角色表
-- -------------------------------------------------------------
CREATE TABLE `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   VARCHAR(32)  NOT NULL COMMENT '角色编码：ADMIN/AGENT/USER',
    `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255)          DEFAULT NULL COMMENT '角色说明',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统角色表';

-- -------------------------------------------------------------
-- 2. 用户表（管理员 / 坐席 / 普通用户共用）
-- -------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密）',
    `nickname`        VARCHAR(50)  NOT NULL COMMENT '昵称/姓名',
    `role_code`       VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色编码：ADMIN/AGENT/USER',
    `phone`           VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `email`           VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `last_login_time` DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`   VARCHAR(64)           DEFAULT NULL COMMENT '最后登录 IP',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role_code` (`role_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表';

-- -------------------------------------------------------------
-- 3. 知识库分类表
-- -------------------------------------------------------------
CREATE TABLE `kb_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  NOT NULL COMMENT '分类名称',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类 ID，0 为顶级',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `description` VARCHAR(255)          DEFAULT NULL COMMENT '分类说明',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_sort` (`sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库分类表';

-- -------------------------------------------------------------
-- 4. 知识库条目表
-- -------------------------------------------------------------
CREATE TABLE `kb_entry` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id` BIGINT       NOT NULL COMMENT '所属分类 ID',
    `question`    VARCHAR(255) NOT NULL COMMENT '标准问题',
    `answer`      TEXT         NOT NULL COMMENT '标准答案',
    `keywords`    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '关键词，英文逗号分隔',
    `hit_count`   INT          NOT NULL DEFAULT 0 COMMENT '累计命中次数',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`   BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`   BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_hit_count` (`hit_count`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库条目表';

-- -------------------------------------------------------------
-- 5. 会话表
-- -------------------------------------------------------------
CREATE TABLE `chat_session` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_no`       VARCHAR(32) NOT NULL COMMENT '会话编号：CS+yyyyMMddHHmmss+4位随机',
    `user_id`          BIGINT      NOT NULL COMMENT '用户 ID',
    `agent_id`         BIGINT               DEFAULT NULL COMMENT '接入坐席 ID',
    `status`           VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '状态：AI/WAITING/HUMAN/CLOSED',
    `close_reason`     VARCHAR(32)          DEFAULT NULL COMMENT '关闭原因：USER_CLOSED/AGENT_CLOSED/TIMEOUT/SYSTEM',
    `rating`           TINYINT              DEFAULT NULL COMMENT '满意度评分 1-5',
    `rating_comment`   VARCHAR(255)         DEFAULT NULL COMMENT '评价内容',
    `queue_enter_time` DATETIME             DEFAULT NULL COMMENT '进入排队时间',
    `transfer_time`    DATETIME             DEFAULT NULL COMMENT '请求转人工时间',
    `accept_time`      DATETIME             DEFAULT NULL COMMENT '坐席接入时间',
    `end_time`         DATETIME             DEFAULT NULL COMMENT '会话结束时间',
    `message_count`    INT         NOT NULL DEFAULT 0 COMMENT '消息条数',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_no` (`session_no`),
    KEY `idx_user_id` (`user_id`, `create_time`),
    KEY `idx_agent_id` (`agent_id`, `status`),
    KEY `idx_status_queue` (`status`, `queue_enter_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客服会话表';

-- -------------------------------------------------------------
-- 6. 聊天消息表
-- -------------------------------------------------------------
CREATE TABLE `chat_message` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`    BIGINT       NOT NULL COMMENT '会话 ID',
    `sender_type`   VARCHAR(16)  NOT NULL COMMENT '发送方类型：USER/AI/AGENT/SYSTEM',
    `sender_id`     BIGINT                DEFAULT NULL COMMENT '发送人 ID（AI/SYSTEM 为空）',
    `content`       TEXT         NOT NULL COMMENT '消息内容',
    `content_type`  VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT '内容类型：TEXT',
    `matched_kb_id` BIGINT                DEFAULT NULL COMMENT '命中的知识库条目 ID',
    `ai_confidence` DECIMAL(5, 4)         DEFAULT NULL COMMENT 'AI 置信度 0.0000-1.0000',
    `ai_model`      VARCHAR(64)           DEFAULT NULL COMMENT 'AI 模型名称',
    `read_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '已读标记：0未读 1已读',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`, `id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '聊天消息表';

-- -------------------------------------------------------------
-- 7. 工单表
-- -------------------------------------------------------------
CREATE TABLE `work_order` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`            VARCHAR(32)  NOT NULL COMMENT '工单号：WO+yyyyMMddHHmmss+4位随机',
    `title`               VARCHAR(120) NOT NULL COMMENT '工单标题',
    `content`             TEXT         NOT NULL COMMENT '问题描述',
    `category`            VARCHAR(32)  NOT NULL DEFAULT '其他' COMMENT '问题分类：技术问题/账号问题/订单问题/售后问题/其他',
    `priority`            VARCHAR(10)  NOT NULL DEFAULT 'P2' COMMENT '优先级：P0/P1/P2/P3',
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ASSIGNED/PROCESSING/RESOLVED/CLOSED/REOPENED',
    `source`              VARCHAR(20)  NOT NULL DEFAULT 'CHAT' COMMENT '来源：CHAT/MANUAL/PHONE',
    `session_id`          BIGINT                DEFAULT NULL COMMENT '关联会话 ID',
    `creator_id`          BIGINT       NOT NULL COMMENT '创建人 ID',
    `assignee_id`         BIGINT                DEFAULT NULL COMMENT '处理人（坐席）ID',
    `sla_deadline`        DATETIME              DEFAULT NULL COMMENT 'SLA 解决截止时间',
    `first_response_time` DATETIME              DEFAULT NULL COMMENT '首次响应时间',
    `resolve_time`        DATETIME              DEFAULT NULL COMMENT '解决时间',
    `close_time`          DATETIME              DEFAULT NULL COMMENT '关闭时间',
    `rating`              TINYINT               DEFAULT NULL COMMENT '满意度 1-5',
    `overdue_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否超时：0否 1是',
    `remark`              VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_assignee` (`assignee_id`, `status`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_sla` (`sla_deadline`, `overdue_flag`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单表';

-- -------------------------------------------------------------
-- 8. 工单流转日志表
-- -------------------------------------------------------------
CREATE TABLE `work_order_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT       NOT NULL COMMENT '工单 ID',
    `action`        VARCHAR(32)  NOT NULL COMMENT '动作：CREATE/ASSIGN/ACCEPT/PROCESS/RESOLVE/CLOSE/REOPEN/COMMENT/OVERDUE',
    `from_status`   VARCHAR(20)           DEFAULT NULL COMMENT '原状态',
    `to_status`     VARCHAR(20)           DEFAULT NULL COMMENT '新状态',
    `operator_id`   BIGINT                DEFAULT NULL COMMENT '操作人 ID',
    `operator_name` VARCHAR(50)           DEFAULT NULL COMMENT '操作人名称（快照）',
    `remark`        VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`, `id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单流转日志表';

-- -------------------------------------------------------------
-- 9. 坐席状态表（在线状态另存 Redis，此表用于持久化与统计）
-- -------------------------------------------------------------
CREATE TABLE `agent_status` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`       BIGINT      NOT NULL COMMENT '坐席用户 ID',
    `online_status`  VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '在线状态：ONLINE/BUSY/OFFLINE',
    `max_concurrent` INT         NOT NULL DEFAULT 5 COMMENT '最大同时接待数',
    `current_count`  INT         NOT NULL DEFAULT 0 COMMENT '当前接待数',
    `total_served`   INT         NOT NULL DEFAULT 0 COMMENT '累计接待数',
    `last_heartbeat` DATETIME             DEFAULT NULL COMMENT '最后心跳时间',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_id` (`agent_id`),
    KEY `idx_online_status` (`online_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '坐席状态表';

-- -------------------------------------------------------------
-- 10. AI 调用日志表
-- -------------------------------------------------------------
CREATE TABLE `ai_call_log` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`        BIGINT               DEFAULT NULL COMMENT '会话 ID',
    `message_id`        BIGINT               DEFAULT NULL COMMENT '对应消息 ID',
    `model`             VARCHAR(64)          DEFAULT NULL COMMENT '模型名称',
    `prompt_tokens`     INT         NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    `completion_tokens` INT         NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    `latency_ms`        INT         NOT NULL DEFAULT 0 COMMENT '调用耗时（毫秒）',
    `success`           TINYINT     NOT NULL DEFAULT 1 COMMENT '是否成功：1是 0否',
    `error_msg`         VARCHAR(500)         DEFAULT NULL COMMENT '失败原因',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 调用日志表';

-- -------------------------------------------------------------
-- 11. 登录日志表
-- -------------------------------------------------------------
CREATE TABLE `sys_login_log` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT               DEFAULT NULL COMMENT '用户 ID',
    `username`    VARCHAR(50)          DEFAULT NULL COMMENT '登录账号',
    `ip`          VARCHAR(64)          DEFAULT NULL COMMENT '登录 IP',
    `user_agent`  VARCHAR(255)         DEFAULT NULL COMMENT '浏览器 UA',
    `success`     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否成功：1是 0否',
    `message`     VARCHAR(255)         DEFAULT NULL COMMENT '结果说明',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '登录日志表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 建表完成：11 张表
--   sys_role, sys_user, kb_category, kb_entry,
--   chat_session, chat_message, work_order, work_order_log,
--   agent_status, ai_call_log, sys_login_log
-- 下一步：执行 sql/02_data.sql 初始化演示数据
-- =============================================================
