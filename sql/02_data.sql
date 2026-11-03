-- =============================================================
-- 智客通 —— 初始化数据（最小集）
-- 说明：只创建套餐、默认租户、4 个角色 + 4 个可登录账号 + 1 条坐席状态 + 1 条网页接入配置
--       + 1 条 AI 模型模板 + 4 条 SLA 默认策略；
--       不写入任何知识库、会话、消息、工单、日志等业务数据，
--       业务数据由后续真实功能（M1～M6）产生。
-- 完整演示数据位于 sql/demo/02_demo_data.sql，默认不导入。
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `ai_model_config`;
TRUNCATE TABLE `tenant_embed_config`;
TRUNCATE TABLE `sys_login_log`;
TRUNCATE TABLE `ai_call_log`;
TRUNCATE TABLE `agent_status`;
TRUNCATE TABLE `work_order_log`;
TRUNCATE TABLE `work_order`;
TRUNCATE TABLE `order_sla_pause`;
TRUNCATE TABLE `order_sla_record`;
TRUNCATE TABLE `order_sla_policy`;
TRUNCATE TABLE `chat_message`;
TRUNCATE TABLE `chat_session`;
TRUNCATE TABLE `kb_entry`;
TRUNCATE TABLE `kb_category`;
TRUNCATE TABLE `sys_user`;
TRUNCATE TABLE `sys_role`;
TRUNCATE TABLE `tenant`;
TRUNCATE TABLE `tenant_plan`;
TRUNCATE TABLE `tenant_usage`;

