/**
 * 前端字典与格式化工具
 * tone 对应 StatusPill 组件的配色：teal / indigo / amber / red / slate / green
 */

export const ROLE_DICT = {
  PLATFORM_ADMIN: { label: '平台管理员', tone: 'red' },
  ADMIN: { label: '管理员', tone: 'red' },
  AGENT: { label: '客服坐席', tone: 'indigo' },
  USER: { label: '普通用户', tone: 'slate' }
}

/** 租户状态：对应 tenant.status（S4-B） */
export const TENANT_STATUS_DICT = {
  ENABLED: { label: '正常', tone: 'teal' },
  DISABLED: { label: '已停用', tone: 'slate' },
  EXPIRED: { label: '已到期', tone: 'red' }
}

export const TENANT_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'ENABLED' },
  { label: '已停用', value: 'DISABLED' },
  { label: '已到期', value: 'EXPIRED' }
]

/** 操作审计模块：对应 audit_log.module（S4-C，PL-06） */
export const AUDIT_MODULE_DICT = {
  TENANT: { label: '租户与套餐', tone: 'red' },
  USER: { label: '账号管理', tone: 'indigo' },
  KB: { label: '知识库', tone: 'teal' },
  EMBED: { label: '网页接入', tone: 'amber' },
  AI: { label: '大模型配置', tone: 'indigo' },
  ORDER_RULE: { label: '工单规则', tone: 'slate' },
  QUALITY: { label: '智能质检', tone: 'amber' },
  ALERT: { label: '告警与监控', tone: 'red' }
}

export const AUDIT_MODULE_OPTIONS = [
  { label: '全部模块', value: '' },
  ...Object.keys(AUDIT_MODULE_DICT).map((value) => ({
    label: AUDIT_MODULE_DICT[value].label,
    value
  }))
]

/** 操作审计动作：对应 audit_log.action（S4-C，PL-06） */
export const AUDIT_ACTION_DICT = {
  CREATE: { label: '新增', tone: 'teal' },
  UPDATE: { label: '编辑', tone: 'indigo' },
  DELETE: { label: '删除', tone: 'red' },
  ENABLE: { label: '启用', tone: 'teal' },
  DISABLE: { label: '停用', tone: 'slate' },
  CHANGE_PLAN: { label: '变更套餐', tone: 'amber' },
  RENEW: { label: '续期', tone: 'indigo' },
  RESET_PASSWORD: { label: '重置密码', tone: 'amber' },
  ROTATE_SECRET: { label: '轮换密钥', tone: 'red' },
  REVIEW: { label: '复核', tone: 'indigo' },
  CONFIRM: { label: '确认归档', tone: 'teal' },
  REJECT: { label: '驳回', tone: 'red' }
}

export const AUDIT_ACTION_OPTIONS = [
  { label: '全部动作', value: '' },
  ...Object.keys(AUDIT_ACTION_DICT).map((value) => ({
    label: AUDIT_ACTION_DICT[value].label,
    value
  }))
]

export const SESSION_STATUS_DICT = {
  AI: { label: 'AI 接待', tone: 'teal' },
  WAITING: { label: '排队中', tone: 'amber' },
  HUMAN: { label: '人工中', tone: 'indigo' },
  CLOSED: { label: '已结束', tone: 'slate' }
}

export const ORDER_STATUS_DICT = {
  PENDING: { label: '待分配', tone: 'amber' },
  ASSIGNED: { label: '已分配', tone: 'indigo' },
  PROCESSING: { label: '处理中', tone: 'indigo' },
  WAITING_CUSTOMER: { label: '等待客户反馈', tone: 'amber' },
  RESOLVED: { label: '已解决', tone: 'teal' },
  CLOSED: { label: '已关闭', tone: 'slate' },
  REOPENED: { label: '已重开', tone: 'red' }
}

/** 状态含义提示：用于工单抽屉顶部的状态说明，避免用户误解流转规则 */
export const ORDER_STATUS_HINT = {
  PENDING: '等待管理员分配或坐席自领',
  ASSIGNED: '已指定受理坐席，等待坐席受理',
  PROCESSING: '坐席处理中，可继续补充进展',
  WAITING_CUSTOMER: '已挂起等待客户反馈，SLA 计时暂停',
  RESOLVED: '问题已解决，等待创建人确认关闭并评价',
  CLOSED: '工单已关闭，如有问题可重开',
  REOPENED: '工单已重开，等待重新受理'
}

