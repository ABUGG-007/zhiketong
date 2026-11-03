/**
 * S2-A WebSocket 冒烟脚本：C-04 TRANSFER_REQUEST 透传 skillGroupId（零依赖：node scripts/smoke-ws-s2a.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、02_data.sql、07_s2a_quick_reply_skill.sql。
 * 覆盖：WS 转人工携带「咨询类型」→ TRANSFER_QUEUED 回显 skillGroupId → 会话落库并通过 REST 回显名称。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const WS_BASE = process.env.ZK_WS || 'ws://127.0.0.1:8080/ws/chat'

async function call(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json = null
  try {
    json = await res.json()
  } catch {
    /* 空响应 */
  }
  return { http: res.status, ...(json || {}) }
}

async function main() {
  const login = await call('POST', '/auth/login', null, { username: 'user01', password: '123456' })
  if (login.code !== 200) throw new Error(`登录失败: ${login.http} ${login.message}`)
  const token = login.data.token

  const current = await call('GET', '/chat/sessions/current', token)
  if (current.code === 200 && current.data?.id) {
    await call('POST', `/chat/sessions/${current.data.id}/close`, token, { closeReason: 'USER_CLOSED' })
  }
  const created = await call('POST', '/chat/sessions', token)
  const sessionId = created.data?.session?.id || created.data?.sessionId || created.data?.id
  if (!sessionId) throw new Error(`创建会话失败: ${JSON.stringify(created)}`)

  const choices = await call('GET', '/chat/skill-groups', token)
  const group = (choices.data || [])[0]
  if (!group) throw new Error('没有启用中的技能组，请先执行 07_s2a_quick_reply_skill.sql')

  const queued = await new Promise((resolve, reject) => {
    const ws = new WebSocket(`${WS_BASE}?token=${encodeURIComponent(token)}`)
    const timer = setTimeout(() => {
      ws.close()
      reject(new Error('10 秒内未收到 TRANSFER_QUEUED'))
    }, 10000)
    ws.onopen = () => {
      ws.send(JSON.stringify({
        type: 'TRANSFER_REQUEST',
        requestId: 'ws-s2a-' + Date.now(),
        timestamp: Date.now(),
        data: { sessionId, reason: 'WS 透传咨询类型校验', skillGroupId: group.id }
      }))
    }
    ws.onmessage = (e) => {
      const msg = JSON.parse(e.data)
      if (msg.type !== 'TRANSFER_QUEUED') return
      clearTimeout(timer)
      ws.close()
      resolve(msg.data)
    }
  })

  const queuedOk = Number(queued?.queuePosition) >= 1 && Number(queued?.queueSize) >= 1
  console.log(`${queuedOk ? 'PASS' : 'FAIL'}  WS 转人工入队回执  → 排位=${queued?.queuePosition} 队列长度=${queued?.queueSize} 预计等待=${queued?.estimatedWaitSeconds}s`)

  // TRANSFER_QUEUED 在事务内推送，REST 回查可能先于提交，轮询等落库
  let after = null
  let stored = null
  for (let i = 0; i < 5; i++) {
    after = await call('GET', `/chat/sessions/${sessionId}`, token)
    stored = after.data?.skillGroupId
    if (Number(stored) === Number(group.id)) break
    await new Promise((r) => setTimeout(r, 300))
  }
  const storedOk = Number(stored) === Number(group.id) && Boolean(after.data?.skillGroupName)
  console.log(`${storedOk ? 'PASS' : 'FAIL'}  会话落库并回显咨询类型名称  → skillGroupId=${stored} name=${after.data?.skillGroupName}`)
  if (!storedOk) console.log('DEBUG ' + JSON.stringify(after))

  await call('POST', `/chat/sessions/${sessionId}/close`, token, { closeReason: 'USER_CLOSED' })
  return queuedOk && storedOk
}

main()
  .then((ok) => {
    console.log(`\n合计 2 项，通过 ${ok ? 2 : 0} 项，失败 ${ok ? 0 : 2} 项`)
    process.exit(ok ? 0 : 1)
  })
  .catch((e) => {
    console.error('ERROR ' + e.message)
    process.exit(2)
  })
