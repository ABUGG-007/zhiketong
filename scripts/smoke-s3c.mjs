/**
 * 智客通 S3-C 工单规则与自动化冒烟脚本（验收用，零依赖：node scripts/smoke-s3c.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/01_schema.sql、sql/02_data.sql、
 *       sql/11_s3b_order_sla.sql、sql/12_s3c_order_rule.sql（order_rule / order_rule_run，预置 0 行）。
 * 覆盖：规则 CRUD（R-137~R-142）与唯一性/动作合法性校验 → 命中预览（R-146）→
 *       建单命中 AUTO_ASSIGN 落到在岗坐席并留痕（WO-04）→ 事件触发器改优先级（WO-15）→
 *       延迟规则入队 PENDING + 手动试跑（R-144）→ 工单维度执行流水（R-143 / R-145）与数据权限 →
 *       无可用在岗坐席记 SKIPPED 且工单状态不变
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

const runsOf = (resp) => (Array.isArray(resp.data)
  ? resp.data
  : (resp.data?.list || resp.data?.records || []))

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')
  log('三种角色登录', true,
    `admin=${admin.user.nickname} agent=${agent.user.nickname} user=${user.user.nickname}`)

  const agentId = agent.user.userId ?? agent.user.id
  const stamp = Date.now()

  /* ---------- 管理端权限与空列表 R-137 ---------- */
  const listAgent = await call('GET', '/admin/order-rules', agent.token)
  log('坐席访问规则管理被拒（R-137 权限）', listAgent.http === 403 || listAgent.code === 403,
    `http=${listAgent.http} code=${listAgent.code}`)

  const listUser = await call('GET', '/admin/order-rules', user.token)
  log('普通用户访问规则管理被拒（R-137 权限）', listUser.http === 403 || listUser.code === 403,
    `http=${listUser.http} code=${listUser.code}`)

  const list0 = await call('GET', '/admin/order-rules', admin.token)
  log('管理员规则列表可查（R-137）', list0.code === 200 && Array.isArray(list0.data),
    `数量=${(list0.data || []).length}`)

  /* ---------- 坐席上线（供 AUTO_ASSIGN 命中） ---------- */
  const online = await call('POST', '/agent/online', agent.token, {})
  log('坐席上线进入在岗池（R-40）', online.code === 200 && online.data?.onlineStatus === 'ONLINE',
    `status=${online.data?.onlineStatus}`)

  /* ---------- 新增 AUTO_ASSIGN 规则 R-139 ---------- */
  const assignName = `[冒烟]P1自动分配-${stamp}`
  const createA = await call('POST', '/admin/order-rules', admin.token, {
    ruleName: assignName,
    ruleType: 'AUTO_ASSIGN',
    triggerEvent: 'CREATED',
    delayMinutes: 0,
    matchPriority: 'P1',
    actionType: 'AUTO_ASSIGN',
    assignGroupId: null,
    description: '冒烟：P1 建单自动分配给在岗坐席',
    sort: 90,
    status: 1
  })
  const ruleAId = createA.data?.id
  log('新增自动分配规则（R-139）', createA.code === 200 && Boolean(ruleAId),
    `id=${ruleAId} 类型=${createA.data?.ruleTypeLabel}/${createA.data?.actionTypeLabel}`)

  const dupA = await call('POST', '/admin/order-rules', admin.token, {
    ruleName: assignName, ruleType: 'AUTO_ASSIGN', triggerEvent: 'CREATED',
    actionType: 'AUTO_ASSIGN', status: 1
  })
  log('重名规则被拦截（R-139 唯一性）', dupA.code !== 200,
    `code=${dupA.code} message=${dupA.message}`)

  const badAction = await call('POST', '/admin/order-rules', admin.token, {
    ruleName: `[冒烟]非法动作-${stamp}`, ruleType: 'TRIGGER', triggerEvent: 'CREATED',
    actionType: 'CHANGE_PRIORITY', actionValue: 'P9', status: 1
  })
  log('非法动作目标值被拦截（R-139 校验）', badAction.code !== 200,
    `code=${badAction.code} message=${badAction.message}`)

  const detailA = await call('GET', `/admin/order-rules/${ruleAId}`, admin.token)
  log('规则详情可查（R-138）', detailA.code === 200 && detailA.data?.id === ruleAId,
    `触发=${detailA.data?.triggerEventLabel} 动作=${detailA.data?.actionTypeLabel}`)

  const updA = await call('PUT', `/admin/order-rules/${ruleAId}`, admin.token, {
    ruleName: assignName, ruleType: 'AUTO_ASSIGN', triggerEvent: 'CREATED', delayMinutes: 0,
    matchPriority: 'P1', actionType: 'AUTO_ASSIGN', description: '冒烟：已修改', sort: 90, status: 1
  })
  log('修改规则（R-140）', updA.code === 200 && updA.data?.description === '冒烟：已修改',
    `说明=${updA.data?.description}`)

  const offA = await call('PUT', `/admin/order-rules/${ruleAId}/status`, admin.token, { status: 0 })
  const onA = await call('PUT', `/admin/order-rules/${ruleAId}/status`, admin.token, { status: 1 })
  log('停用并重新启用规则（R-141）', offA.data?.status === 0 && onA.data?.status === 1,
    `停用=${offA.data?.status} 启用=${onA.data?.status}`)

  /* ---------- 新增事件触发器规则 B（P2 → 改优先级 P0） ---------- */
  const createB = await call('POST', '/admin/order-rules', admin.token, {
    ruleName: `[冒烟]P2升级P0-${stamp}`, ruleType: 'TRIGGER', triggerEvent: 'CREATED', delayMinutes: 0,
    matchPriority: 'P2', actionType: 'CHANGE_PRIORITY', actionValue: 'P0',
    description: '冒烟：P2 建单即升为 P0', sort: 91, status: 1
  })
  const ruleBId = createB.data?.id
  log('新增事件触发器规则（R-139）', createB.code === 200 && Boolean(ruleBId), `id=${ruleBId}`)

  /* ---------- 命中预览 R-146 ---------- */
  const preview = await call('POST', '/admin/order-rules/preview', admin.token, {
    ruleName: `[冒烟]预览草稿-${stamp}`, ruleType: 'TRIGGER', triggerEvent: 'CREATED',
    matchPriority: 'P1', actionType: 'NOTIFY_ROLE', actionValue: 'ADMIN', status: 1
  })
  log('规则命中预览返回匹配数（R-146）', preview.code === 200
    && typeof preview.data?.matched === 'number',
    `matched=${preview.data?.matched} 样本上限=${preview.data?.sampleLimit}`)

  /* ---------- 建 P1 单：命中 AUTO_ASSIGN → 落到在岗坐席 ---------- */
  const wo1 = await call('POST', '/workorders', user.token, {
    title: `[冒烟] 自动分配演练 ${stamp}`, content: '验证 S3-C 建单命中自动分配规则，应落到在岗坐席。',
    category: '技术问题', priority: 'P1', source: 'MANUAL'
  })
  const wo1Id = wo1.data?.id
  const wo1Detail = await call('GET', `/workorders/${wo1Id}`, admin.token)
  const wo1Order = wo1Detail.data?.workOrder || {}
  log('P1 建单命中自动分配并指派在岗坐席（WO-04）', wo1.code === 200
    && wo1Order.status === 'ASSIGNED' && wo1Order.assigneeId === agentId,
    `status=${wo1Order.status} 受理人=${wo1Order.assigneeName}(id=${wo1Order.assigneeId})`)

  const runs1 = await call('GET', `/workorders/${wo1Id}/rules`, user.token)
  const assignRun = runsOf(runs1).find((r) => r.actionType === 'AUTO_ASSIGN')
  log('自动分配写入工单执行流水且成功（R-145）', runs1.code === 200
    && assignRun?.status === 'DONE', `流水数=${runsOf(runs1).length} 结果=${assignRun?.statusLabel}`)

  const logs1 = await call('GET', `/workorders/${wo1Id}/logs?pageNum=1&pageSize=50`, admin.token)
  const logActions = runsOf(logs1).map((i) => i.action)
  log('自动分配写系统流转日志 ASSIGN（WO-04 审计）', logs1.code === 200 && logActions.includes('ASSIGN'),
    `动作=${[...new Set(logActions)].join(',')}`)

  /* ---------- 建 P2 单：事件触发器改优先级为 P0 ---------- */
  const wo2 = await call('POST', '/workorders', user.token, {
    title: `[冒烟] 触发器改优先级演练 ${stamp}`, content: '验证 P2 建单触发器立即升级为 P0。',
    category: '技术问题', priority: 'P2', source: 'MANUAL'
  })
  const wo2Id = wo2.data?.id
  const wo2Detail = await call('GET', `/workorders/${wo2Id}`, admin.token)
  const wo2Order = wo2Detail.data?.workOrder || {}
  log('P2 建单触发器改优先级为 P0（WO-15）', wo2.code === 200 && wo2Order.priority === 'P0',
    `priority=${wo2Order.priority} status=${wo2Order.status}`)

  const runs2 = await call('GET', `/workorders/${wo2Id}/rules`, admin.token)
  const priRun = runsOf(runs2).find((r) => r.actionType === 'CHANGE_PRIORITY')
  log('触发器动作写入执行流水且成功（R-145）', runs2.code === 200 && priRun?.status === 'DONE',
    `结果=${priRun?.statusLabel} 说明=${priRun?.result}`)

  /* ---------- 延迟规则入队 PENDING + 手动试跑 R-143/R-144 ---------- */
  const createC = await call('POST', '/admin/order-rules', admin.token, {
    ruleName: `[冒烟]订单问题延迟通知-${stamp}`, ruleType: 'TRIGGER', triggerEvent: 'CREATED',
    delayMinutes: 60, matchCategory: '订单问题', actionType: 'NOTIFY_ROLE', actionValue: 'ADMIN',
    description: '冒烟：延迟 60 分钟通知管理员', sort: 92, status: 1
  })
  const ruleCId = createC.data?.id
  const wo3 = await call('POST', '/workorders', user.token, {
    title: `[冒烟] 延迟规则演练 ${stamp}`, content: '验证延迟规则先入 PENDING 队列。',
    category: '订单问题', priority: 'P3', source: 'PHONE'
  })
  const wo3Id = wo3.data?.id

  const runsC1 = await call('GET', `/admin/order-rules/${ruleCId}/runs?pageNum=1&pageSize=20`, admin.token)
  const pendingRun = runsOf(runsC1).find((r) => r.orderId === wo3Id && r.status === 'PENDING')
  log('延迟规则先落 PENDING 待执行队列（R-143 / WO-16）', runsC1.code === 200 && Boolean(pendingRun),
    `计划执行=${pendingRun?.plannedTime ?? 'n/a'}`)

  const runOnce = await call('POST', `/admin/order-rules/${ruleCId}/run`, admin.token)
  log('手动试跑立即执行延迟规则（R-144）', runOnce.code === 200
    && Number(runOnce.data?.matched) >= 1 && Number(runOnce.data?.done) >= 1,
    `命中=${runOnce.data?.matched} 成功=${runOnce.data?.done} 失败=${runOnce.data?.failed} 跳过=${runOnce.data?.skipped}`)

  const runsC2 = await call('GET', `/admin/order-rules/${ruleCId}/runs?pageNum=1&pageSize=20`, admin.token)
  const doneRun = runsOf(runsC2).find((r) => r.orderId === wo3Id && r.status === 'DONE')
  log('试跑后执行流水转终态 DONE（R-143）', runsC2.code === 200 && Boolean(doneRun),
    `流水数=${runsOf(runsC2).length}`)

  /* ---------- R-145 数据权限 ---------- */
  const r145Creator = await call('GET', `/workorders/${wo3Id}/rules`, user.token)
  log('创建人可查看工单执行流水（R-145 数据权限）', r145Creator.code === 200 && Array.isArray(r145Creator.data),
    `流水数=${runsOf(r145Creator).length}`)

  const r145Assignee = await call('GET', `/workorders/${wo1Id}/rules`, agent.token)
  log('受理坐席可查看工单执行流水（R-145 数据权限）', r145Assignee.code === 200 && Array.isArray(r145Assignee.data),
    `流水数=${runsOf(r145Assignee).length}`)

  const r145Other = await call('GET', `/workorders/${wo3Id}/rules`, agent.token)
  log('无关坐席越权查看执行流水被拒（R-145 数据权限）', r145Other.http === 403 || r145Other.code === 403,
    `http=${r145Other.http} code=${r145Other.code}`)

  /* ---------- 无可用在岗坐席 → SKIPPED 且工单状态不变 ---------- */
  await call('POST', '/agent/offline', agent.token)
  const createD = await call('POST', '/admin/order-rules', admin.token, {
    ruleName: `[冒烟]P0自动分配-${stamp}`, ruleType: 'AUTO_ASSIGN', triggerEvent: 'CREATED', delayMinutes: 0,
    matchPriority: 'P0', actionType: 'AUTO_ASSIGN', description: '冒烟：无在岗坐席应跳过', sort: 93, status: 1
  })
  const ruleDId = createD.data?.id
  const wo4 = await call('POST', '/workorders', user.token, {
    title: `[冒烟] 无在岗坐席演练 ${stamp}`, content: '验证无可用在岗坐席时自动分配跳过。',
    category: '技术问题', priority: 'P0', source: 'MANUAL'
  })
  const wo4Id = wo4.data?.id
  const wo4Detail = await call('GET', `/workorders/${wo4Id}`, admin.token)
  const wo4Order = wo4Detail.data?.workOrder || {}
  const runsD = await call('GET', `/workorders/${wo4Id}/rules`, admin.token)
  const skippedRun = runsOf(runsD).find((r) => r.actionType === 'AUTO_ASSIGN' && r.status === 'SKIPPED')
  log('无可用在岗坐席记 SKIPPED 且工单状态不变（WO-04 降级）', wo4Order.status === 'PENDING'
    && !wo4Order.assigneeId && Boolean(skippedRun),
    `status=${wo4Order.status} 流水=${skippedRun?.statusLabel ?? '无'}`)

  /* ---------- 删除规则 R-142 ---------- */
  const delD = await call('DELETE', `/admin/order-rules/${ruleDId}`, admin.token)
  log('删除规则（R-142）', delD.code === 200, `code=${delD.code}`)

  const listAfter = await call('GET', '/admin/order-rules?status=1', admin.token)
  log('删除后规则列表不再包含该规则（R-137）', listAfter.code === 200
    && !(listAfter.data || []).some((r) => r.id === ruleDId),
    `启用规则数=${(listAfter.data || []).length}`)

  /* ---------- 清理临时规则与工单 ---------- */
  await call('DELETE', `/admin/order-rules/${ruleAId}`, admin.token)
  await call('DELETE', `/admin/order-rules/${ruleBId}`, admin.token)
  await call('DELETE', `/admin/order-rules/${ruleCId}`, admin.token)

  /* ---------- 汇总 ---------- */
  const failed = results.filter((r) => !r.ok)
  console.log('\n===== S3-C 工单规则与自动化 冒烟汇总 =====')
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