export const ORDER_ACTION_DICT = {
  CREATE: { label: '创建工单', tone: 'slate' },
  ASSIGN: { label: '分配工单', tone: 'indigo' },
  ACCEPT: { label: '受理工单', tone: 'teal' },
  PROCESS: { label: '添加进展', tone: 'indigo' },
  PAUSE: { label: '挂起停表', tone: 'amber' },
  RESUME: { label: '恢复计时', tone: 'teal' },
  RESOLVE: { label: '解决工单', tone: 'teal' },
  CLOSE: { label: '关闭工单', tone: 'slate' },
  REOPEN: { label: '重开工单', tone: 'red' },
  COMMENT: { label: '编辑/备注', tone: 'slate' },
  OVERDUE: { label: 'SLA 超时', tone: 'red' },
  ESCALATE: { label: 'SLA 升级', tone: 'red' }
}

/** SLA 计时状态：对应 work_order.sla_status / order_sla_record.sla_status */
export const SLA_STATUS_DICT = {
  NORMAL: { label: '正常', tone: 'teal' },
  WARN: { label: '预警', tone: 'amber' },
  OVERDUE: { label: '超时', tone: 'red' },
  PAUSED: { label: '停表中', tone: 'slate' },
  FINISHED: { label: '已完结', tone: 'slate' }
}

/** 工单规则类型：对应 order_rule.rule_type（S3-C） */
export const ORDER_RULE_TYPE_DICT = {
  AUTO_ASSIGN: { label: '自动分配', tone: 'indigo' },
  TRIGGER: { label: '事件触发器', tone: 'teal' },
  AUTOMATION: { label: '时间自动化', tone: 'amber' }
}

export const ORDER_RULE_TYPE_OPTIONS = [
  { label: '自动分配', value: 'AUTO_ASSIGN' },
  { label: '事件触发器', value: 'TRIGGER' },
  { label: '时间自动化', value: 'AUTOMATION' }
]

/** 工单规则触发事件：对应 order_rule.trigger_event / order_rule_run.event（S3-C） */
export const ORDER_RULE_EVENT_DICT = {
  CREATED: { label: '工单创建', tone: 'slate' },
  ASSIGNED: { label: '工单分配', tone: 'indigo' },
  PROCESSING: { label: '开始处理', tone: 'indigo' },
  RESOLVED: { label: '工单解决', tone: 'teal' },
  CLOSED: { label: '工单关闭', tone: 'slate' },
  REOPENED: { label: '工单重开', tone: 'red' },
  PRIORITY_CHANGED: { label: '优先级变更', tone: 'amber' },
  SLA_WARN: { label: 'SLA 预警', tone: 'amber' },
  SLA_OVERDUE: { label: 'SLA 超时', tone: 'red' },
  SLA_ESCALATE: { label: 'SLA 升级', tone: 'red' }
}

export const ORDER_RULE_EVENT_OPTIONS = [
  { label: '工单创建', value: 'CREATED' },
  { label: '工单分配', value: 'ASSIGNED' },
  { label: '开始处理', value: 'PROCESSING' },
  { label: '工单解决', value: 'RESOLVED' },
  { label: '工单关闭', value: 'CLOSED' },
  { label: '工单重开', value: 'REOPENED' },
  { label: '优先级变更', value: 'PRIORITY_CHANGED' },
  { label: 'SLA 预警', value: 'SLA_WARN' },
  { label: 'SLA 超时', value: 'SLA_OVERDUE' },
  { label: 'SLA 升级', value: 'SLA_ESCALATE' }
]

/** 工单规则动作类型：对应 order_rule.action_type（S3-C） */
export const ORDER_RULE_ACTION_DICT = {
  AUTO_ASSIGN: { label: '自动分配', tone: 'indigo' },
  ASSIGN_AGENT: { label: '指派坐席', tone: 'teal' },
  CHANGE_PRIORITY: { label: '调整优先级', tone: 'amber' },
  CHANGE_STATUS: { label: '变更状态', tone: 'red' },
  NOTIFY_ROLE: { label: '通知角色', tone: 'slate' }
}

export const ORDER_RULE_ACTION_OPTIONS = [
  { label: '自动分配', value: 'AUTO_ASSIGN' },
  { label: '指派坐席', value: 'ASSIGN_AGENT' },
  { label: '调整优先级', value: 'CHANGE_PRIORITY' },
  { label: '变更状态', value: 'CHANGE_STATUS' },
  { label: '通知角色', value: 'NOTIFY_ROLE' }
]

