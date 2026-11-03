/**
 * S2-A 冒烟脚本：快捷回复话术 + 技能组分配（零依赖：node scripts/smoke-s2a.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、02_data.sql、07_s2a_quick_reply_skill.sql。
 * 覆盖：技能组增删改查/启停 · 我的技能组 · 访客可选咨询类型 · 转人工带咨询类型入队 ·
 *       非组内坐席接入被拦截 · 组内坐席自动接入 · 停用组不可选 · 快捷回复公共/个人可见性与使用计数。
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
  try {
    json = await res.json()
  } catch {
    /* 空响应 */
  }
  return { http: res.status, ...(json || {}) }
}

async function login(username, password = '123456') {
  const r = await call('POST', '/auth/login', null, { username, password })
  if (r.code !== 200) throw new Error(`登录失败 ${username}: ${r.http} ${r.message}`)
  return r.data
}

const stamp = Date.now()

const ctx = {
  admin: null,
  agent: null,
  agent2: null,
  user: null,
  groupId: null,
  sessionId: null,
  quickReplyIds: []
}

async function ensureIdleSession(user) {
  const current = await call('GET', '/chat/sessions/current', user.token)
  if (current.code === 200 && current.data?.id) {
    await call('POST', `/chat/sessions/${current.data.id}/close`, user.token, { closeReason: 'USER_CLOSED' })
  }
}

async function cleanup() {
  try {
    if (ctx.sessionId) {
      await call('POST', `/chat/sessions/${ctx.sessionId}/close`, ctx.user.token, { closeReason: 'USER_CLOSED' })
    }
  } catch {
    /* 忽略清理失败 */
  }
  for (const id of ctx.quickReplyIds) {
    try {
      await call('DELETE', `/quick-replies/${id}`, ctx.admin.token)
    } catch {
      /* 忽略清理失败 */
    }
  }
  if (ctx.groupId) {
    try {
      await call('DELETE', `/admin/skill-groups/${ctx.groupId}`, ctx.admin.token)
    } catch {
      /* 忽略清理失败 */
    }
  }
}

