<template>
  <div class="zk-page">
    <PageHeader
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
            :live="isOnline"
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
        <div v-if="mySkillGroupsReady" class="my-skills">
          <span class="my-label">我的技能组</span>
          <template v-if="mySkillGroups.length">
            <el-tag
              v-for="group in mySkillGroups"
              :key="group.id"
              size="small"
              effect="plain"
            >
              {{ group.name }}
            </el-tag>
          </template>
          <span v-else class="my-hint">尚未加入技能组，仅参与「不限技能组」的自动分配</span>
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
        <span class="summary-value zk-num">{{ queueSize }}</span>
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
      <ZkSkeleton v-if="!agentsLoaded" variant="card" :rows="2" class="agent-skeleton" />
      <template v-else>
        <article
          v-for="(agent, index) in agents"
          :key="agent.agentId"
          v-reveal="{ index: revealIndex(index) }"
          class="agent-tile zk-hover-lift zk-press"
          :class="{ 'is-self': isSelfAgent(agent) }"
          role="listitem"
        >
          <div class="tile-head">
            <div class="tile-avatar" :class="statusTone(agent.onlineStatus)" aria-hidden="true">
              {{ (agent.agentName || '坐').slice(0, 1) }}
            </div>
            <div class="tile-id">
              <div class="tile-name">
                {{ agent.agentName || `坐席 ${agent.agentId}` }}
                <span v-if="isSelfAgent(agent)" class="tile-self">我</span>
              </div>
              <div class="tile-sub">坐席 ID · {{ agent.agentId }}</div>
            </div>
            <StatusPill
              :label="dictLabel(AGENT_STATUS_DICT, agent.onlineStatus)"
              :tone="dictTone(AGENT_STATUS_DICT, agent.onlineStatus)"
              :live="agent.onlineStatus === 'ONLINE' || agent.onlineStatus === 'BUSY'"
              dot
            />
          </div>

          <div class="load-block">
            <div class="load-head">
              <span class="load-label">并发负载</span>
              <span class="load-value zk-num">
                <LoadCount :value="Number(agent.currentCount) || 0" /><span class="load-max">/{{ agent.maxConcurrent ?? 0 }}</span>
              </span>
            </div>
            <div
              class="tile-track"
              role="progressbar"
              :aria-valuenow="Number(agent.currentCount) || 0"
              aria-valuemin="0"
              :aria-valuemax="Math.max(Number(agent.maxConcurrent) || 0, 1)"
              :aria-label="`${agent.agentName || '坐席'} 并发负载`"
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
              :class="agent.onlineStatus === 'OFFLINE' ? 'off' : 'on zk-dot-live'"
              aria-hidden="true"
            />
            最近心跳 {{ formatDateTime(agent.lastHeartbeat) }}
          </div>
        </article>
      </template>

      <div v-if="agentsLoaded && !agents.length" class="agent-empty zk-card">
        <ZkEmpty
          size="sm"
          :title="agentsError || '暂无坐席数据'"
          :hint="agentsError ? '加载失败，请稍后重试' : '还没有坐席上线'"
        >
          <el-button v-if="agentsError" @click="loadAgents">重新加载</el-button>
          <el-button v-else @click="loadAgents">刷新列表</el-button>
        </ZkEmpty>
      </div>
    </section>

    <!-- 等待队列 -->
    <div class="zk-card queue-card">
      <div class="zk-card-head">
        <div>
          <div id="queue-title" class="zk-card-title">等待接入队列</div>
          <div class="zk-card-desc">按进入排队时间先后排序，等待时长实时刷新</div>
        </div>
        <div class="queue-head-right">
          <span class="queue-live"><i class="zk-dot-live" aria-hidden="true" />实时</span>
          <span class="queue-count zk-num" aria-live="polite">{{ queueSize }} 人等待</span>
        </div>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="等待接入队列"
        tabindex="0"
        :aria-busy="queueLoading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!queueLoaded" variant="table" :rows="4" :cols="5" class="table-skeleton" />
        <el-table
          v-else
          v-loading="queueLoading"
          :data="waiting"
          aria-label="坐席等待接入队列"
          :class="queueFadeClass"
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
            <template #default="{ row }">
              <el-tooltip :content="isAgent ? '接入该会话并开始人工接待' : '仅坐席可接入会话'" placement="top">
                <span class="btn-wrap">
                  <el-button
                    type="primary"
                    plain
                    :loading="accepting === row.sessionId"
                    :disabled="Boolean(accepting) || !isAgent"
                    @click.stop="handleAccept(row)"
                  >
                    接入会话
                  </el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="queueError || '当前没有等待接入的用户'"
              :hint="queueError ? '队列加载失败，请稍后重试' : 'AI 无法解决时会自动进入该队列'"
            >
              <el-button v-if="queueError" @click="loadWaiting()">重新加载</el-button>
              <el-button v-else @click="loadWaiting()">刷新队列</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      坐席上线后才能接收排队会话；接入后会话进入「我的会话」，访客端会收到坐席已接入提示
    </div>

    <SessionDrawer
      v-model="drawerVisible"
      :session-id="activeSessionId"
      role="AGENT"
      @changed="reload"
    />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  Coffee,
  Refresh,
  SwitchButton,
  Timer,
  VideoPlay
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import SessionDrawer from '@/components/SessionDrawer.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { useCountUp } from '@/composables/motion'
import {
  acceptAgentSession,
  agentOffline,
  agentOnline,
  agentSetStatus,
  getAgentList,
  getAgentQueue,
  getMySkillGroups
} from '@/api'
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
const queueSize = ref(0)
const accepting = ref(null)
const drawerVisible = ref(false)
const activeSessionId = ref(null)
const loading = ref(false)
const queueLoading = ref(false)
const agentsLoaded = ref(false)
const queueLoaded = ref(false)
const agentsError = ref('')
const queueError = ref('')

