<template>
  <div class="zk-page">
    <PageHeader
      title="用户中心"
      :description="`${greeting}，${displayName}。会话、工单与常见问题都汇总在这里，坐席处理进展会实时同步给你。`"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 账号概览：深色底作为页面视觉锚点，右侧承载账号事实信息 -->
    <section class="hero" aria-label="账号概览" v-reveal.group style="--zk-stagger: 60ms">
      <div class="hero-left">
        <div class="hero-avatar" aria-hidden="true">{{ avatarText }}</div>
        <div class="hero-copy">
          <h2 class="hero-title">{{ greeting }}，{{ displayName }}</h2>
          <p class="hero-desc">
            会话与工单仅本人可见，客服坐席的处理进展会自动同步到这里，无需反复询问。
          </p>
          <div class="hero-actions">
            <el-button class="cta-primary" :icon="ChatDotRound" @click="go('/user/chat')">
              发起咨询
            </el-button>
            <el-button class="cta-ghost" :icon="Plus" @click="go('/user/orders?create=1')">
              提交工单
            </el-button>
          </div>
        </div>
      </div>

      <dl class="hero-facts">
        <div class="fact">
          <dt>账号</dt>
          <dd class="fact-mono">{{ user?.username || '—' }}</dd>
        </div>
        <div class="fact">
          <dt>角色</dt>
          <dd>{{ roleLabel }}</dd>
        </div>
        <div class="fact">
          <dt>联系电话</dt>
          <dd class="fact-mono">{{ maskedPhone }}</dd>
        </div>
        <div class="fact">
          <dt>本次登录</dt>
          <dd>{{ lastLoginText }}</dd>
        </div>
      </dl>
    </section>

    <!-- 四类指标：核心数据在前，待确认工单作为风险项突出 -->
    <section class="stat-grid" aria-label="我的数据概览" v-reveal.group style="--zk-stagger: 60ms">
      <router-link
        v-for="card in statCards"
        :key="card.key"
        :to="card.to"
        class="stat-card zk-hover-lift zk-press"
        :class="[card.tier, { 'is-alert': card.alert }]"
      >
        <div class="stat-top">
          <span class="stat-icon" aria-hidden="true">
            <el-icon><component :is="card.icon" /></el-icon>
          </span>
          <span class="stat-label">{{ card.label }}</span>
          <el-icon class="stat-arrow" aria-hidden="true"><ArrowRight /></el-icon>
        </div>
        <div class="stat-value">
          <span v-if="loading && card.value === '—'" class="sk-bar" />
          <span v-else-if="card.value === '—'" class="stat-dash">—</span>
          <span v-else class="zk-num">{{ card.value }}</span>
        </div>
        <div class="stat-caption">{{ card.caption }}</div>
      </router-link>
    </section>

    <p v-if="loadError" class="load-error" role="status">
      <el-icon aria-hidden="true"><WarningFilled /></el-icon>
      {{ loadError }}
    </p>

    <div class="home-row home-row--wide" v-reveal.group style="--zk-stagger: 60ms">
      <!-- 最近会话 -->
      <section class="zk-card list-card" aria-label="最近会话">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">最近会话</div>
            <div class="zk-card-desc">最近 5 次咨询记录，点击可回看完整对话与评价</div>
          </div>
          <router-link class="head-link" to="/user/sessions">
            全部会话<span class="link-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>

        <div v-if="sessions.length" class="session-list">
          <button
            v-for="row in sessions"
            :key="row.id"
            type="button"
            class="session-row"
            @click="openSession(row)"
          >
            <span class="session-main">
              <span class="session-no">{{ row.sessionNo || `#${row.id}` }}</span>
              <span class="session-meta">
                <StatusPill
                  :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                  :tone="dictTone(SESSION_STATUS_DICT, row.status)"
                  dot
                />
                <span class="session-count">{{ row.messageCount ?? 0 }} 条消息</span>
                <span
                  v-if="Number(row.rating) > 0"
                  class="stars"
                  role="img"
                  :aria-label="`满意度 ${row.rating} 分`"
                >
                  <span v-for="n in 5" :key="n" :class="{ 'is-empty': n > Number(row.rating) }">★</span>
                </span>
                <span v-else class="zk-muted session-muted">未评价</span>
              </span>
            </span>
            <span class="session-side">
              <span class="session-time">{{ shortTime(row.createTime) }}</span>
              <el-icon class="row-arrow" aria-hidden="true"><ArrowRight /></el-icon>
            </span>
          </button>
        </div>

        <ZkSkeleton v-else-if="loading" variant="list" :rows="4" />
        <ZkEmpty
          v-else
          size="sm"
          title="还没有会话记录"
          hint="向智能助手提一个问，会话会自动归档到这里"
        >
          <el-button size="small" type="primary" :icon="ChatDotRound" @click="go('/user/chat')">
            发起咨询
          </el-button>
        </ZkEmpty>
      </section>

      <!-- 我的工单 -->
      <section class="zk-card list-card" aria-label="我的工单">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">我的工单</div>
            <div class="zk-card-desc">最近提交的问题与 SLA 时效</div>
          </div>
          <router-link class="head-link" to="/user/orders">
            全部工单<span class="link-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>

        <div v-if="orders.length" class="order-list">
          <button
            v-for="row in orders"
            :key="row.id"
            type="button"
            class="order-row"
            @click="openOrder(row)"
          >
            <span class="order-main">
              <span class="order-title">{{ row.title }}</span>
              <span class="order-meta">
                <span class="order-no">{{ row.orderNo || `#${row.id}` }}</span>
                <StatusPill
                  :label="dictLabel(PRIORITY_DICT, row.priority)"
                  :tone="dictTone(PRIORITY_DICT, row.priority)"
                  :solid="row.priority === 'P0' || row.priority === 'P1'"
                />
                <span class="sla" :class="`is-${slaState(row).tone}`">{{ slaState(row).text }}</span>
              </span>
            </span>
            <span class="order-side">
              <StatusPill
                :label="dictLabel(ORDER_STATUS_DICT, row.status)"
                :tone="dictTone(ORDER_STATUS_DICT, row.status)"
              />
              <el-icon class="row-arrow" aria-hidden="true"><ArrowRight /></el-icon>
            </span>
          </button>
        </div>

        <ZkSkeleton v-else-if="loading" variant="list" :rows="3" />
        <ZkEmpty
          v-else
          size="sm"
          title="暂无工单"
          hint="AI 解决不了的问题，可以提交工单由人工跟进"
        >
          <el-button size="small" type="primary" :icon="Plus" @click="go('/user/orders?create=1')">
            提交工单
          </el-button>
        </ZkEmpty>
      </section>
    </div>

    <div class="home-row home-row--wide" v-reveal.group style="--zk-stagger: 60ms">
      <!-- 常见问题：直接展开答案，减少一次跳转 -->
      <section class="zk-card faq-card" aria-label="常见问题">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">常见问题</div>
            <div class="zk-card-desc">
              知识库中已启用的标准问答{{ faqTotal ? `，共 ${faqTotal} 条` : '' }}
            </div>
          </div>
          <router-link class="head-link" to="/user/knowledge">
            知识库<span class="link-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>

        <el-collapse
          v-if="faqs.length"
          v-model="faqOpen"
          accordion
          class="faq-collapse"
        >
          <el-collapse-item v-for="item in faqs" :key="item.id" :name="String(item.id)">
            <template #title>
              <span v-if="item.categoryName" class="faq-cat">{{ item.categoryName }}</span>
              <span class="faq-q">{{ item.question }}</span>
            </template>
            <p class="faq-a">{{ item.answer }}</p>
          </el-collapse-item>
        </el-collapse>

        <ZkSkeleton v-else-if="loading" variant="lines" :rows="5" />
        <ZkEmpty
          v-else
          size="sm"
          title="知识库暂时没有可用问答"
          hint="也可以直接在「AI 咨询」中提问，命中知识库会立即回复"
        >
          <el-button size="small" :icon="ChatDotRound" @click="go('/user/chat')">去提问</el-button>
        </ZkEmpty>
      </section>

      <!-- 服务指引：写清真实操作路径，避免空泛口号 -->
      <section class="zk-card guide-card" aria-label="服务指引">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">服务指引</div>
            <div class="zk-card-desc">从自助查询到人工跟进的完整路径</div>
          </div>
        </div>

        <ol class="guide-list">
          <li v-for="(step, index) in guideSteps" :key="step.title" class="guide-item">
            <span class="guide-no" aria-hidden="true">{{ index + 1 }}</span>
            <div class="guide-body">
              <b>{{ step.title }}</b>
              <p>{{ step.desc }}</p>
            </div>
          </li>
        </ol>

        <div class="guide-foot">
          <span class="guide-foot-text">数据范围：仅展示本人会话与工单，其他用户数据不可见</span>
          <div class="guide-foot-actions">
            <el-button size="small" :icon="ChatDotRound" @click="go('/user/chat')">在线咨询</el-button>
            <el-button size="small" type="primary" :icon="Tickets" @click="go('/user/orders')">
              查看工单
            </el-button>
          </div>
        </div>
      </section>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      会话结束会自动邀请评价；工单解决后可在「我的工单」确认关闭并评价
    </div>

    <SessionDrawer v-model="sessionDrawer" :session-id="activeSessionId" role="USER" />
    <OrderDrawer v-model="orderDrawer" :order-id="activeOrderId" @changed="loadAll" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChatDotRound,
  ChatLineSquare,
  CircleCheck,
  Plus,
  Refresh,
  Tickets,
  Timer,
  WarningFilled
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import SessionDrawer from '@/components/SessionDrawer.vue'
import OrderDrawer from '@/components/OrderDrawer.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import { useCountUp } from '@/composables/motion'
import { getUserKbEntries, getUserOrders, getUserProfile, getUserSessions } from '@/api'
import { getUser } from '@/api/http'
import {
  ORDER_STATUS_DICT,
  PRIORITY_DICT,
  ROLE_DICT,
  SESSION_STATUS_DICT,
  dictLabel,
  dictTone,
  formatClock,
  formatDateTime,
  shortDate
} from '@/utils/dict'

