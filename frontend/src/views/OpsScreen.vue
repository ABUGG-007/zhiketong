<template>
  <div class="zk-page screen-page">
    <PageHeader
      title="实时大屏"
      description="排队人数、排队时长与坐席负载实时刷新，支持 WebSocket 订阅推送与全屏投屏。"
    >
      <template #actions>
        <el-button :icon="SetUp" @click="openLayout">布局配置</el-button>
        <el-button :icon="FullScreen" @click="toggleFullscreen">
          {{ fullscreen ? '退出全屏' : '全屏投屏' }}
        </el-button>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="refresh">
          刷新数据
        </el-button>
      </template>
    </PageHeader>

    <section
      ref="screenRef"
      class="screen"
      :class="{ 'is-fullscreen': fullscreen, 'density-compact': isCompact }"
      aria-live="polite"
    >
      <header class="screen-head">
        <div class="screen-title">
          <span class="pulse" aria-hidden="true" />
          <div>
            <h2>运营实时大屏</h2>
            <p>实时负载 · 今日累计 · 坐席表现 · 近 12 小时趋势</p>
          </div>
        </div>
        <div class="screen-meta">
          <span class="screen-time">{{ data?.serverTime || '—' }}</span>
          <StatusPill :label="wsLabel" :tone="wsState === 'open' ? 'teal' : 'amber'" dot />
        </div>
      </header>

      <!-- 实时负载 -->
      <div v-if="isVisible('REALTIME')" class="live-grid" :style="orderStyle('REALTIME')">
        <article v-for="tile in liveTiles" :key="tile.key" class="live-tile" :class="tile.tone">
          <div class="tile-label">
            <el-icon aria-hidden="true"><component :is="tile.icon" /></el-icon>
            {{ tile.label }}
          </div>
          <div class="tile-value">{{ tile.value }}</div>
          <div class="tile-note">{{ tile.note }}</div>
        </article>
      </div>

      <!-- 今日累计 -->
      <div v-if="isVisible('TODAY')" class="today-strip" :style="orderStyle('TODAY')">
        <div v-for="item in todayItems" :key="item.label" class="today-item">
          <span class="today-label">{{ item.label }}</span>
          <b class="today-value">{{ item.value }}</b>
          <span v-if="item.note" class="today-note">{{ item.note }}</span>
        </div>
      </div>

      <div class="screen-body">
        <!-- 坐席实时表现 -->
        <div v-if="isVisible('AGENTS')" class="panel panel--agents" :style="orderStyle('AGENTS')">
          <div class="panel-head">
            <h3>坐席实时表现</h3>
            <span class="panel-hint">在岗优先 · 按当前负载升序 · 最多 20 行</span>
          </div>
          <el-table
            :data="data?.agents || []"
            class="screen-table"
            height="320"
            empty-text="暂无在岗坐席"
          >
            <el-table-column prop="agentName" label="坐席" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.agentName || `坐席 ${row.agentId}` }}</template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <span class="state" :class="statusTone(row.onlineStatus)">{{ statusText(row.onlineStatus) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="负载" width="96" align="right">
              <template #default="{ row }">
                <span class="num">{{ row.currentCount ?? 0 }} / {{ row.maxConcurrent ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="今日接入" width="96" align="right">
              <template #default="{ row }"><span class="num">{{ row.todayServed ?? 0 }}</span></template>
            </el-table-column>
            <el-table-column label="平均等待" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ formatWait(row.avgAcceptSeconds) }}</span></template>
            </el-table-column>
            <el-table-column label="平均首响" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ formatWait(row.avgFirstReplySeconds) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 近 12 小时趋势 -->
        <div v-if="isVisible('TREND')" class="panel panel--trend" :style="orderStyle('TREND')">
          <div class="panel-head">
            <h3>近 12 小时趋势</h3>
            <span class="panel-hint">深色：会话量 · 浅色：消息量</span>
          </div>
          <div v-if="hourly.length" class="hour-chart">
            <div v-for="(point, index) in hourly" :key="index" class="hour-col">
              <div class="hour-bars">
                <span
                  class="hour-bar hour-bar--session"
                  :style="{ height: barHeight(point.sessions, hourlyMax) }"
                  :title="`${point.hour} 会话 ${point.sessions}`"
                />
                <span
                  class="hour-bar hour-bar--message"
                  :style="{ height: barHeight(point.messages, hourlyMax) }"
                  :title="`${point.hour} 消息 ${point.messages}`"
                />
              </div>
              <span class="hour-label">{{ point.hour }}</span>
            </div>
          </div>
          <p v-else class="panel-empty">暂无趋势数据</p>
        </div>

        <!-- 预警 -->
        <div v-if="isVisible('ALERTS')" class="panel panel--alerts" :style="orderStyle('ALERTS')">
          <div class="panel-head">
            <h3>实时预警</h3>
            <span class="panel-hint">内置阈值 · 可在配置中调整</span>
          </div>
          <ul v-if="alerts.length" class="alert-list">
            <li v-for="(item, index) in alerts" :key="index" class="alert-item" :class="item.level.toLowerCase()">
              <span class="alert-level">{{ alertLevelText(item.level) }}</span>
              <div class="alert-body">
                <div class="alert-title">{{ item.title }}</div>
                <div class="alert-detail">{{ item.detail }}</div>
              </div>
            </li>
          </ul>
          <p v-else class="panel-empty panel-empty--ok">当前无预警，各项指标正常</p>
        </div>
      </div>

      <footer class="screen-foot">
        <span>数据来自业务库实时统计，口径与统计页一致</span>
        <span>最后刷新 {{ updatedAt || '—' }}</span>
      </footer>
    </section>

    <el-drawer v-model="layoutVisible" title="大屏布局配置" size="440px" append-to-body>
      <p class="layout-hint">
        只配置「怎么摆」，不改变指标口径：组件按顺序号从小到大排列，隐藏的组件不参与渲染；轮询间隔仅在 WebSocket 不可用时生效。
      </p>
      <el-form label-position="top">
        <el-form-item label="布局名称">
          <el-input v-model="layoutForm.layoutName" maxlength="64" placeholder="如：客服中心大屏" />
        </el-form-item>
        <el-form-item label="展示密度">
          <el-radio-group v-model="layoutForm.density">
            <el-radio-button v-for="item in SCREEN_DENSITY_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="轮询刷新间隔">
          <el-select v-model="layoutForm.refreshSeconds" class="w-full">
            <el-option v-for="item in SCREEN_REFRESH_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="组件显隐与顺序">
          <div class="widget-list">
            <div v-for="widget in layoutForm.widgets" :key="widget.code" class="widget-row">
              <div class="widget-meta">
                <span class="widget-name">{{ widget.name }}</span>
                <span class="widget-desc">{{ widget.description }}</span>
              </div>
              <el-input-number v-model="widget.order" :min="1" :max="99" size="small" controls-position="right" />
              <el-switch v-model="widget.visible" />
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-foot">
          <el-button :loading="layoutResetting" @click="resetLayout">恢复默认</el-button>
          <div class="grow" />
          <el-button @click="layoutVisible = false">取消</el-button>
          <el-button type="primary" :loading="layoutSaving" @click="submitLayout">保存布局</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bell,
  ChatDotRound,
  FullScreen,
  Headset,
  Refresh,
  SetUp,
  TrendCharts,
  Timer,
  UserFilled
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getOpsScreen, getScreenLayout, resetScreenLayout, saveScreenLayout } from '@/api'
import { getToken } from '@/api/http'
import {
  SCREEN_DENSITY_OPTIONS,
  SCREEN_REFRESH_OPTIONS,
  formatRating,
  formatWait
} from '@/utils/dict'

const PUSH_INTERVAL_LABEL = '5 秒'

const data = ref(null)
const loading = ref(false)
const updatedAt = ref('')
const wsState = ref('connecting')
const fullscreen = ref(false)
const screenRef = ref(null)

/* ---------------- 布局配置（S6-C，DB-07） ---------------- */
const layoutVisible = ref(false)
const layoutSaving = ref(false)
const layoutResetting = ref(false)
const layout = ref(null)
const layoutForm = reactive({ layoutName: '默认大屏', density: 'COMFORTABLE', refreshSeconds: 15, widgets: [] })

const isCompact = computed(() => layout.value?.density === 'COMPACT')

function widgetOf(code) {
  const widgets = layout.value?.widgets
  if (!Array.isArray(widgets)) return null
  return widgets.find((item) => item.code === code) || null
}

const isVisible = (code) => widgetOf(code)?.visible !== false
const orderStyle = (code) => ({ order: Number(widgetOf(code)?.order ?? 0) || 0 })

function applyLayout(data) {
  layout.value = data || null
  // 配置变更后重建轮询定时器，让新的刷新间隔立即生效
  stopPolling()
  startPolling()
}

async function loadLayout() {
  try {
    applyLayout(await getScreenLayout())
  } catch {
    // 布局加载失败时保持默认全量展示，不影响大屏可用性
  }
}

function openLayout() {
  const current = layout.value
  layoutForm.layoutName = current?.layoutName || '默认大屏'
  layoutForm.density = current?.density || 'COMFORTABLE'
  layoutForm.refreshSeconds = Number(current?.refreshSeconds ?? 15)
  layoutForm.widgets = (current?.widgets || []).map((item) => ({
    code: item.code,
    name: item.name,
    description: item.description,
    visible: item.visible !== false,
    order: Number(item.order ?? 1)
  }))
  layoutVisible.value = true
}

async function submitLayout() {
  layoutSaving.value = true
  try {
    const payload = {
      layoutName: layoutForm.layoutName.trim() || null,
      density: layoutForm.density,
      refreshSeconds: Number(layoutForm.refreshSeconds),
      widgets: layoutForm.widgets.map((item) => ({
        code: item.code,
        visible: Boolean(item.visible),
        order: Number(item.order) || 1
      }))
    }
    applyLayout(await saveScreenLayout(payload))
    layoutVisible.value = false
    ElMessage.success('大屏布局已保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '布局保存失败，请稍后重试')
  } finally {
    layoutSaving.value = false
  }
}

async function resetLayout() {
  layoutResetting.value = true
  try {
    applyLayout(await resetScreenLayout())
    layoutVisible.value = false
    ElMessage.success('已恢复默认布局')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '重置失败，请稍后重试')
  } finally {
    layoutResetting.value = false
  }
}

const num = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '—'
}

