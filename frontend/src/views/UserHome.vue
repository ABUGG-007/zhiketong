<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="MY SPACE"
      title="用户中心"
      :description="`欢迎回来，${displayName}。这里汇总你的会话与工单进度，也可以直接向 AI 咨询。`"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loadingStats" @click="loadStats">刷新</el-button>
      </template>
    </PageHeader>

    <div class="home-grid">
      <!-- 欢迎 + 统计 -->
      <section
        class="welcome-card zk-card"
        :aria-busy="loadingStats ? 'true' : 'false'"
        aria-label="账号概览"
      >
        <div class="welcome-head">
          <div class="welcome-avatar" aria-hidden="true">{{ avatarText }}</div>
          <div class="welcome-info">
            <h2>你好，{{ displayName }}</h2>
            <div class="welcome-meta">
              <span>账号 <b>{{ user?.username || '—' }}</b></span>
              <span class="meta-sep" aria-hidden="true">·</span>
              <span class="role-inline">
                角色
                <StatusPill :label="roleLabel" :tone="roleTone" />
              </span>
            </div>
          </div>
        </div>

        <p class="welcome-note">
          用户端仅展示本人数据：会话与工单由客服坐席处理后同步到这里，其他用户的数据不可见。
        </p>

        <div class="stat-row">
          <button
            v-for="stat in stats"
            :key="stat.key"
            type="button"
            class="stat-card"
            :aria-label="`${stat.label}：${stat.value}，${stat.sub}`"
            @click="go(stat.path)"
          >
            <span class="stat-icon" aria-hidden="true">
              <el-icon><component :is="stat.icon" /></el-icon>
            </span>
            <span class="stat-body">
              <span class="stat-label">{{ stat.label }}</span>
              <span class="stat-value zk-num">{{ stat.value }}</span>
              <span class="stat-sub">{{ stat.sub }}</span>
            </span>
          </button>
        </div>

        <p v-if="statsError" class="stat-error" role="status">{{ statsError }}</p>
      </section>

      <!-- 快捷入口 -->
      <section class="zk-card quick-card" aria-label="快捷入口">
        <div class="zk-card-head">
          <div>
            <div class="zk-card-title">快捷入口</div>
            <div class="zk-card-desc">常用功能一键直达</div>
          </div>
        </div>
        <div class="quick-grid">
          <router-link
            v-for="item in quickLinks"
            :key="item.path"
            :to="item.path"
            class="quick-item"
          >
            <span class="quick-icon" :class="item.tone" aria-hidden="true">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <span class="quick-text">
              <b>{{ item.title }}</b>
              <span>{{ item.desc }}</span>
            </span>
            <el-icon class="quick-arrow" aria-hidden="true"><ArrowRight /></el-icon>
          </router-link>
        </div>
      </section>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/user/sessions、GET /api/user/orders · 用户端仅展示本人数据
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChatDotRound,
  ChatLineSquare,
  Collection,
  Refresh,
  Tickets
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getUserOrders, getUserSessions } from '@/api'
import { getUser } from '@/api/http'
import { ROLE_DICT, dictLabel, dictTone } from '@/utils/dict'

const router = useRouter()
const user = getUser()

const displayName = computed(() => user?.nickname || user?.username || '用户')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => dictLabel(ROLE_DICT, user?.roleCode, '普通用户'))
const roleTone = computed(() => dictTone(ROLE_DICT, user?.roleCode, 'slate'))

const sessionTotal = ref(null)
const orderTotal = ref(null)
const loadingStats = ref(false)
const statsError = ref('')

const stats = computed(() => [
  {
    key: 'sessions',
    label: '我的会话',
    value: sessionTotal.value === null ? '—' : sessionTotal.value,
    sub: '查看全部会话',
    path: '/user/sessions',
    icon: ChatLineSquare
  },
  {
    key: 'orders',
    label: '我的工单',
    value: orderTotal.value === null ? '—' : orderTotal.value,
    sub: '查看处理进度',
    path: '/user/orders',
    icon: Tickets
  }
])

