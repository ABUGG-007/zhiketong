<template>
  <div class="shell" :class="{ collapsed, 'is-mobile-open': mobileOpen }">
    <a class="zk-skip-link" href="#main-content">跳到主内容</a>

    <transition name="zk-overlay">
      <div
        v-if="mobileOpen"
        class="sidebar-overlay"
        aria-hidden="true"
        @click="closeMobile"
      />
    </transition>

    <aside id="app-sidebar" class="sidebar" aria-label="主导航">
      <div class="brand">
        <div class="brand-logo" aria-hidden="true">
          <img src="/brand-mark.png" alt="" />
        </div>
        <transition name="zk-nav">
          <div v-if="!collapsed || mobileOpen" class="brand-text">
            <div class="brand-name">智客通</div>
            <div class="brand-sub">{{ isPlatform ? '平台运营控制台' : '管理控制台' }}</div>
          </div>
        </transition>
        <button
          v-if="mobileOpen"
          type="button"
          class="sidebar-close zk-press"
          aria-label="关闭导航菜单"
          @click="closeMobile"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <nav ref="navRef" class="nav" aria-label="功能菜单" v-reveal.group>
        <!-- 流动指示器：选中底色是一块绝对定位的 pill，随当前项 transform 滑动（模仿水滴导航的跟随感） -->
        <span
          class="nav-pill"
          :class="{ 'is-ready': pillReady }"
          :style="pillStyle"
          data-zk-reveal="off"
          aria-hidden="true"
        />
        <div v-for="group in groups" :key="group.name" class="nav-group">
          <transition name="zk-nav">
            <div v-if="!collapsed || mobileOpen" class="nav-group-label">{{ group.name }}</div>
          </transition>
          <el-tooltip
            v-for="(item, idx) in group.items"
            :key="item.path"
            :content="item.title"
            placement="right"
            :disabled="!collapsed || mobileOpen"
          >
            <router-link
              :to="item.path"
              class="nav-item"
              active-class="is-active"
              @click="closeMobile"
            >
              <el-icon class="nav-icon"><component :is="iconOf(item.icon)" /></el-icon>
              <transition name="zk-nav">
                <span
                  v-if="!collapsed || mobileOpen"
                  class="nav-text"
                  :style="{ '--zk-nav-delay': `${Math.min(idx, 6) * 45}ms` }"
                >{{ item.title }}</span>
              </transition>
              <transition name="zk-badge">
                <span
                  v-if="(!collapsed || mobileOpen) && item.name === 'admin-sessions' && waitingCount > 0"
                  class="nav-count"
                  :class="{ 'is-bumped': countBumped }"
                  :style="{ minWidth: countWidth }"
                  :aria-label="`${waitingCount} 个会话等待接入`"
                >
                  <transition name="zk-flip">
                    <span :key="waitingCount" class="nav-count-num">{{ waitingCount }}</span>
                  </transition>
                </span>
              </transition>
            </router-link>
          </el-tooltip>
        </div>
      </nav>

      <!-- 系统状态：导航栏底部通栏，与菜单同属一套骨架，不做独立卡片 -->
      <div class="sidebar-status" :class="{ mini: collapsed && !mobileOpen }">
        <template v-if="!collapsed || mobileOpen">
          <div class="status-main" role="status" :title="`MySQL ${health.mysql} · Redis ${health.redis}`">
            <i
              class="status-dot"
              :class="[healthTone, { 'zk-dot-live': health.status === 'loading' }]"
              aria-hidden="true"
            />
            <span class="status-text">{{ healthText }}</span>
          </div>
          <div class="status-meta">
            MySQL {{ healthLabel(health.mysql) }} · Redis {{ healthLabel(health.redis) }} · v1.0
          </div>
        </template>
        <i
          v-else
          class="status-dot"
          :class="[healthTone, { 'zk-dot-live': health.status === 'loading' }]"
          :title="healthText"
          aria-hidden="true"
        />
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button
            type="button"
            class="icon-btn menu-btn zk-press"
            aria-label="打开导航菜单"
            :aria-expanded="mobileOpen"
            aria-controls="app-sidebar"
            @click="toggleMobile"
          >
            <el-icon><Menu /></el-icon>
          </button>
          <button
            type="button"
            class="icon-btn collapse-btn zk-press"
            :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
            :aria-expanded="!collapsed"
            @click="collapsed = !collapsed"
          >
            <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </button>
          <div class="crumb" aria-label="面包屑">
            <span class="crumb-root">{{ isPlatform ? '平台运营' : '运营控制台' }}</span>
            <span class="crumb-sep" aria-hidden="true">/</span>
            <span class="crumb-current" aria-current="page">
              <!-- 标题随路由切换淡入：新标题从下方轻推入，旧标题上移退出 -->
              <transition name="zk-fade" mode="out-in">
                <span :key="route.path">{{ route.meta.title || '数据看板' }}</span>
              </transition>
            </span>
          </div>
        </div>

        <div class="topbar-right">
          <el-dropdown trigger="click" @command="onUserCommand">
            <div class="user-trigger" role="button" tabindex="0" aria-label="用户菜单">
              <div class="avatar" aria-hidden="true">{{ avatarText }}</div>
              <div class="user-meta">
                <div class="user-name">{{ user?.nickname || user?.username || '未登录' }}</div>
                <div class="user-role">{{ roleLabel }}</div>
              </div>
              <el-icon class="caret" aria-hidden="true"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ user?.username }} · {{ roleLabel }}
                </el-dropdown-item>
                <el-dropdown-item command="dashboard" divided>
                  <el-icon><DataAnalysis /></el-icon> {{ isPlatform ? '租户与套餐' : '管理看板' }}
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main id="main-content" class="content" tabindex="-1">
        <router-view v-slot="{ Component }">
          <transition name="zk-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Aim,
  AlarmClock,
  ArrowDown,
  Avatar,
  Bell,
  ChatLineRound,
  ChatLineSquare,
  Close,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  DataLine,
  Document,
  Expand,
  Fold,
  Grid,
  Headset,
  Key,
  Memo,
  Menu,
  Monitor,
  OfficeBuilding,
  Opportunity,
  Position,
  Setting,
  SetUp,
  SwitchButton,
  Tickets,
  Timer,
  TrendCharts,
  User,
  UserFilled,
  Wallet
} from '@element-plus/icons-vue'
import { clearAuth, getUser } from '@/api/http'
import { getAgentQueue, getHealth, logout as logoutApi } from '@/api'
import { ROLE_DICT, dictLabel } from '@/utils/dict'