/** 卡片入场错开序号：超过 4 张后不再继续累加，保证最晚一张入场仍 ≤ 700ms */
const revealIndex = (index) => Math.min(index, 4)

/* 卡片内负载数字滚动：v-for 里无法直接调用组合式，用局部组件包一层 */
const LoadCount = defineComponent({
  props: { value: { type: Number, default: 0 } },
  setup(props) {
    const source = computed(() => props.value)
    const { display } = useCountUp(source, {
      duration: 600,
      updateDuration: 420,
      separator: false
    })
    return () => h('span', display.value)
  }
})

// 队列刷新后重播 160ms 淡入（只淡不位移）
const queueTick = ref(0)
watch(waiting, () => {
  queueTick.value += 1
})
const queueFadeClass = computed(() => (queueTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

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

/** 我的技能组：决定自己能接到哪些咨询类型，加载成功后才展示，避免失败时误报「未加入」 */
const mySkillGroups = ref([])
const mySkillGroupsReady = ref(false)

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
    const data = await getAgentList()
    agents.value = Array.isArray(data) ? data : []
    agentsError.value = ''
  } catch (error) {
    agents.value = []
    agentsError.value =
      error?.response?.status === 404
        ? '坐席数据加载失败，请稍后重试'
        : error?.message || '坐席数据加载失败'
  } finally {
    loading.value = false
    agentsLoaded.value = true
  }
}

async function loadWaiting(silent = false) {
  queueLoading.value = !silent
  try {
    const data = await getAgentQueue({ pageNum: 1, pageSize: 100 })
    waiting.value = Array.isArray(data?.list) ? data.list : []
    queueSize.value = Number(data?.queueSize ?? waiting.value.length) || 0
    fetchedAt.value = Date.now()
    queueError.value = ''
  } catch (error) {
    waiting.value = []
    queueSize.value = 0
    queueError.value =
      error?.response?.status === 404
        ? '等待队列加载失败，请稍后重试'
        : error?.message || '等待队列加载失败'
  } finally {
    queueLoading.value = false
    queueLoaded.value = true
  }
}

/** 接入排队会话：成功后直接打开对话抽屉，减少一步跳转；并刷新坐席负载与队列 */
async function handleAccept(row) {
  if (!isAgent.value || accepting.value) return
  accepting.value = row.sessionId
  try {
    await acceptAgentSession(row.sessionId)
    ElMessage.success(`已接入会话 ${row.sessionNo}，可直接回复`)
    activeSessionId.value = row.sessionId
    drawerVisible.value = true
    await Promise.all([loadAgents(), loadWaiting(true)])
  } catch {
    // 409 等业务错误已由 http 拦截器展示后端 message
  } finally {
    accepting.value = null
  }
}

async function loadMySkillGroups() {
  if (!isAgent.value) return
  try {
    const data = await getMySkillGroups()
    mySkillGroups.value = Array.isArray(data) ? data : []
    mySkillGroupsReady.value = true
  } catch {
    // 技能组信息为辅助信息，加载失败不影响工作台主流程
    mySkillGroupsReady.value = false
  }
}

function reload() {
  loadAgents()
  loadWaiting()
  loadMySkillGroups()
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
/* 首屏骨架占位：卡片与队列各自与真实内容同尺寸，加载前后不跳 */
.agent-skeleton {
  grid-column: 1 / -1;
  padding: 16px 18px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface);
}

.table-skeleton {
  padding: 12px 14px;
}

/* 队列刷新 / 筛选后淡入 160ms：只淡不位移 */
.list-fade-a {
  animation: zk-fade-a 160ms var(--zk-ease-out);
}

.list-fade-b {
  animation: zk-fade-b 160ms var(--zk-ease-out);
}

@keyframes zk-fade-a {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes zk-fade-b {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

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

.my-skills {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: border-color var(--zk-dur-base) var(--zk-ease-out);
}

.agent-tile:hover {
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
  border-radius: var(--zk-radius);
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
  border-radius: var(--zk-radius-lg);
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
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.tile-fill {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  transition: width var(--zk-dur-slow) var(--zk-ease-out);
}

.tile-fill.calm {
  background: var(--zk-green);
}

.tile-fill.warm {
  background: var(--zk-amber-edge);
}

.tile-fill.hot {
  background: var(--zk-red-edge);
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
  background: #10b981;
  color: #10b981;
}

.heartbeat-dot.off {
  background: var(--zk-border-strong);
}

.agent-tile.is-self {
  border-color: var(--zk-accent-line);
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
  border-radius: var(--zk-radius-sm);
}

.queue-live i {
  position: relative;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  color: var(--zk-teal);
  background: var(--zk-teal);
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
  border-radius: var(--zk-radius-sm);
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
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
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
