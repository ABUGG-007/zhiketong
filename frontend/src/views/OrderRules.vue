<template>
  <div class="zk-page">
    <PageHeader
      title="工单规则与自动化"
      description="以统一规则引擎驱动工单流转：自动分配按技能组与负载均衡择优指派坐席；事件触发器在工单状态变更与 SLA 事件时执行动作；时间自动化按延迟或周期驱动。每条命中都会写入执行流水，可追溯、可试跑。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog(null)">新增规则</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <el-tabs v-model="activeTab" class="rule-tabs" @tab-change="onTabChange">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.value"
          :label="tab.label"
          :name="tab.value"
        />
      </el-tabs>

      <div class="zk-toolbar">
        <el-select v-model="filterStatus" class="filter-select" placeholder="全部状态" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          当前分类共 <b class="zk-num">{{ shownRules.length }}</b> 条规则 · 启用
          <b class="zk-num">{{ enabledCount }}</b> 条
        </span>
      </div>

      <div
        class="table-scroll"
        :class="{ 'table-result-fade': resultFade }"
        role="region"
        :aria-label="`${currentTab.label}列表`"
        tabindex="0"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="7" :cols="6" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="shownRules"
          row-key="id"
          :aria-label="`${currentTab.label}列表`"
          :row-class-name="rowClassName"
        >
          <el-table-column label="规则" min-width="220">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">{{ row.ruleName }}</span>
                <span class="cell-muted">{{ row.description || '未填写规则说明' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="触发" min-width="150">
            <template #default="{ row }">
              <div class="cell-stack">
                <StatusPill
                  v-if="row.triggerEvent"
                  :label="dictLabel(ORDER_RULE_EVENT_DICT, row.triggerEvent)"
                  :tone="dictTone(ORDER_RULE_EVENT_DICT, row.triggerEvent)"
                />
                <span v-else class="cell-muted">时间驱动（无事件）</span>
                <span v-if="Number(row.delayMinutes) > 0" class="cell-muted">
                  延迟 {{ formatMinutes(row.delayMinutes) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="命中条件" min-width="170">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ conditionText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="动作" min-width="160">
            <template #default="{ row }">
              <div class="cell-stack">
                <StatusPill
                  :label="dictLabel(ORDER_RULE_ACTION_DICT, row.actionType)"
                  :tone="dictTone(ORDER_RULE_ACTION_DICT, row.actionType)"
                />
                <span class="cell-muted">{{ actionValueText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.status) === 1 ? '启用' : '停用'"
                :tone="Number(row.status) === 1 ? 'teal' : 'red'"
                :class="{ 'zk-flash': changedId === row.id }"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDialog(row)">编辑</el-button>
                <el-button link type="primary" class="op-btn" @click="openRuns(row)">执行流水</el-button>
                <el-button
                  link
                  type="success"
                  class="op-btn"
                  :loading="actionBusy === `run-${row.id}`"
                  @click="runOnce(row)"
                >
                  试跑
                </el-button>
                <el-button
                  link
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  class="op-btn"
                  :loading="actionBusy === `status-${row.id}`"
                  @click="toggleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === `del-${row.id}`"
                  @click="confirmDelete(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '规则加载失败' : `暂无${currentTab.label}`"
              :hint="error ? '请稍后重新加载，或确认后端服务是否正常' : currentTab.emptyHint"
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else type="primary" @click="openDialog(null, currentTab.value)">
                新增规则
              </el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      {{ currentTab.tip }}
    </div>

    <OrderRuleDialog
      v-model="dialogVisible"
      :rule="editingRule"
      :preset-type="activeTab"
      @saved="load"
    />

    <!-- 执行流水抽屉（R-143） -->
    <el-drawer v-model="runsVisible" :title="`执行流水 · ${runsRule?.ruleName || ''}`" size="460px" append-to-body>
      <div v-loading="runsLoading" class="runs-body">
        <el-table :data="runs" row-key="id" aria-label="规则执行流水">
          <el-table-column label="工单" min-width="150">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">#{{ row.orderId }}</span>
                <span class="cell-muted">{{ dictLabel(ORDER_RULE_EVENT_DICT, row.event, '时间驱动') }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="104">
            <template #default="{ row }">
              <span class="cell-muted">{{ dictLabel(ORDER_RULE_ACTION_DICT, row.actionType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="104">
            <template #default="{ row }">
              <div class="cell-stack">
                <StatusPill
                  :label="dictLabel(ORDER_RULE_RUN_STATUS_DICT, row.status)"
                  :tone="dictTone(ORDER_RULE_RUN_STATUS_DICT, row.status)"
                />
                <span v-if="row.result" class="run-result-line">{{ row.result }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="150">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">计划 {{ formatDateTime(row.plannedTime) }}</span>
                <span v-if="row.executedTime" class="cell-muted">执行 {{ formatDateTime(row.executedTime) }}</span>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="runsError || '暂无执行记录'"
              hint="规则命中并执行后会在此留下流水"
            />
          </template>
        </el-table>

        <div v-if="runsTotal > runsPageSize" class="runs-pager">
          <el-pagination
            layout="prev, pager, next"
            :total="runsTotal"
            :page-size="runsPageSize"
            :current-page="runsPageNum"
            background
            @current-change="onRunsPage"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import OrderRuleDialog from '@/components/OrderRuleDialog.vue'
import {
  getOrderRules,
  deleteOrderRule,
  updateOrderRuleStatus,
  runOrderRuleOnce,
  getOrderRuleRuns
} from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import {
  ORDER_RULE_TYPE_DICT,
  ORDER_RULE_EVENT_DICT,
  ORDER_RULE_ACTION_DICT,
  ORDER_RULE_RUN_STATUS_DICT,
  ORDER_SOURCE_DICT,
  PRIORITY_DICT,
  dictLabel,
  dictTone,
  formatDateTime,
  formatMinutes
} from '@/utils/dict'

const tabs = [
  {
    label: '自动分配',
    value: 'AUTO_ASSIGN',
    emptyHint: '还没有自动分配规则，点击右上角「新增规则」创建',
    tip: '自动分配在工单创建或待分配事件时触发，按技能组内在岗坐席的未关闭工单数升序、能力值降序择优指派；组内无在岗坐席时降级全体在岗，仍无可用则跳过并不改变工单状态。'
  },
  {
    label: '事件触发器',
    value: 'TRIGGER',
    emptyHint: '还没有事件触发器，点击右上角「新增规则」创建',
    tip: '触发器由工单状态流转与 SLA 事件驱动，延迟为 0 时同事务立即执行动作，延迟大于 0 时进入待执行队列由扫描任务到期执行。'
  },
  {
    label: '时间自动化',
    value: 'AUTOMATION',
    emptyHint: '还没有时间自动化规则，点击右上角「新增规则」创建',
    tip: '时间自动化以延迟或周期方式驱动动作，无需绑定事件；到期由每分钟扫描任务统一执行并留痕。'
  }
]

const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const actionBusy = ref('')
const rules = ref([])
const activeTab = ref('AUTO_ASSIGN')
const filterStatus = ref('')

/* 首屏骨架屏；切换页签/筛选结果 160ms 淡入；停用与删除的行先淡出再移除 */
const resultFade = ref(false)
const changedId = ref(null)
const removingId = ref(null)
let resultFadeTimer = 0
let changedTimer = 0

const rowClassName = ({ row }) => (row.id === removingId.value ? 'row-removing' : '')

function markChanged(id) {
  changedId.value = id
  window.clearTimeout(changedTimer)
  changedTimer = window.setTimeout(() => {
    changedId.value = null
  }, 700)
}

/* 行先 160ms 淡出，数据刷新后再解除标记，避免列表跳位与闪现 */
async function removeRowAfterFade(id) {
  removingId.value = id
  await new Promise((resolve) => {
    window.setTimeout(resolve, prefersReducedMotion() ? 0 : 160)
  })
  await load()
  removingId.value = null
}

function triggerResultFade() {
  if (prefersReducedMotion()) return
  resultFade.value = false
  requestAnimationFrame(() => {
    resultFade.value = true
    window.clearTimeout(resultFadeTimer)
    resultFadeTimer = window.setTimeout(() => {
      resultFade.value = false
    }, 200)
  })
}

watch(loading, (now, before) => {
  if (before && !now) triggerResultFade()
})

// 页签与状态是本地过滤，切换后同样给结果区一次 160ms 淡入
watch([activeTab, filterStatus], triggerResultFade)

onBeforeUnmount(() => {
  window.clearTimeout(resultFadeTimer)
  window.clearTimeout(changedTimer)
})

const dialogVisible = ref(false)
const editingRule = ref(null)

const runsVisible = ref(false)
const runsLoading = ref(false)
const runsError = ref('')
const runsRule = ref(null)
const runs = ref([])
const runsTotal = ref(0)
const runsPageNum = ref(1)
const runsPageSize = 20

const currentTab = computed(() => tabs.find((t) => t.value === activeTab.value) || tabs[0])

const shownRules = computed(() => {
  return rules.value.filter((r) => {
    if (r.ruleType !== activeTab.value) return false
    if (filterStatus.value !== '' && filterStatus.value !== null && Number(r.status) !== Number(filterStatus.value)) {
      return false
    }
    return true
  })
})

const enabledCount = computed(() => shownRules.value.filter((r) => Number(r.status) === 1).length)

function conditionText(row) {
  const parts = []
  if (row.matchPriority) parts.push(dictLabel(PRIORITY_DICT, row.matchPriority))
  if (row.matchCategory) parts.push(row.matchCategory)
  if (row.matchSource) parts.push(dictLabel(ORDER_SOURCE_DICT, row.matchSource))
  return parts.length ? parts.join(' · ') : '全部工单（不限）'
}

function actionValueText(row) {
  switch (row.actionType) {
    case 'AUTO_ASSIGN':
      return row.assignGroupName ? `技能组：${row.assignGroupName}` : '全体在岗坐席'
    case 'CHANGE_PRIORITY':
      return `→ ${dictLabel(PRIORITY_DICT, row.actionValue)}`
    case 'CHANGE_STATUS':
      return `→ ${dictLabel(ORDER_RULE_STATUS_TARGET_DICT, row.actionValue)}`
    case 'NOTIFY_ROLE':
      return `通知：${notifyRoleLabel(row.actionValue)}`
    case 'ASSIGN_AGENT':
      return `坐席 ID：${row.actionValue || '—'}`
    default:
      return row.actionValue || '—'
  }
}

const ORDER_RULE_STATUS_TARGET_DICT = {
  ASSIGNED: { label: '已分配' },
  PROCESSING: { label: '处理中' },
  WAITING_CUSTOMER: { label: '等待客户反馈' },
  RESOLVED: { label: '已解决' },
  CLOSED: { label: '已关闭' }
}

function notifyRoleLabel(v) {
  return { ADMIN: '管理员', CREATOR: '创建人', ASSIGNEE: '受理人' }[v] || v || '—'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await getOrderRules()
    rules.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e?.response?.data?.message || '规则加载失败'
    rules.value = []
  } finally {
    loaded.value = true
    loading.value = false
  }
}

function onTabChange() {
  // 切换页签仅影响本地过滤，无需重新请求
}

function openDialog(row, presetType) {
  editingRule.value = row ? { ...row } : null
  if (!row && presetType) activeTab.value = presetType
  dialogVisible.value = true
}

async function toggleStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `status-${row.id}`
  try {
    await updateOrderRuleStatus(row.id, target)
    ElMessage.success(target === 1 ? '规则已启用' : '规则已停用')
    await load()
    markChanged(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function runOnce(row) {
  try {
    await ElMessageBox.confirm(
      `将「${row.ruleName}」对当前匹配的在跑工单立即执行一次，动作会真实生效并写入执行流水。确认试跑？`,
      '手动试跑',
      { type: 'warning', confirmButtonText: '确认试跑', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `run-${row.id}`
  try {
    const result = await runOrderRuleOnce(row.id)
    ElMessage.success(
      `试跑完成：命中 ${result?.matched ?? 0}，成功 ${result?.done ?? 0}，失败 ${result?.failed ?? 0}，跳过 ${result?.skipped ?? 0}`
    )
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '试跑失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除规则「${row.ruleName}」后不可恢复，已产生的执行流水仍会保留。确认删除？`,
      '删除规则',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `del-${row.id}`
  try {
    await deleteOrderRule(row.id)
    ElMessage.success('规则已删除')
    await removeRowAfterFade(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function openRuns(row) {
  runsRule.value = row
  runsVisible.value = true
  runsPageNum.value = 1
  await loadRuns()
}

async function loadRuns() {
  if (!runsRule.value) return
  runsLoading.value = true
  runsError.value = ''
  try {
    const page = await getOrderRuleRuns(runsRule.value.id, {
      pageNum: runsPageNum.value,
      pageSize: runsPageSize
    })
    runs.value = page?.list || []
    runsTotal.value = page?.total || 0
  } catch (e) {
    runsError.value = e?.response?.data?.message || '执行流水加载失败'
    runs.value = []
    runsTotal.value = 0
  } finally {
    runsLoading.value = false
  }
}

async function onRunsPage(page) {
  runsPageNum.value = page
  await loadRuns()
}

onMounted(load)
</script>

<style scoped>
.rule-tabs {
  padding: 0 4px;
  margin-bottom: 4px;
}

.filter-select {
  width: 150px;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

/* 筛选结果淡入：160ms，只动位移与透明度 */
.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

/* 删除/停用后行先淡出 160ms 再移除，列表不突然跳位 */
.table-scroll :deep(.el-table__row.row-removing > td.el-table__cell) {
  opacity: 0;
  transition: opacity 160ms var(--zk-ease-in);
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.cell-strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.cell-muted {
  font-size: 12px;
  color: var(--zk-text-3);
}

.runs-body {
  padding: 0 4px;
}

.run-result-line {
  font-size: 11.5px;
  color: var(--zk-text-3);
  word-break: break-word;
}

.runs-pager {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}
</style>
