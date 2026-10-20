-- =============================================================
-- 智客通 —— 智能对话客服与工单管理系统
-- 数据库初始化脚本 02：演示种子数据
-- 环境：MySQL 8.0（最低兼容 5.7；字符集 utf8mb4）
--
-- 执行方式（先执行 01_schema.sql）：
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/02_data.sql
--
-- 说明：
--   1. 脚本可重复执行：先 TRUNCATE 全部业务表，再插入演示数据；
--   2. 表之间不建物理外键，本脚本通过固定 ID 保证逻辑关联一致；
--   3. 所有时间使用 NOW() / DATE_SUB / INTERVAL 生成，
--      数据分布在最近 7 天内，保证趋势统计与图表始终有数据；
--   4. 全部演示账号的登录密码均为 123456（BCrypt 哈希存储）。
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

-- -------------------------------------------------------------
-- 1. 角色表 sys_role：3 条
-- -------------------------------------------------------------
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort`, `status`) VALUES
(1, 'ADMIN', '管理员', '系统最高权限：用户与坐席管理、知识库维护、工单调度、数据统计', 1, 1),
(2, 'AGENT', '客服坐席', '负责接待会话、处理工单、维护坐席在线状态', 2, 1),
(3, 'USER',  '普通用户', '发起智能咨询、申请转人工、提交并跟踪工单', 3, 1);

-- -------------------------------------------------------------
-- 2. 用户表 sys_user：6 条（密码明文均为 123456）
-- -------------------------------------------------------------
INSERT INTO `sys_user`
(`id`, `username`, `password`, `nickname`, `role_code`, `phone`, `email`, `avatar`, `status`,
 `last_login_time`, `last_login_ip`, `create_time`, `update_time`, `deleted`) VALUES
(1, 'admin',   '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '系统管理员', 'ADMIN', '13800000001', 'admin@zhiketong.com',   NULL, 1, NOW() - INTERVAL 30 MINUTE, '127.0.0.1',    NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 30 MINUTE, 0),
(2, 'agent01', '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '张客服',     'AGENT', '13800000002', 'agent01@zhiketong.com', NULL, 1, NOW() - INTERVAL 25 MINUTE, '192.168.1.11', NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 25 MINUTE, 0),
(3, 'agent02', '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '李客服',     'AGENT', '13800000003', 'agent02@zhiketong.com', NULL, 1, NOW() - INTERVAL 3 DAY,     '192.168.1.12', NOW() - INTERVAL 58 DAY, NOW() - INTERVAL 3 DAY,     0),
(4, 'user01',  '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '王小明',     'USER',  '13900000001', 'user01@example.com',    NULL, 1, NOW() - INTERVAL 1 HOUR,    '10.0.0.21',    NOW() - INTERVAL 45 DAY, NOW() - INTERVAL 1 HOUR,    0),
(5, 'user02',  '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '陈小红',     'USER',  '13900000002', 'user02@example.com',    NULL, 1, NOW() - INTERVAL 2 DAY,     '10.0.0.22',    NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 2 DAY,     0),
(6, 'user03',  '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy', '赵小刚',     'USER',  '13900000003', 'user03@example.com',    NULL, 0, NULL,                       NULL,           NOW() - INTERVAL 35 DAY, NOW() - INTERVAL 35 DAY,    0);

-- -------------------------------------------------------------
-- 3. 坐席状态表 agent_status：2 条（agent01 在线、agent02 离线）
-- -------------------------------------------------------------
INSERT INTO `agent_status`
(`id`, `agent_id`, `online_status`, `max_concurrent`, `current_count`, `total_served`, `last_heartbeat`, `create_time`, `update_time`) VALUES
(1, 2, 'ONLINE',  5, 1, 37, NOW() - INTERVAL 30 SECOND, NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 30 SECOND),
(2, 3, 'OFFLINE', 5, 0, 52, NOW() - INTERVAL 1 DAY,    NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 1 DAY);

-- -------------------------------------------------------------
-- 4. 知识库分类 kb_category：5 条
-- -------------------------------------------------------------
INSERT INTO `kb_category` (`id`, `name`, `parent_id`, `sort`, `status`, `description`, `create_time`, `update_time`, `deleted`) VALUES
(1, '账号与登录', 0, 1, 1, '登录异常、密码找回、账号绑定与解绑等相关问题', NOW() - INTERVAL 55 DAY, NOW() - INTERVAL 55 DAY, 0),
(2, '订单与支付', 0, 2, 1, '下单、支付失败、发票、订单取消相关问题',       NOW() - INTERVAL 54 DAY, NOW() - INTERVAL 54 DAY, 0),
(3, '产品使用',   0, 3, 1, '功能操作指引、导入导出、消息通知相关问题',       NOW() - INTERVAL 53 DAY, NOW() - INTERVAL 53 DAY, 0),
(4, '售后与退款', 0, 4, 1, '退款进度、退换货、售后处理相关问题',             NOW() - INTERVAL 52 DAY, NOW() - INTERVAL 52 DAY, 0),
(5, '其他',       0, 5, 1, '服务时间、联系客服、意见反馈等其他问题',         NOW() - INTERVAL 51 DAY, NOW() - INTERVAL 51 DAY, 0);

-- -------------------------------------------------------------
-- 5. 知识库条目 kb_entry：20 条（覆盖 5 个分类，含热门条目）
-- -------------------------------------------------------------
INSERT INTO `kb_entry`
(`id`, `category_id`, `question`, `answer`, `keywords`, `hit_count`, `status`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`) VALUES
(1, 1, '忘记密码如何找回？',
 '请在登录页点击“忘记密码”，输入注册手机号获取验证码后设置新密码。如果您绑定的手机号已停用，可以联系人工客服核验身份后协助重置。建议重置后尽快在个人中心更新手机号。',
 '密码,忘记密码,找回密码,登录不了,重置密码', 186, 1, 1, 1, NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 6 DAY, 0),
(2, 1, '账号被锁定了怎么办？',
 '连续输错密码 5 次会触发临时锁定，锁定时间为 30 分钟，稍后重试即可。若超过 30 分钟仍提示锁定，请通过“忘记密码”重置密码，或联系人工客服解冻。',
 '账号锁定,被锁定,登录失败,账户冻结', 62, 1, 1, 1, NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 8 DAY, 0),
(3, 1, '如何修改绑定手机号？',
 '进入“个人中心 - 账号安全 - 手机号”点击更换，通过原手机号收取验证码后填写新号码即可。原手机号无法接收验证码时，需联系人工客服完成身份核验。',
 '修改手机号,更换手机号,绑定手机', 31, 1, 1, 1, NOW() - INTERVAL 49 DAY, NOW() - INTERVAL 10 DAY, 0),