const router = useRouter()
const user = getUser()

const profile = ref(null)
const sessions = ref([])
const orders = ref([])
const faqs = ref([])
const sessionTotal = ref(null)
const orderTotal = ref(null)
const processingTotal = ref(null)
const resolvedTotal = ref(null)
const faqTotal = ref(null)
const loading = ref(false)
const loadError = ref('')
const faqOpen = ref('')
/** 常见问题仅在首次加载时自动展开第一条，之后尊重用户手动折叠 */
let faqAutoOpened = false

const sessionDrawer = ref(false)
const orderDrawer = ref(false)
const activeSessionId = ref(null)
const activeOrderId = ref(null)

/* SLA 剩余时长按分钟重算，避免停留在打开页面的时刻 */
const nowTick = ref(Date.now())
let tickTimer = null

const displayName = computed(() => user?.nickname || user?.username || '用户')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => dictLabel(ROLE_DICT, user?.roleCode, '普通用户'))

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const maskedPhone = computed(() => {
  const phone = String(profile.value?.phone || '')
  if (!phone) return '未绑定'
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone
})

const lastLoginText = computed(() => {
  const text = formatDateTime(profile.value?.lastLoginTime)
  return text === '—' ? '首次登录' : text
})

// 个人指标滚动：首播 700ms、刷新 500ms；无数据时显示占位
const useMetric = (source) => useCountUp(computed(() => Number(source.value) || 0))
const sessionCount = useMetric(sessionTotal)
const orderCount = useMetric(orderTotal)
const processingCount = useMetric(processingTotal)
const resolvedCount = useMetric(resolvedTotal)
const countText = (counter, raw) => (raw === null || raw === undefined ? '—' : counter.display.value)