-- 角色字典
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort`, `status`) VALUES
(1, 'PLATFORM_ADMIN', '平台管理员', '管理所有租户、套餐、订单与平台配置', 0, 1),
(2, 'ADMIN', '管理员', '租户管理员，管理本租户知识库、工单、客服账号与数据统计', 1, 1),
(3, 'AGENT', '客服坐席', '接收排队会话、处理工单', 2, 1),
(4, 'USER',  '普通用户', '发起咨询、提交工单', 3, 1);

-- 套餐权益（与 docs/产品推进路线图.md 的套餐口径一致；-1 表示不限量）
INSERT INTO `tenant_plan`
(`plan_code`, `plan_name`, `tagline`, `price`, `period`, `seat_limit`, `session_limit`,
 `order_limit`, `kb_limit`, `ai_limit`, `features`, `recommended`, `sort`, `status`) VALUES
('trial', '试用版', '14 天完整体验，适合验证 AI 首答与转人工闭环', 0.00, '14 天', 2, 500,
 100, 50, 300,
 'AI 知识库首答|一键转人工（带完整上下文）|基础工单与 SLA 提醒|满意度评价', 0, 1, 1),
('standard', '标准版', '中小客服团队日常使用，覆盖咨询到工单的完整闭环', 399.00, '月', 5, 3000,
 1000, 300, 2000,
 '试用版全部能力|坐席排队与负载均衡|工单状态机与流转日志|知识库命中排行与未命中分析|AI 自主解决率与 SLA 看板', 1, 2, 1),
('professional', '专业版', '多班次、高并发团队，强调履约与数据沉淀', 899.00, '月', 20, 20000,
 -1, 2000, 10000,
 '标准版全部能力|20 坐席并发接待|工单数量不限|大模型调用明细与失败告警|优先技术支持', 0, 3, 1);

-- 默认租户：id=1，承接全部存量业务数据
INSERT INTO `tenant`
(`id`, `tenant_code`, `tenant_name`, `status`, `plan_code`, `expire_time`, `remark`) VALUES
(1, 'demo', '智客通演示租户', 'ENABLED', 'standard', NULL, '初始化默认租户，承接历史存量数据');

-- 初始化当月用量快照：真实用量由每日 TenantUsageTask 聚合刷新，超额拦截不依赖本表
INSERT INTO `tenant_usage`
(`tenant_id`, `period`, `seat_used`, `session_used`, `order_used`, `kb_used`, `ai_used`, `snapshot_time`) VALUES
(1, DATE_FORMAT(NOW(), '%Y-%m'), 1, 0, 0, 0, 0, NOW());

-- 初始化账号：密码均为 123456（BCrypt 存储）
-- 生产/正式环境请首次登录后立即修改密码；平台管理员 tenant_id=0 表示跨租户视角
INSERT INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `role_code`, `phone`, `email`, `status`) VALUES
(1, 0, 'platform01', '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '平台管理员', 'PLATFORM_ADMIN', NULL, NULL, 1),
(2, 1, 'admin',   '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '系统管理员', 'ADMIN', NULL, NULL, 1),
(3, 1, 'agent01', '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '客服01',   'AGENT', NULL, NULL, 1),
(4, 1, 'user01',  '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '用户01',   'USER',  NULL, NULL, 1);

-- 坐席初始为离线，需通过坐席工作台（M1/M4）真实上线
INSERT INTO `agent_status`
(`tenant_id`, `agent_id`, `online_status`, `max_concurrent`, `current_count`, `total_served`, `last_heartbeat`) VALUES
(1, 3, 'OFFLINE', 5, 0, 0, NULL);

-- 网页接入默认租户配置
-- app_secret 仅用于服务端 HMAC 签名，禁止写入前端页面；正式环境请在控制台重置密钥
INSERT INTO `tenant_embed_config`
(`tenant_id`, `tenant_code`, `tenant_name`, `app_key`, `app_secret`, `brand_name`, `welcome`,
 `theme_color`, `position`, `badge`, `allowed_origins`, `rate_limit_per_minute`, `status`) VALUES
(1, 'demo', '智客通演示租户', 'zk_demo_5f2a1c9e', '8f4c2d7e1a6b9c3f5e0d4a7b2c8f1e6d',
 '智客通客服', '你好，我是智客通智能助手。发票、退款、物流等常见问题可以直接问我。',
 '#4f46e5', 'right', 1, NULL, 30, 1);

-- AI 大模型初始模板：未填 Key、未启用，填入 Key 后在「系统 - 大模型配置」启用即可
INSERT INTO `ai_model_config`
(`tenant_id`, `name`, `provider`, `base_url`, `api_key`, `model`,
 `temperature`, `max_tokens`, `timeout_ms`, `enabled`, `is_default`, `remark`) VALUES
(1, 'DeepSeek 官方', 'deepseek', 'https://api.deepseek.com/v1', '', 'deepseek-chat',
 0.70, 800, 15000, 0, 0, '初始化模板：填入 API Key 后启用并可设为默认');

-- SLA 策略默认配置：P0-P3 四级时效（与 application.yml 的 zhiketong.sla 口径一致，并补回复间隔时限）
-- 新建工单按优先级匹配 default_flag=1 的启用策略；无匹配时回退配置文件兜底时限
INSERT INTO `order_sla_policy`
(`tenant_id`, `policy_name`, `priority`, `first_response_minutes`, `reply_interval_minutes`,
 `resolve_minutes`, `warn_percent`, `escalate_minutes`, `escalate_role`, `default_flag`,
 `description`, `sort`, `status`) VALUES
(1, 'P0 紧急时效', 'P0', 30, 30, 120, 80, 0, 'ADMIN', 1, '紧急工单：30 分钟首响、每 30 分钟回复一次、2 小时内解决，超时立即升级', 1, 1),
(1, 'P1 高优时效', 'P1', 120, 120, 480, 80, 30, 'ADMIN', 1, '高优工单：2 小时首响、每 2 小时回复一次、8 小时内解决，超时 30 分钟后升级', 2, 1),
(1, 'P2 标准时效', 'P2', 480, 480, 1440, 80, 60, 'ADMIN', 1, '标准工单：8 小时首响、每 8 小时回复一次、24 小时内解决，超时 1 小时后升级', 3, 1),
(1, 'P3 低优时效', 'P3', 1440, 1440, 4320, 80, 240, 'ADMIN', 1, '低优工单：24 小时首响、每 24 小时回复一次、3 天内解决，超时 4 小时后升级', 4, 1);

SET FOREIGN_KEY_CHECKS = 1;