(4, 1, '登录提示账号不存在如何处理？',
 '请先确认账号拼写是否正确，本系统账号为注册时设置的登录名而非手机号。若确认无误仍提示不存在，可能是账号已被注销，建议重新注册或联系人工客服查询。',
 '账号不存在,登录不了,注册', 12, 1, 1, 1, NOW() - INTERVAL 48 DAY, NOW() - INTERVAL 12 DAY, 0),
(5, 2, '订单支付失败怎么办？',
 '支付失败常见原因有银行卡余额不足、支付超时、网络中断。请确认余额与网络后重新发起支付。如果已经扣款但订单仍显示待支付，请提供订单号，我们会尽快为您核实。',
 '支付失败,付款失败,支付不了,扣款', 152, 1, 1, 1, NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 5 DAY, 0),
(6, 2, '如何申请发票？',
 '在订单详情页点击“申请发票”，填写发票抬头、税号和接收邮箱后提交。电子普通发票会在 24 小时内发送到您的邮箱，增值税专用发票需要人工审核，一般 1-3 个工作日开具。',
 '发票,开票,电子发票,专票', 84, 1, 1, 1, NOW() - INTERVAL 49 DAY, NOW() - INTERVAL 7 DAY, 0),
(7, 2, '订单可以取消吗？',
 '未付款订单可以随时取消。已付款但未发货的订单可在订单详情页申请取消，审核通过后原路退款。已发货订单无法直接取消，可以拒收或收货后申请售后。',
 '取消订单,撤销订单,不想要了', 47, 1, 1, 1, NOW() - INTERVAL 48 DAY, NOW() - INTERVAL 9 DAY, 0),
(8, 2, '支持哪些支付方式？',
 '目前支持微信支付、支付宝和银联云闪付三种方式，企业用户还可以申请对公转账。对公转账需要 1-2 个工作日到账，到账后订单会自动变为已支付。',
 '支付方式,微信支付,支付宝,对公转账', 21, 1, 1, 1, NOW() - INTERVAL 47 DAY, NOW() - INTERVAL 11 DAY, 0),
(9, 3, '如何修改登录密码？',
 '登录后进入“个人中心 - 账号安全 - 修改密码”，输入原密码并设置新密码即可。新密码需 8-20 位，且同时包含字母和数字，修改后其他已登录设备需要重新登录。',
 '修改密码,改密码,密码规则', 121, 1, 1, 1, NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 4 DAY, 0),
(10, 3, '系统支持批量导入吗？',
 '支持。请进入“数据中心 - 批量导入”，先下载最新 Excel 模板，按模板表头填写数据后上传，单次最多导入 5000 行。日期列请使用 yyyy-MM-dd 格式，导入失败时可下载错误明细。',
 '批量导入,导入,excel,模板', 9, 1, 1, 1, NOW() - INTERVAL 46 DAY, NOW() - INTERVAL 13 DAY, 0),
(11, 3, '消息通知收不到怎么办？',
 '请先检查浏览器是否开启了通知权限，以及系统内“消息设置”是否勾选了对应类型。若使用手机浏览器，还需要确认系统通知权限已开启。仍然收不到可联系人工客服排查。',
 '通知,收不到消息,消息提醒,推送', 38, 1, 1, 1, NOW() - INTERVAL 45 DAY, NOW() - INTERVAL 14 DAY, 0),
(12, 3, '如何导出报表数据？',
 '进入“数据中心 - 报表管理”，选择需要的月份或时间范围，点击右上角“导出 Excel”即可下载。导出范围为当前筛选条件下的全部数据，单个文件最多 10 万行。',
 '导出,报表,下载数据,excel', 16, 1, 1, 1, NOW() - INTERVAL 44 DAY, NOW() - INTERVAL 15 DAY, 0),
(13, 4, '如何申请退款？',
 '进入“我的订单”找到对应订单，点击“申请退款”并选择退款原因，提交后由客服审核。审核通过后款项将原路退回，会员类订单支持开通后 7 天内无理由退款。',
 '退款,申请退款,退钱,无理由退款', 98, 1, 1, 1, NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 3 DAY, 0),
(14, 4, '退款多久能到账？',
 '审核通过后，微信、支付宝一般 1-3 个工作日到账，银行卡 3-7 个工作日到账。如超过时限仍未到账，请提供订单号和退款流水号，我们会联系支付渠道核实。',
 '退款到账,退款时间,什么时候到账', 73, 1, 1, 1, NOW() - INTERVAL 49 DAY, NOW() - INTERVAL 6 DAY, 0),
(15, 4, '商品有质量问题怎么换货？',
 '请在收货后 7 天内于订单详情页申请售后，选择“换货”并上传问题照片。审核通过后按页面提示寄回商品，我们收到后会免费寄出新品。',
 '换货,质量问题,售后,退货', 27, 1, 1, 1, NOW() - INTERVAL 47 DAY, NOW() - INTERVAL 12 DAY, 0),
(16, 4, '退款失败如何处理？',
 '退款失败通常是支付渠道原路退回受限或银行卡状态异常导致。请在“退款详情”查看失败原因，更新收款账户后重新提交，或联系人工客服协助改用其他方式退款。',
 '退款失败,退款不了,退款异常', 6, 1, 1, 1, NOW() - INTERVAL 43 DAY, NOW() - INTERVAL 16 DAY, 0),
(17, 5, '人工客服的服务时间？',
 '人工客服服务时间为每天 9:00-21:00，周末及节假日不休。非服务时间您仍可与智能客服对话，或提交工单，我们会在下一个服务时段优先处理。',
 '人工客服,上班时间,客服电话,服务时间', 57, 1, 1, 1, NOW() - INTERVAL 42 DAY, NOW() - INTERVAL 7 DAY, 0),
(18, 5, '如何联系人工客服？',
 '在与智能客服的会话中发送“转人工”，或在“我的会话”页面点击“转人工客服”，系统会为您排队并分配在线坐席。也可以直接拨打客服电话 400-800-1234。',
 '转人工,人工客服,联系客服', 145, 1, 1, 1, NOW() - INTERVAL 41 DAY, NOW() - INTERVAL 4 DAY, 0),
(19, 5, '如何提交工单？',
 '进入“工单中心 - 新建工单”，选择问题分类和优先级，填写标题与详细描述后提交。工单提交后会生成工单号，您可以在“我的工单”中随时查看处理进度。',
 '提交工单,工单,反馈问题', 33, 1, 1, 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 9 DAY, 0),
(20, 5, '意见与建议反馈渠道？',
 '您可以在会话中直接告诉智能客服，或通过“意见反馈”页面提交建议与投诉。涉及服务态度的投诉会由客服主管在 24 小时内回访处理。',
 '建议,投诉,反馈,意见', 4, 1, 1, 1, NOW() - INTERVAL 39 DAY, NOW() - INTERVAL 17 DAY, 0);