const statCards = computed(() => [
  {
    key: 'sessions',
    label: '我的会话',
    value: countText(sessionCount, sessionTotal.value),
    caption: 'AI 与坐席的全部咨询记录',
    to: { path: '/user/sessions' },
    icon: ChatLineSquare,
    tier: 'core',
    alert: false
  },
  {
    key: 'orders',
    label: '我的工单',
    value: countText(orderCount, orderTotal.value),
    caption: '提交的问题与处理进度',
    to: { path: '/user/orders' },
    icon: Tickets,
    tier: 'core',
    alert: false
  },
  {
    key: 'processing',
    label: '处理中工单',
    value: countText(processingCount, processingTotal.value),
    caption: '坐席正在跟进处理',
    to: { path: '/user/orders', query: { status: 'PROCESSING' } },
    icon: Timer,
    tier: 'resource',
    alert: false
  },
  {
    key: 'resolved',
    label: '待确认工单',
    value: countText(resolvedCount, resolvedTotal.value),
    caption: '已解决，待你确认关闭',
    to: { path: '/user/orders', query: { status: 'RESOLVED' } },
    icon: CircleCheck,
    tier: 'risk',
    alert: Number(resolvedTotal.value) > 0
  }
])

const guideSteps = [
  {
    title: '智能助手先答',
    desc: '在「AI 咨询」中描述问题，命中知识库会直接给出标准答案，无需排队等待。'
  },
  {
    title: '一键转人工',
    desc: '机器人无法解决时点击「转人工」，系统按咨询类型与坐席空闲度自动分配。'
  },
  {
    title: '提交工单跟进',
    desc: '需要持续跟进的问题点击「提交工单」，会自动关联当前会话，坐席无需你重复描述。'
  },
  {
    title: '确认与评价',
    desc: '工单解决后回到「我的工单」确认关闭并评价，评价结果直接进入服务质量统计。'
  }
]

