<template>
  <el-drawer
    v-model="visible"
    :size="isMobile ? '100%' : '520px'"
    :with-header="false"
    append-to-body
    class="order-drawer"
  >
    <div v-if="loading" class="drawer-loading">
      <ZkSkeleton variant="card" :rows="4" />
    </div>

    <div v-else-if="order" class="zk-drawer-stagger">
      <header class="drawer-head">
        <div class="drawer-head-top">
          <StatusPill :label="statusLabel" :tone="statusTone" dot :class="{ 'zk-flash': statusFlashed }" />
          <StatusPill :label="priorityLabel" :tone="priorityTone" :class="{ 'zk-flash': priorityFlashed }" />
          <StatusPill v-if="order.overdueFlag === 1" label="SLA 超时" tone="red" />
          <StatusPill
            v-if="sla"
            :label="sla.slaStatusLabel || dictLabel(SLA_STATUS_DICT, sla.slaStatus)"
            :tone="dictTone(SLA_STATUS_DICT, sla.slaStatus)"
          />
          <button type="button" class="drawer-close" aria-label="关闭抽屉" @click="visible = false">
            ×
          </button>
        </div>
        <h3 class="drawer-title">{{ order.title }}</h3>
        <p class="drawer-no">{{ order.orderNo }}</p>
        <p class="drawer-hint">{{ statusHint }}</p>
      </header>

      <div class="meta-grid">
        <div class="meta-item">
          <span>问题分类</span>
          <b>{{ order.category || '—' }}</b>
        </div>
        <div class="meta-item">
          <span>工单来源</span>
          <b>{{ sourceLabel }}</b>
        </div>
        <div v-if="order.templateName" class="meta-item">
          <span>工单模板</span>
          <b>{{ order.templateName }}</b>
        </div>
        <div class="meta-item">
          <span>创建人</span>
          <b>{{ order.creatorName || '—' }}</b>
        </div>
        <div class="meta-item">
          <span>受理坐席</span>
          <b>{{ order.assigneeName || '未分配' }}</b>
        </div>
        <div class="meta-item">
          <span>创建时间</span>
          <b>{{ formatDateTime(order.createTime) }}</b>
        </div>
        <div class="meta-item">
          <span>SLA 截止</span>
          <b :class="{ 'text-danger': order.overdueFlag === 1 }">
            {{ formatDateTime(order.slaDeadline) }}
          </b>
        </div>
      </div>

      <section v-if="sla" class="drawer-section">
        <h4>SLA 时效</h4>
        <div class="sla-head">
          <span class="sla-policy">{{ sla.policyName || '默认时限' }}</span>
          <span v-if="Number(sla.pauseMinutes) > 0" class="sla-pause-tag">
            累计停表 {{ formatMinutes(sla.pauseMinutes) }}
          </span>
        </div>
        <ul class="sla-levels">
          <li v-for="level in slaLevels" :key="level.key" class="sla-level">
            <div class="sla-level-top">
              <span class="sla-level-name">{{ level.name }}</span>
              <StatusPill :label="level.stateLabel" :tone="level.stateTone" />
            </div>
            <div class="sla-level-meta">
              <span>时限 {{ level.limitText }}</span>
              <span v-if="level.deadlineText">截止 {{ level.deadlineText }}</span>
              <span v-if="level.remainText" :class="level.remainClass">{{ level.remainText }}</span>
            </div>
          </li>
        </ul>
        <div v-if="sla.pauses?.length" class="sla-pauses">
          <p class="sla-pauses-title">停表记录</p>
          <div v-for="pause in sla.pauses" :key="pause.id" class="sla-pause-item">
            <span class="sla-pause-type">{{ pause.pauseTypeLabel }}</span>
            <span class="sla-pause-time">
              {{ formatDateTime(pause.pauseTime) }} ~ {{ pause.resumeTime ? formatDateTime(pause.resumeTime) : '进行中' }}
            </span>
            <span v-if="pause.durationMinutes != null" class="sla-pause-dur">{{ formatMinutes(pause.durationMinutes) }}</span>
            <span v-if="pause.reason" class="sla-pause-reason">{{ pause.reason }}</span>
          </div>
        </div>
      </section>

      <section class="drawer-section">
        <h4>问题描述</h4>
        <p class="content-text">{{ order.content }}</p>
      </section>

      <section v-if="order.remark" class="drawer-section">
        <h4>备注</h4>
        <p class="content-text">{{ order.remark }}</p>
      </section>

      <section v-if="customFieldList.length" class="drawer-section">
        <h4>补充信息</h4>
        <div class="meta-grid custom-field-grid">
          <div
            v-for="item in customFieldList"
            :key="`${item.fieldId}-${item.fieldCode}`"
            class="meta-item"
          >
            <span>{{ item.fieldName }}</span>
            <b>{{ item.value || '—' }}</b>
          </div>
        </div>
      </section>

      <section v-if="order.rating" class="drawer-section">
        <h4>服务评价</h4>
        <div class="rating-result">
          <el-rate :model-value="order.rating" disabled />
          <span>{{ formatRating(order.rating) }} 分</span>
        </div>
      </section>

      <section class="drawer-section">
        <h4>流转记录</h4>
        <el-timeline v-if="logs.length" class="order-timeline" v-reveal.group style="--zk-stagger: 40ms">
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :timestamp="formatDateTime(log.createTime)"
            placement="top"
            :color="timelineColor(log.action)"
          >
            <div class="log-line">
              <b>{{ actionLabel(log.action) }}</b>
              <span v-if="showTransition(log)" class="log-status">
                {{ statusText(log.fromStatus) }} → {{ statusText(log.toStatus) }}
              </span>
            </div>
            <p class="log-remark">
              {{ log.operatorName || '系统' }}<template v-if="log.remark"> · {{ log.remark }}</template>
            </p>
          </el-timeline-item>
        </el-timeline>
        <ZkEmpty v-else size="sm" title="暂无流转记录" hint="状态变更与处理动作会在这里留下记录" />
      </section>

      <section v-if="ruleRuns.length" class="drawer-section">
        <h4>规则执行</h4>
        <ul class="rule-runs" v-reveal.group style="--zk-stagger: 40ms">
          <li v-for="run in ruleRuns" :key="run.id" class="rule-run">
            <div class="rule-run-top">
              <span class="rule-run-name">{{ run.ruleName || ('规则 #' + run.ruleId) }}</span>
              <StatusPill
                :label="dictLabel(ORDER_RULE_RUN_STATUS_DICT, run.status)"
                :tone="dictTone(ORDER_RULE_RUN_STATUS_DICT, run.status)"
              />
            </div>
            <div class="rule-run-meta">
              <span>{{ dictLabel(ORDER_RULE_EVENT_DICT, run.event, '时间驱动') }}</span>
              <span>·</span>
              <span>{{ dictLabel(ORDER_RULE_ACTION_DICT, run.actionType) }}</span>
              <span v-if="run.executedTime">·</span>
              <span v-if="run.executedTime">{{ formatDateTime(run.executedTime) }}</span>
            </div>
            <p v-if="run.result" class="rule-run-result">{{ run.result }}</p>
          </li>
        </ul>
      </section>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">关闭</el-button>
        <div class="footer-actions">
          <el-button v-if="can('ACCEPT')" type="primary" :loading="acting" @click="onAccept">
            受理工单
          </el-button>
          <el-button v-if="can('PROCESS')" :loading="acting" @click="openRemark('process')">
            添加进展
          </el-button>
          <el-button v-if="can('PAUSE')" :loading="acting" @click="openPause">挂起停表</el-button>
          <el-button v-if="can('RESUME')" type="warning" :loading="acting" @click="onResume">恢复计时</el-button>
          <el-button v-if="can('RESOLVE')" type="success" :loading="acting" @click="openRemark('resolve')">
            标记已解决
          </el-button>
          <el-button v-if="can('RATING')" type="primary" @click="openRating">评价服务</el-button>
          <el-button v-if="can('ASSIGN')" type="primary" @click="openAssign">分配坐席</el-button>
          <el-button v-if="can('PRIORITY')" @click="openPriority">调整优先级</el-button>
          <el-button v-if="can('CLOSE')" :loading="acting" @click="onClose">关闭工单</el-button>
          <el-button v-if="can('REOPEN')" :loading="acting" @click="onReopen">重开工单</el-button>
        </div>
      </div>
    </template>
  </el-drawer>

  <el-dialog v-model="remarkVisible" :title="remarkTitle" width="460px" append-to-body>
    <el-input
      v-model="remarkText"
      type="textarea"
      :rows="4"
      maxlength="255"
      show-word-limit
      resize="none"
      :placeholder="remarkPlaceholder"
    />
    <template #footer>
      <el-button @click="remarkVisible = false">取消</el-button>
      <el-button type="primary" :loading="acting" @click="submitRemark">确认提交</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="assignVisible" title="分配受理坐席" width="420px" append-to-body>
    <el-select
      v-model="assignForm.assigneeId"
      placeholder="选择坐席"
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
    <p class="dialog-tip">分配后会通知坐席，并在流转记录中留下分配人。</p>
    <template #footer>
      <el-button @click="assignVisible = false">取消</el-button>
      <el-button type="primary" :loading="acting" :disabled="!assignForm.assigneeId" @click="submitAssign">
        确认分配
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="priorityVisible" title="调整工单优先级" width="420px" append-to-body>
    <el-radio-group v-model="priorityForm.priority" class="priority-group">
      <el-radio v-for="item in priorityOptions" :key="item.value" :label="item.value" border>
        {{ item.label }}
      </el-radio>
    </el-radio-group>
    <p class="dialog-tip">调整优先级会重新计算 SLA 截止时间。</p>
    <template #footer>
      <el-button @click="priorityVisible = false">取消</el-button>
      <el-button type="primary" :loading="acting" @click="submitPriority">确认调整</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="ratingVisible" title="服务评价" width="420px" append-to-body>
    <div class="rating-form">
      <p>请为本次工单处理打分</p>
      <el-rate v-model="ratingForm.rating" show-score score-template="{value} 星" />
      <el-input
        v-model="ratingForm.ratingComment"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        resize="none"
        placeholder="补充说明（选填）"
      />
    </div>
    <template #footer>
      <el-button @click="ratingVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="acting"
        :disabled="!ratingForm.rating"
        @click="submitRating"
      >
        提交评价
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="pauseVisible" title="挂起工单停表" width="440px" append-to-body>
    <el-form label-position="top">
      <el-form-item label="停表类型">
        <el-radio-group v-model="pauseForm.pauseType" class="priority-group">
          <el-radio label="WAITING_CUSTOMER" border>等待客户反馈</el-radio>
          <el-radio label="OTHER" border>其他原因</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="停表说明">
        <el-input
          v-model="pauseForm.reason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          resize="none"
          placeholder="说明挂起原因，将写入流转记录"
        />
      </el-form-item>
    </el-form>
    <p class="dialog-tip">挂起后工单转为「等待客户反馈」，SLA 计时暂停，解决时限按停表时长顺延。</p>
    <template #footer>
      <el-button @click="pauseVisible = false">取消</el-button>
      <el-button type="primary" :loading="acting" @click="submitPause">确认挂起</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  acceptWorkOrder,
  assignWorkOrder,
  closeWorkOrder,
  getAgentList,
  getWorkOrderDetail,
  getWorkOrderLogs,
  getWorkOrderRules,
  getWorkOrderSla,
  pauseWorkOrder,
  processWorkOrder,
  rateWorkOrder,
  reopenWorkOrder,
  resolveWorkOrder,
  resumeWorkOrder,
  updateWorkOrderPriority
} from '@/api'
import { getUser } from '@/api/http'
import {
  ORDER_ACTION_DICT,
  ORDER_SOURCE_DICT,
  ORDER_STATUS_DICT,
  ORDER_STATUS_HINT,
  PRIORITY_DICT,
  ORDER_STATUS_OPTIONS,
  ORDER_RULE_EVENT_DICT,
  ORDER_RULE_ACTION_DICT,
  ORDER_RULE_RUN_STATUS_DICT,
  PRIORITY_OPTIONS,
  SLA_STATUS_DICT,
  dictLabel,
  dictTone,
  formatDateTime,
  formatMinutes,
  formatRating
} from '@/utils/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const user = getUser()
const role = user?.roleCode || ''