const quickLinks = [
  {
    title: 'AI 咨询',
    desc: '智能助手即时答疑',
    path: '/user/chat',
    icon: ChatDotRound,
    tone: 'teal'
  },
  {
    title: '我的会话',
    desc: '查看历史会话与评分',
    path: '/user/sessions',
    icon: ChatLineSquare,
    tone: 'indigo'
  },
  {
    title: '我的工单',
    desc: '跟踪工单处理进度与 SLA',
    path: '/user/orders',
    icon: Tickets,
    tone: 'amber'
  },
  {
    title: '知识库',
    desc: '常见问题自助查询',
    path: '/user/knowledge',
    icon: Collection,
    tone: 'slate'
  }
]

function go(path) {
  router.push(path)
}

async function loadStats() {
  loadingStats.value = true
  statsError.value = ''
  try {
    const [sessions, orders] = await Promise.all([
      getUserSessions({ pageNum: 1, pageSize: 1 }),
      getUserOrders({ pageNum: 1, pageSize: 1 })
    ])
    sessionTotal.value = Number(sessions?.total ?? 0)
    orderTotal.value = Number(orders?.total ?? 0)
  } catch {
    sessionTotal.value = null
    orderTotal.value = null
    statsError.value = '统计接口暂未就绪，稍后点击「刷新」重试'
  } finally {
    loadingStats.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped>
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* ---------------- 欢迎卡 ---------------- */
.welcome-card {
  padding: 24px 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-avatar {
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(140deg, #14b8a6 0%, #0ea5a4 45%, #4f46e5 100%);
  box-shadow: 0 10px 22px -12px rgba(20, 184, 166, 0.9);
}

.welcome-info h2 {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--zk-text);
}

.welcome-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.welcome-meta b {
  color: var(--zk-text);
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
}

.meta-sep {
  color: var(--zk-text-3);
}

.role-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.welcome-note {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--zk-text-3);
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--zk-surface-2);
  border: 1px dashed var(--zk-border-strong);
}

/* 统计卡 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 16px;
  min-height: 92px;
  text-align: left;
  border-radius: var(--zk-radius-lg);
  border: 1px solid var(--zk-border);
  background: var(--zk-surface-2);
  cursor: pointer;
  font-family: inherit;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out),
    background-color var(--zk-dur-fast) var(--zk-ease-out),
    transform var(--zk-dur-fast) var(--zk-ease-out);
}

.stat-card:hover {
  background: var(--zk-surface);
  border-color: var(--zk-border-strong);
  transform: translateY(-1px);
}

.stat-icon {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 19px;
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border: 1px solid var(--zk-teal-line);
}

.stat-card:nth-child(2) .stat-icon {
  color: var(--zk-indigo-dark);
  background: var(--zk-indigo-soft);
  border-color: var(--zk-indigo-line);
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--zk-text-3);
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.4px;
  color: var(--zk-text);
}

.stat-sub {
  font-size: 11.5px;
  color: var(--zk-text-2);
}

.stat-error {
  font-size: 11.5px;
  color: var(--zk-amber);
  line-height: 1.6;
}

/* ---------------- 快捷入口 ---------------- */
.quick-card {
  padding-bottom: 10px;
}

.quick-grid {
  display: grid;
  gap: 6px;
  padding: 0 14px 12px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 10px;
  border-radius: 12px;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.quick-item:hover {
  background: var(--zk-surface-2);
}

.quick-icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 17px;
  border: 1px solid;
}

.quick-icon.teal {
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border-color: var(--zk-teal-line);
}

.quick-icon.indigo {
  color: var(--zk-indigo-dark);
  background: var(--zk-indigo-soft);
  border-color: var(--zk-indigo-line);
}

.quick-icon.amber {
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
  border-color: var(--zk-amber-line);
}

.quick-icon.slate {
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border-color: var(--zk-border);
}

.quick-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.quick-text b {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.quick-text span {
  font-size: 11.5px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-arrow {
  font-size: 13px;
  color: var(--zk-text-3);
}

@media (max-width: 1100px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .welcome-card {
    padding: 18px 16px;
  }

  .stat-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .welcome-avatar {
    width: 46px;
    height: 46px;
    flex-basis: 46px;
    border-radius: 13px;
    font-size: 18px;
  }

  .welcome-info h2 {
    font-size: 17px;
  }
}
</style>