function go(path) {
  router.push(path)
}

function openSession(row) {
  activeSessionId.value = row.id
  sessionDrawer.value = true
}

function openOrder(row) {
  activeOrderId.value = row.id
  orderDrawer.value = true
}

/** 解析后端 yyyy-MM-dd HH:mm:ss（兼容 Safari 需把 - 换成 /） */
function parseTime(value) {
  if (!value) return Number.NaN
  const text = String(value).replace('T', ' ').slice(0, 19).replace(/-/g, '/')
  return new Date(text).getTime()
}

function shortTime(value) {
  if (!value) return '—'
  return `${shortDate(value)} ${formatClock(value)}`
}

/**
 * 工单 SLA 剩余时长：已解决/关闭视为完结，超时优先按 overdueFlag 判定。
 */
function slaState(row) {
  if (row?.status === 'RESOLVED' || row?.status === 'CLOSED') {
    return { text: 'SLA 已完结', tone: 'muted' }
  }
  const deadline = parseTime(row?.slaDeadline)
  if (!Number.isFinite(deadline)) return { text: '未启用 SLA', tone: 'muted' }
  if (Number(row?.overdueFlag) === 1) return { text: 'SLA 已超时', tone: 'red' }

  const diff = deadline - nowTick.value
  if (diff <= 0) return { text: 'SLA 已超时', tone: 'red' }

  const minutes = Math.max(1, Math.floor(diff / 60000))
  if (minutes < 60) return { text: `SLA 剩 ${minutes} 分钟`, tone: minutes <= 30 ? 'amber' : 'muted' }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return { text: `SLA 剩 ${hours} 小时`, tone: hours <= 4 ? 'amber' : 'muted' }
  return { text: `SLA 剩 ${Math.floor(hours / 24)} 天`, tone: 'muted' }
}

const readList = (payload) => (Array.isArray(payload?.list) ? payload.list : [])

