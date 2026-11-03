<template>
  <el-dialog
    v-model="visible"
    :title="form.id ? '编辑工单规则' : '新增工单规则'"
    width="640px"
    append-to-body
    @closed="onClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-row">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" maxlength="64" show-word-limit placeholder="如：P0 紧急自动分配" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="form.ruleType" class="full-select" placeholder="选择规则类型">
            <el-option
              v-for="item in ORDER_RULE_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </div>

      <div class="form-row">
        <el-form-item v-if="form.ruleType !== 'AUTOMATION'" label="触发事件" prop="triggerEvent">
          <el-select v-model="form.triggerEvent" class="full-select" placeholder="选择触发事件">
            <el-option
              v-for="item in ORDER_RULE_EVENT_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.ruleType === 'AUTOMATION' ? '执行延迟（分钟）' : '延迟执行（分钟）'">
          <el-input-number
            v-model="form.delayMinutes"
            :min="0"
            :max="525600"
            controls-position="right"
            class="full-num"
          />
        </el-form-item>
      </div>
      <p class="form-hint">
        延迟为 0 表示事件命中后同事务立即执行；大于 0 表示进入待执行队列，由每分钟扫描任务到期执行。
      </p>

      <el-divider content-position="left">命中条件（留空表示不限）</el-divider>
      <div class="form-row form-row-3">
        <el-form-item label="优先级">
          <el-select v-model="form.matchPriority" class="full-select" placeholder="不限">
            <el-option label="不限" value="" />
            <el-option
              v-for="item in priorityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="问题分类">
          <el-select v-model="form.matchCategory" class="full-select" placeholder="不限" clearable>
            <el-option label="不限" value="" />
            <el-option v-for="item in ORDER_CATEGORIES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源渠道">
          <el-select v-model="form.matchSource" class="full-select" placeholder="不限">
            <el-option
              v-for="item in ORDER_SOURCE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </div>

      <el-divider content-position="left">命中动作</el-divider>
      <div class="form-row">
        <el-form-item label="动作类型" prop="actionType">
          <el-select v-model="form.actionType" class="full-select" placeholder="选择动作">
            <el-option
              v-for="item in ORDER_RULE_ACTION_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.actionType === 'AUTO_ASSIGN'" label="分配技能组">
          <el-select v-model="form.assignGroupId" class="full-select" placeholder="全体在岗坐席" clearable :loading="groupsLoading">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
      </div>

      <!-- 动作目标值：按动作类型动态渲染 -->
      <el-form-item v-if="form.actionType === 'CHANGE_PRIORITY'" label="目标优先级" prop="actionValue">
        <el-radio-group v-model="form.actionValue">
          <el-radio v-for="item in priorityOptions" :key="item.value" :label="item.value" border>
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-else-if="form.actionType === 'CHANGE_STATUS'" label="目标状态" prop="actionValue">
        <el-select v-model="form.actionValue" class="full-select" placeholder="选择目标状态">
          <el-option
            v-for="item in statusTargetOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <p class="form-hint">目标状态需符合工单状态机流转规则，非法流转将被服务端拒绝并记执行失败。</p>
      </el-form-item>

      <el-form-item v-else-if="form.actionType === 'NOTIFY_ROLE'" label="通知角色" prop="actionValue">
        <el-radio-group v-model="form.actionValue">
          <el-radio v-for="item in ORDER_RULE_NOTIFY_ROLE_OPTIONS" :key="item.value" :label="item.value" border>
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-else-if="form.actionType === 'ASSIGN_AGENT'" label="指定坐席" prop="actionValue">
        <el-select
          v-model="form.actionValue"
          class="full-select"
          filterable
          placeholder="选择坐席"
          :loading="agentsLoading"
        >
          <el-option
            v-for="a in agents"
            :key="a.agentId"
            :label="`${a.agentName}（${a.onlineStatus || '离线'}）`"
            :value="String(a.agentId)"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.description !== undefined" label="规则说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="255"
          show-word-limit
          resize="none"
          placeholder="说明该规则的适用场景"
        />
      </el-form-item>

      <div class="form-row">
        <el-form-item label="排序号">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </div>

      <!-- 命中预览结果 -->
      <div v-if="previewResult" class="preview-box">
        <div class="preview-head">
          <span>命中预览：当前匹配 <b class="zk-num">{{ previewResult.matched }}</b> 个在跑工单</span>
          <el-button link type="danger" @click="previewResult = null">清除</el-button>
        </div>
        <ul v-if="previewResult.samples?.length" class="preview-samples" v-reveal.group style="--zk-stagger: 40ms">
          <li v-for="s in previewResult.samples" :key="s.id">
            <span class="ps-no">{{ s.orderNo }}</span>
            <span class="ps-title">{{ s.title }}</span>
            <StatusPill :label="dictLabel(PRIORITY_DICT, s.priority)" :tone="dictTone(PRIORITY_DICT, s.priority)" />
            <StatusPill :label="dictLabel(ORDER_STATUS_DICT, s.status)" :tone="dictTone(ORDER_STATUS_DICT, s.status)" />
          </li>
        </ul>
        <p v-else class="preview-empty">暂无命中的在跑工单（仅统计非终态工单，最多展示 {{ previewResult.sampleLimit }} 条样本）</p>
      </div>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button :loading="previewing" @click="doPreview">命中预览</el-button>
        <div class="grow" />
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          class="zk-submit-btn"
          :class="{ 'is-loading': saving, 'is-done': saved }"
          :disabled="saving || saved"
          @click="submit"
        >
          <span class="zk-submit-label">{{ saved ? '已保存' : '保存' }}</span>
          <span v-if="saving" class="zk-submit-spin" aria-hidden="true" />
          <span v-else-if="saved" class="zk-submit-check" aria-hidden="true">
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
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import {
  createOrderRule,
  updateOrderRule,
  previewOrderRule,
  getSkillGroups,
  getAgentList
} from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import {
  ORDER_RULE_TYPE_OPTIONS,
  ORDER_RULE_EVENT_OPTIONS,
  ORDER_RULE_ACTION_OPTIONS,
  ORDER_RULE_NOTIFY_ROLE_OPTIONS,
  ORDER_CATEGORIES,
  ORDER_SOURCE_OPTIONS,
  PRIORITY_OPTIONS,
  PRIORITY_DICT,
  ORDER_STATUS_DICT,
  dictLabel,
  dictTone
} from '@/utils/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  rule: { type: Object, default: null },
  presetType: { type: String, default: 'TRIGGER' }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const priorityOptions = PRIORITY_OPTIONS.filter((item) => item.value)
