<template>
  <el-dialog v-model="visible" title="新建工单" width="560px" append-to-body class="order-create">
    <el-form
      ref="formRef"
      :model="formModel"
      :rules="fieldRules"
      label-position="top"
      @submit.prevent
    >
      <el-form-item v-if="templates.length" label="工单模板（可选）">
        <el-select
          v-model="form.templateId"
          clearable
          placeholder="选择模板后按模板填写结构化字段"
          class="full-width"
          :loading="templatesLoading"
          @change="onTemplateChange"
        >
          <el-option
            v-for="tpl in templates"
            :key="tpl.id"
            :label="tpl.templateName"
            :value="tpl.id"
          />
        </el-select>
        <p v-if="activeTemplate?.description" class="template-tip">{{ activeTemplate.description }}</p>
      </el-form-item>
      <el-form-item label="工单标题" prop="title">
        <el-input
          v-model="form.title"
          maxlength="100"
          show-word-limit
          placeholder="用一句话描述问题，例如：订单 20261020001 申请退款未到账"
        />
      </el-form-item>
      <el-form-item label="问题描述" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          resize="none"
          placeholder="补充账号、订单号、发生时间、复现步骤等信息，至少 10 个字"
        />
      </el-form-item>
      <div class="form-row">
        <el-form-item label="问题分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" class="full-width">
            <el-option v-for="item in ORDER_CATEGORIES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" class="full-width">
            <el-option
              v-for="item in priorityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item v-if="isAdmin" label="指定受理坐席（可选）">
        <el-select
          v-model="form.assigneeId"
          clearable
          placeholder="暂不指定则进入待分配"
          class="full-width"
          :loading="agentsLoading"
        >
          <el-option
            v-for="agent in agents"
            :key="agent.agentId"
            :label="`${agent.agentName}（${agent.onlineStatus || '离线'}）`"
            :value="agent.agentId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="备注（可选）">
        <el-input v-model="form.remark" maxlength="255" show-word-limit placeholder="给处理坐席的补充说明" />
      </el-form-item>
      <template v-if="activeFields.length">
        <el-divider content-position="left">模板字段</el-divider>
        <el-form-item
          v-for="field in activeFields"
          :key="field.id"
          :label="field.fieldName"
          :prop="`cf_${field.id}`"
          :required="field.required === 1"
        >
          <el-select
            v-if="field.fieldType === 'SELECT'"
            v-model="customFields[`cf_${field.id}`]"
            class="full-width"
            clearable
            :placeholder="field.placeholder || `请选择${field.fieldName}`"
          >
            <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
          </el-select>
          <el-date-picker
            v-else-if="field.fieldType === 'DATE'"
            v-model="customFields[`cf_${field.id}`]"
            type="date"
            value-format="YYYY-MM-DD"
            class="full-width"
            :placeholder="field.placeholder || `请选择${field.fieldName}`"
          />
          <el-input
            v-else-if="field.fieldType === 'MULTILINE'"
            v-model="customFields[`cf_${field.id}`]"
            type="textarea"
            :rows="3"
            maxlength="2000"
            resize="none"
            :placeholder="field.placeholder || `请填写${field.fieldName}`"
          />
          <el-input
            v-else
            v-model="customFields[`cf_${field.id}`]"
            maxlength="2000"
            :placeholder="field.placeholder || `请填写${field.fieldName}`"
          />
          <p v-if="field.description" class="field-tip">{{ field.description }}</p>
        </el-form-item>
      </template>
      <el-alert
        v-if="sessionId"
        type="info"
        :closable="false"
        show-icon
        title="该工单将关联当前会话，坐席可直接查看完整对话上下文"
      />
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        class="zk-submit-btn"
        :class="{ 'is-loading': submitting, 'is-done': submitted }"
        :disabled="submitting || submitted"
        @click="submit"
      >
        <span class="zk-submit-label">{{ submitted ? '已提交' : '提交工单' }}</span>
        <span v-if="submitting" class="zk-submit-spin" aria-hidden="true" />
        <span v-else-if="submitted" class="zk-submit-check" aria-hidden="true">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
            <path
              d="M3.4 8.4l3.1 3.1 6.1-6.9"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createWorkOrder, getAgentList, getOrderFormConfig } from '@/api'
import { getUser } from '@/api/http'
import { prefersReducedMotion } from '@/composables/motion'
import { ORDER_CATEGORIES, PRIORITY_OPTIONS } from '@/utils/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  sessionId: { type: [Number, String], default: null },
  defaultTitle: { type: String, default: '' },
  defaultContent: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'created'])

const user = getUser()
const isAdmin = computed(() => user?.roleCode === 'ADMIN')
const priorityOptions = PRIORITY_OPTIONS.filter((item) => item.value)

const formRef = ref(null)
const submitting = ref(false)
const submitted = ref(false)
const agents = ref([])
const agentsLoading = ref(false)
const templates = ref([])
const templatesLoading = ref(false)
const templatesLoaded = ref(false)
const customFields = reactive({})

const form = reactive({
  title: '',
  content: '',
  category: '',
  priority: 'P2',
  assigneeId: null,
  remark: '',
  templateId: null
})