async function main() {
  ctx.admin = await login('admin')
  ctx.agent = await login('agent01')
  ctx.user = await login('user01')
  try {
    ctx.agent2 = await login('agent02')
  } catch {
    ctx.agent2 = null
    console.log('INFO  未找到 agent02 账号，跳过「非组内坐席接入被拦截」用例')
  }
  const agentId = ctx.agent.user.userId ?? ctx.agent.user.id
  const agent2Id = ctx.agent2 ? ctx.agent2.user.userId ?? ctx.agent2.user.id : null
  log('三种角色登录', true, `admin=${ctx.admin.user.nickname} agent01=${ctx.agent.user.nickname} agent02=${ctx.agent2?.user?.nickname || '无'}`)

  /* ---------- 技能组主数据 ---------- */
  const preset = await call('GET', '/admin/skill-groups?withMembers=true', ctx.admin.token)
  log('技能组列表（管理员）', preset.code === 200 && Array.isArray(preset.data) && preset.data.length >= 3,
    `数量=${preset.data?.length} 首个=${preset.data?.[0]?.name}`)

  const groupName = `冒烟技能组-${stamp}`
  const created = await call('POST', '/admin/skill-groups', ctx.admin.token, {
    name: groupName,
    description: 'S2-A 冒烟用例专用，脚本结束后自动删除',
    sort: 999,
    status: 1,
    memberIds: [agentId],
    levels: [{ userId: agentId, level: 5 }]
  })
  ctx.groupId = created.data?.id
  const memberOk = (created.data?.members || []).some((m) => Number(m.userId) === Number(agentId) && Number(m.level) === 5)
  log('新增技能组并指定组内坐席', created.code === 200 && Boolean(ctx.groupId) && created.data?.memberCount === 1 && memberOk,
    `id=${ctx.groupId} 成员数=${created.data?.memberCount} 能力值=${created.data?.members?.[0]?.level}`)

  const duplicated = await call('POST', '/admin/skill-groups', ctx.admin.token, { name: groupName, memberIds: [] })
  log('同名技能组被拦截', duplicated.code === 409, `code=${duplicated.code} message=${duplicated.message}`)

  const mine = await call('GET', '/agent/skill-groups/mine', ctx.agent.token)
  const mineHit = (mine.data || []).find((g) => Number(g.id) === Number(ctx.groupId))
  log('坐席查看「我的技能组」', mine.code === 200 && Boolean(mineHit), `数量=${mine.data?.length} 命中=${mineHit?.name || '无'}`)

  const myForbidden = await call('GET', '/admin/skill-groups', ctx.agent.token)
  log('坐席访问管理端技能组被拒', myForbidden.http === 403 || myForbidden.code === 403,
    `http=${myForbidden.http} code=${myForbidden.code}`)

  const choices = await call('GET', '/chat/skill-groups', ctx.user.token)
  const choiceHit = (choices.data || []).find((g) => Number(g.id) === Number(ctx.groupId))
  log('访客可选咨询类型', choices.code === 200 && Boolean(choiceHit) && Boolean(choiceHit?.description),
    `数量=${choices.data?.length} 命中=${choiceHit?.name || '无'}`)

  /* ---------- 快捷回复话术 ---------- */
  const publicReply = await call('POST', '/quick-replies', ctx.admin.token, {
    scope: 'PUBLIC',
    category: '冒烟分类',
    title: `冒烟公共话术-${stamp}`,
    content: '您好，这里是智客通客服，请问有什么可以帮您？',
    sort: 0,
    status: 1
  })
  if (publicReply.data?.id) ctx.quickReplyIds.push(publicReply.data.id)
  log('管理员新增公共话术', publicReply.code === 200 && publicReply.data?.scope === 'PUBLIC' && publicReply.data?.ownerId == null,
    `id=${publicReply.data?.id} scope=${publicReply.data?.scope}`)

  const visible = await call('GET', `/quick-replies?scope=PUBLIC&keyword=${encodeURIComponent(`冒烟公共话术-${stamp}`)}`, ctx.agent.token)
  const visibleHit = (visible.data || []).some((r) => Number(r.id) === Number(publicReply.data?.id))
  log('坐席可见公共话术', visible.code === 200 && visibleHit, `数量=${visible.data?.length}`)

  const used = await call('POST', `/quick-replies/${publicReply.data?.id}/use`, ctx.agent.token)
  log('引用话术累加使用次数', used.code === 200 && Number(used.data?.useCount) === 1, `useCount=${used.data?.useCount}`)

  const agentEditPublic = await call('PUT', `/quick-replies/${publicReply.data?.id}`, ctx.agent.token, {
    scope: 'PUBLIC',
    title: publicReply.data?.title,
    content: '越权修改'
  })
  log('坐席修改公共话术被拦截', agentEditPublic.code === 403, `code=${agentEditPublic.code} message=${agentEditPublic.message}`)

  const personalTitle = `冒烟个人话术-${stamp}`
  const personal = await call('POST', '/quick-replies', ctx.agent.token, {
    scope: 'PERSONAL',
    category: '冒烟分类',
    title: personalTitle,
    content: '您好，我这边帮您核实一下，请稍等。',
    status: 1
  })
  if (personal.data?.id) ctx.quickReplyIds.push(personal.data.id)
  log('坐席新增个人话术（自动归属本人）', personal.code === 200 && personal.data?.scope === 'PERSONAL'
    && Number(personal.data?.ownerId) === Number(agentId) && personal.data?.editable === true,
    `id=${personal.data?.id} ownerId=${personal.data?.ownerId}`)

  const asAdminPersonal = await call('GET', '/quick-replies?scope=PERSONAL', ctx.admin.token)
  const adminSeesPersonal = (asAdminPersonal.data || []).some((r) => Number(r.id) === Number(personal.data?.id))
  log('管理员可治理坐席个人话术', asAdminPersonal.code === 200 && adminSeesPersonal,
    `个人话术数量=${asAdminPersonal.data?.length}`)

  const categories = await call('GET', '/quick-replies/categories', ctx.agent.token)
  log('话术分类聚合', categories.code === 200 && (categories.data || []).includes('冒烟分类'),
    `分类=${(categories.data || []).slice(0, 5).join('/')}`)

  const disabled = await call('PUT', `/quick-replies/${publicReply.data?.id}/status`, ctx.admin.token, { status: 0 })
  const afterDisable = await call('GET', `/quick-replies?keyword=${encodeURIComponent(`冒烟公共话术-${stamp}`)}&status=1`, ctx.agent.token)
  const stillVisible = (afterDisable.data || []).some((r) => Number(r.id) === Number(publicReply.data?.id))
  log('停用话术不再进入启用列表', disabled.code === 200 && Number(disabled.data?.status) === 0 && !stillVisible,
    `列表数量=${afterDisable.data?.length}`)
  await call('PUT', `/quick-replies/${publicReply.data?.id}/status`, ctx.admin.token, { status: 1 })

  /* ---------- 技能组分配链路 ---------- */
  await call('POST', '/agent/offline', ctx.agent.token)
  if (ctx.agent2) await call('POST', '/agent/offline', ctx.agent2.token)
  await ensureIdleSession(ctx.user)

  const createdSession = await call('POST', '/chat/sessions', ctx.user.token)
  ctx.sessionId = createdSession.data?.session?.id || createdSession.data?.sessionId || createdSession.data?.id
  log('用户创建会话', createdSession.code === 200 && Boolean(ctx.sessionId), `sessionId=${ctx.sessionId}`)

  const transfer = await call('POST', `/chat/sessions/${ctx.sessionId}/transfer`, ctx.user.token, {
    reason: '订单退款需要人工核实',
    skillGroupId: ctx.groupId
  })
  log('转人工携带咨询类型进入排队', transfer.code === 200 && transfer.data?.status === 'WAITING' && Number(transfer.data?.queuePosition) >= 1,
    `status=${transfer.data?.status} 排位=${transfer.data?.queuePosition ?? '-'}`)

  const detailAfterTransfer = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.admin.token)
  log('会话落库咨询类型并可回显名称', detailAfterTransfer.code === 200
    && Number(detailAfterTransfer.data?.skillGroupId) === Number(ctx.groupId)
    && detailAfterTransfer.data?.skillGroupName === groupName,
    `skillGroupId=${detailAfterTransfer.data?.skillGroupId} name=${detailAfterTransfer.data?.skillGroupName}`)

  const queue = await call('GET', '/agent/queue?pageNum=1&pageSize=20', ctx.agent.token)
  const queueItem = (queue.data?.list || []).find((item) => String(item.sessionId) === String(ctx.sessionId))
  log('等待队列展示咨询类型', queue.code === 200 && Boolean(queueItem)
    && Number(queueItem?.skillGroupId) === Number(ctx.groupId) && queueItem?.skillGroupName === groupName,
    `队列长度=${queue.data?.total ?? queue.data?.list?.length} 咨询类型=${queueItem?.skillGroupName || '无'}`)

  if (ctx.agent2) {
    const outsider = await call('POST', `/agent/sessions/${ctx.sessionId}/accept`, ctx.agent2.token)
    log('非组内坐席接入被拦截', outsider.code === 409 && String(outsider.message || '').includes('技能组'),
      `code=${outsider.code} message=${outsider.message}`)
  }

  await call('POST', '/agent/online', ctx.agent.token, {})
  let status = ''
  for (let i = 0; i < 16; i++) {
    const detail = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.agent.token)
    status = detail.data?.status || ''
    if (status === 'HUMAN') break
    await new Promise((r) => setTimeout(r, 500))
  }
  const assigned = await call('GET', `/chat/sessions/${ctx.sessionId}`, ctx.agent.token)
  log('组内坐席上线后自动接入', status === 'HUMAN' && Number(assigned.data?.agentId) === Number(agentId),
    `status=${status} agentId=${assigned.data?.agentId}`)

  const closed = await call('POST', `/chat/sessions/${ctx.sessionId}/close`, ctx.agent.token, { closeReason: 'AGENT_CLOSED' })
  log('坐席结束会话释放负载', closed.code === 200 && closed.data?.status === 'CLOSED', `status=${closed.data?.status}`)

  /* ---------- 同负载下能力值优先 ---------- */
  if (ctx.agent2) {
    await call('PUT', `/admin/skill-groups/${ctx.groupId}`, ctx.admin.token, {
      name: groupName,
      description: 'S2-A 冒烟用例专用，脚本结束后自动删除',
      sort: 999,
      status: 1,
      memberIds: [agentId, agent2Id],
      levels: [{ userId: agentId, level: 5 }, { userId: agent2Id, level: 1 }]
    })
    await call('POST', '/agent/online', ctx.agent2.token, {})
    await ensureIdleSession(ctx.user)
    const tieSession = await call('POST', '/chat/sessions', ctx.user.token)
    const tieSessionId = tieSession.data?.session?.id || tieSession.data?.sessionId || tieSession.data?.id
    const tieTransfer = await call('POST', `/chat/sessions/${tieSessionId}/transfer`, ctx.user.token, {
      reason: '验证能力值优先',
      skillGroupId: ctx.groupId
    })
    let tieStatus = ''
    let tieAgentId = null
    for (let i = 0; i < 16; i++) {
      const detail = await call('GET', `/chat/sessions/${tieSessionId}`, ctx.admin.token)
      tieStatus = detail.data?.status || ''
      tieAgentId = detail.data?.agentId
      if (tieStatus === 'HUMAN') break
      await new Promise((r) => setTimeout(r, 500))
    }
    log('同负载下能力值高的坐席优先接入', tieTransfer.code === 200 && tieStatus === 'HUMAN' && Number(tieAgentId) === Number(agentId),
      `status=${tieStatus} 接入坐席=${tieAgentId}（期望 ${agentId}）`)
    await call('POST', `/chat/sessions/${tieSessionId}/close`, ctx.agent.token, { closeReason: 'AGENT_CLOSED' })
    await call('POST', '/agent/offline', ctx.agent2.token)
  }

  /* ---------- 停用技能组不可选 ---------- */
  await call('PUT', `/admin/skill-groups/${ctx.groupId}/status`, ctx.admin.token, { status: 0 })
  const choicesAfterDisable = await call('GET', '/chat/skill-groups', ctx.user.token)
  const hidden = !(choicesAfterDisable.data || []).some((g) => Number(g.id) === Number(ctx.groupId))
  log('停用技能组不在访客可选列表', choicesAfterDisable.code === 200 && hidden, `可选数量=${choicesAfterDisable.data?.length}`)

  const freshSession = await call('POST', '/chat/sessions', ctx.user.token)
  const freshId = freshSession.data?.session?.id || freshSession.data?.sessionId || freshSession.data?.id
  const transferDisabled = await call('POST', `/chat/sessions/${freshId}/transfer`, ctx.user.token, {
    reason: '停用咨询类型校验',
    skillGroupId: ctx.groupId
  })
  log('停用咨询类型转人工被拦截', transferDisabled.code === 400, `code=${transferDisabled.code} message=${transferDisabled.message}`)
  await call('POST', `/chat/sessions/${freshId}/close`, ctx.user.token, { closeReason: 'USER_CLOSED' })
  const deleteGroup = await call('DELETE', `/admin/skill-groups/${ctx.groupId}`, ctx.admin.token)
  log('删除技能组', deleteGroup.code === 200, `code=${deleteGroup.code}`)
  ctx.groupId = null
  await call('POST', '/agent/offline', ctx.agent.token)
}

main()
  .then(async () => {
    await cleanup()
    const failed = results.filter((r) => !r.ok)
    console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
    process.exit(failed.length ? 1 : 0)
  })
  .catch(async (error) => {
    console.error('脚本异常：', error)
    await cleanup()
    process.exit(2)
  })
