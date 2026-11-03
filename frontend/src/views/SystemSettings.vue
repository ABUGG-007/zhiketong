<template>
  <div class="zk-page settings-page">
    <PageHeader
      title="系统设置"
      description="账号安全、访客接待参数与运行状态；所有操作直接调用后端接口，保存后即时生效。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshAll">刷新全部</el-button>
      </template>
    </PageHeader>

    <!-- 页签 -->
    <div class="settings-tabs" role="tablist" aria-label="设置分区">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="settings-tab"
        :class="{ 'is-active': activeTab === tab.key }"
        :aria-selected="activeTab === tab.key"
        @click="switchTab(tab.key)"
      >
        <el-icon aria-hidden="true"><component :is="tab.icon" /></el-icon>
        {{ tab.label }}
      </button>
    </div>

    <!-- 账号安全 -->
    <div v-show="activeTab === 'account'" role="tabpanel" aria-label="账号安全">
      <section class="zk-card panel" :aria-busy="accountLoading ? 'true' : 'false'">
        <header class="panel-head">
          <div>
            <div class="panel-title">当前登录账号</div>
            <div class="panel-sub">实时读取 /api/auth/me，资料由管理员统一维护</div>
          </div>
          <el-button size="small" :icon="Refresh" :loading="accountLoading" @click="loadAccount">
            刷新
          </el-button>
        </header>

        <div v-if="accountError" class="zk-empty">
          <span>{{ accountError }}</span>
          <el-button link type="primary" @click="loadAccount">重新加载</el-button>
        </div>
        <dl v-else class="info-grid">
          <div v-for="item in accountItems" :key="item.label" class="info-item">
            <dt>{{ item.label }}</dt>
            <dd :class="{ 'is-mono': item.mono }">{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <section class="zk-card panel">
        <header class="panel-head">
          <div>
            <div class="panel-title">修改登录密码</div>
            <div class="panel-sub">新密码长度 6-32 位；修改成功后建议退出并使用新密码重新登录</div>
          </div>
        </header>

        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          class="password-form"
          @submit.prevent
        >
          <!-- 供密码管理器识别当前账号，避免只出现密码字段时无法匹配凭据 -->
          <input
            class="password-username"
            type="text"
            :value="account?.username || ''"
            autocomplete="username"
            readonly
            tabindex="-1"
            aria-hidden="true"
          />
          <div class="field-grid field-grid--three">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="passwordForm.oldPassword"
                name="oldPassword"
                type="password"
                show-password
                autocomplete="current-password"
                placeholder="请输入当前密码"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                name="newPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="6-32 位新密码"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                name="confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="再次输入新密码"
                @keyup.enter="submitPassword"
              />
            </el-form-item>
          </div>
          <div class="form-actions">
            <el-button
              type="primary"
              class="zk-save"
              :class="`is-${pwdSaveState}`"
              :disabled="pwdSaveState === 'busy'"
              @click="submitPassword"
            >
              <span class="zk-save-label">保存新密码</span>
              <span class="zk-save-mark zk-save-spin" aria-hidden="true" />
              <svg class="zk-save-mark zk-save-tick" viewBox="0 0 16 16" aria-hidden="true">
                <path d="M3.2 8.4l2.9 2.9 6.7-6.7" />
              </svg>
            </el-button>
            <el-button :icon="SwitchButton" @click="handleLogout">退出当前账号</el-button>
          </div>
        </el-form>
        <p class="panel-note">
          接口：PUT /api/auth/password；退出登录会调用 POST /api/auth/logout 将当前令牌写入 Redis 黑名单。
        </p>
      </section>
    </div>

    <!-- 接待设置 -->
    <div v-show="activeTab === 'reception'" role="tabpanel" aria-label="接待设置">
      <section class="zk-card panel" :aria-busy="embedLoading ? 'true' : 'false'">
        <header class="panel-head">
          <div>
            <div class="panel-title">访客接待参数</div>
            <div class="panel-sub">保存后嵌入悬浮球与独立链接即时生效；外观与签名密钥请前往「接入方式」维护</div>
          </div>
          <div class="head-side">
            <StatusPill :label="receptionStatusText" :tone="receptionStatusTone" dot />
            <span v-if="receptionDirty" class="dirty-hint">有未保存修改</span>
            <el-button size="small" :icon="Refresh" :loading="embedLoading" @click="loadReception">
              重新读取
            </el-button>
          </div>
        </header>

        <div v-if="embedError" class="zk-empty">
          <span>{{ embedError }}</span>
          <el-button link type="primary" @click="loadReception">重新加载</el-button>
        </div>
        <el-form v-else v-loading="embedLoading" label-position="top" class="reception-form" @submit.prevent>
          <div class="field-grid">
            <el-form-item label="客服名称">
              <el-input v-model="embedForm.brandName" maxlength="64" show-word-limit placeholder="展示在悬浮球与会话头部" />
            </el-form-item>
            <el-form-item label="单 IP 每分钟提问上限">
              <el-input-number
                v-model="embedForm.rateLimitPerMinute"
                :min="1"
                :max="600"
                controls-position="right"
                class="rate-input"
              />
            </el-form-item>
          </div>
          <el-form-item label="欢迎语">
            <el-input
              v-model="embedForm.welcome"
              maxlength="120"
              show-word-limit
              placeholder="访客打开会话时展示的第一句话"
            />
          </el-form-item>
          <el-form-item label="来源白名单">
            <el-input
              v-model="embedForm.allowedOrigins"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="https://www.example.com,https://*.example.com；留空表示不限制"
            />
          </el-form-item>

          <div class="switch-row">
            <div class="switch-copy">
              <b>访客接待开关</b>
              <span>停用后嵌入端与独立链接会收到维护提示，不影响已结束的历史会话</span>
            </div>
            <el-switch
              v-model="embedForm.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </div>

          <div class="form-actions">
            <el-button
              type="primary"
              class="zk-save"
              :class="`is-${embedSaveState}`"
              :disabled="embedSaveState === 'busy'"
              @click="saveReception"
            >
              <span class="zk-save-label">保存接待设置</span>
              <span class="zk-save-mark zk-save-spin" aria-hidden="true" />
              <svg class="zk-save-mark zk-save-tick" viewBox="0 0 16 16" aria-hidden="true">
                <path d="M3.2 8.4l2.9 2.9 6.7-6.7" />
              </svg>
            </el-button>
            <el-button :icon="Refresh" @click="loadReception">放弃修改并重新读取</el-button>
            <el-button link type="primary" @click="goEmbed">编辑外观与密钥 →</el-button>
          </div>
        </el-form>

        <p v-if="!embedError" class="panel-note">
          <template v-if="embedMeta">
            租户 {{ embedMeta.tenantName || '—' }} · 接入标识 {{ embedMeta.tenantCode || '—' }} ·
            最近更新 {{ formatDateTime(embedMeta.updateTime) }}
          </template>
          接口：GET / PUT /api/admin/embed/config；保存时会保留主题色、悬浮位置与未读红点等现有外观配置。
        </p>
      </section>
    </div>

    <!-- 系统信息 -->
    <div v-show="activeTab === 'system'" role="tabpanel" aria-label="系统信息">
      <section class="system-grid">
        <div class="zk-card panel" :aria-busy="healthLoading ? 'true' : 'false'">
          <header class="panel-head">
            <div>
              <div class="panel-title">服务健康检查</div>
              <div class="panel-sub">GET /api/health · 应用、MySQL 与 Redis</div>
            </div>
            <el-button size="small" :icon="Refresh" :loading="healthLoading" @click="loadHealth">
              刷新
            </el-button>
          </header>

          <div v-if="healthError" class="zk-empty">
            <span>{{ healthError }}</span>
            <el-button link type="primary" @click="loadHealth">重新检查</el-button>
          </div>
          <template v-else>
            <div class="health-hero" :class="healthTone">
              <span class="health-dot" aria-hidden="true" />
              <div class="health-main">
                <b>{{ health?.status || '—' }}</b>
                <span>{{ health?.application || 'zhiketong-server' }} · v{{ health?.version || '—' }}</span>
              </div>
              <time>{{ formatDateTime(health?.time) }}</time>
            </div>
            <ul class="check-list">
              <li v-for="item in healthChecks" :key="item.name">
                <span class="check-dot" :class="item.status === 'UP' ? 'is-up' : 'is-down'" aria-hidden="true" />
                <b>{{ item.name }}</b>
                <span class="check-meta">{{ item.meta }}</span>
                <StatusPill :label="item.status || '—'" :tone="item.status === 'UP' ? 'teal' : 'red'" />
              </li>
              <li v-if="!healthChecks.length" class="check-empty">暂未返回依赖项状态</li>
            </ul>
          </template>
        </div>

        <div class="zk-card panel" :aria-busy="planLoading ? 'true' : 'false'">
          <header class="panel-head">
            <div>
              <div class="panel-title">套餐与额度</div>
              <div class="panel-sub">GET /api/plan · {{ plan?.periodLabel || '本月' }}自然月口径</div>
            </div>
            <el-button size="small" :icon="Refresh" :loading="planLoading" @click="loadPlan">
              刷新
            </el-button>
          </header>

          <div v-if="planError" class="zk-empty">
            <span>{{ planError }}</span>
            <el-button link type="primary" @click="loadPlan">重新加载</el-button>
          </div>
          <template v-else>
            <div class="plan-line">
              <div class="plan-main">
                <b>{{ plan?.planName || '—' }}</b>
                <span>{{ plan?.tagline || '套餐信息加载中' }}</span>
              </div>
              <el-button link type="primary" @click="goPlan">查看详情 →</el-button>
            </div>
            <ul class="quota-list">
              <li v-for="item in planItems" :key="item.code">
                <div class="quota-line">
                  <span>{{ item.name }}</span>
                  <b>{{ formatQuota(item.used) }} / {{ item.limit < 0 ? '不限' : formatQuota(item.limit) }}</b>
                </div>
                <div class="quota-track">
                  <i :class="`is-${item.level || 'normal'}`" :style="{ width: quotaWidth(item) }" />
                </div>
              </li>
              <li v-if="!planItems.length" class="check-empty">暂无额度数据</li>
            </ul>
          </template>
        </div>
      </section>

      <section class="zk-card panel">
        <header class="panel-head">
          <div>
            <div class="panel-title">运行环境</div>
            <div class="panel-sub">前端构建信息、接口入口与当前登录态</div>
          </div>
        </header>
        <dl class="env-grid">
          <div class="info-item">
            <dt>前端版本</dt>
            <dd>v1.0.0 · Vue 3 + Element Plus</dd>
          </div>
          <div class="info-item">
            <dt>接口前缀</dt>
            <dd class="is-mono">{{ apiBase }}</dd>
          </div>
          <div class="info-item">
            <dt>访问来源</dt>
            <dd class="is-mono">{{ origin }}</dd>
          </div>
          <div class="info-item">
            <dt>当前账号</dt>
            <dd>{{ accountLabel }}</dd>
          </div>
          <div class="info-item">
            <dt>最近登录</dt>
            <dd>{{ formatDateTime(account?.lastLoginTime) }} · {{ account?.lastLoginIp || '—' }}</dd>
          </div>
          <div class="info-item">
            <dt>后端接口文档</dt>
            <dd>
              <el-button link type="primary" @click="openDocs">打开 Swagger 文档 →</el-button>
            </dd>
          </div>
        </dl>
        <div class="form-actions">
          <el-button :icon="Refresh" :loading="accountLoading" @click="loadAccount">刷新账号信息</el-button>
          <el-button :icon="Connection" @click="goEmbed">前往接入方式</el-button>
          <el-button :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
        </div>
      </section>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      本页不展示本地假数据：密码修改写入 MySQL，接待参数写入租户配置，健康与额度均为后端实时响应
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Connection,
  Lock,
  Monitor,
  Refresh,
  SwitchButton
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { prefersReducedMotion } from '@/composables/motion'
import { clearAuth } from '@/api/http'
import {
  changePassword,
  getEmbedConfig,
  getHealth,
  getMe,
  getPlanUsage,
  logout as logoutApi,
  updateEmbedConfig
} from '@/api'
import { formatDateTime, formatQuota } from '@/utils/dict'

const router = useRouter()

const tabs = [
  { key: 'account', label: '账号安全', icon: Lock },
  { key: 'reception', label: '接待设置', icon: ChatDotRound },
  { key: 'system', label: '系统信息', icon: Monitor }
]

const activeTab = ref('account')

/* 保存按钮三连变：文字淡出 → 转圈 → 对勾（合计 350ms），密码与接待设置各一个状态 */
const pwdSaveState = ref('idle')
const embedSaveState = ref('idle')
let saveTimer = 0

function flashSaveState(target) {
  if (prefersReducedMotion()) return
  target.value = 'done'
  saveTimer = window.setTimeout(() => {
    target.value = 'idle'
  }, 350)
}

onBeforeUnmount(() => window.clearTimeout(saveTimer))

/* 校验失败：第一个错误字段摇头（输错摇头） */
function shakeFirstError(formInstance) {
  if (prefersReducedMotion()) return
  const el = formInstance?.$el?.querySelector?.('.el-form-item.is-error')
  if (!el) return
  el.classList.remove('zk-shake')
  void el.offsetWidth
  el.classList.add('zk-shake')
  window.setTimeout(() => el.classList.remove('zk-shake'), 420)
}

/* ---------- 账号 ---------- */
const account = ref(null)
const accountLoading = ref(false)
const accountError = ref('')

const accountItems = computed(() => {
  const data = account.value || {}
  return [
    { label: '登录账号', value: data.username || '—', mono: true },
    { label: '昵称', value: data.nickname || '—' },
    { label: '角色', value: data.roleName || data.roleCode || '—' },
    { label: '手机号', value: data.phone || '—', mono: true },
    { label: '邮箱', value: data.email || '—', mono: true },
    { label: '最近登录', value: formatDateTime(data.lastLoginTime) },
    { label: '登录 IP', value: data.lastLoginIp || '—', mono: true },
    { label: '账号创建', value: formatDateTime(data.createTime) }
  ]
})
const accountLabel = computed(() => {
  const data = account.value
  if (!data) return '—'
  return `${data.nickname || data.username || '—'}（${data.roleName || data.roleCode || '—'}）`
})

async function loadAccount() {
  accountLoading.value = true
  accountError.value = ''
  try {
    account.value = await getMe()
  } catch (error) {
    account.value = null
    accountError.value = '账号信息加载失败，请确认登录状态后重试'
  } finally {
    accountLoading.value = false
  }
}

/* ---------- 修改密码 ---------- */
const passwordFormRef = ref(null)
const passwordSaving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度须为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function submitPassword() {
  if (passwordSaving.value) return
  try {
    await passwordFormRef.value?.validate()
  } catch {
    shakeFirstError(passwordFormRef.value)
    return
  }
  passwordSaving.value = true
  pwdSaveState.value = 'busy'
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    flashSaveState(pwdSaveState)
    ElMessage.success('密码已更新，请使用新密码重新登录')
    passwordFormRef.value?.resetFields()
  } catch {
    // 请求拦截器已统一提示错误信息
  } finally {
    passwordSaving.value = false
    if (pwdSaveState.value === 'busy') pwdSaveState.value = 'idle'
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能继续使用管理后台，确定退出？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出登录',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  // 先通知后端把令牌写入黑名单，再清理本地凭证；接口异常不阻塞退出
  await logoutApi().catch(() => {})
  clearAuth()
  ElMessage.success('已安全退出登录')
  router.replace({ name: 'login' })
}

/* ---------- 接待设置 ---------- */
const embedLoading = ref(false)
const embedSaving = ref(false)
const embedError = ref('')
const embedMeta = ref(null)
const embedSnapshot = ref('')

const embedForm = reactive({
  brandName: '',
  welcome: '',
  themeColor: '#4f46e5',
  position: 'right',
  badge: true,
  allowedOrigins: '',
  rateLimitPerMinute: 30,
  status: 1
})

const receptionDirty = computed(() => {
  if (!embedSnapshot.value) return false
  return JSON.stringify(embedPayload()) !== embedSnapshot.value
})
const receptionStatusText = computed(() => (Number(embedForm.status) === 1 ? '接待中' : '已停用'))
const receptionStatusTone = computed(() => (Number(embedForm.status) === 1 ? 'teal' : 'slate'))

function embedPayload() {
  return {
    brandName: String(embedForm.brandName || '').trim(),
    welcome: String(embedForm.welcome || '').trim(),
    themeColor: embedForm.themeColor || '#4f46e5',
    position: embedForm.position === 'left' ? 'left' : 'right',
    badge: Boolean(embedForm.badge),
    allowedOrigins: String(embedForm.allowedOrigins || '').trim(),
    rateLimitPerMinute: Number(embedForm.rateLimitPerMinute) || 30,
    status: Number(embedForm.status) === 0 ? 0 : 1
  }
}

function applyEmbedConfig(data) {
  if (!data) return
  embedForm.brandName = data.brandName || ''
  embedForm.welcome = data.welcome || ''
  embedForm.themeColor = /^#[0-9a-fA-F]{6}$/.test(String(data.themeColor || ''))
    ? data.themeColor
    : '#4f46e5'
  embedForm.position = data.position === 'left' ? 'left' : 'right'
  embedForm.badge = Boolean(data.badge)
  embedForm.allowedOrigins = data.allowedOrigins || ''
  embedForm.rateLimitPerMinute = Number(data.rateLimitPerMinute) || 30
  embedForm.status = Number(data.status) === 0 ? 0 : 1
  embedMeta.value = {
    tenantCode: data.tenantCode,
    tenantName: data.tenantName,
    updateTime: data.updateTime
  }
  embedSnapshot.value = JSON.stringify(embedPayload())
}

async function loadReception() {
  // 有未保存草稿时不覆盖，避免误丢编辑内容
  if (receptionDirty.value) {
    ElMessage.info('当前有未保存的接待设置，已保留草稿；如需放弃请点击「放弃修改并重新读取」')
    return
  }
  embedLoading.value = true
  embedError.value = ''
  try {
    const data = await getEmbedConfig()
    applyEmbedConfig(data)
  } catch (error) {
    embedError.value = '接待配置读取失败，请确认后端服务已启动且当前账号为管理员'
  } finally {
    embedLoading.value = false
  }
}

async function saveReception() {
  if (embedSaving.value) return
  if (!embedForm.brandName.trim()) {
    ElMessage.warning('客服名称不能为空')
    return
  }
  embedSaving.value = true
  embedSaveState.value = 'busy'
  try {
    const data = await updateEmbedConfig(embedPayload())
    applyEmbedConfig(data)
    flashSaveState(embedSaveState)
    ElMessage.success('接待设置已保存，嵌入端刷新后生效')
  } catch {
    // 请求拦截器已统一提示错误信息
  } finally {
    embedSaving.value = false
    if (embedSaveState.value === 'busy') embedSaveState.value = 'idle'
  }
}

/* ---------- 健康检查 ---------- */
const health = ref(null)
const healthLoading = ref(false)
const healthError = ref('')

const healthTone = computed(() => {
  const status = health.value?.status
  if (status === 'UP') return 'is-up'
  if (status === 'DEGRADED') return 'is-warn'
  return 'is-down'
})

const healthChecks = computed(() => {
  const checks = health.value?.checks || {}
  const list = []
  if (checks.mysql) {
    list.push({
      name: 'MySQL',
      status: checks.mysql.status,
      meta: checks.mysql.version ? `版本 ${checks.mysql.version}` : checks.mysql.error || '连接异常'
    })
  }
  if (checks.redis) {
    list.push({
      name: 'Redis',
      status: checks.redis.status,
      meta: checks.redis.ping ? `PING ${checks.redis.ping}` : checks.redis.error || '连接异常'
    })
  }
  return list
})

async function loadHealth() {
  healthLoading.value = true
  healthError.value = ''
  try {
    health.value = await getHealth()
  } catch (error) {
    health.value = null
    healthError.value = '健康检查请求失败，请确认后端服务已启动'
  } finally {
    healthLoading.value = false
  }
}

/* ---------- 套餐额度 ---------- */
const plan = ref(null)
const planLoading = ref(false)
const planError = ref('')

const planItems = computed(() => (Array.isArray(plan.value?.items) ? plan.value.items : []))

function quotaWidth(item) {
  if (Number(item?.limit) < 0) return '6%'
  const percentValue = Number(item?.percent)
  if (!Number.isFinite(percentValue)) return '0%'
  const used = Number(item?.used) > 0 ? 3 : 0
  return `${Math.min(100, Math.max(percentValue, used))}%`
}

async function loadPlan() {
  planLoading.value = true
  planError.value = ''
  try {
    plan.value = await getPlanUsage()
  } catch (error) {
    plan.value = null
    planError.value = '套餐用量加载失败，请确认当前账号具备管理员权限'
  } finally {
    planLoading.value = false
  }
}

/* ---------- 环境信息 ---------- */
const apiBase = import.meta.env.VITE_API_BASE || '/api'
const origin = typeof window === 'undefined' ? '—' : window.location.origin
const docsUrl = /^https?:/i.test(apiBase)
  ? `${apiBase.replace(/\/api\/?$/, '')}/swagger-ui.html`
  : 'http://localhost:8080/swagger-ui.html'

function openDocs() {
  window.open(docsUrl, '_blank', 'noopener')
}

function goEmbed() {
  router.push('/admin/embed')
}

function goPlan() {
  router.push('/admin/plan')
}

/* ---------- 页签与刷新 ---------- */
const refreshing = computed(
  () => accountLoading.value || embedLoading.value || healthLoading.value || planLoading.value
)

async function refreshAll() {
  await Promise.allSettled([loadAccount(), loadHealth(), loadPlan(), loadReception()])
}

function switchTab(tab) {
  activeTab.value = tab
  // 每次切换页签都请求对应分区的最新数据
  if (tab === 'account') {
    loadAccount()
  } else if (tab === 'reception') {
    loadReception()
  } else {
    loadHealth()
    loadPlan()
  }
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
/* 保存按钮三连变：文字淡出 → 转圈 → 对勾（合计约 350ms） */
.zk-save {
  position: relative;
}

.zk-save .zk-save-label {
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out);
}

.is-busy .zk-save-label,
.is-done .zk-save-label {
  opacity: 0;
}

.zk-save-mark {
  position: absolute;
  inset: 0;
  margin: auto;
  width: 13px;
  height: 13px;
  opacity: 0;
  transform: scale(0.7);
  pointer-events: none;
  transition: opacity 160ms var(--zk-ease-out), transform 220ms var(--zk-ease-spring);
}

.is-busy .zk-save-spin,
.is-done .zk-save-tick {
  opacity: 1;
  transform: none;
}

.zk-save-spin {
  border: 1.6px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: zk-spin 780ms linear infinite;
}

.zk-save-tick path {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 24;
}

.is-done .zk-save-tick path {
  animation: zk-check-draw 300ms var(--zk-ease-out) forwards;
}

/* 字段聚焦：标签随输入框激活加深 */
:deep(.el-form-item:focus-within .el-form-item__label) {
  color: var(--zk-accent-hover);
  transition: color var(--zk-dur-fast) var(--zk-ease-out);
}

/* ---------- 页签 ---------- */
.settings-tabs {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  margin-bottom: 14px;
  background: var(--zk-surface-3);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
}

.settings-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 0;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--zk-text-3);
  cursor: pointer;
  white-space: nowrap;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
}

