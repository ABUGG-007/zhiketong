<template>
  <div class="zk-page">
    <PageHeader
      title="告警中心"
      description="扫描任务每分钟按启用规则评估一次，命中后聚合成告警事件：同一目标在冷却期内只累加触发次数，冷却期外再次命中会重新推送，超过 24 小时未关闭自动归档。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading || summaryLoading" @click="reloadAll">刷新</el-button>
        <el-button :icon="Bell" :loading="scanning" @click="runScan">立即扫描</el-button>
      </template>
    </PageHeader>

    <section class="metric-grid" aria-label="告警概览">
      <article class="metric-card red">
        <div class="metric-top">
          <span class="metric-label">待处理</span>
          <span class="metric-icon"><el-icon><Bell /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary.openCount }}<small>条</small></div>
        <div class="metric-foot">尚未有人确认介入</div>
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
      <article class="metric-card red">
        <div class="metric-top">
          <span class="metric-label">近 24 小时严重</span>
          <span class="metric-icon"><el-icon><Warning /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary.criticalCount }}<small>条</small></div>
        <div class="metric-foot">需要立即介入的事件</div>
      </article>
      <article class="metric-card teal">
        <div class="metric-top">
          <span class="metric-label">平均处理耗时</span>
          <span class="metric-icon"><el-icon><Timer /></el-icon></span>
        </div>
        <div class="metric-value">{{ formatWait(summary.avgHandleSeconds) }}<small /></div>
        <div class="metric-foot">近 24 小时已关闭事件</div>
      </article>
      <article class="metric-card green">
        <div class="metric-top">
          <span class="metric-label">启用规则</span>
          <span class="metric-icon"><el-icon><SetUp /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary.enabledRuleCount }}<small>条</small></div>
        <div class="metric-foot">租户级规则，可在告警规则中调整</div>
      </article>
    </section>

    <div class="zk-card">
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
        <el-select v-model="filters.ruleType" class="filter-select" placeholder="全部类型" clearable @change="search">
          <el-option v-for="item in ALERT_RULE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :icon="Search" @click="search">查询</el-button>
        <div class="grow" />
        <span class="toolbar-meta">
          共 <b class="zk-num">{{ total }}</b> 条告警
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="告警列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="rows"
          row-key="id"
          aria-label="告警列表"
        >
          <el-table-column label="告警" min-width="250">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">
                  {{ row.title }}
                  <el-tag v-if="Number(row.occurCount) > 1" size="small" type="warning" effect="plain">
                    ×{{ row.occurCount }}
                  </el-tag>
                </span>
                <span class="cell-code">{{ row.ruleName }} · {{ row.ruleCode }}</span>
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
          <el-table-column label="目标" width="128">
            <template #default="{ row }">
              <div class="cell-stack">
                <StatusPill
                  :label="row.targetTypeName || dictLabel(ALERT_TARGET_TYPE_DICT, row.targetType)"
                  :tone="dictTone(ALERT_TARGET_TYPE_DICT, row.targetType)"
                />
                <span class="cell-hint">{{ targetText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="触发详情" min-width="260">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ row.detail || '—' }}</span>
                <span class="cell-hint">指标值 {{ formatAlertMetric(row.metricValue, unitOf(row)) }} · 阈值 {{ thresholdText(row) }}</span>
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
          <el-table-column label="操作" width="176" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  v-if="row.status === 'OPEN'"
                  link
                  type="primary"
                  class="op-btn"
                  :loading="actionBusy === `ack-${row.id}`"
                  @click="openAck(row)"
                >
                  确认
                </el-button>
                <el-button
                  v-if="row.status !== 'CLOSED'"
                  link
                  type="success"
                  class="op-btn"
                  :loading="actionBusy === `close-${row.id}`"
                  @click="confirmClose(row)"
                >
                  关闭
                </el-button>
                <el-button
                  v-if="row.sessionId"
                  link
                  type="warning"
                  class="op-btn"
                  @click="openSession(row.sessionId)"
                >
                  查看会话
                </el-button>
                <span v-if="row.status === 'CLOSED'" class="cell-hint">
                  {{ row.handleSeconds != null ? `处理 ${formatWait(row.handleSeconds)}` : '已闭环' }}
                </span>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '告警加载失败' : '当前没有告警'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : '没有命中规则的告警事件；可点击「立即扫描」按当前数据评估一轮'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else :loading="scanning" @click="runScan">立即扫描</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>

      <div v-if="total > 0" class="zk-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          aria-label="告警分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      「确认」表示值班人已介入，事件仍保留在列表中便于跟踪 · 「关闭」表示处理完成并记录处理耗时 · 超过 24 小时无人处理的告警由扫描任务自动关闭，避免列表长期堆积
    </div>

    <el-dialog v-model="ackVisible" title="确认告警" width="480px" append-to-body>
      <p class="ack-summary">{{ ackTarget?.title }}</p>
      <p class="ack-detail">{{ ackTarget?.detail || '—' }}</p>
      <el-form label-position="top">
        <el-form-item label="处理说明（选填）">
          <el-input
            v-model="ackNote"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="如：已联系值班坐席处理 / 已知晓，等待客户回复"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ackVisible = false">取消</el-button>
        <el-button type="primary" :loading="ackSaving" @click="submitAck">确认已介入</el-button>
      </template>
    </el-dialog>

    <SessionDrawer v-model="drawerVisible" :session-id="drawerSessionId" role="ADMIN" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlarmClock, Bell, Refresh, Search, SetUp, Timer, View, Warning } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SessionDrawer from '@/components/SessionDrawer.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import { ackAlert, closeAlert, getAlertSummary, getAlerts, scanAlerts } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  ALERT_METRIC_DICT,
  ALERT_RULE_TYPE_OPTIONS,
  ALERT_SEVERITY_DICT,
  ALERT_SEVERITY_OPTIONS,
  ALERT_STATUS_DICT,
  ALERT_STATUS_OPTIONS,
  ALERT_TARGET_TYPE_DICT,
  dictLabel,
  dictTone,
  formatAlertMetric,
  formatDateTime,
  formatWait
} from '@/utils/dict'

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
} = usePagedList(getAlerts, { status: 'OPEN', severity: '', ruleType: '', keyword: '' })

