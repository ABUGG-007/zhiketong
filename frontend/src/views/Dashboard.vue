<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="OVERVIEW"
      title="数据看板"
      description="今日服务概况、近 7 日会话与工单趋势，以及坐席实时负载。数据来自 Spring Boot 只读演示接口。"
      badge="演示数据"
    >
      <template #actions>
        <div class="health-chip" :class="healthTone" role="status" :title="`MySQL ${health.mysql} · Redis ${health.redis}`">
          <i class="dot" aria-hidden="true" />
          <span class="health-item">MySQL {{ health.mysql }}</span>
          <span class="health-sep" aria-hidden="true">·</span>
          <span class="health-item">Redis {{ health.redis }}</span>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="error"
      class="dash-alert"
      type="warning"
      show-icon
      :closable="false"
      title="看板数据接口暂不可用"
    >
      <template #default>
        <div class="alert-body">
          <span>{{ error }}</span>
          <el-button link type="primary" @click="loadAll">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <!-- 指标卡 -->
    <section class="stat-grid">
      <article v-for="card in statCards" :key="card.key" class="stat-card" :class="card.tone">
        <div class="stat-top">
          <span class="stat-icon">
            <el-icon><component :is="card.icon" /></el-icon>
          </span>
          <span class="stat-label">{{ card.label }}</span>
        </div>
        <div class="stat-value">
          <span v-if="loaded" class="zk-num">{{ card.value }}</span>
          <span v-else class="sk-bar" />
        </div>
        <div class="stat-caption">{{ card.caption }}</div>
      </article>
    </section>

    <!-- 趋势 + 坐席 -->
    <section class="dash-row dash-row--split">
      <div class="zk-card trend-card">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">近 7 日会话 / 工单趋势</div>
            <div class="zk-card-desc">按天统计，含 AI 接待与人工接待会话</div>
          </div>
          <div class="trend-side">
            <div class="legend">
              <span class="legend-item"><i class="swatch swatch--sessions" />会话</span>
              <span class="legend-item"><i class="swatch swatch--orders" />工单</span>
            </div>
            <div class="trend-totals">
              合计 <b>{{ trendTotals.sessions }}</b> 会话 / <b>{{ trendTotals.orders }}</b> 工单
            </div>
          </div>
        </div>

        <div v-if="trend.length" class="trend-body">
          <div class="trend-axis">
            <span
              v-for="(label, index) in axisLabels"
              :key="label + index"
              class="axis-label"
              :style="{ top: `${(index / 4) * 100}%` }"
            >
              {{ label }}
            </span>
          </div>
          <div class="trend-main">
            <div class="trend-plot" role="img" :aria-label="trendSummary">
              <span v-for="n in 5" :key="n" class="grid-line" :style="{ top: `${((n - 1) / 4) * 100}%` }" />
              <div class="trend-cols">
                <div v-for="(day, index) in trend" :key="day.date || index" class="trend-col">
                  <div class="trend-bars">
                    <div
                      class="bar bar--sessions"
                      :style="{ height: barHeight(day.sessions) }"
                      :title="`${day.date} 会话 ${num(day.sessions)} 个`"
                    />
                    <div
                      class="bar bar--orders"
                      :style="{ height: barHeight(day.orders) }"
                      :title="`${day.date} 工单 ${num(day.orders)} 单`"
                    />
                  </div>
                </div>
              </div>
            </div>
            <div class="trend-dates">
              <span v-for="(day, index) in trend" :key="'d' + index" class="trend-date">
                {{ shortDate(day.date) }}
              </span>
            </div>
          </div>
        </div>
        <div v-else class="zk-empty">
          <span v-if="loading">加载趋势数据…</span>
          <span v-else>暂无趋势数据（等待 /api/demo/overview 返回 trend）</span>
        </div>

        <div class="trend-foot">
          <div class="mini-stat">
            <span class="mini-label">平均会话评分</span>
            <span class="mini-value zk-num">{{ formatRating(ratings.avgSessionRating) }}</span>
          </div>
          <div class="mini-stat">
            <span class="mini-label">平均工单评分</span>
            <span class="mini-value zk-num">{{ formatRating(ratings.avgOrderRating) }}</span>
          </div>
          <div class="mini-stat">
            <span class="mini-label">今日消息量</span>
            <span class="mini-value zk-num">{{ num(counts.messagesToday) }}</span>
          </div>
          <div class="mini-stat">
            <span class="mini-label">知识库分类</span>
            <span class="mini-value zk-num">{{ num(counts.categories) }}</span>
          </div>
        </div>
      </div>

      <div class="zk-card agent-card">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">坐席状态</div>
            <div class="zk-card-desc">
              在线 {{ onlineAgents }} / 共 {{ agents.length }} 位 · 并发负载
            </div>
          </div>
          <span class="agent-count">{{ onlineAgents }} 在线</span>
        </div>

        <div v-if="agents.length" class="agent-list">
          <div v-for="agent in agents" :key="agent.agentId" class="agent-row">
            <div class="agent-avatar" :class="agentTone(agent.onlineStatus)">
              {{ (agent.nickname || '坐').slice(0, 1) }}
            </div>
            <div class="agent-main">
              <div class="agent-line">
                <span class="agent-name">{{ agent.nickname || `坐席 ${agent.agentId}` }}</span>
                <StatusPill
                  :label="dictLabel(AGENT_STATUS_DICT, agent.onlineStatus)"
                  :tone="dictTone(AGENT_STATUS_DICT, agent.onlineStatus)"
                  dot
                />
              </div>
              <div class="agent-sub">
                负载 {{ num(agent.currentCount) }}/{{ num(agent.maxConcurrent) }} · 累计接待
                {{ num(agent.totalServed) }}
              </div>
              <div class="load-track">
                <span
                  class="load-fill"
                  :class="loadTone(agent.currentCount, agent.maxConcurrent)"
                  :style="{ width: `${percent(agent.currentCount, agent.maxConcurrent)}%` }"
                />
              </div>
            </div>
          </div>
        </div>
        <div v-else class="zk-empty">
          <span v-if="loading">加载坐席数据…</span>
          <span v-else>暂无坐席在线数据</span>
        </div>
      </div>
    </section>

    <!-- 最近工单 / 最近会话 -->
    <section class="dash-row dash-row--tables">
      <div class="zk-card">
        <div class="zk-card-head">
          <div class="zk-card-title">最近工单</div>
          <router-link class="more-link" to="/orders" aria-label="查看全部工单">查看全部 →</router-link>
        </div>
        <el-table
          :data="recentOrders"
          class="dash-table"
          size="default"
          :show-header="true"
          empty-text="暂无工单数据"
        >
          <el-table-column prop="orderNo" label="工单号" width="148">
            <template #default="{ row }">
              <span class="order-no" :title="row.orderNo">{{ row.orderNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
          <el-table-column prop="priority" label="优先级" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(PRIORITY_DICT, row.priority)"
                :tone="dictTone(PRIORITY_DICT, row.priority)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(ORDER_STATUS_DICT, row.status)"
                :tone="dictTone(ORDER_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="112">
            <template #default="{ row }">
              <span class="cell-time">{{ shortTime(row.createTime) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="zk-card">
        <div class="zk-card-head">
          <div class="zk-card-title">最近会话</div>
          <router-link class="more-link" to="/sessions" aria-label="查看全部会话">查看全部 →</router-link>
        </div>
        <el-table :data="recentSessions" class="dash-table" empty-text="暂无会话数据">
          <el-table-column prop="sessionNo" label="会话号" width="136">
            <template #default="{ row }">
              <span class="order-no" :title="row.sessionNo">{{ row.sessionNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="userName" label="用户" width="76" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="98">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                :tone="dictTone(SESSION_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="rating" label="评分" width="66" align="center">
            <template #default="{ row }">
              <span class="cell-rating" :class="{ muted: !row.rating }">
                {{ row.rating ? `${row.rating} ★` : '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="messageCount" label="消息" width="60" align="right">
            <template #default="{ row }">
              <span class="zk-num cell-muted">{{ num(row.messageCount) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <div class="dash-footnote">
      <span class="footnote-dot" />
      演示数据 · 后续里程碑完善（当前接口：<code>/api/demo/overview</code>）
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  Bell,
  ChatDotRound,
  Collection,
  Headset,
  Tickets,
  WarningFilled
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getHealth, getOverview } from '@/api'
import {
  AGENT_STATUS_DICT,
  ORDER_STATUS_DICT,
  PRIORITY_DICT,
  SESSION_STATUS_DICT,
  dictLabel,
  dictTone,
  formatRating,
  percent,
  shortDate
} from '@/utils/dict'

const overview = ref(null)
const health = ref({ mysql: '检测中', redis: '检测中', status: 'loading' })
const loading = ref(false)
const error = ref('')

const loaded = computed(() => Boolean(overview.value))
const counts = computed(() => overview.value?.counts || {})
const ratings = computed(() => overview.value?.ratings || {})
const trend = computed(() => (Array.isArray(overview.value?.trend) ? overview.value.trend : []))
const agents = computed(() => (Array.isArray(overview.value?.agents) ? overview.value.agents : []))
const recentOrders = computed(() =>
  Array.isArray(overview.value?.recentOrders) ? overview.value.recentOrders : []
)
const recentSessions = computed(() =>
  Array.isArray(overview.value?.recentSessions) ? overview.value.recentSessions : []
)

const num = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '—'
}

const onlineAgents = computed(
  () => agents.value.filter((a) => a.onlineStatus === 'ONLINE' || a.onlineStatus === 'BUSY').length
)

const statCards = computed(() => {
  const c = counts.value
  return [
    {
      key: 'sessions',
      label: '今日会话',
      value: num(c.sessionsToday),
      caption: '今日新建会话',
      icon: ChatDotRound,
      tone: 'teal'
    },
    {
      key: 'orders',
      label: '今日工单',
      value: num(c.ordersToday),
      caption: '今日新建工单',
      icon: Tickets,
      tone: 'indigo'
    },
    {
      key: 'waiting',
      label: '待接入',
      value: num(c.waitingSessions),
      caption: '排队等待人工接入',
      icon: Bell,
      tone: 'amber'
    },
    {
      key: 'overdue',
      label: '超时工单',
      value: num(c.overdueOrders),
      caption: 'SLA 已超时未解决',
      icon: WarningFilled,
      tone: 'red'
    },
    {
      key: 'online',
      label: '在线坐席',
      value: num(c.onlineAgents),
      caption: '当前在线 / 忙碌',
      icon: Headset,
      tone: 'green'
    },
    {
      key: 'knowledge',
      label: '知识条目',
      value: num(c.knowledge),
      caption: `${num(c.categories)} 个分类 · 共 ${num(c.users)} 位用户`,
      icon: Collection,
      tone: 'slate'
    }
  ]
})

const trendTotals = computed(() => {
  const sessions = trend.value.reduce((sum, item) => sum + (Number(item.sessions) || 0), 0)
  const orders = trend.value.reduce((sum, item) => sum + (Number(item.orders) || 0), 0)
  return { sessions, orders }
})

const trendSummary = computed(() => {
  if (!trend.value.length) return '暂无近 7 日趋势数据'
  return `近 7 日趋势：会话合计 ${trendTotals.value.sessions} 个，工单合计 ${trendTotals.value.orders} 个。`
})

function niceStep(raw) {
  if (!Number.isFinite(raw) || raw <= 1) return 1
  const power = Math.pow(10, Math.floor(Math.log10(raw)))
  const base = raw / power
  let multiplier = 10
  if (base <= 1) multiplier = 1
  else if (base <= 2) multiplier = 2
  else if (base <= 5) multiplier = 5
  return Math.max(1, multiplier * power)
}

const chartStep = computed(() => {
  const values = trend.value.flatMap((item) => [Number(item.sessions) || 0, Number(item.orders) || 0])
  const rawMax = Math.max(1, ...values, 1)
  return niceStep(rawMax / 4)
})
const chartMax = computed(() => chartStep.value * 4)
const axisLabels = computed(() =>
  [4, 3, 2, 1, 0].map((factor) => Math.round(chartStep.value * factor))
)

function barHeight(value) {
  const v = Number(value) || 0
  if (!chartMax.value) return '1.5%'
  const pct = (v / chartMax.value) * 100
  return `${Math.max(v > 0 ? 3 : 1.2, Math.min(100, pct))}%`
}

const agentTone = (status) =>
  status === 'ONLINE' ? 'teal' : status === 'BUSY' ? 'amber' : 'slate'

const loadTone = (current, max) => {
  const pct = percent(current, max)
  if (pct >= 90) return 'hot'
  if (pct >= 60) return 'warm'
  return 'calm'
}

const healthTone = computed(() => {
  if (health.value.status === 'UP') return 'teal'
  if (health.value.status === 'loading') return 'slate'
  if (health.value.mysql === 'UP' || health.value.redis === 'UP') return 'amber'
  return 'red'
})

const shortTime = (value) => {
  if (!value) return '—'
  const text = String(value).replace('T', ' ')
  const match = text.match(/(\d{2}:\d{2})/)
  return match ? match[1] : text.slice(5, 16)
}

async function loadHealth() {
  try {
    const data = await getHealth()
    health.value = {
      mysql: data?.checks?.mysql?.status || 'UNKNOWN',
      redis: data?.checks?.redis?.status || 'UNKNOWN',
      status: data?.status || 'UNKNOWN'
    }
  } catch {
    health.value = { mysql: 'DOWN', redis: 'DOWN', status: 'DOWN' }
  }
}

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    const data = await getOverview()
    overview.value = data || null
  } catch (err) {
    error.value =
      err?.response?.status === 404
        ? 'GET /api/demo/overview 返回 404 —— 后端演示接口尚未部署，指标卡与图表将在接口就绪后自动填充。'
        : err?.message || '请求失败，请确认后端 8080 正常运行'
    overview.value = null
  } finally {
    loading.value = false
  }
}

function loadAll() {
  loadHealth()
  loadOverview()
}

onMounted(loadAll)
</script>

<style scoped>
/* ---------- 健康徽标 ---------- */
.health-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
  font-variant-numeric: tabular-nums;
}

.health-chip.teal {
  color: #0f766e;
  background: #edfdf9;
  border-color: #b9f0e6;
}
.health-chip.amber {
  color: #b45309;
  background: #fff8ea;
  border-color: #fbe0b0;
}
.health-chip.red {
  color: #be123c;
  background: #fff1f4;
  border-color: #f9cdd8;
}
.health-chip.slate {
  color: #64748b;
  background: #f4f6fa;
  border-color: #e2e8f0;
}

.health-chip .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.health-sep {
  opacity: 0.45;
}

.dash-alert {
  margin-bottom: 16px;
  border-radius: var(--zk-radius);
}

.alert-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  font-size: 13px;
}

/* ---------- 指标卡 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  position: relative;
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 16px 16px 14px;
  box-shadow: var(--zk-shadow-sm);
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -28px;
  top: -32px;
  width: 86px;
  height: 86px;
  border-radius: 50%;
  background: var(--tone-soft, #f1f5f9);
  opacity: 0.75;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--zk-shadow-md);
  border-color: var(--tone-border, #e2e8f0);
}

.stat-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-icon {
  width: 28px;
  height: 28px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-size: 15px;
  color: var(--tone, #475569);
  background: var(--tone-soft, #f1f5f9);
  border: 1px solid var(--tone-border, #e2e8f0);
}

.stat-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text-2);
  letter-spacing: 0.2px;
}

.stat-value {
  position: relative;
  z-index: 1;
  margin-top: 12px;
  font-size: 27px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.5px;
  color: var(--zk-text);
  min-height: 31px;
  display: flex;
  align-items: center;
}

.stat-caption {
  position: relative;
  z-index: 1;
  margin-top: 6px;
  font-size: 11px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-card.teal {
  --tone: #0f766e;
  --tone-soft: #e6fbf7;
  --tone-border: #bff0e7;
}
.stat-card.indigo {
  --tone: #4338ca;
  --tone-soft: #eef0ff;
  --tone-border: #d8dafb;
}
.stat-card.amber {
  --tone: #b45309;
  --tone-soft: #fff7e6;
  --tone-border: #fde3b4;
}
.stat-card.red {
  --tone: #be123c;
  --tone-soft: #fff0f3;
  --tone-border: #fbd0da;
}
.stat-card.green {
  --tone: #047857;
  --tone-soft: #e9fbf3;
  --tone-border: #c3f0dd;
}
.stat-card.slate {
  --tone: #475569;
  --tone-soft: #f1f5fa;
  --tone-border: #e2e8f0;
}

/* 骨架 */
.sk-bar {
  display: block;
  width: 62%;
  height: 24px;
  border-radius: 7px;
  background: linear-gradient(90deg, #eef1f7 25%, #f7f9fd 37%, #eef1f7 63%);
  background-size: 400% 100%;
  animation: shimmer 1.3s ease infinite;
}

@keyframes shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* ---------- 行布局 ---------- */
.dash-row {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.dash-row--split {
  grid-template-columns: minmax(0, 1.72fr) minmax(0, 1fr);
}

.dash-row--tables {
  grid-template-columns: minmax(0, 1.42fr) minmax(0, 1fr);
}

/* ---------- 趋势图 ---------- */
.trend-card {
  display: flex;
  flex-direction: column;
}

.trend-side {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.legend {
  display: flex;
  align-items: center;
  gap: 14px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-weight: 500;
}

.swatch {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  display: inline-block;
}

.swatch--sessions {
  background: linear-gradient(180deg, #2dd4bf, #0f9488);
}

.swatch--orders {
  background: linear-gradient(180deg, #818cf8, #4f46e5);
}

.trend-totals {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.trend-totals b {
  color: var(--zk-text);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.trend-body {
  display: flex;
  gap: 8px;
  padding: 6px 20px 0;
  flex: 1;
}

.trend-axis {
  position: relative;
  width: 30px;
  height: 168px;
  flex: 0 0 30px;
}

.axis-label {
  position: absolute;
  right: 0;
  transform: translateY(-50%);
  font-size: 10px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.axis-label:first-child {
  transform: translateY(0);
}

.axis-label:last-child {
  transform: translateY(-100%);
}

.trend-main {
  flex: 1;
  min-width: 0;
}

.trend-plot {
  position: relative;
  height: 168px;
  padding: 0 4px;
}

.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: #eef1f7;
}

.grid-line:last-of-type {
  background: #e2e8f0;
}

.trend-cols {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 6px;
}

.trend-col {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 5px;
  width: 100%;
  height: 100%;
}

.bar {
  width: 13px;
  max-width: 15px;
  border-radius: 4px 4px 2px 2px;
  transition: height 0.5s cubic-bezier(0.22, 1, 0.36, 1), filter 0.15s ease;
  cursor: default;
}

.bar:hover {
  filter: brightness(1.1) saturate(1.1);
}

.bar--sessions {
  background: linear-gradient(180deg, #2dd4bf 0%, #14b8a6 45%, #0f9488 100%);
  box-shadow: 0 6px 14px -10px rgba(15, 148, 136, 0.9);
}

.bar--orders {
  background: linear-gradient(180deg, #a5b4fc 0%, #6366f1 45%, #4f46e5 100%);
  box-shadow: 0 6px 14px -10px rgba(79, 70, 229, 0.9);
}

.trend-dates {
  display: flex;
  justify-content: space-around;
  gap: 6px;
  padding: 8px 4px 0;
}

.trend-date {
  flex: 1;
  text-align: center;
  font-size: 10.5px;
  color: var(--zk-text-3);
  letter-spacing: 0.2px;
}

.trend-foot {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 16px 20px 18px;
  margin-top: 6px;
  border-top: 1px dashed var(--zk-border);
}

.mini-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 12px;
  border-left: 2px solid var(--zk-border);
}

.mini-stat:first-child {
  border-left-color: #99f6e4;
}

.mini-label {
  font-size: 11px;
  color: var(--zk-text-3);
}

.mini-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--zk-text);
}

/* ---------- 坐席 ---------- */
.agent-card {
  display: flex;
  flex-direction: column;
}

.agent-count {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--zk-teal-dark);
  background: var(--zk-teal-soft);
  border: 1px solid #bff0e7;
  border-radius: 999px;
  padding: 3px 9px;
}

.agent-list {
  padding: 4px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.agent-row {
  display: flex;
  gap: 11px;
  align-items: flex-start;
  padding: 11px 8px;
  border-radius: 10px;
  transition: background 0.15s ease;
}

.agent-row:hover {
  background: #f8fafd;
}

.agent-row + .agent-row {
  border-top: 1px solid #f1f5f9;
}

.agent-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
  border: 1px solid;
}

.agent-avatar.teal {
  color: #0f766e;
  background: #e6fbf7;
  border-color: #bff0e7;
}
.agent-avatar.amber {
  color: #b45309;
  background: #fff7e6;
  border-color: #fde3b4;
}
.agent-avatar.slate {
  color: #64748b;
  background: #f1f5fa;
  border-color: #e2e8f0;
}

.agent-main {
  flex: 1;
  min-width: 0;
}

.agent-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.agent-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.agent-sub {
  margin-top: 5px;
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.load-track {
  margin-top: 7px;
  height: 5px;
  border-radius: 999px;
  background: #eef1f7;
  overflow: hidden;
}

.load-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width 0.5s ease;
}

.load-fill.calm {
  background: linear-gradient(90deg, #2dd4bf, #14b8a6);
}

.load-fill.warm {
  background: linear-gradient(90deg, #fbbf24, #f59e0b);
}

.load-fill.hot {
  background: linear-gradient(90deg, #fb7185, #e11d48);
}

/* ---------- 表格 ---------- */
.more-link {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 4px;
  font-size: 12px;
  color: var(--zk-indigo);
  font-weight: 550;
  border-radius: 6px;
  transition: color var(--zk-dur-fast) var(--zk-ease-out),
    background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.more-link:hover {
  color: var(--zk-indigo-dark);
  background: var(--zk-indigo-soft);
}

.more-link:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: 2px;
}

.dash-table {
  width: 100%;
}

.order-no {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-2);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.cell-rating {
  font-size: 12px;
  font-weight: 600;
  color: #b45309;
}

.cell-rating.muted,
.cell-muted {
  color: var(--zk-text-3);
  font-weight: 400;
}

.dash-footnote {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.footnote-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #cbd5e1;
}

.dash-footnote code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  background: #eef1f7;
  padding: 1px 5px;
  border-radius: 4px;
  color: #475569;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1360px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .dash-row--split,
  .dash-row--tables {
    grid-template-columns: minmax(0, 1fr);
  }
  .trend-foot {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .stat-card {
    padding: 14px 14px 12px;
  }

  .stat-value {
    margin-top: 10px;
    font-size: 24px;
  }

  .stat-caption {
    white-space: normal;
    line-height: 1.45;
  }

  .trend-body {
    padding: 4px 12px 0;
    gap: 4px;
  }

  .trend-card .zk-card-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .trend-side {
    width: 100%;
    justify-content: space-between;
    gap: 10px;
  }

  .trend-totals {
    font-size: 11px;
  }

  .trend-axis {
    width: 22px;
    flex-basis: 22px;
    height: 150px;
  }

  .trend-plot {
    height: 150px;
  }

  .trend-side {
    width: 100%;
    justify-content: space-between;
  }

  .bar {
    width: 10px;
  }

  .dash-footnote {
    align-items: flex-start;
    line-height: 1.6;
  }
}

@media (max-width: 360px) {
  .stat-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .trend-foot {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