const router = useRouter()
const route = useRoute()

const collapsed = ref(false)
const mobileOpen = ref(false)
const user = ref(getUser())

/* ---------- 流动指示器：测量当前菜单项，pill 只靠 transform 滑过去 ---------- */
const navRef = ref(null)
const pillStyle = ref({ opacity: 0 })
const pillReady = ref(false)

function measurePill() {
  const nav = navRef.value
  if (!nav) return
  const active = nav.querySelector('.nav-item.is-active')
  // 当前路由不在菜单里（如 403）时淡出 pill，不残留旧位置
  if (!active) {
    pillStyle.value = { ...pillStyle.value, opacity: 0 }
    return
  }
  pillStyle.value = {
    height: `${active.offsetHeight}px`,
    transform: `translate3d(0, ${active.offsetTop}px, 0)`,
    opacity: 1
  }
}

function toggleMobile() {
  mobileOpen.value = !mobileOpen.value
}

function closeMobile() {
  mobileOpen.value = false
}

watch(
  () => route.path,
  () => {
    mobileOpen.value = false
    // 等 is-active 落到新菜单项后再滑，避免指示器先跳再滑
    nextTick(measurePill)
  }
)

watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  nextTick(measurePill)
})

watch(collapsed, () => nextTick(measurePill))

const roleLabel = computed(() => dictLabel(ROLE_DICT, user.value?.roleCode, '访客'))
// 平台管理员与租户管理员共用这套骨架，仅导航范围与面包屑文案不同
const isPlatform = computed(() => user.value?.roleCode === 'PLATFORM_ADMIN')
const avatarText = computed(() => {
  const nickname = user.value?.nickname || user.value?.username || '客'
  return nickname.slice(0, 1).toUpperCase()
})

