const BASE = 'http://127.0.0.1:8080/api'

async function call(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json = null
  try { json = await res.json() } catch { /* 空响应 */ }
  return { http: res.status, ...(json || {}) }
}

const login = await call('POST', '/auth/login', null, { username: 'admin', password: '123456' })
const token = login.data.token
const sessions = await call('GET', '/admin/quality/sessions?pageNum=1&pageSize=5', token)
console.log('sessions', sessions.code, sessions.data?.total, JSON.stringify(sessions.data?.list?.[0]))
const target = sessions.data.list[0]
const t0 = Date.now()
const ev = await call('POST', `/admin/quality/sessions/${target.id}/evaluate`, token)
console.log('evaluate ms=', Date.now() - t0, 'code=', ev.code, 'msg=', ev.message)
if (ev.data) {
  console.log('score=', ev.data.score, 'grade=', ev.data.grade, 'engine=', ev.data.engine,
    'items=', ev.data.items?.length, 'hit=', ev.data.hitCount)
  console.log(JSON.stringify(ev.data.items?.slice(0, 4), null, 1))
}
const ov = await call('GET', '/admin/quality/overview', token)
console.log('overview', ov.code, JSON.stringify(ov.data))
