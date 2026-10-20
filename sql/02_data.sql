-- =============================================================
-- 智客通 —— 初始化数据（最小集）
-- 说明：只创建 3 个角色 + 3 个可登录账号 + 1 条坐席状态；
--       不写入任何知识库、会话、消息、工单、日志等业务数据，
--       业务数据由后续真实功能（M1～M6）产生。
-- 完整演示数据位于 sql/demo/02_demo_data.sql，默认不导入。
-- =============================================================

USE `zhiketong`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `sys_login_log`;
TRUNCATE TABLE `ai_call_log`;
TRUNCATE TABLE `agent_status`;
TRUNCATE TABLE `work_order_log`;
TRUNCATE TABLE `work_order`;
TRUNCATE TABLE `chat_message`;
TRUNCATE TABLE `chat_session`;
TRUNCATE TABLE `kb_entry`;
TRUNCATE TABLE `kb_category`;
TRUNCATE TABLE `sys_user`;
TRUNCATE TABLE `sys_role`;

-- 角色字典
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort`, `status`) VALUES
(1, 'ADMIN', '管理员', '系统管理员，拥有全部权限', 1, 1),
(2, 'AGENT', '客服坐席', '接收排队会话、处理工单', 2, 1),
(3, 'USER',  '普通用户', '发起咨询、提交工单', 3, 1);

-- 初始化账号：密码均为 123456（BCrypt 存储）
-- 生产/正式环境请首次登录后立即修改密码
INSERT INTO `sys_user`
(`id`, `username`, `password`, `nickname`, `role_code`, `phone`, `email`, `status`) VALUES
(1, 'admin',   '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '系统管理员', 'ADMIN', NULL, NULL, 1),
(2, 'agent01', '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '客服01',   'AGENT', NULL, NULL, 1),
(3, 'user01',  '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '用户01',   'USER',  NULL, NULL, 1);

-- 坐席初始为离线，需通过坐席工作台（M1/M4）真实上线
INSERT INTO `agent_status`
(`agent_id`, `online_status`, `max_concurrent`, `current_count`, `total_served`, `last_heartbeat`) VALUES
(2, 'OFFLINE', 5, 0, 0, NULL);

SET FOREIGN_KEY_CHECKS = 1;