const percentOf = (value) => {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return `${(n * 100).toFixed(1)}%`
}

const realtime = computed(() => data.value?.realtime || {})
const today = computed(() => data.value?.today || {})
const hourly = computed(() => (Array.isArray(data.value?.hourly) ? data.value.hourly : []))
const alerts = computed(() => (Array.isArray(data.value?.alerts) ? data.value.alerts : []))

const hourlyMax = computed(() => {
  let max = 0
  for (const point of hourly.value) {
    max = Math.max(max, Number(point.sessions) || 0, Number(point.messages) || 0)
  }
  return max || 1
})

const barHeight = (value, max) => {
  const n = Number(value) || 0
  const ratio = max > 0 ? n / max : 0
  return `${Math.max(n > 0 ? 4 : 0, Math.round(ratio * 100))}%`
}

const liveTiles = computed(() => {
  const rt = realtime.value
  return [
    {
      key: 'waiting',
      label: '排队人数',
      icon: UserFilled,
      value: num(rt.waitingCount),
      note: '等待接入人工的访客',
      tone: (rt.waitingCount || 0) > 0 ? 'warn' : 'ok'
    },
    {
      key: 'waitSeconds',
      label: '最长排队时长',
      icon: Timer,
      value: formatWait(rt.longestWaitSeconds),
      note: `平均等待 ${formatWait(rt.avgWaitSeconds)}`,
      tone: (rt.longestWaitSeconds || 0) >= 300 ? 'danger' : 'ok'
    },
    {
      key: 'online',
      label: '在线坐席',
      icon: Headset,
      value: `${num(rt.onlineAgents)} / ${num((rt.onlineAgents || 0) + (rt.busyAgents || 0))}`,
      note: `忙碌 ${num(rt.busyAgents)} 人`,
      tone: 'ok'
    },
    {
      key: 'human',
      label: '人工接待中',
      icon: ChatDotRound,
      value: num(rt.servingSessions),
      note: '当前正在人工服务的会话',
      tone: 'ok'
    },
    {
      key: 'ai',
      label: '机器人接待中',
      icon: TrendCharts,
      value: num(rt.aiSessions),
      note: 'AI 应答中的会话',
      tone: 'ok'
    }
  ]
})

