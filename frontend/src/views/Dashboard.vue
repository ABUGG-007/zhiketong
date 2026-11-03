<template>
  <div class="zk-page">
    <PageHeader
      title="数据看板"
      description="今日服务概况、近 7 日趋势与坐席负载，数据实时聚合。"
    >
      <template #actions>
        <div class="top-status">
          <div
            class="health-chip"
            :class="healthTone"
            role="status"
            :title="`MySQL ${health.mysql} · Redis ${health.redis}`"
          >
            <i class="dot" aria-hidden="true" />
            <span class="health-item">数据库 {{ healthLabel(health.mysql) }}</span>
            <span class="health-sep" aria-hidden="true">·</span>
            <span class="health-item">缓存 {{ healthLabel(health.redis) }}</span>
          </div>
          <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </template>
    </PageHeader>

    <el-alert
      v-if="error"
      class="dash-alert"
      type="warning"
      show-icon
      :closable="false"
      title="看板数据暂不可用"
    >
      <template #default>
        <div class="alert-body">
          <span>{{ error }}</span>
          <el-button link type="primary" @click="loadAll">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <!-- 指标卡：核心 / 风险 / 资源 三级视觉权重 -->
    <section v-if="!loaded && loading" class="stat-grid" aria-busy="true">
      <article v-for="n in 6" :key="`sk-${n}`" class="stat-card">
        <ZkSkeleton variant="stat" />
      </article>
    </section>
    <section v-else class="stat-grid" v-reveal.group style="--zk-stagger: 50ms">
      <article
        v-for="card in statCards"
        :key="card.key"
        class="stat-card"
        :class="[card.tier, card.tone]"
      >
        <div class="stat-top">
          <span class="stat-icon">
            <el-icon><component :is="card.icon" /></el-icon>
          </span>
          <span class="stat-label">{{ card.label }}</span>
        </div>
        <div class="stat-value">
          <CountNum class="zk-num" :value="card.raw" />
        </div>
        <div class="stat-caption">{{ card.caption }}</div>
        <div class="stat-delta">
          <template v-if="card.delta && loaded">
            <span class="delta" :class="card.delta.cls" :title="`较昨日${card.delta.text}`">
              <i class="delta-arrow" aria-hidden="true">{{ card.delta.arrow }}</i>{{ card.delta.text }}
            </span>
            <span class="delta-cmp">较昨日</span>
          </template>
        </div>
      </article>
    </section>

    <!-- 套餐额度：左侧版本说明，右侧 3 列额度进度，入口靠右 -->
    <section
      v-if="plan"
      v-reveal.group
      class="plan-strip"
      :class="`is-${planTone}`"
      aria-label="套餐额度"
    >
      <div class="plan-intro">
        <div class="plan-name-row">
          <i class="plan-dot" aria-hidden="true" />
          <b class="plan-name">{{ plan.planName }}</b>
        </div>
        <div class="plan-desc">{{ planText }}</div>
      </div>
      <div class="plan-meters">
        <div v-for="item in planMeters" :key="item.name" class="plan-meter">
          <div class="plan-meter-name">{{ item.name }}</div>
          <div class="plan-meter-row">
            <span
              class="plan-meter-track"
              role="progressbar"
              :aria-valuenow="item.percent"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-label="`${item.name}额度已使用 ${item.percent}%`"
            >
              <i class="plan-meter-fill" :style="{ width: `${Math.min(100, Math.max(3, item.percent))}%` }" />
            </span>
            <span class="plan-meter-num zk-num"><CountNum :value="item.used" /> / {{ item.limitText }}</span>
          </div>
        </div>
      </div>
      <router-link class="plan-strip-link zk-press" to="/admin/plan">
        {{ planAlert ? '处理额度' : '查看套餐与用量' }}<span class="link-arrow" aria-hidden="true">→</span>
      </router-link>
    </section>

    <!-- 趋势 + 坐席 -->
    <section class="dash-row dash-row--split" v-reveal.group>
      <div class="zk-card trend-card">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">近 7 日会话 / 工单趋势</div>
            <div class="zk-card-desc">按天统计，含 AI 接待与人工接待会话</div>
          </div>
          <div class="trend-totals">
            合计 <b><CountNum :value="trendTotals.sessions" /></b> 会话 /
            <b><CountNum :value="trendTotals.orders" /></b> 工单
          </div>
        </div>

        <div v-if="!loaded && !error" class="trend-skeleton" aria-busy="true">
          <ZkSkeleton variant="chart" :cols="7" />
        </div>
        <div v-else-if="trend.length" class="trend-body">
          <div class="trend-legend">
            <span class="legend-item"><i class="swatch swatch--sessions" />会话</span>
            <span class="legend-item"><i class="swatch swatch--orders" />工单</span>
          </div>
          <div class="trend-chart" :class="{ 'is-ready': chartReady }">
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
              <div
                class="trend-plot"
                :class="{ 'is-live': barsLive, 'is-morphing': morphing, 'is-snap': snapping }"
                role="img"
                :aria-label="trendSummary"
              >
                <span
                  v-for="n in 5"
                  :key="n"
                  class="grid-line"
                  aria-hidden="true"
                  :style="{ top: `${((n - 1) / 4) * 100}%` }"
                />
                <div class="trend-cols">
                  <div
                    v-for="(day, index) in trend"
                    :key="index"
                    class="trend-col"
                    :style="{ '--zk-bar-i': index }"
                  >
                    <div class="trend-bars">
                      <div class="bar-group">
                        <CountNum class="bar-val" :value="day.sessions" />
                        <div
                          class="bar bar--sessions"
                          :style="{ height: barHeight(day.sessions), '--zk-bar-scale': barScale('s', index) }"
                        />
                      </div>
                      <div class="bar-group">
                        <CountNum class="bar-val bar-val--muted" :value="day.orders" />
                        <div
                          class="bar bar--orders"
                          :style="{ height: barHeight(day.orders), '--zk-bar-scale': barScale('o', index) }"
                        />
                      </div>
                    </div>
                    <div class="trend-tip" role="tooltip">
                      <div class="tip-date">{{ day.date }}</div>
                      <div class="tip-row"><i class="dot dot--sessions" />会话 {{ num(day.sessions) }}</div>
                      <div class="tip-row"><i class="dot dot--orders" />工单 {{ num(day.orders) }}</div>
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
        </div>
        <ZkEmpty
          v-else
          size="sm"
          title="暂无趋势数据"
          hint="产生会话与工单后，这里会自动生成近 7 日走势"
        />

        <div class="trend-foot">
          <div class="mini-stat">
            <span class="mini-label">平均会话评分</span>
            <span class="mini-value zk-num">
              <CountNum
                :value="Number(ratings.avgSessionRating) > 0 ? ratings.avgSessionRating : null"
                :decimals="1"
              />
            </span>
          </div>
          <div class="mini-stat">
            <span class="mini-label">平均工单评分</span>
            <span class="mini-value zk-num">
              <CountNum
                :value="Number(ratings.avgOrderRating) > 0 ? ratings.avgOrderRating : null"
                :decimals="1"
              />
            </span>
          </div>
          <div class="mini-stat">
            <span class="mini-label">今日消息量</span>
            <span class="mini-value zk-num"><CountNum :value="counts.messagesToday" /></span>
          </div>
          <div class="mini-stat">
            <span class="mini-label">知识库分类</span>
            <span class="mini-value zk-num"><CountNum :value="counts.categories" /></span>
          </div>
        </div>
      </div>

      <div class="zk-card agent-card">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">坐席状态</div>
            <div class="zk-card-desc">在线 {{ onlineAgents }} / 共 {{ agents.length }} 位</div>
          </div>
          <span class="agent-count">{{ onlineAgents }} 在线</span>
        </div>

        <div class="agent-overall">
          <span class="overall-label">整体并发负载</span>
          <span class="overall-num zk-num">
            <CountNum :value="overallLoad.used" /> / <CountNum :value="overallLoad.capacity" />
          </span>
          <span class="load-track load-track--lg">
            <span class="load-fill" :class="overallLoad.tone" :style="{ width: `${overallLoad.percent}%` }" />
          </span>
          <b class="overall-pct zk-num" :class="overallLoad.tone">
            <CountNum :value="overallLoad.percent" suffix="%" />
          </b>
        </div>

        <div v-if="agents.length" class="agent-list">
          <div v-for="agent in agents" :key="agent.agentId" class="agent-row">
            <div class="agent-avatar" aria-hidden="true">{{ (agent.nickname || '坐').slice(0, 1) }}</div>
            <div class="agent-main">
              <div class="agent-line">
                <span class="agent-name">{{ agent.nickname || `坐席 ${agent.agentId}` }}</span>
                <span class="agent-status" :class="statusTone(agent.onlineStatus)">
                  <i class="status-dot" aria-hidden="true" />
                  {{ dictLabel(AGENT_STATUS_DICT, agent.onlineStatus) }}
                </span>
              </div>
              <div class="agent-metrics">
                <span class="agent-load">
                  负载
                  <em class="zk-num">
                    <CountNum :value="agent.currentCount" />/<CountNum :value="agent.maxConcurrent" />
                  </em>
                </span>
                <span class="agent-total">
                  累计接待 <b class="zk-num"><CountNum :value="agent.totalServed" /></b>
                </span>
              </div>
              <span class="load-track">
                <span
                  class="load-fill"
                  :class="loadTone(agent.currentCount, agent.maxConcurrent)"
                  :style="{ width: `${percent(agent.currentCount, agent.maxConcurrent)}%` }"
                />
              </span>
            </div>
          </div>
        </div>
        <ZkSkeleton v-else-if="!loaded && !error" variant="list" :rows="4" />
        <ZkEmpty v-else size="sm" title="暂无坐席在线数据" hint="坐席上线后这里会实时刷新状态与负载" />
      </div>
    </section>

    <!-- 最近工单 / 最近会话 -->
    <section class="dash-row dash-row--tables" v-reveal.group>
      <div class="zk-card">
        <div class="zk-card-head">
          <div class="zk-card-title">最近工单</div>
          <router-link class="more-link zk-press" to="/orders" aria-label="查看全部工单">查看全部 →</router-link>
        </div>
        <ZkSkeleton v-if="!loaded && !error" variant="table" :rows="5" :cols="4" />
        <el-table v-else :data="recentOrders" class="dash-table" size="default" empty-text="暂无工单数据">
          <el-table-column prop="orderNo" label="工单号" width="126">
            <template #default="{ row }">
              <span class="order-no" :title="row.orderNo">{{ row.orderNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="110" show-overflow-tooltip />
          <el-table-column prop="priority" label="优先级" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(PRIORITY_DICT, row.priority)"
                :tone="priorityTone(row.priority)"
                solid
              />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="88">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(ORDER_STATUS_DICT, row.status)"
                :tone="dictTone(ORDER_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column label="SLA 时效" min-width="150">
            <template #default="{ row }">
              <div v-if="slaInfo(row)" class="sla-cell">
                <div class="sla-head">
                  <span class="sla-tag" :class="slaInfo(row).tone">{{ slaInfo(row).text }}</span>
                </div>
                <span class="load-track sla-track">
                  <span class="load-fill" :class="slaInfo(row).tone" :style="{ width: `${slaInfo(row).pct}%` }" />
                </span>
              </div>
              <span v-else class="cell-muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="zk-card">
        <div class="zk-card-head">
          <div class="zk-card-title">最近会话</div>
          <router-link class="more-link zk-press" to="/sessions" aria-label="查看全部会话">查看全部 →</router-link>
        </div>
        <ZkSkeleton v-if="!loaded && !error" variant="table" :rows="5" :cols="5" />
        <el-table v-else :data="recentSessions" class="dash-table" empty-text="暂无会话数据">
          <el-table-column prop="sessionNo" label="会话号" width="122">
            <template #default="{ row }">
              <span class="order-no" :title="row.sessionNo">{{ row.sessionNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="userName" label="用户" min-width="104" show-overflow-tooltip />
          <el-table-column label="接待" width="72">
            <template #default="{ row }">
              <span class="svc-tag" :class="serviceType(row).tone">{{ serviceType(row).label }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="88">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                :tone="dictTone(SESSION_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="rating" label="评分" width="58" align="center">
            <template #default="{ row }">
              <span class="cell-rating" :class="{ muted: !row.rating }">
                <template v-if="row.rating"><CountNum :value="row.rating" /> ★</template>
                <template v-else>—</template>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="messageCount" label="消息" width="54" align="right">
            <template #default="{ row }">
              <CountNum class="zk-num cell-muted" :value="row.messageCount" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import { useCountUp, useEnteredOnce } from '@/composables/motion'
import { getDashboard, getHealth, getPlanUsage } from '@/api'
import {
  AGENT_STATUS_DICT,
  ORDER_STATUS_DICT,
  PRIORITY_DICT,
  SESSION_STATUS_DICT,
  dictLabel,
  dictTone,
  formatQuota,
  percent,
  shortDate
} from '@/utils/dict'

const overview = ref(null)
const plan = ref(null)
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

/**
 * 数字滚动：v-for 里的指标无法逐个调用 useCountUp，这里包一层组件统一节奏。
 * 首播 700ms，数据刷新 500ms；prefers-reduced-motion 由 useCountUp 内置处理。
 */
const CountNum = defineComponent({
  name: 'CountNum',
  props: {
    value: { type: [Number, String], default: null },
    decimals: { type: Number, default: 0 },
    prefix: { type: String, default: '' },
    suffix: { type: String, default: '' }
  },
  setup(props) {
    const valid = computed(
      () => props.value !== null && props.value !== '' && Number.isFinite(Number(props.value))
    )
    const { display } = useCountUp(computed(() => (valid.value ? Number(props.value) : 0)), {
      duration: 700,
      updateDuration: 500,
      decimals: props.decimals,
      prefix: props.prefix,
      suffix: props.suffix
    })
    return () => h('span', valid.value ? display.value : '—')
  }
})

const onlineAgents = computed(
  () => agents.value.filter((a) => a.onlineStatus === 'ONLINE' || a.onlineStatus === 'BUSY').length
)

/* ---------- 较昨日环比 ---------- */
// trend 为升序自然日，最后一点为今日，倒数第二点为昨日
function yesterdayValue(metric) {
  if (trend.value.length < 2) return null
  const y = trend.value[trend.value.length - 2]
  return Number(y?.[metric]) || 0
}

// 环比只看方向：上升深绿、下降深红，箭头与数值紧凑同行
function buildDelta(todayVal, yVal) {
  if (yVal == null) return null
  const t = Number(todayVal) || 0
  if (yVal === 0 && t === 0) {
    return { arrow: '—', text: '持平', cls: 'flat' }
  }
  const base = yVal === 0 ? 1 : yVal
  const pct = Math.round(((t - yVal) / base) * 100)
  if (pct === 0) return { arrow: '—', text: '持平', cls: 'flat' }
  return {
    arrow: pct > 0 ? '↑' : '↓',
    text: `${Math.abs(pct)}%`,
    cls: pct > 0 ? 'up' : 'down'
  }
}

const statCards = computed(() => {
  const c = counts.value
  return [
    {
      key: 'sessions',
      tier: 'core',
      label: '今日会话',
      raw: c.sessionsToday,
      caption: '今日新建会话',
      delta: buildDelta(c.sessionsToday, yesterdayValue('sessions')),
      icon: ChatDotRound,
      tone: 'teal'
    },
    {
      key: 'orders',
      tier: 'core',
      label: '今日工单',
      raw: c.ordersToday,
      caption: '今日新建工单',
      delta: buildDelta(c.ordersToday, yesterdayValue('orders')),
      icon: Tickets,
      tone: 'indigo'
    },
    {
      key: 'waiting',
      tier: 'risk',
      label: '待接入',
      raw: c.waitingSessions,
      caption: '排队等待人工接入',
      icon: Bell,
      tone: 'amber'
    },
    {
      key: 'overdue',
      tier: 'risk',
      label: '超时工单',
      raw: c.overdueOrders,
      caption: 'SLA 已超时未解决',
      icon: WarningFilled,
      tone: 'red'
    },
    {
      key: 'online',
      tier: 'resource',
      label: '在线坐席',
      raw: c.onlineAgents,
      caption: '当前在线 / 忙碌',
      icon: Headset,
      tone: 'green'
    },
    {
      key: 'knowledge',
      tier: 'resource',
      label: '知识条目',
      raw: c.knowledge,
      caption: `${num(c.categories)} 个分类 · 共 ${num(c.users)} 位用户`,
      icon: Collection,
      tone: 'slate'
    }
  ]
})

/* ---------- 趋势图 ---------- */
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
  if (v <= 0) return '0%'
  const pct = (v / chartMax.value) * 100
  return `${Math.max(3, Math.min(100, pct))}%`
}

/* ---------- 趋势图动效：刻度先淡入 → 柱子从底部长出（只播一次）→ 刷新走形态过渡 ---------- */
const entered = useEnteredOnce()
const chartReady = ref(false) // 刻度线 / 网格线先淡入
const barsLive = ref(false) // 首播结束后，后续变化一律走形态过渡
const morphing = ref(false)
const snapping = ref(false) // 形态过渡起始帧：先无过渡地落到旧高度
const morphScale = ref({}) // { s0: 0.42, o0: 0.98 } 旧高度 / 新高度
let morphTimer = 0
let entranceScheduled = false

// 刻度上限（旧数据也要算一次，柱子才能从旧高度接上）
function chartMaxOf(list) {
  const values = list.flatMap((item) => [Number(item.sessions) || 0, Number(item.orders) || 0])
  return niceStep(Math.max(1, ...values, 1) / 4) * 4
}

function pctOf(value, max) {
  const v = Number(value) || 0
  if (!max || v <= 0) return 0
  return Math.max(3, Math.min(100, (v / max) * 100))
}

const barScale = (prefix, index) => {
  if (!barsLive.value) return 0
  return morphScale.value[`${prefix}${index}`] ?? 1
}

// 首播只播一次：刻度淡入 300ms，柱子随后依次生长（错开 68ms）
function playEntrance() {
  if (entranceScheduled || barsLive.value || !entered.value || !trend.value.length) return
  entranceScheduled = true
  nextTick(() => {
    requestAnimationFrame(() => {
      chartReady.value = true
      requestAnimationFrame(() => {
        barsLive.value = true
      })
    })
  })
}
watch([trend, entered], playEntrance)

// 数据刷新：同一批柱子从旧高度过渡到新高度（500ms，错开 25ms），不清空重画
watch(trend, async (next, prev) => {
  if (!barsLive.value || !next.length || !prev || !prev.length) return
  const prevMax = chartMaxOf(prev)
  const nextMax = chartMaxOf(next)
  const from = {}
  next.forEach((day, index) => {
    const old = prev[index]
    if (!old) return
    from[`s${index}`] = pctOf(old.sessions, prevMax) / Math.max(1, pctOf(day.sessions, nextMax))
    from[`o${index}`] = pctOf(old.orders, prevMax) / Math.max(1, pctOf(day.orders, nextMax))
  })
  morphing.value = true
  snapping.value = true
  morphScale.value = from
  await nextTick()
  snapping.value = false
  // 双 rAF：先让浏览器画一帧「旧高度」，再解除起始缩放走过渡
  requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      morphScale.value = {}
    })
  )
  window.clearTimeout(morphTimer)
  // 等最后一根柱子落位，再解除「形态过渡中」状态
  morphTimer = window.setTimeout(() => {
    morphing.value = false
  }, 520 + Math.min(6, next.length) * 25)
})

onBeforeUnmount(() => window.clearTimeout(morphTimer))

/* ---------- 套餐额度进度 ---------- */
const planAlert = computed(() => {
  const items = plan.value?.items || []
  return (
    items.find((item) => item.level === 'danger') ||
    items.find((item) => item.level === 'warning') ||
    null
  )
})

const planPeak = computed(() => {
  const items = (plan.value?.items || []).filter((item) => Number(item.limit) > 0)
  if (!items.length) return null
  return items.reduce(
    (max, item) => ((Number(item.percent) || 0) > (Number(max.percent) || 0) ? item : max),
    items[0]
  )
})

const planTone = computed(() => planAlert.value?.level || 'normal')

const planMeters = computed(() => {
  const items = (plan.value?.items || []).filter((item) => Number(item.limit) > 0)
  return items.slice(0, 3).map((item) => ({
    name: item.name,
    used: item.used,
    limitText: `${formatQuota(item.limit)} ${item.unit || ''}`.trim(),
    percent: Math.round(Number(item.percent) || 0)
  }))
})

const planText = computed(() => {
  const item = planAlert.value
  if (!item) {
    return planPeak.value
      ? `本月额度充足，最高占用为${planPeak.value.name} ${Math.round(planPeak.value.percent)}%`
      : '本月额度充足，未触发任何上限提醒'
  }
  const prefix = item.level === 'danger' ? '已超出套餐上限' : '已接近套餐上限'
  return `${item.name}${prefix}`
})

/* ---------- 坐席整体负载 ---------- */
const overallLoad = computed(() => {
  let used = 0
  let capacity = 0
  for (const a of agents.value) {
    used += Number(a.currentCount) || 0
    capacity += Number(a.maxConcurrent) || 0
  }
  const pct = percent(used, capacity)
  const tone = pct >= 90 ? 'hot' : pct >= 60 ? 'warm' : 'calm'
  return { used, capacity, pct, percent: pct, tone }
})

// 坐席状态：实心小圆点 + 文字（在线深绿、忙碌橙、离线中灰）
const statusTone = (status) =>
  status === 'ONLINE' ? 'online' : status === 'BUSY' ? 'busy' : 'offline'

const loadTone = (current, max) => {
  const pct = percent(current, max)
  if (pct >= 90) return 'hot'
  if (pct >= 60) return 'warm'
  return 'calm'
}

/* ---------- 工单 SLA 时效 ---------- */
const parseTs = (value) => {
  if (!value) return null
  const text = String(value).replace('T', ' ').slice(0, 19).replace(/-/g, '/')
  const d = new Date(text)
  return Number.isFinite(d.getTime()) ? d.getTime() : null
}

const formatDuration = (ms) => {
  const totalMin = Math.floor(Math.abs(ms) / 60000)
  if (totalMin < 60) return `${totalMin} 分钟`
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  if (h < 24) return m ? `${h} 小时 ${m} 分` : `${h} 小时`
  const day = Math.floor(h / 24)
  return `${day} 天 ${h % 24} 小时`
}

function slaInfo(order) {
  const deadline = parseTs(order.slaDeadline)
  if (!deadline) return null
  const created = parseTs(order.createTime)
  const now = Date.now()
  const closed = order.status === 'CLOSED' || order.status === 'RESOLVED'
  const overdue = Number(order.overdueFlag) === 1 || (!closed && now > deadline)
  const pct =
    created && deadline > created
      ? Math.max(2, Math.min(100, Math.round(((now - created) / (deadline - created)) * 100)))
      : 100
  if (closed) {
    return { text: '已达成', tone: 'calm', pct: Math.min(pct, 100) }
  }
  if (overdue) {
    return { text: `超时 ${formatDuration(now - deadline)}`, tone: 'hot', pct: 100 }
  }
  const tone = pct >= 80 ? 'warm' : 'calm'
  return { text: `剩余 ${formatDuration(deadline - now)}`, tone, pct }
}

// 优先级：P0/P1 红底、P2 橙底、P3 灰底，均实色白字
const priorityTone = (priority) =>
  priority === 'P0' || priority === 'P1' ? 'red' : priority === 'P2' ? 'amber' : 'slate'

/* ---------- 会话接待类型 ---------- */
function serviceType(row) {
  if (row.status === 'WAITING') return { label: '排队', tone: 'queue' }
  if (row.status === 'HUMAN') return { label: '人工', tone: 'human' }
  if (row.agentName) return { label: '人工', tone: 'human' }
  return { label: 'AI', tone: 'ai' }
}

/* ---------- 健康 & 数据加载 ---------- */
const healthTone = computed(() => {
  if (health.value.status === 'UP') return 'teal'
  if (health.value.status === 'loading') return 'slate'
  if (health.value.mysql === 'UP' || health.value.redis === 'UP') return 'amber'
  return 'red'
})

const healthLabel = (value) => {
  if (value === 'UP') return '正常'
  if (value === 'DOWN') return '异常'
  if (value === 'UNKNOWN') return '未知'
  return '检测中'
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
    const data = await getDashboard()
    overview.value = data || null
  } catch (err) {
    error.value =
      err?.response?.status === 404
        ? '统计数据加载失败，请稍后重试'
        : err?.message || '数据加载失败，请稍后重试'
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function loadPlan() {
  try {
    plan.value = await getPlanUsage()
  } catch {
    plan.value = null
  }
}

function loadAll() {
  loadHealth()
  loadOverview()
  loadPlan()
}

onMounted(loadAll)
</script>

<style scoped>
/* ---------- 顶部状态：状态点 + 浅灰文字，与刷新按钮基线对齐 ---------- */
.top-status {
  display: flex;
  align-items: center;
  gap: 10px;
}
.health-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface);
  font-size: 12px;
  font-weight: 400;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}
