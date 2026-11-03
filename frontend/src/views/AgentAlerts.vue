<template>
  <div class="zk-page">
    <PageHeader
      title="我的告警"
      description="本人负责的会话命中告警规则后，事件会推送到这里：确认表示已介入，关闭表示处理完成。个人规则只作用于本人接待的会话，租户级规则由管理员统一维护。"
    >
      <template #actions>
        <el-radio-group v-model="view" class="view-switch">
          <el-radio-button label="alerts">告警事件</el-radio-button>
          <el-radio-button label="rules">告警规则</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" :loading="loading || summaryLoading" @click="reloadAll">刷新</el-button>
        <el-button v-if="view === 'rules'" type="primary" :icon="Plus" @click="openDialog(null)">新增规则</el-button>
      </template>
    </PageHeader>

    <section class="metric-grid" aria-label="我的告警概览">
      <article class="metric-card red">
        <div class="metric-top">
          <span class="metric-label">待处理</span>
          <span class="metric-icon"><el-icon><Bell /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary.openCount }}<small>条</small></div>
        <div class="metric-foot">尚未确认介入</div>
      </article>
      <article class="metric-card amber">
        <div class="metric-top">
          <span class="metric-label">已确认</span>
          <span class="metric-icon"><el-icon><View /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary.ackedCount }}<small>条</small></div>
        <div class="metric-foot">处理中，等待闭环</div>
      </article>
      <article class="metric-card indigo">
        <div class="metric-top">
          <span class="metric-label">近 24 小时新增</span>
          <span class="metric-icon"><el-icon><AlarmClock /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary.last24hCount }}<small>条</small></div>
        <div class="metric-foot">含已关闭的历史事件</div>
      </article>
      <article class="metric-card teal">
        <div class="metric-top">
          <span class="metric-label">平均处理耗时</span>
          <span class="metric-icon"><el-icon><Timer /></el-icon></span>
        </div>
        <div class="metric-value">{{ formatWait(summary.avgHandleSeconds) }}<small /></div>
        <div class="metric-foot">近 24 小时已关闭事件</div>
      </article>
    </section>

    <!-- 告警事件 -->
    <div v-show="view === 'alerts'" class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索告警标题 / 规则"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.status" class="filter-select" placeholder="全部状态" clearable @change="search">
          <el-option v-for="item in ALERT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.severity" class="filter-select" placeholder="全部程度" clearable @change="search">
          <el-option v-for="item in ALERT_SEVERITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :icon="Search" @click="search">查询</el-button>
        <div class="grow" />
        <span class="toolbar-meta">共 <b class="zk-num">{{ total }}</b> 条</span>
      </div>

      <div class="table-scroll" role="region" aria-label="我的告警列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="6" class="table-skeleton" />
        <el-table v-else v-loading="loading" class="zk-table-fade-in" :data="rows" row-key="id" aria-label="我的告警列表">
          <el-table-column label="告警" min-width="250">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">
                  {{ row.title }}
                  <el-tag v-if="Number(row.occurCount) > 1" size="small" type="warning" effect="plain">
                    ×{{ row.occurCount }}
                  </el-tag>
                </span>
                <span class="cell-code">{{ row.ruleName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="程度" width="92">
            <template #default="{ row }">
              <StatusPill
                :label="row.severityName || dictLabel(ALERT_SEVERITY_DICT, row.severity)"
                :tone="dictTone(ALERT_SEVERITY_DICT, row.severity)"
                :solid="row.severity === 'CRITICAL'"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="触发详情" min-width="280">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ row.detail || '—' }}</span>
                <span class="cell-hint">指标值 {{ formatAlertMetric(row.metricValue, unitOf(row)) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近触发" width="152">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ formatDateTime(row.lastTime) }}</span>
                <span class="cell-hint">首次 {{ formatDateTime(row.firstTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <StatusPill
                :label="row.statusName || dictLabel(ALERT_STATUS_DICT, row.status)"
                :tone="dictTone(ALERT_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  v-if="row.status === 'OPEN'"
                  link
                  type="primary"
                  class="op-btn"
                  :loading="actionBusy === `ack-${row.id}`"
                  @click="ackNow(row)"
                >
                  确认
                </el-button>
                <el-button
                  v-if="row.status !== 'CLOSED'"
                  link
                  type="success"
                  class="op-btn"
                  :loading="actionBusy === `close-${row.id}`"
                  @click="closeNow(row)"
                >
                  关闭
                </el-button>
                <span v-if="row.status === 'CLOSED'" class="cell-hint">已闭环</span>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '告警加载失败' : '当前没有告警'"
              :hint="error ? '请稍后重新加载，或确认后端服务是否正常' : '本人接待的会话均未命中告警规则'"
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>

      <div v-if="total > 0" class="zk-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          aria-label="我的告警分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <!-- 告警规则 -->
    <div v-show="view === 'rules'" class="zk-card">
      <div class="zk-toolbar">
        <el-input v-model="ruleKeyword" class="el-input--wide" placeholder="搜索规则名称" :prefix-icon="Search" clearable />
        <div class="grow" />
        <span class="toolbar-meta">
          共 <b class="zk-num">{{ rules.length }}</b> 条 · 个人规则 <b class="zk-num">{{ personalCount }}</b> 条
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="我的告警规则" tabindex="0">
        <ZkSkeleton v-if="!rulesLoaded" variant="table" :rows="6" :cols="6" class="table-skeleton" />
        <el-table v-else v-loading="rulesLoading" class="zk-table-fade-in" :data="visibleRules" row-key="id" aria-label="我的告警规则">
          <el-table-column label="规则" min-width="230">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">
                  {{ row.ruleName }}
                  <el-tag v-if="row.tenantLevel" size="small" type="info" effect="plain">租户级</el-tag>
                </span>
                <span class="cell-code">{{ row.metricName || row.metricCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="严重程度" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="row.severityName || dictLabel(ALERT_SEVERITY_DICT, row.severity)"
                :tone="dictTone(ALERT_SEVERITY_DICT, row.severity)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="触发条件" min-width="230">
            <template #default="{ row }">
              <span class="cell-muted">{{ caliberText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="冷却" width="90">
            <template #default="{ row }">
              <span class="zk-num">{{ Number(row.cooldownMinutes) > 0 ? `${row.cooldownMinutes} 分钟` : '不冷却' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="82">
            <template #default="{ row }">
              <StatusPill :label="Number(row.status) === 1 ? '启用' : '停用'" :tone="Number(row.status) === 1 ? 'teal' : 'red'" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div v-if="row.tenantLevel" class="cell-hint">租户级规则由管理员维护</div>
              <div v-else class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDialog(row)">编辑</el-button>
                <el-button
                  link
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  class="op-btn"
                  :loading="actionBusy === `rule-status-${row.id}`"
                  @click="toggleRuleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === `rule-del-${row.id}`"
                  @click="confirmDeleteRule(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="rulesError ? '告警规则加载失败' : '暂无告警规则'"
              :hint="rulesError ? '请稍后重新加载' : '点击右上角「新增规则」创建个人告警规则'"
            >
              <el-button v-if="rulesError" @click="loadRules">重新加载</el-button>
              <el-button v-else type="primary" @click="openDialog(null)">新增规则</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      个人规则默认只通知本人，可在阈值上比租户级规则更敏感（如「访客 60 秒未获回复」提醒自己）· 删除个人规则后不再产生新告警，已产生的事件保留规则快照
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑个人告警规则' : '新增个人告警规则'"
      width="620px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <div class="form-row">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" maxlength="64" show-word-limit placeholder="如：我的会话 60 秒未回复" />
          </el-form-item>
          <el-form-item label="严重程度">
            <el-select v-model="form.severity" class="full-select">
              <el-option v-for="item in ALERT_SEVERITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="判定指标" prop="metricCode">
          <el-select v-model="form.metricCode" class="full-select" :disabled="form.builtin" placeholder="选择要监控的指标">
            <el-option-group v-for="group in metricGroups" :key="group.label" :label="group.label">
              <el-option v-for="item in group.items" :key="item.value" :label="item.label" :value="item.value" />
            </el-option-group>
          </el-select>
          <p v-if="metricHint" class="form-hint">{{ metricHint }}</p>
        </el-form-item>

        <div class="form-row">
          <el-form-item :label="thresholdLabel" prop="thresholdValue">
            <el-input-number
              v-model="form.thresholdValue"
              :min="1"
              :max="form.metricCode === 'LOW_RATING' ? 5 : 604800"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="冷却时间（分钟内不重复推送）">
            <el-input-number v-model="form.cooldownMinutes" :min="0" :max="1440" controls-position="right" />
          </el-form-item>
        </div>

        <el-form-item v-if="form.metricCode === 'SENSITIVE_WORD'" label="敏感词（英文逗号分隔）" prop="keywords">
          <el-input
            v-model="form.keywords"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            resize="none"
            placeholder="如：投诉,曝光,退款不到账"
          />
        </el-form-item>

        <el-form-item label="规则说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="说明触发后希望自己做什么，便于后续复盘"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlarmClock, Bell, Plus, Refresh, Search, Timer, View } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  ackMyAlert,
  closeMyAlert,
  createMyAlertRule,
  deleteMyAlertRule,
  getMyAlertRules,
  getMyAlertSummary,
  getMyAlerts,
  updateMyAlertRule,
  updateMyAlertRuleStatus
} from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  ALERT_METRIC_DICT,
  ALERT_METRIC_OPTIONS,
  ALERT_RULE_TYPE_OPTIONS,
  ALERT_SEVERITY_DICT,
  ALERT_SEVERITY_OPTIONS,
  ALERT_STATUS_DICT,
  ALERT_STATUS_OPTIONS,
  ALERT_UNIT_LABEL,
  alertThresholdLabel,
  dictLabel,
  dictTone,
  formatAlertMetric,
  formatDateTime,
  formatMinutes,
  formatWait
} from '@/utils/dict'

const view = ref('alerts')

const {
  filters,
  rows,
  total,
  pageNum,
  pageSize,
  loading,
  loaded,
  error,
  load,
  search,
  changePage,
  changeSize
} = usePagedList(getMyAlerts, { status: 'OPEN', severity: '', keyword: '' })

/* ---------- 概览 ---------- */
const summary = reactive({
  openCount: 0,
  ackedCount: 0,
  last24hCount: 0,
  criticalCount: 0,
  avgHandleSeconds: 0,
  enabledRuleCount: 0
})
const summaryLoading = ref(false)

async function loadSummary() {
  summaryLoading.value = true
  try {
    const data = await getMyAlertSummary()
    Object.assign(summary, {
      openCount: Number(data?.openCount ?? 0),
      ackedCount: Number(data?.ackedCount ?? 0),
      last24hCount: Number(data?.last24hCount ?? 0),
      criticalCount: Number(data?.criticalCount ?? 0),
      avgHandleSeconds: Number(data?.avgHandleSeconds ?? 0),
      enabledRuleCount: Number(data?.enabledRuleCount ?? 0)
    })
  } catch {
    // 概览失败不阻塞列表
  } finally {
    summaryLoading.value = false
  }
}

/* ---------- 告警处理 ---------- */
const actionBusy = ref('')
const unitOf = (row) => ALERT_METRIC_DICT[row.metricCode]?.unit || 'COUNT'

async function ackNow(row) {
  actionBusy.value = `ack-${row.id}`
  try {
    await ackMyAlert(row.id, null)
    ElMessage.success('告警已确认')
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '确认失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function closeNow(row) {
  try {
    await ElMessageBox.confirm(`关闭告警「${row.title}」后不再计入待处理，确认已处理完成？`, '关闭告警', {
      type: 'warning',
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  actionBusy.value = `close-${row.id}`
  try {
    await closeMyAlert(row.id)
    ElMessage.success('告警已关闭')
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '关闭失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

/* ---------- 规则 ---------- */
const rules = ref([])
const rulesLoading = ref(false)
const rulesLoaded = ref(false)
const rulesError = ref('')
const ruleKeyword = ref('')

const personalCount = computed(() => rules.value.filter((item) => !item.tenantLevel).length)
const visibleRules = computed(() => {
  const text = ruleKeyword.value.trim().toLowerCase()
  if (!text) return rules.value
  return rules.value.filter((item) => String(item.ruleName || '').toLowerCase().includes(text))
})

async function loadRules() {
  rulesLoading.value = true
  rulesError.value = ''
  try {
    const list = await getMyAlertRules()
    rules.value = Array.isArray(list) ? list : []
  } catch (e) {
    rulesError.value = e?.response?.data?.message || '告警规则加载失败'
    rules.value = []
  } finally {
    rulesLoaded.value = true
    rulesLoading.value = false
  }
}

function caliberText(row) {
  const metric = ALERT_METRIC_DICT[row.metricCode]
  const unit = row.thresholdUnit || metric?.unit || 'COUNT'
  const threshold = Number(row.thresholdValue) || 0
  if (row.metricCode === 'LOW_RATING') return `评分不高于 ${threshold} 星即触发`
  if (row.metricCode === 'SENSITIVE_WORD') {
    const words = String(row.keywords || '').split(',').filter(Boolean)
    return `命中关键词：${words.slice(0, 3).join(' / ')}${words.length > 3 ? ` 等 ${words.length} 个词` : ''}`
  }
  if (unit === 'SECONDS') return `等待超过 ${formatWait(threshold)} 即触发`
  if (unit === 'MINUTES') return `持续超过 ${formatMinutes(threshold)} 即触发`
  return `数量达到 ${threshold} 次即触发`
}

/* ---------- 规则表单 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  ruleName: '',
  metricCode: 'AGENT_RESPONSE_TIMEOUT',
  severity: 'WARN',
  thresholdValue: 60,
  cooldownMinutes: 5,
  keywords: '',
  description: '',
  status: 1,
  builtin: false
})

const form = reactive(emptyForm())

const metricGroups = computed(() =>
  ALERT_RULE_TYPE_OPTIONS.map((type) => ({
    label: type.label,
    items: ALERT_METRIC_OPTIONS.filter((item) => item.ruleType === type.value)
  })).filter((group) => group.items.length)
)

const metricHint = computed(() => ALERT_METRIC_DICT[form.metricCode]?.hint || '')

const thresholdLabel = computed(() => {
  const metric = ALERT_METRIC_DICT[form.metricCode]
  const base = alertThresholdLabel(form.metricCode, metric?.unit)
  const unit = ALERT_UNIT_LABEL[metric?.unit || 'COUNT']
  return `${base}${unit ? ` · ${unit}` : ''}`
})

const formRules = {
  ruleName: [
    { required: true, message: '请填写规则名称', trigger: 'blur' },
    { min: 2, max: 64, message: '规则名称长度须为 2-64 个字符', trigger: 'blur' }
  ],
  metricCode: [{ required: true, message: '请选择判定指标', trigger: 'change' }],
  thresholdValue: [
    {
      validator: (rule, value, callback) => {
        const num = Number(value)
        if (form.metricCode === 'LOW_RATING' && (num < 1 || num > 5)) {
          callback(new Error('「客户差评」阈值须为 1-5 星'))
          return
        }
        if (!(num > 0)) {
          callback(new Error('触发阈值必须大于 0'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  keywords: [
    {
      validator: (rule, value, callback) => {
        if (form.metricCode === 'SENSITIVE_WORD' && !String(value || '').trim()) {
          callback(new Error('「会话命中敏感词」规则必须配置关键词'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openDialog(row) {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      id: row.id,
      ruleName: row.ruleName,
      metricCode: row.metricCode || 'AGENT_RESPONSE_TIMEOUT',
      severity: row.severity || 'WARN',
      thresholdValue: Number(row.thresholdValue) || 1,
      cooldownMinutes: row.cooldownMinutes ?? 5,
      keywords: row.keywords || '',
      description: row.description || '',
      status: Number(row.status) === 0 ? 0 : 1,
      builtin: Boolean(row.builtin)
    })
  }
  dialogVisible.value = true
}

async function submit() {
  formRef.value?.clearValidate()
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const metric = ALERT_METRIC_DICT[form.metricCode]
  saving.value = true
  try {
    const payload = {
      ruleName: form.ruleName.trim(),
      metricCode: form.metricCode,
      ruleType: metric?.ruleType || 'AGENT',
      thresholdUnit: metric?.unit || 'COUNT',
      severity: form.severity,
      thresholdValue: form.thresholdValue,
      cooldownMinutes: form.cooldownMinutes ?? 5,
      // 个人规则只通知本人，通知对象固定为责任坐席
      notifyRole: 'AGENT',
      keywords: form.metricCode === 'SENSITIVE_WORD' ? form.keywords.trim() || null : null,
      description: form.description.trim() || null,
      status: form.status
    }
    if (form.id) {
      await updateMyAlertRule(form.id, payload)
    } else {
      await createMyAlertRule(payload)
    }
    ElMessage.success(form.id ? '规则已更新' : '规则已创建，仅对本人接待的会话生效')
    dialogVisible.value = false
    await loadRules()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function toggleRuleStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `rule-status-${row.id}`
  try {
    await updateMyAlertRuleStatus(row.id, target)
    ElMessage.success(target === 1 ? '规则已启用' : '规则已停用')
    await loadRules()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDeleteRule(row) {
  try {
    await ElMessageBox.confirm(`删除个人规则「${row.ruleName}」后不再产生新告警。确认删除？`, '删除规则', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  actionBusy.value = `rule-del-${row.id}`
  try {
    await deleteMyAlertRule(row.id)
    ElMessage.success('规则已删除')
    await loadRules()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

/* ---------- 交互 ---------- */
function reloadAll() {
  loadSummary()
  load()
  if (view.value === 'rules') loadRules()
}

watch(view, (value) => {
  if (value === 'rules' && !rulesLoaded.value) loadRules()
  if (value === 'alerts') load()
})

onMounted(() => {
  loadSummary()
  load()
})
</script>

<style scoped>
.view-switch {
  margin-right: 4px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 13px 14px 12px;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out);
}

.metric-card:hover {
  border-color: var(--zk-border-strong);
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-label {
  font-size: 12px;
  color: var(--zk-text-3);
}

.metric-icon {
  width: 22px;
  height: 22px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
}

.metric-value {
  display: flex;
  align-items: baseline;
  min-height: 30px;
  margin-top: 9px;
  font-size: 25px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.metric-value small {
  margin-left: 4px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-3);
}

.metric-foot {
  margin-top: 3px;
  font-size: 11px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card.red .metric-icon {
  color: var(--zk-red);
  background: var(--zk-red-soft);
}

.metric-card.amber .metric-icon {
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
}

.metric-card.indigo .metric-icon {
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}

.metric-card.teal .metric-icon {
  color: #15803d;
  background: #f0fdf4;
}

.filter-select {
  width: 126px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 1080px;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.cell-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.cell-muted {
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.cell-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
  line-height: 1.5;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-select {
  width: 100%;
}

.form-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }
}
</style>
