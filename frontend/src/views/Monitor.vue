<template>
  <div class="zk-page">
    <PageHeader
      title="会话监听"
      description="组长实时巡视进行中的会话：看到待回复时长与未闭环告警，可直接强插发言、强制接管或改派坐席。所有介入动作都会在会话中留下系统提示与审计记录。"
    >
      <template #actions>
        <el-switch
          v-model="autoRefresh"
          class="auto-switch"
          active-text="自动刷新"
          @change="toggleAutoRefresh"
        />
        <el-button :icon="Refresh" :loading="loading" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索会话号 / 访客名称"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.status" class="filter-select" placeholder="全部状态" clearable @change="search">
          <el-option label="AI 接待" value="AI" />
          <el-option label="排队中" value="WAITING" />
          <el-option label="人工中" value="HUMAN" />
        </el-select>
        <el-select
          v-model="filters.agentId"
          class="filter-select"
          placeholder="全部坐席"
          clearable
          filterable
          @change="search"
        >
          <el-option
            v-for="item in agents"
            :key="item.agentId"
            :label="`${item.agentName || '坐席 ' + item.agentId}（${agentLoadText(item)}）`"
            :value="item.agentId"
          />
        </el-select>
        <el-button :icon="Search" @click="search">查询</el-button>
        <div class="grow" />
        <span class="toolbar-meta">
          在线坐席 <b class="zk-num">{{ onlineAgents }}</b> 人 · 进行中 <b class="zk-num">{{ total }}</b> 个会话
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="监听会话列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="rows"
          row-key="sessionId"
          aria-label="监听会话列表"
          :row-class-name="rowClassName"
        >
          <el-table-column label="会话" min-width="190">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">{{ row.sessionNo || `#${row.sessionId}` }}</span>
                <span class="cell-hint">创建 {{ formatDateTime(row.createTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                :tone="dictTone(SESSION_STATUS_DICT, row.status)"
                dot
                :live="row.status === 'HUMAN' || row.status === 'WAITING'"
              />
            </template>
          </el-table-column>
          <el-table-column label="访客" width="120">
            <template #default="{ row }">
              <span class="cell-muted">{{ row.userName || `用户 #${row.userId || '—'}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="接待" width="120">
            <template #default="{ row }">
              <span class="cell-muted">{{ row.agentName || (row.status === 'AI' ? 'AI 助手' : '未接入') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最后一条消息" min-width="240">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ senderLabel(row.lastSenderType) }}：{{ row.lastMessage || '—' }}</span>
                <span class="cell-hint">{{ formatDateTime(row.lastMessageTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="待回复" width="116">
            <template #default="{ row }">
              <StatusPill
                v-if="row.pendingSeconds != null"
                :label="formatWait(row.pendingSeconds)"
                :tone="pendingTone(row.pendingSeconds)"
                dot
              />
              <span v-else class="cell-hint">已回复</span>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="112">
            <template #default="{ row }">
              <span class="zk-num">{{ formatWait(row.durationSeconds) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="告警" width="92">
            <template #default="{ row }">
              <StatusPill
                v-if="Number(row.openAlertCount) > 0"
                :label="`${row.openAlertCount} 条`"
                tone="red"
                dot
                :live="true"
              />
              <span v-else class="cell-hint">正常</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDetail(row)">监听</el-button>
                <el-button
                  v-if="row.status === 'HUMAN' || row.status === 'WAITING'"
                  link
                  type="warning"
                  class="op-btn"
                  @click="quickTakeover(row)"
                >
                  接管
                </el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '监听列表加载失败' : '当前没有进行中的会话'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : '只在访客发起会话并进入 AI 接待 / 排队 / 人工接待后出现在这里'
              "
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
          aria-label="监听列表分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      强插发言以「组长」身份进入会话，访客与坐席双方可见 · 强制接管会把会话责任人改为当前管理员，原坐席并发数同步释放 · 改派只允许指派给在线且未满并发的坐席
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      :size="drawerSize"
      append-to-body
      class="monitor-drawer"
      @closed="onDrawerClosed"
    >
      <div v-if="detailLoading" class="md-loading">
        <ZkSkeleton variant="card" :rows="4" />
      </div>

      <div v-else-if="detail" class="md">
        <header class="md-head">
          <div class="md-head-main">
            <span class="md-no">{{ detail.session?.sessionNo || `#${detail.session?.id}` }}</span>
            <StatusPill
              :label="dictLabel(SESSION_STATUS_DICT, detail.session?.status)"
              :tone="dictTone(SESSION_STATUS_DICT, detail.session?.status)"
              dot
              :live="detail.session?.status === 'HUMAN' || detail.session?.status === 'WAITING'"
            />
            <StatusPill
              v-if="detail.pendingSeconds != null"
              :label="`待回复 ${formatWait(detail.pendingSeconds)}`"
              :tone="pendingTone(detail.pendingSeconds)"
              dot
            />
          </div>
          <div class="md-meta">
            <span>访客：{{ detail.userName || '—' }}</span>
            <span>接待：{{ detail.agentName || (detail.session?.status === 'AI' ? 'AI 助手' : '未接入') }}</span>
            <span>创建：{{ formatDateTime(detail.session?.createTime) }}</span>
            <span v-if="detail.session?.acceptTime">接入：{{ formatDateTime(detail.session.acceptTime) }}</span>
          </div>
          <p v-if="!detail.takeoverAllowed && detail.takeoverHint" class="md-hint">
            {{ detail.takeoverHint }}
          </p>
        </header>

        <section v-if="detail.alerts?.length" class="md-alerts" aria-label="该会话未闭环告警">
          <article v-for="item in detail.alerts" :key="item.id" class="md-alert">
            <div class="md-alert-main">
              <StatusPill
                :label="item.severityName || dictLabel(ALERT_SEVERITY_DICT, item.severity)"
                :tone="dictTone(ALERT_SEVERITY_DICT, item.severity)"
                :solid="item.severity === 'CRITICAL'"
                dot
              />
              <span class="md-alert-title">{{ item.title }}</span>
              <span class="md-alert-time">{{ formatDateTime(item.lastTime) }}</span>
            </div>
            <p class="md-alert-detail">{{ item.detail || '—' }}</p>
          </article>
          <el-button link type="primary" class="md-alert-more" @click="goAlerts">
            到告警中心处理 →
          </el-button>
        </section>

        <div ref="msgRef" class="md-messages" role="log" aria-label="会话消息">
          <p v-if="!messageList.length" class="md-empty">暂无消息记录</p>
          <div v-for="msg in messageList" :key="msg.key" class="md-msg" :class="msg.role">
            <div v-if="msg.role === 'system'" class="md-system">{{ msg.content }}</div>
            <div v-else-if="msg.role === 'note'" class="md-note">
              <span class="md-note-tag">内部备注 · 仅坐席可见</span>
              <p>{{ msg.content }}</p>
            </div>
            <template v-else>
              <div class="md-avatar" :class="`is-${msg.role}`" aria-hidden="true">{{ msg.badge }}</div>
              <div class="md-bubble-wrap">
                <div class="md-bubble-head">
                  <span class="md-msg-name">{{ msg.name }}</span>
                  <span class="md-msg-time">{{ msg.time }}</span>
                </div>
                <div class="md-bubble" :class="`is-${msg.role}`">{{ msg.content }}</div>
              </div>
            </template>
          </div>
        </div>

        <footer class="md-foot">
          <div v-if="detail.whisperAllowed" class="md-whisper">
            <el-input
              v-model="whisperDraft"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="500"
              show-word-limit
              placeholder="以「组长」身份强插发言，访客与坐席双方可见"
              aria-label="组长强插发言"
              @keydown.enter.exact.prevent="sendWhisper"
            />
            <div class="md-whisper-actions">
              <el-button
                type="primary"
                :icon="Promotion"
                :loading="whisperSending"
                :disabled="!whisperDraft.trim()"
                @click="sendWhisper"
              >
                发送强插
              </el-button>
            </div>
          </div>
          <p v-else class="md-foot-tip">会话已结束，无法再强插发言</p>

          <div class="md-actions">
            <el-button
              v-if="detail.takeoverAllowed"
              type="warning"
              plain
              :icon="Switch"
              :loading="takingOver"
              @click="takeoverInDrawer"
            >
              强制接管
            </el-button>
            <el-button
              v-if="detail.takeoverAllowed"
              :icon="Position"
              @click="openReassign"
            >
              改派坐席
            </el-button>
          </div>
        </footer>
      </div>

      <div v-else class="md-error">
        <p>{{ detailError || '会话详情加载失败' }}</p>
        <el-button @click="loadDetail">重新加载</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="reassignVisible" title="改派坐席" width="520px" append-to-body>
      <p class="md-dialog-tip">
        会话 {{ detail?.session?.sessionNo || '' }} 将转交给所选坐席，原坐席并发数同步释放。
      </p>
      <el-form label-position="top">
        <el-form-item label="目标坐席">
          <el-select v-model="reassignForm.targetAgentId" class="full-select" filterable placeholder="选择在线坐席">
            <el-option
              v-for="item in assignableAgents"
              :key="item.agentId"
              :label="`${item.agentName || '坐席 ' + item.agentId}（${agentLoadText(item)}）`"
              :value="item.agentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="改派原因（选填）">
          <el-input
            v-model="reassignForm.reason"
            type="textarea"
            :rows="2"
            maxlength="120"
            show-word-limit
            resize="none"
            placeholder="如：原坐席技能不匹配 / 负载过高"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reassigning"
          :disabled="!reassignForm.targetAgentId"
          @click="submitReassign"
        >
          确认改派
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Position, Promotion, Refresh, Search, Switch } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  getMonitorAgents,
  getMonitorDetail,
  getMonitorSessions,
  supervisorReassign,
  supervisorTakeover,
  supervisorWhisper
} from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  ALERT_SEVERITY_DICT,
  SESSION_STATUS_DICT,
  dictLabel,
  dictTone,
  formatClock,
  formatDateTime,
  formatWait
} from '@/utils/dict'

const router = useRouter()

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
} = usePagedList(getMonitorSessions, { status: '', agentId: '', keyword: '' })

const rowClassName = () => ''

/* ---------- 坐席候选 ---------- */
const agents = ref([])
const onlineAgents = computed(
  () => agents.value.filter((item) => item.onlineStatus === 'ONLINE' || item.onlineStatus === 'BUSY').length
)

async function loadAgents() {
  try {
    const data = await getMonitorAgents()
    agents.value = data?.list || []
  } catch {
    agents.value = []
  }
}

function agentLoadText(item) {
  const status = item.onlineStatus === 'ONLINE' ? '在线' : item.onlineStatus === 'BUSY' ? '忙碌' : '离线'
  return `${status} ${item.currentCount}/${item.maxConcurrent || '—'}`
}

/* ---------- 自动刷新 ---------- */
const autoRefresh = ref(true)
let listTimer = null

function startListTimer() {
  stopListTimer()
  if (!autoRefresh.value) return
  listTimer = window.setInterval(() => {
    if (!drawerVisible.value) load()
  }, 15000)
}

function stopListTimer() {
  if (listTimer) window.clearInterval(listTimer)
  listTimer = null
}

function toggleAutoRefresh(value) {
  if (value) {
    startListTimer()
    ElMessage.success('已开启自动刷新（每 15 秒）')
  } else {
    stopListTimer()
    ElMessage.info('已关闭自动刷新')
  }
}

function reloadAll() {
  load()
  loadAgents()
}

/* ---------- 列表展示 ---------- */
const pendingTone = (seconds) => {
  const sec = Number(seconds) || 0
  if (sec >= 180) return 'red'
  if (sec >= 60) return 'amber'
  return 'teal'
}

const senderLabel = (senderType) => {
  if (senderType === 'USER') return '访客'
  if (senderType === 'AGENT') return '坐席'
  if (senderType === 'SUPERVISOR') return '组长'
  if (senderType === 'SYSTEM') return '系统'
  if (senderType === 'INTERNAL_NOTE') return '备注'
  return 'AI'
}

/* ---------- 详情 ---------- */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const detailSessionId = ref(null)
const whisperDraft = ref('')
const whisperSending = ref(false)
const takingOver = ref(false)
const msgRef = ref(null)
let detailTimer = null

const drawerSize = computed(() => (window.innerWidth < 900 ? '100%' : '760px'))
const drawerTitle = computed(() => {
  const no = detail.value?.session?.sessionNo
  return no ? `会话监听 · ${no}` : '会话监听'
})

const messageList = computed(() => {
  const list = detail.value?.messages || []
  return list.map((raw) => {
    const sender = raw.senderType || 'AI'
    const role =
      sender === 'USER'
        ? 'user'
        : sender === 'AGENT'
          ? 'agent'
          : sender === 'SUPERVISOR'
            ? 'supervisor'
            : sender === 'SYSTEM'
              ? 'system'
              : sender === 'INTERNAL_NOTE'
                ? 'note'
                : 'ai'
    const nameMap = {
      user: raw.senderName || '访客',
      agent: raw.senderName || '坐席',
      supervisor: raw.senderName || '组长',
      ai: raw.senderName || 'AI 助手',
      note: raw.senderName || '内部备注',
      system: '系统'
    }
    const badgeMap = { user: 'U', agent: 'CS', supervisor: 'SV', ai: 'AI', note: '备', system: 'SYS' }
    return {
      key: raw.id || `m-${Math.random().toString(36).slice(2, 8)}`,
      role,
      badge: badgeMap[role] || 'AI',
      name: nameMap[role],
      content: raw.content || '',
      time: formatClock(raw.createTime) || ''
    }
  })
})

function openDetail(row) {
  detailSessionId.value = row.sessionId
  detail.value = null
  detailError.value = ''
  drawerVisible.value = true
  loadDetail()
  startDetailTimer()
}

async function loadDetail(silent = false) {
  if (!detailSessionId.value) return
  if (!silent) detailLoading.value = true
  detailError.value = ''
  try {
    const data = await getMonitorDetail(detailSessionId.value)
    detail.value = data || null
    if (!silent) scrollToBottom()
  } catch (e) {
    if (!silent) {
      detail.value = null
      detailError.value = e?.response?.data?.message || '会话详情加载失败'
    }
  } finally {
    if (!silent) detailLoading.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function startDetailTimer() {
  stopDetailTimer()
  detailTimer = window.setInterval(() => {
    if (drawerVisible.value) loadDetail(true)
  }, 6000)
}

function stopDetailTimer() {
  if (detailTimer) window.clearInterval(detailTimer)
  detailTimer = null
}

function onDrawerClosed() {
  stopDetailTimer()
  detail.value = null
  detailSessionId.value = null
  whisperDraft.value = ''
}

watch(drawerVisible, (open) => {
  if (!open) stopDetailTimer()
})

/* ---------- 强插 ---------- */
async function sendWhisper() {
  const content = whisperDraft.value.trim()
  if (!content || whisperSending.value || !detail.value) return
  whisperSending.value = true
  try {
    await supervisorWhisper(detailSessionId.value, content)
    whisperDraft.value = ''
    ElMessage.success('已以组长身份强插发言')
    await loadDetail(true)
    scrollToBottom()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '强插发言失败，请稍后重试')
  } finally {
    whisperSending.value = false
  }
}

/* ---------- 接管 ---------- */
async function confirmTakeover(sessionNo) {
  try {
    await ElMessageBox.confirm(
      `接管会话 ${sessionNo || ''} 后，会话责任人将变为当前管理员，原坐席不再负责该会话。确认接管？`,
      '强制接管',
      { type: 'warning', confirmButtonText: '确认接管', cancelButtonText: '取消' }
    )
    return true
  } catch {
    return false
  }
}

async function takeoverInDrawer() {
  if (!detail.value) return
  if (!(await confirmTakeover(detail.value.session?.sessionNo))) return
  takingOver.value = true
  try {
    const data = await supervisorTakeover(detailSessionId.value)
    detail.value = data || detail.value
    ElMessage.success('已接管该会话，可继续强插或改派')
    load()
    scrollToBottom()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '接管失败，请稍后重试')
  } finally {
    takingOver.value = false
  }
}

async function quickTakeover(row) {
  if (!(await confirmTakeover(row.sessionNo))) return
  try {
    await supervisorTakeover(row.sessionId)
    ElMessage.success(`已接管会话 ${row.sessionNo || ''}`)
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '接管失败，请稍后重试')
  }
}

