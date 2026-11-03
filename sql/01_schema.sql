-- =============================================================
-- 智客通 —— 智能对话客服与工单管理系统
-- 数据库初始化脚本 01：建库 + 建表
-- 环境：MySQL 8.0（最低兼容 5.7；字符集 utf8mb4）
-- 说明：本产品不建物理外键，由应用层（Service 事务）保证一致性
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

DROP TABLE IF EXISTS `voc_topic`;
DROP TABLE IF EXISTS `screen_layout`;
DROP TABLE IF EXISTS `alert_event`;
DROP TABLE IF EXISTS `alert_rule`;
DROP TABLE IF EXISTS `quality_item`;
DROP TABLE IF EXISTS `quality_record`;
DROP TABLE IF EXISTS `quality_rule`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `tenant`;
DROP TABLE IF EXISTS `tenant_plan`;
DROP TABLE IF EXISTS `tenant_usage`;
DROP TABLE IF EXISTS `session_summary`;
DROP TABLE IF EXISTS `quick_reply`;
DROP TABLE IF EXISTS `sys_user_skill`;
DROP TABLE IF EXISTS `skill_group`;
DROP TABLE IF EXISTS `kb_import_task`;
DROP TABLE IF EXISTS `kb_entry_version`;
DROP TABLE IF EXISTS `kb_unmatched_question`;
DROP TABLE IF EXISTS `kb_similar_question`;
DROP TABLE IF EXISTS `ai_model_config`;
DROP TABLE IF EXISTS `tenant_embed_config`;
DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `ai_call_log`;
DROP TABLE IF EXISTS `agent_status`;
DROP TABLE IF EXISTS `order_sla_pause`;
DROP TABLE IF EXISTS `order_sla_record`;
DROP TABLE IF EXISTS `order_sla_policy`;
DROP TABLE IF EXISTS `order_field_value`;
DROP TABLE IF EXISTS `order_template`;
DROP TABLE IF EXISTS `order_field`;
DROP TABLE IF EXISTS `work_order_log`;
DROP TABLE IF EXISTS `work_order`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `kb_entry`;
DROP TABLE IF EXISTS `kb_category`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_role`;

-- -------------------------------------------------------------
-- 0-1. 租户主数据表（tenant.id 即业务表 tenant_id 取值）
-- -------------------------------------------------------------
CREATE TABLE `tenant` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，即业务表 tenant_id 取值',
    `tenant_code`   VARCHAR(32)  NOT NULL COMMENT '租户编码，嵌入接入等场景的对外标识',
    `tenant_name`   VARCHAR(64)  NOT NULL COMMENT '租户名称（企业名）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 正常 / DISABLED 已停用 / EXPIRED 已到期',
    `plan_code`     VARCHAR(32)  NOT NULL DEFAULT 'standard' COMMENT '套餐编码，关联 tenant_plan.plan_code',
    `expire_time`   DATETIME              DEFAULT NULL COMMENT '服务到期时间，NULL 表示不限（试用版按开通时间计算）',
    `contact_name`  VARCHAR(50)           DEFAULT NULL COMMENT '联系人姓名',
    `contact_phone` VARCHAR(20)           DEFAULT NULL COMMENT '联系人手机号',
    `contact_email` VARCHAR(100)          DEFAULT NULL COMMENT '联系人邮箱',
    `remark`        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`, `deleted`),
    KEY `idx_status_expire` (`status`, `expire_time`),
    KEY `idx_plan_code` (`plan_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户主数据表';

-- -------------------------------------------------------------
-- 0-2. 套餐权益字典表（平台级系统表，不参与租户隔离）
-- -------------------------------------------------------------
CREATE TABLE `tenant_plan` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_code`       VARCHAR(32)   NOT NULL COMMENT '套餐编码：trial / standard / professional',
    `plan_name`       VARCHAR(64)   NOT NULL COMMENT '套餐名称',
    `tagline`         VARCHAR(255)           DEFAULT NULL COMMENT '一句话定位',
    `price`           DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '价格（元），0 表示免费',
    `period`          VARCHAR(16)   NOT NULL DEFAULT '月' COMMENT '计费周期：月 / 年 / 14 天',
    `seat_limit`      INT           NOT NULL DEFAULT 1 COMMENT '坐席账号上限，-1 表示不限',
    `session_limit`   INT           NOT NULL DEFAULT 0 COMMENT '每自然月会话上限，-1 表示不限',
    `order_limit`     INT           NOT NULL DEFAULT 0 COMMENT '每自然月工单上限，-1 表示不限',
    `kb_limit`        INT           NOT NULL DEFAULT 0 COMMENT '知识库条目上限，-1 表示不限',
    `ai_limit`        INT           NOT NULL DEFAULT 0 COMMENT '每自然月 AI 应答次数上限，-1 表示不限',
    `features`        VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '功能点清单，英文竖线 | 分隔',
    `recommended`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否推荐套餐：1是 0否',
    `sort`            INT           NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '套餐权益字典表';

-- -------------------------------------------------------------
-- 套餐用量快照表（S4-B，PL-04）：每日聚合写入，不参与实时拦截判断
-- -------------------------------------------------------------
CREATE TABLE `tenant_usage` (
    `id`            BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT     NOT NULL COMMENT '租户 ID',
    `period`        VARCHAR(7) NOT NULL COMMENT '统计周期，格式 yyyy-MM',
    `seat_used`     INT        NOT NULL DEFAULT 0 COMMENT '已启用坐席账号数',
    `session_used`  INT        NOT NULL DEFAULT 0 COMMENT '周期内创建会话数',
    `order_used`    INT        NOT NULL DEFAULT 0 COMMENT '周期内创建工单数',
    `kb_used`       INT        NOT NULL DEFAULT 0 COMMENT '知识库条目数',
    `ai_used`       INT        NOT NULL DEFAULT 0 COMMENT '周期内 AI 应答调用次数',
    `snapshot_time` DATETIME   NOT NULL COMMENT '快照生成时间',
    `create_time`   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_period` (`tenant_id`, `period`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period` (`period`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户用量快照（按自然月）';

-- -------------------------------------------------------------
-- 0-4. 通用操作审计表（S4-C，PL-06）：只追加，不设 update_time / deleted
-- -------------------------------------------------------------
CREATE TABLE `audit_log` (
    `tenant_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `module`         VARCHAR(32)  NOT NULL COMMENT '业务模块：TENANT/USER/KB/EMBED/AI/ORDER_RULE',
    `action`         VARCHAR(24)  NOT NULL COMMENT '动作：CREATE/UPDATE/DELETE/ENABLE/DISABLE/CHANGE_PLAN/RENEW/RESET_PASSWORD/ROTATE_SECRET',
    `target_type`    VARCHAR(32)  NOT NULL COMMENT '对象类型：TENANT/SYS_USER/KB_ENTRY/TENANT_EMBED_CONFIG/AI_MODEL_CONFIG/ORDER_RULE',
    `target_id`      BIGINT                DEFAULT NULL COMMENT '对象 ID',
    `target_name`    VARCHAR(128)          DEFAULT NULL COMMENT '对象名称快照（租户名/账号/词条问题等）',
    `detail`         VARCHAR(1000)         DEFAULT NULL COMMENT '变更摘要，可直接展示的中文描述',
    `before_data`    TEXT                  DEFAULT NULL COMMENT '变更前快照（JSON，敏感字段已脱敏）',
    `after_data`     TEXT                  DEFAULT NULL COMMENT '变更后快照（JSON，敏感字段已脱敏）',
    `operator_id`    BIGINT                DEFAULT NULL COMMENT '操作人 ID',
    `operator_name`  VARCHAR(50)           DEFAULT NULL COMMENT '操作人名称快照',
    `operator_role`  VARCHAR(32)           DEFAULT NULL COMMENT '操作人角色编码',
    `ip`             VARCHAR(64)           DEFAULT NULL COMMENT '操作来源 IP',
    `user_agent`     VARCHAR(255)          DEFAULT NULL COMMENT '浏览器 UA',
    `request_method` VARCHAR(8)            DEFAULT NULL COMMENT 'HTTP 方法',
    `request_uri`    VARCHAR(255)          DEFAULT NULL COMMENT '请求路径',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_time` (`tenant_id`, `create_time`),
    KEY `idx_module_action` (`tenant_id`, `module`, `action`),
    KEY `idx_operator` (`tenant_id`, `operator_id`, `create_time`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通用操作审计日志（只追加）';

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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `deleted`),
    KEY `idx_role_code` (`role_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表';

-- -------------------------------------------------------------
-- 3. 知识库分类表
-- -------------------------------------------------------------
CREATE TABLE `kb_category` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id` BIGINT       NOT NULL COMMENT '所属分类 ID',
    `question`    VARCHAR(255) NOT NULL COMMENT '标准问题',
    `answer`      TEXT         NOT NULL COMMENT '标准答案',
    `keywords`    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '关键词，英文逗号分隔',
    `tags`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '标签，英文逗号分隔',
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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_no`       VARCHAR(32) NOT NULL COMMENT '会话编号：CS+yyyyMMddHHmmss+4位随机',
    `user_id`          BIGINT      NOT NULL COMMENT '用户 ID',
    `agent_id`         BIGINT               DEFAULT NULL COMMENT '接入坐席 ID',
    `skill_group_id`   BIGINT               DEFAULT NULL COMMENT '期望技能组 ID（转人工时确定，空表示不限）',
    `status`           VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '状态：AI/WAITING/HUMAN/CLOSED',
    `close_reason`     VARCHAR(32)          DEFAULT NULL COMMENT '关闭原因：USER_CLOSED/AGENT_CLOSED/TIMEOUT/SYSTEM',
    `rating`           TINYINT              DEFAULT NULL COMMENT '满意度评分 1-5',
    `rating_comment`   VARCHAR(255)         DEFAULT NULL COMMENT '评价内容',
    `first_user_msg_time` DATETIME          DEFAULT NULL COMMENT '访客首条有效消息时间（首响计时起点）',
    `first_reply_time` DATETIME             DEFAULT NULL COMMENT '首次有效回复时间（AI/坐席，不含系统欢迎语）',
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
    KEY `idx_skill_group` (`skill_group_id`, `status`),
    KEY `idx_status_queue` (`status`, `queue_enter_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客服会话表';

-- -------------------------------------------------------------
-- 6. 聊天消息表
-- -------------------------------------------------------------
CREATE TABLE `chat_message` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`    BIGINT       NOT NULL COMMENT '会话 ID',
    `sender_type`   VARCHAR(16)  NOT NULL COMMENT '发送方类型：USER/AI/AGENT/SYSTEM/INTERNAL_NOTE（内部备注仅坐席可见）',
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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`            VARCHAR(32)  NOT NULL COMMENT '工单号：WO+yyyyMMddHHmmss+4位随机',
    `title`               VARCHAR(120) NOT NULL COMMENT '工单标题',
    `content`             TEXT         NOT NULL COMMENT '问题描述',
    `category`            VARCHAR(32)  NOT NULL DEFAULT '其他' COMMENT '问题分类：技术问题/账号问题/订单问题/售后问题/其他',
    `priority`            VARCHAR(10)  NOT NULL DEFAULT 'P2' COMMENT '优先级：P0/P1/P2/P3',
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ASSIGNED/PROCESSING/WAITING_CUSTOMER/RESOLVED/CLOSED/REOPENED',
    `source`              VARCHAR(20)  NOT NULL DEFAULT 'CHAT' COMMENT '来源：CHAT/MANUAL/PHONE',
    `template_id`         BIGINT                DEFAULT NULL COMMENT '来源工单模板 ID，为空表示未使用模板',
    `sla_id`              BIGINT                DEFAULT NULL COMMENT 'SLA 计时记录 ID，指向 order_sla_record.id',
    `session_id`          BIGINT                DEFAULT NULL COMMENT '关联会话 ID',
    `creator_id`          BIGINT       NOT NULL COMMENT '创建人 ID',
    `assignee_id`         BIGINT                DEFAULT NULL COMMENT '处理人（坐席）ID',
    `sla_deadline`        DATETIME              DEFAULT NULL COMMENT 'SLA 解决截止时间',
    `first_response_time` DATETIME              DEFAULT NULL COMMENT '首次响应时间',
    `resolve_time`        DATETIME              DEFAULT NULL COMMENT '解决时间',
    `close_time`          DATETIME              DEFAULT NULL COMMENT '关闭时间',
    `rating`              TINYINT               DEFAULT NULL COMMENT '满意度 1-5',
    `overdue_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否超时：0否 1是',
    `sla_status`          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'SLA 状态：NORMAL 正常 / WARN 预警 / OVERDUE 超时 / PAUSED 停表中 / FINISHED 已完结',
    `pause_minutes`       INT          NOT NULL DEFAULT 0 COMMENT '累计停表分钟数，解决时限按此时长顺延',
    `reply_due_time`      DATETIME              DEFAULT NULL COMMENT '下次回复截止时间（回复间隔 SLA），为空表示未启用该级时限',
    `remark`              VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_assignee` (`assignee_id`, `status`),
    KEY `idx_template` (`template_id`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_sla` (`sla_deadline`, `overdue_flag`),
    KEY `idx_sla_status` (`sla_status`, `overdue_flag`),
    KEY `idx_reply_due` (`reply_due_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单表';

-- -------------------------------------------------------------
-- 8. 工单流转日志表
-- -------------------------------------------------------------
CREATE TABLE `work_order_log` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT       NOT NULL COMMENT '工单 ID',
    `action`        VARCHAR(32)  NOT NULL COMMENT '动作：CREATE/ASSIGN/ACCEPT/PROCESS/PAUSE/RESUME/RESOLVE/CLOSE/REOPEN/COMMENT/OVERDUE/ESCALATE',
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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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

-- -------------------------------------------------------------
-- 12. 网页接入配置表（按租户保存悬浮球配置与签名密钥）
-- -------------------------------------------------------------
CREATE TABLE `tenant_embed_config` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_code`           VARCHAR(32)  NOT NULL COMMENT '租户接入标识',
    `tenant_name`           VARCHAR(64)  NOT NULL COMMENT '租户名称（控制台展示）',
    `app_key`               VARCHAR(40)  NOT NULL COMMENT '接入 Key（公开标识，可出现在商家页面）',
    `app_secret`            VARCHAR(64)  NOT NULL COMMENT '签名密钥（仅服务端保存，禁止下发浏览器）',
    `brand_name`            VARCHAR(64)  NOT NULL DEFAULT '智客通客服' COMMENT '客服名称',
    `welcome`               VARCHAR(255)          DEFAULT NULL COMMENT '欢迎语',
    `theme_color`           CHAR(7)      NOT NULL DEFAULT '#4f46e5' COMMENT '主题色 #RRGGBB',
    `position`              VARCHAR(8)   NOT NULL DEFAULT 'right' COMMENT '悬浮位置：right/left',
    `badge`                 TINYINT      NOT NULL DEFAULT 1 COMMENT '未读红点：1显示 0隐藏',
    `allowed_origins`       VARCHAR(500)          DEFAULT NULL COMMENT '允许的嵌入来源，英文逗号分隔；为空不限制',
    `rate_limit_per_minute` INT          NOT NULL DEFAULT 30 COMMENT '单 IP 每分钟提问上限',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '接入状态：1启用 0停用',
    `secret_rotate_time`    DATETIME              DEFAULT NULL COMMENT '密钥最近轮换时间',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`),
    UNIQUE KEY `uk_app_key` (`app_key`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '网页接入配置表';

-- -------------------------------------------------------------
-- 13. AI 大模型配置表（管理端可视化接入各大平台与中转站）
-- -------------------------------------------------------------
CREATE TABLE `ai_model_config` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`            BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `name`                 VARCHAR(64)  NOT NULL COMMENT '配置名称，如 主力-DeepSeek',
    `provider`             VARCHAR(32)  NOT NULL DEFAULT 'custom' COMMENT '平台标识：deepseek/openai/qwen/zhipu/moonshot/volcengine/qianfan/hunyuan/minimax/stepfun/lingyiwanwu/baichuan/siliconflow/openrouter/relay/ollama/custom',
    `base_url`             VARCHAR(255) NOT NULL COMMENT 'OpenAI 兼容接口地址（不含 /chat/completions）',
    `api_key`              VARCHAR(255) NOT NULL COMMENT 'API Key（仅服务端保存，接口返回脱敏）',
    `model`                VARCHAR(128) NOT NULL COMMENT '模型名称，如 deepseek-chat',
    `temperature`          DECIMAL(3, 2) NOT NULL DEFAULT 0.70 COMMENT '采样温度 0.00-2.00',
    `max_tokens`           INT          NOT NULL DEFAULT 800 COMMENT '单次最大输出 token',
    `timeout_ms`           INT          NOT NULL DEFAULT 15000 COMMENT '请求超时（毫秒）',
    `extra_headers`        VARCHAR(500)          DEFAULT NULL COMMENT '附加请求头 JSON，兼容需额外鉴权头的中转站',
    `enabled`              TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态：1启用 0停用',
    `is_default`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模型：1是 0否',
    `last_test_time`       DATETIME              DEFAULT NULL COMMENT '最近连通性测试时间',
    `last_test_ok`         TINYINT               DEFAULT NULL COMMENT '最近测试结果：1成功 0失败',
    `last_test_latency_ms` INT                   DEFAULT NULL COMMENT '最近测试耗时（毫秒）',
    `last_test_message`    VARCHAR(255)          DEFAULT NULL COMMENT '最近测试结果说明',
    `remark`               VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_enabled` (`tenant_id`, `enabled`, `deleted`),
    KEY `idx_tenant_default` (`tenant_id`, `is_default`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 大模型配置表';

-- -------------------------------------------------------------
-- 14. 知识库相似问法表（S1）
-- -------------------------------------------------------------
CREATE TABLE `kb_similar_question` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
-- 15. 知识库未命中问题表（S1，按归一化问句聚合）
-- -------------------------------------------------------------
CREATE TABLE `kb_unmatched_question` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
    `topic_id`            BIGINT                DEFAULT NULL COMMENT '归属的客户之声主题 ID（S6-C），NULL 表示尚未聚类',
    `cluster_score`       INT                   DEFAULT NULL COMMENT '归属相似度百分比 0-100，人工创建主题下为 NULL',
    `first_ask_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现时间',
    `last_ask_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近出现时间',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_normalized_question` (`normalized_question`),
    KEY `idx_status_ask` (`status`, `ask_count`),
    KEY `idx_last_ask_time` (`last_ask_time`),
    KEY `idx_tenant_topic` (`tenant_id`, `topic_id`, `ask_count`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库未命中问题表';

-- -------------------------------------------------------------
-- 16. 知识库词条版本表（S1，只追加不改写）
-- -------------------------------------------------------------
CREATE TABLE `kb_entry_version` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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
-- 17. 知识包导入批次表（S1）
-- -------------------------------------------------------------
CREATE TABLE `kb_import_task` (
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认租户',
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

-- -------------------------------------------------------------
-- 18. 技能组表（S2）
-- -------------------------------------------------------------
CREATE TABLE `skill_group` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `name`        VARCHAR(64)  NOT NULL COMMENT '技能组名称，如 订单售后',
    `description` VARCHAR(255)          DEFAULT NULL COMMENT '组职责说明，转人工时为访客展示',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '技能组表';

-- -------------------------------------------------------------
-- 19. 坐席技能组关系表（S2）
-- -------------------------------------------------------------
CREATE TABLE `sys_user_skill` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      BIGINT   NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `user_id`        BIGINT   NOT NULL COMMENT '坐席用户 ID',
    `skill_group_id` BIGINT   NOT NULL COMMENT '技能组 ID',
    `level`          TINYINT  NOT NULL DEFAULT 3 COMMENT '能力值 1-5，5 为最强',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_group` (`user_id`, `skill_group_id`),
    KEY `idx_group_user` (`skill_group_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '坐席技能组关系表';

-- -------------------------------------------------------------
-- 20. 快捷回复话术表（S2）
-- -------------------------------------------------------------
CREATE TABLE `quick_reply` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `scope`       VARCHAR(16)   NOT NULL DEFAULT 'PERSONAL' COMMENT '范围：PUBLIC 公共话术 / PERSONAL 个人话术',
    `owner_id`    BIGINT                 DEFAULT NULL COMMENT '所有人 ID：PERSONAL 必填，PUBLIC 为空',
    `category`    VARCHAR(32)   NOT NULL DEFAULT '通用' COMMENT '话术分类',
    `title`       VARCHAR(64)   NOT NULL COMMENT '话术标题',
    `content`     VARCHAR(1000) NOT NULL COMMENT '话术正文',
    `sort`        INT           NOT NULL DEFAULT 0 COMMENT '排序号',
    `use_count`   INT           NOT NULL DEFAULT 0 COMMENT '累计使用次数',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`   BIGINT                 DEFAULT NULL COMMENT '创建人 ID',
    `update_by`   BIGINT                 DEFAULT NULL COMMENT '更新人 ID',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_scope_owner` (`scope`, `owner_id`, `status`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '快捷回复话术表';

-- -------------------------------------------------------------
-- 21. 会话摘要与服务小结表（S2-B，等价于 sql/08_s2b_agent_assist.sql）
-- -------------------------------------------------------------
CREATE TABLE `session_summary` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `session_id`       BIGINT        NOT NULL COMMENT '会话 ID',
    `summary_type`     VARCHAR(16)   NOT NULL COMMENT '类型：BRIEF 进线摘要 / SUMMARY 服务小结',
    `customer_demand`  VARCHAR(500)           DEFAULT NULL COMMENT '客户诉求',
    `service_process`  VARCHAR(1000)          DEFAULT NULL COMMENT '服务过程',
    `result`           VARCHAR(500)           DEFAULT NULL COMMENT '处理结果',
    `follow_up`        VARCHAR(500)           DEFAULT NULL COMMENT '待办事项',
    `keywords`         VARCHAR(255)           DEFAULT NULL COMMENT '关键词，英文逗号分隔',
    `raw_content`      TEXT                   DEFAULT NULL COMMENT '大模型原始输出，便于排查与二次整理',
    `model`            VARCHAR(64)            DEFAULT NULL COMMENT '生成所用模型；RULE 来源时为空',
    `source`           VARCHAR(16)   NOT NULL DEFAULT 'AI' COMMENT '来源：AI 大模型 / RULE 规则兜底 / AGENT 坐席编写',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / ADOPTED 已采纳归档',
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
-- 22. 工单自定义字段定义表（S3-A）
-- -------------------------------------------------------------
CREATE TABLE `order_field` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `field_code`    VARCHAR(32)  NOT NULL COMMENT '字段编码（唯一）',
    `field_name`    VARCHAR(64)  NOT NULL COMMENT '字段名称（表单标签）',
    `field_type`    VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT '字段类型：TEXT/MULTILINE/SELECT/NUMBER/DATE',
    `options`       VARCHAR(500)          DEFAULT NULL COMMENT '下拉选项，英文逗号分隔',
    `required`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填：1是 0否',
    `default_value` VARCHAR(255)          DEFAULT NULL COMMENT '默认值',
    `placeholder`   VARCHAR(100)          DEFAULT NULL COMMENT '输入提示',
    `description`   VARCHAR(255)          DEFAULT NULL COMMENT '字段说明',
    `sort`          INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`     BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`     BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `field_code`, `deleted`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单自定义字段定义表';

-- -------------------------------------------------------------
-- 23. 工单模板表（S3-A）
-- -------------------------------------------------------------
CREATE TABLE `order_template` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `template_name`    VARCHAR(64)  NOT NULL COMMENT '模板名称',
    `category`         VARCHAR(32)           DEFAULT NULL COMMENT '默认问题分类',
    `default_priority` VARCHAR(10)           DEFAULT NULL COMMENT '默认优先级 P0-P3',
    `skill_group_id`   BIGINT                DEFAULT NULL COMMENT '默认技能组 ID',
    `field_ids`        VARCHAR(500)          DEFAULT NULL COMMENT '绑定字段 ID，英文逗号分隔',
    `description`      VARCHAR(255)          DEFAULT NULL COMMENT '模板说明',
    `sort`             INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_by`        BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`        BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `template_name`, `deleted`),
    KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单模板表';

-- -------------------------------------------------------------
-- 24. 工单自定义字段值表（S3-A）
-- -------------------------------------------------------------
CREATE TABLE `order_field_value` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `order_id`    BIGINT        NOT NULL COMMENT '工单 ID',
    `field_id`    BIGINT        NOT NULL COMMENT '字段定义 ID',
    `field_code`  VARCHAR(32)   NOT NULL COMMENT '字段编码快照',
    `field_name`  VARCHAR(64)   NOT NULL COMMENT '字段名称快照',
    `field_type`  VARCHAR(16)   NOT NULL COMMENT '字段类型快照',
    `field_value` VARCHAR(2000)          DEFAULT NULL COMMENT '字段值',
    `sort`        INT           NOT NULL DEFAULT 0 COMMENT '展示顺序快照',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_field` (`order_id`, `field_id`, `deleted`),
    KEY `idx_order_sort` (`order_id`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单自定义字段值表';

-- -------------------------------------------------------------
-- 25. 工单 SLA 策略表（S3-B，等价于 sql/11_s3b_order_sla.sql）
-- -------------------------------------------------------------
CREATE TABLE `order_sla_policy` (
    `id`                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `policy_name`            VARCHAR(64) NOT NULL COMMENT '策略名称，如 P1 标准时效',
    `priority`               VARCHAR(10) NOT NULL COMMENT '适用优先级：P0/P1/P2/P3',
    `first_response_minutes` INT         NOT NULL DEFAULT 30 COMMENT '首次响应时限（分钟），0 表示不启用该级',
    `reply_interval_minutes` INT         NOT NULL DEFAULT 0 COMMENT '每次回复间隔时限（分钟），0 表示不启用该级',
    `resolve_minutes`        INT         NOT NULL DEFAULT 120 COMMENT '解决时限（分钟），0 表示不启用该级',
    `warn_percent`           INT         NOT NULL DEFAULT 80 COMMENT '已消耗时长达到时限的该百分比时预警，取值 1-99',
    `escalate_minutes`       INT         NOT NULL DEFAULT 0 COMMENT '超时后再经过多少分钟仍未解决则升级通知上级，0 表示超时即升级',
    `escalate_role`          VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '升级通知对象角色，当前支持 ADMIN',
    `default_flag`           TINYINT     NOT NULL DEFAULT 0 COMMENT '是否为该优先级的默认策略：1是 0否；同优先级同租户仅允许一条默认',
    `description`            VARCHAR(255)         DEFAULT NULL COMMENT '策略说明',
    `sort`                   INT         NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`                 TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后新建工单不再使用该策略）',
    `create_by`              BIGINT               DEFAULT NULL COMMENT '创建人 ID',
    `update_by`              BIGINT               DEFAULT NULL COMMENT '更新人 ID',
    `create_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_policy` (`tenant_id`, `policy_name`, `deleted`),
    KEY `idx_tenant_priority` (`tenant_id`, `priority`, `status`, `default_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单 SLA 策略表';

-- -------------------------------------------------------------
-- 26. 工单 SLA 计时记录表（S3-B，每工单一条，含时限快照与停表累计）
-- -------------------------------------------------------------
CREATE TABLE `order_sla_record` (
    `id`                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `order_id`               BIGINT      NOT NULL COMMENT '工单 ID',
    `policy_id`              BIGINT               DEFAULT NULL COMMENT '来源策略 ID，为空表示使用配置文件兜底时限',
    `policy_name`            VARCHAR(64)          DEFAULT NULL COMMENT '策略名称快照',
    `priority`               VARCHAR(10) NOT NULL COMMENT '优先级快照，优先级变更时重算时限并更新快照',
    `first_response_minutes` INT         NOT NULL DEFAULT 0 COMMENT '首次响应时限快照（分钟）',
    `reply_interval_minutes` INT         NOT NULL DEFAULT 0 COMMENT '回复间隔时限快照（分钟）',
    `resolve_minutes`        INT         NOT NULL DEFAULT 0 COMMENT '解决时限快照（分钟）',
    `warn_percent`           INT         NOT NULL DEFAULT 80 COMMENT '预警阈值快照（百分比）',
    `escalate_minutes`       INT         NOT NULL DEFAULT 0 COMMENT '升级延迟快照（分钟）',
    `start_time`             DATETIME    NOT NULL COMMENT '计时起点，取工单创建时间；重开后重置为当前时间',
    `first_response_deadline` DATETIME            DEFAULT NULL COMMENT '首次响应截止时间，含停表顺延',
    `first_response_time`    DATETIME             DEFAULT NULL COMMENT '实际首次响应时间，与 work_order.first_response_time 同步',
    `last_reply_time`        DATETIME             DEFAULT NULL COMMENT '最近一次坐席回复时间（添加处理进展）',
    `reply_due_time`         DATETIME             DEFAULT NULL COMMENT '下次回复截止时间，含停表顺延；未启用回复间隔时为空',
    `resolve_deadline`       DATETIME             DEFAULT NULL COMMENT '解决截止时间，含停表顺延；与 work_order.sla_deadline 同步',
    `pause_minutes`          INT         NOT NULL DEFAULT 0 COMMENT '累计停表分钟数',
    `paused`                 TINYINT     NOT NULL DEFAULT 0 COMMENT '当前是否停表中：1是 0否',
    `pause_start`            DATETIME             DEFAULT NULL COMMENT '本次停表开始时间',
    `first_response_warn`    TINYINT     NOT NULL DEFAULT 0 COMMENT '首次响应是否已预警：1是 0否',
    `first_response_overdue` TINYINT     NOT NULL DEFAULT 0 COMMENT '首次响应是否已超时：1是 0否',
    `reply_warn`             TINYINT     NOT NULL DEFAULT 0 COMMENT '回复间隔是否已预警：1是 0否',
    `reply_overdue`          TINYINT     NOT NULL DEFAULT 0 COMMENT '回复间隔是否已超时：1是 0否',
    `resolve_warn`           TINYINT     NOT NULL DEFAULT 0 COMMENT '解决时限是否已预警：1是 0否',
    `resolve_overdue`        TINYINT     NOT NULL DEFAULT 0 COMMENT '解决时限是否已超时：1是 0否',
    `escalated`              TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已升级通知上级：1是 0否',
    `status`                 VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '计时状态：RUNNING 计时中 / PAUSED 停表中 / FINISHED 已完结',
    `sla_status`             VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'SLA 状态：NORMAL/WARN/OVERDUE/PAUSED/FINISHED，与 work_order.sla_status 同步',
    `create_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order` (`order_id`, `deleted`),
    KEY `idx_status_resolve` (`status`, `resolve_deadline`),
    KEY `idx_status_first_response` (`status`, `first_response_deadline`),
    KEY `idx_status_reply` (`status`, `reply_due_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单 SLA 计时记录表';

-- -------------------------------------------------------------
-- 27. 工单 SLA 停表流水表（S3-B，等待客户反馈 / 其他原因）
-- -------------------------------------------------------------
CREATE TABLE `order_sla_pause` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `order_id`         BIGINT       NOT NULL COMMENT '工单 ID',
    `record_id`        BIGINT       NOT NULL COMMENT 'SLA 计时记录 ID',
    `pause_type`       VARCHAR(32)  NOT NULL DEFAULT 'WAITING_CUSTOMER' COMMENT '停表类型：WAITING_CUSTOMER 等待客户反馈 / OTHER 其他原因',
    `reason`           VARCHAR(255)          DEFAULT NULL COMMENT '停表原因',
    `pause_time`       DATETIME     NOT NULL COMMENT '停表开始时间',
    `resume_time`      DATETIME              DEFAULT NULL COMMENT '恢复计时时间，为空表示仍在停表中',
    `duration_minutes` INT                   DEFAULT NULL COMMENT '本次停表时长（分钟），恢复时写入',
    `operator_id`      BIGINT                DEFAULT NULL COMMENT '操作人 ID',
    `operator_name`    VARCHAR(50)           DEFAULT NULL COMMENT '操作人名称（快照）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_order_time` (`order_id`, `pause_time`),
    KEY `idx_record` (`record_id`, `resume_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单 SLA 停表流水表';

-- -------------------------------------------------------------
-- 28. 工单规则与自动化配置表（S3-C，自动分配 / 事件触发器 / 时间自动化）
-- -------------------------------------------------------------
CREATE TABLE `order_rule` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_name`       VARCHAR(64) NOT NULL COMMENT '规则名称，同租户内唯一',
    `rule_type`       VARCHAR(24) NOT NULL COMMENT '规则类型：AUTO_ASSIGN 自动分配 / TRIGGER 事件触发器 / AUTOMATION 时间自动化',
    `trigger_event`   VARCHAR(32)          DEFAULT NULL COMMENT '触发事件：CREATED/ASSIGNED/PROCESSING/RESOLVED/CLOSED/REOPENED/PRIORITY_CHANGED/SLA_WARN/SLA_OVERDUE/SLA_ESCALATE；纯时间自动化可为空',
    `delay_minutes`   INT         NOT NULL DEFAULT 0 COMMENT '事件后延迟执行分钟数，0 表示立即执行，>0 由扫描任务到期执行',
    `match_priority`  VARCHAR(10)          DEFAULT NULL COMMENT '匹配条件：工单优先级 P0-P3，为空表示不限',
    `match_category`  VARCHAR(32)          DEFAULT NULL COMMENT '匹配条件：问题分类，为空表示不限',
    `match_source`    VARCHAR(24)          DEFAULT NULL COMMENT '匹配条件：来源渠道 CHAT/MANUAL/PHONE，为空表示不限',
    `action_type`     VARCHAR(32) NOT NULL COMMENT '动作类型：AUTO_ASSIGN/ASSIGN_AGENT/CHANGE_PRIORITY/CHANGE_STATUS/NOTIFY_ROLE',
    `action_value`    VARCHAR(255)         DEFAULT NULL COMMENT '动作目标值：新优先级 / 新状态 / 通知角色(ADMIN|CREATOR|ASSIGNEE) / 指定坐席 ID',
    `assign_group_id` BIGINT               DEFAULT NULL COMMENT '自动分配取该技能组在岗坐席，为空表示全体在岗',
    `webhook_url`     VARCHAR(255)         DEFAULT NULL COMMENT 'WEBHOOK 回调地址（本期预留，暂不生效）',
    `description`     VARCHAR(255)         DEFAULT NULL COMMENT '规则说明',
    `sort`            INT         NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不再匹配执行）',
    `create_by`       BIGINT               DEFAULT NULL COMMENT '创建人 ID',
    `update_by`       BIGINT               DEFAULT NULL COMMENT '更新人 ID',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `rule_name`, `deleted`),
    KEY `idx_tenant_type_status` (`tenant_id`, `rule_type`, `status`),
    KEY `idx_trigger` (`tenant_id`, `trigger_event`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单规则与自动化配置表';

-- -------------------------------------------------------------
-- 29. 工单规则待执行队列与执行流水表（S3-C）
-- -------------------------------------------------------------
CREATE TABLE `order_rule_run` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT      NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_id`       BIGINT      NOT NULL COMMENT '规则 ID',
    `order_id`      BIGINT      NOT NULL COMMENT '工单 ID',
    `event`         VARCHAR(32)          DEFAULT NULL COMMENT '触发该次执行的事件编码',
    `action_type`   VARCHAR(32) NOT NULL COMMENT '执行的动作类型快照',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '执行状态：PENDING 待执行 / DONE 成功 / FAILED 失败 / SKIPPED 跳过（无可用坐席等）',
    `planned_time`  DATETIME    NOT NULL COMMENT '计划执行时间，延迟/时间驱动为到期时刻，立即执行为当前时刻',
    `executed_time` DATETIME             DEFAULT NULL COMMENT '实际执行时间，执行后写入',
    `result`        VARCHAR(500)         DEFAULT NULL COMMENT '执行结果说明（成功动作描述或失败/跳过原因）',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dedup` (`order_id`, `rule_id`, `planned_time`, `deleted`),
    KEY `idx_order_time` (`order_id`, `create_time`),
    KEY `idx_rule_time` (`rule_id`, `create_time`),
    KEY `idx_status_plan` (`status`, `planned_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单规则待执行队列与执行流水表';

-- -------------------------------------------------------------
-- 30. 质检规则配置表（S5-A，QC-01~QC-05：四类规则统一建模）
-- -------------------------------------------------------------
CREATE TABLE `quality_rule` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_code`       VARCHAR(32)  NOT NULL COMMENT '规则编码，同租户内唯一；内置规则编码固定，供引擎识别特殊算法',
    `rule_name`       VARCHAR(64)  NOT NULL COMMENT '规则名称',
    `rule_type`       VARCHAR(16)  NOT NULL COMMENT '规则类型：SEMANTIC 语义 / SENTIMENT 情感 / SERVICE 服务规范 / SENSITIVE 敏感词',
    `severity`        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '严重程度：LOW 一般 / MEDIUM 中等 / HIGH 严重',
    `deduct_score`    INT          NOT NULL DEFAULT 5 COMMENT '命中扣分 0-30，0 表示只提示不计分',
    `keywords`        VARCHAR(500)          DEFAULT NULL COMMENT '关键词表，英文逗号分隔；SERVICE/SENSITIVE 规则必填',
    `match_mode`      VARCHAR(16)  NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配方式：CONTAINS 出现即命中 / ABSENT 未出现即命中',
    `threshold_value` INT          NOT NULL DEFAULT 0 COMMENT '阈值：SLOW_REPLY 为允许的最大回复间隔分钟数，其余规则为 0',
    `description`     VARCHAR(255)          DEFAULT NULL COMMENT '规则说明与判定口径',
    `sort`            INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不参与质检）',
    `create_by`       BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`       BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `rule_code`, `deleted`),
    KEY `idx_tenant_type_status` (`tenant_id`, `rule_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '质检规则配置表';

-- -------------------------------------------------------------
-- 31. 质检记录表（S5-A + S5-B 复核列）：一个会话一条，重评覆盖写
-- -------------------------------------------------------------
CREATE TABLE `quality_record` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `session_id`         BIGINT       NOT NULL COMMENT '会话 ID',
    `session_no`         VARCHAR(32)           DEFAULT NULL COMMENT '会话编号快照',
    `customer_id`        BIGINT                DEFAULT NULL COMMENT '访客用户 ID 快照',
    `agent_id`           BIGINT                DEFAULT NULL COMMENT '接待坐席 ID 快照，空表示纯机器人会话',
    `agent_name`         VARCHAR(50)           DEFAULT NULL COMMENT '接待坐席名称快照',
    `session_end_time`   DATETIME              DEFAULT NULL COMMENT '会话结束时间快照',
    `message_count`      INT          NOT NULL DEFAULT 0 COMMENT '参与质检的消息条数',
    `customer_msg_count` INT          NOT NULL DEFAULT 0 COMMENT '访客消息条数',
    `agent_msg_count`    INT          NOT NULL DEFAULT 0 COMMENT '坐席消息条数',
    `ai_msg_count`       INT          NOT NULL DEFAULT 0 COMMENT '智能客服消息条数',
    `score`              INT          NOT NULL DEFAULT 100 COMMENT '质检得分 0-100（100 减去命中扣分）',
    `grade`              VARCHAR(4)   NOT NULL DEFAULT 'A' COMMENT '等级：A(>=90) / B(>=80) / C(>=70) / D(<70)',
    `pass_score`         INT          NOT NULL DEFAULT 80 COMMENT '本次判定使用的合格线快照',
    `pass_flag`          TINYINT      NOT NULL DEFAULT 1 COMMENT '是否合格：1合格 0不合格',
    `total_deduct`       INT          NOT NULL DEFAULT 0 COMMENT '累计扣分',
    `rule_count`         INT          NOT NULL DEFAULT 0 COMMENT '参与评估的规则数',
    `hit_count`          INT          NOT NULL DEFAULT 0 COMMENT '命中规则数',
    `engine`             VARCHAR(8)   NOT NULL DEFAULT 'RULE' COMMENT '评估引擎：AI 仅大模型 / RULE 仅规则 / MIX 规则+大模型',
    `ai_model`           VARCHAR(64)           DEFAULT NULL COMMENT '语义与情感评估所用模型，规则兜底时为空',
    `ai_summary`         VARCHAR(1000)         DEFAULT NULL COMMENT 'AI 质检结论摘要',
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '复核状态：PENDING 待复核 / REVIEWED 已复核 / CONFIRMED 已确认归档',
    `reviewer_id`        BIGINT                DEFAULT NULL COMMENT '复核人 ID',
    `reviewer_name`      VARCHAR(50)           DEFAULT NULL COMMENT '复核人名称快照',
    `review_comment`     VARCHAR(500)          DEFAULT NULL COMMENT '复核意见',
    `review_score`       INT                   DEFAULT NULL COMMENT '人工复核得分 0-100，为空表示复核未调分',
    `review_time`        DATETIME              DEFAULT NULL COMMENT '复核时间',
    `review_action`      VARCHAR(16)           DEFAULT NULL COMMENT '最近复核动作：REVIEW 提交复核 / CONFIRM 确认归档 / REJECT 驳回退回，空为未复核',
    `evaluate_by`        BIGINT                DEFAULT NULL COMMENT '手动质检发起人 ID，为空表示定时任务自动质检',
    `evaluate_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次质检时间',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session` (`session_id`, `deleted`),
    KEY `idx_tenant_time` (`tenant_id`, `create_time`),
    KEY `idx_agent_time` (`tenant_id`, `agent_id`, `evaluate_time`),
    KEY `idx_status_time` (`tenant_id`, `status`, `evaluate_time`),
    KEY `idx_grade` (`tenant_id`, `grade`),
    KEY `idx_tenant_review_time` (`tenant_id`, `status`, `review_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '质检记录表';

-- -------------------------------------------------------------
-- 32. 质检明细表（S5-A + S5-B 改判列）：逐规则一行，重评时旧行逻辑删除后重建
-- -------------------------------------------------------------
CREATE TABLE `quality_item` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `record_id`     BIGINT       NOT NULL COMMENT '质检记录 ID',
    `session_id`    BIGINT       NOT NULL COMMENT '会话 ID，便于按会话回溯',
    `rule_id`       BIGINT                DEFAULT NULL COMMENT '规则 ID，规则删除后保留快照',
    `rule_code`     VARCHAR(32)  NOT NULL COMMENT '规则编码快照',
    `rule_name`     VARCHAR(64)  NOT NULL COMMENT '规则名称快照',
    `rule_type`     VARCHAR(16)  NOT NULL COMMENT '规则类型快照',
    `severity`      VARCHAR(16)  NOT NULL COMMENT '严重程度快照',
    `hit`           TINYINT      NOT NULL DEFAULT 0 COMMENT '自动判定是否命中：1命中 0未命中',
    `deduct_score`  INT          NOT NULL DEFAULT 0 COMMENT '自动判定实际扣分，未命中为 0',
    `evidence`      VARCHAR(500)          DEFAULT NULL COMMENT '命中证据（引用会话原文或轮次描述）',
    `suggestion`    VARCHAR(255)          DEFAULT NULL COMMENT '改进建议',
    `source`        VARCHAR(8)   NOT NULL DEFAULT 'RULE' COMMENT '判定来源：RULE 规则匹配 / AI 大模型评估',
    `review_hit`    TINYINT               DEFAULT NULL COMMENT '人工改判命中结论：1命中 0未命中，NULL 未改判（沿用 hit）',
    `review_deduct` INT                   DEFAULT NULL COMMENT '人工改判后扣分，未改判为 NULL',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_record` (`record_id`, `id`),
    KEY `idx_tenant_rule` (`tenant_id`, `rule_code`, `create_time`),
    KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '质检明细表';

-- -------------------------------------------------------------
-- 33. 告警规则配置表（S6-B，AS-10 / AS-11）：租户级 + 坐席个人级统一建模
-- -------------------------------------------------------------
CREATE TABLE `alert_rule` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_code`        VARCHAR(48)  NOT NULL COMMENT '规则编码，同租户同归属内唯一；内置规则与指标编码一致',
    `metric_code`      VARCHAR(48)  NOT NULL COMMENT '指标编码，扫描引擎据此选择判定算法',
    `rule_name`        VARCHAR(64)  NOT NULL COMMENT '规则名称',
    `rule_type`        VARCHAR(16)  NOT NULL COMMENT '规则类型：QUEUE 排队 / SESSION 会话 / AGENT 坐席 / ORDER 工单',
    `severity`         VARCHAR(16)  NOT NULL DEFAULT 'WARN' COMMENT '严重程度：INFO 提示 / WARN 警告 / CRITICAL 严重',
    `threshold_value`  INT          NOT NULL DEFAULT 0 COMMENT '触发阈值，单位见 threshold_unit',
    `threshold_unit`   VARCHAR(16)  NOT NULL DEFAULT 'COUNT' COMMENT '阈值单位：COUNT 数量 / SECONDS 秒 / MINUTES 分钟 / SCORE 评分',
    `cooldown_minutes` INT          NOT NULL DEFAULT 10 COMMENT '冷却分钟数，同规则同目标在冷却期内只告警一次',
    `notify_role`      VARCHAR(16)  NOT NULL DEFAULT 'ADMIN' COMMENT '通知对象：ADMIN 管理员 / AGENT 责任坐席 / ALL 两者',
    `owner_id`         BIGINT       NOT NULL DEFAULT 0 COMMENT '规则归属坐席 ID，0 表示租户级规则',
    `keywords`         VARCHAR(500)          DEFAULT NULL COMMENT '敏感词表，英文逗号分隔，仅 SENSITIVE_WORD 使用',
    `description`      VARCHAR(255)          DEFAULT NULL COMMENT '规则说明与判定口径',
    `sort`             INT          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用（停用后不参与扫描）',
    `built_in`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否内置规则：1内置（编码与类型不可改）0自定义',
    `create_by`        BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_by`        BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_owner_code` (`tenant_id`, `owner_id`, `rule_code`, `deleted`),
    KEY `idx_tenant_type_status` (`tenant_id`, `rule_type`, `status`),
    KEY `idx_tenant_owner_status` (`tenant_id`, `owner_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '告警规则配置表';

-- -------------------------------------------------------------
-- 34. 告警事件表（S6-B）：按「规则 + 目标」去重，确认与关闭留痕
-- -------------------------------------------------------------
CREATE TABLE `alert_event` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `rule_id`         BIGINT                DEFAULT NULL COMMENT '规则 ID，规则删除后保留快照',
    `rule_code`       VARCHAR(48)  NOT NULL COMMENT '规则编码快照',
    `metric_code`     VARCHAR(48)  NOT NULL COMMENT '指标编码快照',
    `rule_name`       VARCHAR(64)  NOT NULL COMMENT '规则名称快照',
    `rule_type`       VARCHAR(16)  NOT NULL COMMENT '规则类型快照',
    `severity`        VARCHAR(16)  NOT NULL COMMENT '严重程度快照',
    `target_type`     VARCHAR(16)  NOT NULL COMMENT '告警目标类型：QUEUE 排队 / SESSION 会话 / AGENT 坐席 / ORDER 工单',
    `target_id`       BIGINT                DEFAULT NULL COMMENT '目标 ID：队列与工单为租户 ID，会话为会话 ID，坐席为坐席 ID',
    `session_id`      BIGINT                DEFAULT NULL COMMENT '关联会话 ID，便于直接跳转监听',
    `agent_id`        BIGINT                DEFAULT NULL COMMENT '关联坐席 ID，为空表示无责任坐席',
    `title`           VARCHAR(128) NOT NULL COMMENT '告警标题',
    `detail`          VARCHAR(500)          DEFAULT NULL COMMENT '告警详情，写入触发时的指标快照',
    `metric_value`    BIGINT       NOT NULL DEFAULT 0 COMMENT '触发时指标实际值',
    `threshold_value` INT          NOT NULL DEFAULT 0 COMMENT '触发时阈值快照',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '处理状态：OPEN 待处理 / ACKED 已确认 / CLOSED 已关闭',
    `occur_count`     INT          NOT NULL DEFAULT 1 COMMENT '同一规则同一目标的累计触发次数',
    `first_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次触发时间',
    `last_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近触发时间',
    `ack_by`          BIGINT                DEFAULT NULL COMMENT '确认人 ID',
    `ack_name`        VARCHAR(50)           DEFAULT NULL COMMENT '确认人名称快照',
    `ack_time`        DATETIME              DEFAULT NULL COMMENT '确认时间',
    `ack_note`        VARCHAR(255)          DEFAULT NULL COMMENT '确认说明',
    `close_by`        BIGINT                DEFAULT NULL COMMENT '关闭人 ID，为空表示系统自动关闭',
    `close_time`      DATETIME              DEFAULT NULL COMMENT '关闭时间',
    `handle_seconds`  BIGINT                DEFAULT NULL COMMENT '处理耗时（秒），首次触发到关闭',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status_time` (`tenant_id`, `status`, `last_time`),
    KEY `idx_tenant_rule_target` (`tenant_id`, `rule_code`, `target_type`, `target_id`, `status`),
    KEY `idx_tenant_agent` (`tenant_id`, `agent_id`, `status`),
    KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '告警事件表';

-- -------------------------------------------------------------
-- 35. 客户之声主题表（S6-C，DB-06）：自动聚类与人工创建统一建模
-- -------------------------------------------------------------
CREATE TABLE `voc_topic` (
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
-- 36. 大屏布局配置表（S6-C，DB-07）：组件显隐、顺序与密度
-- -------------------------------------------------------------
CREATE TABLE `screen_layout` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `layout_code`     VARCHAR(32)   NOT NULL DEFAULT 'default' COMMENT '布局编码，default 为租户默认大屏',
    `layout_name`     VARCHAR(64)   NOT NULL DEFAULT '默认大屏' COMMENT '布局名称',
    `widgets`         VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '组件配置 JSON 数组：[{code,visible,order}]',
    `density`         VARCHAR(16)   NOT NULL DEFAULT 'COMFORTABLE' COMMENT '展示密度：COMFORTABLE 舒适 / COMPACT 紧凑',
    `refresh_seconds` INT           NOT NULL DEFAULT 15 COMMENT '轮询刷新间隔（秒），WS 不可用时生效，5-300',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `update_by`       BIGINT                 DEFAULT NULL COMMENT '更新人 ID',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `layout_code`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '大屏布局配置表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 建表完成：40 张表（业务表 36 张 + 平台级表 4 张）
--   平台级：tenant, tenant_plan, tenant_usage, audit_log
--   业务表：sys_role, sys_user, kb_category, kb_entry,
--   kb_similar_question, kb_unmatched_question,
--   kb_entry_version, kb_import_task,
--   chat_session, chat_message, work_order, work_order_log,
--   agent_status, ai_call_log, sys_login_log, tenant_embed_config,
--   ai_model_config, skill_group, sys_user_skill, quick_reply,
--   session_summary, order_field, order_template, order_field_value,
--   order_sla_policy, order_sla_record, order_sla_pause,
--   order_rule, order_rule_run,
--   quality_rule, quality_record, quality_item,
--   alert_rule, alert_event,
--   voc_topic, screen_layout
-- 增量脚本（本文件已含最终态，历史库升级按序执行）：
--   05_s0_metrics.sql / 06_s1_kb_ops.sql / 07_s2a_quick_reply_skill.sql /
--   08_s2b_agent_assist.sql / 09_s2c_assist_customer.sql / 10_s3a_order_form.sql /
--   11_s3b_order_sla.sql / 12_s3c_order_rule.sql / 13_s4a_tenant.sql /
--   14_s4b_plan_usage.sql / 15_s4c_audit_log.sql / 16_s5a_quality.sql /
--   17_s5b_quality_review.sql / 18_s6a_ops_metrics.sql / 19_s6b_alert_monitor.sql /
--   20_s6c_voc_screen.sql
-- 下一步：执行 sql/02_data.sql 初始化演示数据
-- =============================================================