const detail = ref(null)
const logs = ref([])
const sla = ref(null)
const ruleRuns = ref([])
const loading = ref(false)
const acting = ref(false)
const agents = ref([])
const agentsLoading = ref(false)
const isMobile = ref(window.innerWidth <= 768)

const remarkVisible = ref(false)
const remarkMode = ref('process')
const remarkText = ref('')
const assignVisible = ref(false)
const assignForm = reactive({ assigneeId: null })
const priorityVisible = ref(false)
const priorityForm = reactive({ priority: 'P2' })
const ratingVisible = ref(false)
const ratingForm = reactive({ rating: 5, ratingComment: '' })
const pauseVisible = ref(false)
const pauseForm = reactive({ pauseType: 'WAITING_CUSTOMER', reason: '' })

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const order = computed(() => detail.value?.workOrder || null)
/** 自定义字段值：后端已按模板顺序返回，空数组时不渲染区块 */
const customFieldList = computed(() => order.value?.customFields || [])
const actions = computed(() => detail.value?.allowedActions || [])
const statusLabel = computed(() => dictLabel(ORDER_STATUS_DICT, order.value?.status))
const statusTone = computed(() => dictTone(ORDER_STATUS_DICT, order.value?.status))
const statusHint = computed(() => ORDER_STATUS_HINT[order.value?.status] || '')
const priorityLabel = computed(() => dictLabel(PRIORITY_DICT, order.value?.priority))
const priorityTone = computed(() => dictTone(PRIORITY_DICT, order.value?.priority))
const sourceLabel = computed(() => dictLabel(ORDER_SOURCE_DICT, order.value?.source))