.health-chip .dot {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border-radius: 50%;
  background: #a1a1aa;
}
.health-chip .health-sep { color: #d4d4d8; }
.health-chip.teal .dot { background: var(--zk-green); }
.health-chip.amber .dot { background: var(--zk-amber-edge); }
.health-chip.red .dot { background: var(--zk-red-edge); }

.dash-alert { margin-bottom: 16px; border-radius: var(--zk-radius); }
.alert-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  font-size: 13px;
}

/* ---------- 指标卡：数字统一放大，辅助说明与环比分行 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}
.stat-card {
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 13px 15px 11px;
  /* 入场只动 opacity / transform；错开延迟只给 opacity，悬停边框立即可响应 */
  transition: opacity 380ms var(--zk-ease-soft), transform 480ms var(--zk-ease-spring),
    border-color var(--zk-dur-fast) var(--zk-ease-out);
  transition-delay: calc(var(--zk-i, 0) * var(--zk-stagger, 50ms)), 0ms, 0ms;
}
.stat-card:hover { border-color: var(--zk-border-strong); }

.stat-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-icon {
  width: 24px;
  height: 24px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 14px;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
}
.stat-label {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--zk-text-2);
}
.stat-value {
  margin-top: 10px;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -1px;
  color: var(--zk-text);
  min-height: 34px;
  display: flex;
  align-items: center;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}
