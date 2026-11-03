/**
 * S1 知识库运营闭环接口自测
 * 用法：node scripts/s1-smoke.mjs [baseUrl]
 * 覆盖：健康度 / 查重 / 词条创建（含标签+相似问法）/ 版本与回滚 / 相似问法 /
 *      AI 扩写 / 未命中采集（站内会话）/ 转词条 / 知识包导入 / 批次列表 / 权限
 */
const base = (process.argv[2] || 'http://127.0.0.1:8080').replace(/\/$/, '')
let failed = 0
const log = (...args) => console.log(...args)
const ok = (label, extra = '') => log(`  [OK] ${label}${extra ? ' -> ' + extra : ''}`)
const bad = (label, extra = '') => {
  failed += 1
  log(`  [FAIL] ${label}${extra ? ' -> ' + extra : ''}`)
}
const expect = (label, cond, extra = '') => (cond ? ok(label, extra) : bad(label, extra))

async function call(method, path, { body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let data = null
  try {
    data = await res.json()
  } catch {
    data = { raw: await res.text().catch(() => '') }
  }
  return { status: res.status, body: data }
}

async function login(username, password = '123456') {
  const { body } = await call('POST', '/api/auth/login', { body: { username, password } })
  if (body?.code !== 200) throw new Error(`登录失败 ${username}: ${JSON.stringify(body)}`)
  return body.data.token
}

const main = async () => {
  // 每次运行使用独立标记，避免上一轮采集的未命中记录干扰断言
  const runId = Date.now().toString(36)
  const admin = await login('admin')
  const user = await login('user01')
  ok('登录 admin / user01')

  /* 1. 健康度 */
  const health = await call('GET', '/api/kb/health', { token: admin })
  expect('GET /api/kb/health 200', health.body?.code === 200, health.body?.message || '')
  const h = health.body?.data || {}
  expect(
    '健康度字段齐备',
    ['entryTotal', 'entryEnabled', 'similarTotal', 'unmatchedPending', 'zeroHitEntries', 'duplicatePairs', 'topHits', 'trend', 'categories'].every(
      (key) => key in h
    ),
    `entryTotal=${h.entryTotal}, similarTotal=${h.similarTotal}, unmatchedPending=${h.unmatchedPending}`
  )

  /* 2. 准备分类 */
  const catRes = await call('GET', '/api/kb/categories?tree=false', { token: admin })
  const categories = catRes.body?.data || []
  let categoryId = categories.find((item) => Number(item.status) === 1)?.id
  if (!categoryId) {
    const created = await call('POST', '/api/kb/categories', {
      token: admin,
      body: { name: 'S1自测分类', description: 'S1 接口自测', sort: 99, status: 1 }
    })
    categoryId = created.body?.data?.id
  }
  expect('获取可用分类', Boolean(categoryId), `categoryId=${categoryId}`)

  /* 3. 创建词条（带标签 + 相似问法） */
  const question = `S1自测：如何开启自动回复${Date.now()}`
  const created = await call('POST', '/api/kb/entries', {
    token: admin,
    body: {
      categoryId,
      question,
      answer: '进入「系统设置 - 接待参数」开启自动回复，并填写欢迎语后保存即可生效。',
      keywords: 's1自测,自动回复',
      tags: '自测,配置',
      similarQuestions: ['怎么设置自动回复', '自动回复怎么配置'],
      status: 1
    }
  })
  const entryId = created.body?.data?.id
  expect('POST /api/kb/entries 创建词条', created.body?.code === 200 && Boolean(entryId), `entryId=${entryId}`)

  const detail = await call('GET', `/api/kb/entries/${entryId}`, { token: admin })
  const d = detail.body?.data || {}
  expect('详情返回标签', d.tags === '自测,配置', `tags=${d.tags}`)
  expect('详情返回相似问法', Array.isArray(d.similarQuestions) && d.similarQuestions.length === 2, `similarCount=${d.similarCount}`)

  /* 4. 版本记录 */
  const v1 = await call('GET', `/api/kb/entries/${entryId}/versions`, { token: admin })
  const versions1 = v1.body?.data || []
  expect('GET versions 创建版本', versions1.length >= 1, `count=${versions1.length}, type=${versions1[0]?.changeType}`)

  /* 5. 相似问法增删 */
  const addSimilar = await call('POST', `/api/kb/entries/${entryId}/similar-questions`, {
    token: admin,
    body: { questions: ['自动回复在哪里设置'], source: 'MANUAL' }
  })
  expect('POST similar-questions 新增', addSimilar.body?.code === 200)
  const detail2 = await call('GET', `/api/kb/entries/${entryId}`, { token: admin })
  expect('相似问法计数变为 3', detail2.body?.data?.similarCount === 3, `similarCount=${detail2.body?.data?.similarCount}`)

  const expand = await call('POST', `/api/kb/entries/${entryId}/similar-questions/expand`, { token: admin })
  if (expand.body?.code === 200) {
    ok('POST similar-questions/expand AI 扩写', `candidates=${expand.body?.data?.candidates?.length ?? 0}`)
  } else {
    log(`  [SKIP] AI 扩写未返回成功（大模型未配置或调用失败）：${expand.body?.message || expand.status}`)
  }

  /* 6. 编辑触发第二个版本并回滚 */
  const updated = await call('PUT', `/api/kb/entries/${entryId}`, {
    token: admin,
    body: {
      answer: '进入「系统设置 - 接待参数」开启自动回复，填写欢迎语与兜底话术后保存生效。',
      similarQuestions: ['怎么设置自动回复', '自动回复怎么配置', '自动回复在哪里设置']
    }
  })
  expect('PUT /api/kb/entries 更新词条', updated.body?.code === 200)
  const versions2 = (await call('GET', `/api/kb/entries/${entryId}/versions`, { token: admin })).body?.data || []
  const rollbackTarget = versions2.find((item) => item.beforeContent)
  expect('更新后产生新版本', versions2.length >= 2, `count=${versions2.length}`)

  if (rollbackTarget) {
    const rollback = await call('POST', `/api/kb/versions/${rollbackTarget.id}/rollback`, { token: admin })
    expect('POST versions/{id}/rollback 回滚', rollback.body?.code === 200, rollback.body?.message || '')
    const afterRollback = await call('GET', `/api/kb/entries/${entryId}`, { token: admin })
    expect(
      '回滚后答案恢复旧值',
      String(afterRollback.body?.data?.answer || '').includes('保存即可生效'),
      String(afterRollback.body?.data?.answer || '').slice(0, 24)
    )
  } else {
    bad('未找到可回滚版本（beforeContent 为空）')
  }

  /* 7. 未命中采集：站内会话发一条无覆盖问题 */
  const sessionRes = await call('POST', '/api/chat/sessions', { token: user })
  let sessionId = sessionRes.body?.data?.session?.id
  if (!sessionId) {
    // 已有进行中会话时创建接口会拦截，改为复用当前会话
    const current = await call('GET', '/api/chat/sessions/current', { token: user })
    sessionId = current.body?.data?.id
    log(`  [INFO] 复用进行中会话，sessionId=${sessionId}`)
  }
  expect('POST /api/chat/sessions 创建会话', Boolean(sessionId), `sessionId=${sessionId}`)
  const askContent = `S1未命中${runId}：火星车轮胎在真空环境下的保修政策是怎样的`
  const send = await call('POST', `/api/chat/sessions/${sessionId}/messages`, {
    token: user,
    body: { content: askContent, contentType: 'TEXT', clientMsgId: `s1verify-${Date.now()}-a` }
  })
  const firstCategory = send.body?.data?.aiReply?.answerCategory
  expect(
    'POST chat messages（五分类应答）',
    send.body?.code === 200 && ['DIRECT', 'REFERENCE', 'FREE', 'SUGGEST', 'CHITCHAT'].includes(firstCategory),
    `category=${firstCategory}, aiModel=${send.body?.data?.aiReply?.aiModel}`
  )

  // 规则：只有「本轮没有给出有效答案」才算未命中，大模型正常回答不应污染看板
  const noMiss = await call('GET', `/api/kb/unmatched-questions?pageNum=1&pageSize=5&keyword=${runId}`, { token: admin })
  expect('有效回答不计入未命中', (noMiss.body?.data?.total || 0) === 0, `total=${noMiss.body?.data?.total}`)

  // 停用大模型模拟不可用，验证 SUGGEST/FALLBACK 降级与未命中落库
  const configs = await call('GET', '/api/admin/ai/configs', { token: admin })
  const enabledConfigs = (configs.body?.data || []).filter((item) => Number(item.enabled) === 1)
  let record = null
  try {
    for (const cfg of enabledConfigs) {
      const off = await call('PUT', `/api/admin/ai/configs/${cfg.id}/status?enabled=false`, { token: admin })
      expect(`停用大模型配置 ${cfg.name || cfg.id}`, off.body?.code === 200, off.body?.message || '')
    }
    const degraded = await call('POST', `/api/chat/sessions/${sessionId}/messages`, {
      token: user,
      body: {
        content: `S1未命中${runId}：火星车轮胎在真空环境下还能保修吗`,
        contentType: 'TEXT',
        clientMsgId: `s1verify-${Date.now()}-b`
      }
    })
    const degradedCategory = degraded.body?.data?.aiReply?.answerCategory
    expect(
      '大模型不可用时降级为引导话术/兜底',
      ['SUGGEST', 'FALLBACK'].includes(degradedCategory),
      `category=${degradedCategory}, aiModel=${degraded.body?.data?.aiReply?.aiModel}, fallbackCount=${degraded.body?.data?.fallbackCount}`
    )
    const unmatched = await call('GET', `/api/kb/unmatched-questions?pageNum=1&pageSize=5&status=PENDING&keyword=${runId}`, { token: admin })
    record = (unmatched.body?.data?.list || [])[0]
    expect('降级后未命中问题已落库', Boolean(record), `total=${unmatched.body?.data?.total}`)
  } finally {
    for (const cfg of enabledConfigs) {
      await call('PUT', `/api/admin/ai/configs/${cfg.id}/status?enabled=true`, { token: admin })
    }
    ok('已恢复大模型启用状态')
  }

  if (record) {
    const convert = await call('POST', `/api/kb/unmatched-questions/${record.id}/convert`, {
      token: admin,
      body: {
        categoryId,
        question: '火星车轮胎保修政策',
        answer: '火星车轮胎不属于真空环境保修范围，地面测试场景下提供 12 个月质保。',
        keywords: '火星车,轮胎,保修',
        tags: '自测',
        keepAsSimilar: true
      }
    })
    const convertedEntryId = convert.body?.data?.entryId
    expect('POST unmatched-questions/{id}/convert 转词条', convert.body?.code === 200 && Boolean(convertedEntryId), `entryId=${convertedEntryId}`)
    if (convertedEntryId) {
      const convertedDetail = await call('GET', `/api/kb/entries/${convertedEntryId}`, { token: admin })
      expect(
        '转词条保留原问法为相似问法',
        (convertedDetail.body?.data?.similarQuestions || []).some((item) => item.question === record.question),
        `similarCount=${convertedDetail.body?.data?.similarCount}`
      )
    }
    const afterConvert = await call('GET', `/api/kb/unmatched-questions?pageNum=1&pageSize=5&status=CONVERTED&keyword=${runId}`, { token: admin })
    expect('未命中状态更新为 CONVERTED', (afterConvert.body?.data?.total || 0) >= 1)
    await call('DELETE', `/api/kb/entries/${convertedEntryId}`, { token: admin })
  }

  /* 8. 查重 */
  const dup = await call('GET', '/api/kb/duplicates?limit=10', { token: admin })
  expect('GET /api/kb/duplicates 200', dup.body?.code === 200 && Array.isArray(dup.body?.data), `pairs=${dup.body?.data?.length}`)

  /* 9. 知识包导入 + 批次 */
  const packName = `S1自测知识包${Date.now()}`
  const imported = await call('POST', '/api/kb/import', {
    token: admin,
    body: {
      packageName: packName,
      entries: [
        {
          categoryName: 'S1自测分类',
          question: '知识包自测：发票如何开具',
          answer: '在「我的订单」选择对应订单，点击「申请开票」填写抬头后提交，1 个工作日内开出。',
          keywords: ['发票', '开票'],
          tags: ['自测'],
          similarQuestions: ['怎么开发票', '发票怎么申请']
        },
        {
          categoryName: 'S1自测分类',
          question: '知识包自测：发票如何开具',
          answer: '重复条目应被跳过',
          keywords: ['发票'],
          tags: [],
          similarQuestions: []
        }
      ]
    }
  })
  const importResult = imported.body?.data || {}
  expect(
    'POST /api/kb/import 导入知识包',
    imported.body?.code === 200,
    `status=${importResult.status}, success=${importResult.successCount}, fail=${importResult.failCount}`
  )
  expect('重复条目被跳过', importResult.successCount === 1 && importResult.failCount === 1, `errors=${JSON.stringify(importResult.errors || [])}`)

  const tasks = await call('GET', '/api/kb/import/tasks?limit=5', { token: admin })
  expect(
    'GET /api/kb/import/tasks 返回批次',
    (tasks.body?.data || []).some((item) => item.packageName === packName),
    `count=${(tasks.body?.data || []).length}`
  )

  /* 10. 清理：删除主词条与导入词条 */
  await call('DELETE', `/api/kb/entries/${entryId}`, { token: admin })
  const search = await call('GET', `/api/kb/entries?pageNum=1&pageSize=20&keyword=${encodeURIComponent('知识包自测')}`, { token: admin })
  for (const item of search.body?.data?.list || []) {
    await call('DELETE', `/api/kb/entries/${item.id}`, { token: admin })
  }
  ok('清理自测数据')

  /* 11. 权限校验：AGENT 不得调用诊断接口 */
  const agent = await login('agent01')
  const denied = await call('GET', '/api/kb/health', { token: agent })
  expect('AGENT 访问 /api/kb/health 被拒绝', denied.status === 403 || denied.body?.code === 403, `status=${denied.status}, code=${denied.body?.code}`)

  log(`\n=== 自测完成：失败 ${failed} 项 ===`)
  if (failed > 0) process.exitCode = 1
}

main().catch((err) => {
  console.error('自测异常中断：', err)
  process.exitCode = 2
})