/** 工单规则执行状态：对应 order_rule_run.status（S3-C） */
export const ORDER_RULE_RUN_STATUS_DICT = {
  PENDING: { label: '待执行', tone: 'amber' },
  DONE: { label: '执行成功', tone: 'teal' },
  FAILED: { label: '执行失败', tone: 'red' },
  SKIPPED: { label: '已跳过', tone: 'slate' }
}

/** 通知角色动作值：对应 action_type=NOTIFY_ROLE 时的 action_value（S3-C） */
export const ORDER_RULE_NOTIFY_ROLE_OPTIONS = [
  { label: '管理员', value: 'ADMIN' },
  { label: '创建人', value: 'CREATOR' },
  { label: '受理人', value: 'ASSIGNEE' }
]

/** 工单来源选项（含全部），对应 order_rule.match_source / work_order.source */
export const ORDER_SOURCE_OPTIONS = [
  { label: '全部来源', value: '' },
  { label: '会话转入', value: 'CHAT' },
  { label: '手工创建', value: 'MANUAL' },
  { label: '电话受理', value: 'PHONE' }
]

/** 工单分类：必须与后端 WorkOrderCreateRequest 的正则保持一致 */
export const ORDER_CATEGORIES = ['技术问题', '账号问题', '订单问题', '售后问题', '其他']

export const ORDER_CATEGORY_OPTIONS = [
  { label: '全部分类', value: '' },
  ...ORDER_CATEGORIES.map((item) => ({ label: item, value: item }))
]

export const CLOSE_REASON_DICT = {
  USER_CLOSED: '用户主动结束',
  AGENT_CLOSED: '坐席结束会话',
  TIMEOUT: '超时自动结束',
  SYSTEM: '系统结束'
}

export const PRIORITY_DICT = {
  P0: { label: 'P0 紧急', tone: 'red' },
  P1: { label: 'P1 高', tone: 'amber' },
  P2: { label: 'P2 中', tone: 'indigo' },
  P3: { label: 'P3 低', tone: 'slate' }
}

export const ORDER_SOURCE_DICT = {
  CHAT: { label: '会话转入', tone: 'teal' },
  MANUAL: { label: '手工创建', tone: 'slate' },
  PHONE: { label: '电话受理', tone: 'indigo' }
}

export const AGENT_STATUS_DICT = {
  ONLINE: { label: '在线', tone: 'teal' },
  BUSY: { label: '忙碌', tone: 'amber' },
  OFFLINE: { label: '离线', tone: 'slate' }
}

export const SESSION_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: 'AI 接待', value: 'AI' },
  { label: '排队中', value: 'WAITING' },
  { label: '人工中', value: 'HUMAN' },
  { label: '已结束', value: 'CLOSED' }
]

export const ORDER_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '待分配', value: 'PENDING' },
  { label: '已分配', value: 'ASSIGNED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '等待客户反馈', value: 'WAITING_CUSTOMER' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已重开', value: 'REOPENED' }
]

export const PRIORITY_OPTIONS = [
  { label: '全部优先级', value: '' },
  { label: 'P0 紧急', value: 'P0' },
  { label: 'P1 高', value: 'P1' },
  { label: 'P2 中', value: 'P2' },
  { label: 'P3 低', value: 'P3' }
]

export const ROLE_OPTIONS = [
  { label: '全部角色', value: '' },
  { label: '管理员', value: 'ADMIN' },
  { label: '客服坐席', value: 'AGENT' },
  { label: '普通用户', value: 'USER' }
]

/** 智能质检规则类型：对应 quality_rule.rule_type（S5-A，QC-02~QC-05） */
export const QUALITY_RULE_TYPE_DICT = {
  SEMANTIC: { label: '语义类', tone: 'indigo' },
  SENTIMENT: { label: '情感类', tone: 'amber' },
  SERVICE: { label: '服务规范类', tone: 'teal' },
  SENSITIVE: { label: '敏感词类', tone: 'red' }
}

export const QUALITY_RULE_TYPE_OPTIONS = [
  { label: '语义类', value: 'SEMANTIC' },
  { label: '情感类', value: 'SENTIMENT' },
  { label: '服务规范类', value: 'SERVICE' },
  { label: '敏感词类', value: 'SENSITIVE' }
]