.stat-caption {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.4;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stat-delta {
  margin-top: 3px;
  min-height: 17px;
  display: flex;
  align-items: center;
  gap: 5px;
}

/* 风险告警卡：加深彩色边框 + 极浅底色，数字告警色放大 */
.stat-card.risk.amber {
  border-color: var(--zk-amber-edge);
  background: var(--zk-amber-tint);
}
.stat-card.risk.red {
  border-color: var(--zk-red-edge);
  background: var(--zk-red-tint);
}
.stat-card.risk .stat-value { font-size: 36px; letter-spacing: -1.4px; }
.stat-card.risk.amber .stat-icon { color: #fff; background: var(--zk-amber); }
.stat-card.risk.red .stat-icon { color: #fff; background: var(--zk-red); }
.stat-card.risk.amber .stat-value { color: var(--zk-amber); }
.stat-card.risk.red .stat-value { color: var(--zk-red); }

/* 核心卡：主色图标 */
.stat-card.core.teal .stat-icon { color: var(--zk-accent); background: var(--zk-accent-soft); }
.stat-card.core.indigo .stat-icon { color: var(--zk-accent); background: var(--zk-accent-soft); }

/* 资源卡：数字与其他卡统一字号字重 */
.stat-card.resource .stat-label { color: var(--zk-text-2); }

/* 环比：数字下方第二行，⇑绿⇓红 */
.delta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.delta-arrow { font-style: normal; font-size: 11px; }
.delta-cmp { color: var(--zk-text-3); font-size: 11px; font-weight: 400; }
.delta.up { color: #067a52; }
.delta.down { color: #b3123c; }
.delta.flat { color: var(--zk-text-3); }

.sk-bar {
  display: block;
  width: 62%;
  height: 26px;
  border-radius: var(--zk-radius-sm);
  background: linear-gradient(90deg, #f0f0f2 25%, #fafafa 37%, #f0f0f2 63%);
  background-size: 400% 100%;
  animation: shimmer 1.3s ease infinite;
}
@keyframes shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

/* 骨架卡与真实卡同高，数据到达前后版面不跳 */
.stat-card > .zk-skeleton-block {
  min-height: 108px;
  justify-content: center;
}
.trend-skeleton {
  padding: 4px 18px 8px;
}
.trend-skeleton .zk-skeleton-block {
  min-height: 292px;
}

/* ---------- 套餐额度：左侧说明 + 3 列进度 + 右侧入口 ---------- */
.plan-strip {
  display: grid;
  grid-template-columns: minmax(180px, 0.85fr) minmax(0, 2.4fr) auto;
  align-items: center;
  gap: 24px;
  margin-top: 12px;
  padding: 13px 18px;
  border-radius: var(--zk-radius);
  border: 1px solid var(--zk-border);
  background: var(--zk-surface);
}
.plan-strip.is-warning { border-color: var(--zk-amber-line); }
.plan-strip.is-danger { border-color: var(--zk-red-line); }
.plan-intro { min-width: 0; }
.plan-name-row {
  display: flex;
  align-items: center;
  gap: 7px;
}
.plan-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: var(--zk-accent);
}
.plan-strip.is-warning .plan-dot { background: var(--zk-amber-edge); }
.plan-strip.is-danger .plan-dot { background: var(--zk-red-edge); }
.plan-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
  white-space: nowrap;
}
.plan-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--zk-text-3);
}
.plan-strip.is-warning .plan-desc { color: var(--zk-amber); }
.plan-strip.is-danger .plan-desc { color: var(--zk-red); }

.plan-meters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  min-width: 0;
}
.plan-meter { min-width: 0; }
.plan-meter-name {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-2);
  margin-bottom: 6px;
}
.plan-meter-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.plan-meter-track {
  flex: 1;
  min-width: 36px;
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}
.plan-meter-fill {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  /* 既有高度进度条：宽度过渡与柱子形态过渡同节奏（460ms） */
  transition: width 460ms var(--zk-ease-emphasis);
}
.plan-meter-num {
  flex: 0 0 auto;
  font-size: 11.5px;
  color: var(--zk-text-2);
  white-space: nowrap;
}
.plan-strip-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--zk-accent-hover);
  white-space: nowrap;
}
.plan-strip-link:hover { color: var(--zk-accent-active); }
.link-arrow {
  font-size: 12px;
  transition: transform var(--zk-dur-fast) var(--zk-ease-out);
}
.plan-strip-link:hover .link-arrow { transform: translateX(2px); }

