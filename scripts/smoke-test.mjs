/**
 * 智客通核心流程冒烟脚本（验收用，零依赖：node scripts/smoke-test.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/demo/02_demo_data.sql，
 *       且 agent01 当前无进行中的人工会话。
 * 覆盖：登录 → AI 知识库命中 → 转人工排队 → 坐席上线自动接入 → 回复/结束 → 用户评分
 *       工单 创建 → 分配 → 受理 → 进展 → 解决 → 评分 → 关闭 → 重开
 *       非法状态流转拦截、越权访问拦截
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
  try { json = await res.json() } catch { /* 空响应 */ }
  return { http: res.status, ...(json || {}) }
}

async function login(username, password = '123456') {
  const r = await call('POST', '/auth/login', null, { username, password })
  if (r.code !== 200) throw new Error(`登录失败 ${username}: ${r.http} ${r.message}`)
  return r.data
}

/** 消息幂等键：后端要求 16-64 位字母/数字/下划线/连字符 */
const msgId = () => `smoke-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')
  log('三种角色登录', true, `admin=${admin.user.nickname} agent=${agent.user.nickname} user=${user.user.nickname}`)

  /* ---------- 管理端数据面 ---------- */
  const plan = await call('GET', '/plan', admin.token)
  log('管理端套餐用量 /api/plan', plan.code === 200 && Array.isArray(plan.data?.items),
    `套餐=${plan.data?.planName} 指标=${plan.data?.items?.length} 档位=${plan.data?.plans?.length}`)

  const dash = await call('GET', '/statistics/overview', admin.token)
  log('管理端看板 /api/statistics/overview', dash.code === 200 && Number(dash.data?.sessionTotal) >= 1,
    `会话总数=${dash.data?.sessionTotal ?? 'n/a'} 工单总数=${dash.data?.workOrderTotal ?? 'n/a'} 当前排队=${dash.data?.sessionWaiting ?? 'n/a'}`)

  const orders = await call('GET', '/workorders?pageNum=1&pageSize=5', admin.token)
  log('工单分页（中文演示数据）', orders.code === 200 && orders.data?.total >= 14,
    `total=${orders.data?.total} 首条=${orders.data?.list?.[0]?.title}`)

  const kb = await call('GET', '/kb/entries?pageNum=1&pageSize=5', admin.token)
  log('知识库分页', kb.code === 200 && kb.data?.total === 20,
    `total=${kb.data?.total} 首条=${kb.data?.list?.[0]?.question}`)

  const agents = await call('GET', '/agent/list', admin.token)
  log('坐席列表', agents.code === 200, `坐席数=${Array.isArray(agents.data) ? agents.data.length : 'n/a'}`)

  const forbidden = await call('GET', '/admin/users?pageNum=1&pageSize=5', user.token)
  log('普通用户越权访问用户管理被拒', forbidden.http === 403 || forbidden.code === 403,
    `http=${forbidden.http} code=${forbidden.code}`)

  /* ---------- 对话闭环 ---------- */
  // 先让坐席离线，确保能验证“无空闲坐席 → 排队”链路
  const offline = await call('POST', '/agent/offline', agent.token)
  log('坐席置为离线', offline.code === 200, `status=${offline.data?.onlineStatus ?? 'OFFLINE'}`)

  let sessionId
  const current = await call('GET', '/chat/sessions/current', user.token)
  if (current.code === 200 && current.data?.id) {
    sessionId = current.data.id
    console.log(`INFO  复用进行中会话 #${sessionId}（${current.data.status}），先结束以便验证新会话`)
    const closed = await call('POST', `/chat/sessions/${sessionId}/close`, user.token, { closeReason: 'USER_CLOSED' })
    log('用户取消排队/结束进行中会话', closed.code === 200, `status=${closed.data?.status}`)
  }
  const created = await call('POST', '/chat/sessions', user.token)
  if (created.code !== 200) throw new Error('创建会话失败: ' + JSON.stringify(created))
  sessionId = created.data.session?.id || created.data.sessionId || created.data.id
  log('用户创建会话', Boolean(sessionId), `sessionId=${sessionId} status=${created.data.session?.status}`)

  const ask = await call('POST', `/chat/sessions/${sessionId}/messages`, user.token,
    { content: '怎么申请退款', contentType: 'TEXT', clientMsgId: msgId() })
  const reply = ask.data?.aiReply
  log('AI 命中知识库（退款词条）', ask.code === 200 && Number(reply?.matchedKbId) === 13,
    `matchedKbId=${reply?.matchedKbId} 置信度=${reply?.aiConfidence} 回复前 24 字=${(reply?.content || '').slice(0, 24)}`)

  const ask2 = await call('POST', `/chat/sessions/${sessionId}/messages`, user.token,
    { content: '退款一般几天能到账', contentType: 'TEXT', clientMsgId: msgId() })
  log('AI 命中第二问（到账时效）', ask2.code === 200 && Number(ask2.data?.aiReply?.matchedKbId) === 14,
    `matchedKbId=${ask2.data?.aiReply?.matchedKbId}`)

  const transfer = await call('POST', `/chat/sessions/${sessionId}/transfer`, user.token,
    { reason: '退款进度需要人工核实' })
  log('用户请求转人工（无空闲坐席进入排队）', transfer.code === 200 && transfer.data?.status === 'WAITING'
    && Number(transfer.data?.queuePosition) >= 1,
    `status=${transfer.data?.status} queuePosition=${transfer.data?.queuePosition ?? '-'} queueSize=${transfer.data?.queueSize ?? '-'}`)

  const queue = await call('GET', '/agent/queue?pageNum=1&pageSize=20', agent.token)
  const queueItem = (queue.data?.list || []).find((item) => String(item.sessionId ?? item.id) === String(sessionId))
  log('坐席看到等待队列', queue.code === 200 && Boolean(queueItem) && Number(queueItem?.queuePosition) >= 1,
    `队列长度=${queue.data?.total ?? queue.data?.list?.length} 排位=${queueItem?.queuePosition ?? '-'} 等待秒=${queueItem?.waitSeconds ?? '-'}`)

  const online = await call('POST', '/agent/online', agent.token, {})
  log('坐席上线', online.code === 200, `status=${online.data?.onlineStatus ?? 'ONLINE'}`)
  // 上线后自动分配（可能由定时/事件触发），最多等 6 秒；未自动接入则手动接入
  let sessionStatus = ''
  for (let i = 0; i < 12; i++) {
    const detail = await call('GET', `/chat/sessions/${sessionId}`, agent.token)
    sessionStatus = detail.data?.status || ''
    if (sessionStatus === 'HUMAN') break
    await new Promise((r) => setTimeout(r, 500))
  }
  log('坐席上线后自动接入排队会话', sessionStatus === 'HUMAN', `status=${sessionStatus}`)
  if (sessionStatus !== 'HUMAN') {
    const accept = await call('POST', `/agent/sessions/${sessionId}/accept`, agent.token)
    log('坐席手动接入会话', accept.code === 200 && accept.data?.status === 'HUMAN',
      `status=${accept.data?.status}`)
  }

  const agentMsg = await call('POST', `/chat/sessions/${sessionId}/messages`, agent.token,
    { content: '您好，我是客服张客服，已收到您的退款问题，正在为您核实。', contentType: 'TEXT', clientMsgId: msgId() })
  log('坐席回复消息', agentMsg.code === 200, `senderType=${agentMsg.data?.message?.senderType}`)

  const userMsg = await call('POST', `/chat/sessions/${sessionId}/messages`, user.token,
    { content: '好的，麻烦了', contentType: 'TEXT', clientMsgId: msgId() })
  log('用户追问（人工态不再触发 AI 回复）', userMsg.code === 200 && !userMsg.data?.aiReply,
    `code=${userMsg.code} aiReply=${userMsg.data?.aiReply ? '有' : '无'}`)

  const close = await call('POST', `/chat/sessions/${sessionId}/close`, agent.token,
    { closeReason: 'AGENT_CLOSED' })
  log('坐席结束会话', close.code === 200 && close.data?.status === 'CLOSED', `status=${close.data?.status}`)

  const rating = await call('POST', `/chat/sessions/${sessionId}/rating`, user.token,
    { rating: 5, ratingComment: '响应很快，问题已解决' })
  log('用户提交会话评分', rating.code === 200, `rating=${rating.data?.rating ?? 5}`)

  /* ---------- 工单闭环 ---------- */
  const wo = await call('POST', '/workorders', user.token, {
    title: '[冒烟] 退款未到账需要人工核实',
    content: '订单支付后申请退款，超过 5 个工作日仍未到账，请协助核查支付渠道流水。',
    category: '售后问题',
    priority: 'P1',
    source: 'MANUAL',
    sessionId
  })
  const woId = wo.data?.id
  log('用户创建工单', wo.code === 200 && Boolean(woId), `id=${woId} 状态=${wo.data?.status} 编号=${wo.data?.orderNo}`)

  const assign = await call('POST', `/workorders/${woId}/assign`, admin.token,
    { assigneeId: agent.user.userId ?? agent.user.id, remark: '指派给张客服核实退款流水' })
  log('管理员分配工单', assign.code === 200 && assign.data?.status === 'ASSIGNED',
    `status=${assign.data?.status} 处理人=${assign.data?.assigneeName}`)

  const acceptWo = await call('POST', `/workorders/${woId}/accept`, agent.token,
    { remark: '坐席已受理' })
  log('坐席受理工单', acceptWo.code === 200 && acceptWo.data?.status === 'PROCESSING',
    `status=${acceptWo.data?.status}`)

  const processWo = await call('POST', `/workorders/${woId}/process`, agent.token,
    { remark: '已向支付渠道提交查询申请' })
  log('坐席提交处理进展', processWo.code === 200, `status=${processWo.data?.status}`)

  const resolveWo = await call('POST', `/workorders/${woId}/resolve`, agent.token,
    { remark: '退款已原路退回，请查收' })
  log('坐席解决工单', resolveWo.code === 200 && resolveWo.data?.status === 'RESOLVED',
    `status=${resolveWo.data?.status}`)

  const rateWo = await call('POST', `/workorders/${woId}/rating`, user.token,
    { rating: 5, ratingComment: '处理及时' })
  log('用户评价工单', rateWo.code === 200, `rating=${rateWo.data?.rating ?? 5}`)

  const closeWo = await call('POST', `/workorders/${woId}/close`, user.token, { remark: '问题已解决，确认关闭' })
  log('用户确认关闭工单', closeWo.code === 200 && closeWo.data?.status === 'CLOSED',
    `status=${closeWo.data?.status}`)

  const closeAgain = await call('POST', `/workorders/${woId}/close`, admin.token, { remark: '重复关闭' })
  log('终态工单重复关闭被拦截', closeAgain.code !== 200, `code=${closeAgain.code} message=${closeAgain.message}`)

  const reopen = await call('POST', `/workorders/${woId}/reopen`, user.token, { reason: '退款仍未到账' })
  log('已关闭工单可由创建人重开（REOPENED）', reopen.code === 200 && reopen.data?.status === 'REOPENED',
    `code=${reopen.code} status=${reopen.data?.status}`)

  const logs = await call('GET', `/workorders/${woId}/logs?pageNum=1&pageSize=50`, admin.token)
  const actions = (logs.data?.list || []).map((item) => item.action)
  const expected = ['CREATE', 'ASSIGN', 'ACCEPT', 'PROCESS', 'RESOLVE', 'CLOSE', 'REOPEN']
  const missing = expected.filter((a) => !actions.includes(a))
  const rated = (logs.data?.list || []).some((item) => (item.remark || '').includes('工单评分'))
  log('工单流转日志完整', logs.code === 200 && missing.length === 0 && rated,
    `logs=${logs.data?.total} 缺失=${missing.join(',') || '无'} 评分日志=${rated ? '有' : '无'}`)

  const detail = await call('GET', `/workorders/${woId}`, admin.token)
  log('工单详情可查', detail.code === 200 && detail.data?.workOrder?.id === woId,
    `SLA 截止=${detail.data?.workOrder?.slaDeadline ?? 'n/a'}`)

  /* ---------- 汇总 ---------- */
  const failed = results.filter((r) => !r.ok)
  console.log('\n===== 冒烟汇总 =====')
  console.log(`总计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
  if (failed.length) {
    console.log('失败项：' + failed.map((r) => r.name).join(' | '))
    process.exitCode = 1
  }
}

main().catch((e) => {
  console.error('SMOKE_ERROR', e)
  process.exitCode = 2
})