async function loadAll() {
  loading.value = true
  loadError.value = ''

  // 六个只读接口并发拉取，任一失败不影响其余区块渲染
  const results = await Promise.allSettled([
    getUserSessions({ pageNum: 1, pageSize: 5 }),
    getUserOrders({ pageNum: 1, pageSize: 5 }),
    getUserOrders({ pageNum: 1, pageSize: 1, status: 'PROCESSING' }),
    getUserOrders({ pageNum: 1, pageSize: 1, status: 'RESOLVED' }),
    getUserKbEntries({ pageNum: 1, pageSize: 6 }),
    getUserProfile()
  ])

  const [sessionRes, orderRes, processingRes, resolvedRes, faqRes, profileRes] = results

  if (sessionRes.status === 'fulfilled') {
    sessions.value = readList(sessionRes.value)
    sessionTotal.value = Number(sessionRes.value?.total ?? 0)
  } else {
    sessions.value = []
    sessionTotal.value = null
  }

  if (orderRes.status === 'fulfilled') {
    orders.value = readList(orderRes.value)
    orderTotal.value = Number(orderRes.value?.total ?? 0)
  } else {
    orders.value = []
    orderTotal.value = null
  }

  processingTotal.value =
    processingRes.status === 'fulfilled' ? Number(processingRes.value?.total ?? 0) : null
  resolvedTotal.value =
    resolvedRes.status === 'fulfilled' ? Number(resolvedRes.value?.total ?? 0) : null

  if (faqRes.status === 'fulfilled') {
    faqs.value = readList(faqRes.value).slice(0, 6)
    faqTotal.value = Number(faqRes.value?.total ?? faqs.value.length)
    // 首屏直接展示一条答案，避免卡片只剩折叠标题显得空
    if (!faqAutoOpened && faqs.value.length) {
      faqOpen.value = String(faqs.value[0].id)
      faqAutoOpened = true
    }
  } else {
    faqs.value = []
    faqTotal.value = null
  }

  if (profileRes.status === 'fulfilled') {
    profile.value = profileRes.value || null
  }

  if (results.some((item) => item.status === 'rejected')) {
    loadError.value = '部分数据加载失败，请点击右上角「刷新」重试'
  }

  loading.value = false
}

onMounted(() => {
  loadAll()
  tickTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 30000)
})

onBeforeUnmount(() => {
  if (tickTimer) window.clearInterval(tickTimer)
})
</script>

<style scoped>
/* ================= 账号概览 ================= */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.66fr);
  gap: 24px;
  padding: 20px 22px;
  border-radius: var(--zk-radius);
  border: 1px solid var(--zk-ink-700);
  background: var(--zk-ink-900);
  color: var(--zk-text-invert);
}

.hero-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
}

.hero-avatar {
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 17px;
  font-weight: 600;
  color: var(--zk-ink-900);
  background: #fafafa;
}

.hero-copy {
  min-width: 0;
}

.hero-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.2px;
  line-height: 1.35;
  color: #ffffff;
}

.hero-desc {
  margin-top: 5px;
  font-size: 12.5px;
  line-height: 1.65;
  color: rgba(250, 250, 250, 0.62);
  max-width: 560px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
}

/* 深色底上的主操作：白色实底，保证最高对比度 */
.hero .cta-primary.el-button {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-ink-900);
  background: #ffffff;
  border-color: #ffffff;
}

.hero .cta-primary.el-button:hover,
.hero .cta-primary.el-button:focus-visible {
  color: var(--zk-ink-950);
  background: #eaeaee;
  border-color: #eaeaee;
}

.hero .cta-ghost.el-button {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  color: rgba(250, 250, 250, 0.9);
  background: transparent;
  border-color: rgba(255, 255, 255, 0.24);
}

.hero .cta-ghost.el-button:hover,
.hero .cta-ghost.el-button:focus-visible {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.4);
}

.hero-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
  margin: 0;
  padding-left: 22px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  align-content: center;
}

.fact {
  min-width: 0;
}

.fact dt {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: rgba(250, 250, 250, 0.5);
}

.fact dd {
  margin: 5px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: #fafafa;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fact-mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px !important;
  font-weight: 500 !important;
  font-variant-numeric: tabular-nums;
}

/* ================= 指标卡 ================= */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.stat-card {
  display: block;
  padding: 13px 15px 12px;
  border-radius: var(--zk-radius);
  border: 1px solid var(--zk-border);
  background: var(--zk-surface);
  /* 入场只动 opacity / transform；错开延迟只给 opacity，悬停反馈立即可响应 */
  transition: opacity 380ms var(--zk-ease-soft), transform 480ms var(--zk-ease-spring),
    border-color var(--zk-dur-fast) var(--zk-ease-out),
    background-color var(--zk-dur-fast) var(--zk-ease-out),
    box-shadow var(--zk-dur-base) var(--zk-ease-out);
  transition-delay: calc(var(--zk-i, 0) * var(--zk-stagger, 60ms)), 0ms, 0ms, 0ms, 0ms;
}

