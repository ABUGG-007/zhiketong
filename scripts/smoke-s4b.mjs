/**
 * 智客通 S4-B 商业化基座冒烟脚本（PL-02/PL-03/PL-04 验收用，零依赖：node scripts/smoke-s4b.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、sql/02_data.sql、
 *       sql/14_s4b_plan_usage.sql；MySQL 可用。
 * 覆盖：
 *   1. 平台端租户分页与角色边界（租户管理员访问 /api/platform/** 返回 403）；
 *   2. 开通租户一次性生成租户、商户管理员、网页接入配置与当月用量快照；
 *   3. 套餐额度实时拦截：坐席超额、知识库超额；
 *   4. 套餐升级即时生效、降级时按存量用量拒绝；
 *   5. 用量快照刷新、/api/plan 数据源切换为 tenant_plan；
 *   6. 停用/启用、续期、删除（默认演示租户不可删）。
 * 脚本自建自清临时租户及其业务数据，不污染既有数据。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
import { execFileSync } from 'node:child_process'

const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const MYSQL = process.env.ZK_MYSQL || 'F:\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe'
const DB = 'zhiketong'
const DB_USER = process.env.MYSQL_USERNAME || 'root'
const DB_PASSWORD = process.env.MYSQL_PASSWORD || '246790'

const SUFFIX = Date.now().toString().slice(-8)
const TENANT_CODE = `smoke_b_${SUFFIX}`
const OWNER = `smoke_owner_${SUFFIX}`
const AGENT_A = `smoke_agent_a_${SUFFIX}`
const AGENT_B = `smoke_agent_b_${SUFFIX}`
const AGENT_C = `smoke_agent_c_${SUFFIX}`
const PASSWORD = 'smoke1234'

/** 试用版额度（与 sql/13_s4a_tenant.sql 种子一致） */
const TRIAL_SEAT_LIMIT = 2
const TRIAL_KB_LIMIT = 50
/** 造数条目数：略高于试用版上限，用于验证超额拦截与降级拒绝 */
const BULK_KB = 51

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

async function login(username, password = PASSWORD) {
  return call('POST', '/auth/login', null, { username, password })
}

let tenantId = null

/** 清理临时租户及其业务数据，保证脚本可重复执行 */
function cleanup() {
  if (!tenantId) {
    try {
      const found = sql(`SELECT id FROM tenant WHERE tenant_code = '${TENANT_CODE}'`).trim()
      tenantId = found ? Number(found.split('\n')[0]) : null
    } catch (e) {
      console.warn('查询临时租户失败：', e.message)
      return
    }
  }
  if (!tenantId) return
  try {
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
    sql(`DELETE FROM tenant WHERE id = ${id}`)
  } catch (e) {
    console.warn('清理临时数据失败：', e.message)
  }
}

/** 往新租户批量造知识条目，模拟存量知识规模（同一连接内完成，保证 LAST_INSERT_ID 有效） */
function seedKbEntries(id, count) {
  sql(`INSERT INTO kb_category (tenant_id, name, parent_id, sort, status, deleted)
         VALUES (${id}, '[冒烟]额度分类', 0, 1, 1, 0);
       INSERT INTO kb_entry
         (tenant_id, category_id, question, answer, keywords, tags, hit_count, status, deleted)
       WITH RECURSIVE seq (n) AS (
           SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ${count}
       )
       SELECT ${id}, LAST_INSERT_ID(), CONCAT('[冒烟]额度问题-', n), CONCAT('[冒烟]额度答案-', n),
              '', '', 0, 1, 0
       FROM seq`)
}

