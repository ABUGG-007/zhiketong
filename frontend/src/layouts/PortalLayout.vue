<template>
  <div class="portal">
    <a class="zk-skip-link" href="#portal-main">跳到主内容</a>

    <header class="portal-header">
      <div class="header-inner">
        <router-link :to="homePath" class="brand" :aria-label="`${brandTitle}首页`">
          <span class="brand-logo" aria-hidden="true">
            <img src="/brand-mark.png" alt="" />
          </span>
          <span class="brand-text">
            <span class="brand-name">{{ brandTitle }}</span>
            <span class="brand-sub">{{ brandSub }}</span>
          </span>
        </router-link>

        <nav ref="navRef" class="portal-nav" aria-label="主要导航">
          <!-- 流动指示器：横向选中底片，只靠 transform 位移与缩放（不动 width） -->
          <span
            class="nav-pill"
            :class="{ 'is-ready': pillReady }"
            :style="pillStyle"
            aria-hidden="true"
          />
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            active-class="is-active"
          >
            <el-icon class="nav-icon" aria-hidden="true">
              <component :is="iconOf(item.icon)" />
            </el-icon>
            <span>{{ item.title }}</span>
          </router-link>
        </nav>

        <div class="header-right">
          <div
            class="health-chip"
            :class="healthTone"
            role="status"
            :title="healthTitle"
            :aria-label="healthTitle"
          >
            <i class="health-dot" :class="{ 'zk-dot-live': health.status === 'loading' }" aria-hidden="true" />
            <span class="health-text">{{ healthText }}</span>
          </div>

          <el-dropdown trigger="click" @command="onCommand">
            <button type="button" class="user-trigger" aria-label="用户菜单">
              <span class="avatar" aria-hidden="true">{{ avatarText }}</span>
              <span class="user-meta">
                <span class="user-name">{{ user?.nickname || user?.username || '未登录' }}</span>
                <span class="user-role">{{ roleLabel }}</span>
              </span>
              <el-icon class="caret" aria-hidden="true"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ user?.username }} · {{ roleLabel }}
                </el-dropdown-item>
                <el-dropdown-item command="home" divided>
                  <el-icon><House /></el-icon> {{ portalKind === 'agent' ? '坐席工作台' : '用户首页' }}
                </el-dropdown-item>
                <el-dropdown-item v-if="portalKind === 'user'" command="profile">
                  <el-icon><User /></el-icon> 个人资料
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <button
            type="button"
            class="menu-btn zk-press"
            aria-label="打开导航菜单"
            :aria-expanded="mobileOpen"
            aria-controls="portal-drawer"
            @click="mobileOpen = true"
          >
            <el-icon><Menu /></el-icon>
          </button>
        </div>
      </div>
    </header>

    <main id="portal-main" class="portal-main" tabindex="-1">
      <router-view v-slot="{ Component }">
        <transition name="zk-fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </main>

    <el-drawer
      id="portal-drawer"
      v-model="mobileOpen"
      :title="brandTitle"
      direction="rtl"
      size="min(300px, 86vw)"
    >
      <nav class="drawer-nav" aria-label="移动端导航">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="drawer-link"
          active-class="is-active"
          @click="mobileOpen = false"
        >
          <el-icon aria-hidden="true"><component :is="iconOf(item.icon)" /></el-icon>
          <span>{{ item.title }}</span>
        </router-link>
        <div class="drawer-divider" aria-hidden="true" />
        <button type="button" class="drawer-link drawer-logout" @click="onCommand('logout')">
          <el-icon aria-hidden="true"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </button>
      </nav>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Avatar,
  ChatDotRound,
  ChatLineSquare,
  Collection,
  DataAnalysis,
  Document,
  Headset,
  House,
  Menu,
  SwitchButton,
  Tickets,
  User
} from '@element-plus/icons-vue'
import { getHealth, logout as logoutApi } from '@/api'
import { clearAuth, getUser } from '@/api/http'
import { ROLE_DICT, dictLabel } from '@/utils/dict'

const route = useRoute()
const router = useRouter()

const user = getUser()

const mobileOpen = ref(false)

