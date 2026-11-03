/**
 * 智客通 S6-C 客户之声与大屏配置化冒烟脚本（验收用：node scripts/smoke-s6c.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql ~ sql/20_s6c_voc_screen.sql；
 *       本机可用 MySQL 客户端（用于造数与清理，路径可用 ZK_MYSQL 覆盖）。
 * 覆盖：权限收紧（R-205/R-215 仅管理员）→ 大屏布局读写与校验（R-215~R-217）→
 *       VOC 概览（R-205）→ 未命中问法聚类与幂等重算（R-213）→ 主题分页/详情（R-206/R-207）→
 *       主题新增/修改/启停/合并/删除（R-208~R-212）→ 词云（R-214）。
 * 说明：未命中问法由脚本直接写入 kb_unmatched_question，避免依赖大模型是否命中，保证可重复执行。
 * 退出码：全部通过 0，存在失败 1，脚本异常 2。
 */
import { execFileSync } from 'node:child_process'

const BASE = process.env.ZK_BASE || 'http://127.0.0.1:8080/api'
const MYSQL = process.env.ZK_MYSQL || 'F:\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe'
const DB = 'zhiketong'
const DB_USER = process.env.MYSQL_USERNAME || 'root'
const DB_PASSWORD = process.env.MYSQL_PASSWORD || '246790'

const STAMP = Date.now().toString()
const TAG = `S6C${STAMP}`
const SEED_QUESTIONS = [
  `${TAG}发票开错了怎么重新开红字发票`,
  `${TAG}发票开错了如何重开红字发票`,
  `${TAG}发票开错了能重开红字发票吗`,
  `${TAG}发票开错怎么重开红字发票`,
  `${TAG}发票开错了要重开红字发票怎么办`
]
const SEED_ASK = [3, 2, 2, 1, 1]

const results = []
const log = (name, ok, detail) => {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  → ' + detail : ''}`)
}

