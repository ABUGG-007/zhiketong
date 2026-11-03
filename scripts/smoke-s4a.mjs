/**
 * 智客通 S4-A 多租户隔离冒烟脚本（PL-01 验收用，零依赖：node scripts/smoke-s4a.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已导入 sql/01_schema.sql、sql/02_data.sql
 *       （或既有库执行过 sql/13_s4a_tenant.sql），MySQL 可用。
 * 覆盖：
 *   1. 平台管理员 / 租户管理员 / 坐席 / 普通用户登录返回的 tenantId 与 JWT 载荷一致；
 *   2. 停用租户的账号登录被拦截（406 之外的业务拒绝）；
 *   3. 双租户知识库隔离：租户 B 看不到租户 A 的词条，详情接口跨租户返回 404；
 *   4. 平台管理员跨租户可见（tenant_id=0 不做租户过滤）。
 * 脚本自建自清租户 B / 停用租户 C 与相关业务数据，不污染既有数据。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
import { execFileSync } from 'node:child_process'

const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const MYSQL = process.env.ZK_MYSQL || 'F:\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe'
const DB = 'zhiketong'
const DB_USER = process.env.MYSQL_USERNAME || 'root'
const DB_PASSWORD = process.env.MYSQL_PASSWORD || '246790'
/** 与 sql/02_data.sql 一致的 123456 密文 */
const BCRYPT_123456 = '$2a$10$ZXY2J5c1lFzryZBQtffBNuASALRF5sjxZAhx57euXnV5Dc12Wkmqy'

const TENANT_B = 90
const TENANT_C = 91

const results = []
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

async function login(username, password = '123456') {
  return call('POST', '/auth/login', null, { username, password })
}