/** 质检规则严重程度：对应 quality_rule.severity */
export const QUALITY_SEVERITY_DICT = {
  LOW: { label: '一般', tone: 'slate' },
  MEDIUM: { label: '中等', tone: 'amber' },
  HIGH: { label: '严重', tone: 'red' }
}

export const QUALITY_SEVERITY_OPTIONS = [
  { label: '一般', value: 'LOW' },
  { label: '中等', value: 'MEDIUM' },
  { label: '严重', value: 'HIGH' }
]

/** 质检规则匹配方式：CONTAINS 出现即命中 / ABSENT 未出现即命中 */
export const QUALITY_MATCH_MODE_OPTIONS = [
  { label: '出现即命中', value: 'CONTAINS' },
  { label: '未出现即命中', value: 'ABSENT' }
]

/** 质检等级：对应 quality_record.grade（A≥90 / B≥80 / C≥70 / D<70） */
export const QUALITY_GRADE_DICT = {
  A: { label: '优秀', tone: 'teal' },
  B: { label: '良好', tone: 'indigo' },
  C: { label: '待改进', tone: 'amber' },
  D: { label: '不合格', tone: 'red' }
}

export const QUALITY_GRADE_OPTIONS = [
  { label: '全部等级', value: '' },
  { label: 'A 优秀', value: 'A' },
  { label: 'B 良好', value: 'B' },
  { label: 'C 待改进', value: 'C' },
  { label: 'D 不合格', value: 'D' }
]

/** 质检记录复核状态：对应 quality_record.status（S5-B 复核流转） */
export const QUALITY_RECORD_STATUS_DICT = {
  PENDING: { label: '待复核', tone: 'amber' },
  REVIEWED: { label: '已复核', tone: 'teal' },
  CONFIRMED: { label: '已确认', tone: 'indigo' }
}

/** 质检复核动作：对应 quality_record.review_action（S5-B） */
export const QUALITY_REVIEW_ACTION_DICT = {
  REVIEW: { label: '提交复核', tone: 'indigo' },
  CONFIRM: { label: '确认归档', tone: 'teal' },
  REJECT: { label: '驳回退回', tone: 'red' }
}

export const QUALITY_RECORD_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '待复核', value: 'PENDING' },
  { label: '已复核', value: 'REVIEWED' },
  { label: '已确认', value: 'CONFIRMED' }
]

/** 质检评估引擎：对应 quality_record.engine */
export const QUALITY_ENGINE_DICT = {
  RULE: { label: '规则引擎', tone: 'slate' },
  AI: { label: '大模型', tone: 'indigo' },
  MIX: { label: '规则+大模型', tone: 'teal' }
}

export const dictLabel = (dict, key, fallback = '—') => dict[key]?.label || key || fallback
export const dictTone = (dict, key, fallback = 'slate') => dict[key]?.tone || fallback

/* =========================================================
   告警与监控（S6-B，AS-10 / AS-11）
   ========================================================= */

/** 告警规则类型：对应 alert_rule.rule_type */
export const ALERT_RULE_TYPE_DICT = {
  QUEUE: { label: '排队', tone: 'amber' },
  SESSION: { label: '会话', tone: 'indigo' },
  AGENT: { label: '坐席', tone: 'teal' },
  ORDER: { label: '工单', tone: 'red' }
}

export const ALERT_RULE_TYPE_OPTIONS = [
  { label: '排队', value: 'QUEUE' },
  { label: '会话', value: 'SESSION' },
  { label: '坐席', value: 'AGENT' },
  { label: '工单', value: 'ORDER' }
]

/** 告警严重程度：对应 alert_rule.severity / alert_event.severity */
export const ALERT_SEVERITY_DICT = {
  INFO: { label: '提示', tone: 'slate' },
  WARN: { label: '警告', tone: 'amber' },
  CRITICAL: { label: '严重', tone: 'red' }
}

export const ALERT_SEVERITY_OPTIONS = [
  { label: '提示', value: 'INFO' },
  { label: '警告', value: 'WARN' },
  { label: '严重', value: 'CRITICAL' }
]

/** 告警处理状态：对应 alert_event.status */
export const ALERT_STATUS_DICT = {
  OPEN: { label: '待处理', tone: 'red' },
  ACKED: { label: '已确认', tone: 'amber' },
  CLOSED: { label: '已关闭', tone: 'slate' }
}

export const ALERT_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 'OPEN' },
  { label: '已确认', value: 'ACKED' },
  { label: '已关闭', value: 'CLOSED' }
]