const todayItems = computed(() => {
  const t = today.value
  return [
    { label: '今日会话', value: num(t.sessions) },
    { label: '人工接待', value: num(t.humanSessions) },
    { label: '已关闭', value: num(t.closedSessions) },
    {
      label: 'AI 自主解决率',
      value: percentOf(t.aiResolveRate),
      note: `独立解决 ${num(t.aiResolved)}`
    },
    { label: '首响均值', value: formatWait(t.avgFirstResponseSeconds) },
    {
      label: '3 分钟回复率',
      value: percentOf(t.threeMinuteReplyRate),
      note: `${num(t.withinCount)} / ${num(t.roundCount)} 轮`
    },
    { label: '消息量', value: num(t.messages) },
    { label: '新建工单', value: num(t.orders), note: `待处理 ${num(t.pendingOrders)}` },
    { label: '超时工单', value: num(t.overdueOrders) },
    {
      label: '满意度',
      value: formatRating(t.avgRating),
      note: `回收率 ${percentOf(t.recoveryRate)}`
    }
  ]
})

const wsLabel = computed(() => {
  if (wsState.value === 'open') return `WS 推送中 ${PUSH_INTERVAL_LABEL}`
  if (wsState.value === 'connecting') return 'WS 连接中'
  return 'HTTP 轮询中'
})