/* ---------- 改派 ---------- */
const reassignVisible = ref(false)
const reassigning = ref(false)
const reassignForm = reactive({ targetAgentId: null, reason: '' })

const assignableAgents = computed(() =>
  agents.value.filter(
    (item) =>
      (item.onlineStatus === 'ONLINE' || item.onlineStatus === 'BUSY') &&
      Number(item.currentCount) < Number(item.maxConcurrent || 0)
  )
)

function openReassign() {
  reassignForm.targetAgentId = null
  reassignForm.reason = ''
  reassignVisible.value = true
  if (!agents.value.length) loadAgents()
}

async function submitReassign() {
  if (!reassignForm.targetAgentId || !detail.value) return
  reassigning.value = true
  try {
    const data = await supervisorReassign(detailSessionId.value, {
      targetAgentId: reassignForm.targetAgentId,
      reason: reassignForm.reason.trim() || null
    })
    detail.value = data || detail.value
    reassignVisible.value = false
    ElMessage.success('已改派给目标坐席')
    load()
    loadAgents()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '改派失败，请稍后重试')
  } finally {
    reassigning.value = false
  }
}

/* ---------- 跳转 ---------- */
function goAlerts() {
  drawerVisible.value = false
  router.push('/admin/alerts')
}

onMounted(() => {
  reloadAll()
  startListTimer()
})