/* 状态/优先级变化时 pill 闪一次，600ms 后把注意力交回内容 */
const statusFlashed = ref(false)
const priorityFlashed = ref(false)
let statusFlashTimer = 0
let priorityFlashTimer = 0

watch(
  () => order.value?.status,
  (now, before) => {
    if (!before || now === before) return
    statusFlashed.value = true
    window.clearTimeout(statusFlashTimer)
    statusFlashTimer = window.setTimeout(() => {
      statusFlashed.value = false
    }, 700)
  }
)

watch(
  () => order.value?.priority,
  (now, before) => {
    if (!before || now === before) return
    priorityFlashed.value = true
    window.clearTimeout(priorityFlashTimer)
    priorityFlashTimer = window.setTimeout(() => {
      priorityFlashed.value = false
    }, 700)
  }
)

/** 以服务端快照时间为基准解析时间字符串 */
function parseTime(value) {
  if (!value) return new Date()
  return new Date(String(value).replace(' ', 'T'))
}

/** 构建单级时限的展示数据：时限 / 截止 / 剩余与状态 */
function buildLevel(name, limit, deadline, done, doneLabel) {
  const limitNum = Number(limit) || 0
  const base = { key: name, name, limitText: limitNum > 0 ? formatMinutes(limitNum) : '不启用' }
  if (limitNum <= 0) {
    return { ...base, deadlineText: '', remainText: '', remainClass: '', stateLabel: '不启用', stateTone: 'slate' }
  }
  if (done) {
    return {
      ...base,
      deadlineText: deadline ? formatDateTime(deadline) : '',
      remainText: '',
      remainClass: '',
      stateLabel: doneLabel || '已完成',
      stateTone: 'teal'
    }
  }
  if (!deadline) {
    return {
      ...base,
      deadlineText: '',
      remainText: '',
      remainClass: '',
      stateLabel: doneLabel || '未开始',
      stateTone: 'slate'
    }
  }
  const now = parseTime(sla.value?.serverTime)
  const remainMin = Math.round((parseTime(deadline) - now) / 60000)
  const deadlineText = formatDateTime(deadline)
  if (remainMin <= 0) {
    return {
      ...base,
      deadlineText,
      remainText: `已超时 ${formatMinutes(Math.abs(remainMin))}`,
      remainClass: 'text-danger',
      stateLabel: '已超时',
      stateTone: 'red'
    }
  }
  const warnThreshold = (limitNum * (100 - (Number(sla.value?.warnPercent) || 80))) / 100
  const remainText = `剩余 ${formatMinutes(remainMin)}`
  if (remainMin <= warnThreshold) {
    return { ...base, deadlineText, remainText, remainClass: 'text-warn', stateLabel: '预警中', stateTone: 'amber' }
  }
  return { ...base, deadlineText, remainText, remainClass: '', stateLabel: '计时中', stateTone: 'teal' }
}

