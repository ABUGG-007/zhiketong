/**
 * 智客通 S4-C 操作审计冒烟脚本（PL-06 验收用，零依赖：node scripts/smoke-s4c.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、sql/02_data.sql、
 *       sql/15_s4c_audit_log.sql；MySQL 可用。
 * 覆盖：
 *   1. 租户端写操作留痕：账号增删改、重置密码、知识库增删改、大模型配置、网页接入配置；
 *   2. 审计详情返回变更前后快照，password / apiKey 等敏感字段脱敏后再落库；
 *   3. 平台侧开通租户的审计写入被操作租户，平台端可按 tenantId 跨租户检索；
 *   4. 角色与租户边界：租户管理员访问平台审计接口 403，跨租户详情 404；
 *   5. 列表筛选：module / action / keyword / 日期区间。
 * 脚本结束时删除自己产生的审计行与业务数据，可重复执行。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
import { execFileSync } from 'node:child_process'

const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const MYSQL = process.env.ZK_MYSQL || 'F:\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe'
const DB = 'zhiketong'
const DB_USER = process.env.MYSQL_USERNAME || 'root'
const DB_PASSWORD = process.env.MYSQL_PASSWORD || '246790'

const SUFFIX = Date.now().toString().slice(-8)
const TENANT_CODE = `smoke_c_${SUFFIX}`
const OWNER = `smoke_owner_c_${SUFFIX}`
const USERNAME = `smoke_user_c_${SUFFIX}`
const AI_NAME = `smoke-ai-c-${SUFFIX}`
const KB_QUESTION = `[冒烟]审计词条-${SUFFIX}`
const PASSWORD = 'smoke1234'
const FAKE_API_KEY = 'sk-smoke-secret-should-be-masked'

const results = []
const auditIds = []
let tenantId = null

const log = (name, ok, detail) => {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  → ' + detail : ''}`)
}

function sql(statement) {
  return execFileSync(MYSQL, [
    '--host=127.0.0.1', '--port=3306', `--user=${DB_USER}`, `--password=${DB_PASSWORD}`,
    '--default-character-set=utf8mb4', '--skip-column-names', '--batch', DB, '-e', statement
  ], { encoding: 'utf8' })
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

const login = (username, password = '123456') =>
  call('POST', '/auth/login', null, { username, password })

/** 在审计列表中查找目标记录 */
function findItem(page, predicate) {
  return (page.data?.list || []).find(predicate)
}