/* ---------- 概览 ---------- */
const summary = reactive({
  openCount: 0,
  ackedCount: 0,
  last24hCount: 0,
  criticalCount: 0,
  avgHandleSeconds: 0,
  enabledRuleCount: 0,
  latestTitle: '',
  latestTime: null
})
const summaryLoading = ref(false)

async function loadSummary() {
  summaryLoading.value = true
  try {
    const data = await getAlertSummary()
    Object.assign(summary, {
      openCount: Number(data?.openCount ?? 0),
      ackedCount: Number(data?.ackedCount ?? 0),
      last24hCount: Number(data?.last24hCount ?? 0),
      criticalCount: Number(data?.criticalCount ?? 0),
      avgHandleSeconds: Number(data?.avgHandleSeconds ?? 0),
      enabledRuleCount: Number(data?.enabledRuleCount ?? 0),
      latestTitle: data?.latestTitle || '',
      latestTime: data?.latestTime || null
    })
  } catch {
    // 概览失败不阻塞列表，指标保留上一次结果
  } finally {
    summaryLoading.value = false
  }
}

const scanning = ref(false)

async function runScan() {
  scanning.value = true
  try {
    const data = await scanAlerts()
    if (data?.locked === false) {
      ElMessage.warning(data?.message || '另一轮扫描正在进行，请稍后再试')
    } else {
      ElMessage.success(
        `扫描完成：新增 ${data?.createdCount ?? 0} 条 · 复发 ${data?.repeatedCount ?? 0} 条 · 抑制 ${data?.suppressedCount ?? 0} 条 · 自动关闭 ${data?.autoClosedCount ?? 0} 条`
      )
    }
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '扫描失败，请稍后重试')
  } finally {
    scanning.value = false
  }
}

function reloadAll() {
  loadSummary()
  load()
}

/* ---------- 列表辅助展示 ---------- */
const unitOf = (row) => ALERT_METRIC_DICT[row.metricCode]?.unit || 'COUNT'

function targetText(row) {
  if (row.targetType === 'SESSION') return row.sessionId ? `会话 #${row.sessionId}` : '会话'
  if (row.targetType === 'AGENT') return row.agentId ? `坐席 #${row.agentId}` : '坐席'
  return row.targetType === 'ORDER' ? '本租户工单' : '本租户排队'
}

function thresholdText(row) {
  const unit = unitOf(row)
  const value = Number(row.thresholdValue) || 0
  if (unit === 'SECONDS') return formatWait(value)
  if (unit === 'MINUTES') return `${value} 分钟`
  if (unit === 'SCORE') return `${value} 星`
  return `${value} 次`
}

/* ---------- 确认 / 关闭 ---------- */
const actionBusy = ref('')
const ackVisible = ref(false)
const ackSaving = ref(false)
const ackTarget = ref(null)
const ackNote = ref('')

function openAck(row) {
  ackTarget.value = row
  ackNote.value = ''
  ackVisible.value = true
}

async function submitAck() {
  if (!ackTarget.value) return
  ackSaving.value = true
  try {
    await ackAlert(ackTarget.value.id, ackNote.value.trim() || null)
    ElMessage.success('告警已确认，事件保留在列表中等待闭环')
    ackVisible.value = false
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '确认失败，请稍后重试')
  } finally {
    ackSaving.value = false
  }
}

async function confirmClose(row) {
  try {
    await ElMessageBox.confirm(
      `关闭告警「${row.title}」后不再计入待处理，处理耗时会写入事件记录。确认已处理完成？`,
      '关闭告警',
      { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `close-${row.id}`
  try {
    await closeAlert(row.id)
    ElMessage.success('告警已关闭')
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '关闭失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

/* ---------- 会话抽屉 ---------- */
const drawerVisible = ref(false)
const drawerSessionId = ref(null)

function openSession(sessionId) {
  drawerSessionId.value = sessionId
  drawerVisible.value = true
}

onMounted(reloadAll)
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.metric-card.green .metric-icon {
  color: var(--zk-green);
  background: var(--zk-green-soft);
}

.filter-select {
  width: 126px;
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

.ack-summary {
  margin: 0 0 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.ack-detail {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--zk-text-2);
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

  .filter-select {
    width: 100%;
  }
}
</style>
