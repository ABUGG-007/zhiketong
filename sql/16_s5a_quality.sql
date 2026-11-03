-- =============================================================
-- 16_s5a_quality.sql  S5-A 智能质检（文本会话）
-- 依据 docs/产品推进路线图.md 1.2.5：
--   QC-01 全量自动质检（文本会话）
--   QC-02 语义类规则（答非所问、未解决、重复提问）
--   QC-03 情感类规则（客户负面、坐席情绪）
--   QC-04 服务规范类规则（未问候、未确认、超时未回、禁语）
--   QC-05 敏感词命中
-- 变更内容：
--   1) quality_rule    质检规则配置（SEMANTIC / SENTIMENT / SERVICE / SENSITIVE 四类统一建模）
--   2) quality_record  质检记录（一会话一条，重评覆盖；含评分卡结果与复核字段）
--   3) quality_item    质检逐规则命中明细（规则快照 + 证据 + 建议，只追加）
-- 说明：
--   - 新增表从建表起即带 tenant_id，由多租户拦截器自动注入条件
--   - 可重复执行：建表用 IF NOT EXISTS，预置数据按 (tenant_id, rule_code) 判重
--   - 默认规则按现有全部租户各预置一套（11 条），管理员可改扣分/停用/删除
--   - 质检对象为已关闭（CLOSED）会话；QC-06 人工复核、QC-07 对比报表在 S5-B 表结构已预留字段
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. 质检规则配置表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_rule` (
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
-- 2. 质检记录表（一个会话一条，重评覆盖写；复核结论 S5-B 使用）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_record` (
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
    KEY `idx_grade` (`tenant_id`, `grade`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '质检记录表';

-- -------------------------------------------------------------
-- 3. 质检明细表（逐规则一行，含命中证据；重评时旧行逻辑删除后重建）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID，1 为平台默认',
    `record_id`    BIGINT       NOT NULL COMMENT '质检记录 ID',
    `session_id`   BIGINT       NOT NULL COMMENT '会话 ID，便于按会话回溯',
    `rule_id`      BIGINT                DEFAULT NULL COMMENT '规则 ID，规则删除后保留快照',
    `rule_code`    VARCHAR(32)  NOT NULL COMMENT '规则编码快照',
    `rule_name`    VARCHAR(64)  NOT NULL COMMENT '规则名称快照',
    `rule_type`    VARCHAR(16)  NOT NULL COMMENT '规则类型快照',
    `severity`     VARCHAR(16)  NOT NULL COMMENT '严重程度快照',
    `hit`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否命中：1命中 0未命中',
    `deduct_score` INT          NOT NULL DEFAULT 0 COMMENT '实际扣分，未命中为 0',
    `evidence`     VARCHAR(500)          DEFAULT NULL COMMENT '命中证据（引用会话原文或轮次描述）',
    `suggestion`   VARCHAR(255)          DEFAULT NULL COMMENT '改进建议',
    `source`       VARCHAR(8)   NOT NULL DEFAULT 'RULE' COMMENT '判定来源：RULE 规则匹配 / AI 大模型评估',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_record` (`record_id`, `id`),
    KEY `idx_tenant_rule` (`tenant_id`, `rule_code`, `create_time`),
    KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '质检明细表';

-- -------------------------------------------------------------
-- 4. 预置默认规则（按现有租户各一套，已存在则跳过）
-- -------------------------------------------------------------
INSERT INTO `quality_rule` (`tenant_id`, `rule_code`, `rule_name`, `rule_type`, `severity`,
                            `deduct_score`, `keywords`, `match_mode`, `threshold_value`, `description`, `sort`)
SELECT t.id, d.rule_code, d.rule_name, d.rule_type, d.severity,
       d.deduct_score, d.keywords, d.match_mode, d.threshold_value, d.description, d.sort
FROM `tenant` t
         JOIN (SELECT 'GREETING_MISSING'          AS rule_code,
                      '未使用问候语'                  AS rule_name,
                      'SERVICE'                    AS rule_type,
                      'LOW'                        AS severity,
                      5                            AS deduct_score,
                      '你好,您好,哈喽,hi,hello,在的,很高兴为您服务' AS keywords,
                      'ABSENT'                     AS match_mode,
                      0                            AS threshold_value,
                      '坐席首次回复未出现问候用语即命中；无人工坐席参与的会话不适用' AS description,
                      10                           AS sort
               UNION ALL
               SELECT 'DEMAND_CONFIRM_MISSING', '未确认客户诉求', 'SERVICE', 'MEDIUM', 5,
                      '请问,确认,核实,我了解一下,您是说,您的意思', 'ABSENT', 0,
                      '坐席全程未复述或确认客户诉求即命中；无人工坐席参与的会话不适用', 20
               UNION ALL
               SELECT 'CLOSING_MISSING', '未使用结束语', 'SERVICE', 'LOW', 3,
                      '还有问题,其他问题,祝您,再见,感谢咨询,再会', 'ABSENT', 0,
                      '坐席最后一条消息未出现结束语即命中；无人工坐席参与的会话不适用', 30
               UNION ALL
               SELECT 'SLOW_REPLY', '回复超时', 'SERVICE', 'HIGH', 10,
                      NULL, 'ABSENT', 3,
                      '任一轮访客消息后，首条人工或智能回复间隔超过阈值分钟数即命中', 40
               UNION ALL
               SELECT 'BANNED_WORD', '坐席使用禁语', 'SERVICE', 'HIGH', 15,
                      '不知道,不清楚,没办法,不归我管,你自己看,爱莫能助', 'CONTAINS', 0,
                      '坐席消息出现禁语即命中', 50
               UNION ALL
               SELECT 'CUSTOMER_PROFANITY', '客户辱骂或过激用语', 'SENSITIVE', 'LOW', 0,
                      '垃圾,骗子,恶心,滚,傻,投诉你', 'CONTAINS', 0,
                      '会话出现客户过激用语，仅提示不扣坐席分，用于风险预警', 60
               UNION ALL
               SELECT 'AGENT_OVER_PROMISE', '坐席违规承诺', 'SENSITIVE', 'HIGH', 15,
                      '保证,绝对,一定可以,百分百,肯定没问题,包退', 'CONTAINS', 0,
                      '会话出现绝对化承诺用语即命中，合规风险项', 70
               UNION ALL
               SELECT 'IRRELEVANT_ANSWER', '答非所问', 'SEMANTIC', 'HIGH', 15,
                      NULL, 'CONTAINS', 0,
                      '回复与客户问题无关或答非所疑，由大模型判定；未启用大模型时跳过', 80
               UNION ALL
               SELECT 'UNRESOLVED_ISSUE', '问题未解决', 'SEMANTIC', 'HIGH', 10,
                      NULL, 'CONTAINS', 0,
                      '会话结束时客户诉求仍未闭环且无后续安排，由大模型判定；未启用大模型时跳过', 90
               UNION ALL
               SELECT 'REPEAT_QUESTION', '客户重复提问', 'SEMANTIC', 'MEDIUM', 5,
                      NULL, 'CONTAINS', 0,
                      '客户因未得到有效答复而反复提问同一问题，由大模型判定；未启用大模型时跳过', 100
               UNION ALL
               SELECT 'CUSTOMER_NEGATIVE', '客户负面情绪', 'SENTIMENT', 'MEDIUM', 5,
                      NULL, 'CONTAINS', 0,
                      '客户出现明显不满、焦躁或失望情绪，由大模型判定；未启用大模型时跳过', 110) d
WHERE t.deleted = 0
  AND NOT EXISTS (SELECT 1
                  FROM `quality_rule` r
                  WHERE r.tenant_id = t.id
                    AND r.rule_code = d.rule_code
                    AND r.deleted = 0);

-- -------------------------------------------------------------
-- 5. 自检
-- -------------------------------------------------------------
SELECT COUNT(*) AS quality_rule_cnt FROM `quality_rule` WHERE deleted = 0;
SELECT COUNT(*) AS quality_record_cnt FROM `quality_record` WHERE deleted = 0;
SELECT COUNT(*) AS quality_item_cnt FROM `quality_item` WHERE deleted = 0;
SELECT TABLE_NAME, TABLE_COMMENT
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('quality_rule', 'quality_record', 'quality_item');
