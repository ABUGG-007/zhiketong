<template>
  <div class="shell" :class="{ collapsed, 'is-mobile-open': mobileOpen }">
    <a class="zk-skip-link" href="#main-content">跳到主内容</a>

    <div
      v-if="mobileOpen"
      class="sidebar-overlay"
      aria-hidden="true"
      @click="closeMobile"
    />

    <aside id="app-sidebar" class="sidebar" aria-label="主导航">
      <div class="brand">
        <div class="brand-logo" aria-hidden="true">智</div>
        <div v-if="!collapsed || mobileOpen" class="brand-text">
          <div class="brand-name">智客通</div>
          <div class="brand-sub">管理控制台</div>
        </div>
        <button
          v-if="mobileOpen"
          type="button"
          class="sidebar-close"
          aria-label="关闭导航菜单"
          @click="closeMobile"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <nav class="nav" aria-label="功能菜单">
        <div v-for="group in groups" :key="group.name" class="nav-group">
          <div v-if="!collapsed || mobileOpen" class="nav-group-label">{{ group.name }}</div>
          <el-tooltip
            v-for="item in group.items"
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
              <span v-if="!collapsed || mobileOpen" class="nav-text">{{ item.title }}</span>
              <span
                v-if="(!collapsed || mobileOpen) && item.name === 'admin-sessions' && waitingCount > 0"
                class="nav-count"
                :aria-label="`${waitingCount} 个会话等待接入`"
              >
                {{ waitingCount }}
              </span>
            </router-link>
          </el-tooltip>
        </div>
      </nav>

      <div class="sidebar-foot">
        <div class="side-card" :class="{ mini: collapsed && !mobileOpen }">
          <div class="side-row">
            <i class="side-dot" :class="healthTone" aria-hidden="true" />
            <span v-if="!collapsed || mobileOpen" class="side-label">系统服务</span>
          </div>
          <div v-if="!collapsed || mobileOpen" class="side-value">{{ healthText }}</div>
          <div v-if="!collapsed || mobileOpen" class="side-note">v1.0 · 实时数据</div>
        </div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button
            type="button"
            class="icon-btn menu-btn"
            aria-label="打开导航菜单"
            :aria-expanded="mobileOpen"
            aria-controls="app-sidebar"
            @click="toggleMobile"
          >
            <el-icon><Menu /></el-icon>
          </button>
          <button
            type="button"
            class="icon-btn collapse-btn"
            :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
            :aria-expanded="!collapsed"
            @click="collapsed = !collapsed"
          >
            <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </button>
          <div class="crumb" aria-label="面包屑">
            <span class="crumb-root">运营控制台</span>
            <span class="crumb-sep" aria-hidden="true">/</span>
            <span class="crumb-current" aria-current="page">{{ route.meta.title || '数据看板' }}</span>
          </div>
        </div>

        <div class="topbar-right">
          <div
            class="health-badge"
            :class="healthTone"
            role="status"
            :title="`MySQL ${health.mysql} · Redis ${health.redis}`"
          >
            <i class="health-dot" aria-hidden="true" />
            <span>{{ healthShort }}</span>
          </div>
          <span class="topbar-divider" aria-hidden="true" />
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
                  <el-icon><DataAnalysis /></el-icon> 管理看板
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Avatar,
  ChatDotRound,
  ChatLineSquare,
  Close,
  Collection,
  DataAnalysis,
  Document,
  Expand,
  Fold,
  Headset,
  Menu,
  SwitchButton,
  Tickets,
  User
} from '@element-plus/icons-vue'
import { clearAuth, getUser } from '@/api/http'
import { getHealth, getWaitingQueue } from '@/api'
import { ROLE_DICT, dictLabel } from '@/utils/dict'

const router = useRouter()
const route = useRoute()

const collapsed = ref(false)
const mobileOpen = ref(false)
const user = ref(getUser())

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
  }
)

watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

const roleLabel = computed(() => dictLabel(ROLE_DICT, user.value?.roleCode, '访客'))
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
  if (health.value.status === 'DOWN') return '后端不可用'
  return '状态未知'
})
const healthShort = computed(() => {
  if (health.value.status === 'loading') return '检测中'
  if (health.value.status === 'UP') return '系统正常'
  if (health.value.status === 'DEGRADED') return '依赖降级'
  if (health.value.status === 'DOWN') return '后端离线'
  return '状态未知'
})

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
const iconMap = {
  DataAnalysis,
  ChatDotRound,
  ChatLineSquare,
  Tickets,
  Headset,
  Collection,
  User,
  Avatar,
  Document
}
const iconOf = (name) => iconMap[name] || DataAnalysis