-- -------------------------------------------------------------
-- 6. 会话表 chat_session：10 条
--    AI 3 条、WAITING 2 条、HUMAN 2 条、CLOSED 3 条（带评分）
-- -------------------------------------------------------------
INSERT INTO `chat_session`
(`id`, `session_no`, `user_id`, `agent_id`, `status`, `close_reason`, `rating`, `rating_comment`,
 `queue_enter_time`, `transfer_time`, `accept_time`, `end_time`, `message_count`,
 `create_time`, `update_time`, `deleted`) VALUES
(1,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 6 DAY - INTERVAL 120 MINUTE, '%Y%m%d%H%i%s'), '1001'),
 4, NULL, 'CLOSED', 'USER_CLOSED', 5, '智能客服一次解决，无需转人工',
 NULL, NULL, NULL, NOW() - INTERVAL 6 DAY - INTERVAL 115 MINUTE, 4,
 NOW() - INTERVAL 6 DAY - INTERVAL 120 MINUTE, NOW() - INTERVAL 6 DAY - INTERVAL 115 MINUTE, 0),
(2,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 5 DAY - INTERVAL 60 MINUTE, '%Y%m%d%H%i%s'), '2002'),
 5, NULL, 'AI', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, 4,
 NOW() - INTERVAL 5 DAY - INTERVAL 60 MINUTE, NOW() - INTERVAL 5 DAY - INTERVAL 56 MINUTE, 0),
(3,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 4 DAY - INTERVAL 300 MINUTE, '%Y%m%d%H%i%s'), '3003'),
 4, 2, 'HUMAN', NULL, NULL, NULL,
 NOW() - INTERVAL 4 DAY - INTERVAL 297 MINUTE, NOW() - INTERVAL 4 DAY - INTERVAL 297 MINUTE,
 NOW() - INTERVAL 4 DAY - INTERVAL 294 MINUTE, NULL, 7,
 NOW() - INTERVAL 4 DAY - INTERVAL 300 MINUTE, NOW() - INTERVAL 4 DAY - INTERVAL 291 MINUTE, 0),
(4,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 3 DAY - INTERVAL 240 MINUTE, '%Y%m%d%H%i%s'), '4004'),
 5, 2, 'CLOSED', 'USER_CLOSED', 5, '客服响应很快，退款已到账，问题解决',
 NOW() - INTERVAL 3 DAY - INTERVAL 238 MINUTE, NOW() - INTERVAL 3 DAY - INTERVAL 238 MINUTE,
 NOW() - INTERVAL 3 DAY - INTERVAL 235 MINUTE, NOW() - INTERVAL 3 DAY - INTERVAL 219 MINUTE, 5,
 NOW() - INTERVAL 3 DAY - INTERVAL 240 MINUTE, NOW() - INTERVAL 3 DAY - INTERVAL 219 MINUTE, 0),
(5,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 2 DAY - INTERVAL 420 MINUTE, '%Y%m%d%H%i%s'), '5005'),
 4, 3, 'CLOSED', 'AGENT_CLOSED', 4, '处理专业，发票已收到',
 NOW() - INTERVAL 2 DAY - INTERVAL 418 MINUTE, NOW() - INTERVAL 2 DAY - INTERVAL 418 MINUTE,
 NOW() - INTERVAL 2 DAY - INTERVAL 415 MINUTE, NOW() - INTERVAL 2 DAY - INTERVAL 414 MINUTE, 4,
 NOW() - INTERVAL 2 DAY - INTERVAL 420 MINUTE, NOW() - INTERVAL 2 DAY - INTERVAL 414 MINUTE, 0),
(6,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 1 DAY - INTERVAL 540 MINUTE, '%Y%m%d%H%i%s'), '6006'),
 6, 2, 'CLOSED', 'TIMEOUT', 3, '回复有点慢，问题最终自己解决了',
 NOW() - INTERVAL 1 DAY - INTERVAL 536 MINUTE, NOW() - INTERVAL 1 DAY - INTERVAL 536 MINUTE,
 NOW() - INTERVAL 1 DAY - INTERVAL 532 MINUTE, NOW() - INTERVAL 1 DAY - INTERVAL 480 MINUTE, 6,
 NOW() - INTERVAL 1 DAY - INTERVAL 540 MINUTE, NOW() - INTERVAL 1 DAY - INTERVAL 480 MINUTE, 0),
(7,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 480 MINUTE, '%Y%m%d%H%i%s'), '7007'),
 5, 3, 'HUMAN', NULL, NULL, NULL,
 NOW() - INTERVAL 476 MINUTE, NOW() - INTERVAL 476 MINUTE,
 NOW() - INTERVAL 474 MINUTE, NULL, 5,
 NOW() - INTERVAL 480 MINUTE, NOW() - INTERVAL 472 MINUTE, 0),
(8,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 180 MINUTE, '%Y%m%d%H%i%s'), '8008'),
 4, NULL, 'AI', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, 3,
 NOW() - INTERVAL 180 MINUTE, NOW() - INTERVAL 176 MINUTE, 0),
(9,  CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 8 MINUTE, '%Y%m%d%H%i%s'), '9009'),
 5, NULL, 'WAITING', NULL, NULL, NULL,
 NOW() - INTERVAL 8 MINUTE, NOW() - INTERVAL 8 MINUTE, NULL, NULL, 3,
 NOW() - INTERVAL 8 MINUTE, NOW() - INTERVAL 5 MINUTE, 0),
(10, CONCAT('CS', DATE_FORMAT(NOW() - INTERVAL 5 MINUTE, '%Y%m%d%H%i%s'), '1010'),
 4, NULL, 'WAITING', NULL, NULL, NULL,
 NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 5 MINUTE, NULL, NULL, 3,
 NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 3 MINUTE, 0);

-- -------------------------------------------------------------
-- 7. 聊天消息 chat_message：44 条（每个会话 3-7 条，USER/AI/AGENT/SYSTEM 混合）
-- -------------------------------------------------------------
INSERT INTO `chat_message`
(`id`, `session_id`, `sender_type`, `sender_id`, `content`, `content_type`,
 `matched_kb_id`, `ai_confidence`, `ai_model`, `read_flag`, `create_time`) VALUES
