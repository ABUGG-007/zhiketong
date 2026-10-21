<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="AGENT DESK"
      title="坐席工作台"
      description="坐席在线状态、并发负载与等待接入队列。等待时长每秒刷新，队列每 30 秒自动拉取一次。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 当前坐席状态（仅 AGENT 可自助操作） -->
    <div
      v-if="isAgent"
      class="my-agent zk-card"
      role="region"
      aria-label="我的坐席状态"
      :aria-busy="statusBusy ? 'true' : 'false'"
    >
      <div class="my-agent-main">
        <div class="my-agent-head">
          <span class="my-label">我的坐席状态</span>
          <StatusPill
            :label="dictLabel(AGENT_STATUS_DICT, myStatus)"
            :tone="dictTone(AGENT_STATUS_DICT, myStatus)"
            dot
          />
          <span v-if="!myAgent" class="my-hint">尚未建档，点击「上线」创建坐席状态</span>
        </div>
        <div class="my-meta">
          <span v-if="myAgent">
            负载
            <b class="zk-num">{{ myAgent.currentCount ?? 0 }}/{{ myAgent.maxConcurrent ?? 0 }}</b>
          </span>
          <span v-if="myAgent">累计接待 <b class="zk-num">{{ myAgent.totalServed ?? 0 }}</b></span>
          <span>最近心跳 {{ formatDateTime(myAgent?.lastHeartbeat) }}</span>
        </div>
      </div>
      <div class="my-actions">
        <el-button
          v-if="!isOnline"
          type="primary"
          :icon="VideoPlay"
          :loading="statusBusy === 'online'"
          :disabled="Boolean(statusBusy)"
          @click="handleAgentAction('online')"
        >
          上线
        </el-button>
        <template v-else>
          <el-button
            :type="myStatus === 'BUSY' ? 'success' : 'warning'"
            :icon="myStatus === 'BUSY' ? CircleCheck : Coffee"
            :loading="statusBusy === (myStatus === 'BUSY' ? 'ONLINE' : 'BUSY')"
            :disabled="Boolean(statusBusy)"
            @click="handleAgentAction(myStatus === 'BUSY' ? 'ONLINE' : 'BUSY')"
          >
            {{ myStatus === 'BUSY' ? '恢复在线' : '设为忙碌' }}
          </el-button>
          <el-button
            :icon="SwitchButton"
            :loading="statusBusy === 'offline'"
            :disabled="Boolean(statusBusy)"
            @click="handleAgentAction('offline')"
          >
            下线
          </el-button>
        </template>
      </div>
    </div>

    <div
      v-else
      class="my-agent my-agent--readonly zk-card"
      role="region"
      aria-label="坐席状态说明"
    >
      <div class="my-agent-main">
        <div class="my-agent-head">
          <span class="my-label">坐席状态管理</span>
          <StatusPill label="只读视角" tone="slate" />
        </div>
        <div class="my-meta">
          当前登录角色为{{ roleLabel }}，仅坐席（AGENT）可自助上线 / 下线 / 切换忙碌；管理员可查看全部坐席负载。
        </div>
      </div>
    </div>

    <!-- 概览 -->
    <div class="agent-summary" role="group" aria-label="坐席状态概览">
      <div class="summary-item">
        <span class="summary-label">在线坐席</span>
        <span class="summary-value zk-num">{{ statusCount.ONLINE }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">忙碌中</span>
        <span class="summary-value zk-num">{{ statusCount.BUSY }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">离线</span>
        <span class="summary-value zk-num">{{ statusCount.OFFLINE }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">当前排队用户</span>
        <span class="summary-value zk-num">{{ waiting.length }}</span>
      </div>
      <div class="summary-desc">
        负载 = 当前接待中会话数 / 坐席最大并发数，达到 90% 标红提示需要扩容。
      </div>
    </div>

    <!-- 坐席卡片 -->
    <section
      class="agent-grid"
      role="list"
      aria-label="坐席状态列表"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <article
        v-for="agent in agents"
        :key="agent.agentId"
        class="agent-tile"
        :class="{ 'is-self': isSelfAgent(agent) }"
        role="listitem"
      >
        <div class="tile-head">
          <div class="tile-avatar" :class="statusTone(agent.onlineStatus)" aria-hidden="true">
            {{ (agent.nickname || '坐').slice(0, 1) }}
          </div>
          <div class="tile-id">
            <div class="tile-name">
              {{ agent.nickname || `坐席 ${agent.agentId}` }}
              <span v-if="isSelfAgent(agent)" class="tile-self">我</span>
            </div>
            <div class="tile-sub">坐席 ID · {{ agent.agentId }}</div>
          </div>
          <StatusPill
            :label="dictLabel(AGENT_STATUS_DICT, agent.onlineStatus)"
            :tone="dictTone(AGENT_STATUS_DICT, agent.onlineStatus)"
            dot
          />
        </div>

        <div class="load-block">
          <div class="load-head">
            <span class="load-label">并发负载</span>
            <span class="load-value zk-num">
              {{ agent.currentCount ?? 0 }}<span class="load-max">/{{ agent.maxConcurrent ?? 0 }}</span>
            </span>
          </div>
          <div
            class="tile-track"
            role="progressbar"
            :aria-valuenow="Number(agent.currentCount) || 0"
            aria-valuemin="0"
            :aria-valuemax="Math.max(Number(agent.maxConcurrent) || 0, 1)"
            :aria-label="`${agent.nickname || '坐席'} 并发负载`"
          >
            <span
              class="tile-fill"
              :class="loadTone(agent.currentCount, agent.maxConcurrent)"
              :style="{ width: `${percent(agent.currentCount, agent.maxConcurrent)}%` }"
              aria-hidden="true"
            />
          </div>
          <div class="load-foot">
            <span>负载率 {{ percent(agent.currentCount, agent.maxConcurrent) }}%</span>
            <span class="muted">累计接待 {{ agent.totalServed ?? 0 }}</span>
          </div>
        </div>

        <div class="tile-foot">
          <span
            class="heartbeat-dot"
            :class="agent.onlineStatus === 'OFFLINE' ? 'off' : 'on'"
            aria-hidden="true"
          />
          最近心跳 {{ formatDateTime(agent.lastHeartbeat) }}
        </div>
      </article>

      <div v-if="!agents.length" class="agent-empty zk-card">
        <div class="table-empty">
          <div class="empty-icon" aria-hidden="true">
            <el-icon><Headset /></el-icon>
          </div>
          <p>{{ agentsError || '暂无坐席数据' }}</p>
          <span v-if="agentsError" class="empty-hint">请确认后端 /api/console/agents 已就绪</span>
          <span v-else class="empty-hint">还没有坐席上线</span>
          <el-button v-if="agentsError" @click="loadAgents">重新加载</el-button>
          <el-button v-else @click="loadAgents">刷新列表</el-button>
        </div>
      </div>
    </section>

    <!-- 等待队列 -->
    <div class="zk-card queue-card">
      <div class="zk-card-head">
        <div>
          <div id="queue-title" class="zk-card-title">等待接入队列</div>
          <div class="zk-card-desc">按进入排队时间先后排序，waitSeconds 每秒递增</div>
        </div>
        <div class="queue-head-right">
          <span class="queue-live"><i aria-hidden="true" />实时</span>
          <span class="queue-count zk-num" aria-live="polite">{{ waiting.length }} 人等待</span>
        </div>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="等待接入队列"
        tabindex="0"
        :aria-busy="queueLoading ? 'true' : 'false'"
      >
        <el-table
          v-loading="queueLoading"
          :data="waiting"
          aria-label="坐席等待接入队列"
          empty-text="当前没有等待接入的用户"
        >
          <el-table-column label="会话号" width="150">
            <template #default="{ row }">
              <span class="order-no">{{ row.sessionNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用户" width="130">
            <template #default="{ row }">
              <span class="person">{{ row.userName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="排队进入时间" width="190">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.queueEnterTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已等待" width="160">
            <template #default="{ row }">
              <span class="wait-badge zk-num" :class="waitLevel(row)">
                <el-icon aria-hidden="true"><Timer /></el-icon>
                {{ formatWait(liveWait(row)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="140">
            <template #default>
              <el-tooltip content="接入会话功能将在 M4 里程碑开放" placement="top">
                <span class="btn-wrap">
                  <el-button type="primary" plain disabled>接入会话</el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><Timer /></el-icon>
              </div>
              <p>{{ queueError || '当前没有等待接入的用户' }}</p>
              <span v-if="queueError" class="empty-hint">请确认后端 /api/console/waiting 已就绪</span>
              <span v-else class="empty-hint">AI 无法解决时会自动进入该队列</span>
              <el-button v-if="queueError" @click="loadWaiting()">重新加载</el-button>
              <el-button v-else @click="loadWaiting()">刷新队列</el-button>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/console/agents、GET /api/console/waiting · 接入/挂起等写操作后续里程碑完善
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  Coffee,
  Headset,
  Refresh,
  SwitchButton,
  Timer,
  VideoPlay
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { agentOffline, agentOnline, agentSetStatus, getAgents, getWaitingQueue } from '@/api'
import { getUser } from '@/api/http'
import {
  AGENT_STATUS_DICT,
  ROLE_DICT,
  dictLabel,
  dictTone,
  formatDateTime,
  formatWait,
  percent
} from '@/utils/dict'

const currentUser = getUser()

const agents = ref([])
const waiting = ref([])
const loading = ref(false)
const queueLoading = ref(false)
const agentsError = ref('')
const queueError = ref('')

/* ---------- 当前登录坐席状态 ---------- */
const isAgent = computed(() => currentUser?.roleCode === 'AGENT')
const roleLabel = computed(() => dictLabel(ROLE_DICT, currentUser?.roleCode, '访客'))
const myAgent = computed(
  () =>
    agents.value.find(
      (agent) => Number(agent.agentId) === Number(currentUser?.id)
    ) || null
)
const myStatus = computed(() => myAgent.value?.onlineStatus || 'OFFLINE')
const isOnline = computed(() => myStatus.value === 'ONLINE' || myStatus.value === 'BUSY')
const statusBusy = ref('')
const isSelfAgent = (agent) => Number(agent?.agentId) === Number(currentUser?.id)

const fetchedAt = ref(Date.now())
const nowTick = ref(Date.now())
let tickTimer = null
let queueTimer = null

const statusCount = computed(() => {
  const result = { ONLINE: 0, BUSY: 0, OFFLINE: 0 }
  for (const agent of agents.value) {
    const key = agent.onlineStatus in result ? agent.onlineStatus : 'OFFLINE'
    result[key] += 1
  }
  return result
})

const statusTone = (status) =>
  status === 'ONLINE' ? 'teal' : status === 'BUSY' ? 'amber' : 'slate'

const loadTone = (current, max) => {
  const pct = percent(current, max)
  if (pct >= 90) return 'hot'
  if (pct >= 60) return 'warm'
  return 'calm'
}

/** 队列等待秒数在客户端每秒递增，避免看起来是静态数据 */
const liveWait = (row) => {
  const base = Number(row.waitSeconds) || 0
  const elapsed = Math.floor((nowTick.value - fetchedAt.value) / 1000)
  return base + Math.max(0, elapsed)
}

const waitLevel = (row) => {
  const seconds = liveWait(row)
  if (seconds >= 180) return 'danger'
  if (seconds >= 60) return 'warn'
  return 'normal'
}

/** 坐席上线 / 下线 / 忙碌切换；成功后刷新坐席与队列 */
async function handleAgentAction(action) {
  if (!isAgent.value || statusBusy.value) return
  statusBusy.value = action
  try {
    if (action === 'online') {
      await agentOnline({ maxConcurrent: Number(myAgent.value?.maxConcurrent) || 5 })
      ElMessage.success('已上线，可开始接待排队会话')
    } else if (action === 'offline') {
      await agentOffline()
      ElMessage.success('已下线')
    } else {
      await agentSetStatus(action)
      ElMessage.success(action === 'BUSY' ? '已设为忙碌，暂停分配新会话' : '已恢复在线')
    }
    await Promise.all([loadAgents(), loadWaiting(true)])
  } catch {
    // 409（仍有进行中会话）等业务错误已由 http 拦截器展示后端 message
  } finally {
    statusBusy.value = ''
  }
}

async function loadAgents() {
  loading.value = true
  try {
    const data = await getAgents()
    agents.value = Array.isArray(data) ? data : []
    agentsError.value = ''
  } catch (error) {
    agents.value = []
    agentsError.value =
      error?.response?.status === 404
        ? '接口返回 404：/api/console/agents 尚未部署'
        : error?.message || '坐席数据加载失败'
  } finally {
    loading.value = false
  }
}

async function loadWaiting(silent = false) {
  queueLoading.value = !silent
  try {
    const data = await getWaitingQueue()
    waiting.value = Array.isArray(data) ? data : []
    fetchedAt.value = Date.now()
    queueError.value = ''
  } catch (error) {
    waiting.value = []
    queueError.value =
      error?.response?.status === 404
        ? '接口返回 404：/api/console/waiting 尚未部署'
        : error?.message || '等待队列加载失败'
  } finally {
    queueLoading.value = false
  }
}

function reload() {
  loadAgents()
  loadWaiting()
}

onMounted(() => {
  reload()
  tickTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
  queueTimer = window.setInterval(() => {
    loadWaiting(true)
    loadAgents()
  }, 30000)
})

onBeforeUnmount(() => {
  if (tickTimer) window.clearInterval(tickTimer)
  if (queueTimer) window.clearInterval(queueTimer)
})
</script>

<style scoped>
/* 概览条 */
.agent-summary {
  display: flex;
  align-items: center;
  gap: 26px;
  flex-wrap: wrap;
  padding: 16px 22px;
  margin-bottom: 16px;
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-right: 26px;
  border-right: 1px solid var(--zk-border);
}

.summary-label {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.summary-value {
  font-size: 21px;
  font-weight: 700;
  color: var(--zk-text);
  letter-spacing: -0.3px;
}

.summary-desc {
  flex: 1;
  min-width: 220px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
}

/* 我的坐席状态（仅 AGENT） */
.my-agent {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 16px 22px;
  margin-bottom: 16px;
}

.my-agent--readonly {
  background: var(--zk-surface-2);
}

.my-agent-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.my-agent-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.my-label {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.my-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.my-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.my-meta b {
  color: var(--zk-text);
  font-weight: 650;
}

.my-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.my-actions .el-button {
  min-height: 38px;
  padding: 0 16px;
  font-weight: 600;
}

/* 坐席卡片 */
.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.agent-tile {
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: transform var(--zk-dur-base) var(--zk-ease-out),
    box-shadow var(--zk-dur-base) var(--zk-ease-out),
    border-color var(--zk-dur-base) var(--zk-ease-out);
}

.agent-tile:hover {
  transform: translateY(-2px);
  box-shadow: var(--zk-shadow-md);
  border-color: var(--zk-border-strong);
}

.tile-head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.tile-avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 15px;
  font-weight: 700;
  border: 1px solid;
}

.tile-avatar.teal {
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border-color: var(--zk-teal-line);
}

.tile-avatar.amber {
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
  border-color: var(--zk-amber-line);
}

.tile-avatar.slate {
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border-color: var(--zk-border);
}

.tile-id {
  flex: 1;
  min-width: 0;
}

.tile-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile-sub {
  margin-top: 3px;
  font-size: 11px;
  color: var(--zk-text-3);
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.load-block {
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  border-radius: 12px;
  padding: 12px 14px;
}

.load-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.load-label {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.load-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--zk-text);
}

.load-max {
  font-size: 12px;
  font-weight: 500;
  color: var(--zk-text-3);
}

.tile-track {
  margin-top: 9px;
  height: 6px;
  border-radius: 999px;
  background: var(--zk-border);
  overflow: hidden;
}

.tile-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width var(--zk-dur-slow) var(--zk-ease-out);
}

.tile-fill.calm {
  background: linear-gradient(90deg, #2dd4bf, var(--zk-teal));
}

.tile-fill.warm {
  background: linear-gradient(90deg, #fbbf24, var(--zk-amber));
}

.tile-fill.hot {
  background: linear-gradient(90deg, #fb7185, var(--zk-red));
}

.load-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.load-foot .muted {
  color: var(--zk-text-3);
}

.tile-foot {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.heartbeat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zk-text-3);
}

.heartbeat-dot.on {
  background: var(--zk-teal);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.14);
}

.heartbeat-dot.off {
  background: var(--zk-border-strong);
}

.agent-tile.is-self {
  border-color: var(--zk-teal-line);
  box-shadow: 0 0 0 1px rgba(20, 184, 166, 0.14), var(--zk-shadow-sm);
}

.agent-empty {
  grid-column: 1 / -1;
}

/* 队列 */
.queue-card {
  margin-top: 16px;
}

.queue-head-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.queue-live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 26px;
  padding: 3px 9px;
  font-size: 11px;
  font-weight: 600;
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border: 1px solid var(--zk-teal-line);
  border-radius: 999px;
}

.queue-live i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--zk-teal);
  animation: blink 1.6s ease-in-out infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

.queue-count {
  font-size: 12px;
  color: var(--zk-text-2);
}

.order-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.person {
  font-size: 12.5px;
  color: var(--zk-text);
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.wait-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 999px;
  border: 1px solid;
}

.wait-badge.normal {
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border-color: var(--zk-teal-line);
}

.wait-badge.warn {
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
  border-color: var(--zk-amber-line);
}

.wait-badge.danger {
  color: var(--zk-red);
  background: var(--zk-red-soft);
  border-color: var(--zk-red-line);
}

.btn-wrap {
  display: inline-block;
}

/* 表格横向滚动：窄屏滚动而不是挤压列宽 */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: 0 0 var(--zk-radius-lg) var(--zk-radius-lg);
  outline-offset: -2px;
}

.table-scroll:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: -2px;
}

.table-scroll :deep(.el-table) {
  min-width: 770px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

/* 响应式：任务要求 ≤640px 坐席卡片单列 */
@media (max-width: 1180px) {
  .agent-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .agent-summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 16px;
    padding: 14px 16px;
  }

  .summary-item {
    padding-right: 0;
    border-right: none;
  }

  .summary-desc {
    grid-column: 1 / -1;
    min-width: 0;
  }

  .my-agent {
    align-items: flex-start;
    padding: 14px 16px;
  }

  .my-actions {
    width: 100%;
  }

  .my-actions .el-button {
    flex: 1 1 auto;
  }

  .queue-card .zk-card-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .queue-head-right {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .agent-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .agent-tile {
    padding: 16px;
  }

  .agent-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
