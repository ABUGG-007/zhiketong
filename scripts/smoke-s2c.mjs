/**
 * S2-C 冒烟脚本：客户信息卡 · 输入联想 · 内部备注 · 排队中机器人接待（零依赖：node scripts/smoke-s2c.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、02_data.sql、09_s2c_assist_customer.sql。
 * 覆盖：访客联想 R-111（不返回答案 / 参数校验 / 越权）· 排队机器人 RB-15（WAITING 仍有 AI 应答且不累计未命中）·
 *       客户信息卡 R-110（画像 / 历史会话 / 历史工单 / 越权）· 坐席联想 R-112 · 内部备注 R-113/R-114
 *       （仅坐席可见 / 不计入会话消息数 / 空内容与超长拦截 / AI 状态 409）。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const results = []
const log = (name, ok, detail) => {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  → ' + detail : ''}`)
}

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

async function login(username, password = '123456') {
  const r = await call('POST', '/auth/login', null, { username, password })
  if (r.code !== 200) throw new Error(`登录失败 ${username}: ${r.http} ${r.message}`)
  return r.data
}

const ctx = {
  admin: null,
  agent: null,
  agent2: null,
  user: null,
  sessionId: null,
  secondSessionId: null
}

async function ensureIdleSession(user) {
  const current = await call('GET', '/chat/sessions/current', user.token)
  if (current.code === 200 && current.data?.id) {
    await call('POST', `/chat/sessions/${current.data.id}/close`, user.token, { closeReason: 'USER_CLOSED' })
  }
}

async function pageMessages(token) {
  // 会话消息分页 pageSize 上限 100（R-35 参数校验），超过会直接 400
  const page = await call('GET', `/chat/sessions/${ctx.sessionId}/messages?pageNum=1&pageSize=100`, token)
  if (page.code !== 200) {
    console.log(`INFO  读取消息分页失败：code=${page.code} message=${page.message}`)
  }
  return { total: Number(page.data?.total || 0), list: page.data?.list || [] }
}

async function waitStatus(token, expected, times = 16) {
  let status = ''
  for (let i = 0; i < times; i++) {
    const detail = await call('GET', `/chat/sessions/${ctx.sessionId}`, token)
    status = detail.data?.status || ''
    if (status === expected) break
    await new Promise((r) => setTimeout(r, 500))
  }
  return status
}

async function cleanup() {
  try {
    if (ctx.sessionId) {
      await call('POST', `/chat/sessions/${ctx.sessionId}/close`, ctx.user.token, { closeReason: 'USER_CLOSED' })
    }
    if (ctx.secondSessionId) {
      await call('POST', `/chat/sessions/${ctx.secondSessionId}/close`, ctx.user.token, { closeReason: 'USER_CLOSED' })
    }
  } catch {
    /* 忽略清理失败 */
  }
  try {
    await call('POST', '/agent/offline', ctx.agent.token)
    if (ctx.agent2) await call('POST', '/agent/offline', ctx.agent2.token)
  } catch {
    /* 忽略清理失败 */
  }
}