-- 会话 1（AI，6 天前）
(1,  1, 'USER', 4,    '你好，我忘记登录密码了，怎么重置？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 6 DAY - INTERVAL 120 MINUTE),
(2,  1, 'AI',   NULL, '您好，请在登录页点击“忘记密码”，输入注册手机号获取验证码后设置新密码。若手机号已停用，可以联系人工客服核验身份后重置。', 'TEXT', 1, 0.9300, 'deepseek-chat', 1, NOW() - INTERVAL 6 DAY - INTERVAL 119 MINUTE),
(3,  1, 'USER', 4,    '好的，已经重置成功了，谢谢！', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 6 DAY - INTERVAL 118 MINUTE),
(4,  1, 'AI',   NULL, '不客气～如后续还有问题，随时联系我，祝您使用愉快！', 'TEXT', 1, 0.8800, 'deepseek-chat', 1, NOW() - INTERVAL 6 DAY - INTERVAL 117 MINUTE),
-- 会话 2（AI，5 天前）
(5,  2, 'USER', 5,    '我付款的时候一直失败，是什么原因？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 5 DAY - INTERVAL 60 MINUTE),
(6,  2, 'AI',   NULL, '支付失败常见原因有：银行卡余额不足、支付超时或网络中断。建议确认余额后重新发起支付；如果已经扣款但订单仍显示待支付，请提供订单号，我帮您转接人工核实。', 'TEXT', 5, 0.9100, 'deepseek-chat', 1, NOW() - INTERVAL 5 DAY - INTERVAL 59 MINUTE),
(7,  2, 'USER', 5,    '钱已经扣了，订单还是待支付', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 5 DAY - INTERVAL 57 MINUTE),
(8,  2, 'AI',   NULL, '很抱歉给您带来不便。请提供订单号，我为您转接人工客服核实支付记录，我们会在 4 小时内处理完成。', 'TEXT', 5, 0.7600, 'deepseek-chat', 1, NOW() - INTERVAL 5 DAY - INTERVAL 56 MINUTE),
-- 会话 3（HUMAN，4 天前，坐席 agent01）
(9,  3, 'USER',  4, '系统可以在手机上操作吗？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 4 DAY - INTERVAL 300 MINUTE),
(10, 3, 'AI',    NULL, '我们的系统支持浏览器访问，手机端可以直接用手机浏览器登录，暂未提供独立 App。如果您需要移动端的专属功能，我可以帮您转接人工客服。', 'TEXT', NULL, 0.4200, 'deepseek-chat', 1, NOW() - INTERVAL 4 DAY - INTERVAL 299 MINUTE),
(11, 3, 'USER',  4, '那帮我转人工吧，我还有个订单问题', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 4 DAY - INTERVAL 297 MINUTE),
(12, 3, 'SYSTEM', NULL, '用户请求转人工，正在为您排队，请稍候…', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 4 DAY - INTERVAL 296 MINUTE),
(13, 3, 'AGENT', 2, '您好，我是客服张客服，工号 A1002，请问有什么可以帮您？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 4 DAY - INTERVAL 294 MINUTE),
(14, 3, 'USER',  4, '帮我查一下订单 ZK20261010A001 的发货情况', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 4 DAY - INTERVAL 293 MINUTE),
(15, 3, 'AGENT', 2, '已为您查询，该订单昨天已出库，物流单号 SF1234567890，预计 2 天内送达，请您留意短信通知。', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 4 DAY - INTERVAL 291 MINUTE),
-- 会话 4（CLOSED，3 天前，评分 5）
(16, 4, 'USER',  5, '我买的会员服务可以退款吗？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 3 DAY - INTERVAL 240 MINUTE),
(17, 4, 'AI',    NULL, '会员服务支持开通后 7 天内无理由退款。您可以在“我的订单”中选择对应订单点击“申请退款”，审核通过后原路退回。', 'TEXT', 13, 0.8900, 'deepseek-chat', 1, NOW() - INTERVAL 3 DAY - INTERVAL 239 MINUTE),
(18, 4, 'SYSTEM', NULL, '用户请求转人工，正在为您排队，请稍候…', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 3 DAY - INTERVAL 237 MINUTE),
(19, 4, 'AGENT', 2, '您好，已收到您的退款需求，我这边帮您提交申请，预计 1-3 个工作日到账，请留意银行卡通知。', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 3 DAY - INTERVAL 235 MINUTE),
(20, 4, 'USER',  5, '好的，已经到账了，非常感谢！', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 3 DAY - INTERVAL 220 MINUTE),
-- 会话 5（CLOSED，2 天前，评分 4）
(21, 5, 'USER',  4, '怎么开发票？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 2 DAY - INTERVAL 420 MINUTE),
(22, 5, 'AI',    NULL, '您可以在订单详情页点击“申请发票”，填写抬头和税号后提交，电子发票会在 24 小时内发送到您的邮箱。', 'TEXT', 6, 0.9000, 'deepseek-chat', 1, NOW() - INTERVAL 2 DAY - INTERVAL 419 MINUTE),
(23, 5, 'USER',  4, '我需要开公司抬头的增值税专用发票', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 2 DAY - INTERVAL 417 MINUTE),
(24, 5, 'AGENT', 3, '您好，专票需要人工审核，请把公司名称、税号、开户行和账号发给我，我为您登记处理。', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 2 DAY - INTERVAL 415 MINUTE),
-- 会话 6（CLOSED，1 天前，超时关闭，评分 3）
(25, 6, 'USER',  6, '你们的批量导入一直报错，到底怎么回事？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 1 DAY - INTERVAL 540 MINUTE),
(26, 6, 'AI',    NULL, '批量导入报错通常与模板格式有关，请确认已下载最新模板并保持表头不变，日期列请使用 yyyy-MM-dd 格式。', 'TEXT', 10, 0.7000, 'deepseek-chat', 1, NOW() - INTERVAL 1 DAY - INTERVAL 539 MINUTE),
(27, 6, 'USER',  6, '我就是用的最新模板，还是报错', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 1 DAY - INTERVAL 536 MINUTE),
(28, 6, 'SYSTEM', NULL, '用户请求转人工，正在为您排队，请稍候…', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 1 DAY - INTERVAL 535 MINUTE),
(29, 6, 'AGENT', 2, '您好，麻烦把报错截图和导入文件发给我，我帮您排查具体是哪一行数据有问题。', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 1 DAY - INTERVAL 532 MINUTE),
(30, 6, 'SYSTEM', NULL, '会话因长时间无响应已自动结束，如有需要请重新发起咨询。', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 1 DAY - INTERVAL 480 MINUTE),
-- 会话 7（HUMAN，8 小时前，坐席 agent02）
(31, 7, 'USER',  5, '请问人工客服几点下班？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 480 MINUTE),
(32, 7, 'AI',    NULL, '人工客服服务时间为每天 9:00-21:00，节假日不休，您可以直接在会话中请求转人工。', 'TEXT', 17, 0.8600, 'deepseek-chat', 1, NOW() - INTERVAL 479 MINUTE),
(33, 7, 'USER',  5, '那帮我转人工，我想查一下退款进度', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 476 MINUTE),
(34, 7, 'SYSTEM', NULL, '用户请求转人工，客服李客服已接入，请稍候…', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 474 MINUTE),
(35, 7, 'AGENT', 3, '您好，我是客服李客服，请提供一下订单号，我帮您查询退款进度。', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 472 MINUTE),
-- 会话 8（AI，3 小时前）
(36, 8, 'USER',  4, '上个月的报表怎么导出？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 180 MINUTE),
(37, 8, 'AI',    NULL, '进入“数据中心 - 报表管理”，选择月份后点击右上角“导出 Excel”即可，文件会直接下载到本地。', 'TEXT', 12, 0.9200, 'deepseek-chat', 1, NOW() - INTERVAL 179 MINUTE),
(38, 8, 'USER',  4, '导出来的文件是空的，没有数据', 'TEXT', NULL, NULL, NULL, 0, NOW() - INTERVAL 176 MINUTE),
-- 会话 9（WAITING，8 分钟前，排队演示）
(39, 9, 'USER',  5, '我要投诉，你们的服务太差了！', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 8 MINUTE),
(40, 9, 'AI',    NULL, '非常抱歉给您带来不好的体验，我马上为您转接人工客服优先处理，请稍候。', 'TEXT', 20, 0.6500, 'deepseek-chat', 1, NOW() - INTERVAL 7 MINUTE),
(41, 9, 'USER',  5, '快点，我有急事！', 'TEXT', NULL, NULL, NULL, 0, NOW() - INTERVAL 5 MINUTE),
-- 会话 10（WAITING，5 分钟前，排队演示）
(42, 10, 'USER',  4, '我的订单一直显示待发货，能帮我看看吗？', 'TEXT', NULL, NULL, NULL, 1, NOW() - INTERVAL 5 MINUTE),
(43, 10, 'AI',    NULL, '我帮您看了一下，订单正在仓库备货中，通常 24 小时内发出；如果超过 24 小时仍未发货，我可以为您转接人工客服核实。', 'TEXT', NULL, 0.5500, 'deepseek-chat', 1, NOW() - INTERVAL 4 MINUTE),
(44, 10, 'USER',  4, '帮我转人工', 'TEXT', NULL, NULL, NULL, 0, NOW() - INTERVAL 3 MINUTE);

-- 校准 message_count，保证与 chat_message 实际条数一致
UPDATE `chat_session`
SET `message_count` = (
    SELECT COUNT(*) FROM `chat_message` WHERE `chat_message`.`session_id` = `chat_session`.`id`
);

-- -------------------------------------------------------------
-- 8. 工单 work_order：14 条
--    覆盖 6 种状态、P0-P3、5 个分类、3 种来源；4 条超时
-- -------------------------------------------------------------
INSERT INTO `work_order`
(`id`, `order_no`, `title`, `content`, `category`, `priority`, `status`, `source`,
 `session_id`, `creator_id`, `assignee_id`, `sla_deadline`, `first_response_time`,
 `resolve_time`, `close_time`, `rating`, `overdue_flag`, `remark`,
 `create_time`, `update_time`, `deleted`) VALUES
(1,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 2 HOUR, '%Y%m%d%H%i%s'), '1001'),
 '旧账号无法登录，绑定手机号已停用',
 '用户使用旧账号登录时提示“账号不存在”，该账号绑定的手机号已经停用，无法通过短信验证码找回密码，希望人工协助恢复账号。',
 '账号问题', 'P1', 'PENDING', 'MANUAL', NULL, 4, NULL,
 NOW() + INTERVAL 6 HOUR, NULL, NULL, NULL, NULL, 0, '用户反馈旧账号无法登录，需核实绑定手机号',
 NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 95 MINUTE, 0),
(2,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 4 DAY, '%Y%m%d%H%i%s'), '2002'),
 '建议优化首页加载速度',
 '用户来电反馈首页加载超过 5 秒，希望优化静态资源与接口响应速度。已记录用户联系方式，等待排期评估。',
 '其他', 'P3', 'PENDING', 'PHONE', NULL, 5, NULL,
 NOW() - INTERVAL 1 DAY, NULL, NULL, NULL, NULL, 1, '电话建议，已超 SLA 仍未处理',
 NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 1 DAY, 0),
(3,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 3 HOUR, '%Y%m%d%H%i%s'), '3003'),
 '批量导入持续报错，影响数据初始化',
 '用户反馈使用最新模板批量导入仍然报错，导入文件约 2000 行，报错信息为“第 1 行日期格式不正确”，用户情绪焦急。',
 '技术问题', 'P0', 'ASSIGNED', 'CHAT', 6, 6, 2,
 NOW() - INTERVAL 1 HOUR, NULL, NULL, NULL, NULL, 1, '会话转工单，P0 需 30 分钟内首响',
 NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 60 MINUTE, 0),
(4,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 40 HOUR, '%Y%m%d%H%i%s'), '4004'),
 '支付成功但订单状态仍为待支付',
 '用户支付成功且银行卡已扣款，但订单状态一直显示待支付，已提供订单号，需要核对支付渠道流水。',
 '订单问题', 'P1', 'PROCESSING', 'CHAT', 4, 5, 2,
 NOW() - INTERVAL 32 HOUR, NOW() - INTERVAL 39 HOUR, NULL, NULL, NULL, 1, '支付渠道对账中，已超解决时限',
 NOW() - INTERVAL 40 HOUR, NOW() - INTERVAL 30 HOUR, 0),
(5,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 10 HOUR, '%Y%m%d%H%i%s'), '5005'),
 '移动端无法上传附件',
 '用户在手机浏览器中上传附件时提示“文件格式不支持”，实际上传的是 JPG 图片，需要确认移动端上传组件兼容性。',
 '技术问题', 'P2', 'PROCESSING', 'MANUAL', NULL, 4, 3,
 NOW() + INTERVAL 14 HOUR, NOW() - INTERVAL 590 MINUTE, NULL, NULL, NULL, 0, '已远程协助用户排查浏览器设置',
 NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 5 HOUR, 0),