.stat-card:hover {
  border-color: var(--zk-border-strong);
  background: var(--zk-surface-2);
}

/* v-reveal 落位后会写 transform: none，这里按同一参数补回悬停抬起与按下反馈 */
@media (hover: hover) and (pointer: fine) {
  .stat-card.is-in:hover {
    transform: translateY(-2px);
    box-shadow: var(--zk-shadow-md);
  }

  .stat-card.is-in:active {
    transform: translateY(0) scale(0.98);
    transition-duration: var(--zk-dur-instant);
  }
}

.stat-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-icon {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
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

.stat-arrow {
  margin-left: auto;
  font-size: 12px;
  color: var(--zk-text-3);
  opacity: 0;
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out);
}

.stat-card:hover .stat-arrow {
  opacity: 1;
}

.stat-value {
  margin-top: 10px;
  min-height: 34px;
  display: flex;
  align-items: center;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -1px;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.stat-dash {
  color: var(--zk-text-3);
  font-weight: 600;
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

.stat-card.core .stat-icon {
  color: var(--zk-accent);
  background: var(--zk-accent-soft);
}

/* 待确认工单：有数据时用告警色强调，避免长期被忽略 */
.stat-card.risk.is-alert {
  border-color: var(--zk-amber-edge);
  background: var(--zk-amber-tint);
}

.stat-card.risk.is-alert .stat-icon {
  color: #ffffff;
  background: var(--zk-amber);
}

.stat-card.risk.is-alert .stat-value {
  color: var(--zk-amber);
}

.sk-bar {
  display: block;
  width: 62%;
  height: 26px;
  border-radius: var(--zk-radius-sm);
  background: linear-gradient(90deg, #f0f0f2 25%, #fafafa 37%, #f0f0f2 63%);
  background-size: 400% 100%;
  animation: zk-shimmer 1.3s linear infinite;
}

.load-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: var(--zk-radius-sm);
  border: 1px solid var(--zk-amber-line);
  background: var(--zk-amber-soft);
  font-size: 12px;
  color: var(--zk-amber);
}

/* ================= 两列卡片区 ================= */
.home-row {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.home-row--wide {
  grid-template-columns: minmax(0, 1.28fr) minmax(0, 1fr);
}

.list-card,
.faq-card,
.guide-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.head-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  font-weight: 500;
  color: var(--zk-text-3);
  white-space: nowrap;
  transition: color var(--zk-dur-fast) var(--zk-ease-out);
}

.head-link:hover {
  color: var(--zk-accent);
}

.link-arrow {
  font-size: 12px;
}

/* ---------------- 最近会话 ---------------- */
.session-list {
  display: flex;
  flex-direction: column;
  padding: 4px 0 8px;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 18px;
  border: 0;
  border-bottom: 1px solid var(--zk-border);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.session-row:last-child {
  border-bottom: 0;
}

.session-row:hover {
  background: #eef1ff;
}

.session-row:focus-visible {
  outline: 2px solid var(--zk-accent);
  outline-offset: -2px;
}

.session-main {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  flex: 1;
}

.session-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  flex-wrap: wrap;
}

.session-count {
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.session-side {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.session-time {
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.session-row:hover .row-arrow {
  opacity: 1;
}

.session-muted {
  font-size: 11.5px;
}

.stars {
  display: inline-flex;
  gap: 1px;
  font-size: 11px;
  color: #d97706;
  letter-spacing: -0.5px;
}

.stars .is-empty {
  color: #e4e4e7;
}

/* ---------------- 我的工单 ---------------- */
.order-list {
  display: flex;
  flex-direction: column;
  padding: 4px 0 8px;
}

.order-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 18px;
  border: 0;
  border-bottom: 1px solid var(--zk-border);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.order-row:last-child {
  border-bottom: 0;
}

.order-row:hover {
  background: #eef1ff;
}

.order-row:focus-visible {
  outline: 2px solid var(--zk-accent);
  outline-offset: -2px;
}

.order-main {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  flex: 1;
}

.order-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  flex-wrap: wrap;
}

.order-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.sla {
  font-size: 11px;
  font-weight: 500;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.sla.is-amber {
  color: var(--zk-amber);
  font-weight: 600;
}

.sla.is-red {
  color: var(--zk-red);
  font-weight: 600;
}

.order-side {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.row-arrow {
  font-size: 12px;
  color: var(--zk-text-3);
  opacity: 0;
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out);
}

.order-row:hover .row-arrow {
  opacity: 1;
}

/* ---------------- 空态 ---------------- */
.list-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 34px 18px 30px;
  text-align: center;
}

.list-empty .el-icon {
  font-size: 20px;
  color: var(--zk-text-3);
}

.list-empty p {
  font-size: 13px;
  font-weight: 500;
  color: var(--zk-text-2);
}

.list-empty span {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
  max-width: 280px;
}

.list-empty .el-button {
  margin-top: 4px;
}

/* ---------------- 常见问题 ---------------- */
.faq-collapse {
  border-top: 0;
  border-bottom: 0;
}

.faq-collapse :deep(.el-collapse-item__header) {
  height: 40px;
  padding: 0 18px;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--zk-text);
  border-bottom: 1px solid var(--zk-border);
}

.faq-collapse :deep(.el-collapse-item__header:hover) {
  background: var(--zk-surface-2);
}

.faq-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid var(--zk-border);
}

.faq-collapse :deep(.el-collapse-item__content) {
  padding: 0 18px 14px;
}

.faq-collapse :deep(.el-collapse-item__arrow) {
  font-size: 12px;
  color: var(--zk-text-3);
  margin-left: 8px;
  /* 展开/收起时箭头跟着面板一起转到位（300ms，回弹 ≤6%） */
  transition: transform var(--zk-dur-slow) var(--zk-ease-spring);
}

.faq-cat {
  flex: 0 0 auto;
  font-size: 10.5px;
  font-weight: 500;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-xs);
  padding: 1px 6px;
}

.faq-q {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.faq-a {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--zk-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- 服务指引 ---------------- */
.guide-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 4px 18px 16px;
  list-style: none;
}

.guide-item {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.guide-no {
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  border-radius: var(--zk-radius-xs);
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--zk-text-2);
  background: var(--zk-surface-3);
  border: 1px solid var(--zk-border);
  font-variant-numeric: tabular-nums;
}

.guide-body {
  min-width: 0;
}

.guide-body b {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.guide-body p {
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
}

.guide-foot {
  margin-top: auto;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--zk-border);
  background: var(--zk-surface-2);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guide-foot-text {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.guide-foot-actions {
  display: flex;
  gap: 8px;
}

/* ================= 响应式 ================= */
@media (max-width: 1180px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    gap: 18px;
  }

  .hero-facts {
    padding-left: 0;
    padding-top: 16px;
    border-left: 0;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
  }

  .home-row--wide {
    grid-template-columns: minmax(0, 1fr);
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .hero {
    padding: 16px 14px;
  }

  .hero-left {
    gap: 12px;
  }

  .hero-avatar {
    width: 40px;
    height: 40px;
    flex-basis: 40px;
    font-size: 16px;
  }

  .hero-title {
    font-size: 16px;
  }

  .hero-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 14px;
  }

  /* 两行式行结构在窄屏自然堆叠，只收紧内边距 */
  .session-row {
    padding: 10px 14px;
  }

  .order-row {
    padding: 10px 14px;
  }

  .faq-collapse :deep(.el-collapse-item__header) {
    padding: 0 14px;
    height: auto;
    min-height: 40px;
  }

  .faq-collapse :deep(.el-collapse-item__content) {
    padding: 0 14px 12px;
  }

  .guide-list {
    padding: 4px 14px 14px;
  }

  .guide-foot {
    padding: 12px 14px 14px;
  }
}

@media (max-width: 480px) {
  .stat-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .stat-value {
    font-size: 28px;
  }
}
</style>