/* ---------- 健康检查 ---------- */
const health = ref({ status: 'loading', mysql: '', redis: '' })
let healthTimer = null

const healthTone = computed(() => {
  if (health.value.status === 'loading') return 'slate'
  if (health.value.status === 'UP') return 'teal'
  if (health.value.status === 'DEGRADED') return 'amber'
  return 'red'
})
const healthText = computed(() => {
  if (health.value.status === 'loading') return '正在检测…'
  if (health.value.status === 'UP') return '运行正常'
  if (health.value.status === 'DEGRADED') return '部分依赖异常'
  if (health.value.status === 'DOWN') return '服务不可用'
  return '状态未知'
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
    const mysqlUp = data?.checks?.mysql?.status === 'UP'
    const redisUp = data?.checks?.redis?.status === 'UP'
    health.value = {
      status: data?.status === 'UP' && mysqlUp && redisUp ? 'UP' : data?.status || 'DOWN',
      mysql: data?.checks?.mysql?.status || 'UNKNOWN',
      redis: data?.checks?.redis?.status || 'UNKNOWN'
    }
  } catch {
    health.value = { status: 'DOWN', mysql: 'UNKNOWN', redis: 'UNKNOWN' }
  }
}

/* ---------- 侧栏菜单（由路由 meta 生成） ---------- */
// 线性图标：导航统一描边风格，与卡片 1px 描边的克制基调一致
const iconMap = {
  Aim,
  DataAnalysis,
  DataLine,
  Wallet,
  TrendCharts,
  ChatLineSquare,
  Tickets,
  Headset,
  Collection,
  Connection,
  User,
  UserFilled,
  Avatar,
  ChatLineRound,
  Key,
  Document,
  Cpu,
  Grid,
  Timer,
  Memo,
  Monitor,
  Setting,
  SetUp,
  OfficeBuilding,
  Bell,
  AlarmClock,
  Opportunity,
  Position
}
const iconOf = (name) => iconMap[name] || DataAnalysis

// 等待队列数量：用于会话菜单上的红色角标（失败静默）
const waitingCount = ref(0)
const countBumped = ref(false)
let bumpTimer = 0

// 一位数是圆、两位数变胶囊：只过渡 min-width，角标不参与其它元素排版
const countWidth = computed(() => {
  const value = waitingCount.value
  if (value > 99) return '28px'
  if (value > 9) return '22px'
  return '18px'
})

// 数字变化时角标弹一下（1 → 1.18 → 1，300ms），翻牌由模板内的 zk-flip 过渡完成
watch(waitingCount, () => {
  countBumped.value = false
  requestAnimationFrame(() => {
    countBumped.value = true
    window.clearTimeout(bumpTimer)
    bumpTimer = window.setTimeout(() => {
      countBumped.value = false
    }, 320)
  })
})

const groups = computed(() => {
  const collected = []
  // 仅收集 /admin/* 与 /platform/* 子路由；菜单与路由守卫共用 meta.roles
  const roleCode = user.value?.roleCode || ''
  const prefixes = ['/admin/', '/platform/']
  for (const record of router.getRoutes()) {
    const meta = record.meta || {}
    if (!prefixes.some((prefix) => record.path.startsWith(prefix))) continue
    if (!meta.group || !meta.title) continue
    if (Array.isArray(meta.roles) && meta.roles.length && !meta.roles.includes(roleCode)) continue
    let group = collected.find((g) => g.name === meta.group)
    if (!group) {
      group = { name: meta.group, items: [] }
      collected.push(group)
    }
    group.items.push({
      path: record.path,
      name: record.name,
      title: meta.title,
      icon: meta.icon,
      order: meta.order || 99
    })
  }
  collected.forEach((g) => g.items.sort((a, b) => a.order - b.order))
  collected.sort((a, b) => Math.min(...a.items.map((i) => i.order)) - Math.min(...b.items.map((i) => i.order)))
  return collected
})

// 菜单分组变化（角色解析、路由注册）后重算指示器位置
watch(groups, () => nextTick(measurePill))