/* ---------- 行布局 ---------- */
.dash-row { display: grid; gap: 14px; margin-top: 14px; }
.dash-row--split { grid-template-columns: minmax(0, 1.72fr) minmax(0, 1fr); }
.dash-row--tables { grid-template-columns: minmax(0, 1.42fr) minmax(0, 1fr); }

/* ---------- 趋势图 ---------- */
.trend-card { display: flex; flex-direction: column; }
.trend-totals {
  font-size: 11.5px;
  color: var(--zk-text-3);
  white-space: nowrap;
}
.trend-totals b {
  color: var(--zk-text);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* 图例固定在图表左上角 */
.trend-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 2px 18px 0;
}
.trend-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 2px 0 10px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-weight: 500;
}
.swatch { width: 10px; height: 10px; border-radius: var(--zk-radius-xs); display: inline-block; }
.swatch--sessions { background: var(--zk-chart-sessions); }
.swatch--orders { background: var(--zk-chart-orders); }

.trend-chart { display: flex; gap: 8px; flex: 1; }
.trend-axis { position: relative; width: 32px; height: 240px; flex: 0 0 32px; }
.axis-label {
  position: absolute;
  right: 0;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
  opacity: 0;
  transition: opacity 300ms var(--zk-ease-out);
}
.axis-label:first-child { transform: translateY(0); }
.axis-label:last-child { transform: translateY(-100%); }
.trend-main { flex: 1; min-width: 0; }
.trend-plot { position: relative; height: 240px; padding: 0 4px; }
.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: #ededf0;
  opacity: 0;
  transition: opacity 300ms var(--zk-ease-out);
}
.grid-line:last-of-type { background: var(--zk-border-strong); }
/* 刻度先淡入（300ms），柱子随后依次长出 */
.trend-chart.is-ready .axis-label,
.trend-chart.is-ready .grid-line { opacity: 1; }
.trend-cols {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 6px;
}
.trend-col {
  position: relative;
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
  gap: 6px;
  width: 100%;
  height: 100%;
}
.bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  gap: 3px;
}
.bar-val {
  font-size: 11.5px;
  font-weight: 600;
  color: #3730a3;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  opacity: 0;
  transition: opacity 300ms var(--zk-ease-out) calc(var(--zk-bar-i, 0) * 40ms + 140ms);
}
.bar-val--muted { color: var(--zk-text-2); }
.trend-plot.is-live .bar-val { opacity: 1; }
/* 数据切换时柱顶数字先隐去，柱子落位后再按 25ms 错开回来 */
.trend-plot.is-morphing .bar-val { opacity: 0; transition: opacity 120ms var(--zk-ease-in) 0ms; }
.bar {
  width: 18px;
  max-width: 20px;
  border-radius: 2px 2px 0 0;
  transform: scaleY(var(--zk-bar-scale, 1));
  transform-origin: bottom center;
  will-change: transform;
  /* 只动 transform：刻度先淡入，柱子 120ms 后从底部长出，逐根错开 68ms */
  transition: transform 520ms var(--zk-ease-out) calc(var(--zk-bar-i, 0) * 68ms + 120ms),
    filter var(--zk-dur-fast) var(--zk-ease-out);
}
/* 从旧高度变到新高度：500ms，错开 25ms */
.trend-plot.is-morphing .bar {
  transition-duration: 500ms, var(--zk-dur-fast);
  transition-delay: calc(var(--zk-bar-i, 0) * 25ms), 0ms;
}
/* 形态过渡的起始帧不允许有过渡，否则会先反向缩一下 */
.trend-plot.is-snap .bar { transition: none !important; }
.bar--sessions { background: var(--zk-chart-sessions); }
.bar--orders { background: var(--zk-chart-orders); }

