/**
 * 智客通 S5-B 人工复核与人机对比冒烟脚本（验收用，零依赖，node scripts/smoke-s5b.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/01_schema.sql ~ sql/17_s5b_quality_review.sql，
 *       且库中存在「待复核」质检记录；若不足两条，脚本会自动对未质检的已结束会话补建记录。
 * 覆盖：权限收紧（复核接口仅管理员）→ 复核参数校验（复核分越界 / 意见必填 / 明细越权 / 改判缺扣分 /
 *       复核分与改判不一致）→ 提交复核 R-172 → 人机对比报表 R-175 → 确认归档 R-173 →
 *       驳回退回 R-174 → 非法流转 409 → 列表与概览生效口径 → 审计留痕 REVIEW/CONFIRM/REJECT
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

/** 按改判结果核算应得复核分：命中行扣分累加，未改判行沿用生效结论 */
function expectedScore(items, patches) {
  const patchMap = new Map(patches.map((p) => [p.itemId, p]))
  let deduct = 0
  for (const item of items) {
    const patch = patchMap.get(item.id)
    const hit = patch ? Number(patch.hit) : Number(item.effectiveHit)
    const score = patch
      ? (Number(patch.hit) === 1 ? Number(patch.deductScore) || 0 : 0)
      : Number(item.effectiveDeduct) || 0
    if (hit === 1) deduct += score
  }
  return Math.max(0, 100 - deduct)
}