// 等待接入数量：侧栏会话菜单角标（接口未就绪时静默）
async function loadWaiting() {
  try {
    const page = await getAgentQueue({ pageNum: 1, pageSize: 1 })
    waitingCount.value = Number(page?.queueSize ?? page?.total ?? 0) || 0
  } catch {
    waitingCount.value = 0
  }
}

/* ---------- 用户操作 ---------- */
async function onUserCommand(command) {
  if (command === 'logout') {
    // 先通知后端注销登录态，再清理本地凭证；接口异常不阻塞退出
    await logoutApi().catch(() => {})
    clearAuth()
    ElMessage.success('已安全退出登录')
    router.replace({ name: 'login' })
  } else if (command === 'dashboard') {
    router.push(isPlatform.value ? '/platform/tenants' : '/admin/dashboard')
  }
}

onMounted(() => {
  loadHealth()
  // 等待队列属于租户业务指标，平台管理员不展示
  if (!isPlatform.value) loadWaiting()
  healthTimer = window.setInterval(() => {
    loadHealth()
    if (!isPlatform.value) loadWaiting()
  }, 60000)
  // 首屏先定位、等一帧真正画上去后再开过渡：避免 pill 从顶部滑到目标位置的假动画
  nextTick(() => {
    measurePill()
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        pillReady.value = true
      })
    })
  })
  window.addEventListener('resize', measurePill)
})

onBeforeUnmount(() => {
  if (healthTimer) window.clearInterval(healthTimer)
  if (bumpTimer) window.clearTimeout(bumpTimer)
  window.removeEventListener('resize', measurePill)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--zk-bg);
}

/* ---------------- 侧栏：浅色、扁平、与内容同色系 ---------------- */
.sidebar {
  width: var(--zk-sidebar-w);
  flex: 0 0 var(--zk-sidebar-w);
  background: #fbfbfc;
  color: var(--zk-text-2);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  border-right: 1px solid var(--zk-border);
  transition: width var(--zk-dur-base) var(--zk-ease-emphasis),
    flex-basis var(--zk-dur-base) var(--zk-ease-emphasis);
  overflow: hidden;
  z-index: 30;
}

.shell.collapsed .sidebar {
  width: 64px;
  flex-basis: 64px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 14px 14px 12px;
  min-height: 56px;
}

.brand-logo {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: var(--zk-radius);
  background: var(--zk-surface);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.brand-text {
  overflow: hidden;
  white-space: nowrap;
}

.brand-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--zk-text);
  letter-spacing: 0;
}

.brand-sub {
  font-size: 12.5px;
  color: var(--zk-text-3);
  letter-spacing: 0;
  margin-top: 1px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0 10px;
  position: relative;
  /* 隐藏细滚动条：pill 的左右留白才能与菜单项严格对齐（仍可滚轮 / 键盘滚动） */
  scrollbar-width: none;
}

.nav::-webkit-scrollbar {
  display: none;
}

/* 流动指示器：绝对定位的圆角底片，只动 transform / opacity（模仿水滴导航的跟随感） */
.nav-pill {
  position: absolute;
  top: 0;
  left: 8px;
  right: 8px;
  height: 34px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-accent);
  pointer-events: none;
  opacity: 0;
  will-change: transform;
}

.nav-pill.is-ready {
  transition: transform var(--zk-dur-slow) var(--zk-ease-emphasis),
    opacity var(--zk-dur-base) var(--zk-ease-out);
}

/* 折叠/展开：品牌与菜单文字淡出淡入，进场 45ms 错开（只动 opacity / transform） */
.zk-nav-enter-active {
  transition: opacity var(--zk-dur-base) var(--zk-ease-out),
    transform var(--zk-dur-base) var(--zk-ease-out);
  transition-delay: var(--zk-nav-delay, 0ms);
}

.zk-nav-leave-active {
  position: absolute;
  transition: opacity var(--zk-dur-fast) var(--zk-ease-in),
    transform var(--zk-dur-fast) var(--zk-ease-in);
}

.zk-nav-enter-from,
.zk-nav-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