/** 告警目标类型：对应 alert_event.target_type */
export const ALERT_TARGET_TYPE_DICT = {
  QUEUE: { label: '排队', tone: 'amber' },
  SESSION: { label: '会话', tone: 'indigo' },
  AGENT: { label: '坐席', tone: 'teal' },
  ORDER: { label: '工单', tone: 'red' }
}

/** 告警通知对象：对应 alert_rule.notify_role */
export const ALERT_NOTIFY_ROLE_OPTIONS = [
  { label: '租户管理员', value: 'ADMIN' },
  { label: '责任坐席', value: 'AGENT' },
  { label: '管理员 + 坐席', value: 'ALL' }
]

export const ALERT_NOTIFY_ROLE_DICT = {
  ADMIN: { label: '管理员', tone: 'red' },
  AGENT: { label: '责任坐席', tone: 'indigo' },
  ALL: { label: '管理员 + 坐席', tone: 'teal' }
}

/**
 * 告警指标：与后端 AlertMetric 一一对应。
 * unit 决定阈值输入框的提示语与事件指标的展示口径。
 */
export const ALERT_METRIC_OPTIONS = [
  {
    value: 'NO_AGENT_ONLINE',
    label: '排队无在线坐席',
    ruleType: 'QUEUE',
    unit: 'COUNT',
    hint: '当前排队人数达到阈值且全租户无在线坐席时触发'
  },
  {
    value: 'QUEUE_BACKLOG',
    label: '排队积压',
    ruleType: 'QUEUE',
    unit: 'COUNT',
    hint: '当前排队人数达到阈值时触发，用于提前扩容或召回坐席'
  },
  {
    value: 'QUEUE_SLOW',
    label: '排队时长过长',
    ruleType: 'QUEUE',
    unit: 'SECONDS',
    hint: '队首访客等待秒数达到阈值时触发'
  },
  {
    value: 'FIRST_RESPONSE_SLOW',
    label: '首响偏慢',
    ruleType: 'SESSION',
    unit: 'SECONDS',
    hint: '坐席已接入但尚未回复，等待秒数达到阈值时触发'
  },
  {
    value: 'ORDER_OVERDUE',
    label: '工单超时',
    ruleType: 'ORDER',
    unit: 'COUNT',
    hint: '已超时且未关闭的工单数达到阈值时触发'
  },
  {
    value: 'AGENT_RESPONSE_TIMEOUT',
    label: '访客消息待回复超时',
    ruleType: 'AGENT',
    unit: 'SECONDS',
    hint: '会话最后一条为访客消息且等待秒数达到阈值时触发'
  },
  {
    value: 'SESSION_OVERLONG',
    label: '会话时长超长',
    ruleType: 'SESSION',
    unit: 'MINUTES',
    hint: '人工会话持续分钟数达到阈值时触发'
  },
  {
    value: 'LOW_RATING',
    label: '客户差评',
    ruleType: 'SESSION',
    unit: 'SCORE',
    hint: '会话评分不高于阈值（1-5 星）时触发，建议阈值为 2'
  },
  {
    value: 'SENSITIVE_WORD',
    label: '会话命中敏感词',
    ruleType: 'SESSION',
    unit: 'COUNT',
    hint: '会话消息命中规则关键词时触发，必须配置关键词'
  }
]

export const ALERT_METRIC_DICT = Object.fromEntries(
  ALERT_METRIC_OPTIONS.map((item) => [item.value, item])
)

/** 阈值单位文案：COUNT → 次 / SECONDS → 秒 / MINUTES → 分钟 / SCORE → 星 */
export const ALERT_UNIT_LABEL = {
  COUNT: '次',
  SECONDS: '秒',
  MINUTES: '分钟',
  SCORE: '星'
}

/** 阈值输入提示：例「人数达到 5 人」 */
export const alertThresholdLabel = (metricCode, thresholdUnit) => {
  const metric = ALERT_METRIC_DICT[metricCode]
  const unit = thresholdUnit || metric?.unit || 'COUNT'
  if (metricCode === 'LOW_RATING') return '评分不高于（1-5 星）'
  if (unit === 'SECONDS') return '等待时长达到（秒）'
  if (unit === 'MINUTES') return '持续时长达到（分钟）'
  return '数量达到（次）'
}