const statusText = (status) => {
  if (status === 'ONLINE') return '在线'
  if (status === 'BUSY') return '忙碌'
  if (status === 'AWAY') return '离开'
  return '离线'
}

const statusTone = (status) => {
  if (status === 'ONLINE') return 'ok'
  if (status === 'BUSY') return 'warn'
  if (status === 'AWAY') return 'muted'
  return 'off'
}

const alertLevelText = (level) => {
  if (level === 'CRITICAL') return '严重'
  if (level === 'WARN') return '警告'
  return '提示'
}

/* ---------------- 数据加载 ---------------- */
async function loadScreen() {
  loading.value = true
  try {
    data.value = await getOpsScreen()
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    // 首屏失败时保留上一次快照，避免大屏闪空
  } finally {
    loading.value = false
  }
}

function refresh() {
  loadScreen()
}

/* ---------------- WebSocket 订阅 ---------------- */
let socket = null
let reconnectTimer = null
let pollTimer = null
let pingTimer = null
let closing = false

function wsUrl() {
  const token = getToken()
  const base = import.meta.env.VITE_WS_BASE
  if (base) {
    return `${base.replace(/\/$/, '')}/ws/chat?token=${encodeURIComponent(token)}`
  }
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws/chat?token=${encodeURIComponent(token)}`
}

function send(type) {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  socket.send(
    JSON.stringify({ type, requestId: `${type.toLowerCase()}-${Date.now()}`, timestamp: Date.now(), data: {} })
  )
}

function stopTimers() {
  if (pingTimer) window.clearInterval(pingTimer)
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  pingTimer = null
  reconnectTimer = null
}

function startPolling() {
  if (pollTimer) return
  // WS 不可用时退化为轮询，间隔取自布局配置（默认 15 秒）
  const interval = Math.max(5, Number(layout.value?.refreshSeconds ?? 15) || 15) * 1000
  pollTimer = window.setInterval(() => {
    if (wsState.value !== 'open') loadScreen()
  }, interval)
}

function stopPolling() {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = null
}

function onMessage(event) {
  let payload
  try {
    payload = JSON.parse(event.data)
  } catch {
    return
  }
  if (!payload || !payload.type) return
  if (payload.type === 'SCREEN_SUBSCRIBED' || payload.type === 'SCREEN_UPDATE') {
    data.value = payload.data || data.value
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    return
  }
  if (payload.type === 'ERROR') {
    const message = payload.data?.message
    if (message) ElMessage.warning(message)
  }
}

function connectWs() {
  if (!getToken()) {
    wsState.value = 'closed'
    startPolling()
    return
  }
  try {
    socket = new WebSocket(wsUrl())
  } catch {
    wsState.value = 'closed'
    startPolling()
    return
  }
  wsState.value = 'connecting'
  socket.onopen = () => {
    wsState.value = 'open'
    stopPolling()
    send('SCREEN_SUBSCRIBE')
    pingTimer = window.setInterval(() => send('PING'), 20000)
  }
  socket.onmessage = onMessage
  socket.onerror = () => {
    wsState.value = 'closed'
  }
  socket.onclose = () => {
    stopTimers()
    if (closing) return
    wsState.value = 'closed'
    startPolling()
    reconnectTimer = window.setTimeout(connectWs, 5000)
  }
}

/* ---------------- 全屏 ---------------- */
function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
  document.body.style.overflow = fullscreen.value ? 'hidden' : ''
}

function onKeydown(event) {
  if (event.key === 'Escape' && fullscreen.value) toggleFullscreen()
}

onMounted(() => {
  loadLayout()
  loadScreen()
  connectWs()
  startPolling()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  closing = true
  send('SCREEN_UNSUBSCRIBE')
  stopTimers()
  stopPolling()
  window.removeEventListener('keydown', onKeydown)
  if (socket) {
    try {
      socket.close(1000, 'leave screen')
    } catch {
      /* 忽略关闭异常 */
    }
  }
  document.body.style.overflow = ''
})
</script>

<style scoped>
.screen {
  --s-bg: #0a1120;
  --s-panel: rgba(255, 255, 255, 0.045);
  --s-panel-strong: rgba(255, 255, 255, 0.08);
  --s-border: rgba(255, 255, 255, 0.09);
  --s-text: #e9effd;
  --s-muted: #8fa3c6;
  --s-accent: #4fd1c5;
  --s-accent-2: #7c8cf8;
  --s-warn: #f6c66a;
  --s-danger: #f8806f;

  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  padding: 18px 18px 14px;
  color: var(--s-text);
  background:
    radial-gradient(900px 420px at 12% -12%, rgba(79, 209, 197, 0.16), transparent 62%),
    radial-gradient(760px 420px at 100% -6%, rgba(124, 140, 248, 0.2), transparent 58%),
    var(--s-bg);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: 0 24px 60px -32px rgba(9, 15, 30, 0.9);
  overflow: hidden;
}

.screen.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  border-radius: 0;
  padding: 22px 26px 18px;
  overflow: auto;
}

/* ---------------- 头部 ---------------- */
.screen-head {
  order: -1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.screen-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pulse {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--s-accent);
  box-shadow: 0 0 0 0 rgba(79, 209, 197, 0.55);
  animation: pulse 2.4s var(--zk-ease-out, ease-out) infinite;
}

.screen-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0.01em;
}

.screen-title p {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: var(--s-muted);
}

.screen-meta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.screen-time {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--s-muted);
}

/* ---------------- 实时负载 ---------------- */
.live-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.live-tile {
  padding: 13px 14px;
  border-radius: 14px;
  background: var(--s-panel);
  border: 1px solid var(--s-border);
}

.live-tile.warn {
  border-color: rgba(246, 198, 106, 0.42);
  background: rgba(246, 198, 106, 0.08);
}

.live-tile.danger {
  border-color: rgba(248, 128, 111, 0.45);
  background: rgba(248, 128, 111, 0.09);
}

.tile-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--s-muted);
}

.tile-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 660;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.live-tile.danger .tile-value {
  color: var(--s-danger);
}

.live-tile.warn .tile-value {
  color: var(--s-warn);
}

.tile-note {
  margin-top: 6px;
  font-size: 11px;
  color: var(--s-muted);
}

/* ---------------- 今日累计 ---------------- */
.today-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 1px;
  padding: 12px 6px;
  border-radius: 14px;
  background: var(--s-panel);
  border: 1px solid var(--s-border);
  margin-bottom: 12px;
}

.today-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.07);
}

.today-item:nth-child(5n + 1) {
  border-left: 0;
}

.today-label {
  font-size: 11.5px;
  color: var(--s-muted);
}

.today-value {
  font-size: 17px;
  font-weight: 620;
  font-variant-numeric: tabular-nums;
}

.today-note {
  font-size: 10.5px;
  color: var(--s-muted);
}

/* ---------------- 区块栅格 ---------------- */
/* 不加命名栅格：让组件按 order 自动流式排列，布局配置里的顺序号才能真正生效 */
.screen-body {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.panel {
  border-radius: 14px;
  background: var(--s-panel);
  border: 1px solid var(--s-border);
  padding: 12px 14px 14px;
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 620;
}

.panel-hint {
  font-size: 11px;
  color: var(--s-muted);
}

.panel-empty {
  margin: 18px 0;
  font-size: 12px;
  color: var(--s-muted);
  text-align: center;
}

.panel-empty--ok {
  color: rgba(79, 209, 197, 0.86);
}

/* ---------------- 坐席表 ---------------- */
.screen-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-border-color: rgba(255, 255, 255, 0.07);
  --el-table-text-color: var(--s-text);
  --el-table-header-text-color: var(--s-muted);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
  background: transparent;
  font-size: 12px;
}

.screen-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.screen-table :deep(th.el-table__cell) {
  font-weight: 550;
  font-size: 11.5px;
}

.screen-table :deep(.el-table__empty-text) {
  color: var(--s-muted);
}

.num {
  font-variant-numeric: tabular-nums;
}

.state {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  border: 1px solid transparent;
}

.state.ok {
  color: rgba(79, 209, 197, 0.95);
  background: rgba(79, 209, 197, 0.12);
  border-color: rgba(79, 209, 197, 0.28);
}

.state.warn {
  color: var(--s-warn);
  background: rgba(246, 198, 106, 0.12);
  border-color: rgba(246, 198, 106, 0.28);
}

.state.muted {
  color: var(--s-muted);
  background: rgba(255, 255, 255, 0.06);
}

.state.off {
  color: rgba(143, 163, 198, 0.7);
  background: rgba(255, 255, 255, 0.04);
}

/* ---------------- 小时趋势 ---------------- */
.hour-chart {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 6px;
  align-items: end;
  height: 128px;
}

.hour-col {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 6px;
  height: 100%;
}

.hour-bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  height: 100%;
}

.hour-bar {
  width: 6px;
  min-height: 2px;
  border-radius: 3px 3px 0 0;
  transition: height var(--zk-dur-base, 240ms) var(--zk-ease-out, ease-out);
}

.hour-bar--session {
  background: linear-gradient(180deg, var(--s-accent), rgba(79, 209, 197, 0.35));
}

.hour-bar--message {
  background: linear-gradient(180deg, var(--s-accent-2), rgba(124, 140, 248, 0.28));
}

.hour-label {
  font-size: 10px;
  text-align: center;
  color: var(--s-muted);
  font-variant-numeric: tabular-nums;
}

/* ---------------- 预警 ---------------- */
.alert-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.alert-item {
  display: flex;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 10px;
  background: var(--s-panel-strong);
  border: 1px solid var(--s-border);
}

.alert-item.warn {
  border-color: rgba(246, 198, 106, 0.32);
  background: rgba(246, 198, 106, 0.08);
}

.alert-item.critical {
  border-color: rgba(248, 128, 111, 0.36);
  background: rgba(248, 128, 111, 0.09);
}

.alert-level {
  flex: 0 0 auto;
  height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 10.5px;
  line-height: 18px;
  color: var(--s-muted);
  background: rgba(255, 255, 255, 0.08);
}

.alert-item.warn .alert-level {
  color: var(--s-warn);
}

.alert-item.critical .alert-level {
  color: var(--s-danger);
}

.alert-title {
  font-size: 12px;
  font-weight: 580;
}

.alert-detail {
  margin-top: 2px;
  font-size: 11px;
  color: var(--s-muted);
}

/* ---------------- 底部 ---------------- */
.screen-foot {
  order: 999;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 11px;
  color: var(--s-muted);
  font-variant-numeric: tabular-nums;
}

/* ---------------- 紧凑密度 ---------------- */
.density-compact {
  padding: 14px 14px 10px;
}

.density-compact .live-grid,
.density-compact .today-strip {
  margin-bottom: 9px;
}

.density-compact .live-tile {
  padding: 10px 12px;
}

.density-compact .tile-value {
  margin-top: 6px;
  font-size: 23px;
}

.density-compact .today-strip {
  padding: 8px 4px;
}

.density-compact .today-item {
  padding: 4px 10px;
}

.density-compact .today-value {
  font-size: 15.5px;
}

.density-compact .panel {
  padding: 9px 11px 10px;
}

.density-compact .panel-head {
  margin-bottom: 8px;
}

/* ---------------- 布局配置抽屉 ---------------- */
.layout-hint {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--zk-text-2);
}

.widget-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.widget-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out);
}

.widget-row:hover {
  border-color: var(--zk-border-strong);
}

.widget-meta {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.widget-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.widget-desc {
  font-size: 11px;
  line-height: 1.5;
  color: var(--zk-text-3);
}

.widget-row :deep(.el-input-number) {
  width: 92px;
}

.drawer-foot {
  display: flex;
  align-items: center;
  gap: 8px;
}

.w-full {
  width: 100%;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(79, 209, 197, 0.5);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(79, 209, 197, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(79, 209, 197, 0);
  }
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1280px) {
  .live-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .today-strip {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .today-item:nth-child(5n + 1) {
    border-left: 1px solid rgba(255, 255, 255, 0.07);
  }

  .today-item:nth-child(4n + 1) {
    border-left: 0;
  }
}

@media (max-width: 980px) {
  .screen-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .live-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .today-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .today-item:nth-child(4n + 1) {
    border-left: 1px solid rgba(255, 255, 255, 0.07);
  }

  .today-item:nth-child(2n + 1) {
    border-left: 0;
  }

  .tile-value {
    font-size: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pulse {
    animation: none;
  }

  .hour-bar {
    transition: none;
  }
}
</style>
