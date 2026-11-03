/**
 * S3-A 冒烟脚本：工单表单引擎（零依赖：node scripts/smoke-s3a.mjs）
 *
 * 前置：后端已启动（默认 http://127.0.0.1:8080），已执行 sql/01_schema.sql、02_data.sql、10_s3a_order_form.sql。
 * 覆盖：自定义字段 R-115~R-119（编码唯一/不可改、下拉选项、停用启用、引用保护）·
 *       工单模板 R-120~R-125（绑定字段、默认分类与优先级、重名、停用、删除）·
 *       建单表单 R-126（字段/模板/分类/类型字典、普通用户可读、坐席越权）·
 *       按模板建单 R-49（默认值补齐、必填与类型校验、字段不属于模板、未选模板提交字段、字段值快照）。
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

const ctx = {
  admin: null,
  agent: null,
  user: null,
  orderNoFieldId: null,
  refundWayFieldId: null,
  refundAmountFieldId: null,
  expectDateFieldId: null,
  templateId: null,
  orderId: null
}

const stamp = Date.now().toString().slice(-8)

async function cleanup() {
  try {
    if (ctx.templateId) await call('DELETE', `/admin/order-templates/${ctx.templateId}`, ctx.admin.token)
  } catch {
    /* 忽略清理失败 */
  }
  for (const id of [ctx.orderNoFieldId, ctx.refundWayFieldId, ctx.refundAmountFieldId, ctx.expectDateFieldId]) {
    try {
      if (id) await call('DELETE', `/admin/order-fields/${id}`, ctx.admin.token)
    } catch {
      /* 忽略清理失败 */
    }
  }
}