/* 分组标题退场更快：离场瞬间菜单项会立刻上移，缩短叠字的时间窗 */
.zk-nav-leave-active.nav-group-label {
  transition-duration: var(--zk-dur-instant);
}

/* 分组：上方细分割线 + 中灰色加粗标题，明确模块边界 */
.nav-group + .nav-group {
  margin-top: 6px;
  padding-top: 2px;
  border-top: 1px solid var(--zk-border);
}

.nav-group-label {
  padding: 8px 16px 5px;
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--zk-text-2);
  --zk-nav-delay: 40ms;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 34px;
  margin: 0 8px;
  padding: 0 10px;
  border-radius: var(--zk-radius-sm);
  color: var(--zk-text-2);
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
  white-space: nowrap;
}

.nav-item + .nav-item {
  margin-top: 1px;
}

.collapsed .nav-item {
  justify-content: center;
  padding: 0;
  margin: 1px 8px;
}

.nav-item:hover {
  background: #f2f2f4;
  color: var(--zk-text);
}

/* 选中态：底色交给 .nav-pill，菜单项本身只切文字对比，避免切换时整块瞬移 */
.nav-item.is-active {
  color: #ffffff;
  font-weight: 600;
  box-shadow: none;
}

.nav-item.is-active:hover {
  background: transparent;
  color: #ffffff;
}

.nav-item.is-active .nav-icon {
  color: #ffffff;
}

.nav-item.is-active .nav-count {
  background: rgba(255, 255, 255, 0.24);
  color: #ffffff;
}

.nav-icon {
  font-size: 17px;
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.nav-icon svg {
  width: 1em;
  height: 1em;
  display: block;
}

.nav-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}

.nav-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--zk-red-soft);
  color: var(--zk-red);
  font-size: 11px;
  font-weight: 600;
  display: grid;
  place-items: center;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  /* 一位数圆形、两位数平滑变宽成胶囊（模仿 58 红点数字角标） */
  transition: min-width var(--zk-dur-base) var(--zk-ease-out);
}

/* 数字变化时弹一下：1 → 1.18 → 1，300ms */
.nav-count.is-bumped {
  animation: nav-count-bump var(--zk-dur-slow) var(--zk-ease-spring);
}

@keyframes nav-count-bump {
  0% {
    transform: scale(1);
  }
  40% {
    transform: scale(1.18);
  }
  100% {
    transform: scale(1);
  }
}

/* 翻牌：旧数字向上滑走、新数字从下方滑进来，两片叠在同一格 */
.nav-count-num {
  grid-area: 1 / 1;
}

.zk-flip-enter-active,
.zk-flip-leave-active {
  transition: opacity 220ms var(--zk-ease-out), transform 220ms var(--zk-ease-out);
}

.zk-flip-enter-from {
  opacity: 0;
  transform: translateY(9px);
}

.zk-flip-leave-to {
  opacity: 0;
  transform: translateY(-9px);
}

/* 0 ↔ N：没有等待时缩小淡出，出现时从 0.6 弹入（可逆） */
.zk-badge-enter-active {
  transition: opacity var(--zk-dur-base) var(--zk-ease-out),
    transform 220ms var(--zk-ease-spring);
}

.zk-badge-leave-active {
  transition: opacity 180ms var(--zk-ease-in), transform 180ms var(--zk-ease-in);
}

.zk-badge-enter-from,
.zk-badge-leave-to {
  opacity: 0;
  transform: scale(0.6);
}

/* 底部系统状态：通栏融入导航，细分割线分隔，小字号浅灰 */
.sidebar-status {
  flex: 0 0 auto;
  padding: 10px 16px 14px;
  border-top: 1px solid var(--zk-border);
}

.sidebar-status.mini {
  display: grid;
  place-items: center;
  padding: 14px 0;
}

.status-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: #a1a1aa;
}

.status-dot.teal {
  background: #10b981;
  color: #10b981;
}
.status-dot.amber {
  background: #f59e0b;
  color: #f59e0b;
}
.status-dot.red {
  background: #f43f5e;
  color: #f43f5e;
}
.status-dot.slate {
  color: #a1a1aa;
}