.settings-tab:hover {
  color: var(--zk-text);
  background: var(--zk-surface);
}

.settings-tab.is-active {
  color: var(--zk-accent-hover);
  background: var(--zk-surface);
  box-shadow: inset 0 0 0 1px var(--zk-border);
}

/* ---------- 面板 ---------- */
.panel {
  padding: 18px 20px 16px;
  margin-bottom: 14px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.panel-sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.panel-note {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--zk-border);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
}

.head-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.dirty-hint {
  font-size: 11.5px;
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
  border: 1px solid var(--zk-amber-line);
  border-radius: var(--zk-radius-sm);
  padding: 3px 9px;
  white-space: nowrap;
}

/* ---------- 信息栅格 ---------- */
.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px 18px;
  margin: 0;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.info-item dt {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.info-item dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-item dd.is-mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  font-weight: 500;
  color: var(--zk-text-2);
}

/* ---------- 表单 ---------- */
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.field-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.password-form,
.reception-form {
  max-width: 880px;
}

.password-username {
  position: absolute;
  width: 0;
  height: 0;
  padding: 0;
  margin: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.rate-input {
  width: 100%;
}

.rate-input :deep(.el-input__wrapper) {
  width: 100%;
}

.reception-form :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 14px;
  margin: 4px 0 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
}