const rules = {
  title: [
    { required: true, message: '请填写工单标题', trigger: 'blur' },
    { min: 4, max: 100, message: '标题长度须为 4-100 个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写问题描述', trigger: 'blur' },
    { min: 10, max: 2000, message: '问题描述长度须为 10-2000 个字符', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择问题分类', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }]
}

/** 当前选中的模板及其绑定字段 */
const activeTemplate = computed(
  () => templates.value.find((item) => item.id === form.templateId) || null
)
const activeFields = computed(() => activeTemplate.value?.fields || [])

/** 表单模型 = 基础表单 + 模板字段值，供 Element Plus 统一校验 */
const formModel = computed(() => ({ ...form, ...customFields }))

/** 模板字段按后端 required 标记动态追加必填规则 */
const fieldRules = computed(() => {
  const dynamic = {}
  for (const field of activeFields.value) {
    if (field.required !== 1) continue
    dynamic[`cf_${field.id}`] = [
      {
        required: true,
        message: `请填写${field.fieldName}`,
        trigger: ['SELECT', 'DATE'].includes(field.fieldType) ? 'change' : 'blur'
      }
    ]
  }
  return { ...rules, ...dynamic }
})

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

async function loadAgents() {
  if (!isAdmin.value || agents.value.length) return
  agentsLoading.value = true
  try {
    const list = await getAgentList()
    agents.value = Array.isArray(list) ? list : []
  } catch {
    agents.value = []
  } finally {
    agentsLoading.value = false
  }
}

/** 启用中的工单模板与字段，首次打开弹窗时加载 */
async function loadFormConfig() {
  if (templatesLoaded.value) return
  templatesLoading.value = true
  try {
    const config = await getOrderFormConfig()
    templates.value = Array.isArray(config?.templates) ? config.templates : []
    templatesLoaded.value = true
  } catch {
    templates.value = []
  } finally {
    templatesLoading.value = false
  }
}

/** 切换模板：带出默认分类与优先级，并按字段默认值初始化填写项 */
function onTemplateChange() {
  const tpl = activeTemplate.value
  if (tpl) {
    if (tpl.category) form.category = tpl.category
    if (tpl.defaultPriority) form.priority = tpl.defaultPriority
  }
  resetCustomFields()
}

function resetCustomFields() {
  Object.keys(customFields).forEach((key) => delete customFields[key])
  for (const field of activeFields.value) {
    customFields[`cf_${field.id}`] = field.defaultValue || ''
  }
}

/** 读取字段值并规整空串，后端只接收有效值 */
function fieldValue(field) {
  const raw = customFields[`cf_${field.id}`]
  if (raw === null || raw === undefined) return null
  const text = String(raw).trim()
  return text || null
}

/* 校验失败：只摇第一个错误字段，边框与文字提示沿用 Element Plus 的错误态 */
function shakeFirstError() {
  nextTick(() => {
    const item = formRef.value?.$el?.querySelector('.el-form-item.is-error')
    if (!item) return
    item.classList.remove('zk-shake')
    void item.offsetWidth
    item.classList.add('zk-shake')
    window.setTimeout(() => item.classList.remove('zk-shake'), 460)
  })
}

async function submit() {
  if (submitting.value || submitted.value) return
  try {
    await formRef.value.validate()
  } catch {
    shakeFirstError()
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      category: form.category,
      priority: form.priority,
      source: props.sessionId ? 'CHAT' : 'MANUAL',
      remark: form.remark.trim() || null
    }
    if (props.sessionId) payload.sessionId = Number(props.sessionId)
    if (isAdmin.value && form.assigneeId) payload.assigneeId = form.assigneeId
    if (form.templateId) {
      payload.templateId = form.templateId
      const values = activeFields.value
        .map((field) => ({ fieldId: field.id, value: fieldValue(field) }))
        .filter((item) => item.value !== null)
      if (values.length) payload.customFields = values
    }
    const order = await createWorkOrder(payload)
    ElMessage.success('工单已提交，我们会尽快处理')
    submitting.value = false
    submitted.value = true
    // 对勾画完 350ms 再关闭，成功态与打开路径一致
    await new Promise((resolve) => {
      window.setTimeout(resolve, prefersReducedMotion() ? 0 : 380)
    })
    visible.value = false
    emit('created', order)
  } catch (error) {
    submitting.value = false
    ElMessage.error(error?.response?.data?.message || '提交失败，请稍后重试')
  }
}

watch(visible, (open) => {
  if (!open) return
  submitting.value = false
  submitted.value = false
  form.title = props.defaultTitle || ''
  form.content = props.defaultContent || ''
  form.category = ''
  form.priority = 'P2'
  form.assigneeId = null
  form.remark = ''
  form.templateId = null
  resetCustomFields()
  loadAgents()
  loadFormConfig()
})
</script>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-width {
  width: 100%;
}

.template-tip,
.field-tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--zk-text-3);
}

/* 提交按钮三连变：文字淡出 → 转圈 → 对勾 350ms → 成功关闭 */
.zk-submit-btn {
  position: relative;
  min-width: 122px;
}

.zk-submit-label {
  transition: opacity 140ms var(--zk-ease-out);
}

.zk-submit-btn.is-loading .zk-submit-label,
.zk-submit-btn.is-done .zk-submit-label {
  opacity: 0;
}

.zk-submit-spin {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 14px;
  height: 14px;
  margin: -7px 0 0 -7px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: zk-spin 720ms cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.zk-submit-check {
  position: absolute;
  left: 50%;
  top: 50%;
  display: inline-flex;
  margin: -7px 0 0 -7px;
}

.zk-submit-check path {
  stroke-dasharray: 24;
  stroke-dashoffset: 24;
  animation: zk-check-draw 350ms var(--zk-ease-out) 40ms forwards;
}

/* 校验失败字段：红色边框由 Element Plus 输出，这里只补充更明确的描边 */
:deep(.el-form-item.is-error .el-input__wrapper),
:deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

@media (max-width: 640px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
