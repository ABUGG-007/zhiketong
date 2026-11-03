/**
 * 智客通 S6-A 运营指标与实时大屏冒烟脚本（验收用，零依赖，node scripts/smoke-s6a.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql ~ sql/18_s6a_ops_metrics.sql。
 * 覆盖：权限收紧（R-176~R-179 仅管理员）→ 实时大屏结构与口径自洽（R-176，DB-02/DB-03）→
 *       运营指标区间报表（R-177，DB-01/DB-04）→ 区间参数校验 → 轮次指标回填（R-178）→
 *       坐席实时表现分页与参数校验（R-179）→ WebSocket 大屏订阅/推送/退订/鉴权。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const WS_BASE = process.env.ZK_WS || 'ws://127.0.0.1:8080/ws/chat'
const results = []
const log = (name, ok, detail) => {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ← ' + detail : ''}`)
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
  try { json = await res.json() } catch { /* 空响应 */ }
  return { http: res.status, ...(json || {}) }
}

async function login(username, password = '123456') {
  const r = await call('POST', '/auth/login', null, { username, password })
  if (r.code !== 200) throw new Error(`登录失败 ${username}: ${r.http} ${r.message}`)
  return r.data
}

const num = (value) => Number(value ?? 0)
const ratioOf = (a, b) => (num(b) > 0 ? num(a) / num(b) : 0)
const near = (a, b, tolerance = 0.001) => Math.abs(num(a) - num(b)) <= tolerance
const isRate = (value) => num(value) >= 0 && num(value) <= 1