const statusTargetOptions = [
  { label: '已分配', value: 'ASSIGNED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '等待客户反馈', value: 'WAITING_CUSTOMER' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' }
]

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const saving = ref(false)
const saved = ref(false)
const previewing = ref(false)
const previewResult = ref(null)
const formRef = ref(null)
const groups = ref([])
const groupsLoading = ref(false)
const agents = ref([])
const agentsLoading = ref(false)

const emptyForm = () => ({
  id: null,
  ruleName: '',
  ruleType: props.presetType || 'TRIGGER',
  triggerEvent: '',
  delayMinutes: 0,
  matchPriority: '',
  matchCategory: '',
  matchSource: '',
  actionType: 'CHANGE_PRIORITY',
  actionValue: '',
  assignGroupId: null,
  description: '',
  sort: 0,
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  ruleName: [
    { required: true, message: '请填写规则名称', trigger: 'blur' },
    { min: 2, max: 64, message: '规则名称长度须为 2-64 个字符', trigger: 'blur' }
  ],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  triggerEvent: [
    {
      validator: (rule, value, callback) => {
        if (form.ruleType !== 'AUTOMATION' && !value) callback(new Error('请选择触发事件'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  actionType: [{ required: true, message: '请选择动作类型', trigger: 'change' }],
  actionValue: [
    {
      validator: (rule, value, callback) => {
        if (['CHANGE_PRIORITY', 'CHANGE_STATUS', 'NOTIFY_ROLE', 'ASSIGN_AGENT'].includes(form.actionType) && !value) {
          callback(new Error('请设置动作目标值'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function fillFrom(row) {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      id: row.id,
      ruleName: row.ruleName,
      ruleType: row.ruleType,
      triggerEvent: row.triggerEvent || '',
      delayMinutes: row.delayMinutes ?? 0,
      matchPriority: row.matchPriority || '',
      matchCategory: row.matchCategory || '',
      matchSource: row.matchSource || '',
      actionType: row.actionType,
      actionValue: row.actionValue || '',
      assignGroupId: row.assignGroupId ?? null,
      description: row.description || '',
      sort: row.sort ?? 0,
      status: Number(row.status) === 0 ? 0 : 1
    })
  } else {
    form.ruleType = props.presetType || 'TRIGGER'
  }
  previewResult.value = null
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    saving.value = false
    saved.value = false
    fillFrom(props.rule)
  }
)

// 切换类型时清理不适用的字段
watch(
  () => form.ruleType,
  (t) => {
    if (t === 'AUTO_ASSIGN') {
      form.actionType = 'AUTO_ASSIGN'
    } else if (t === 'AUTOMATION' && form.actionType === 'AUTO_ASSIGN') {
      form.actionType = 'CHANGE_PRIORITY'
      form.assignGroupId = null
    }
  }
)

watch(
  () => form.actionType,
  (a) => {
    if (a === 'AUTO_ASSIGN') {
      form.actionValue = ''
      ensureGroups()
    } else if (a === 'CHANGE_PRIORITY') {
      if (!['P0', 'P1', 'P2', 'P3'].includes(form.actionValue)) form.actionValue = 'P1'
    } else if (a === 'ASSIGN_AGENT') {
      form.actionValue = ''
      ensureAgents()
    } else if (a === 'NOTIFY_ROLE') {
      if (!['ADMIN', 'CREATOR', 'ASSIGNEE'].includes(form.actionValue)) form.actionValue = 'ADMIN'
    } else if (a === 'CHANGE_STATUS') {
      if (!statusTargetOptions.some((o) => o.value === form.actionValue)) form.actionValue = ''
    }
  }
)

async function ensureGroups() {
  if (groups.value.length || groupsLoading.value) return
  groupsLoading.value = true
  try {
    const list = await getSkillGroups()
    groups.value = (Array.isArray(list) ? list : []).filter((g) => Number(g.status) === 1)
  } catch {
    groups.value = []
  } finally {
    groupsLoading.value = false
  }
}

async function ensureAgents() {
  if (agents.value.length || agentsLoading.value) return
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

function buildPayload() {
  return {
    ruleName: form.ruleName.trim(),
    ruleType: form.ruleType,
    triggerEvent: form.ruleType === 'AUTOMATION' ? null : form.triggerEvent || null,
    delayMinutes: form.delayMinutes ?? 0,
    matchPriority: form.matchPriority || null,
    matchCategory: form.matchCategory || null,
    matchSource: form.matchSource || null,
    actionType: form.actionType,
    actionValue: form.actionType === 'AUTO_ASSIGN' ? null : form.actionValue || null,
    assignGroupId: form.actionType === 'AUTO_ASSIGN' ? form.assignGroupId || null : null,
    description: (form.description || '').trim() || null,
    sort: form.sort ?? 0,
    status: form.status
  }
}

async function doPreview() {
  previewing.value = true
  try {
    previewResult.value = await previewOrderRule(buildPayload())
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '命中预览失败')
  } finally {
    previewing.value = false
  }
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
  if (saving.value || saved.value) return
  try {
    await formRef.value.validate()
  } catch {
    shakeFirstError()
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (form.id) {
      await updateOrderRule(form.id, payload)
    } else {
      await createOrderRule(payload)
    }
    ElMessage.success(form.id ? '规则已更新' : '规则已创建')
    saving.value = false
    saved.value = true
    // 对勾画完 350ms 再关闭，成功态与打开路径一致
    await new Promise((resolve) => {
      window.setTimeout(resolve, prefersReducedMotion() ? 0 : 380)
    })
    visible.value = false
    emit('saved')
  } catch (e) {
    saving.value = false
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  }
}

function onClosed() {
  previewResult.value = null
}
</script>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-row-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.full-select {
  width: 100%;
}

.full-num {
  width: 100%;
}

.form-hint {
  margin: -6px 0 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--zk-text-3);
}

.grow {
  flex: 1;
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-box {
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.preview-samples {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-samples li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--zk-text-2);
}

.ps-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: var(--zk-text-3);
}

.ps-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-empty {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--zk-text-3);
}

/* 提交按钮三连变：文字淡出 → 转圈 → 对勾 350ms → 成功关闭 */
.zk-submit-btn {
  position: relative;
  min-width: 96px;
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
  .form-row,
  .form-row-3 {
    grid-template-columns: 1fr;
  }
}
</style>