.switch-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.switch-copy b {
  font-size: 12.5px;
  color: var(--zk-text);
}

.switch-copy span {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

/* ---------- 健康检查 ---------- */
.system-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.health-hero {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--zk-radius);
  border: 1px solid var(--zk-border);
  background: var(--zk-surface-2);
}

.health-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
  background: var(--zk-text-3);
}

.health-hero.is-up .health-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
}

.health-hero.is-warn .health-dot {
  background: var(--zk-amber);
  box-shadow: 0 0 0 3px rgba(180, 83, 9, 0.14);
}

.health-hero.is-down .health-dot {
  background: var(--zk-red);
  box-shadow: 0 0 0 3px rgba(190, 18, 60, 0.14);
}

.health-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.health-main b {
  font-size: 14px;
  letter-spacing: 0.4px;
  color: var(--zk-text);
}

.health-main span {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.health-hero time {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.check-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
}

.check-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 2px;
  border-bottom: 1px solid var(--zk-border);
  font-size: 12.5px;
}

.check-list li:last-child {
  border-bottom: 0;
}

.check-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
}

.check-dot.is-up {
  background: #10b981;
}

.check-dot.is-down {
  background: var(--zk-red);
}

.check-list b {
  color: var(--zk-text);
  font-weight: 600;
  min-width: 52px;
}