async function main() {
  ctx.admin = await login('admin')
  ctx.agent = await login('agent01')
  ctx.user = await login('user01')
  log('三种角色登录', true, `admin=${ctx.admin.user.nickname} agent=${ctx.agent.user.nickname}`)

  /* ---------- R-115 字段列表 ---------- */
  const initial = await call('GET', '/admin/order-fields', ctx.admin.token)
  log('字段列表可访问', initial.code === 200 && Array.isArray(initial.data),
    `code=${initial.code} 条数=${initial.data?.length ?? 0}`)

  /* ---------- R-116 新增字段 ---------- */
  const orderNo = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: `order_no_${stamp}`,
    fieldName: '关联订单号',
    fieldType: 'TEXT',
    required: 0,
    placeholder: '请输入 12 位订单号',
    sort: 1,
    status: 1
  })
  ctx.orderNoFieldId = orderNo.data?.id || null
  log('新增文本字段', orderNo.code === 200 && Boolean(ctx.orderNoFieldId)
    && orderNo.data?.fieldTypeLabel === '文本',
    `id=${ctx.orderNoFieldId} code=${orderNo.data?.fieldCode}`)

  const duplicated = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: `order_no_${stamp}`,
    fieldName: '重复编码字段',
    fieldType: 'TEXT'
  })
  log('字段编码重复被拦截', duplicated.code === 409,
    `code=${duplicated.code} message=${duplicated.message}`)

  const badCode = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: 'OrderNo',
    fieldName: '大写编码字段',
    fieldType: 'TEXT'
  })
  log('字段编码格式非法被拦截', badCode.code === 400, `code=${badCode.code} message=${badCode.message}`)

  const noOptions = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: `refund_way_empty_${stamp}`,
    fieldName: '空选项下拉',
    fieldType: 'SELECT'
  })
  log('下拉字段缺少选项被拦截', noOptions.code === 400,
    `code=${noOptions.code} message=${noOptions.message}`)

  const refundWay = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: `refund_way_${stamp}`,
    fieldName: '退款方式',
    fieldType: 'SELECT',
    options: ['原路退回', '余额', '原路退回'],
    defaultValue: '原路退回',
    required: 0,
    sort: 2,
    status: 1
  })
  ctx.refundWayFieldId = refundWay.data?.id || null
  log('新增下拉字段并去重选项',
    refundWay.code === 200 && Array.isArray(refundWay.data?.options)
      && refundWay.data.options.length === 2,
    `options=${refundWay.data?.options?.join('/')}`)

  const refundAmount = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: `refund_amount_${stamp}`,
    fieldName: '退款金额',
    fieldType: 'NUMBER',
    required: 1,
    placeholder: '单位：元',
    sort: 3,
    status: 1
  })
  ctx.refundAmountFieldId = refundAmount.data?.id || null
  log('新增必填数字字段',
    refundAmount.code === 200 && Number(refundAmount.data?.required) === 1,
    `id=${ctx.refundAmountFieldId} required=${refundAmount.data?.required}`)

  const expectDate = await call('POST', '/admin/order-fields', ctx.admin.token, {
    fieldCode: `expect_date_${stamp}`,
    fieldName: '期望到账日期',
    fieldType: 'DATE',
    required: 0,
    sort: 4,
    status: 1
  })
  ctx.expectDateFieldId = expectDate.data?.id || null
  log('新增日期字段', expectDate.code === 200, `id=${ctx.expectDateFieldId}`)

  /* ---------- R-117 修改字段 ---------- */
  const changeCode = await call('PUT', `/admin/order-fields/${ctx.orderNoFieldId}`, ctx.admin.token, {
    fieldCode: `order_no_changed_${stamp}`,
    fieldName: '关联订单号',
    fieldType: 'TEXT'
  })
  log('字段编码创建后不可修改', changeCode.code === 400,
    `code=${changeCode.code} message=${changeCode.message}`)

  const renameField = await call('PUT', `/admin/order-fields/${ctx.orderNoFieldId}`, ctx.admin.token, {
    fieldCode: `order_no_${stamp}`,
    fieldName: '订单编号',
    fieldType: 'TEXT',
    sort: 1,
    status: 1
  })
  log('字段名称可修改', renameField.code === 200 && renameField.data?.fieldName === '订单编号',
    `fieldName=${renameField.data?.fieldName}`)

  /* ---------- R-122 新增模板 ---------- */
  const template = await call('POST', '/admin/order-templates', ctx.admin.token, {
    templateName: `退款工单_${stamp}`,
    category: '订单问题',
    defaultPriority: 'P1',
    fieldIds: [ctx.orderNoFieldId, ctx.refundWayFieldId, ctx.refundAmountFieldId, ctx.expectDateFieldId],
    description: '退款场景结构化建单',
    sort: 1,
    status: 1
  })
  ctx.templateId = template.data?.id || null
  log('新增模板并绑定字段',
    template.code === 200 && Boolean(ctx.templateId)
      && (template.data?.fields || []).length === 4,
    `id=${ctx.templateId} fields=${template.data?.fields?.map((f) => f.fieldName).join('/')}`)

  const duplicatedTemplate = await call('POST', '/admin/order-templates', ctx.admin.token, {
    templateName: `退款工单_${stamp}`,
    fieldIds: [ctx.orderNoFieldId]
  })
  log('模板重名被拦截', duplicatedTemplate.code === 409,
    `code=${duplicatedTemplate.code} message=${duplicatedTemplate.message}`)

  const missingField = await call('POST', '/admin/order-templates', ctx.admin.token, {
    templateName: `无效字段模板_${stamp}`,
    fieldIds: [99999999]
  })
  log('模板绑定不存在字段被拦截', missingField.code === 404,
    `code=${missingField.code} message=${missingField.message}`)

  /* ---------- R-119 引用保护 ---------- */
  const referenced = await call('DELETE', `/admin/order-fields/${ctx.orderNoFieldId}`, ctx.admin.token)
  log('删除被模板引用字段被拦截', referenced.code === 409,
    `code=${referenced.code} message=${referenced.message}`)

  /* ---------- R-126 建单表单 ---------- */
  const form = await call('GET', '/workorders/form', ctx.admin.token)
  const formFields = form.data?.fields || []
  const formTemplates = form.data?.templates || []
  const formTemplate = formTemplates.find((item) => item.id === ctx.templateId)
  log('建单表单返回字段与模板',
    form.code === 200 && formFields.length >= 4 && Boolean(formTemplate)
      && (formTemplate?.fields || []).length === 4,
    `字段=${formFields.length} 模板=${formTemplates.length}`)
  log('建单表单返回分类与类型字典',
    (form.data?.categories || []).includes('订单问题')
      && (form.data?.fieldTypes || []).length === 5,
    `分类=${form.data?.categories?.length} 类型=${form.data?.fieldTypes?.length}`)

  const userForm = await call('GET', '/workorders/form', ctx.user.token)
  log('普通用户可读取建单表单', userForm.code === 200, `code=${userForm.code}`)

  const agentAdmin = await call('GET', '/admin/order-fields', ctx.agent.token)
  log('坐席访问字段管理被拒', agentAdmin.code === 403 || agentAdmin.http === 403,
    `code=${agentAdmin.code}`)

  /* ---------- R-49 按模板建单 ---------- */
  const createOrder = async (payload) =>
    call('POST', '/workorders', ctx.admin.token, {
      title: '订单退款未到账需要协助核实',
      content: '订单在 10 月 20 日申请退款，至今未到账，请协助核实处理进度。',
      source: 'MANUAL',
      ...payload
    })

  const created = await createOrder({
    templateId: ctx.templateId,
    customFields: [
      { fieldId: ctx.orderNoFieldId, value: 'SO202610240001' },
      { fieldId: ctx.refundWayFieldId, value: '原路退回' },
      { fieldId: ctx.refundAmountFieldId, value: '199.00' },
      { fieldId: ctx.expectDateFieldId, value: '2026-10-30' }
    ]
  })
  ctx.orderId = created.data?.id || null
  log('按模板创建工单并补齐默认分类与优先级',
    created.code === 200 && created.data?.category === '订单问题'
      && created.data?.priority === 'P1' && Boolean(created.data?.templateName),
    `category=${created.data?.category} priority=${created.data?.priority} template=${created.data?.templateName}`)

  const detail = await call('GET', `/workorders/${ctx.orderId}`, ctx.admin.token)
  const values = detail.data?.workOrder?.customFields || []
  log('工单详情返回自定义字段值快照',
    detail.code === 200 && values.length === 4
      && values.some((item) => item.fieldName === '退款金额' && item.value === '199.00'),
    `字段值=${values.map((item) => `${item.fieldName}=${item.value}`).join(' / ')}`)

  const missingRequired = await createOrder({
    templateId: ctx.templateId,
    customFields: [{ fieldId: ctx.orderNoFieldId, value: 'SO202610240002' }]
  })
  log('必填字段缺失被拦截', missingRequired.code === 400,
    `code=${missingRequired.code} message=${missingRequired.message}`)

  const badNumber = await createOrder({
    templateId: ctx.templateId,
    customFields: [
      { fieldId: ctx.refundAmountFieldId, value: 'abc' }
    ]
  })
  log('数字字段格式错误被拦截', badNumber.code === 400,
    `code=${badNumber.code} message=${badNumber.message}`)

  const badOption = await createOrder({
    templateId: ctx.templateId,
    customFields: [
      { fieldId: ctx.refundAmountFieldId, value: '88.00' },
      { fieldId: ctx.refundWayFieldId, value: '不存在的选项' }
    ]
  })
  log('下拉字段非法选项被拦截', badOption.code === 400,
    `code=${badOption.code} message=${badOption.message}`)

  const badDate = await createOrder({
    templateId: ctx.templateId,
    customFields: [
      { fieldId: ctx.refundAmountFieldId, value: '88.00' },
      { fieldId: ctx.expectDateFieldId, value: '2026/10/30' }
    ]
  })
  log('日期字段格式错误被拦截', badDate.code === 400,
    `code=${badDate.code} message=${badDate.message}`)

  const duplicatedValue = await createOrder({
    templateId: ctx.templateId,
    customFields: [
      { fieldId: ctx.refundAmountFieldId, value: '88.00' },
      { fieldId: ctx.refundAmountFieldId, value: '99.00' }
    ]
  })
  log('同一字段重复提交被拦截', duplicatedValue.code === 400,
    `code=${duplicatedValue.code} message=${duplicatedValue.message}`)

  const foreignField = await createOrder({
    templateId: ctx.templateId,
    customFields: [
      { fieldId: ctx.refundAmountFieldId, value: '88.00' },
      { fieldId: 99999999, value: '无效字段' }
    ]
  })
  log('提交不属于模板的字段被拦截', foreignField.code === 400,
    `code=${foreignField.code} message=${foreignField.message}`)

  const noTemplateValues = await createOrder({
    category: '其他',
    customFields: [{ fieldId: ctx.orderNoFieldId, value: 'SO202610240003' }]
  })
  log('未选模板提交字段值被拦截',
    noTemplateValues.code === 400 && String(noTemplateValues.message || '').includes('模板'),
    `code=${noTemplateValues.code} message=${noTemplateValues.message}`)

  const plainOrder = await createOrder({ category: '其他' })
  log('未选模板仍可普通建单', plainOrder.code === 200 && !plainOrder.data?.templateName,
    `id=${plainOrder.data?.id} customFields=${plainOrder.data?.customFields?.length ?? 0}`)

  /* ---------- R-118 停用字段 ---------- */
  const disableField = await call('PUT', `/admin/order-fields/${ctx.refundWayFieldId}/status`,
    ctx.admin.token, { status: 0 })
  log('停用字段成功', disableField.code === 200 && Number(disableField.data?.status) === 0,
    `status=${disableField.data?.status}`)

  const formAfterDisable = await call('GET', '/workorders/form', ctx.admin.token)
  const disabledVisible = (formAfterDisable.data?.fields || [])
    .some((item) => item.id === ctx.refundWayFieldId)
  const templateAfterDisable = (formAfterDisable.data?.templates || [])
    .find((item) => item.id === ctx.templateId)
  log('停用字段后建单表单不再返回',
    formAfterDisable.code === 200 && !disabledVisible
      && (templateAfterDisable?.fields || []).length === 3,
    `表单字段含停用字段=${disabledVisible} 模板字段=${templateAfterDisable?.fields?.length}`)

  const detailAfterDisable = await call('GET', `/workorders/${ctx.orderId}`, ctx.admin.token)
  const valuesAfterDisable = detailAfterDisable.data?.workOrder?.customFields || []
  log('停用字段后历史工单仍展示字段值',
    detailAfterDisable.code === 200 && valuesAfterDisable.length === 4,
    `字段值=${valuesAfterDisable.length}`)

  /* ---------- R-124 停用模板 ---------- */
  const disableTemplate = await call('PUT', `/admin/order-templates/${ctx.templateId}/status`,
    ctx.admin.token, { status: 0 })
  log('停用模板成功', disableTemplate.code === 200 && Number(disableTemplate.data?.status) === 0,
    `status=${disableTemplate.data?.status}`)

  const useDisabledTemplate = await createOrder({
    templateId: ctx.templateId,
    customFields: [{ fieldId: ctx.refundAmountFieldId, value: '66.00' }]
  })
  log('停用模板不可用于建单', useDisabledTemplate.code === 404,
    `code=${useDisabledTemplate.code} message=${useDisabledTemplate.message}`)

  /* ---------- R-125 删除模板 + R-119 删除字段 ---------- */
  const deleteTemplate = await call('DELETE', `/admin/order-templates/${ctx.templateId}`, ctx.admin.token)
  log('删除模板成功', deleteTemplate.code === 200, `code=${deleteTemplate.code}`)
  ctx.templateId = null

  const deleteField = await call('DELETE', `/admin/order-fields/${ctx.refundWayFieldId}`, ctx.admin.token)
  log('模板移除后可删除字段', deleteField.code === 200, `code=${deleteField.code}`)
  ctx.refundWayFieldId = null

  const detailAfterDelete = await call('GET', `/workorders/${ctx.orderId}`, ctx.admin.token)
  const valuesAfterDelete = detailAfterDelete.data?.workOrder?.customFields || []
  log('删除模板与字段后历史工单字段值仍完整',
    detailAfterDelete.code === 200 && valuesAfterDelete.length === 4
      && valuesAfterDelete.some((item) => item.fieldName === '退款方式'),
    `字段值=${valuesAfterDelete.map((item) => item.fieldName).join('/')}`)
}

main()
  .catch((error) => {
    log('脚本执行未抛出异常', false, error?.message || String(error))
  })
  .finally(async () => {
    await cleanup()
    const failed = results.filter((item) => !item.ok)
    console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`)
    process.exit(failed.length ? 1 : 0)
  })