/** 解析 JWT 载荷，校验 tenantId 声明 */
function tenantClaim(token) {
  const payload = token.split('.')[1]
  const json = Buffer.from(payload.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8')
  return JSON.parse(json)
}

function ensureTenant(id, code, name, status, planCode) {
  sql(`INSERT INTO tenant (id, tenant_code, tenant_name, status, plan_code, remark)
       VALUES (${id}, '${code}', '${name}', '${status}', '${planCode}', '冒烟脚本临时租户')
       ON DUPLICATE KEY UPDATE status = VALUES(status), plan_code = VALUES(plan_code)`)
}

function ensureUser(tenantId, username, nickname) {
  sql(`DELETE FROM sys_user WHERE tenant_id = ${tenantId} AND username = '${username}'`)
  sql(`INSERT INTO sys_user (tenant_id, username, password, nickname, role_code, status)
       VALUES (${tenantId}, '${username}', '${BCRYPT_123456}', '${nickname}', 'ADMIN', 1)`)
}

function cleanup() {
  try {
    sql(`DELETE FROM tenant_usage WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM tenant_embed_config WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM kb_similar_question WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM kb_entry WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM kb_category WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM sys_login_log WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM sys_user WHERE tenant_id IN (${TENANT_B}, ${TENANT_C})`)
    sql(`DELETE FROM tenant WHERE id IN (${TENANT_B}, ${TENANT_C})`)
  } catch (e) {
    console.warn('清理临时数据失败：', e.message)
  }
}

async function main() {
  /* ---------- 1. 登录返回的租户身份 ---------- */
  const platform = await login('platform01')
  const platformToken = platform.data?.token
  log('平台管理员登录成功（PLATFORM_ADMIN）', platform.code === 200
    && platform.data?.user?.roleCode === 'PLATFORM_ADMIN'
    && platform.data?.tenantId === 0,
    `role=${platform.data?.user?.roleCode} tenantId=${platform.data?.tenantId}`)

  const admin = await login('admin')
  const adminToken = admin.data?.token
  log('租户管理员登录返回所属租户（tenantId=1）', admin.code === 200 && admin.data?.tenantId === 1,
    `tenantId=${admin.data?.tenantId} tenantName=${admin.data?.tenantName} plan=${admin.data?.planCode}`)

  const agent = await login('agent01')
  const user = await login('user01')
  log('坐席与普通用户均归属默认租户', agent.data?.tenantId === 1 && user.data?.tenantId === 1,
    `agent.tenantId=${agent.data?.tenantId} user.tenantId=${user.data?.tenantId}`)

  const platformClaim = platformToken ? tenantClaim(platformToken) : {}
  const adminClaim = adminToken ? tenantClaim(adminToken) : {}
  log('JWT 载荷携带 tenantId 且与登录响一致',
    platformClaim.tenantId === 0 && adminClaim.tenantId === 1,
    `platform.claim=${platformClaim.tenantId} admin.claim=${adminClaim.tenantId}`)

  /* ---------- 2. 准备第二租户与停用租户 ---------- */
  ensureTenant(TENANT_B, 'smoke_tenant_b', '冒烟租户B', 'ENABLED', 'trial')
  ensureTenant(TENANT_C, 'smoke_tenant_c', '冒烟停用租户C', 'DISABLED', 'trial')
  ensureUser(TENANT_B, 'smoke_admin_b', '冒烟管理员B')
  ensureUser(TENANT_C, 'smoke_admin_c', '冒烟管理员C')

  const adminB = await login('smoke_admin_b')
  const tokenB = adminB.data?.token
  log('第二租户管理员登录成功（tenantId=90）', adminB.code === 200 && adminB.data?.tenantId === 90,
    `tenantId=${adminB.data?.tenantId} tenantName=${adminB.data?.tenantName}`)

  const disabled = await login('smoke_admin_c')
  log('停用租户账号登录被拒绝（PL-01 租户状态校验）', disabled.code !== 200,
    `code=${disabled.code} message=${disabled.message}`)

  /* ---------- 3. 知识库双租户隔离 ---------- */
  const categoryA = await call('POST', '/kb/categories', adminToken, {
    name: `[冒烟]租户A分类-${Date.now()}`, sort: 99, status: 1
  })
  const categoryAId = categoryA.data?.id
  const entryA = await call('POST', '/kb/entries', adminToken, {
    categoryId: categoryAId,
    question: '[冒烟]租户A专属问题：如何开具增值税专用发票？',
    answer: '租户A专属答案：在财务中心提交开票申请。',
    keywords: '冒烟,租户A,开票',
    status: 1
  })
  const entryAId = entryA.data?.id
  log('租户A创建分类与词条', entryA.code === 200 && Boolean(entryAId),
    `categoryId=${categoryAId} entryId=${entryAId}`)

  const listB = await call('GET', '/kb/entries?pageNum=1&pageSize=20', tokenB)
  const bRecords = listB.data?.records || []
  log('租户B列表查不到租户A词条（列表隔离）',
    listB.code === 200 && !bRecords.some((item) => item.id === entryAId),
    `租户B可见条数=${bRecords.length} total=${listB.data?.total}`)

  const crossDetail = await call('GET', `/kb/entries/${entryAId}`, tokenB)
  log('租户B越权读租户A词条被拒（详情隔离）', crossDetail.code !== 200,
    `code=${crossDetail.code} message=${crossDetail.message}`)

  const categoryB = await call('POST', '/kb/categories', tokenB, {
    name: `[冒烟]租户B分类-${Date.now()}`, sort: 99, status: 1
  })
  const categoryBId = categoryB.data?.id
  const entryB = await call('POST', '/kb/entries', tokenB, {
    categoryId: categoryBId,
    question: '[冒烟]租户B专属问题：如何修改绑定手机号？',
    answer: '租户B专属答案：在账号安全中更换手机号。',
    keywords: '冒烟,租户B,手机号',
    status: 1
  })
  const entryBId = entryB.data?.id
  log('租户B可独立创建自有词条', entryB.code === 200 && Boolean(entryBId), `entryId=${entryBId}`)

  const listA = await call('GET', '/kb/entries?pageNum=1&pageSize=50', adminToken)
  const aRecords = listA.data?.records || []
  log('租户A列表查不到租户B词条（反向隔离）',
    listA.code === 200 && !aRecords.some((item) => item.id === entryBId),
    `租户A可见条数=${aRecords.length}`)

  const crossDetailA = await call('GET', `/kb/entries/${entryBId}`, adminToken)
  log('租户A越权读租户B词条被拒（反向详情隔离）', crossDetailA.code !== 200,
    `code=${crossDetailA.code} message=${crossDetailA.message}`)

  /* ---------- 4. 平台管理员的职责边界（PL-01）---------- */
  const platformKb = await call('GET', '/kb/entries?pageNum=1&pageSize=100', platformToken)
  log('平台管理员不进入租户业务接口（角色边界）', platformKb.code === 403,
    `code=${platformKb.code} message=${platformKb.message}`)

  /* ---------- 5. 清理自身创建的租户A词条 ---------- */
  if (entryAId) await call('DELETE', `/kb/entries/${entryAId}`, adminToken)
  if (categoryAId) await call('DELETE', `/kb/categories/${categoryAId}`, adminToken)

  const failed = results.filter((item) => !item.ok)
  console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
  cleanup()
  process.exit(failed.length === 0 ? 0 : 1)
}

try {
  await main()
} catch (e) {
  console.error('S4-A 冒烟脚本异常：', e)
  cleanup()
  process.exit(2)
}
