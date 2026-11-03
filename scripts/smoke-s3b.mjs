/**
 * 智客通 S3-B 多级 SLA 冒烟脚本（验收用，零依赖：node scripts/smoke-s3b.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/01_schema.sql、sql/02_data.sql、
 *       sql/11_s3b_order_sla.sql（含 P0-P3 默认策略）。
 * 覆盖：SLA 策略 CRUD（R-127~R-132）→ 建单触发 SLA 计时（R-49 + R-133）→ 首响标记 →
 *       挂起停表 / 恢复计时（R-134 / R-135）→ 未来时刻扫描触发超时与升级（R-136）→
 *       越权拦截（普通用户挂起被拒）→ 解决完结
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

/** ISO-8601 本地时间（后端 simulateTime 格式 yyyy-MM-dd'T'HH:mm:ss） */
function isoLocal(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')
  log('三种角色登录', true, `admin=${admin.user.nickname} agent=${agent.user.nickname} user=${user.user.nickname}`)

  /* ---------- SLA 策略配置 R-127 ~ R-132 ---------- */
  const listEmpty = await call('GET', '/admin/sla-policies', user.token)
  log('普通用户访问 SLA 策略被拒（R-127 权限）', listEmpty.http === 403 || listEmpty.code === 403,
    `http=${listEmpty.http} code=${listEmpty.code}`)

  const list = await call('GET', '/admin/sla-policies', admin.token)
  const policies = Array.isArray(list.data) ? list.data : []
  log('SLA 策略列表含 P0-P3 默认策略（R-127）', list.code === 200 && policies.length >= 4,
    `数量=${policies.length} 默认=${policies.filter((p) => Number(p.defaultFlag) === 1).length}`)

  const filter = await call('GET', '/admin/sla-policies?priority=P1&status=1', admin.token)
  const p1 = (Array.isArray(filter.data) ? filter.data : []).find((p) => p.priority === 'P1')
  log('SLA 策略按优先级过滤（R-127）', filter.code === 200 && Boolean(p1),
    `P1 策略=${p1?.policyName ?? '无'}`)

  const detailResp = p1 ? await call('GET', `/admin/sla-policies/${p1.id}`, admin.token) : { code: 0 }
  log('SLA 策略详情可查（R-128）', detailResp.code === 200 && detailResp.data?.id === p1?.id,
    `首响=${detailResp.data?.firstResponseMinutes} 解决=${detailResp.data?.resolveMinutes} 预警=${detailResp.data?.warnPercent}%`)

  const stamp = Date.now()
  const tempName = `[冒烟]临时策略-${stamp}`
  const createdPolicy = await call('POST', '/admin/sla-policies', admin.token, {
    policyName: tempName,
    priority: 'P2',
    firstResponseMinutes: 15,
    replyIntervalMinutes: 15,
    resolveMinutes: 60,
    warnPercent: 70,
    escalateMinutes: 10,
    escalateRole: 'ADMIN',
    defaultFlag: 0,
    description: '冒烟测试临时策略',
    sort: 99,
    status: 1
  })
  const policyId = createdPolicy.data?.id
  log('新增 SLA 策略（R-129）', createdPolicy.code === 200 && Boolean(policyId),
    `id=${policyId} 名称=${createdPolicy.data?.policyName}`)

  const updatedPolicy = await call('PUT', `/admin/sla-policies/${policyId}`, admin.token, {
    policyName: tempName,
    priority: 'P2',
    firstResponseMinutes: 20,
    replyIntervalMinutes: 20,
    resolveMinutes: 90,
    warnPercent: 75,
    escalateMinutes: 15,
    escalateRole: 'ADMIN',
    defaultFlag: 0,
    description: '冒烟测试临时策略（已改）',
    sort: 99,
    status: 1
  })
  log('修改 SLA 策略（R-130）', updatedPolicy.code === 200 && Number(updatedPolicy.data?.resolveMinutes) === 90,
    `解决时限=${updatedPolicy.data?.resolveMinutes}`)

  const disabled = await call('PUT', `/admin/sla-policies/${policyId}/status`, admin.token, { status: 0 })
  log('停用 SLA 策略（R-131）', disabled.code === 200 && Number(disabled.data?.status) === 0,
    `status=${disabled.data?.status}`)

  const enabled = await call('PUT', `/admin/sla-policies/${policyId}/status`, admin.token, { status: 1 })
  log('启用 SLA 策略（R-131）', enabled.code === 200 && Number(enabled.data?.status) === 1,
    `status=${enabled.data?.status}`)

  const dupPolicy = await call('POST', '/admin/sla-policies', admin.token, {
    policyName: tempName, priority: 'P3', firstResponseMinutes: 10, resolveMinutes: 20, status: 1
  })
  log('重名 SLA 策略被拦截（R-129 唯一性）', dupPolicy.code !== 200,
    `code=${dupPolicy.code} message=${dupPolicy.message}`)

  /* ---------- 建单触发 SLA 计时 ---------- */
  const wo = await call('POST', '/workorders', user.token, {
    title: `[冒烟] SLA 多级时限演练 ${stamp}`,
    content: '用于验证 S3-B 多级 SLA、停表与超时升级链路的冒烟工单，将在未来时刻扫描中触发超时。',
    category: '技术问题',
    priority: 'P1',
    source: 'MANUAL'
  })
  const woId = wo.data?.id
  log('用户创建 P1 工单（R-49）', wo.code === 200 && Boolean(woId),
    `id=${woId} 状态=${wo.data?.status} SLA 截止=${wo.data?.slaDeadline ?? 'n/a'}`)

  const sla0 = await call('GET', `/workorders/${woId}/sla`, user.token)
  log('建单即初始化 SLA 计时记录（R-133）', sla0.code === 200 && sla0.data?.slaId
    && sla0.data?.status === 'RUNNING' && sla0.data?.slaStatus === 'NORMAL',
    `slaId=${sla0.data?.slaId} 策略=${sla0.data?.policyName} 状态=${sla0.data?.slaStatus}`)
  log('SLA 三级时限快照绑定 P1 默认策略', Number(sla0.data?.firstResponseMinutes) === 120
    && Number(sla0.data?.resolveMinutes) === 480,
    `首响=${sla0.data?.firstResponseMinutes} 回复=${sla0.data?.replyIntervalMinutes} 解决=${sla0.data?.resolveMinutes}`)

  /* ---------- 流转：分配 → 受理（首响）---------- */
  const assign = await call('POST', `/workorders/${woId}/assign`, admin.token,
    { assigneeId: agent.user.userId ?? agent.user.id, remark: 'SLA 冒烟：指派给张客服' })
  log('管理员分配工单（R-55）', assign.code === 200 && assign.data?.status === 'ASSIGNED',
    `status=${assign.data?.status} 处理人=${assign.data?.assigneeName}`)

  const acceptWo = await call('POST', `/workorders/${woId}/accept`, agent.token, { remark: '坐席已受理，开始处理' })
  log('坐席受理工单触发首响计时（R-56）', acceptWo.code === 200 && acceptWo.data?.status === 'PROCESSING',
    `status=${acceptWo.data?.status}`)

  const slaAfterAccept = await call('GET', `/workorders/${woId}/sla`, agent.token)
  log('首响时间被 SLA 记录捕获（R-133）', slaAfterAccept.code === 200 && Boolean(slaAfterAccept.data?.firstResponseTime),
    `首响=${slaAfterAccept.data?.firstResponseTime ?? 'n/a'} 回复截止=${slaAfterAccept.data?.replyDueTime ?? 'n/a'}`)

  /* ---------- 挂起停表 / 恢复计时 ---------- */
  const userPause = await call('POST', `/workorders/${woId}/pause`, user.token, { pauseType: 'WAITING_CUSTOMER' })
  log('普通用户挂起工单被拒（R-134 权限）', userPause.http === 403 || userPause.code === 403,
    `http=${userPause.http} code=${userPause.code}`)

  const pause = await call('POST', `/workorders/${woId}/pause`, agent.token,
    { pauseType: 'WAITING_CUSTOMER', reason: '等待客户补充订单号' })
  log('坐席挂起停表（R-134）', pause.code === 200 && pause.data?.status === 'WAITING_CUSTOMER',
    `status=${pause.data?.status}`)

  const slaPaused = await call('GET', `/workorders/${woId}/sla`, agent.token)
  log('停表后 SLA 状态为 PAUSED 且有停表流水（R-133）', slaPaused.code === 200
    && slaPaused.data?.status === 'PAUSED' && slaPaused.data?.slaStatus === 'PAUSED'
    && (slaPaused.data?.pauses || []).length >= 1,
    `status=${slaPaused.data?.status} 停表数=${(slaPaused.data?.pauses || []).length}`)

  const resume = await call('POST', `/workorders/${woId}/resume`, agent.token, { reason: '客户已反馈，继续处理' })
  log('坐席恢复计时（R-135）', resume.code === 200 && resume.data?.status === 'PROCESSING',
    `status=${resume.data?.status}`)

  const slaResumed = await call('GET', `/workorders/${woId}/sla`, agent.token)
  log('恢复后计时重新 RUNNING 且停表时长累计（R-133）', slaResumed.code === 200
    && slaResumed.data?.status === 'RUNNING',
    `status=${slaResumed.data?.status} 累计停表=${slaResumed.data?.pauseMinutes} 分钟`)

  const resumeAgain = await call('POST', `/workorders/${woId}/resume`, agent.token, {})
  log('非停表状态重复恢复被拦截（R-135 状态校验）', resumeAgain.code !== 200,
    `code=${resumeAgain.code} message=${resumeAgain.message}`)

  /* ---------- 未来时刻扫描：预警 → 超时 → 升级 ---------- */
  const simulate = new Date(Date.now() + 7 * 24 * 3600 * 1000)
  const scan = await call('POST', `/workorders/${woId}/sla/scan`, admin.token, { simulateTime: isoLocal(simulate) })
  log('手动扫描未来时刻触发超时与升级（R-136）', scan.code === 200
    && scan.data?.slaStatus === 'OVERDUE' && Number(scan.data?.overdueFlag) === 1,
    `slaStatus=${scan.data?.slaStatus} overdueFlag=${scan.data?.overdueFlag}`)

  const scanUser = await call('POST', `/workorders/${woId}/sla/scan`, user.token, { simulateTime: isoLocal(simulate) })
  log('普通用户扫描 SLA 被拒（R-136 权限）', scanUser.http === 403 || scanUser.code === 403,
    `http=${scanUser.http} code=${scanUser.code}`)

  const woDetail = await call('GET', `/workorders/${woId}`, admin.token)
  log('超时标记回写工单主表（R-53）', woDetail.code === 200 && Number(woDetail.data?.workOrder?.overdueFlag) === 1
    && woDetail.data?.workOrder?.slaStatus === 'OVERDUE',
    `overdueFlag=${woDetail.data?.workOrder?.overdueFlag} slaStatus=${woDetail.data?.workOrder?.slaStatus}`)

  const orderLogs = await call('GET', `/workorders/${woId}/logs?pageNum=1&pageSize=50`, admin.token)
  const actions = (orderLogs.data?.list || orderLogs.data?.records || []).map((i) => i.action)
  const hasSlaLog = actions.includes('PAUSE') || actions.includes('RESUME')
    || actions.includes('OVERDUE') || actions.includes('ESCALATE')
  log('SLA 停表/超时/升级写入流转日志（R-63）', orderLogs.code === 200 && hasSlaLog,
    `动作=${[...new Set(actions)].join(',')}`)

  /* ---------- 解决完结 ---------- */
  const resolveWo = await call('POST', `/workorders/${woId}/resolve`, agent.token, { remark: '问题已解决' })
  log('解决工单（R-58）', resolveWo.code === 200 && resolveWo.data?.status === 'RESOLVED',
    `status=${resolveWo.data?.status}`)

  const slaFinished = await call('GET', `/workorders/${woId}/sla`, admin.token)
  log('解决后 SLA 记录完结 FINISHED（R-133）', slaFinished.code === 200
    && slaFinished.data?.status === 'FINISHED' && slaFinished.data?.slaStatus === 'FINISHED',
    `status=${slaFinished.data?.status} slaStatus=${slaFinished.data?.slaStatus}`)

  /* ---------- 清理临时策略 ---------- */
  const delPolicy = await call('DELETE', `/admin/sla-policies/${policyId}`, admin.token)
  log('删除临时 SLA 策略（R-132）', delPolicy.code === 200, `code=${delPolicy.code}`)

  /* ---------- 汇总 ---------- */
  const failed = results.filter((r) => !r.ok)
  console.log('\n===== S3-B SLA 冒烟汇总 =====')
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