/* hover 交互提示 */
.trend-tip {
  position: absolute;
  bottom: calc(100% - 4px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  background: var(--zk-ink-900);
  color: #fff;
  border-radius: var(--zk-radius-sm);
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out),
    transform var(--zk-dur-fast) var(--zk-ease-out);
  z-index: 5;
  box-shadow: var(--zk-shadow-md);
}
.tip-date { font-weight: 600; margin-bottom: 3px; color: #e4e4e7; }
.tip-row { display: flex; align-items: center; gap: 6px; }
.tip-row .dot { width: 6px; height: 6px; border-radius: 50%; }
.dot--sessions { background: var(--zk-chart-sessions); }
.dot--orders { background: #c7cbff; }
.trend-col:hover .trend-tip { opacity: 1; transform: translateX(-50%) translateY(0); }
.trend-col:hover .bar { filter: brightness(0.94); }

.trend-dates {
  display: flex;
  justify-content: space-around;
  gap: 6px;
  padding: 8px 4px 0;
}
.trend-date {
  flex: 1;
  text-align: center;
  font-size: 11px;
  color: var(--zk-text-2);
  letter-spacing: 0.2px;
}

/* 汇总指标：小指标卡，与图表同属一个模块 */
.trend-foot {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 6px;
  padding: 12px 18px 14px;
  border-top: 1px solid var(--zk-border);
}
.mini-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
}
.mini-label { font-size: 11px; color: var(--zk-text-3); }
.mini-value {
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--zk-text);
}