/** 三级时限：首次响应 / 回复间隔 / 解决 */
const slaLevels = computed(() => {
  const s = sla.value
  if (!s) return []
  const finished = s.status === 'FINISHED'
  const replyPending = Number(s.replyIntervalMinutes) > 0 && !s.replyDueTime && !s.firstResponseTime
  return [
    buildLevel('首次响应', s.firstResponseMinutes, s.firstResponseDeadline, Boolean(s.firstResponseTime), '已响应'),
    buildLevel('回复间隔', s.replyIntervalMinutes, s.replyDueTime, false, replyPending ? '待首响' : null),
    buildLevel('解决时限', s.resolveMinutes, s.resolveDeadline, finished, finished ? '已完结' : null)
  ]
})

const priorityOptions = PRIORITY_OPTIONS.filter((item) => item.value)
const remarkTitle = computed(() => (remarkMode.value === 'resolve' ? '标记已解决' : '添加处理进展'))
const remarkPlaceholder = computed(() =>
  remarkMode.value === 'resolve'
    ? '说明解决方式，便于创建人确认关闭'
    : '记录本次处理进展，创建人可以看到'
)

function can(action) {
  return actions.value.includes(action)
}

function readError(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function actionLabel(action) {
  return ORDER_ACTION_DICT[action]?.label || action || '操作'
}

function statusText(status) {
  return status ? ORDER_STATUS_DICT[status]?.label || status : '—'
}

// 备注、评分、SLA 提醒不改变状态，避免出现“已关闭 → 已关闭”这类无效流转
function showTransition(log) {
  if (!log?.fromStatus && !log?.toStatus) return false
  return log.fromStatus !== log.toStatus
}

function timelineColor(action) {
  if (action === 'OVERDUE') return '#ef4444'
  if (action === 'RESOLVE' || action === 'ACCEPT') return '#10b981'
  if (action === 'REOPEN') return '#f59e0b'
  return '#cbd5e1'
}

async function load() {
  if (!props.orderId) return
  loading.value = true
  try {
    detail.value = await getWorkOrderDetail(props.orderId)
    await Promise.all([loadLogs(), loadSla(), loadRules()])
  } catch (error) {
    ElMessage.error(readError(error, '加载工单详情失败'))
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  try {
    const page = await getWorkOrderLogs(props.orderId, { pageNum: 1, pageSize: 50 })
    logs.value = [...(page?.list || [])].sort((a, b) => (b.id || 0) - (a.id || 0))
  } catch {
    logs.value = []
  }
}

async function loadSla() {
  try {
    sla.value = await getWorkOrderSla(props.orderId)
  } catch {
    sla.value = null
  }
}

async function loadRules() {
  try {
    const list = await getWorkOrderRules(props.orderId)
    ruleRuns.value = Array.isArray(list) ? [...list].sort((a, b) => (b.id || 0) - (a.id || 0)) : []
  } catch {
    ruleRuns.value = []
  }
}

async function refresh() {
  await load()
  emit('changed')
}

function openRemark(mode) {
  remarkMode.value = mode
  remarkText.value = ''
  remarkVisible.value = true
}

async function submitRemark() {
  if (!remarkText.value.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  acting.value = true
  try {
    if (remarkMode.value === 'resolve') {
      await resolveWorkOrder(props.orderId, remarkText.value.trim())
      ElMessage.success('工单已标记为已解决')
    } else {
      await processWorkOrder(props.orderId, remarkText.value.trim())
      ElMessage.success('处理进展已记录')
    }
    remarkVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '操作失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

async function onAccept() {
  acting.value = true
  try {
    await acceptWorkOrder(props.orderId, '坐席受理工单')
    ElMessage.success('工单已受理')
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '受理失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

async function onClose() {
  let remark = ''
  try {
    const result = await ElMessageBox.prompt('可填写关闭说明（选填）', '关闭工单', {
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消',
      inputPlaceholder: '关闭说明',
      inputValidator: (value) => !value || value.length <= 255 || '说明不能超过 255 个字符'
    })
    remark = result.value || ''
  } catch {
    return
  }
  acting.value = true
  try {
    await closeWorkOrder(props.orderId, { remark })
    ElMessage.success('工单已关闭')
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '关闭失败：' + '请检查工单状态'))
  } finally {
    acting.value = false
  }
}

async function onReopen() {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写重开原因', '重开工单', {
      confirmButtonText: '确认重开',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：问题仍然存在',
      inputValidator: (value) => (value && value.trim().length >= 2) || '请填写至少 2 个字符的原因'
    })
    reason = result.value.trim()
  } catch {
    return
  }
  acting.value = true
  try {
    await reopenWorkOrder(props.orderId, reason)
    ElMessage.success('工单已重开')
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '重开失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

async function openAssign() {
  assignVisible.value = true
  assignForm.assigneeId = order.value?.assigneeId || null
  if (agents.value.length) return
  agentsLoading.value = true
  try {
    const list = await getAgentList()
    agents.value = Array.isArray(list) ? list : []
  } catch (error) {
    ElMessage.error(readError(error, '加载坐席列表失败'))
  } finally {
    agentsLoading.value = false
  }
}

async function submitAssign() {
  acting.value = true
  try {
    await assignWorkOrder(props.orderId, { assigneeId: assignForm.assigneeId })
    ElMessage.success('已分配受理坐席')
    assignVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '分配失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

function openPriority() {
  priorityForm.priority = order.value?.priority || 'P2'
  priorityVisible.value = true
}

async function submitPriority() {
  acting.value = true
  try {
    await updateWorkOrderPriority(props.orderId, priorityForm.priority)
    ElMessage.success('优先级已调整，SLA 已重新计算')
    priorityVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '调整失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

function openRating() {
  ratingForm.rating = order.value?.rating || 5
  ratingForm.ratingComment = ''
  ratingVisible.value = true
}

async function submitRating() {
  acting.value = true
  try {
    await rateWorkOrder(props.orderId, {
      rating: ratingForm.rating,
      ratingComment: ratingForm.ratingComment.trim() || null
    })
    ElMessage.success('感谢你的评价')
    ratingVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '评价提交失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

function onResize() {
  isMobile.value = window.innerWidth <= 768
}

function openPause() {
  pauseForm.pauseType = 'WAITING_CUSTOMER'
  pauseForm.reason = ''
  pauseVisible.value = true
}

async function submitPause() {
  acting.value = true
  try {
    await pauseWorkOrder(props.orderId, {
      pauseType: pauseForm.pauseType,
      reason: pauseForm.reason.trim() || null
    })
    ElMessage.success('工单已挂起，SLA 计时暂停')
    pauseVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '挂起失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

async function onResume() {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('可填写恢复说明（选填）', '恢复计时', {
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：客户已反馈，继续处理',
      inputValidator: (value) => !value || value.length <= 255 || '说明不能超过 255 个字符'
    })
    reason = result.value || ''
  } catch {
    return
  }
  acting.value = true
  try {
    await resumeWorkOrder(props.orderId, { reason: reason.trim() || null })
    ElMessage.success('工单已恢复计时')
    await refresh()
  } catch (error) {
    ElMessage.error(readError(error, '恢复失败，请稍后重试'))
  } finally {
    acting.value = false
  }
}

watch(
  () => [props.modelValue, props.orderId],
  ([open]) => {
    if (open) load()
  }
)

onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => {
  window.clearTimeout(statusFlashTimer)
  window.clearTimeout(priorityFlashTimer)
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.drawer-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.drawer-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--zk-border);
}

.drawer-head-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.drawer-close {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border: 1px solid var(--zk-border);
  background: #fff;
  border-radius: var(--zk-radius-sm);
  font-size: 18px;
  line-height: 1;
  color: var(--zk-text-3);
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.drawer-close:hover {
  background: var(--zk-surface-2);
}

.drawer-title {
  margin: 12px 0 6px;
  font-size: 18px;
  font-weight: 650;
  color: var(--zk-text);
  line-height: 1.45;
}

.drawer-no {
  margin: 0;
  font-size: 11.5px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: var(--zk-text-3);
}

.drawer-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
  padding: 7px 10px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--zk-border);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.meta-item span {
  font-size: 11px;
  color: var(--zk-text-3);
}

.meta-item b {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
  word-break: break-word;
}

.custom-field-grid {
  padding: 0;
  border-bottom: none;
}

.text-danger {
  color: #dc2626 !important;
}

.text-warn {
  color: #d97706 !important;
}

.sla-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.sla-policy {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.sla-pause-tag {
  font-size: 11.5px;
  color: var(--zk-text-3);
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
  padding: 2px 8px;
}

.sla-levels {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sla-level {
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
  padding: 9px 11px;
}

.sla-level-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sla-level-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.sla-level-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 5px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.sla-pauses {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--zk-border);
}

.sla-pauses-title {
  margin: 0 0 6px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--zk-text-2);
}

.sla-pause-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.sla-pause-type {
  font-weight: 600;
  color: var(--zk-text-2);
}

.sla-pause-dur {
  color: var(--zk-text-2);
}

.sla-pause-reason {
  width: 100%;
  word-break: break-word;
}

.drawer-section {
  padding: 16px 0;
  border-bottom: 1px solid var(--zk-border);
}

.drawer-section:last-of-type {
  border-bottom: none;
}

.drawer-section h4 {
  margin: 0 0 10px;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.content-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--zk-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}

.rating-result {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.order-timeline {
  padding-left: 2px;
}

.rule-runs {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rule-run {
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
  padding: 9px 11px;
}

.rule-run-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.rule-run-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.rule-run-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 5px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.rule-run-result {
  margin: 6px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-2);
  word-break: break-word;
}

.log-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-line b {
  font-size: 12.5px;
  color: var(--zk-text);
}

.log-status {
  font-size: 11px;
  color: var(--zk-text-3);
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
  padding: 1px 8px;
}

.log-remark {
  margin: 4px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
  word-break: break-word;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.footer-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.full-width {
  width: 100%;
}

.dialog-tip {
  margin: 10px 0 0;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.priority-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.priority-group :deep(.el-radio) {
  margin-right: 0;
  width: 100%;
}

.rating-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rating-form p {
  margin: 0;
  font-size: 13px;
  color: var(--zk-text-2);
}

@media (max-width: 768px) {
  .meta-grid {
    grid-template-columns: 1fr;
  }

  .drawer-footer {
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .footer-actions {
    justify-content: stretch;
  }

  .footer-actions .el-button {
    flex: 1;
  }
}
</style>