(6,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 3 DAY, '%Y%m%d%H%i%s'), '6006'),
 '会员服务退款申请未到账',
 '用户申请会员服务退款后超过 3 个工作日仍未到账，需要核查退款流水并跟进支付渠道处理。',
 '售后问题', 'P2', 'RESOLVED', 'CHAT', 4, 5, 2,
 NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 12 MINUTE, NOW() - INTERVAL 67 HOUR, NULL, NULL, 0, '退款已原路退回，用户确认收到',
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 67 HOUR, 0),
(7,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 5 DAY, '%Y%m%d%H%i%s'), '7007'),
 '企业开票信息填写错误',
 '用户电话咨询开票流程，因公司名称填写错误导致发票作废，需要重新登记开票信息并重新开具。',
 '其他', 'P3', 'RESOLVED', 'PHONE', NULL, 5, 3,
 NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 5 DAY + INTERVAL 30 MINUTE, NOW() - INTERVAL 100 HOUR, NULL, NULL, 0, '已电话告知开票流程并重新登记',
 NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 100 HOUR, 0),
(8,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 6 DAY, '%Y%m%d%H%i%s'), '8008'),
 '账号绑定手机号解绑申请',
 '用户更换手机号后旧号码已停用，无法自行接收验证码，申请人工解绑旧手机号并绑定新号码。',
 '账号问题', 'P3', 'CLOSED', 'MANUAL', NULL, 4, 2,
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 6 DAY + INTERVAL 15 MINUTE, NOW() - INTERVAL 134 HOUR, NOW() - INTERVAL 132 HOUR, 5, 0, '已解绑旧手机号并重新绑定新号码',
 NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 132 HOUR, 0),
