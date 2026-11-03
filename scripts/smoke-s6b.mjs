/**
 * 智客通 S6-B 告警规则化与监听强插冒烟脚本（验收用，零依赖：node scripts/smoke-s6b.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/01_schema.sql、sql/02_data.sql
 *       与 sql/19_s6b_alert_monitor.sql（alert_rule 预置 9 条内置规则）。
 * 覆盖：告警规则 CRUD 与指标推导（R-180~R-184）→ 坐席个人规则与越权拦截（R-190~R-194）
 *       → 手动扫描（R-187）→ 告警分页/概览（R-185/R-186）→ 敏感词命中生成告警、冷却抑制、
 *       确认与关闭（R-188/R-189）→ 组长监听列表/详情（R-199/R-200）→ 强插发言（R-201）
 *       → 强制接管（R-202）→ 派单候选（R-204）与强制派单（R-203）→ 一次性解决率口径（DB-05）。
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
    body: body === undefined ? null : JSON.stringify(body)
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

/** 兼容数组与分页两种返回结构 */
const listOf = (resp) => (Array.isArray(resp.data)
  ? resp.data
  : (resp.data?.list || resp.data?.records || []))

/** 关掉当前访客会话，保证后续建会话拿到干净的 AI 会话 */
async function ensureIdleSession(userToken) {
  const current = await call('GET', '/chat/sessions/current', userToken)
  if (current.code === 200 && current.data?.id) {
    await call('POST', `/chat/sessions/${current.data.id}/close`, userToken, { closeReason: 'USER_CLOSED' })
  }
}

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const agent2 = await login('agent02')
  const user = await login('user01')
  const agentId = agent.user.userId ?? agent.user.id
  const agent2Id = agent2.user.userId ?? agent2.user.id
  const adminId = admin.user.userId ?? admin.user.id
  const stamp = Date.now()
  const cleanupRuleIds = []

  log('四种角色登录', true,
    `admin=${admin.user.nickname} agent=${agent.user.nickname} agent2=${agent2.user.nickname} user=${user.user.nickname}`)

  /* ================================================================
     一、告警规则管理（R-180 ~ R-184）
     ================================================================ */
  const denyAgent = await call('GET', '/admin/alert-rules', agent.token)
  log('坐席访问管理端告警规则被拒（R-180 权限）', denyAgent.http === 403 || denyAgent.code === 403,
    `http=${denyAgent.http} code=${denyAgent.code}`)

  const builtin = await call('GET', '/admin/alert-rules', admin.token)
  const builtinList = listOf(builtin)
  const builtinRules = builtinList.filter((r) => r.builtin)
  log('管理员规则列表返回内置规则（R-180）', builtin.code === 200 && builtinRules.length >= 9,
    `规则数=${builtinList.length} 内置=${builtinRules.length}`)
  log('规则返回指标编码与名称（R-180 字段）',
    builtinRules.every((r) => Boolean(r.metricCode) && Boolean(r.metricName) && Boolean(r.thresholdUnit)),
    `示例=${builtinRules[0]?.metricCode}/${builtinRules[0]?.metricName}/${builtinRules[0]?.thresholdUnit}`)

  const createBacklog = await call('POST', '/admin/alert-rules', admin.token, {
    ruleName: `[冒烟]排队积压-${stamp}`,
    metricCode: 'QUEUE_BACKLOG',
    ruleType: 'SESSION',
    severity: 'WARN',
    thresholdValue: 3,
    cooldownMinutes: 5,
    notifyRole: 'ADMIN',
    description: '冒烟：排队人数达到 3 人告警',
    sort: 90,
    status: 1
  })
  const backlogRuleId = createBacklog.data?.id
  cleanupRuleIds.push(backlogRuleId)
  log('新增告警规则且类型由指标推导（R-181）', createBacklog.code === 200 && Boolean(backlogRuleId)
    && createBacklog.data?.ruleType === 'QUEUE' && createBacklog.data?.thresholdUnit === 'COUNT'
    && createBacklog.data?.builtin === false,
    `id=${backlogRuleId} 类型=${createBacklog.data?.ruleType} 单位=${createBacklog.data?.thresholdUnit} 编码=${createBacklog.data?.ruleCode}`)

  const dupRule = await call('POST', '/admin/alert-rules', admin.token, {
    ruleName: `[冒烟]排队积压-${stamp}`,
    metricCode: 'QUEUE_BACKLOG',
    ruleType: 'QUEUE',
    thresholdValue: 2,
    status: 1
  })
  log('租户级重名规则被拦截（R-181 唯一性）', dupRule.code !== 200,
    `code=${dupRule.code} message=${dupRule.message}`)

  const badMetric = await call('POST', '/admin/alert-rules', admin.token, {
    ruleName: `[冒烟]非法指标-${stamp}`,
    metricCode: 'NOT_A_METRIC',
    ruleType: 'QUEUE',
    thresholdValue: 1,
    status: 1
  })
  log('非法指标编码被拦截（R-181 校验）', badMetric.code !== 200,
    `code=${badMetric.code} message=${badMetric.message}`)

  const noKeywords = await call('POST', '/admin/alert-rules', admin.token, {
    ruleName: `[冒烟]敏感词缺关键词-${stamp}`,
    metricCode: 'SENSITIVE_WORD',
    ruleType: 'SESSION',
    thresholdValue: 1,
    status: 1
  })
  log('敏感词规则缺关键词被拦截（R-181 校验）', noKeywords.code !== 200,
    `code=${noKeywords.code} message=${noKeywords.message}`)

  const badRating = await call('POST', '/admin/alert-rules', admin.token, {
    ruleName: `[冒烟]差评阈值越界-${stamp}`,
    metricCode: 'LOW_RATING',
    ruleType: 'SESSION',
    thresholdValue: 9,
    status: 1
  })
  log('差评阈值超出 1-5 星被拦截（R-181 校验）', badRating.code !== 200,
    `code=${badRating.code} message=${badRating.message}`)

  const updateBacklog = await call('PUT', `/admin/alert-rules/${backlogRuleId}`, admin.token, {
    ruleName: `[冒烟]排队积压-${stamp}`,
    metricCode: 'QUEUE_BACKLOG',
    thresholdValue: 4,
    severity: 'CRITICAL',
    cooldownMinutes: 10,
    description: '冒烟：阈值改为 4 人',
    status: 1
  })
  log('修改告警规则阈值与严重程度（R-182）', updateBacklog.code === 200
    && updateBacklog.data?.thresholdValue === 4 && updateBacklog.data?.severity === 'CRITICAL',
    `阈值=${updateBacklog.data?.thresholdValue} 严重程度=${updateBacklog.data?.severityName}`)

  const offBacklog = await call('PUT', `/admin/alert-rules/${backlogRuleId}/status`, admin.token, { status: 0 })
  const onBacklog = await call('PUT', `/admin/alert-rules/${backlogRuleId}/status`, admin.token, { status: 1 })
  log('停用并重新启用告警规则（R-183）',
    offBacklog.data?.status === 0 && onBacklog.data?.status === 1,
    `停用=${offBacklog.data?.status} 启用=${onBacklog.data?.status}`)

  const builtinId = builtinRules[0]?.id
  const delBuiltin = await call('DELETE', `/admin/alert-rules/${builtinId}`, admin.token)
  log('内置规则不可删除（R-184）', delBuiltin.code !== 200,
    `code=${delBuiltin.code} message=${delBuiltin.message}`)

  const delBacklog = await call('DELETE', `/admin/alert-rules/${backlogRuleId}`, admin.token)
  log('删除自定义告警规则（R-184）', delBacklog.code === 200, `code=${delBacklog.code}`)

  /* ================================================================
     二、坐席个人告警规则（R-190 ~ R-194）
     ================================================================ */
  const myRules = await call('GET', '/agent/alert-rules', agent.token)
  const myRulesList = listOf(myRules)
  log('坐席可见租户级规则（R-190）', myRules.code === 200
    && myRulesList.filter((r) => r.tenantLevel).length >= 9,
    `规则数=${myRulesList.length} 租户级=${myRulesList.filter((r) => r.tenantLevel).length}`)

  const createMine = await call('POST', '/agent/alert-rules', agent.token, {
    ruleName: `[冒烟]我的待回复告警-${stamp}`,
    metricCode: 'AGENT_RESPONSE_TIMEOUT',
    thresholdValue: 120,
    severity: 'WARN',
    description: '冒烟：本人会话访客等待 120 秒未回复时提醒我',
    status: 1
  })
  const mineRuleId = createMine.data?.id
  log('坐席新增个人告警规则（R-191）', createMine.code === 200 && Boolean(mineRuleId)
    && Number(createMine.data?.ownerId) === Number(agentId) && createMine.data?.notifyRole === 'AGENT'
    && createMine.data?.ruleType === 'AGENT',
    `id=${mineRuleId} owner=${createMine.data?.ownerId} 通知=${createMine.data?.notifyRole}`)

  const editBuiltin = await call('PUT', `/agent/alert-rules/${builtinId}`, agent.token, {
    ruleName: `[冒烟]越权改内置-${stamp}`,
    metricCode: 'QUEUE_BACKLOG',
    thresholdValue: 1,
    status: 1
  })
  log('坐席修改租户级规则被拦截（R-192 归属校验）', editBuiltin.http === 403 || editBuiltin.code === 403,
    `http=${editBuiltin.http} code=${editBuiltin.code} message=${editBuiltin.message}`)

  const delMine = await call('DELETE', `/agent/alert-rules/${mineRuleId}`, agent.token)
  log('坐席删除本人规则（R-194）', delMine.code === 200, `code=${delMine.code}`)

  /* ================================================================
     三、扫描、告警中心与闭环（R-185 ~ R-189）
     ================================================================ */
  const scan1 = await call('POST', '/admin/alerts/scan', admin.token)
  log('手动执行一轮告警扫描（R-187）', scan1.code === 200 && scan1.data?.locked === true
    && Number(scan1.data?.ruleCount) >= 9,
    `规则数=${scan1.data?.ruleCount} 新增=${scan1.data?.createdCount} 复发=${scan1.data?.repeatedCount} 抑制=${scan1.data?.suppressedCount} 自动关闭=${scan1.data?.autoClosedCount}`)

  const pageAll = await call('GET', '/admin/alerts?pageNum=1&pageSize=10', admin.token)
  log('告警分页返回标准分页结构（R-185）', pageAll.code === 200
    && Array.isArray(pageAll.data?.list) && typeof pageAll.data?.total === 'number',
    `总数=${pageAll.data?.total} 本页=${pageAll.data?.list?.length}`)

  const summary0 = await call('GET', '/admin/alerts/summary', admin.token)
  log('告警概览返回待处理与规则数（R-186）', summary0.code === 200
    && typeof summary0.data?.openCount === 'number'
    && Number(summary0.data?.enabledRuleCount) >= 9,
    `待处理=${summary0.data?.openCount} 已确认=${summary0.data?.ackedCount} 近24h=${summary0.data?.last24hCount} 启用规则=${summary0.data?.enabledRuleCount}`)

  /* ---------- 构造真实告警：敏感词命中 ---------- */
  await ensureIdleSession(user.token)
  const created = await call('POST', '/chat/sessions', user.token)
  const sessionId = created.data?.session?.id || created.data?.sessionId || created.data?.id
  const keyword = `冒烟敏感词${stamp}`
  const sent = await call('POST', `/chat/sessions/${sessionId}/messages`, user.token, {
    content: `我要投诉，${keyword}`,
    contentType: 'TEXT',
    clientMsgId: `smoke-s6b-${stamp}-${Math.random().toString(36).slice(2, 10)}`
  })
  log('访客发送含敏感词的会话消息', created.code === 200 && sent.code === 200,
    `sessionId=${sessionId} code=${sent.code} ${sent.message || ''}`)

  const sensitiveRule = await call('POST', '/admin/alert-rules', admin.token, {
    ruleName: `[冒烟]敏感词拦截-${stamp}`,
    metricCode: 'SENSITIVE_WORD',
    thresholdValue: 1,
    severity: 'CRITICAL',
    cooldownMinutes: 5,
    notifyRole: 'ALL',
    keywords: `${keyword}, 其他占位词`,
    description: '冒烟：命中关键词即产生高优告警',
    status: 1
  })
  const sensitiveRuleId = sensitiveRule.data?.id
  cleanupRuleIds.push(sensitiveRuleId)
  log('新增敏感词告警规则并归一化关键词（R-181）', sensitiveRule.code === 200
    && sensitiveRule.data?.keywords === `${keyword},其他占位词`,
    `id=${sensitiveRuleId} 关键词=${sensitiveRule.data?.keywords}`)

  const scan2 = await call('POST', '/admin/alerts/scan', admin.token)
  log('扫描命中敏感词生成告警（R-187 / AS-11）', scan2.code === 200 && Number(scan2.data?.createdCount) >= 1,
    `新增=${scan2.data?.createdCount} 复发=${scan2.data?.repeatedCount} 抑制=${scan2.data?.suppressedCount}`)

  const alertPage = await call('GET', `/admin/alerts?pageNum=1&pageSize=20&keyword=${encodeURIComponent(keyword)}`,
    admin.token)
  const alertList = listOf(alertPage)
  const alert = alertList.find((a) => Number(a.sessionId) === Number(sessionId))
  log('告警落库含规则快照与目标会话（R-185）', Boolean(alert)
    && alert.metricCode === 'SENSITIVE_WORD' && alert.status === 'OPEN'
    && alert.ruleCode === sensitiveRule.data?.ruleCode,
    `id=${alert?.id} 标题=${alert?.title} 目标=${alert?.targetTypeName} 次数=${alert?.occurCount}`)

  const scan3 = await call('POST', '/admin/alerts/scan', admin.token)
  log('冷却期内重复触发被抑制且不新增事件（R-187 冷却）',
    scan3.code === 200 && Number(scan3.data?.createdCount) === 0 && Number(scan3.data?.suppressedCount) >= 1,
    `新增=${scan3.data?.createdCount} 抑制=${scan3.data?.suppressedCount}`)

  const monitorAlerts = await call('GET', `/admin/monitor/sessions/${sessionId}`, admin.token)
  log('监听详情带出该会话未闭环告警（R-200）',
    monitorAlerts.code === 200 && (monitorAlerts.data?.alerts || []).some((a) => Number(a.id) === Number(alert?.id)),
    `未闭环告警=${(monitorAlerts.data?.alerts || []).length}`)

  const ackResp = await call('PUT', `/admin/alerts/${alert?.id}/ack`, admin.token, { note: '冒烟：已接手处理' })
  log('确认告警置为 ACKED 并留痕（R-188）', ackResp.code === 200
    && ackResp.data?.status === 'ACKED' && Boolean(ackResp.data?.ackName),
    `status=${ackResp.data?.status} 确认人=${ackResp.data?.ackName} 说明=${ackResp.data?.ackNote}`)

  const closeResp = await call('PUT', `/admin/alerts/${alert?.id}/close`, admin.token)
  log('关闭告警置为 CLOSED 并记录处理耗时（R-189）', closeResp.code === 200
    && closeResp.data?.status === 'CLOSED' && Number(closeResp.data?.handleSeconds) >= 0,
    `status=${closeResp.data?.status} 耗时=${closeResp.data?.handleSeconds}s`)

  const closeAgain = await call('PUT', `/admin/alerts/${alert?.id}/close`, admin.token)
  log('重复关闭告警幂等（R-189）', closeAgain.code === 200 && closeAgain.data?.status === 'CLOSED',
    `status=${closeAgain.data?.status}`)

  const myAlerts = await call('GET', '/agent/alerts?pageNum=1&pageSize=10', agent.token)
  log('坐席端告警分页可查（R-195）', myAlerts.code === 200 && Array.isArray(myAlerts.data?.list),
    `总数=${myAlerts.data?.total}`)

  const mySummary = await call('GET', '/agent/alerts/summary', agent.token)
  log('坐席端告警概览可查（R-196）', mySummary.code === 200 && typeof mySummary.data?.openCount === 'number',
    `待处理=${mySummary.data?.openCount} 已确认=${mySummary.data?.ackedCount}`)

  /* ================================================================
     四、组长监听与强插（R-199 ~ R-204）
     ================================================================ */
  // 先让两位坐席离线，保证转人工进入排队而不是被自动接入
  await call('POST', '/agent/offline', agent.token)
  await call('POST', '/agent/offline', agent2.token)
  const transfer = await call('POST', `/chat/sessions/${sessionId}/transfer`, user.token, {
    reason: '冒烟：验证组长监听与强插'
  })
  log('会话转人工进入排队（监听前置）', transfer.code === 200 && transfer.data?.status === 'WAITING',
    `status=${transfer.data?.status} 排位=${transfer.data?.queuePosition ?? '-'}`)

  const monitorPage = await call('GET', '/admin/monitor/sessions?pageNum=1&pageSize=20&status=WAITING', admin.token)
  const monitorRow = listOf(monitorPage).find((row) => Number(row.sessionId) === Number(sessionId))
  log('监听列表列出排队中会话（R-199）', monitorPage.code === 200 && Boolean(monitorRow)
    && monitorRow.status === 'WAITING',
    `会话数=${monitorPage.data?.total} 状态=${monitorRow?.status} 等待=${monitorRow?.durationSeconds}s 最后消息=${monitorRow?.lastMessage || '无'}`)

  const detail0 = await call('GET', `/admin/monitor/sessions/${sessionId}`, admin.token)
  log('监听详情返回消息流与可操作标记（R-200）', detail0.code === 200
    && Array.isArray(detail0.data?.messages) && detail0.data?.whisperAllowed === true
    && detail0.data?.takeoverAllowed === true,
    `消息数=${detail0.data?.messages?.length} 强插=${detail0.data?.whisperAllowed} 接管=${detail0.data?.takeoverAllowed}`)

  const whisper = await call('POST', `/admin/monitor/sessions/${sessionId}/whisper`, admin.token, {
    content: '组长介入：已收到您的反馈，请稍等，我们正在核实。'
  })
  log('组长强插发言以 SUPERVISOR 落库（R-201）', whisper.code === 200
    && whisper.data?.senderType === 'SUPERVISOR',
    `发送方=${whisper.data?.senderType} 内容=${whisper.data?.content}`)

  const detail1 = await call('GET', `/admin/monitor/sessions/${sessionId}`, admin.token)
  const supervisorMsg = (detail1.data?.messages || []).find((m) => m.senderType === 'SUPERVISOR')
  log('监听详情回显强插消息（R-200 / R-201）',
    Boolean(supervisorMsg) && supervisorMsg?.content?.includes('组长介入'),
    `消息数=${detail1.data?.messages?.length}`)

  const takeover = await call('POST', `/admin/monitor/sessions/${sessionId}/takeover`, admin.token)
  log('强制接管把排队会话直接接入管理员（R-202）', takeover.code === 200
    && takeover.data?.session?.status === 'HUMAN'
    && Number(takeover.data?.session?.agentId) === Number(adminId),
    `status=${takeover.data?.session?.status} 责任坐席=${takeover.data?.agentName}(id=${takeover.data?.session?.agentId})`)

  const agents = await call('GET', '/admin/monitor/agents', admin.token)
  log('派单候选返回在线坐席与负载（R-204）', agents.code === 200 && Array.isArray(agents.data?.list),
    `候选数=${agents.data?.list?.length}`)

  await call('POST', '/agent/online', agent.token, {})
  const reassign = await call('POST', `/admin/monitor/sessions/${sessionId}/reassign`, admin.token, {
    targetAgentId: agentId,
    reason: '冒烟：交由一线坐席继续处理'
  })
  log('强制派单把会话改派给指定坐席（R-203）', reassign.code === 200
    && reassign.data?.session?.status === 'HUMAN'
    && Number(reassign.data?.session?.agentId) === Number(agentId),
    `status=${reassign.data?.session?.status} 责任坐席=${reassign.data?.agentName}`)

  const reassignOffline = await call('POST', `/admin/monitor/sessions/${sessionId}/reassign`, admin.token, {
    targetAgentId: agent2Id
  })
  log('派单给未上线坐席被拦截（R-203 / R-204）', reassignOffline.code !== 200,
    `code=${reassignOffline.code} message=${reassignOffline.message}`)

  const closed = await call('POST', `/chat/sessions/${sessionId}/close`, agent.token, { closeReason: 'AGENT_CLOSED' })
  log('坐席结束会话（监听后置）', closed.code === 200 && closed.data?.status === 'CLOSED',
    `status=${closed.data?.status}`)

  const whisperClosed = await call('POST', `/admin/monitor/sessions/${sessionId}/whisper`, admin.token, {
    content: '会话结束后不应允许强插'
  })
  log('已结束会话强插被拦截（R-201）', whisperClosed.code !== 200,
    `code=${whisperClosed.code} message=${whisperClosed.message}`)

  const takeoverClosed = await call('POST', `/admin/monitor/sessions/${sessionId}/takeover`, admin.token)
  log('已结束会话接管被拦截（R-202）', takeoverClosed.code !== 200,
    `code=${takeoverClosed.code} message=${takeoverClosed.message}`)

  /* ================================================================
     五、一次性解决率口径（DB-05 / R-177）
     ================================================================ */
  const metrics = await call('GET', '/statistics/ops/metrics', admin.token)
  log('运营指标返回一次性解决率口径（DB-05 / R-177）', metrics.code === 200
    && Number(metrics.data?.oneTimeResolveWindowHours) === 72
    && typeof metrics.data?.oneTimeResolveBase === 'number'
    && typeof metrics.data?.revisitCount === 'number',
    `窗口=${metrics.data?.oneTimeResolveWindowHours}h 分母=${metrics.data?.oneTimeResolveBase} 分子=${metrics.data?.oneTimeResolveCount} 二次进线=${metrics.data?.revisitCount} 费率=${metrics.data?.oneTimeResolveRate}`)

  /* ---------- 清理临时规则 ---------- */
  for (const id of cleanupRuleIds) {
    if (id) await call('DELETE', `/admin/alert-rules/${id}`, admin.token)
  }
  await call('POST', '/agent/offline', agent.token)

  const failed = results.filter((r) => !r.ok)
  console.log('\n===== S6-B 告警规则化与监听强插 冒烟汇总 =====')
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
