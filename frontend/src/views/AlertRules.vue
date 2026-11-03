<template>
  <div class="zk-page">
    <PageHeader
      title="告警规则"
      description="把运营阈值变成可配置规则：每条规则绑定一个判定指标，扫描任务每分钟评估一次，命中后按冷却时间聚合成告警事件并推送给指定对象。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog(null)">新增规则</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-model="keyword"
          class="el-input--wide"
          placeholder="搜索规则名称 / 指标"
          :prefix-icon="Search"
          clearable
        />
        <el-select v-model="filterType" class="filter-select" placeholder="全部类型" clearable @change="load">
          <el-option v-for="item in ALERT_RULE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filterStatus" class="filter-select" placeholder="全部状态" clearable @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta">
          共 <b class="zk-num">{{ rules.length }}</b> 条 · 启用 <b class="zk-num">{{ enabledCount }}</b> 条
        </span>
      </div>

      <div
        class="table-scroll"
        :class="{ 'table-result-fade': resultFade }"
        role="region"
        aria-label="告警规则列表"
        tabindex="0"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="7" :cols="8" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="visibleRules"
          row-key="id"
          aria-label="告警规则列表"
          :row-class-name="rowClassName"
        >
          <el-table-column label="规则" min-width="230">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">
                  {{ row.ruleName }}
                  <el-tag v-if="row.builtin" size="small" type="info" effect="plain">内置</el-tag>
                  <el-tag v-if="row.tenantLevel" size="small" type="success" effect="plain">租户级</el-tag>
                </span>
                <span class="cell-code">{{ row.ruleCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="判定指标" min-width="150">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">{{ row.metricName || row.metricCode }}</span>
                <StatusPill
                  :label="row.ruleTypeName || dictLabel(ALERT_RULE_TYPE_DICT, row.ruleType)"
                  :tone="dictTone(ALERT_RULE_TYPE_DICT, row.ruleType)"
                />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="严重程度" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="row.severityName || dictLabel(ALERT_SEVERITY_DICT, row.severity)"
                :tone="dictTone(ALERT_SEVERITY_DICT, row.severity)"
                :solid="row.severity === 'CRITICAL'"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="触发条件" min-width="230">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ caliberText(row) }}</span>
                <span v-if="row.description" class="cell-hint">{{ row.description }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="通知对象" width="110">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(ALERT_NOTIFY_ROLE_DICT, row.notifyRole)"
                :tone="dictTone(ALERT_NOTIFY_ROLE_DICT, row.notifyRole)"
              />
            </template>
          </el-table-column>
          <el-table-column label="冷却" width="90">
            <template #default="{ row }">
              <span class="zk-num">{{ Number(row.cooldownMinutes) > 0 ? `${row.cooldownMinutes} 分钟` : '不冷却' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="82">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.status) === 1 ? '启用' : '停用'"
                :tone="Number(row.status) === 1 ? 'teal' : 'red'"
                :class="{ 'zk-flash': changedId === row.id }"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDialog(row)">编辑</el-button>
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
                  :disabled="row.builtin"
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
              :title="error ? '告警规则加载失败' : '暂无告警规则'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : '点击右上角「新增规则」创建，或重新执行建表脚本导入内置规则'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else type="primary" @click="openDialog(null)">新增规则</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      同一条指标可以配置多条规则（如「排队积压」分别给值班组长与客服主管），冷却时间内的重复命中只累加触发次数不再重复推送 · 内置规则的指标不可更换，但阈值、冷却与通知对象可按运营节奏调整
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑告警规则' : '新增告警规则'"
      width="620px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <div class="form-row">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" maxlength="64" show-word-limit placeholder="如：排队超 5 人通知组长" />
          </el-form-item>
          <el-form-item label="严重程度">
            <el-select v-model="form.severity" class="full-select">
              <el-option v-for="item in ALERT_SEVERITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="判定指标" prop="metricCode">
          <el-select
            v-model="form.metricCode"
            class="full-select"
            :disabled="form.builtin"
            placeholder="选择要监控的指标"
          >
            <el-option-group
              v-for="group in metricGroups"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="item in group.items"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-option-group>
          </el-select>
          <p v-if="metricHint" class="form-hint">{{ metricHint }}</p>
        </el-form-item>

        <div class="form-row">
          <el-form-item :label="thresholdLabel" prop="thresholdValue">
            <el-input-number
              v-model="form.thresholdValue"
              :min="form.metricCode === 'LOW_RATING' ? 1 : 1"
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
            placeholder="如：投诉,曝光,诈骗,律师函"
          />
        </el-form-item>

        <el-form-item label="通知对象">
          <el-select v-model="form.notifyRole" class="full-select">
            <el-option v-for="item in ALERT_NOTIFY_ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="规则说明与判定口径">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="说明该规则的适用场景，便于值班人员判断是否需要立即介入"
          />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="排序号（越小越靠前）">
            <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
        <p v-if="form.builtin" class="form-hint">
          内置规则的判定指标由扫描引擎识别，因此不可更换；名称、阈值、冷却与通知对象可按业务调整。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  createAlertRule,
  deleteAlertRule,
  getAlertRules,
  updateAlertRule,
  updateAlertRuleStatus
} from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import {
  ALERT_METRIC_DICT,
  ALERT_METRIC_OPTIONS,
  ALERT_NOTIFY_ROLE_DICT,
  ALERT_NOTIFY_ROLE_OPTIONS,
  ALERT_RULE_TYPE_DICT,
  ALERT_RULE_TYPE_OPTIONS,
  ALERT_SEVERITY_DICT,
  ALERT_SEVERITY_OPTIONS,
  ALERT_UNIT_LABEL,
  alertThresholdLabel,
  dictLabel,
  dictTone,
  formatMinutes,
  formatWait
} from '@/utils/dict'

const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const actionBusy = ref('')
const rules = ref([])
const keyword = ref('')
const filterType = ref('')
const filterStatus = ref('')

/* 首屏骨架屏；筛选结果 160ms 淡入；停用/删除的行先淡出再移除 */
const resultFade = ref(false)
const changedId = ref(null)
const removingId = ref(null)
let resultFadeTimer = 0
let changedTimer = 0
let keywordTimer = 0

const rowClassName = ({ row }) => (row.id === removingId.value ? 'row-removing' : '')

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

function markChanged(id) {
  changedId.value = id
  window.clearTimeout(changedTimer)
  changedTimer = window.setTimeout(() => {
    changedId.value = null
  }, 700)
}

async function removeRowAfterFade(id) {
  removingId.value = id
  await new Promise((resolve) => {
    window.setTimeout(resolve, prefersReducedMotion() ? 0 : 160)
  })
  await load()
  removingId.value = null
}

watch(loading, (now, before) => {
  if (before && !now) triggerResultFade()
})

watch(keyword, () => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(triggerResultFade, 200)
})