(9,  CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 5 DAY - INTERVAL 60 MINUTE, '%Y%m%d%H%i%s'), '9009'),
 '订单漏发商品申请补发',
 '用户收到的订单缺少一件商品，已提供开箱照片，申请补发。',
 '订单问题', 'P1', 'CLOSED', 'CHAT', 5, 4, 3,
 NOW() - INTERVAL 112 HOUR, NOW() - INTERVAL 118 HOUR, NOW() - INTERVAL 114 HOUR, NOW() - INTERVAL 113 HOUR, 4, 0, '订单已补发，用户确认收货',
 NOW() - INTERVAL 5 DAY - INTERVAL 60 MINUTE, NOW() - INTERVAL 113 HOUR, 0),
(10, CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 48 HOUR, '%Y%m%d%H%i%s'), '1010'),
 '批量导入文件行数超限',
 '用户导入 8000 行数据时提示超过单次 5000 行限制，希望支持分批导入或提高上限。',
 '技术问题', 'P0', 'REOPENED', 'CHAT', 6, 6, 2,
 NOW() - INTERVAL 46 HOUR, NOW() - INTERVAL 47 HOUR, NOW() - INTERVAL 45 HOUR, NULL, NULL, 1, '用户反馈问题复现，重新打开',
 NOW() - INTERVAL 48 HOUR, NOW() - INTERVAL 20 HOUR, 0),
(11, CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 4 DAY, '%Y%m%d%H%i%s'), '1101'),
 '退款失败需要更换收款账户',
 '用户申请退款时提示“原支付方式不可用”，需要人工核实并改为银行卡退款。',
 '售后问题', 'P2', 'CLOSED', 'MANUAL', NULL, 6, 3,
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 4 DAY + INTERVAL 20 MINUTE, NOW() - INTERVAL 87 HOUR, NOW() - INTERVAL 86 HOUR, 5, 0, '已更换收款账户并退款成功',
 NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 86 HOUR, 0),
(12, CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 6 HOUR, '%Y%m%d%H%i%s'), '1201'),
 '手机端上传附件按钮点击无响应',
 '用户来电反馈在手机浏览器中点击上传附件按钮没有任何反应，需要使用 Chrome、Safari 等主流浏览器复现。',
 '技术问题', 'P2', 'ASSIGNED', 'PHONE', NULL, 5, 2,
 NOW() + INTERVAL 18 HOUR, NULL, NULL, NULL, NULL, 0, '电话反馈移动端兼容性问题，待坐席首响',
 NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 5 HOUR - INTERVAL 30 MINUTE, 0),
(13, CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 1 HOUR, '%Y%m%d%H%i%s'), '1301'),
 '希望增加深色模式',
 '用户希望系统支持深色模式，可以根据系统设置自动切换，减少夜间使用的视觉疲劳。',
 '其他', 'P3', 'PENDING', 'MANUAL', NULL, 4, NULL,
 NOW() + INTERVAL 71 HOUR, NULL, NULL, NULL, NULL, 0, '产品建议类工单，等待排期',
 NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 50 MINUTE, 0),
(14, CONCAT('WO', DATE_FORMAT(NOW() - INTERVAL 3 HOUR, '%Y%m%d%H%i%s'), '1401'),
 '导出的报表文件为空',
 '用户导出报表时文件可以下载，但打开后只有表头没有数据，需要排查数据权限与筛选条件。',
 '技术问题', 'P2', 'PROCESSING', 'CHAT', 8, 4, 3,
 NOW() + INTERVAL 21 HOUR, NOW() - INTERVAL 170 MINUTE, NULL, NULL, NULL, 0, '正在排查报表导出权限配置',
 NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 150 MINUTE, 0);