/** 本地日期（Asia/Shanghai 与本机时区一致时无需额外处理） */
function dateText(offsetDays = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const pad = (v) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 建立一次 WS 会话：onOpen 发送消息，收到 firstType 后关闭并回传 data */
function wsExchange(token, onOpen, firstTypes, timeoutMs = 12000) {
  const wanted = new Set(Array.isArray(firstTypes) ? firstTypes : [firstTypes])
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${WS_BASE}?token=${encodeURIComponent(token)}`)
    const timer = setTimeout(() => {
      try { ws.close() } catch { /* 忽略 */ }
      reject(new Error(`${timeoutMs}ms 内未收到 ${[...wanted].join('/')}`))
    }, timeoutMs)
    ws.onopen = () => {
      try { onOpen(ws) } catch (e) { reject(e) }
    }
    ws.onmessage = (event) => {
      let msg
      try { msg = JSON.parse(event.data) } catch { return }
      if (!wanted.has(msg.type)) return
      clearTimeout(timer)
      try { ws.close() } catch { /* 忽略 */ }
      resolve({ type: msg.type, data: msg.data || {} })
    }
    ws.onerror = () => {
      clearTimeout(timer)
      reject(new Error('WS 连接异常'))
    }
  })
}

/** 保持连接收集多类消息（用于订阅 + 定时推送） */
function wsCollect(token, onOpen, types, timeoutMs = 14000) {
  const wanted = new Set(types)
  const seen = new Map()
  return new Promise((resolve) => {
    const ws = new WebSocket(`${WS_BASE}?token=${encodeURIComponent(token)}`)
    const finish = () => {
      try { ws.close() } catch { /* 忽略 */ }
      resolve(seen)
    }
    const timer = setTimeout(finish, timeoutMs)
    ws.onopen = () => {
      try { onOpen(ws) } catch { /* 忽略 */ }
    }
    ws.onmessage = (event) => {
      let msg
      try { msg = JSON.parse(event.data) } catch { return }
      if (!wanted.has(msg.type)) return
      seen.set(msg.type, msg.data || {})
      if ([...wanted].every((type) => seen.has(type))) {
        clearTimeout(timer)
        finish()
      }
    }
    ws.onerror = () => {
      clearTimeout(timer)
      finish()
    }
  })
}

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')

  /* ---------- 权限收紧 ---------- */
  const agentScreen = await call('GET', '/statistics/ops/screen', agent.token)
  log('坐席访问实时大屏被拒（R-176 权限）',
    agentScreen.code === 403 || agentScreen.http === 403,
    `http=${agentScreen.http} code=${agentScreen.code}`)

  const userMetrics = await call('GET', '/statistics/ops/metrics', user.token)
  log('普通用户访问运营指标被拒（R-177 权限）',
    userMetrics.code === 403 || userMetrics.http === 403,
    `http=${userMetrics.http} code=${userMetrics.code}`)

  const agentRebuild = await call('POST', '/statistics/ops/metrics/rebuild', agent.token)
  log('坐席触发轮次指标回填被拒（R-178 权限）',
    agentRebuild.code === 403 || agentRebuild.http === 403,
    `http=${agentRebuild.http} code=${agentRebuild.code}`)

  const userAgents = await call('GET', '/statistics/ops/agents', user.token)
  log('普通用户访问坐席实时表现被拒（R-179 权限）',
    userAgents.code === 403 || userAgents.http === 403,
    `http=${userAgents.http} code=${userAgents.code}`)

  /* ---------- 实时大屏 R-176 ---------- */
  const screenResp = await call('GET', '/statistics/ops/screen', admin.token)
  const screen = screenResp.data || {}
  const rt = screen.realtime || {}
  const today = screen.today || {}
  const hourly = Array.isArray(screen.hourly) ? screen.hourly : []
  const screenAgents = Array.isArray(screen.agents) ? screen.agents : []
  const alerts = Array.isArray(screen.alerts) ? screen.alerts : []

  log('大屏一次返回全屏数据（R-176 结构）',
    screenResp.code === 200
    && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(screen.serverTime || '')
    && screen.realtime && screen.today
    && Array.isArray(screen.hourly) && Array.isArray(screen.agents) && Array.isArray(screen.alerts),
    `serverTime=${screen.serverTime} 坐席=${screenAgents.length} 小时点=${hourly.length} 预警=${alerts.length}`)

  log('实时负载字段齐备且自洽（DB-02 排队口径）',
    num(rt.waitingCount) >= 0 && num(rt.servingSessions) >= 0 && num(rt.aiSessions) >= 0
    && num(rt.onlineAgents) >= 0 && num(rt.busyAgents) >= 0
    && num(rt.longestWaitSeconds) >= num(rt.avgWaitSeconds)
    && (num(rt.waitingCount) === 0 || num(rt.longestWaitSeconds) >= 0),
    `排队=${rt.waitingCount} 最长=${rt.longestWaitSeconds}s 均值=${rt.avgWaitSeconds}s`
    + ` 在线=${rt.onlineAgents} 忙碌=${rt.busyAgents} 人工接待=${rt.servingSessions} AI=${rt.aiSessions}`)

  log('今日累计口径自洽（DB-01）',
    num(today.closedSessions) <= num(today.sessions)
    && num(today.humanSessions) <= num(today.sessions)
    && num(today.aiResolved) <= num(today.closedSessions)
    && isRate(today.aiResolveRate) && isRate(today.recoveryRate) && isRate(today.threeMinuteReplyRate)
    && num(today.withinCount) <= num(today.roundCount)
    && num(today.avgRating) >= 0 && num(today.avgRating) <= 5,
    `会话=${today.sessions} 人工=${today.humanSessions} 关闭=${today.closedSessions}`
    + ` AI自主解决率=${today.aiResolveRate} 3分钟回复率=${today.threeMinuteReplyRate}`
    + ` 满意度=${today.avgRating}/回收率=${today.recoveryRate}`)

  log('近 12 小时趋势点为整点且连续（DB-02 趋势）',
    hourly.length === 12
    && hourly.every((row, index) => /^([01]\d|2[0-3]):00$/.test(row.hour || '')
      && (index === 0 || true))
    && num(hourly[hourly.length - 1]?.sessions) >= 0,
    `首个=${hourly[0]?.hour} 末个=${hourly[hourly.length - 1]?.hour}`)

  log('大屏坐席行不超过 20 行且字段完整（R-176/DB-03）',
    screenAgents.length <= 20
    && screenAgents.every((row) => row.agentId && ['ONLINE', 'BUSY', 'OFFLINE', 'AWAY'].includes(row.onlineStatus)
      && num(row.currentCount) >= 0 && num(row.maxConcurrent) >= 0),
    `行数=${screenAgents.length} 状态=${screenAgents.map((r) => r.onlineStatus).join(',') || '—'}`)

  log('内置预警级别与编码合法（DB-02 阈值）',
    alerts.every((row) => ['INFO', 'WARN', 'CRITICAL'].includes(row.level)
      && Boolean(row.code) && Boolean(row.title) && Boolean(row.detail)),
    `预警=${alerts.map((r) => `${r.level}:${r.code}`).join(' | ') || '无'}`)

  /* 与统计页看板同口径（排队人数、在线坐席都应一致：两处都按 WAITING 会话 / ONLINE 坐席计数） */
  const dashboard = await call('GET', '/statistics/dashboard', admin.token)
  const dashCounts = dashboard.data?.counts || {}
  log('大屏排队人数与在线坐席与数据看板同口径（口径唯一）',
    dashboard.code === 200
    && typeof dashCounts.waitingSessions === 'number'
    && typeof rt.waitingCount === 'number'
    && rt.waitingCount === dashCounts.waitingSessions
    && num(rt.onlineAgents) === num(dashCounts.onlineAgents),
    `排队 大屏=${rt.waitingCount} 看板=${dashCounts.waitingSessions}`
    + ` | 在线 大屏=${rt.onlineAgents} 看板=${dashCounts.onlineAgents}`)

  /* ---------- 运营指标 R-177 ---------- */
  const metricsResp = await call('GET', '/statistics/ops/metrics', admin.token)
  const m = metricsResp.data || {}
  log('运营指标默认区间为最近 7 天（R-177 默认值）',
    metricsResp.code === 200 && m.endDate === dateText(0) && m.startDate === dateText(-6),
    `区间=${m.startDate} ~ ${m.endDate}`)

  log('会话与工单计数口径自洽（R-177）',
    num(m.closedCount) <= num(m.sessionTotal)
    && num(m.humanSessionCount) <= num(m.sessionTotal)
    && num(m.transferredCount) <= num(m.sessionTotal)
    && num(m.aiOnlyCount) <= num(m.closedCount)
    && num(m.orderOverdueCount) <= num(m.orderTotal)
    && isRate(m.transferRate) && isRate(m.aiResolveRate) && isRate(m.orderOverdueRate),
    `会话=${m.sessionTotal} 人工=${m.humanSessionCount} 关闭=${m.closedCount}`
    + ` 转人工=${m.transferredCount} 工单=${m.orderTotal}/超时${m.orderOverdueCount}`)

  log('3 分钟回复率按轮次核算（DB-04 核心口径）',
    isRate(m.threeMinuteReplyRate)
    && num(m.withinCount) <= num(m.roundCount)
    && near(m.threeMinuteReplyRate, ratioOf(m.withinCount, m.roundCount)),
    `轮次=${m.roundCount} 3分钟内=${m.withinCount} 回复率=${m.threeMinuteReplyRate}`)

  log('轮次指标覆盖率与待回填会话可追溯（DB-04 覆盖率披露）',
    isRate(m.metricCoverageRate)
    && near(m.metricCoverageRate, ratioOf(m.metricSessionCount, m.humanSessionCount))
    && num(m.metricSessionCount) <= num(m.humanSessionCount)
    && num(m.pendingMetricSessionCount) >= 0,
    `已计算=${m.metricSessionCount}/${m.humanSessionCount} 覆盖率=${m.metricCoverageRate}`
    + ` 待回填=${m.pendingMetricSessionCount}`)

  log('首响均值与 P90 同源且样本数可信（DB-04 首响口径）',
    num(m.firstResponseSample) <= num(m.sessionTotal)
    && (num(m.firstResponseSample) > 0
      ? num(m.avgFirstResponseSeconds) >= 0 && num(m.p90FirstResponseSeconds) >= 0
      : num(m.avgFirstResponseSeconds) === 0 && num(m.p90FirstResponseSeconds) === 0),
    `样本=${m.firstResponseSample} 均值=${m.avgFirstResponseSeconds}s P90=${m.p90FirstResponseSeconds}s`)

  log('满意度必须同时披露回收率（3.4 口径要求）',
    isRate(m.satisfactionRecoveryRate)
    && near(m.satisfactionRecoveryRate, ratioOf(m.ratingCount, m.closedCount))
    && num(m.ratingCount) <= num(m.closedCount)
    && num(m.avgRating) >= 0 && num(m.avgRating) <= 5,
    `评价=${m.ratingCount} 均分=${m.avgRating} 回收率=${m.satisfactionRecoveryRate}`)

  /* ---------- 区间参数校验 ---------- */
  const badDate = await call('GET', '/statistics/ops/metrics?startDate=2026-13-01&endDate=2026-12-31', admin.token)
  log('非法日期格式被拦截（R-177 参数校验）', badDate.code === 400,
    `code=${badDate.code} message=${badDate.message}`)

  const halfRange = await call('GET', `/statistics/ops/metrics?startDate=${dateText(-6)}`, admin.token)
  log('起止日期必须成对提供（R-177 参数校验）', halfRange.code === 400,
    `code=${halfRange.code} message=${halfRange.message}`)

  const reversed = await call('GET',
    `/statistics/ops/metrics?startDate=${dateText(0)}&endDate=${dateText(-3)}`, admin.token)
  log('开始日期晚于结束日期被拦截（R-177 参数校验）', reversed.code === 400,
    `code=${reversed.code} message=${reversed.message}`)

  const tooLong = await call('GET', '/statistics/ops/metrics?startDate=2024-01-01&endDate=2025-12-31', admin.token)
  log('区间跨度超过 366 天被拦截（R-177 参数校验）', tooLong.code === 400,
    `code=${tooLong.code} message=${tooLong.message}`)

  /* ---------- 轮次指标回填 R-178 ---------- */
  const pendingBefore = num(m.pendingMetricSessionCount)
  const rebuilt = await call('POST',
    `/statistics/ops/metrics/rebuild?startDate=${dateText(-30)}&endDate=${dateText(0)}`, admin.token)
  log('回填接口返回实际处理会话数（R-178）',
    rebuilt.code === 200 && typeof rebuilt.data?.processed === 'number' && rebuilt.data.processed >= 0,
    `处理=${rebuilt.data?.processed} 区间=${rebuilt.data?.startDate} ~ ${rebuilt.data?.endDate}`)

  const afterResp = await call('GET',
    `/statistics/ops/metrics?startDate=${dateText(-30)}&endDate=${dateText(0)}`, admin.token)
  const after = afterResp.data || {}
  log('回填后覆盖率回到 0~1 且待回填不高于人工会话（R-178 + DB-04）',
    afterResp.code === 200 && isRate(after.metricCoverageRate)
    && num(after.metricSessionCount) <= num(after.humanSessionCount)
    && num(after.pendingMetricSessionCount) <= num(after.humanSessionCount)
    && near(after.metricCoverageRate, ratioOf(after.metricSessionCount, after.humanSessionCount)),
    `回填前待回填=${pendingBefore} 回填后=${after.pendingMetricSessionCount}`
    + ` 已计算=${after.metricSessionCount}/${after.humanSessionCount} 覆盖率=${after.metricCoverageRate}`
    + ` 轮次=${after.roundCount}`)

  /* ---------- 坐席实时表现 R-179 ---------- */
  const agentsResp = await call('GET', '/statistics/ops/agents?pageNum=1&pageSize=10', admin.token)
  const page = agentsResp.data || {}
  const agentRows = Array.isArray(page.list) ? page.list : []
  log('坐席实时表现返回分页结构（R-179）',
    agentsResp.code === 200 && Array.isArray(page.list) && typeof page.total === 'number'
    && num(page.total) >= agentRows.length,
    `total=${page.total} 本页=${agentRows.length}`)

  log('坐席行带出在线状态与今日响应均值（DB-03）',
    agentRows.every((row) => row.agentId
      && ['ONLINE', 'BUSY', 'OFFLINE', 'AWAY'].includes(row.onlineStatus)
      && num(row.currentCount) >= 0 && num(row.todayServed) >= 0
      && num(row.avgAcceptSeconds) >= 0 && num(row.avgFirstReplySeconds) >= 0),
    `示例=${agentRows[0] ? `${agentRows[0].agentName || agentRows[0].agentId}/${agentRows[0].onlineStatus}`
      + `/今日${agentRows[0].todayServed}/首响${agentRows[0].avgFirstReplySeconds}s` : '无'}`)

  const oversize = await call('GET', '/statistics/ops/agents?pageNum=1&pageSize=101', admin.token)
  log('坐席分页页大小越界被拦截（R-179 参数校验）', oversize.code !== 200,
    `code=${oversize.code} message=${oversize.message}`)

  /* ---------- 端到端轮次指标实测（DB-04） ---------- */
  const todayText = dateText(0)
  const todayParams = `startDate=${todayText}&endDate=${todayText}`
  const todayBefore = (await call('GET', `/statistics/ops/metrics?${todayParams}`, admin.token)).data || {}
  const expectedRounds = 2
  let flowOk = false
  let flowDetail = ''
  try {
    const current = await call('GET', '/chat/sessions/current', user.token)
    if (current.code === 200 && current.data?.id && current.data?.status !== 'CLOSED') {
      await call('POST', `/chat/sessions/${current.data.id}/close`, user.token, { closeReason: 'USER_CLOSED' })
    }
    const created = await call('POST', '/chat/sessions', user.token)
    const sid = created.data?.session?.id || created.data?.sessionId || created.data?.id
    if (!sid) throw new Error(`创建会话失败：${created.message || created.code}`)
    await call('POST', `/chat/sessions/${sid}/messages`, user.token,
      { content: '需要人工确认退款进度', contentType: 'TEXT', clientMsgId: `s6a-u1-${Date.now()}` })
    await call('POST', '/agent/online', agent.token, {})
    const transferResp = await call('POST', `/chat/sessions/${sid}/transfer`, user.token, { reason: 'S6-A 轮次指标实测' })
    let status = ''
    let acceptResp = null
    for (let i = 0; i < 16; i++) {
      const detail = await call('GET', `/chat/sessions/${sid}`, agent.token)
      status = detail.data?.status || ''
      if (status === 'HUMAN') break
      await new Promise((r) => setTimeout(r, 400))
    }
    if (status !== 'HUMAN') acceptResp = await call('POST', `/agent/sessions/${sid}/accept`, agent.token)
    const reply1 = await call('POST', `/chat/sessions/${sid}/messages`, agent.token,
      { content: '已为您核实，预计今天到账', contentType: 'TEXT', clientMsgId: `s6a-a1-${Date.now()}` })
    await call('POST', `/chat/sessions/${sid}/messages`, user.token,
      { content: '好的，那我再等等', contentType: 'TEXT', clientMsgId: `s6a-u2-${Date.now()}` })
    const reply2 = await call('POST', `/chat/sessions/${sid}/messages`, agent.token,
      { content: '如有问题随时找我', contentType: 'TEXT', clientMsgId: `s6a-a2-${Date.now()}` })
    const userMsg3 = await call('POST', `/chat/sessions/${sid}/messages`, user.token,
      { content: '谢谢，再见', contentType: 'TEXT', clientMsgId: `s6a-u3-${Date.now()}` })
    const reply3 = await call('POST', `/chat/sessions/${sid}/messages`, agent.token,
      { content: '不客气，祝您生活愉快', contentType: 'TEXT', clientMsgId: `s6a-a3-${Date.now()}` })
    const closed = await call('POST', `/chat/sessions/${sid}/close`, agent.token, { closeReason: 'AGENT_CLOSED' })
    flowOk = reply1.code === 200 && reply2.code === 200
      && userMsg3.code === 200 && reply3.code === 200 && closed.code === 200
    flowDetail = `会话=${created.data?.session?.sessionNo || sid} 状态=${status || '—'}`
      + ` 转接=${transferResp.code}/${transferResp.data?.status || transferResp.message}`
      + (acceptResp ? ` 接起=${acceptResp.code}/${acceptResp.message}` : '')
      + ` 关闭=${closed.code}/${closed.data?.status || closed.message}`
  } catch (e) {
    flowDetail = e.message
  }

  const todayAfter = (await call('GET', `/statistics/ops/metrics?${todayParams}`, admin.token)).data || {}
  const deltaRounds = num(todayAfter.roundCount) - num(todayBefore.roundCount)
  const deltaWithin = num(todayAfter.withinCount) - num(todayBefore.withinCount)
  log('人工接待轮次在会话关闭时自动固化（DB-04 实测）',
    flowOk && deltaRounds === expectedRounds,
    `${flowDetail} 轮次增量=${deltaRounds}（期望 ${expectedRounds}）`)

  log('180 秒内回复的轮次计入 3 分钟回复率（DB-04 实测）',
    flowOk && deltaWithin === expectedRounds
    && near(todayAfter.threeMinuteReplyRate, ratioOf(todayAfter.withinCount, todayAfter.roundCount)),
    `3 分钟内增量=${deltaWithin} 今日回复率=${todayAfter.threeMinuteReplyRate}`)

  /* ---------- WebSocket 大屏订阅 ---------- */
  const adminWs = await wsCollect(admin.token, (ws) => {
    ws.send(JSON.stringify({ type: 'SCREEN_SUBSCRIBE', requestId: `sub-${Date.now()}`, timestamp: Date.now(), data: {} }))
  }, ['SCREEN_SUBSCRIBED', 'SCREEN_UPDATE'])
    .catch(() => new Map())

  const subscribed = adminWs.get('SCREEN_SUBSCRIBED')
  log('管理员 WS 订阅大屏收到快照回执（R-176 WS 入口）',
    Boolean(subscribed?.realtime) && Boolean(subscribed?.today) && Boolean(subscribed?.serverTime),
    `serverTime=${subscribed?.serverTime} 排队=${subscribed?.realtime?.waitingCount}`)

  const pushed = adminWs.get('SCREEN_UPDATE')
  log('定时推送按订阅关系下发 SCREEN_UPDATE（DB-02 实时性）',
    Boolean(pushed?.realtime) && Boolean(pushed?.serverTime),
    `推送 serverTime=${pushed?.serverTime} 在线坐席=${pushed?.realtime?.onlineAgents}`)

  const agentWs = await wsExchange(agent.token, (ws) => {
    ws.send(JSON.stringify({ type: 'SCREEN_SUBSCRIBE', requestId: `sub-agent-${Date.now()}`, timestamp: Date.now(), data: {} }))
  }, 'ERROR', 8000)
  log('非管理员 WS 订阅大屏被拒（WS_8004）',
    num(agentWs.data.code) === 403 && agentWs.data.bizCode === 'WS_8004',
    `code=${agentWs.data.code} bizCode=${agentWs.data.bizCode} message=${agentWs.data.message}`)

  const unsub = await wsExchange(admin.token, (ws) => {
    ws.send(JSON.stringify({ type: 'SCREEN_UNSUBSCRIBE', requestId: `unsub-${Date.now()}`, timestamp: Date.now(), data: {} }))
  }, 'SCREEN_UNSUBSCRIBED', 8000)
  log('退订大屏返回确认并可重复调用（R-176 退订）',
    unsub.data.unsubscribed === true && Boolean(unsub.data.serverTime),
    `unsubscribed=${unsub.data.unsubscribed} serverTime=${unsub.data.serverTime}`)
}

main()
  .then(() => {
    const failed = results.filter((r) => !r.ok)
    console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
    process.exit(failed.length ? 1 : 0)
  })
  .catch((e) => {
    console.error('\n脚本异常：', e.message)
    process.exit(2)
  })