.status-text {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--zk-text-2);
}

.status-meta {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

/* ---------------- 主区域 ---------------- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--zk-topbar-h);
  flex: 0 0 var(--zk-topbar-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px 0 16px;
  background: var(--zk-surface);
  border-bottom: 1px solid var(--zk-border);
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.icon-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--zk-radius-sm);
  border: 0;
  background: transparent;
  color: var(--zk-text-3);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
}

.icon-btn:hover {
  color: var(--zk-text);
  background: var(--zk-surface-3);
}

.crumb {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--zk-text-2);
  min-width: 0;
  margin-left: 4px;
}

.crumb-root {
  color: var(--zk-text-2);
  letter-spacing: 0;
}

.crumb-sep {
  color: #c4c5cb;
}

.crumb-current {
  color: var(--zk-text);
  font-weight: 600;
  font-size: 13px;
}

/* 路由切换时标题靠 zk-fade 过渡，内层需要可过渡的行内盒 */
.crumb-current > span {
  display: inline-block;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 3px 4px;
  border-radius: var(--zk-radius-sm);
  outline: none;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.user-trigger:hover {
  background: var(--zk-surface-3);
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: var(--zk-ink-800);
}

.user-meta {
  line-height: 1.25;
}

.user-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--zk-text);
}

.user-role {
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.caret {
  font-size: 11px;
  color: var(--zk-text-3);
}

.content {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1080px) {
  .user-meta {
    display: none;
  }
}

@media (max-width: 900px) {
  .crumb-root,
  .crumb-sep {
    display: none;
  }
  .topbar {
    padding: 0 12px;
  }
}

/* ---------------- 移动端抽屉导航 ---------------- */
.sidebar {
  height: 100vh;
  height: 100dvh;
}

.sidebar-close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-left: auto;
  border-radius: var(--zk-radius-sm);
  border: 0;
  background: transparent;
  color: var(--zk-text-3);
  cursor: pointer;
}

.sidebar-close:hover {
  background: var(--zk-surface-3);
  color: var(--zk-text);
}

.sidebar-overlay {
  display: none;
}

/* 移动端遮罩 200ms 淡入；抽屉面板 320ms 滑入，关闭走同一条路径的反向 */
.zk-overlay-enter-active {
  transition: opacity var(--zk-dur-base) var(--zk-ease-out);
}

.zk-overlay-leave-active {
  transition: opacity var(--zk-dur-base) var(--zk-ease-in);
}

.zk-overlay-enter-from,
.zk-overlay-leave-to {
  opacity: 0;
}

.menu-btn {
  display: none;
}

.sidebar :focus-visible,
.topbar :focus-visible {
  outline: 2px solid var(--zk-accent);
  outline-offset: 2px;
}

@media (max-width: 1024px) {
  .menu-btn {
    display: grid;
  }

  .collapse-btn {
    display: none;
  }

  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    width: 264px;
    flex-basis: 264px;
    height: 100dvh;
    transform: translateX(-102%);
    transition: transform 320ms var(--zk-ease-emphasis);
    z-index: var(--zk-z-overlay);
    box-shadow: none;
  }

  .shell .sidebar,
  .shell.collapsed .sidebar {
    width: 264px;
    flex-basis: 264px;
  }

  .shell.is-mobile-open .sidebar {
    transform: translateX(0);
    box-shadow: var(--zk-shadow-lg);
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(9, 9, 11, 0.32);
    z-index: calc(var(--zk-z-overlay) - 1);
  }

  .main {
    width: 100%;
  }

  .content {
    min-height: calc(100dvh - var(--zk-topbar-h));
  }
}

@media (max-width: 640px) {
  .topbar {
    height: 52px;
    flex-basis: 52px;
    padding: 0 10px;
  }

  .topbar-left {
    gap: 2px;
  }

  .icon-btn {
    width: 32px;
    height: 32px;
  }

  .user-meta {
    display: none;
  }

  .user-trigger {
    padding: 2px;
  }

  .crumb {
    font-size: 12px;
  }

  .content {
    min-height: calc(100dvh - 52px);
  }
}
</style>