-- -------------------------------------------------------------
-- 9. 工单流转日志 work_order_log：52 条（每单 2-5 条，CREATE 必有）
-- -------------------------------------------------------------
INSERT INTO `work_order_log`
(`id`, `order_id`, `action`, `from_status`, `to_status`, `operator_id`, `operator_name`, `remark`, `create_time`) VALUES
-- 工单 1
(1,  1,  'CREATE',  NULL,       'PENDING',    4,    '王小明',     '用户提交工单：旧账号无法登录', NOW() - INTERVAL 120 MINUTE),
(2,  1,  'COMMENT', 'PENDING',  'PENDING',    4,    '王小明',     '补充说明：账号绑定的手机号已停用', NOW() - INTERVAL 95 MINUTE),
-- 工单 2
(3,  2,  'CREATE',  NULL,       'PENDING',    5,    '陈小红',     '电话客服代客创建工单', NOW() - INTERVAL 4 DAY),
(4,  2,  'OVERDUE', 'PENDING',  'PENDING',    NULL, '系统',       'SLA 已超时，工单仍未处理', NOW() - INTERVAL 1 DAY),
-- 工单 3
(5,  3,  'CREATE',  NULL,       'PENDING',    6,    '赵小刚',     '会话转工单：批量导入持续报错', NOW() - INTERVAL 180 MINUTE),
(6,  3,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给张客服处理', NOW() - INTERVAL 170 MINUTE),
(7,  3,  'OVERDUE', 'ASSIGNED', 'ASSIGNED',   NULL, '系统',       'SLA 首响已超时', NOW() - INTERVAL 60 MINUTE),
-- 工单 4
(8,  4,  'CREATE',  NULL,       'PENDING',    5,    '陈小红',     '会话转工单：支付成功但订单状态异常', NOW() - INTERVAL 40 HOUR),
(9,  4,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给张客服跟进支付渠道对账', NOW() - INTERVAL 39 HOUR - INTERVAL 50 MINUTE),
(10, 4,  'ACCEPT',  'ASSIGNED', 'PROCESSING', 2,    '张客服',     '坐席已受理工单', NOW() - INTERVAL 39 HOUR - INTERVAL 40 MINUTE),
(11, 4,  'PROCESS', 'PROCESSING','PROCESSING',2,    '张客服',     '已联系仓库核实发货状态', NOW() - INTERVAL 30 HOUR),
-- 工单 5
(12, 5,  'CREATE',  NULL,       'PENDING',    4,    '王小明',     '用户提交工单：移动端无法上传附件', NOW() - INTERVAL 600 MINUTE),
(13, 5,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给李客服处理', NOW() - INTERVAL 590 MINUTE),
(14, 5,  'ACCEPT',  'ASSIGNED', 'PROCESSING', 3,    '李客服',     '坐席已受理工单', NOW() - INTERVAL 585 MINUTE),
(15, 5,  'PROCESS', 'PROCESSING','PROCESSING',3,    '李客服',     '已远程协助用户完成浏览器设置', NOW() - INTERVAL 300 MINUTE),
-- 工单 6
(16, 6,  'CREATE',  NULL,       'PENDING',    5,    '陈小红',     '会话转工单：会员退款未到账', NOW() - INTERVAL 3 DAY),
(17, 6,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给张客服核实退款流水', NOW() - INTERVAL 71 HOUR),
(18, 6,  'ACCEPT',  'ASSIGNED', 'PROCESSING', 2,    '张客服',     '坐席已受理工单', NOW() - INTERVAL 70 HOUR),
(19, 6,  'PROCESS', 'PROCESSING','PROCESSING',2,    '张客服',     '已向支付渠道提交查询申请', NOW() - INTERVAL 68 HOUR),
(20, 6,  'RESOLVE', 'PROCESSING','RESOLVED',  2,    '张客服',     '退款已原路退回，用户确认收到', NOW() - INTERVAL 67 HOUR),
-- 工单 7
(21, 7,  'CREATE',  NULL,       'PENDING',    5,    '陈小红',     '电话咨询开票问题，代客创建工单', NOW() - INTERVAL 5 DAY),
(22, 7,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给李客服处理', NOW() - INTERVAL 119 HOUR),
(23, 7,  'ACCEPT',  'ASSIGNED', 'PROCESSING', 3,    '李客服',     '坐席已受理工单', NOW() - INTERVAL 118 HOUR),
(24, 7,  'RESOLVE', 'PROCESSING','RESOLVED',  3,    '李客服',     '已电话告知开票流程并重新登记信息', NOW() - INTERVAL 100 HOUR),
-- 工单 8
(25, 8,  'CREATE',  NULL,       'PENDING',    4,    '王小明',     '用户提交工单：账号手机号解绑', NOW() - INTERVAL 6 DAY),
(26, 8,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给张客服处理', NOW() - INTERVAL 143 HOUR),
(27, 8,  'ACCEPT',  'ASSIGNED', 'PROCESSING', 2,    '张客服',     '坐席已受理工单', NOW() - INTERVAL 142 HOUR),
(28, 8,  'RESOLVE', 'PROCESSING','RESOLVED',  2,    '张客服',     '已解绑旧手机号并重新绑定新号码', NOW() - INTERVAL 134 HOUR),
(29, 8,  'CLOSE',   'RESOLVED', 'CLOSED',     4,    '王小明',     '用户确认后关闭工单', NOW() - INTERVAL 132 HOUR),
-- 工单 9
(30, 9,  'CREATE',  NULL,       'PENDING',    4,    '王小明',     '会话转工单：订单漏发商品', NOW() - INTERVAL 5 DAY - INTERVAL 60 MINUTE),
(31, 9,  'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给李客服处理', NOW() - INTERVAL 118 HOUR),
(32, 9,  'ACCEPT',  'ASSIGNED', 'PROCESSING', 3,    '李客服',     '坐席已受理工单', NOW() - INTERVAL 117 HOUR),
(33, 9,  'RESOLVE', 'PROCESSING','RESOLVED',  3,    '李客服',     '订单已补发，用户确认收货', NOW() - INTERVAL 114 HOUR),
(34, 9,  'CLOSE',   'RESOLVED', 'CLOSED',     1,    '系统管理员', '管理员复核后关闭', NOW() - INTERVAL 113 HOUR),
-- 工单 10
(35, 10, 'CREATE',  NULL,       'PENDING',    6,    '赵小刚',     '会话转工单：导入行数超限', NOW() - INTERVAL 48 HOUR),
(36, 10, 'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给张客服处理', NOW() - INTERVAL 47 HOUR),
(37, 10, 'ACCEPT',  'ASSIGNED', 'PROCESSING', 2,    '张客服',     '坐席已受理工单', NOW() - INTERVAL 46 HOUR - INTERVAL 30 MINUTE),
(38, 10, 'RESOLVE', 'PROCESSING','RESOLVED',  2,    '张客服',     '已调整导入模板兼容性', NOW() - INTERVAL 45 HOUR),
(39, 10, 'REOPEN',  'RESOLVED', 'REOPENED',   6,    '赵小刚',     '用户反馈问题复现，重新打开', NOW() - INTERVAL 20 HOUR),
-- 工单 11
(40, 11, 'CREATE',  NULL,       'PENDING',    6,    '赵小刚',     '用户提交工单：退款失败需更换账户', NOW() - INTERVAL 4 DAY),
(41, 11, 'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给李客服处理', NOW() - INTERVAL 95 HOUR),
(42, 11, 'ACCEPT',  'ASSIGNED', 'PROCESSING', 3,    '李客服',     '坐席已受理工单', NOW() - INTERVAL 94 HOUR),
(43, 11, 'RESOLVE', 'PROCESSING','RESOLVED',  3,    '李客服',     '已更换收款账户并退款成功', NOW() - INTERVAL 87 HOUR),
(44, 11, 'CLOSE',   'RESOLVED', 'CLOSED',     1,    '系统管理员', '工单关闭', NOW() - INTERVAL 86 HOUR),
-- 工单 12
(45, 12, 'CREATE',  NULL,       'PENDING',    5,    '陈小红',     '电话反馈移动端上传按钮异常', NOW() - INTERVAL 6 HOUR),
(46, 12, 'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给张客服处理', NOW() - INTERVAL 5 HOUR - INTERVAL 30 MINUTE),
-- 工单 13
(47, 13, 'CREATE',  NULL,       'PENDING',    4,    '王小明',     '用户提交产品建议：深色模式', NOW() - INTERVAL 60 MINUTE),
(48, 13, 'COMMENT', 'PENDING',  'PENDING',    4,    '王小明',     '补充：希望跟随系统设置自动切换', NOW() - INTERVAL 50 MINUTE),
-- 工单 14
(49, 14, 'CREATE',  NULL,       'PENDING',    4,    '王小明',     '会话转工单：报表导出为空', NOW() - INTERVAL 180 MINUTE),
(50, 14, 'ASSIGN',  'PENDING',  'ASSIGNED',   1,    '系统管理员', '指派给李客服处理', NOW() - INTERVAL 175 MINUTE),
(51, 14, 'ACCEPT',  'ASSIGNED', 'PROCESSING', 3,    '李客服',     '坐席已受理工单', NOW() - INTERVAL 172 MINUTE),
(52, 14, 'PROCESS', 'PROCESSING','PROCESSING',3,    '李客服',     '正在排查报表导出权限配置', NOW() - INTERVAL 150 MINUTE);

-- -------------------------------------------------------------
-- 10. AI 调用日志 ai_call_log：19 条（含 7 条失败降级样本）
-- -------------------------------------------------------------
INSERT INTO `ai_call_log`
(`id`, `session_id`, `message_id`, `model`, `prompt_tokens`, `completion_tokens`, `latency_ms`,
 `success`, `error_msg`, `create_time`) VALUES
(1,  1,  2,    'deepseek-chat', 320, 156, 1280, 1, NULL, NOW() - INTERVAL 6 DAY - INTERVAL 119 MINUTE),
(2,  1,  4,    'deepseek-chat', 210, 88,  860,  1, NULL, NOW() - INTERVAL 6 DAY - INTERVAL 117 MINUTE),
(3,  2,  6,    'deepseek-chat', 305, 142, 1420, 1, NULL, NOW() - INTERVAL 5 DAY - INTERVAL 59 MINUTE),
(4,  2,  8,    'deepseek-chat', 260, 120, 1105, 1, NULL, NOW() - INTERVAL 5 DAY - INTERVAL 56 MINUTE),
(5,  3,  10,   'deepseek-chat', 280, 96,  950,  1, NULL, NOW() - INTERVAL 4 DAY - INTERVAL 299 MINUTE),
(6,  4,  17,   'deepseek-chat', 300, 130, 1360, 1, NULL, NOW() - INTERVAL 3 DAY - INTERVAL 239 MINUTE),
(7,  5,  22,   'deepseek-chat', 240, 110, 760,  1, NULL, NOW() - INTERVAL 2 DAY - INTERVAL 419 MINUTE),
(8,  6,  26,   'deepseek-chat', 310, 128, 1710, 1, NULL, NOW() - INTERVAL 1 DAY - INTERVAL 539 MINUTE),
(9,  7,  32,   'deepseek-chat', 190, 76,  640,  1, NULL, NOW() - INTERVAL 479 MINUTE),
(10, 8,  37,   'deepseek-chat', 225, 105, 890,  1, NULL, NOW() - INTERVAL 179 MINUTE),
(11, 9,  40,   'deepseek-chat', 400, 150, 2200, 1, NULL, NOW() - INTERVAL 7 MINUTE),
(12, 10, 43,   'deepseek-chat', 350, 140, 1850, 1, NULL, NOW() - INTERVAL 4 MINUTE),
(13, 2,  NULL, 'deepseek-chat', 0,   0,   3000, 0, 'DeepSeek API 请求超时（15s），已降级为知识库回答', NOW() - INTERVAL 5 DAY - INTERVAL 57 MINUTE),
(14, 6,  NULL, 'deepseek-chat', 0,   0,   1200, 0, 'HTTP 429 Too Many Requests，触发上游限流', NOW() - INTERVAL 1 DAY - INTERVAL 538 MINUTE),
(15, 8,  NULL, 'deepseek-chat', 0,   0,   800,  0, 'LLM 返回内容为空，已降级为知识库回答', NOW() - INTERVAL 178 MINUTE),
(16, 3,  NULL, 'deepseek-chat', 0,   0,   350,  0, '未配置 DeepSeek API Key，降级为知识库回答', NOW() - INTERVAL 4 DAY - INTERVAL 298 MINUTE),
(17, 9,  NULL, 'deepseek-chat', 0,   0,   3000, 0, 'DeepSeek API 请求超时（15s）', NOW() - INTERVAL 8 MINUTE),
(18, 4,  NULL, 'deepseek-chat', 0,   0,   1800, 0, 'HTTP 500 Internal Server Error', NOW() - INTERVAL 3 DAY - INTERVAL 238 MINUTE),
(19, 10, NULL, 'deepseek-chat', 0,   0,   900,  0, '网络连接被重置，已重试 1 次后成功', NOW() - INTERVAL 5 MINUTE);

-- -------------------------------------------------------------
-- 11. 登录日志 sys_login_log：13 条（成功 10 条、失败 3 条）
-- -------------------------------------------------------------
INSERT INTO `sys_login_log`
(`id`, `user_id`, `username`, `ip`, `user_agent`, `success`, `message`, `create_time`) VALUES
(1,  1,    'admin',   '127.0.0.1',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 1, '登录成功', NOW() - INTERVAL 15 MINUTE),
(2,  2,    'agent01', '192.168.1.11', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 1, '登录成功', NOW() - INTERVAL 25 MINUTE),
(3,  4,    'user01',  '10.0.0.21',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/120.0',   1, '登录成功', NOW() - INTERVAL 1 HOUR),
(4,  NULL, 'admin',   '127.0.0.1',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 0, '登录失败：密码错误', NOW() - INTERVAL 2 HOUR),
(5,  NULL, 'user01',  '10.0.0.21',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/120.0',   0, '登录失败：短信验证码已过期', NOW() - INTERVAL 6 HOUR),
(6,  NULL, 'user03',  '10.0.0.23',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 0, '登录失败：账号已被禁用', NOW() - INTERVAL 1 DAY),
(7,  5,    'user02',  '10.0.0.22',    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/604.1', 1, '登录成功', NOW() - INTERVAL 2 DAY),
(8,  2,    'agent01', '192.168.1.11', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 1, '登录成功', NOW() - INTERVAL 2 DAY - INTERVAL 30 MINUTE),
(9,  3,    'agent02', '192.168.1.12', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/120.0',   1, '登录成功', NOW() - INTERVAL 3 DAY),
(10, 1,    'admin',   '127.0.0.1',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 1, '登录成功', NOW() - INTERVAL 3 DAY - INTERVAL 20 MINUTE),
(11, 5,    'user02',  '10.0.0.22',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/120.0',   1, '登录成功', NOW() - INTERVAL 4 DAY),
(12, 4,    'user01',  '10.0.0.21',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 1, '登录成功', NOW() - INTERVAL 5 DAY),
(13, 1,    'admin',   '127.0.0.1',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0', 1, '登录成功', NOW() - INTERVAL 6 DAY);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 演示数据初始化完成，数据量概览：
--   sys_role 3、sys_user 6、agent_status 2、kb_category 5、
--   kb_entry 20、chat_session 10、chat_message 44、work_order 14、
--   work_order_log 52、ai_call_log 19、sys_login_log 13
-- 演示账号（密码均为 123456）：
--   admin / agent01 / agent02 / user01 / user02 / user03（禁用）
-- =============================================================
