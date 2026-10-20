/**
 * 后端演示接口快速自检（开发期使用）
 * 用法：node scripts/check-api.mjs [baseUrl]
 * 默认 baseUrl = http://localhost:8080
 */
const base = (process.argv[2] || 'http://localhost:8080').replace(/\/$/, '')

const login = async () => {
  const res = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' })
  })
  const body = await res.json()
  return body?.data?.token
}

const token = await login()
if (!token) {
  console.error('登录失败，无法继续自检')
  process.exit(1)
}
const auth = { Authorization: `Bearer ${token}` }

const get = async (path) => {
  const res = await fetch(`${base}${path}`, { headers: auth })
  const body = await res.json()
  return body
}

const keysOf = (obj) => (obj && typeof obj === 'object' ? Object.keys(obj).join(', ') : String(obj))
const check = (label, actual, expected) => {
  const missing = expected.filter((key) => !(key in (actual || {})))
  const extra = Object.keys(actual || {}).filter((key) => !expected.includes(key))
  console.log(
    `${missing.length || extra.length ? '!' : '✓'} ${label}` +
      (missing.length ? ` | 缺少: ${missing.join(',')}` : '') +
      (extra.length ? ` | 多余: ${extra.join(',')}` : '')
  )
}

const overview = await get('/api/demo/overview')
console.log('overview.code =', overview.code)
const data = overview.data || {}
check('overview.counts', data.counts, [
  'users', 'knowledge', 'categories', 'sessionsToday', 'messagesToday',
  'ordersToday', 'pendingOrders', 'waitingSessions', 'overdueOrders', 'onlineAgents'
])
check('overview.ratings', data.ratings, ['avgSessionRating', 'avgOrderRating'])
console.log('  trend:', Array.isArray(data.trend) ? `${data.trend.length} 天` : data.trend, '| 首项:', keysOf(data.trend?.[0]))
console.log('  recentOrders:', keysOf(data.recentOrders?.[0]))
console.log('  recentSessions:', keysOf(data.recentSessions?.[0]))
console.log('  agents:', keysOf(data.agents?.[0]))

const kb = await get('/api/demo/kb/entries?pageNum=1&pageSize=2')
console.log('\nkb page:', keysOf(kb.data), '| 条目:', keysOf(kb.data?.list?.[0]))

const orders = await get('/api/demo/workorders?status=PENDING&pageNum=1&pageSize=2')
console.log('workorders:', keysOf(orders.data?.list?.[0]), '| 状态筛选返回', orders.data?.total, '条')

const closed = await get('/api/demo/sessions?status=CLOSED&pageNum=1&pageSize=3')
console.log('sessions(CLOSED):', keysOf(closed.data?.list?.[0]))
const sessions = await get('/api/demo/sessions?pageNum=1&pageSize=3')
console.log('sessions(all):', keysOf(sessions.data?.list?.[0]))

const users = await get('/api/demo/users?roleCode=AGENT&pageNum=1&pageSize=2')
console.log('users:', keysOf(users.data?.list?.[0]))

const agents = await get('/api/demo/agents')
console.log('agents:', keysOf(agents.data?.[0]))

const waiting = await get('/api/demo/waiting')
console.log('waiting:', keysOf(waiting.data?.[0]))

const health = await get('/api/health')
console.log('\nhealth:', health.data?.status, '| checks:', keysOf(health.data?.checks))

const chatRes = await fetch(`${base}/api/ai/chat`, {
  method: 'POST',
  headers: { ...auth, 'Content-Type': 'application/json' },
  body: JSON.stringify({ message: '怎么退款' })
})
const chat = await chatRes.json()
console.log('ai/chat:', chat.code, '| data:', keysOf(chat.data))
console.log('  content:', String(chat.data?.content || '').slice(0, 90))
console.log('  fallback:', chat.data?.fallback, '| reason:', chat.data?.fallbackReason)