/* ---------- 门户类型：由 /agent、/user 父路由 meta.portalKind 决定 ---------- */
const portalKind = computed(() => route.meta?.portalKind === 'agent' ? 'agent' : 'user')
const portalPrefix = computed(() => (portalKind.value === 'agent' ? '/agent' : '/user'))
const homePath = computed(() =>
  portalKind.value === 'agent' ? '/agent/workbench' : '/user/home'
)
const brandTitle = computed(() =>
  portalKind.value === 'agent' ? '智客通 · 坐席工作台' : '智客通 · 用户中心'
)
const brandSub = computed(() => (portalKind.value === 'agent' ? '坐席端' : '用户端'))

const roleLabel = computed(() => dictLabel(ROLE_DICT, user?.roleCode, '访客'))
const avatarText = computed(() =>
  (user?.nickname || user?.username || '客').slice(0, 1).toUpperCase()
)

/* ---------- 导航：仅当前门户父路径下的子路由 ---------- */
const iconMap = {
  DataAnalysis,
  ChatDotRound,
  ChatLineSquare,
  Tickets,
  Headset,
  Collection,
  User,
  Avatar,
  Document,
  House
}
const iconOf = (name) => iconMap[name] || DataAnalysis

const navItems = computed(() => {
  const prefix = `${portalPrefix.value}/`
  return router
    .getRoutes()
    .filter((record) => record.path.startsWith(prefix) && record.meta?.title)
    .sort((a, b) => (a.meta.order || 99) - (b.meta.order || 99))
    .map((record) => ({
      path: record.path,
      title: record.meta.title,
      icon: record.meta.icon
    }))
})

/* ---------- 流动指示器：横向底片跟随当前导航项 ---------- */
const navRef = ref(null)
const pillStyle = ref({ opacity: 0 })
const pillReady = ref(false)
// 底片的基准宽度：真实宽度用 scaleX 表达，避免过渡 width 触发排版
const PILL_BASE = 100

function measurePill() {
  const nav = navRef.value
  if (!nav) return
  const active = nav.querySelector('.nav-item.is-active')
  if (!active) {
    pillStyle.value = { ...pillStyle.value, opacity: 0 }
    return
  }
  pillStyle.value = {
    height: `${active.offsetHeight}px`,
    transform: `translate3d(${active.offsetLeft}px, ${active.offsetTop}px, 0) scaleX(${
      active.offsetWidth / PILL_BASE
    })`,
    opacity: 1
  }
}

watch(
  () => route.path,
  () => nextTick(measurePill)
)

watch(navItems, () => nextTick(measurePill))

/* ---------- 健康状态（简化） ---------- */
const health = ref({ status: 'loading' })
const healthTone = computed(() => {
  if (health.value.status === 'loading') return 'slate'
  if (health.value.status === 'UP') return 'teal'
  return 'red'
})
const healthText = computed(() => {
  if (health.value.status === 'loading') return '检测中'
  return health.value.status === 'UP' ? '系统正常' : '服务离线'
})
const healthTitle = computed(() =>
  health.value.status === 'UP' ? '服务运行正常' : '服务未连接'
)
let healthTimer = null

async function loadHealth() {
  try {
    const data = await getHealth()
    health.value = { status: data?.status === 'UP' ? 'UP' : 'DOWN' }
  } catch {
    health.value = { status: 'DOWN' }
  }
}

/* ---------- 交互 ---------- */
function onCommand(command) {
  mobileOpen.value = false
  if (command === 'logout') {
    // 同步注销后端登录态，接口失败也允许本地退出
    logoutApi().catch(() => {})
    clearAuth()
    ElMessage.success('已退出登录')
    router.replace({ name: 'login' })
    return
  }
  if (command === 'home') {
    router.push(homePath.value)
    return
  }
  if (command === 'profile') {
    router.push('/user/profile')
  }
}

watch(
  () => route.path,
  () => {
    mobileOpen.value = false
  }
)

watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