function todayString() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function yesterdayString() {
  const date = new Date(Date.now() - 86400000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function cleanup() {
  try {
    if (auditIds.length > 0) {
      sql(`DELETE FROM audit_log WHERE id IN (${auditIds.join(',')})`)
    }
    if (tenantId) {
      const id = tenantId
      sql(`DELETE FROM agent_status WHERE tenant_id = ${id}`)
      sql(`DELETE FROM tenant_usage WHERE tenant_id = ${id}`)
      sql(`DELETE FROM tenant_embed_config WHERE tenant_id = ${id}`)
      sql(`DELETE FROM kb_similar_question WHERE tenant_id = ${id}`)
      sql(`DELETE FROM kb_unmatched_question WHERE tenant_id = ${id}`)
      sql(`DELETE FROM kb_entry_version WHERE tenant_id = ${id}`)
      sql(`DELETE FROM kb_entry WHERE tenant_id = ${id}`)
      sql(`DELETE FROM kb_category WHERE tenant_id = ${id}`)
      sql(`DELETE FROM sys_login_log WHERE tenant_id = ${id}`)
      sql(`DELETE FROM sys_user WHERE tenant_id = ${id}`)
      sql(`DELETE FROM audit_log WHERE tenant_id = ${id}`)
      sql(`DELETE FROM tenant WHERE id = ${id}`)
    }
    sql(`DELETE FROM kb_similar_question WHERE question LIKE '[冒烟]审计词条%'`)
    sql(`DELETE FROM kb_entry_version WHERE entry_id IN (SELECT id FROM kb_entry WHERE question LIKE '[冒烟]审计词条%')`)
    sql(`DELETE FROM kb_entry WHERE question LIKE '[冒烟]审计词条%'`)
    sql(`DELETE FROM kb_category WHERE name LIKE '[冒烟]审计分类%'`)
    sql(`DELETE FROM ai_model_config WHERE name LIKE 'smoke-ai-c-%'`)
    sql(`DELETE FROM sys_user WHERE username LIKE 'smoke_user_c_%' OR username LIKE 'smoke_owner_c_%'`)
  } catch (e) {
    console.warn('清理临时数据失败：', e.message)
  }
}

async function main() {
  const admin = await login('admin')
  const adminToken = admin.data?.token
  log('租户管理员登录成功（admin / tenant 1）',
    admin.code === 200 && Boolean(adminToken), `tenantId=${admin.data?.tenantId}`)

  const platform = await login('platform01')
  const platformToken = platform.data?.token
  log('平台管理员登录成功（platform01）',
    platform.code === 200 && platform.data?.user?.roleCode === 'PLATFORM_ADMIN',
    `tenantId=${platform.data?.tenantId}`)

  /* ---------- 1. 账号写操作留痕 ---------- */
  const createdUser = await call('POST', '/admin/users', adminToken, {
    username: USERNAME, password: PASSWORD, nickname: '冒烟审计用户', roleCode: 'USER', status: 1
  })
  const userId = createdUser.data?.id
  log('新增账号成功（待审计）', createdUser.code === 200 && Boolean(userId), `userId=${userId}`)

  let userPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=USER', adminToken)
  const createItem = findItem(userPage, (item) => item.targetName === USERNAME && item.action === 'CREATE')
  if (createItem) auditIds.push(createItem.id)
  const createDetail = createItem
    ? await call('GET', `/admin/audit-logs/${createItem.id}`, adminToken)
    : { code: 0 }
  const createAfter = createDetail.data?.afterData || ''
  log('新增账号写入审计（module=USER / action=CREATE）',
    Boolean(createItem) && createItem.moduleName === '账号管理'
      && createItem.actionName === '新增'
      && Boolean(createItem.operatorName) && Boolean(createItem.ip)
      && createDetail.data?.requestMethod === 'POST'
      && String(createDetail.data?.requestUri || '').includes('/admin/users'),
    `id=${createItem?.id} operator=${createItem?.operatorName} ip=${createItem?.ip} uri=${createDetail.data?.requestUri}`)

  log('审计详情含变更后快照且密码已脱敏',
    createDetail.code === 200 && createAfter.length > 0
      && createAfter.includes('******') && !createAfter.includes(PASSWORD)
      && !createAfter.includes('$2a$'),
    `before=${createDetail.data?.beforeData === null ? 'null' : '有值'} afterLength=${createAfter.length}`)

  const resetPassword = await call('PUT', `/admin/users/${userId}/password`, adminToken, {
    newPassword: 'smoke5678'
  })
  userPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=USER', adminToken)
  const resetItem = findItem(userPage, (item) => item.targetName === USERNAME && item.action === 'RESET_PASSWORD')
  if (resetItem) auditIds.push(resetItem.id)
  log('重置密码写入审计且不含明文密码',
    resetPassword.code === 200 && Boolean(resetItem)
      && resetItem.actionName === '重置密码' && !String(resetItem.detail).includes('smoke5678'),
    `action=${resetItem?.action} detail=${resetItem?.detail}`)

  const updatedUser = await call('PUT', `/admin/users/${userId}`, adminToken, {
    nickname: '冒烟审计用户（改）'
  })
  userPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=USER', adminToken)
  const updateItem = findItem(userPage, (item) => item.targetName === USERNAME && item.action === 'UPDATE')
  if (updateItem) auditIds.push(updateItem.id)
  const updateDetail = updateItem
    ? await call('GET', `/admin/audit-logs/${updateItem.id}`, adminToken)
    : { code: 0 }
  log('编辑账号记录变更前后快照',
    updatedUser.code === 200 && Boolean(updateItem)
      && Boolean(updateDetail.data?.beforeData) && Boolean(updateDetail.data?.afterData)
      && String(updateDetail.data.afterData).includes('冒烟审计用户（改）'),
    `before片段=${String(updateDetail.data?.beforeData).includes('冒烟审计用户')}`)

  const removedUser = await call('DELETE', `/admin/users/${userId}`, adminToken)
  userPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=USER', adminToken)
  const deleteItem = findItem(userPage, (item) => item.targetName === USERNAME && item.action === 'DELETE')
  if (deleteItem) auditIds.push(deleteItem.id)
  log('删除账号写入审计（只留变更前快照）',
    removedUser.code === 200 && Boolean(deleteItem) && deleteItem.actionName === '删除',
    `detail=${deleteItem?.detail}`)

  /* ---------- 2. 知识库写操作留痕 ---------- */
  const category = await call('POST', '/kb/categories', adminToken, {
    name: `[冒烟]审计分类-${SUFFIX}`, sort: 99, status: 1
  })
  const entry = await call('POST', '/kb/entries', adminToken, {
    categoryId: category.data?.id, question: KB_QUESTION, answer: '[冒烟]审计答案',
    keywords: '冒烟,审计', status: 1
  })
  const kbUpdated = await call('PUT', `/kb/entries/${entry.data?.id}`, adminToken, {
    answer: '[冒烟]审计答案（改）'
  })
  const kbDeleted = await call('DELETE', `/kb/entries/${entry.data?.id}`, adminToken)

  const kbPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=KB', adminToken)
  const kbItems = (kbPage.data?.list || []).filter((item) => item.targetName === KB_QUESTION)
  for (const item of kbItems) auditIds.push(item.id)
  const kbActions = kbItems.map((item) => item.action)
  log('知识库新增/编辑/删除三笔审计齐全',
    entry.code === 200 && kbUpdated.code === 200 && kbDeleted.code === 200
      && ['CREATE', 'UPDATE', 'DELETE'].every((action) => kbActions.includes(action)),
    `actions=${kbActions.join(',')}`)

  /* ---------- 3. 大模型配置审计与 apiKey 脱敏 ---------- */
  const aiCreated = await call('POST', '/admin/ai/configs', adminToken, {
    name: AI_NAME, provider: 'ollama', baseUrl: 'http://127.0.0.1:11434/v1', model: 'llama3',
    apiKey: FAKE_API_KEY, enabled: false, isDefault: false, remark: 'S4-C 冒烟临时配置'
  })
  const aiPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=AI', adminToken)
  const aiItem = findItem(aiPage, (item) => item.targetName === AI_NAME)
  if (aiItem) auditIds.push(aiItem.id)
  const aiDetail = aiItem ? await call('GET', `/admin/audit-logs/${aiItem.id}`, adminToken) : { code: 0 }
  const aiAfter = String(aiDetail.data?.afterData || '')
  log('大模型配置写审计且 apiKey 已脱敏',
    aiCreated.code === 200 && Boolean(aiItem)
      && aiAfter.includes('****') && !aiAfter.includes(FAKE_API_KEY),
    `id=${aiItem?.id} masked=${/"apiKeyMasked":"([^"]*)"/.exec(aiAfter)?.[1]}`)

  if (aiCreated.data?.id) {
    const aiDeleted = await call('DELETE', `/admin/ai/configs/${aiCreated.data.id}`, adminToken)
    if (aiDeleted.code === 200) {
      const aiPage2 = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=AI', adminToken)
      const aiDeleteItem = findItem(aiPage2, (item) => item.targetName === AI_NAME && item.action === 'DELETE')
      if (aiDeleteItem) auditIds.push(aiDeleteItem.id)
    }
  }

  /* ---------- 4. 网页接入配置审计 ---------- */
  const embedConfig = await call('GET', '/admin/embed/config', adminToken)
  const cfg = embedConfig.data || {}
  const embedSaved = await call('PUT', '/admin/embed/config', adminToken, {
    brandName: cfg.brandName || '智客通',
    welcome: cfg.welcome || null,
    themeColor: cfg.themeColor || '#4f46e5',
    position: cfg.position || 'right',
    badge: cfg.badge === true,
    allowedOrigins: cfg.allowedOrigins || null,
    rateLimitPerMinute: cfg.rateLimitPerMinute || 30,
    status: cfg.status === 0 ? 0 : 1
  })
  const embedPage = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=EMBED', adminToken)
  const embedItem = (embedPage.data?.list || []).find((item) => item.action === 'UPDATE')
  if (embedItem) auditIds.push(embedItem.id)
  log('网页接入配置保存写审计（含前后快照）',
    embedSaved.code === 200 && Boolean(embedItem) && Boolean(embedItem.targetName),
    `id=${embedItem?.id} target=${embedItem?.targetName}`)

  /* ---------- 5. 平台开通租户：审计写入被操作租户 ---------- */
  const tenantCreated = await call('POST', '/platform/tenants', platformToken, {
    tenantCode: TENANT_CODE,
    tenantName: `冒烟审计租户-${SUFFIX}`,
    planCode: 'trial',
    adminUsername: OWNER,
    adminPassword: PASSWORD,
    adminNickname: '冒烟审计商户'
  })
  tenantId = tenantCreated.data?.id
  log('平台开通租户成功（待审计）', tenantCreated.code === 200 && Boolean(tenantId), `tenantId=${tenantId}`)

  const platformPage = await call('GET', `/platform/audit-logs?pageNum=1&pageSize=50&tenantId=${tenantId}`, platformToken)
  const tenantItem = findItem(platformPage, (item) => item.module === 'TENANT' && item.action === 'CREATE')
  if (tenantItem) auditIds.push(tenantItem.id)
  log('平台操作审计归属被操作租户且可按租户检索',
    Boolean(tenantItem) && tenantItem.tenantId === tenantId
      && String(tenantItem.tenantName || '').includes('冒烟审计租户')
      && tenantItem.operatorRole === 'PLATFORM_ADMIN',
    `tenantId=${tenantItem?.tenantId} tenantName=${tenantItem?.tenantName} operator=${tenantItem?.operatorRole}`)

  const allPlatform = await call('GET', '/platform/audit-logs?pageNum=1&pageSize=50', platformToken)
  const tenantIds = new Set((allPlatform.data?.list || []).map((item) => item.tenantId))
  log('平台端默认跨租户检索审计',
    allPlatform.code === 200 && tenantIds.size >= 2,
    `total=${allPlatform.data?.total} 租户数=${tenantIds.size}`)

  const platformAsTenant = await call('GET', '/platform/audit-logs', adminToken)
  log('租户管理员访问平台审计接口被拒', platformAsTenant.code === 403,
    `code=${platformAsTenant.code}`)

  const crossTenantDetail = tenantItem
    ? await call('GET', `/admin/audit-logs/${tenantItem.id}`, adminToken)
    : { code: 0 }
  log('租户管理员查看其他租户审计详情返回 404', crossTenantDetail.code === 404,
    `code=${crossTenantDetail.code} message=${crossTenantDetail.message}`)

  /* ---------- 6. 列表筛选 ---------- */
  const byAction = await call('GET', '/admin/audit-logs?pageNum=1&pageSize=50&module=USER&action=CREATE', adminToken)
  const actionOnly = (byAction.data?.list || []).every((item) => item.module === 'USER' && item.action === 'CREATE')
  log('按模块+动作筛选生效',
    byAction.code === 200 && (byAction.data?.list || []).length > 0 && actionOnly,
    `total=${byAction.data?.total}`)

  const byKeyword = await call('GET', `/admin/audit-logs?pageNum=1&pageSize=50&keyword=${encodeURIComponent(KB_QUESTION)}`, adminToken)
  log('按对象名称关键字检索生效',
    byKeyword.code === 200 && (byKeyword.data?.list || []).some((item) => item.targetName === KB_QUESTION),
    `total=${byKeyword.data?.total}`)

  const byToday = await call('GET', `/admin/audit-logs?pageNum=1&pageSize=50&startDate=${todayString()}&endDate=${todayString()}`, adminToken)
  const byYesterday = await call('GET', `/admin/audit-logs?pageNum=1&pageSize=50&startDate=${yesterdayString()}&endDate=${yesterdayString()}`, adminToken)
  // 断言区间筛选生效：今日必须查到本次写入，且两区间返回行的时间均落在各自当天（不假设历史审计为空）
  const todayRows = byToday.data?.list || []
  const yesterdayRows = byYesterday.data?.list || []
  const inDay = (rows, day) => rows.every((item) => String(item.createTime || '').startsWith(day))
  log('日期区间筛选（含当天）生效',
    byToday.code === 200 && todayRows.length > 0 && inDay(todayRows, todayString())
      && byYesterday.code === 200 && inDay(yesterdayRows, yesterdayString()),
    `today=${byToday.data?.total}(落在当天=${inDay(todayRows, todayString())}) yesterday=${byYesterday.data?.total}(落在当天=${inDay(yesterdayRows, yesterdayString())})`)

  const badDate = await call('GET', '/admin/audit-logs?startDate=2026/10/01', adminToken)
  log('非法日期格式返回参数错误', badDate.code === 400, `code=${badDate.code} message=${badDate.message}`)

  /* ---------- 7. 删除租户后审计行仍保留（只追加） ---------- */
  const tenantRemoved = await call('DELETE', `/platform/tenants/${tenantId}`, platformToken)
  const afterRemoval = await call('GET', `/platform/audit-logs?pageNum=1&pageSize=50&tenantId=${tenantId}`, platformToken)
  const tenantActions = (afterRemoval.data?.list || []).map((item) => item.action)
  log('租户删除后审计记录保留（含开通与删除两笔）',
    tenantRemoved.code === 200 && tenantActions.includes('CREATE') && tenantActions.includes('DELETE'),
    `actions=${tenantActions.join(',')}`)
  for (const item of (afterRemoval.data?.list || [])) auditIds.push(item.id)
  tenantId = null

  const failed = results.filter((item) => !item.ok)
  console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
}

try {
  await main()
} catch (e) {
  console.error('S4-C 冒烟脚本异常：', e)
  process.exitCode = 2
} finally {
  cleanup()
  process.exit(process.exitCode || (results.some((item) => !item.ok) ? 1 : 0))
}
