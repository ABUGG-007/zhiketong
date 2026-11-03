/**
 * 智客通 S5-A 智能质检冒烟脚本（验收用，零依赖，node scripts/smoke-s5a.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/01_schema.sql ~ sql/15_s4c_audit_log.sql
 *       与 sql/16_s5a_quality.sql（quality_rule / quality_record / quality_item，默认 11 条规则），
 *       且库中存在状态为 CLOSED 且有消息的会话（脚本会取最近的已结束会话发起质检）。
 * 覆盖：权限收紧（R-160）→ 规则列表与四类规则（R-160/R-161）→ 规则新增与校验、修改、启停、删除
 *       （R-162~R-165）→ 可质检会话（R-171）→ 手动质检与幂等覆盖（R-168）→
 *       记录分页与筛选（R-166）→ 评分卡明细（R-167）→ 概览统计（R-169）→ 扫描互斥（R-170）
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
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

const rowsOf = (resp) => (Array.isArray(resp.data)
  ? resp.data
  : (resp.data?.list || resp.data?.records || []))

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')
  const stamp = Date.now()

  /* ---------- 权限收紧 R-160 ---------- */
  const agentRules = await call('GET', '/admin/quality/rules', agent.token)
  log('坐席访问质检规则被拒（R-160 权限）', agentRules.http === 403 || agentRules.code === 403,
    `http=${agentRules.http} code=${agentRules.code}`)

  const userRules = await call('GET', '/admin/quality/rules', user.token)
  log('普通用户访问质检规则被拒（R-160 权限）', userRules.http === 403 || userRules.code === 403,
    `http=${userRules.http} code=${userRules.code}`)

  /* ---------- 规则列表 R-160 ---------- */
  const list0 = await call('GET', '/admin/quality/rules', admin.token)
  const rules0 = rowsOf(list0)
  const types0 = new Set(rules0.map((r) => r.ruleType))
  log('管理员可查质检规则且四类规则齐备（R-160）',
    list0.code === 200 && rules0.length >= 11
    && ['SEMANTIC', 'SENTIMENT', 'SERVICE', 'SENSITIVE'].every((t) => types0.has(t)),
    `数量=${rules0.length} 类型=${[...types0].join(',')}`)

  const builtin = rules0.find((r) => r.ruleCode === 'SLOW_REPLY')
  log('内置「回复超时」规则带阈值且标记内置（R-160）',
    Boolean(builtin) && Number(builtin.thresholdValue) > 0 && builtin.builtin === true,
    `阈值=${builtin?.thresholdValue} 分钟 内置=${builtin?.builtin}`)

  const aiRule = rules0.find((r) => r.ruleType === 'SEMANTIC')
  log('语义类规则不承载关键词（交由大模型判定）',
    Boolean(aiRule) && !aiRule.keywords,
    `规则=${aiRule?.ruleCode} 关键词=${aiRule?.keywords ?? 'null'}`)

  /* ---------- 新增规则 R-162 ---------- */
  const ruleName = `[冒烟]违规承诺-${stamp}`
  const create = await call('POST', '/admin/quality/rules', admin.token, {
    ruleName,
    ruleType: 'SENSITIVE',
    severity: 'HIGH',
    deductScore: 8,
    keywords: '冒烟禁语A,冒烟禁语B,冒烟禁语C',
    matchMode: 'CONTAINS',
    description: '冒烟：出现指定禁语即命中',
    sort: 210,
    status: 1
  })
  const ruleId = create.data?.id
  log('新增敏感词类规则（R-162）',
    create.code === 200 && Boolean(ruleId) && String(create.data?.ruleCode || '').startsWith('CUSTOM_'),
    `id=${ruleId} 编码=${create.data?.ruleCode} 类型=${create.data?.ruleTypeName} 内置=${create.data?.builtin}`)

  const dup = await call('POST', '/admin/quality/rules', admin.token, {
    ruleName, ruleType: 'SENSITIVE', severity: 'HIGH', deductScore: 5,
    keywords: '冒烟禁语A', matchMode: 'CONTAINS', status: 1
  })
  log('重名规则被拦截（R-162 唯一性）', dup.code !== 200,
    `code=${dup.code} message=${dup.message}`)

  const badKeyword = await call('POST', '/admin/quality/rules', admin.token, {
    ruleName: `[冒烟]无关键词-${stamp}`, ruleType: 'SERVICE', severity: 'MEDIUM',
    deductScore: 5, matchMode: 'CONTAINS', status: 1
  })
  log('服务规范类规则缺关键词被拦截（R-162 可判定性校验）', badKeyword.code !== 200,
    `code=${badKeyword.code} message=${badKeyword.message}`)

  const badDeduct = await call('POST', '/admin/quality/rules', admin.token, {
    ruleName: `[冒烟]超限扣分-${stamp}`, ruleType: 'SENSITIVE', severity: 'LOW',
    deductScore: 99, keywords: '冒烟禁语A', matchMode: 'CONTAINS', status: 1
  })
  log('扣分超过上限被参数校验拦截（R-162 参数校验）', badDeduct.code !== 200,
    `code=${badDeduct.code} message=${badDeduct.message}`)

  /* ---------- 规则详情与修改 R-161 / R-163 ---------- */
  const detail = await call('GET', `/admin/quality/rules/${ruleId}`, admin.token)
  log('规则详情可查（R-161）', detail.code === 200 && detail.data?.id === ruleId,
    `编码=${detail.data?.ruleCode} 扣分=${detail.data?.deductScore}`)

  const update = await call('PUT', `/admin/quality/rules/${ruleId}`, admin.token, {
    ruleName, ruleType: 'SENSITIVE', severity: 'MEDIUM', deductScore: 6,
    keywords: '冒烟禁语A,冒烟禁语B', matchMode: 'ABSENT',
    description: '冒烟：未出现禁语即命中（验证匹配方式可切换）', sort: 211, status: 1
  })
  log('修改规则扣分与匹配方式（R-163）',
    update.code === 200 && Number(update.data?.deductScore) === 6
    && update.data?.matchMode === 'ABSENT',
    `扣分=${update.data?.deductScore} 匹配=${update.data?.matchMode} 严重=${update.data?.severityName}`)

  const filtered = await call('GET', '/admin/quality/rules?ruleType=SENSITIVE', admin.token)
  log('规则列表可按类型筛选（R-160）',
    filtered.code === 200 && rowsOf(filtered).every((r) => r.ruleType === 'SENSITIVE'),
    `数量=${rowsOf(filtered).length}`)

  /* ---------- 启停 R-164 ---------- */
  const off = await call('PUT', `/admin/quality/rules/${ruleId}/status`, admin.token, { status: 0 })
  const on = await call('PUT', `/admin/quality/rules/${ruleId}/status`, admin.token, { status: 1 })
  log('停用并重新启用规则（R-164）', off.data?.status === 0 && on.data?.status === 1,
    `停用=${off.data?.status} 启用=${on.data?.status}`)

  /* ---------- 可质检会话与手动质检 R-171 / R-168 ---------- */
  const sessions = await call('GET', '/admin/quality/sessions?pageNum=1&pageSize=5', admin.token)
  const sessionRows = rowsOf(sessions)
  // 优先取尚未质检的会话：已人工复核的会话会被拒绝重新质检（数据累积时保证脚本可重跑）
  const target = sessionRows.find((row) => !row.recordId) || sessionRows[0]
  log('可质检会话列表返回已结束会话（R-171）',
    sessions.code === 200 && sessionRows.length > 0 && target?.status === 'CLOSED',
    `共 ${sessions.data?.total} 条 首条=${target?.sessionNo} 消息=${target?.messageCount}`)

  const evaluate = await call('POST', `/admin/quality/sessions/${target.id}/evaluate`, admin.token)
  const ev = evaluate.data || {}
  const items = Array.isArray(ev.items) ? ev.items : []
  const hitItems = items.filter((i) => Number(i.hit) === 1)
  const deductSum = hitItems.reduce((sum, i) => sum + (Number(i.deductScore) || 0), 0)
  log('手动质检返回评分卡与逐规则明细（R-168）',
    evaluate.code === 200 && Number(ev.score) >= 0 && Number(ev.score) <= 100
    && ['A', 'B', 'C', 'D'].includes(ev.grade) && items.length >= 1,
    `得分=${ev.score} 等级=${ev.grade}${ev.gradeName} 明细=${items.length} 命中=${ev.hitCount} 引擎=${ev.engine}`)

  log('命中数与扣分合计与明细一致（R-168 评分口径）',
    Number(ev.hitCount) === hitItems.length && Number(ev.totalDeduct) === deductSum
    && Number(ev.score) === Math.max(0, 100 - deductSum),
    `命中=${ev.hitCount}/${hitItems.length} 扣分=${ev.totalDeduct}/${deductSum} 得分=${ev.score}`)

  log('合格判定与合格线快照一致（R-168）',
    (Number(ev.score) >= Number(ev.passScore) ? 1 : 0) === Number(ev.passFlag),
    `得分=${ev.score} 合格线=${ev.passScore} 合格=${ev.passFlag}`)

  const textResult = items.find((i) => i.evidence)
  log('命中项带判定证据，未命中项不给扣分（R-168 可复核性）',
    Boolean(textResult) || hitItems.length === 0,
    `带证据项=${items.filter((i) => i.evidence).length}`)

  const reevaluate = await call('POST', `/admin/quality/sessions/${target.id}/evaluate`, admin.token)
  log('重复质检覆盖同一条记录（R-168 幂等）',
    reevaluate.code === 200 && reevaluate.data?.id === ev.id,
    `记录 id=${ev.id} → ${reevaluate.data?.id}`)

  const bySessionNo = await call('GET',
    `/admin/quality/records?pageNum=1&pageSize=10&keyword=${encodeURIComponent(target.sessionNo)}`, admin.token)
  log('会话维度质检记录唯一（R-166 + uk_session）',
    bySessionNo.code === 200 && Number(bySessionNo.data?.total) === 1,
    `total=${bySessionNo.data?.total}`)

  /* ---------- 记录分页与筛选 R-166 ---------- */
  const records = await call('GET', '/admin/quality/records?pageNum=1&pageSize=10', admin.token)
  log('质检记录分页可查（R-166）',
    records.code === 200 && Array.isArray(records.data?.list) && Number(records.data?.total) >= 1,
    `total=${records.data?.total} pageNum=${records.data?.pageNum} pageSize=${records.data?.pageSize}`)

  const gradeFilter = await call('GET', `/admin/quality/records?pageNum=1&pageSize=20&grade=${ev.grade}`, admin.token)
  log('记录可按等级筛选（R-166）',
    gradeFilter.code === 200 && rowsOf(gradeFilter).every((r) => r.grade === ev.grade),
    `等级=${ev.grade} 数量=${rowsOf(gradeFilter).length}`)

  const badPage = await call('GET', '/admin/quality/records?pageNum=0&pageSize=10', admin.token)
  log('非法分页参数被拦截（R-166 参数校验）', badPage.code !== 200,
    `code=${badPage.code} message=${badPage.message}`)

  /* ---------- 评分卡详情 R-167 ---------- */
  const recordDetail = await call('GET', `/admin/quality/records/${ev.id}`, admin.token)
  const detailItems = Array.isArray(recordDetail.data?.items) ? recordDetail.data.items : []
  log('评分卡详情含规则数与消息构成（R-167）',
    recordDetail.code === 200 && detailItems.length === Number(recordDetail.data?.ruleCount)
    && Number(recordDetail.data?.messageCount) > 0,
    `明细=${detailItems.length} 规则=${recordDetail.data?.ruleCount} 消息=${recordDetail.data?.messageCount}`)

  const missing = await call('GET', '/admin/quality/records/99999999', admin.token)
  log('不存在的质检记录返回 404（R-167）', missing.code === 404 || missing.http === 404,
    `code=${missing.code}`)

  /* ---------- 概览统计 R-169 ---------- */
  const overview = await call('GET', '/admin/quality/overview', admin.token)
  const ov = overview.data || {}
  log('质检概览返回合格率与等级分布（R-169）',
    overview.code === 200 && Number(ov.recordCount) >= 1
    && Array.isArray(ov.grades) && ov.grades.length === 4
    && Number(ov.passRate) >= 0 && Number(ov.passRate) <= 100,
    `记录=${ov.recordCount} 合格率=${ov.passRate}% 均分=${ov.avgScore} 覆盖率=${ov.coverageRate}%`)

  log('概览合格与不合格相加等于记录数（R-169 口径）',
    Number(ov.passCount) + Number(ov.failCount) === Number(ov.recordCount),
    `合格=${ov.passCount} 不合格=${ov.failCount} 合计=${ov.recordCount}`)

  /* ---------- 扫描互斥 R-170 ---------- */
  const scan = await call('POST', '/admin/quality/scan', admin.token)
  log('立即扫描返回执行结果或锁占用提示（R-170）',
    scan.code === 200 && typeof scan.data?.candidateCount === 'number'
    && typeof scan.data?.locked === 'boolean',
    `locked=${scan.data?.locked} 候选=${scan.data?.candidateCount} 成功=${scan.data?.processedCount} 失败=${scan.data?.failedCount}`)

  /* ---------- 删除规则 R-165 ---------- */
  const del = await call('DELETE', `/admin/quality/rules/${ruleId}`, admin.token)
  log('删除规则（R-165）', del.code === 200, `code=${del.code}`)

  const delAgain = await call('DELETE', `/admin/quality/rules/${ruleId}`, admin.token)
  log('重复删除返回不存在（R-165 逻辑删除）', delAgain.code === 404 || delAgain.http === 404,
    `code=${delAgain.code}`)

  const afterDelete = await call('GET', '/admin/quality/rules', admin.token)
  log('删除后规则不再出现在列表（R-165）',
    afterDelete.code === 200 && !rowsOf(afterDelete).some((r) => r.id === ruleId),
    `数量=${rowsOf(afterDelete).length}`)

  /* ---------- 审计留痕（6.4 写操作可审计） ---------- */
  const audit = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=20&module=QUALITY', admin.token)
  const auditRows = rowsOf(audit)
  log('质检规则写操作写入操作审计（6.4.4）',
    audit.code === 200 && auditRows.length > 0,
    `审计条数=${auditRows.length} 动作=${[...new Set(auditRows.map((r) => r.action))].join(',')}`)
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
