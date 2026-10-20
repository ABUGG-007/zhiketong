/**
 * 前端字典与格式化工具
 * tone 对应 StatusPill 组件的配色：teal / indigo / amber / red / slate / green
 */

export const ROLE_DICT = {
  ADMIN: { label: '管理员', tone: 'red' },
  AGENT: { label: '客服坐席', tone: 'indigo' },
  USER: { label: '普通用户', tone: 'slate' }
}

export const SESSION_STATUS_DICT = {
  AI: { label: 'AI 接待', tone: 'teal' },
  WAITING: { label: '排队中', tone: 'amber' },
  HUMAN: { label: '人工中', tone: 'indigo' },
  CLOSED: { label: '已结束', tone: 'slate' }
}

export const ORDER_STATUS_DICT = {
  PENDING: { label: '待受理', tone: 'amber' },
  ASSIGNED: { label: '已分配', tone: 'indigo' },
  PROCESSING: { label: '处理中', tone: 'indigo' },
  RESOLVED: { label: '已解决', tone: 'teal' },
  CLOSED: { label: '已关闭', tone: 'slate' },
  REOPENED: { label: '已重开', tone: 'red' }
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
  { label: '待受理', value: 'PENDING' },
  { label: '已分配', value: 'ASSIGNED' },
  { label: '处理中', value: 'PROCESSING' },
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

export const dictLabel = (dict, key, fallback = '—') => dict[key]?.label || key || fallback
export const dictTone = (dict, key, fallback = 'slate') => dict[key]?.tone || fallback

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