.check-meta {
  flex: 1;
  min-width: 0;
  color: var(--zk-text-3);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check-empty {
  display: block;
  padding: 8px 2px;
  color: var(--zk-text-3);
  font-size: 12px;
  border-bottom: 0 !important;
}

/* ---------- 套餐额度 ---------- */
.plan-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--zk-radius);
  background: var(--zk-accent-soft);
  border: 1px solid var(--zk-accent-line);
}

.plan-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.plan-main b {
  font-size: 14px;
  color: var(--zk-accent-hover);
}

.plan-main span {
  font-size: 11.5px;
  color: var(--zk-text-2);
}

.quota-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quota-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--zk-text-2);
}

.quota-line b {
  color: var(--zk-text);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.quota-track {
  height: 4px;
  margin-top: 5px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.quota-track i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  transition: width var(--zk-dur-slow) var(--zk-ease-out);
}

.quota-track i.is-warning {
  background: var(--zk-amber);
}

.quota-track i.is-danger {
  background: var(--zk-red);
}

/* ---------- 运行环境 ---------- */
.env-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
  margin: 0 0 14px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1080px) {
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .system-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .field-grid--three {
    grid-template-columns: minmax(0, 1fr);
  }

  .env-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .settings-tabs {
    display: flex;
    width: 100%;
    overflow-x: auto;
  }

  .settings-tab {
    flex: 0 0 auto;
  }

  .panel {
    padding: 15px 14px 13px;
  }

  .info-grid,
  .field-grid,
  .env-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .health-hero {
    flex-wrap: wrap;
  }

  .health-hero time {
    margin-left: 18px;
  }
}
</style>