onMounted(() => {
  loadHealth()
  healthTimer = window.setInterval(loadHealth, 60000)
  // 首屏先定位、等一帧真正画上去后再开过渡，避免底片从左侧滑入
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
  window.removeEventListener('resize', measurePill)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.portal {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--zk-bg);
}

/* ---------------- 顶栏 ---------------- */
.portal-header {
  position: sticky;
  top: 0;
  z-index: var(--zk-z-topbar, 20);
  background: var(--zk-surface);
  border-bottom: 1px solid var(--zk-border);
}

.header-inner {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  height: 58px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  border-radius: 6px;
}

.brand-logo {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: var(--zk-radius);
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--zk-surface);
}

.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.brand-name {
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--zk-text);
  white-space: nowrap;
}

.brand-sub {
  font-size: 10.5px;
  letter-spacing: 0;
  color: var(--zk-text-3);
}

/* 横向导航：中性选中态，不抢内容 */
.portal-nav {
  display: flex;
  align-items: center;
  gap: 1px;
  min-width: 0;
  flex: 1;
  overflow-x: auto;
  scrollbar-width: none;
  position: relative;
}

/* 流动指示器：横向底片，用 scaleX 表达宽度，只过渡 transform / opacity */
.nav-pill {
  position: absolute;
  top: 0;
  left: 0;
  width: 100px;
  height: 32px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-3);
  transform-origin: left center;
  pointer-events: none;
  opacity: 0;
  will-change: transform;
}

.nav-pill.is-ready {
  transition: transform var(--zk-dur-slow) var(--zk-ease-emphasis),
    opacity var(--zk-dur-base) var(--zk-ease-out);
}

.portal-nav::-webkit-scrollbar {
  display: none;
}

.nav-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--zk-radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--zk-text-2);
  white-space: nowrap;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
}

.nav-item:hover {
  background: var(--zk-surface-3);
  color: var(--zk-text);
}

.nav-item.is-active {
  color: var(--zk-text);
  background: transparent;
  box-shadow: none;
}

.nav-item.is-active .nav-icon {
  color: var(--zk-accent);
}

.nav-icon {
  font-size: 14px;
  color: var(--zk-text-3);
}

/* 右侧 */
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  flex: 0 0 auto;
}

.health-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 2px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 400;
  border: 0;
  color: var(--zk-text-3);
  white-space: nowrap;
}

.health-chip.teal {
  color: var(--zk-text-2);
}

.health-chip.red {
  color: var(--zk-red);
}

.health-chip.slate {
  color: var(--zk-text-3);
}

.health-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.health-chip.red .health-dot {
  background: #f43f5e;
}

.health-chip.slate .health-dot {
  background: #a1a1aa;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 4px;
  border: 0;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  cursor: pointer;
  font: inherit;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.user-trigger:hover {
  background: var(--zk-surface-3);
}

.avatar {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  color: #ffffff;
  background: var(--zk-ink-800);
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  text-align: left;
  line-height: 1.25;
}

.user-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--zk-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.caret {
  font-size: 11px;
  color: var(--zk-text-3);
}

.menu-btn {
  display: none;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  color: var(--zk-text-2);
  cursor: pointer;
}

.menu-btn:hover {
  background: var(--zk-surface-3);
}

.portal-main {
  flex: 1;
  min-width: 0;
}

/* ---------------- 移动端抽屉 ---------------- */
.drawer-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.drawer-link {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 40px;
  padding: 0 10px;
  border-radius: var(--zk-radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--zk-text-2);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.drawer-link:hover {
  background: var(--zk-surface-3);
}

.drawer-link.is-active {
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}

.drawer-divider {
  height: 1px;
  background: var(--zk-border);
  margin: 8px 4px;
}

.drawer-logout {
  color: var(--zk-red);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 980px) {
  .portal-nav {
    display: none;
  }

  .menu-btn {
    display: inline-flex;
  }

  .brand-name {
    font-size: 13px;
  }

  .user-meta {
    display: none;
  }
}

@media (max-width: 640px) {
  .header-inner {
    padding: 0 14px;
    gap: 10px;
    height: 54px;
  }

  .brand-sub {
    display: none;
  }

  .health-text {
    display: none;
  }
}
</style>
