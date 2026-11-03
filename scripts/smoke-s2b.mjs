/**
 * S2-B 冒烟脚本：坐席辅助（零依赖：node scripts/smoke-s2b.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、02_data.sql、08_s2b_agent_assist.sql。
 * 覆盖：实时推荐答案（知识库命中 + 不自动发送）· 进线摘要（读取/重新生成）· 服务小结（生成草稿/采纳归档/空内容校验）·
 *       角色与数据权限（访客、非接待坐席被拒）· 会话结束后仍可归档小结。
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
  sessionId: null
}

async function ensureIdleSession(user) {
  const current = await call('GET', '/chat/sessions/current', user.token)
  if (current.code === 200 && current.data?.id) {
    await call('POST', `/chat/sessions/${current.data.id}/close`, user.token, { closeReason: 'USER_CLOSED' })
  }
}

async function messageTotal(token) {
  // 会话消息分页 pageSize 上限 100（R-35 参数校验），超过会直接 400
  const page = await call('GET', `/chat/sessions/${ctx.sessionId}/messages?pageNum=1&pageSize=100`, token)
  if (page.code !== 200) {
    console.log(`INFO  读取消息分页失败：code=${page.code} message=${page.message}`)
  }
  return Number(page.data?.total || 0)
}

async function cleanup() {
  try {
    if (ctx.sessionId) {
      await call('POST', `/chat/sessions/${ctx.sessionId}/close`, ctx.user.token, { closeReason: 'USER_CLOSED' })
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
  const agentId = ctx.agent.user.userId ?? ctx.agent.user.id
  log('三种角色登录', true, `admin=${ctx.admin.user.nickname} agent01=${ctx.agent.user.nickname}`)

  /* ---------- 准备：坐席上线 + 访客会话 ---------- */
  await call('POST', '/agent/offline', ctx.agent.token)
  if (ctx.agent2) await call('POST', '/agent/offline', ctx.agent2.token)
  await ensureIdleSession(ctx.user)
  await call('POST', '/agent/online', ctx.agent.token, {})

  const created = await call('POST', '/chat/sessions', ctx.user.token)
  ctx.sessionId = created.data?.session?.id || created.data?.sessionId || created.data?.id
  log('用户创建会话', created.code === 200 && Boolean(ctx.sessionId), `sessionId=${ctx.sessionId}`)

  const sent = await call('POST', `/chat/sessions/${ctx.sessionId}/messages`, ctx.user.token, {
    content: '退款多久能到账？',
    contentType: 'TEXT',
    clientMsgId: `smoke-s2b-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  })
  log('访客提问并得到 AI 应答', sent.code === 200 && Boolean(sent.data?.aiReply?.content),
    `code=${sent.code} AI=${(sent.data?.aiReply?.content || '').slice(0, 30)}`)
  log('访客消息已落库', (await messageTotal(ctx.user.token)) >= 2,
    `消息数=${await messageTotal(ctx.user.token)}`)

  const transfer = await call('POST', `/chat/sessions/${ctx.sessionId}/transfer`, ctx.user.token, {
    reason: '需要人工确认退款到账时间'
  })
  log('访客请求转人工', transfer.code === 200 && transfer.data?.status === 'WAITING',
    `status=${transfer.data?.status} 排位=${transfer.data?.queuePosition ?? '-'}`)

  let status = ''
  for (let i = 0; i < 16; i++) {
    const detail = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.agent.token)
    status = detail.data?.status || ''
    if (status === 'HUMAN') break
    await new Promise((r) => setTimeout(r, 500))
  }
  log('坐席上线后自动接入', status === 'HUMAN', `status=${status}`)

  /* ---------- R-104 实时推荐答案 ---------- */
  const totalBeforeRecommend = await messageTotal(ctx.agent.token)
  const recommend = await call('GET', `/agent/sessions/${ctx.sessionId}/recommendations?limit=3`, ctx.agent.token)
  const items = recommend.data?.items || []
  const top = items[0]
  log('推荐答案命中知识库（带来源与可信度）',
    recommend.code === 200 && recommend.data?.answerSource === 'KB'
      && items.length >= 1 && Number(top?.kbId) === 14 && Number(top?.score) >= 0.35,
    `source=${recommend.data?.answerSource} kbId=${top?.kbId} score=${top?.score} 条数=${items.length}`)
  log('推荐答案精准命中并标注可信度', recommend.data?.directHit === true
    && Boolean(top?.answerSource) && Array.isArray(top?.hitKeywords),
    `directHit=${recommend.data?.directHit} 来源=${top?.answerSource} 关键词=${(top?.hitKeywords || []).join('/')}`)

  const totalAfterRecommend = await messageTotal(ctx.agent.token)
  log('推荐答案不自动对客发送', totalAfterRecommend === totalBeforeRecommend,
    `消息数 ${totalBeforeRecommend} → ${totalAfterRecommend}`)

  const userForbidden = await call('GET', `/agent/sessions/${ctx.sessionId}/recommendations`, ctx.user.token)
  log('访客访问坐席推荐被拒', userForbidden.http === 403 || userForbidden.code === 403,
    `code=${userForbidden.code} message=${userForbidden.message}`)

  if (ctx.agent2) {
    const outsider = await call('GET', `/agent/sessions/${ctx.sessionId}/recommendations`, ctx.agent2.token)
    log('非接待坐席访问推荐被拒', outsider.http === 403 || outsider.code === 403,
      `code=${outsider.code} message=${outsider.message}`)
  }

  /* ---------- R-105 / R-106 进线摘要 ---------- */
  const brief = await call('GET', `/agent/sessions/${ctx.sessionId}/brief`, ctx.agent.token)
  const briefSource = brief.data?.source
  log('坐席获取进线摘要', brief.code === 200
    && Boolean(brief.data?.customerDemand)
    && ['AI', 'RULE', 'AGENT'].includes(briefSource),
    `code=${brief.code} 来源=${briefSource} 诉求=${(brief.data?.customerDemand || '').slice(0, 30)}`)
  log('进线摘要带出客户近期工单列表', Array.isArray(brief.data?.recentOrders),
    `工单数=${brief.data?.recentOrders?.length ?? 0}`)

  const regenerated = await call('POST', `/agent/sessions/${ctx.sessionId}/brief/regenerate`, ctx.agent.token)
  log('重新生成进线摘要', regenerated.code === 200 && regenerated.data?.id === brief.data?.id
    && Boolean(regenerated.data?.customerDemand),
    `id=${regenerated.data?.id} 来源=${regenerated.data?.source}`)

  const adminBrief = await call('GET', `/agent/sessions/${ctx.sessionId}/brief`, ctx.admin.token)
  log('管理员可查看任意会话进线摘要', adminBrief.code === 200 && Boolean(adminBrief.data?.sessionId),
    `sessionId=${adminBrief.data?.sessionId}`)

  /* ---------- R-107 / R-108 / R-109 服务小结 ---------- */
  const emptySummary = await call('GET', `/agent/sessions/${ctx.sessionId}/summary`, ctx.agent.token)
  log('未生成时服务小结为空', emptySummary.code === 200 && emptySummary.data == null,
    `code=${emptySummary.code} data=${JSON.stringify(emptySummary.data)}`)

  const generated = await call('POST', `/agent/sessions/${ctx.sessionId}/summary/generate`, ctx.agent.token)
  log('生成服务小结草稿', generated.code === 200 && generated.data?.status === 'DRAFT'
    && Boolean(generated.data?.customerDemand),
    `code=${generated.code} 状态=${generated.data?.status} 来源=${generated.data?.source} 诉求=${(generated.data?.customerDemand || '').slice(0, 24)}`)
  log('服务小结草稿未被自动归档', generated.data?.status === 'DRAFT', `状态=${generated.data?.status}`)

  const invalid = await call('PUT', `/agent/sessions/${ctx.sessionId}/summary`, ctx.agent.token, {
    customerDemand: '',
    serviceProcess: '',
    result: '',
    followUp: '',
    keywords: []
  })
  log('空内容采纳被拦截', invalid.code === 400, `code=${invalid.code} message=${invalid.message}`)

  const adopted = await call('PUT', `/agent/sessions/${ctx.sessionId}/summary`, ctx.agent.token, {
    customerDemand: '访客咨询退款到账时间',
    serviceProcess: '智能客服已说明退款时效，坐席进一步核对订单退款进度',
    result: '已告知预计 1-3 个工作日到账',
    followUp: '明日回访确认到账情况',
    keywords: ['退款', '到账时效']
  })
  log('坐席确认后归档服务小结', adopted.code === 200 && adopted.data?.status === 'ADOPTED'
    && adopted.data?.source === 'AGENT' && (adopted.data?.keywords || []).includes('退款'),
    `状态=${adopted.data?.status} 来源=${adopted.data?.source} 关键词=${(adopted.data?.keywords || []).join('/')}`)

  const closed = await call('POST', `/agent/sessions/${ctx.sessionId}/close`, ctx.agent.token, {
    closeReason: 'AGENT_CLOSED'
  })
  log('坐席结束会话', closed.code === 200 && closed.data?.status === 'CLOSED', `status=${closed.data?.status}`)

  const afterClose = await call('PUT', `/agent/sessions/${ctx.sessionId}/summary`, ctx.agent.token, {
    customerDemand: '访客咨询退款到账时间',
    serviceProcess: '智能客服说明时效，坐席核对退款单',
    result: '已解决并结束会话',
    followUp: '无'
  })
  log('会话结束后仍可归档小结', afterClose.code === 200 && afterClose.data?.status === 'ADOPTED'
    && afterClose.data?.editable === true,
    `状态=${afterClose.data?.status} editable=${afterClose.data?.editable}`)
  await call('POST', '/agent/offline', ctx.agent.token)
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