/** 取至少 count 条「待复核」记录，不足时对未质检会话补建 */
async function ensurePending(admin, count) {
  const resp = await call('GET', '/admin/quality/records?pageNum=1&pageSize=50&status=PENDING', admin.token)
  const ids = rowsOf(resp).map((row) => row.id)
  if (ids.length >= count) return ids.slice(0, count)
  const sessions = await call('GET', '/admin/quality/sessions?pageNum=1&pageSize=50', admin.token)
  for (const session of rowsOf(sessions)) {
    if (ids.length >= count) break
    if (session.recordId) continue
    const created = await call('POST', `/admin/quality/sessions/${session.id}/evaluate`, admin.token)
    if (created.code === 200 && created.data?.id) ids.push(created.data.id)
  }
  return ids.slice(0, count)
}

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')
  const stamp = Date.now()

  /* ---------- 权限收紧（接口仅管理员可调用） ---------- */
  const agentReview = await call('PUT', '/admin/quality/records/1/review', agent.token,
    { reviewScore: 90, reviewComment: '冒烟：坐席不应有复核权限' })
  log('坐席调用复核接口被拒（R-172 权限）', agentReview.http === 403 || agentReview.code === 403,
    `http=${agentReview.http} code=${agentReview.code}`)

  const userConfirm = await call('PUT', '/admin/quality/records/1/confirm', user.token, {})
  log('普通用户调用归档接口被拒（R-173 权限）', userConfirm.http === 403 || userConfirm.code === 403,
    `http=${userConfirm.http} code=${userConfirm.code}`)

  const userCompare = await call('GET', '/admin/quality/comparison', user.token)
  log('普通用户访问人机对比报表被拒（R-175 权限）',
    userCompare.http === 403 || userCompare.code === 403, `http=${userCompare.http} code=${userCompare.code}`)

  /* ---------- 准备待复核记录 ---------- */
  const ids = await ensurePending(admin, 2)
  log('可取得至少两条待复核记录（R-172 前置）', ids.length >= 2, `记录=${ids.join(',')}`)
  if (ids.length < 2) {
    console.log('\n待复核记录不足，无法继续复核流转用例')
    return
  }
  const [idA, idB] = ids

  const detailA = await call('GET', `/admin/quality/records/${idA}`, admin.token)
  const recA = detailA.data || {}
  const itemsA = Array.isArray(recA.items) ? recA.items : []
  const autoScoreA = Number(recA.score)
  log('待复核记录带出自动结论与生效分（R-167 生效口径）',
    detailA.code === 200 && recA.status === 'PENDING' && itemsA.length >= 1
    && Number(recA.effectiveScore) === autoScoreA,
    `会话=${recA.sessionNo} 自动=${autoScoreA} 生效=${recA.effectiveScore} 明细=${itemsA.length}`)

  /* 优先把一条已命中的规则改判为未命中（撤扣分），否则把一条未命中改判为命中（加扣分） */
  const hitItem = itemsA.find((item) => Number(item.effectiveHit) === 1)
  const missItem = itemsA.find((item) => Number(item.effectiveHit) === 0)
  const patch = hitItem
    ? { itemId: hitItem.id, hit: 0, deductScore: 0 }
    : { itemId: missItem.id, hit: 1, deductScore: 6 }
  const patchName = hitItem ? hitItem.ruleName : missItem?.ruleName
  const expected = expectedScore(itemsA, [patch])
  const comment = `冒烟复核 ${stamp}：改判「${patchName}」`

  /* ---------- 复核参数校验 ---------- */
  /* 取一个落在 0-100 内但偏离改判结果的值，验证的是「口径一致性校验」而非 @Max */
  const inconsistent = expected >= 3 ? expected - 3 : expected + 3
  const badScore = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token,
    { reviewScore: inconsistent, reviewComment: comment, items: [patch] })
  log('复核分与逐条改判不一致被拦截（R-172 口径自洽）',
    badScore.code === 400 && String(badScore.message || '').includes('不一致'),
    `code=${badScore.code} message=${badScore.message}`)

  const outOfRange = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token,
    { reviewScore: 101, reviewComment: comment })
  log('复核分越界被参数校验拦截（R-172 参数校验）', outOfRange.code !== 200,
    `code=${outOfRange.code} message=${outOfRange.message}`)

  const noComment = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token, { items: [patch] })
  log('复核意见必填被拦截（R-172 可追溯性）', noComment.code !== 200,
    `code=${noComment.code} message=${noComment.message}`)

  const foreignItem = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token,
    { reviewComment: comment, items: [{ itemId: 99999999, hit: 0 }] })
  log('改判不属于本记录的明细被拦截（R-172 越权校验）', foreignItem.code === 400,
    `code=${foreignItem.code} message=${foreignItem.message}`)

  /* 扣分必填用例必须显式省略 deductScore：若直接复用带扣分的 patch，请求会合法通过并把记录提前置为已复核 */
  const deductTarget = missItem || itemsA[0]
  const targetForDeduct = deductTarget ? { itemId: deductTarget.id, hit: 1 } : null
  if (targetForDeduct) {
    const noDeduct = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token,
      { reviewComment: comment, items: [targetForDeduct] })
    log('改判为命中但缺扣分被拦截（R-172 扣分必填）', noDeduct.code === 400,
      `code=${noDeduct.code} message=${noDeduct.message}`)
  }

  /* ---------- 提交复核 R-172 ---------- */
  const reviewed = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token,
    { reviewComment: comment, items: [patch] })
  const rev = reviewed.data || {}
  const revItem = (rev.items || []).find((item) => item.id === patch.itemId)
  log('提交复核流转到「已复核」（R-172 状态机）',
    reviewed.code === 200 && rev.status === 'REVIEWED' && rev.reviewAction === 'REVIEW',
    `状态=${rev.status} 动作=${rev.reviewAction} 复核人=${rev.reviewerName}`)

  log('改判后复核分按扣分合计核算（R-172 评分口径）',
    Number(rev.reviewScore) === expected && Number(rev.effectiveScore) === expected,
    `复核分=${rev.reviewScore} 应得=${expected} 生效=${rev.effectiveScore}`)

  log('自动结论不被复核改写（QC-07 人机对比前提）',
    Number(rev.score) === autoScoreA && Number(rev.scoreDiff) === expected - autoScoreA,
    `自动分=${rev.score}（复核前 ${autoScoreA}）分差=${rev.scoreDiff}`)

  log('改判明细留痕且生效结论取人工值（R-172 明细改判）',
    Boolean(revItem) && revItem.overturned === true && Number(revItem.reviewHit) === patch.hit
    && Number(revItem.effectiveHit) === patch.hit,
    `规则=${revItem?.ruleName} 自动命中=${revItem?.hit} 复核命中=${revItem?.reviewHit} 生效扣分=${revItem?.effectiveDeduct}`)

  log('未改判明细不写复核结论（R-172 精准改判）',
    (rev.items || []).filter((item) => item.id !== patch.itemId).every((item) => (item.reviewHit ?? null) === null),
    `改判行数=${(rev.items || []).filter((item) => item.overturned).length}`)

  const reviewAgain = await call('PUT', `/admin/quality/records/${idA}/review`, admin.token,
    { reviewScore: expected, reviewComment: '冒烟：重复提交应被拦截' })
  log('已复核记录重复提交复核被 409 拦截（R-172 状态机）',
    reviewAgain.code === 409 || reviewAgain.http === 409,
    `code=${reviewAgain.code} message=${reviewAgain.message}`)

  /* ---------- 人机对比报表 R-175 ---------- */
  const compare = await call('GET', '/admin/quality/comparison', admin.token)
  const cmp = compare.data || {}
  const diffs = Array.isArray(cmp.diffs) ? cmp.diffs : []
  const diffSum = diffs.reduce((sum, row) => sum + Number(row.count || 0), 0)
  log('人机对比报表返回样本与一致率（R-175）',
    compare.code === 200 && Number(cmp.reviewedCount) >= 1
    && Number(cmp.consistencyRate) >= 0 && Number(cmp.consistencyRate) <= 100
    && Number(cmp.autoPassRate) >= 0 && Number(cmp.reviewPassRate) <= 100,
    `样本=${cmp.reviewedCount} 一致率=${cmp.consistencyRate}% 自动合格率=${cmp.autoPassRate}% 复核合格率=${cmp.reviewPassRate}%`)

  log('一致 / 上调 / 下调之和等于样本数（R-175 口径）',
    Number(cmp.sameCount) + Number(cmp.upCount) + Number(cmp.downCount) === Number(cmp.reviewedCount),
    `一致=${cmp.sameCount} 上调=${cmp.upCount} 下调=${cmp.downCount} 样本=${cmp.reviewedCount}`)

  log('分差分布覆盖七个区间且合计等于样本数（R-175 分桶）',
    diffs.length === 7 && diffSum === Number(cmp.reviewedCount)
    && ['SAME', 'UP_1_5', 'UP_6_15', 'UP_OVER_15', 'DOWN_1_5', 'DOWN_6_15', 'DOWN_OVER_15']
      .every((bucket) => diffs.some((row) => row.bucket === bucket)),
    `区间=${diffs.length} 合计=${diffSum} 样本=${cmp.reviewedCount}`)

  log('逐规则人机对比含改判统计（R-175 明细一致率）',
    Array.isArray(cmp.rules) && cmp.rules.length >= 1 && Number(cmp.itemTotal) >= 1
    && Number(cmp.itemOverturn) >= 1 && Number(cmp.itemAgreeRate) >= 0 && Number(cmp.itemAgreeRate) <= 100,
    `规则行=${cmp.rules?.length} 明细=${cmp.itemTotal} 改判=${cmp.itemOverturn} 一致率=${cmp.itemAgreeRate}%`)

  log('对比样本数 + 待复核数等于区间质检总量（R-175 分母）',
    Number(cmp.reviewedCount) + Number(cmp.pendingCount) === Number(cmp.recordCount),
    `样本=${cmp.reviewedCount} 待复核=${cmp.pendingCount} 总量=${cmp.recordCount}`)

  const badDate = await call('GET', '/admin/quality/comparison?startDate=2026-13-01', admin.token)
  log('对比报表非法日期被拦截（R-175 参数校验）', badDate.code === 400,
    `code=${badDate.code} message=${badDate.message}`)

  /* ---------- 确认归档 R-173 ---------- */
  const confirmed = await call('PUT', `/admin/quality/records/${idA}/confirm`, admin.token,
    { comment: `冒烟确认归档 ${stamp}` })
  const cfm = confirmed.data || {}
  log('复核结论确认归档到终态（R-173 状态机）',
    confirmed.code === 200 && cfm.status === 'CONFIRMED' && cfm.reviewAction === 'CONFIRM',
    `状态=${cfm.status} 动作=${cfm.reviewAction} 生效分=${cfm.effectiveScore}`)

  log('归档保留复核分与改判结果（R-173 口径固化）',
    Number(cfm.reviewScore) === expected && Number(cfm.effectiveScore) === expected
    && (cfm.items || []).some((item) => item.overturned === true),
    `复核分=${cfm.reviewScore} 生效分=${cfm.effectiveScore}`)

  const confirmAgain = await call('PUT', `/admin/quality/records/${idA}/confirm`, admin.token, {})
  log('终态记录重复归档被 409 拦截（R-173 终态不可变更）',
    confirmAgain.code === 409 || confirmAgain.http === 409,
    `code=${confirmAgain.code} message=${confirmAgain.message}`)

  const rejectConfirmed = await call('PUT', `/admin/quality/records/${idA}/reject`, admin.token,
    { comment: '冒烟：终态不应可驳回' })
  log('终态记录驳回被 409 拦截（R-174 状态机）',
    rejectConfirmed.code === 409 || rejectConfirmed.http === 409,
    `code=${rejectConfirmed.code} message=${rejectConfirmed.message}`)

  /* ---------- 列表与概览生效口径 ---------- */
  const confirmedList = await call('GET',
    `/admin/quality/records?pageNum=1&pageSize=10&status=CONFIRMED&keyword=${encodeURIComponent(recA.sessionNo)}`,
    admin.token)
  const confirmedRow = rowsOf(confirmedList).find((row) => row.id === idA)
  log('列表按确认状态可筛且返回生效分（R-166 + R-173）',
    confirmedList.code === 200 && Boolean(confirmedRow)
    && Number(confirmedRow.effectiveScore) === expected && confirmedRow.reviewAction === 'CONFIRM',
    `命中=${rowsOf(confirmedList).length} 生效分=${confirmedRow?.effectiveScore} 动作=${confirmedRow?.reviewAction}`)

  const overview = await call('GET', '/admin/quality/overview', admin.token)
  const ov = overview.data || {}
  log('概览统计复核进度与生效口径（R-169）',
    overview.code === 200 && Number(ov.confirmedCount) >= 1
    && typeof ov.reviewedCount === 'number'
    && Number(ov.passCount) + Number(ov.failCount) === Number(ov.recordCount),
    `记录=${ov.recordCount} 已复核=${ov.reviewedCount} 已确认=${ov.confirmedCount} 合格率=${ov.passRate}%`)

  /* ---------- 驳回退回 R-174 ---------- */
  const detailB = await call('GET', `/admin/quality/records/${idB}`, admin.token)
  const recB = detailB.data || {}
  const autoScoreB = Number(recB.score)
  const adjustedB = Math.max(0, autoScoreB - 5)

  const reviewedB = await call('PUT', `/admin/quality/records/${idB}/review`, admin.token,
    { reviewScore: adjustedB, reviewComment: `冒烟整体调分 ${stamp}：下调 5 分` })
  log('不改判明细时可直接整体调分（R-172 整体调分）',
    reviewedB.code === 200 && reviewedB.data?.status === 'REVIEWED'
    && Number(reviewedB.data?.reviewScore) === adjustedB,
    `自动=${autoScoreB} 复核=${reviewedB.data?.reviewScore} 状态=${reviewedB.data?.status}`)

  log('整体调分不动明细结论（R-172 明细不受影响）',
    (reviewedB.data?.items || []).every((item) => (item.reviewHit ?? null) === null),
    `明细=${(reviewedB.data?.items || []).length}`)

  const rejectNoReason = await call('PUT', `/admin/quality/records/${idB}/reject`, admin.token, {})
  log('驳回缺少原因被拦截（R-174 可追溯性）', rejectNoReason.code === 400,
    `code=${rejectNoReason.code} message=${rejectNoReason.message}`)

  const rejectReason = `冒烟驳回 ${stamp}：调分依据不足，请补充证据`
  const rejected = await call('PUT', `/admin/quality/records/${idB}/reject`, admin.token,
    { comment: rejectReason })
  const rj = rejected.data || {}
  log('驳回复核结论退回待复核（R-174 状态机）',
    rejected.code === 200 && rj.status === 'PENDING' && rj.reviewAction === 'REJECT',
    `状态=${rj.status} 动作=${rj.reviewAction} 原因=${rj.reviewComment}`)

  log('驳回清空复核分并保留自动分（R-174 作废口径）',
    (rj.reviewScore ?? null) === null && Number(rj.score) === autoScoreB && Number(rj.effectiveScore) === autoScoreB,
    `复核分=${rj.reviewScore ?? 'null（字段省略）'} 自动分=${rj.score} 生效分=${rj.effectiveScore}`)

  const rejectAgain = await call('PUT', `/admin/quality/records/${idB}/reject`, admin.token,
    { comment: '冒烟：重复驳回应被拦截' })
  log('待复核记录再次驳回被 409 拦截（R-174 状态机）',
    rejectAgain.code === 409 || rejectAgain.http === 409,
    `code=${rejectAgain.code} message=${rejectAgain.message}`)

  /* ---------- 会话选择器带出质检状态 ---------- */
  const sessions = await call('GET', '/admin/quality/sessions?pageNum=1&pageSize=50', admin.token)
  const sessionRow = rowsOf(sessions).find((row) => row.recordId)
  log('可质检会话带出质检记录状态（R-171）',
    sessions.code === 200 && Boolean(sessionRow) && ['PENDING', 'REVIEWED', 'CONFIRMED'].includes(sessionRow.recordStatus),
    `会话=${sessionRow?.sessionNo} 记录=${sessionRow?.recordId} 状态=${sessionRow?.recordStatus}`)

  /* ---------- 审计留痕（6.4 写操作可审计） ---------- */
  for (const action of ['REVIEW', 'CONFIRM', 'REJECT']) {
    const audit = await call('GET',
      `/admin/audit-logs?pageNum=1&pageSize=10&module=QUALITY&action=${action}`, admin.token)
    const auditRows = rowsOf(audit)
    log(`复核动作 ${action} 写入操作审计（6.4.4）`,
      audit.code === 200 && auditRows.length >= 1 && auditRows.every((row) => row.action === action),
      `审计条数=${auditRows.length} 目标=${auditRows[0]?.targetName}`)
  }
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