onBeforeUnmount(() => {
  window.clearTimeout(resultFadeTimer)
  window.clearTimeout(changedTimer)
  window.clearTimeout(keywordTimer)
})

const enabledCount = computed(() => rules.value.filter((item) => Number(item.status) === 1).length)

const visibleRules = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return rules.value
  return rules.value.filter((item) => {
    const metric = ALERT_METRIC_DICT[item.metricCode]
    return (
      String(item.ruleName || '').toLowerCase().includes(text) ||
      String(item.ruleCode || '').toLowerCase().includes(text) ||
      String(metric?.label || '').toLowerCase().includes(text)
    )
  })
})

/** 触发条件：按指标单位生成可读口径，避免直接暴露原始字段 */
function caliberText(row) {
  const metric = ALERT_METRIC_DICT[row.metricCode]
  const unit = row.thresholdUnit || metric?.unit || 'COUNT'
  const threshold = Number(row.thresholdValue) || 0
  if (row.metricCode === 'LOW_RATING') {
    return `评分不高于 ${threshold} 星即触发`
  }
  if (row.metricCode === 'SENSITIVE_WORD') {
    const words = String(row.keywords || '')
      .split(',')
      .filter(Boolean)
    return `命中关键词：${words.slice(0, 3).join(' / ')}${words.length > 3 ? ` 等 ${words.length} 个词` : ''}`
  }
  if (unit === 'SECONDS') return `等待超过 ${formatWait(threshold)} 即触发`
  if (unit === 'MINUTES') return `持续超过 ${formatMinutes(threshold)} 即触发`
  return `数量达到 ${threshold} 次即触发`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (filterType.value) params.ruleType = filterType.value
    if (filterStatus.value !== '' && filterStatus.value !== null) params.status = filterStatus.value
    const list = await getAlertRules(params)
    rules.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e?.response?.data?.message || '告警规则加载失败'
    rules.value = []
  } finally {
    loaded.value = true
    loading.value = false
  }
}

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  ruleCode: '',
  ruleName: '',
  metricCode: 'QUEUE_BACKLOG',
  severity: 'WARN',
  thresholdValue: 5,
  cooldownMinutes: 10,
  notifyRole: 'ADMIN',
  keywords: '',
  description: '',
  sort: 100,
  status: 1,
  builtin: false
})