async function main() {
  ctx.admin = await login('admin')
  ctx.agent = await login('agent01')
  ctx.user = await login('user01')
  try {
    ctx.agent2 = await login('agent02')
  } catch {
    ctx.agent2 = null
    console.log('INFO  未找到 agent02 账号，跳过「非接待坐席被拒」用例')
  }
  log('三种角色登录', true, `admin=${ctx.admin.user.nickname} agent01=${ctx.agent.user.nickname}`)

  /* ---------- 准备：坐席离线 + 访客会话（先验证排队机器人） ---------- */
  await call('POST', '/agent/offline', ctx.agent.token)
  if (ctx.agent2) await call('POST', '/agent/offline', ctx.agent2.token)
  await ensureIdleSession(ctx.user)

  const created = await call('POST', '/chat/sessions', ctx.user.token)
  ctx.sessionId = created.data?.session?.id || created.data?.sessionId || created.data?.id
  log('用户创建会话', created.code === 200 && Boolean(ctx.sessionId), `sessionId=${ctx.sessionId}`)

  const first = await call('POST', `/chat/sessions/${ctx.sessionId}/messages`, ctx.user.token, {
    content: '退款多久能到账？',
    contentType: 'TEXT',
    clientMsgId: `smoke-s2c-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  })
  log('AI 状态提问得到应答', first.code === 200 && Boolean(first.data?.aiReply?.content),
    `AI=${(first.data?.aiReply?.content || '').slice(0, 30)}`)

  /* ---------- R-111 访客输入联想 ---------- */
  const visitorSuggest = await call('GET', `/chat/suggestions?query=${encodeURIComponent('退款')}&limit=3`,
    ctx.user.token)
  const visitorItems = visitorSuggest.data?.items || []
  const visitorTop = visitorItems[0]
  log('访客联想命中知识库问法',
    visitorSuggest.code === 200 && visitorSuggest.data?.scope === 'VISITOR'
      && visitorItems.length >= 1 && Boolean(visitorTop?.question),
    `scope=${visitorSuggest.data?.scope} 条数=${visitorItems.length} 首条=${visitorTop?.question} score=${visitorTop?.score}`)
  log('访客联想不返回答案内容',
    visitorItems.every((item) => !item.answer),
    `answer=${visitorItems.map((item) => item.answer ?? 'null').join(',')}`)

  const emptySuggest = await call('GET', '/chat/suggestions?query=&limit=5', ctx.user.token)
  log('空关键词返回提示而非报错',
    emptySuggest.code === 200 && (emptySuggest.data?.items || []).length === 0
      && Boolean(emptySuggest.data?.note),
    `code=${emptySuggest.code} note=${emptySuggest.data?.note}`)

  const tooLongSuggest = await call('GET',
    `/chat/suggestions?query=${encodeURIComponent('退'.repeat(51))}&limit=5`, ctx.user.token)
  log('联想关键词超长被拦截', tooLongSuggest.code === 400,
    `code=${tooLongSuggest.code} message=${tooLongSuggest.message}`)

  const agentOnVisitorSuggest = await call('GET',
    `/chat/suggestions?query=${encodeURIComponent('退款')}&limit=3`, ctx.agent.token)
  log('坐席访问访客联想被拒', agentOnVisitorSuggest.http === 403 || agentOnVisitorSuggest.code === 403,
    `code=${agentOnVisitorSuggest.code}`)

  /* ---------- RB-15 排队中机器人接待 ---------- */
  const transfer = await call('POST', `/chat/sessions/${ctx.sessionId}/transfer`, ctx.user.token, {
    reason: '需要人工确认退款到账时间'
  })
  log('无空闲坐席时进入排队', transfer.code === 200 && transfer.data?.status === 'WAITING',
    `status=${transfer.data?.status} 排位=${transfer.data?.queuePosition ?? '-'}`)

  const queued = await call('POST', `/chat/sessions/${ctx.sessionId}/messages`, ctx.user.token, {
    content: '忘记密码怎么办？',
    contentType: 'TEXT',
    clientMsgId: `smoke-s2c-q-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  })
  log('排队中机器人继续接待（RB-15）',
    queued.code === 200 && Boolean(queued.data?.aiReply?.content)
      && queued.data?.sessionStatus === 'WAITING',
    `AI=${(queued.data?.aiReply?.content || '').slice(0, 30)} status=${queued.data?.sessionStatus}`)
  log('排队应答不累计未命中', Number(queued.data?.fallbackCount || 0) === 0,
    `fallbackCount=${queued.data?.fallbackCount}`)

  // 排队中会话尚未分配给坐席，坐席端无数据权限，改用访客本人查询会话状态
  const stillWaiting = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.user.token)
  log('排队机器人不改变会话状态', stillWaiting.data?.status === 'WAITING', `status=${stillWaiting.data?.status}`)

  const queuedNote = await call('POST', `/agent/sessions/${ctx.sessionId}/notes`, ctx.admin.token, {
    content: '排队中不能写内部备注'
  })
  log('排队中新增内部备注被拦截', queuedNote.code === 409,
    `code=${queuedNote.code} message=${queuedNote.message}`)

  /* ---------- 坐席上线自动接入 ---------- */
  await call('POST', '/agent/online', ctx.agent.token, {})
  const humanStatus = await waitStatus(ctx.agent.token, 'HUMAN')
  log('坐席上线后自动接入', humanStatus === 'HUMAN', `status=${humanStatus}`)

  /* ---------- R-110 客户信息卡 ---------- */
  const card = await call('GET', `/agent/sessions/${ctx.sessionId}/customer`, ctx.agent.token)
  const profile = card.data?.profile || {}
  const orderStats = card.data?.orderStats || {}
  log('客户信息卡返回画像与本次诉求',
    card.code === 200 && Boolean(card.data?.userName)
      && Number(profile.totalSessions) >= 1 && Boolean(card.data?.current?.statusName),
    `客户=${card.data?.userName} 会话数=${profile.totalSessions} 状态=${card.data?.current?.statusName}`)
  log('客户信息卡带出历史会话与工单统计',
    Array.isArray(card.data?.recentSessions) && card.data.recentSessions.length >= 1
      && Array.isArray(card.data?.recentOrders)
      && Number(orderStats.total) >= 0,
    `历史会话=${card.data?.recentSessions?.length ?? 0} 工单总数=${orderStats.total} 处理中=${orderStats.open}`)

  const userCard = await call('GET', `/agent/sessions/${ctx.sessionId}/customer`, ctx.user.token)
  log('访客访问客户信息卡被拒', userCard.http === 403 || userCard.code === 403, `code=${userCard.code}`)
  if (ctx.agent2) {
    const outsiderCard = await call('GET', `/agent/sessions/${ctx.sessionId}/customer`, ctx.agent2.token)
    log('非接待坐席访问客户信息卡被拒',
      outsiderCard.http === 403 || outsiderCard.code === 403, `code=${outsiderCard.code}`)
  }

  /* ---------- R-112 坐席输入联想 ---------- */
  const agentSuggest = await call('GET',
    `/agent/sessions/${ctx.sessionId}/suggestions?query=${encodeURIComponent('退款')}&limit=3`, ctx.agent.token)
  const agentItems = agentSuggest.data?.items || []
  log('坐席联想返回问法与答案摘要',
    agentSuggest.code === 200 && agentSuggest.data?.scope === 'AGENT'
      && agentItems.length >= 1 && Boolean(agentItems[0]?.answer),
    `scope=${agentSuggest.data?.scope} 条数=${agentItems.length} 摘要=${(agentItems[0]?.answer || '').slice(0, 24)}`)
  if (ctx.agent2) {
    const outsiderSuggest = await call('GET',
      `/agent/sessions/${ctx.sessionId}/suggestions?query=${encodeURIComponent('退款')}`, ctx.agent2.token)
    log('非接待坐席访问联想被拒',
      outsiderSuggest.http === 403 || outsiderSuggest.code === 403, `code=${outsiderSuggest.code}`)
  }

  /* ---------- R-113 / R-114 内部备注 ---------- */
  const emptyNotes = await call('GET', `/agent/sessions/${ctx.sessionId}/notes`, ctx.agent.token)
  log('初始内部备注为空', emptyNotes.code === 200 && (emptyNotes.data || []).length === 0,
    `条数=${(emptyNotes.data || []).length}`)

  const beforeUserPage = await pageMessages(ctx.user.token)
  const beforeDetail = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.agent.token)
  const beforeCount = Number(beforeDetail.data?.messageCount || 0)

  const note = await call('POST', `/agent/sessions/${ctx.sessionId}/notes`, ctx.agent.token, {
    content: '客户为老客户，退款诉求已核对订单号，走加急通道'
  })
  log('坐席新增内部备注', note.code === 200 && note.data?.senderType === 'INTERNAL_NOTE',
    `code=${note.code} senderType=${note.data?.senderType} senderName=${note.data?.senderName}`)

  const notes = await call('GET', `/agent/sessions/${ctx.sessionId}/notes`, ctx.agent.token)
  log('内部备注可在坐席端回读', notes.code === 200 && (notes.data || []).length === 1,
    `条数=${(notes.data || []).length}`)

  const afterUserPage = await pageMessages(ctx.user.token)
  log('访客消息列表不含内部备注',
    afterUserPage.total === beforeUserPage.total
      && afterUserPage.list.every((item) => item.senderType !== 'INTERNAL_NOTE'),
    `访客消息数 ${beforeUserPage.total} → ${afterUserPage.total}`)

  const agentPage = await pageMessages(ctx.agent.token)
  log('坐席消息列表包含内部备注',
    agentPage.list.some((item) => item.senderType === 'INTERNAL_NOTE' && item.senderName === '内部备注'),
    `坐席可见条数=${agentPage.list.filter((item) => item.senderType === 'INTERNAL_NOTE').length}`)

  const afterDetail = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.agent.token)
  log('内部备注不计入会话消息数',
    Number(afterDetail.data?.messageCount || 0) === beforeCount,
    `messageCount ${beforeCount} → ${afterDetail.data?.messageCount}`)

  const emptyNote = await call('POST', `/agent/sessions/${ctx.sessionId}/notes`, ctx.agent.token, {
    content: '   '
  })
  log('空内部备注被拦截', emptyNote.code === 400, `code=${emptyNote.code} message=${emptyNote.message}`)

  const longNote = await call('POST', `/agent/sessions/${ctx.sessionId}/notes`, ctx.agent.token, {
    content: '备'.repeat(501)
  })
  log('内部备注超长被拦截', longNote.code === 400, `code=${longNote.code} message=${longNote.message}`)

  const userNote = await call('POST', `/agent/sessions/${ctx.sessionId}/notes`, ctx.user.token, {
    content: '访客尝试写备注'
  })
  log('访客新增内部备注被拒', userNote.http === 403 || userNote.code === 403, `code=${userNote.code}`)

  if (ctx.agent2) {
    const outsiderNote = await call('POST', `/agent/sessions/${ctx.sessionId}/notes`, ctx.agent2.token, {
      content: '非接待坐席尝试写备注'
    })
    log('非接待坐席新增内部备注被拒',
      outsiderNote.http === 403 || outsiderNote.code === 403, `code=${outsiderNote.code}`)
  }

  /* ---------- 收尾：关闭主业会话，验证 AI 状态不可写备注 ---------- */
  await call('POST', `/agent/sessions/${ctx.sessionId}/close`, ctx.agent.token, { closeReason: 'AGENT_CLOSED' })
  await call('POST', '/agent/offline', ctx.agent.token)

  const second = await call('POST', '/chat/sessions', ctx.user.token)
  ctx.secondSessionId = second.data?.session?.id || second.data?.sessionId || second.data?.id
  const aiNote = await call('POST', `/agent/sessions/${ctx.secondSessionId}/notes`, ctx.admin.token, {
    content: 'AI 接待中不应允许写内部备注'
  })
  log('AI 状态新增内部备注被拦截', aiNote.code === 409,
    `code=${aiNote.code} message=${aiNote.message}`)
  await call('POST', `/chat/sessions/${ctx.secondSessionId}/close`, ctx.user.token, { closeReason: 'USER_CLOSED' })
}

main()
  .then(async () => {
    await cleanup()
    const failed = results.filter((r) => !r.ok)
    console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
    process.exit(failed.length ? 1 : 0)
  })
  .catch(async (error) => {
    console.error('脚本异常：', error)
    await cleanup()
    process.exit(2)
  })