function sql(statement) {
  return execFileSync(MYSQL, [
    '--host=127.0.0.1', '--port=3306', `--user=${DB_USER}`, `--password=${DB_PASSWORD}`,
    '--default-character-set=utf8mb4', '--skip-column-names', '--batch', DB, '-e', statement
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
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

/** 与后端 KnowledgeMatcher#normalize 口径一致的简化版：小写、去标点、压缩空白 */
function normalize(text) {
  return text
    .replace(/[\uFF01-\uFF5E]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xFEE0))
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

/** 造数：写入若干条相似未命中问法，返回写入条数 */
function seedUnmatched() {
  const values = SEED_QUESTIONS.map((question, index) => {
    const normalized = normalize(question)
    return `(1, '${question}', '${normalized}', 'SITE', ${SEED_ASK[index]}, 0.2, 'PENDING', NOW(), NOW(), 0)`
  })
  sql(`DELETE FROM kb_unmatched_question WHERE question LIKE '${TAG}%';
       INSERT INTO kb_unmatched_question
         (tenant_id, question, normalized_question, channel, ask_count, last_score, status,
          first_ask_time, last_ask_time, deleted)
       VALUES ${values.join(',\n              ')};`)
  return Number(sql(`SELECT COUNT(*) FROM kb_unmatched_question WHERE question LIKE '${TAG}%'`).trim())
}

/** 清理：删除本次造数与主题，保证脚本可重复执行 */
function cleanup() {
  try {
    sql(`DELETE FROM kb_unmatched_question WHERE question LIKE '${TAG}%';
         DELETE FROM voc_topic WHERE sample_question LIKE '%${TAG}%'
            OR topic_name LIKE '%${STAMP}%'
            OR topic_name LIKE '%S6C人工主题%${STAMP}%';
         DELETE FROM screen_layout;`)
  } catch (e) {
    console.warn('清理临时数据失败：', e.message)
  }
}

/** 拉取本租户全部主题（冒烟库量小，一页取够） */
async function allTopics(token) {
  const resp = await call('GET', '/kb/voc/topics?pageNum=1&pageSize=100', token)
  return (resp.data?.list || []).filter((row) => !row.deleted)
}

async function main() {
  const admin = await login('admin')
  const agent = await login('agent01')
  const user = await login('user01')

  /* ================================================================
     一、权限收紧（R-205 / R-215 仅管理员）
     ================================================================ */
  const agentVoc = await call('GET', '/kb/voc/overview', agent.token)
  log('坐席访问客户之声被拒（R-205 权限）', agentVoc.http === 403 || agentVoc.code === 403,
    `http=${agentVoc.http} code=${agentVoc.code}`)

  const userVoc = await call('GET', '/kb/voc/topics?pageNum=1&pageSize=5', user.token)
  log('普通用户访问客户之声主题被拒（R-206 权限）', userVoc.http === 403 || userVoc.code === 403,
    `http=${userVoc.http} code=${userVoc.code}`)

  const agentLayout = await call('GET', '/statistics/ops/layout', agent.token)
  log('坐席访问大屏布局配置被拒（R-215 权限）', agentLayout.http === 403 || agentLayout.code === 403,
    `http=${agentLayout.http} code=${agentLayout.code}`)

  /* ================================================================
     二、大屏布局（R-215 ~ R-217）
     ================================================================ */
  const defaultLayout = await call('GET', '/statistics/ops/layout', admin.token)
  const defaultWidgets = defaultLayout.data?.widgets || []
  log('未配置时返回内置默认布局（R-215）',
    defaultLayout.code === 200 && defaultLayout.data?.defaultLayout === true
    && defaultWidgets.length === 5 && defaultWidgets.every((item) => item.visible !== false)
    && defaultWidgets.every((item, index) => Number(item.order) === index + 1),
    `density=${defaultLayout.data?.density} refresh=${defaultLayout.data?.refreshSeconds}s `
    + `组件=${defaultWidgets.map((item) => `${item.code}#${item.order}`).join(',')}`)

  log('默认布局组件清单来自服务端白名单（R-215）',
    defaultWidgets.map((item) => item.code).join(',') === 'REALTIME,TODAY,AGENTS,TREND,ALERTS'
    && defaultWidgets.every((item) => Boolean(item.name) && Boolean(item.description)),
    `编码=${defaultWidgets.map((item) => item.code).join('|')}`)

  const badCode = await call('PUT', '/statistics/ops/layout', admin.token, {
    widgets: [{ code: 'NOT_A_WIDGET', visible: true, order: 1 }]
  })
  log('未知组件编码被拦截（R-216 白名单）', badCode.code !== 200,
    `code=${badCode.code} message=${badCode.message}`)

  const badDensity = await call('PUT', '/statistics/ops/layout', admin.token, {
    density: 'LOOSE', refreshSeconds: 15
  })
  log('非法展示密度被拦截（R-216 校验）', badDensity.code !== 200,
    `code=${badDensity.code} message=${badDensity.message}`)

  const badRefresh = await call('PUT', '/statistics/ops/layout', admin.token, {
    density: 'COMPACT', refreshSeconds: 3
  })
  log('刷新间隔低于 5 秒被拦截（R-216 校验）', badRefresh.code !== 200,
    `code=${badRefresh.code} message=${badRefresh.message}`)

  const saved = await call('PUT', '/statistics/ops/layout', admin.token, {
    layoutName: `[冒烟]大屏-${STAMP}`,
    density: 'COMPACT',
    refreshSeconds: 30,
    widgets: [
      { code: 'TREND', visible: true, order: 1 },
      { code: 'REALTIME', visible: false, order: 5 }
    ]
  })
  const savedWidgets = saved.data?.widgets || []
  log('保存布局并按顺序号归一化（R-216）',
    saved.code === 200 && saved.data?.density === 'COMPACT' && saved.data?.refreshSeconds === 30
    && savedWidgets[0]?.code === 'TREND'
    && savedWidgets.find((item) => item.code === 'REALTIME')?.visible === false
    && savedWidgets.every((item, index) => Number(item.order) === index + 1),
    `density=${saved.data?.density} refresh=${saved.data?.refreshSeconds}s `
    + `顺序=${savedWidgets.map((item) => `${item.code}#${item.order}`).join(',')}`)

  const reloaded = await call('GET', '/statistics/ops/layout', admin.token)
  log('布局落库后可回读（R-216）',
    reloaded.code === 200 && reloaded.data?.defaultLayout === false
    && reloaded.data?.density === 'COMPACT' && reloaded.data?.refreshSeconds === 30
    && reloaded.data?.widgets?.[0]?.code === 'TREND',
    `defaultLayout=${reloaded.data?.defaultLayout} density=${reloaded.data?.density}`)

  const resetLayout = await call('POST', '/statistics/ops/layout/reset', admin.token)
  log('重置布局回到内置默认（R-217）',
    resetLayout.code === 200 && resetLayout.data?.defaultLayout === true
    && resetLayout.data?.density === 'COMFORTABLE'
    && (resetLayout.data?.widgets || []).every((item) => item.visible !== false),
    `defaultLayout=${resetLayout.data?.defaultLayout} density=${resetLayout.data?.density}`)

  const afterReset = await call('GET', '/statistics/ops/layout', admin.token)
  log('重置后配置行已清除（R-217）', afterReset.data?.defaultLayout === true,
    `defaultLayout=${afterReset.data?.defaultLayout}`)

  /* ================================================================
     三、VOC 概览（R-205）
     ================================================================ */
  const seeded = seedUnmatched()
  log('造数：写入相似未命中问法（前置）', seeded === SEED_QUESTIONS.length, `写入 ${seeded} 条`)

  const overview = await call('GET', '/kb/voc/overview', admin.token)
  const ov = overview.data || {}
  const channels = Array.isArray(ov.channels) ? ov.channels : []
  const trend = Array.isArray(ov.trend) ? ov.trend : []
  log('客户之声概览结构自洽（R-205）',
    overview.code === 200
    && Number(ov.unmatchedTotal) >= SEED_QUESTIONS.length
    && Number(ov.clusteredCount) <= Number(ov.unmatchedTotal)
    && Number(ov.clusterRate) >= 0 && Number(ov.clusterRate) <= 1
    && Number(ov.topicCount) === Number(ov.autoTopicCount) + Number(ov.manualTopicCount)
    && chansOk(channels) && trend.length === 7,
    `未命中=${ov.unmatchedTotal} 已聚类=${ov.clusteredCount} 覆盖率=${ov.clusterRate}`
    + ` 主题=${ov.topicCount} 渠道=${channels.map((c) => `${c.channel}:${c.questionCount}`).join(',')}`
    + ` 趋势点=${trend.length}`)

  /* ================================================================
     四、聚类（R-213）
     ================================================================ */
  const cluster = await call('POST', '/kb/voc/cluster', admin.token)
  const c1 = cluster.data || {}
  log('首轮聚类把相似问法归成主题（R-213）',
    cluster.code === 200 && c1.locked === true
    && Number(c1.scannedCount) >= SEED_QUESTIONS.length
    && Number(c1.createdCount) >= 1
    && Number(c1.assignedCount) + Number(c1.createdCount) >= SEED_QUESTIONS.length
    && c1.topicCount >= 1,
    `扫描=${c1.scannedCount} 归入=${c1.assignedCount} 新建=${c1.createdCount} 主题=${c1.topicCount}`)

  const topicsAfterCluster = await allTopics(admin.token)
  const autoTopics = topicsAfterCluster.filter((row) => String(row.sampleQuestion || '').includes(TAG))
  const clusteredQuestions = autoTopics.reduce((sum, row) => sum + Number(row.questionCount || 0), 0)
  log('自动主题由问法派生且聚合数正确（R-206/R-207）',
    autoTopics.length >= 1 && autoTopics.every((row) => row.topicType === 'AUTO')
    && clusteredQuestions === SEED_QUESTIONS.length
    && autoTopics.every((row) => Number(row.askTotal) > 0 && Boolean(row.lastClusterTime)),
    `主题数=${autoTopics.length} 问法合计=${clusteredQuestions} `
    + `名称=${autoTopics.map((row) => row.topicName).join(' | ')}`)

  const secondCluster = await call('POST', '/kb/voc/cluster', admin.token)
  const c2 = secondCluster.data || {}
  log('重复聚类幂等，不再新建主题（R-213）',
    secondCluster.code === 200 && Number(c2.createdCount) === 0
    && Number(c2.assignedCount) === Number(c1.scannedCount),
    `扫描=${c2.scannedCount} 归入=${c2.assignedCount} 新建=${c2.createdCount}`)

  const autoTopic = autoTopics[0]
  const detail = await call('GET', `/kb/voc/topics/${autoTopic.id}`, admin.token)
  const detailQuestions = detail.data?.questions || []
  log('主题详情带出归属问法（R-207）',
    detail.code === 200 && detail.data?.topic?.id === autoTopic.id
    && detailQuestions.length === Number(autoTopic.questionCount)
    && detailQuestions.every((row) => Number(row.topicId) === Number(autoTopic.id)
      && row.topicName === autoTopic.topicName
      && Number(row.clusterScore) >= 0 && Number(row.clusterScore) <= 100)
    && detailQuestions.every((row, index, arr) => index === 0
      || Number(arr[index - 1].askCount) >= Number(row.askCount)),
    `问法=${detailQuestions.length} 首个=${detailQuestions[0]?.question} 相似度=${detailQuestions[0]?.clusterScore}`)

  const unmatchedAfter = await call('GET',
    `/kb/unmatched-questions?pageNum=1&pageSize=20&keyword=${encodeURIComponent(TAG)}`, admin.token)
  const unmatchedRows = unmatchedAfter.data?.list || []
  log('未命中问题列表回填主题与相似度（R-206 联动）',
    unmatchedAfter.code === 200 && unmatchedRows.length === SEED_QUESTIONS.length
    && unmatchedRows.every((row) => Boolean(row.topicName) && row.clusterScore !== null),
    `条数=${unmatchedRows.length} 主题=${unmatchedRows[0]?.topicName} 相似度=${unmatchedRows[0]?.clusterScore}`)

  /* ================================================================
     五、词云（R-214）
     ================================================================ */
  const cloud = await call('GET', '/kb/voc/word-cloud', admin.token)
  const words = cloud.data?.words || []
  log('词云按权重倒序且覆盖本批问法（R-214）',
    cloud.code === 200 && Number(cloud.data?.questionCount) >= SEED_QUESTIONS.length
    && words.length >= 1 && words.length <= 80
    && words.every((word, index, arr) => index === 0
      || Number(arr[index - 1].weight) >= Number(word.weight))
    && words.every((word) => Boolean(word.term) && Number(word.weight) >= 1),
    `问法=${cloud.data?.questionCount} 词条=${words.length} 榜首=${words[0]?.term}/${words[0]?.weight}`)

  const topicCloud = await call('GET', `/kb/voc/word-cloud?topicId=${autoTopic.id}&limit=20`, admin.token)
  log('词云支持按主题收窄（R-214）',
    topicCloud.code === 200
    && Number(topicCloud.data?.questionCount) <= Number(cloud.data?.questionCount)
    && (topicCloud.data?.words || []).length >= 1,
    `主题 #${autoTopic.id} 问法=${topicCloud.data?.questionCount} 词条=${(topicCloud.data?.words || []).length}`)

  const badLimit = await call('GET', '/kb/voc/word-cloud?limit=5', admin.token)
  log('词条数低于 10 被拦截（R-214 校验）', badLimit.code !== 200,
    `code=${badLimit.code} message=${badLimit.message}`)

  /* ================================================================
     六、主题 CRUD / 合并（R-208 ~ R-212）
     ================================================================ */
  const manualName = `S6C人工主题-${STAMP}`
  const createdManual = await call('POST', '/kb/voc/topics', admin.token, {
    topicName: manualName,
    keywords: '红字发票,重开',
    sampleQuestion: '人工维护的代表问法',
    remark: '冒烟：人工主题',
    sort: 10,
    status: 1
  })
  const manualId = createdManual.data?.id
  log('新增人工主题（R-208）',
    createdManual.code === 200 && Boolean(manualId)
    && createdManual.data?.topicType === 'MANUAL'
    && createdManual.data?.topicTypeName === '人工创建'
    && Number(createdManual.data?.status) === 1,
    `id=${manualId} 名称=${createdManual.data?.topicName} 来源=${createdManual.data?.topicTypeName}`)

  const duplicate = await call('POST', '/kb/voc/topics', admin.token, { topicName: manualName })
  log('同租户重名主题被拦截（R-208 唯一性）', duplicate.code !== 200,
    `code=${duplicate.code} message=${duplicate.message}`)

  const updated = await call('PUT', `/kb/voc/topics/${manualId}`, admin.token, {
    topicName: `${manualName}-改`,
    keywords: '红字发票,重开,作废',
    sampleQuestion: '修改后的代表问法',
    remark: '冒烟：已修改',
    sort: 20,
    status: 1
  })
  log('修改主题名称与关键词（R-209）',
    updated.code === 200 && updated.data?.topicName === `${manualName}-改`
    && updated.data?.keywords === '红字发票,重开,作废' && Number(updated.data?.sort) === 20,
    `名称=${updated.data?.topicName} 关键词=${updated.data?.keywords} 排序=${updated.data?.sort}`)

  const off = await call('PUT', `/kb/voc/topics/${manualId}/status?status=0`, admin.token)
  const on = await call('PUT', `/kb/voc/topics/${manualId}/status?status=1`, admin.token)
  log('停用并重新启用主题（R-212）',
    Number(off.data?.status) === 0 && Number(on.data?.status) === 1,
    `停用=${off.data?.status} 启用=${on.data?.status}`)

  const badStatus = await call('PUT', `/kb/voc/topics/${manualId}/status?status=2`, admin.token)
  log('非法状态值被拦截（R-212 校验）', badStatus.code !== 200,
    `code=${badStatus.code} message=${badStatus.message}`)

  const createdSource = await call('POST', '/kb/voc/topics', admin.token, {
    topicName: `${manualName}-待合并`, keywords: '红字发票', status: 1
  })
  const sourceId = createdSource.data?.id
  const merged = await call('POST', '/kb/voc/topics/merge', admin.token, {
    targetTopicId: manualId,
    sourceTopicIds: [sourceId]
  })
  log('合并主题并删除源主题（R-211）',
    merged.code === 200 && Number(merged.data?.id) === Number(manualId),
    `目标=${merged.data?.id} 源=${sourceId}`)

  const afterMerge = await allTopics(admin.token)
  log('源主题合并后不再出现在列表（R-211）',
    !afterMerge.some((row) => Number(row.id) === Number(sourceId))
    && afterMerge.some((row) => Number(row.id) === Number(manualId)),
    `列表主题数=${afterMerge.length}`)

  const deleted = await call('DELETE', `/kb/voc/topics/${manualId}`, admin.token)
  const afterDelete = await call('GET', `/kb/voc/topics/${manualId}`, admin.token)
  log('删除主题后详情不可再查（R-210）',
    deleted.code === 200 && afterDelete.code !== 200,
    `delete=${deleted.code} detail=${afterDelete.code}`)

  const detachedRows = Number(sql(
    `SELECT COUNT(*) FROM kb_unmatched_question WHERE topic_id = ${manualId} AND deleted = 0`).trim())
  log('删除主题同步解除问法归属（R-210）', detachedRows === 0, `残留归属=${detachedRows}`)

  /* ================================================================
     七、审计与清理
     ================================================================ */
  const auditCount = Number(sql(
    `SELECT COUNT(*) FROM audit_log WHERE module = 'VOC' AND create_time >= NOW() - INTERVAL 1 HOUR`).trim())
  log('客户之声写操作已写入审计（多租户审计要求）', auditCount >= 4, `近 1 小时 VOC 审计=${auditCount} 条`)

  const screenRows = Number(sql('SELECT COUNT(*) FROM screen_layout WHERE deleted = 0').trim())
  log('重置后布局表无生效配置（R-217）', screenRows === 0, `screen_layout 生效行=${screenRows}`)

  return results.every((row) => row.ok)
}

/** 渠道分布项校验：编码、名称、条数、次数齐备 */
function chansOk(channels) {
  return Array.isArray(channels) && channels.every((item) =>
    Boolean(item.channel) && Boolean(item.channelName)
    && Number(item.questionCount) >= 0 && Number(item.askCount) >= 0)
}

main()
  .then((ok) => {
    cleanup()
    const failed = results.filter((row) => !row.ok).length
    console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed} 项，失败 ${failed} 项`)
    process.exit(ok ? 0 : 1)
  })
  .catch((e) => {
    cleanup()
    console.error('ERROR ' + (e?.stack || e?.message || e))
    process.exit(2)
  })