/* ---------- 坐席 ---------- */
.agent-card { display: flex; flex-direction: column; }
.agent-count { font-size: 11.5px; font-weight: 500; color: var(--zk-text-3); }

/* 整体负载：标签 + 数值 + 加粗进度条 + 末尾百分比 */
.agent-overall {
  display: grid;
  grid-template-columns: auto auto minmax(40px, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 2px 18px 10px;
}
.overall-label {
  font-size: 11.5px;
  color: var(--zk-text-2);
  white-space: nowrap;
}
.overall-num {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--zk-text);
  white-space: nowrap;
}
.overall-pct {
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.overall-pct.calm { color: var(--zk-green); }
.overall-pct.warm { color: #b25e09; }
.overall-pct.hot { color: var(--zk-red); }

.agent-list { padding: 0 16px 8px; display: flex; flex-direction: column; flex: 1; }
.agent-row {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  padding: 7px 0;
}
.agent-row + .agent-row { border-top: 1px solid var(--zk-border); }

/* 头像：姓氏文字 + 浅灰底，不使用大块纯色 */
.agent-avatar {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--zk-text-2);
  background: #f2f2f4;
  border: 1px solid var(--zk-border);
}
.agent-main { flex: 1; min-width: 0; }
.agent-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.agent-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-3);
  white-space: nowrap;
}
.status-dot {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border-radius: 50%;
  background: #9ca3af;
}
.agent-status.online { color: var(--zk-green); }
.agent-status.online .status-dot { background: var(--zk-green); }
.agent-status.busy { color: var(--zk-amber); }
.agent-status.busy .status-dot { background: var(--zk-amber-edge); }
.agent-status.offline .status-dot { background: #9ca3af; }

.agent-metrics {
  margin-top: 1px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-size: 11px;
  line-height: 1.45;
  color: var(--zk-text-3);
}
.agent-load em {
  font-style: normal;
  font-weight: 500;
  color: var(--zk-text-2);
}
.agent-total b {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--zk-text);
}

