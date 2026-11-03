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
const t0 = Date.now()
const scan = await call('POST', '/admin/quality/scan', token)
console.log('scan ms=', Date.now() - t0, 'code=', scan.code, JSON.stringify(scan.data))
const recs = await call('GET', '/admin/quality/records?pageNum=1&pageSize=5', token)
console.log('records total=', recs.data?.total)
console.log(JSON.stringify(recs.data?.list?.[0], null, 1))