// 等待队列数量：用于会话菜单上的红色角标（失败静默）
const waitingCount = ref(0)

const groups = computed(() => {
  const collected = []
  // 仅收集 /admin/* 子路由；菜单与路由守卫共用 meta.roles
  const roleCode = user.value?.roleCode || ''
  for (const record of router.getRoutes()) {
    const meta = record.meta || {}
    if (!record.path.startsWith('/admin/')) continue
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

// 等待接入数量：侧栏会话菜单角标（接口未就绪时静默）
async function loadWaiting() {
  try {
    const list = await getWaitingQueue()
    waitingCount.value = Array.isArray(list) ? list.length : 0
  } catch {
    waitingCount.value = 0
  }
}

/* ---------- 用户操作 ---------- */
function onUserCommand(command) {
  if (command === 'logout') {
    clearAuth()
    ElMessage.success('已安全退出登录')
    router.replace({ name: 'login' })
  } else if (command === 'dashboard') {
    router.push('/admin/dashboard')
  }
}

onMounted(() => {
  loadHealth()
  loadWaiting()
  healthTimer = window.setInterval(() => {
    loadHealth()
    loadWaiting()
  }, 60000)
})

onBeforeUnmount(() => {
  if (healthTimer) window.clearInterval(healthTimer)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--zk-bg);
}

/* ---------------- 侧栏 ---------------- */
.sidebar {
  width: var(--zk-sidebar-w);
  flex: 0 0 var(--zk-sidebar-w);
  background: radial-gradient(120% 90% at 0% 0%, #14203a 0%, transparent 55%),
    linear-gradient(180deg, #0b1220 0%, #0f172a 52%, #101a2e 100%);
  color: #cbd5e1;
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
  transition: width 0.22s ease, flex-basis 0.22s ease;
  overflow: hidden;
  z-index: 30;
}

.shell.collapsed .sidebar {
  width: 76px;
  flex-basis: 76px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 18px 18px 16px;
  min-height: 70px;
}

.brand-logo {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 11px;
  background: linear-gradient(140deg, #14b8a6 0%, #0ea5a4 45%, #4f46e5 100%);
  color: #ffffff;
  font-size: 18px;
  font-weight: 700;
  display: grid;
  place-items: center;
  box-shadow: 0 8px 18px -8px rgba(20, 184, 166, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.16);
  letter-spacing: 0;
}

.brand-text {
  overflow: hidden;
  white-space: nowrap;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 1px;
}

.brand-sub {
  font-size: 10px;
  color: #7c8ba3;
  letter-spacing: 0.8px;
  margin-top: 2px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0 12px;
}

.nav::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.25);
}

.nav-group-label {
  padding: 16px 22px 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.6px;
  color: #5b6b83;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  height: 40px;
  margin: 2px 12px;
  padding: 0 12px;
  border-radius: 10px;
  color: #9aabc1;
  font-size: 13.5px;
  font-weight: 500;
  transition: background 0.16s ease, color 0.16s ease;
  white-space: nowrap;
}

.collapsed .nav-item {
  justify-content: center;
  padding: 0;
  margin: 3px 12px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #e2e8f0;
}

.nav-item.is-active {
  background: linear-gradient(90deg, rgba(20, 184, 166, 0.22), rgba(79, 70, 229, 0.16));
  color: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(20, 184, 166, 0.2);
}

.nav-item.is-active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #2dd4bf, #14b8a6);
  box-shadow: 0 0 12px rgba(45, 212, 191, 0.8);
}

.nav-icon {
  font-size: 17px;
  flex: 0 0 auto;
}

.nav-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(225, 29, 72, 0.9);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.sidebar-foot {
  padding: 12px;
}

.side-card {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(148, 163, 184, 0.14);
  padding: 12px;
}

.side-card.mini {
  padding: 12px 0;
  display: grid;
  place-items: center;
}

.side-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.side-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #64748b;
  box-shadow: 0 0 0 3px rgba(100, 116, 139, 0.18);
}

.side-dot.teal {
  background: #2dd4bf;
  box-shadow: 0 0 0 3px rgba(45, 212, 191, 0.16), 0 0 10px rgba(45, 212, 191, 0.6);
}
.side-dot.amber {
  background: #fbbf24;
  box-shadow: 0 0 0 3px rgba(251, 191, 36, 0.16);
}
.side-dot.red {
  background: #fb7185;
  box-shadow: 0 0 0 3px rgba(251, 113, 133, 0.16);
}

.side-label {
  font-size: 11px;
  color: #8ba0bd;
  letter-spacing: 0.4px;
}

.side-value {
  margin-top: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: #dbe4f0;
}

.side-note {
  margin-top: 10px;
  font-size: 10px;
  color: #61738e;
  letter-spacing: 0.4px;
}

/* ---------------- 主区域 ---------------- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 64px;
  flex: 0 0 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--zk-border);
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  border: 1px solid var(--zk-border);
  background: #fff;
  color: #56657d;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all 0.16s ease;
}

.icon-btn:hover {
  color: var(--zk-indigo);
  border-color: #cdd3f7;
  background: #f6f7ff;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--zk-text-3);
  min-width: 0;
}

.crumb-root {
  letter-spacing: 0.3px;
}

.crumb-sep {
  color: #cbd5e1;
}

.crumb-current {
  color: var(--zk-text);
  font-weight: 600;
  font-size: 14px;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.health-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
}

.health-badge.teal {
  color: #0f766e;
  background: #edfdf9;
  border-color: #b9f0e6;
}
.health-badge.amber {
  color: #b45309;
  background: #fff8ea;
  border-color: #fbe0b0;
}
.health-badge.red {
  color: #be123c;
  background: #fff1f4;
  border-color: #f9cdd8;
}
.health-badge.slate {
  color: #64748b;
  background: #f4f6fa;
  border-color: #e2e8f0;
}

.health-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.topbar-divider {
  width: 1px;
  height: 22px;
  background: var(--zk-border);
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 6px 4px 4px;
  border-radius: 10px;
  outline: none;
  transition: background 0.16s ease;
}

.user-trigger:hover {
  background: #f4f6fb;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #4f46e5, #14b8a6);
  box-shadow: 0 6px 14px -8px rgba(79, 70, 229, 0.9);
}

.user-meta {
  line-height: 1.25;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.user-role {
  font-size: 11px;
  color: var(--zk-text-3);
}

.caret {
  font-size: 12px;
  color: #94a3b8;
}

.content {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1180px) {
  .user-meta,
  .health-badge span {
    display: none;
  }
  .health-badge {
    padding: 0 10px;
  }
}

@media (max-width: 900px) {
  .crumb-root,
  .crumb-sep {
    display: none;
  }
  .topbar {
    padding: 0 14px;
  }
}
/* ---------------- v2：可访问性与移动端抽屉导航 ---------------- */
.sidebar {
  height: 100vh;
  height: 100dvh;
}

.sidebar-close {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-left: auto;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: transparent;
  color: #cbd5e1;
  cursor: pointer;
}

.sidebar-close:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.sidebar-overlay {
  display: none;
}

.menu-btn {
  display: none;
}

.nav-item {
  min-height: 44px;
  height: 44px;
}

.nav-item:focus-visible {
  outline: 2px solid #2dd4bf;
  outline-offset: -2px;
  background: rgba(255, 255, 255, 0.07);
}

.sidebar :focus-visible,
.topbar :focus-visible {
  outline: 2px solid var(--zk-teal-dark);
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
    width: 268px;
    flex-basis: 268px;
    height: 100dvh;
    transform: translateX(-102%);
    transition: transform var(--zk-dur-base) var(--zk-ease-out);
    z-index: var(--zk-z-overlay);
    box-shadow: none;
  }

  .shell .sidebar,
  .shell.collapsed .sidebar {
    width: 268px;
    flex-basis: 268px;
  }

  .shell.is-mobile-open .sidebar {
    transform: translateX(0);
    box-shadow: 30px 0 60px -30px rgba(0, 0, 0, 0.65);
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(7, 12, 22, 0.55);
    backdrop-filter: blur(2px);
    z-index: calc(var(--zk-z-overlay) - 1);
  }

  .main {
    width: 100%;
  }

  .content {
    min-height: calc(100dvh - 64px);
  }
}

@media (max-width: 640px) {
  .topbar {
    height: 58px;
    flex-basis: 58px;
    padding: 0 12px;
  }

  .topbar-left {
    gap: 8px;
  }

  .icon-btn {
    width: 40px;
    height: 40px;
  }

  .health-badge {
    height: 28px;
    padding: 0 9px;
  }

  .user-meta {
    display: none;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }

  .user-trigger {
    padding: 3px;
  }

  .crumb {
    font-size: 12px;
  }

  .content {
    min-height: calc(100dvh - 58px);
  }
}
</style>