async function main() {
  const platform = await login('platform01', '123456')
  const platformToken = platform.data?.token
  log('平台管理员登录成功',
    platform.code === 200 && platform.data?.user?.roleCode === 'PLATFORM_ADMIN',
    `role=${platform.data?.user?.roleCode} tenantId=${platform.data?.tenantId}`)

  const admin = await login('admin', '123456')
  const adminToken = admin.data?.token

  /* ---------- 1. 平台端入口与角色边界 ---------- */
  const tenants = await call('GET', '/platform/tenants?pageNum=1&pageSize=5', platformToken)
  log('平台管理员可分页查看租户（PL-02）',
    tenants.code === 200 && Array.isArray(tenants.data?.list) && tenants.data.list.length > 0,
    `total=${tenants.data?.total}`)

  const tenantPlans = await call('GET', '/platform/plans', platformToken)
  log('套餐字典由 tenant_plan 提供（PL-03）',
    tenantPlans.code === 200 && (tenantPlans.data || []).length === 3
      && tenantPlans.data.some((item) => item.code === 'trial'),
    `plans=${(tenantPlans.data || []).map((item) => item.code).join(',')}`)

  const denyPlatform = await call('GET', '/platform/tenants', adminToken)
  log('租户管理员访问平台接口被拒（角色边界）', denyPlatform.code === 403,
    `code=${denyPlatform.code} message=${denyPlatform.message}`)

  /* ---------- 2. 开通租户（一次性初始化四件套） ---------- */
  const created = await call('POST', '/platform/tenants', platformToken, {
    tenantCode: TENANT_CODE,
    tenantName: `冒烟租户-${SUFFIX}`,
    planCode: 'trial',
    contactName: '冒烟联系人',
    contactPhone: '13800138000',
    contactEmail: `smoke_${SUFFIX}@example.com`,
    remark: 'S4-B 冒烟脚本临时租户',
    adminUsername: OWNER,
    adminPassword: PASSWORD,
    adminNickname: '冒烟商户管理员'
  })
  tenantId = created.data?.id
  log('平台开通租户成功（含商户管理员）',
    created.code === 200 && Boolean(tenantId)
      && created.data?.planName === '试用版'
      && created.data?.seatLimit === TRIAL_SEAT_LIMIT
      && created.data?.kbLimit === TRIAL_KB_LIMIT,
    `tenantId=${tenantId} plan=${created.data?.planCode}/${created.data?.planName} expire=${created.data?.expireTime}`)

  log('试用租户自动获得到期时间（14 天）', Boolean(created.data?.expireTime), `expireTime=${created.data?.expireTime}`)

  const embedCount = Number(sql(`SELECT COUNT(*) FROM tenant_embed_config WHERE tenant_id = ${tenantId}`).trim())
  const usageCount = Number(sql(`SELECT COUNT(*) FROM tenant_usage WHERE tenant_id = ${tenantId}`).trim())
  log('开通即生成网页接入配置与当月用量快照',
    embedCount === 1 && usageCount === 1, `embed=${embedCount} usage=${usageCount}`)

  const owner = await login(OWNER)
  const ownerToken = owner.data?.token
  log('新租户管理员可立即登录并归属新租户',
    owner.code === 200 && owner.data?.tenantId === tenantId && owner.data?.planCode === 'trial',
    `tenantId=${owner.data?.tenantId} tenantName=${owner.data?.tenantName}`)

  const ownerKb = await call('GET', '/kb/entries?pageNum=1&pageSize=10', ownerToken)
  log('新租户业务数据与既有租户隔离', ownerKb.code === 200 && ownerKb.data?.total === 0,
    `total=${ownerKb.data?.total}`)

  /* ---------- 3. 坐席额度实时拦截 ---------- */
  const agentOne = await call('POST', '/admin/users', ownerToken, {
    username: AGENT_A, password: PASSWORD, nickname: '冒烟坐席A', roleCode: 'AGENT', status: 1
  })
  const agentTwo = await call('POST', '/admin/users', ownerToken, {
    username: AGENT_B, password: PASSWORD, nickname: '冒烟坐席B', roleCode: 'AGENT', status: 1
  })
  log('试用版可创建 2 个坐席（额度内）',
    agentOne.code === 200 && agentTwo.code === 200,
    `first=${agentOne.code} second=${agentTwo.code}`)

  const agentThree = await call('POST', '/admin/users', ownerToken, {
    username: AGENT_C, password: PASSWORD, nickname: '冒烟坐席C', roleCode: 'AGENT', status: 1
  })
  log('第 3 个坐席被套餐额度拦截（PL-04）',
    agentThree.code === 403 && String(agentThree.message || '').includes('额度'),
    `code=${agentThree.code} message=${agentThree.message}`)

  /* ---------- 4. 知识库额度实时拦截 ---------- */
  seedKbEntries(tenantId, BULK_KB)
  const kbCat = await call('POST', '/kb/categories', ownerToken, {
    name: `[冒烟]超额验证分类-${SUFFIX}`, sort: 1, status: 1
  })
  const overflowEntry = await call('POST', '/kb/entries', ownerToken, {
    categoryId: kbCat.data?.id,
    question: '[冒烟]超额词条：这条本不该创建成功',
    answer: '[冒烟]超额答案',
    keywords: '冒烟,超额',
    status: 1
  })
  log('知识库条目超额被拦截（PL-04）',
    overflowEntry.code === 403 && String(overflowEntry.message || '').includes('额度'),
    `code=${overflowEntry.code} message=${overflowEntry.message}`)

  /* ---------- 5. 套餐升级即时生效、降级按存量拒绝 ---------- */
  const upgrade = await call('PUT', `/platform/tenants/${tenantId}/plan`, platformToken, {
    planCode: 'professional'
  })
  log('套餐升级为专业版即时生效（PL-03）',
    upgrade.code === 200 && upgrade.data?.planCode === 'professional'
      && upgrade.data?.seatLimit === 20 && upgrade.data?.kbLimit === 2000,
    `plan=${upgrade.data?.planCode} kbLimit=${upgrade.data?.kbLimit}`)

  const downgrade = await call('PUT', `/platform/tenants/${tenantId}/plan`, platformToken, {
    planCode: 'trial'
  })
  log('降级到试用版因存量超额被拒绝',
    downgrade.code === 409 && String(downgrade.message || '').includes('知识库'),
    `code=${downgrade.code} message=${downgrade.message}`)

  /* ---------- 6. 用量快照与租户端套餐页 ---------- */
  const snapshot = await call('POST', `/platform/tenants/${tenantId}/usage/snapshot`, platformToken)
  log('平台端可刷新租户用量快照（PL-04）',
    snapshot.code === 200 && snapshot.data?.kbUsed === BULK_KB && snapshot.data?.seatUsed === 2,
    `period=${snapshot.data?.period} kb=${snapshot.data?.kbUsed} seat=${snapshot.data?.seatUsed}`)

  const planInfo = await call('GET', '/plan', ownerToken)
  const planItems = planInfo.data?.items || []
  const kbItem = planItems.find((item) => item.code === 'kb')
  log('/api/plan 套餐数据来自 tenant_plan 与实时用量',
    planInfo.code === 200 && planInfo.data?.planCode === 'professional'
      && kbItem?.used === BULK_KB && kbItem?.limit === 2000
      && planInfo.data?.tenantName === `冒烟租户-${SUFFIX}`,
    `plan=${planInfo.data?.planCode} kb=${kbItem?.used}/${kbItem?.limit} tenant=${planInfo.data?.tenantName}`)

  /* ---------- 7. 停用/启用、续期、删除 ---------- */
  const disable = await call('PUT', `/platform/tenants/${tenantId}/status`, platformToken, {
    status: 'DISABLED'
  })
  const loginDisabled = await login(OWNER)
  log('停用租户后账号无法登录',
    disable.code === 200 && disable.data?.status === 'DISABLED' && loginDisabled.code !== 200,
    `status=${disable.data?.status} loginCode=${loginDisabled.code}`)

  const enable = await call('PUT', `/platform/tenants/${tenantId}/status`, platformToken, {
    status: 'ENABLED'
  })
  const loginEnabled = await login(OWNER)
  log('恢复启用后账号可重新登录',
    enable.code === 200 && loginEnabled.code === 200,
    `status=${enable.data?.status} loginCode=${loginEnabled.code}`)

  const renew = await call('PUT', `/platform/tenants/${tenantId}/expire`, platformToken, {
    expireTime: '2031-12-31 23:59:59'
  })
  log('续期设置新到期时间成功',
    renew.code === 200 && String(renew.data?.expireTime || '').startsWith('2031-12-31'),
    `expireTime=${renew.data?.expireTime}`)

  const deleteDefault = await call('DELETE', '/platform/tenants/1', platformToken)
  log('默认演示租户不允许删除', deleteDefault.code === 403,
    `code=${deleteDefault.code} message=${deleteDefault.message}`)

  const removed = await call('DELETE', `/platform/tenants/${tenantId}`, platformToken)
  const afterDelete = await call('GET', `/platform/tenants?pageNum=1&pageSize=10&keyword=${TENANT_CODE}`, platformToken)
  const loginDeleted = await login(OWNER)
  log('删除租户后列表不可见且无法登录',
    removed.code === 200 && afterDelete.data?.total === 0 && loginDeleted.code !== 200,
    `deleteCode=${removed.code} total=${afterDelete.data?.total} loginCode=${loginDeleted.code}`)

  const failed = results.filter((item) => !item.ok)
  console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
}

try {
  await main()
} catch (e) {
  console.error('S4-B 冒烟脚本异常：', e)
  process.exitCode = 2
} finally {
  cleanup()
  process.exit(process.exitCode || (results.some((item) => !item.ok) ? 1 : 0))
}