.load-track {
  display: block;
  margin-top: 5px;
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}
.load-track--lg { height: 10px; margin-top: 0; }
.load-fill {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  transition: width 460ms var(--zk-ease-emphasis);
}
.load-fill.calm { background: var(--zk-green); }
.load-fill.warm { background: #ea8a1c; }
.load-fill.hot { background: var(--zk-red); }

/* ---------- 表格增强 ---------- */
.more-link {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-accent-active);
  border-radius: var(--zk-radius-sm);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
.more-link:hover { background: var(--zk-accent-soft); }
.dash-table { width: 100%; }
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
.cell-rating { font-size: 12px; font-weight: 600; color: #b45309; }
.cell-rating.muted,
.cell-muted { color: var(--zk-text-3); font-weight: 400; }

/* SLA 单元格 */
.sla-cell { display: flex; flex-direction: column; gap: 4px; }
.sla-head { display: flex; }
.sla-tag { font-size: 11px; font-weight: 600; font-variant-numeric: tabular-nums; }
.sla-tag.calm { color: var(--zk-green); }
.sla-tag.warm { color: #b45309; }
.sla-tag.hot { color: var(--zk-red); }
.sla-track { margin-top: 0; height: 4px; }

/* 接待类型标签 */
.svc-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 7px;
  border-radius: var(--zk-radius-sm);
  font-size: 11px;
  font-weight: 600;
}
.svc-tag.ai { color: var(--zk-cyan); background: var(--zk-cyan-soft); }
.svc-tag.human { color: #4338ca; background: var(--zk-accent-soft); }
.svc-tag.queue { color: #a34d08; background: var(--zk-amber-soft); }

/* ---------- 响应式 ---------- */
@media (max-width: 1180px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1100px) {
  .dash-row--split,
  .dash-row--tables { grid-template-columns: minmax(0, 1fr); }
  .trend-foot { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .plan-strip { grid-template-columns: minmax(0, 1fr) auto; row-gap: 12px; }
  .plan-intro { grid-column: 1; }
  .plan-strip-link { grid-column: 2; grid-row: 1; }
  .plan-meters { grid-column: 1 / -1; grid-row: 2; }
}
@media (max-width: 720px) {
  .top-status { width: 100%; justify-content: space-between; }
}
@media (max-width: 640px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .stat-value { font-size: 26px; }
  .stat-card.risk .stat-value { font-size: 30px; }
  .plan-strip { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .plan-meters { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .plan-strip-link { grid-column: 1; grid-row: auto; justify-self: flex-start; }
  .trend-body { padding: 2px 12px 0; }
  .trend-card .zk-card-head { flex-direction: column; align-items: flex-start; gap: 6px; }
  .trend-axis,
  .trend-plot { height: 180px; }
  .bar { width: 12px; max-width: 14px; }
  .trend-foot { padding: 10px 12px 12px; }
  .agent-overall { grid-template-columns: auto minmax(30px, 1fr) auto; }
  .overall-label { display: none; }
}
@media (max-width: 360px) {
  .stat-grid { grid-template-columns: minmax(0, 1fr); }
  .trend-foot { grid-template-columns: minmax(0, 1fr); }
}

/* ---------- 减少动效：图形直接给最终值，不靠 JS 绕过 ---------- */
@media (prefers-reduced-motion: reduce) {
  .bar { transition: none !important; }
  .bar-val,
  .axis-label,
  .grid-line { opacity: 1 !important; transition: none !important; }
}
</style>