onBeforeUnmount(() => {
  stopListTimer()
  stopDetailTimer()
})
</script>

<style scoped>
.auto-switch {
  --el-switch-on-color: var(--zk-accent);
}

.filter-select {
  width: 148px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 1180px;
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
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.cell-muted {
  font-size: 12.5px;
  color: var(--zk-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
  line-height: 1.5;
}

/* ---------- 详情抽屉 ---------- */
.md-loading {
  padding: 8px;
}

.md {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.md-head {
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--zk-border);
}

.md-head-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.md-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.md-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.md-hint {
  margin: 8px 0 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--zk-amber);
}

.md-alerts {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--zk-radius-sm);
  background: #fef2f2;
  border: 1px solid #fecaca;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.md-alert-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.md-alert-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.md-alert-time {
  margin-left: auto;
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.md-alert-detail {
  margin: 2px 0 0;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--zk-text-2);
}

.md-alert-more {
  align-self: flex-start;
  padding: 0;
}

.md-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 2px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.md-empty {
  margin: auto;
  font-size: 12px;
  color: var(--zk-text-3);
}

.md-msg {
  display: flex;
  gap: 8px;
}

.md-msg.user {
  flex-direction: row-reverse;
}

.md-system {
  margin: 0 auto;
  padding: 3px 12px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
  font-size: 11px;
  color: var(--zk-text-3);
}