const form = reactive(emptyForm())

/** 指标按规则类型分组展示，坐席一眼能找到「排队 / 会话 / 坐席 / 工单」 */
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
        if (form.metricCode !== 'SENSITIVE_WORD') {
          callback()
          return
        }
        if (!String(value || '').trim()) {
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
      ruleCode: row.ruleCode,
      ruleName: row.ruleName,
      metricCode: row.metricCode || 'QUEUE_BACKLOG',
      severity: row.severity || 'WARN',
      thresholdValue: Number(row.thresholdValue) || 1,
      cooldownMinutes: row.cooldownMinutes ?? 10,
      notifyRole: row.notifyRole || 'ADMIN',
      keywords: row.keywords || '',
      description: row.description || '',
      sort: row.sort ?? 100,
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
      // 后端按指标重新推导类型与单位，这里保持与指标一致，便于审计日志可读
      ruleType: metric?.ruleType || 'SESSION',
      thresholdUnit: metric?.unit || 'COUNT',
      severity: form.severity,
      thresholdValue: form.thresholdValue,
      cooldownMinutes: form.cooldownMinutes ?? 10,
      notifyRole: form.notifyRole,
      keywords: form.metricCode === 'SENSITIVE_WORD' ? form.keywords.trim() || null : null,
      description: form.description.trim() || null,
      sort: form.sort ?? 100,
      status: form.status
    }
    if (form.id) {
      await updateAlertRule(form.id, payload)
    } else {
      await createAlertRule(payload)
    }
    ElMessage.success(form.id ? '规则已更新' : '规则已创建，下一轮扫描生效')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `status-${row.id}`
  try {
    await updateAlertRuleStatus(row.id, target)
    ElMessage.success(target === 1 ? '规则已启用' : '规则已停用，后续扫描不再评估该规则')
    await load()
    markChanged(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除规则「${row.ruleName}」后不再产生新告警，已产生的告警事件保留当时的规则快照。`,
      '删除规则',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `del-${row.id}`
  try {
    await deleteAlertRule(row.id)
    ElMessage.success('规则已删除')
    await removeRowAfterFade(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.filter-select {
  width: 132px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 1120px;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

.table-scroll :deep(.el-table__row.row-removing > td.el-table__cell) {
  opacity: 0;
  transition: opacity 160ms var(--zk-ease-in);
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

@media (max-width: 640px) {
  .form-row {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }
}
</style>