/** 事件指标值展示：秒 → 可读时长，其余直接加单位 */
export function formatAlertMetric(value, unit) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  if (unit === 'SECONDS') return formatWait(num)
  if (unit === 'MINUTES') return formatMinutes(num)
  if (unit === 'SCORE') return `${num} 星`
  return `${formatNumber(num)} 次`
}

/** 后端统一返回 yyyy-MM-dd HH:mm:ss，兼容 ISO 的 T 分隔与毫秒 */
export function formatDateTime(value) {
  if (!value) return '—'
  const text = String(value).replace('T', ' ').replace(/\.\d+$/, '')
  return text.slice(0, 19)
}

export function formatDate(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 10)
}

/** "2026-10-20" -> "10-20" */
export function shortDate(value) {
  const text = formatDate(value)
  return text.length >= 10 ? text.slice(5) : text
}

/** "2026-10-20 21:03:11" -> "21:03" */
export function formatClock(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  const match = text.match(/(\d{2}):(\d{2})/)
  return match ? `${match[1]}:${match[2]}` : ''
}

export function nowClock() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 等待秒数 -> "3 分 12 秒" / "12 秒" */
export function formatWait(seconds) {
  const sec = Number(seconds)
  if (!Number.isFinite(sec) || sec < 0) return '—'
  if (sec < 60) return `${Math.floor(sec)} 秒`
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  if (m < 60) return s ? `${m} 分 ${s} 秒` : `${m} 分钟`
  const h = Math.floor(m / 60)
  return `${h} 小时 ${m % 60} 分`
}

/** 评分展示，null 显示占位 */
export function formatRating(rating) {
  const n = Number(rating)
  if (!Number.isFinite(n) || n <= 0) return '—'
  return n.toFixed(1)
}

/** 数字安全化 */
export function formatNumber(n) {
  const num = Number(n)
  if (!Number.isFinite(num)) return '—'
  return num.toLocaleString('zh-CN')
}

/** 百分比（0-100） */
export function percent(part, total) {
  const p = Number(part)
  const t = Number(total)
  if (!Number.isFinite(p) || !Number.isFinite(t) || t <= 0) return 0
  return Math.min(100, Math.round((p / t) * 100))
}

/** 价格展示：0 显示免费，-1 显示不限 */
export function formatPrice(price, period = '月') {
  const n = Number(price)
  if (!Number.isFinite(n)) return '—'
  if (n === 0) return '免费'
  return `¥${formatNumber(n)} / ${period}`
}

/** 额度展示：-1 显示不限 */
export function formatQuota(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return n < 0 ? '不限' : formatNumber(n)
}

/** 分钟数 -> “2 小时 15 分” */
export function formatMinutes(minutes) {
  const n = Number(minutes)
  if (!Number.isFinite(n) || n < 0) return '—'
  if (n < 60) return `${Math.round(n)} 分钟`
  const h = Math.floor(n / 60)
  const m = Math.round(n % 60)
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`
}

/* =========================================================
   客户之声（S6-C，DB-06）
   ========================================================= */

/** 主题来源：对应 voc_topic.topic_type */
export const VOC_TOPIC_TYPE_DICT = {
  AUTO: { label: '自动聚类', tone: 'indigo' },
  MANUAL: { label: '人工创建', tone: 'teal' }
}

export const VOC_TOPIC_TYPE_OPTIONS = [
  { label: '全部来源', value: '' },
  { label: '自动聚类', value: 'AUTO' },
  { label: '人工创建', value: 'MANUAL' }
]

export const VOC_TOPIC_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

/** 未命中来源渠道：对应 kb_unmatched_question.channel */
export const VOC_CHANNEL_DICT = {
  SITE: { label: '站内会话', tone: 'indigo' },
  EMBED: { label: '网页接入', tone: 'teal' }
}

/** 未命中处理状态（与知识运营一致） */
export const VOC_QUESTION_STATUS_DICT = {
  PENDING: { label: '待处理', tone: 'amber' },
  CONVERTED: { label: '已转词条', tone: 'green' },
  IGNORED: { label: '已忽略', tone: 'slate' }
}

/** 大屏展示密度：对应 screen_layout.density */
export const SCREEN_DENSITY_OPTIONS = [
  { label: '舒适', value: 'COMFORTABLE' },
  { label: '紧凑', value: 'COMPACT' }
]

/** 大屏轮询间隔候选值（秒），WS 不可用时生效 */
export const SCREEN_REFRESH_OPTIONS = [5, 10, 15, 30, 60].map((value) => ({
  label: `${value} 秒`,
  value
}))