.md-note {
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--zk-radius-sm);
  border: 1px dashed var(--zk-amber);
  background: #fffbeb;
}

.md-note-tag {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--zk-amber);
}

.md-note p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--zk-text-2);
}

.md-avatar {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: var(--zk-accent);
}

.md-avatar.is-user {
  background: #17181c;
}

.md-avatar.is-agent {
  background: #b45309;
}

.md-avatar.is-supervisor {
  background: #7c3aed;
}

.md-bubble-wrap {
  max-width: 78%;
  min-width: 0;
}

.md-msg.user .md-bubble-wrap {
  text-align: right;
}

.md-bubble-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.md-msg.user .md-bubble-head {
  justify-content: flex-end;
}

.md-msg-name {
  font-weight: 600;
  color: var(--zk-text-2);
}

.md-bubble {
  display: inline-block;
  padding: 7px 11px;
  border-radius: var(--zk-radius-sm);
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
  background: var(--zk-surface-2);
  color: var(--zk-text);
}

.md-bubble.is-user {
  background: #17181c;
  color: #fff;
}

.md-bubble.is-agent {
  background: #eef0ff;
  color: #312e81;
}

.md-bubble.is-supervisor {
  background: #f5f3ff;
  color: #5b21b6;
  border: 1px solid #ddd6fe;
}

.md-foot {
  border-top: 1px dashed var(--zk-border);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.md-whisper-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.md-foot-tip {
  margin: 0;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.md-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.md-error {
  padding: 24px 8px;
  text-align: center;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.md-dialog-tip {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

.full-select {
  width: 100%;
}

@media (max-width: 640px) {
  .filter-select {
    width: 100%;
  }
}
</style>
