<template>
  <div class="login-page">
    <!-- 背景：纯渐变底图（独立素材），3D 插画由 .login-shell 内的 .login-art 单独摆放 -->
    <div class="login-bg" aria-hidden="true" />

    <div class="login-shell">
      <!-- 插画：独立素材，按设计稿位置（左 113、底 154、宽 589）摆放 -->
      <img
        class="login-art"
        src="/login/login-robot.webp"
        alt=""
        aria-hidden="true"
        width="613"
        height="398"
        decoding="async"
      />

      <!-- 左侧：品牌、主标题、能力矩阵 -->
      <section class="intro">
        <header class="brand">
          <img class="brand-mark" src="/brand-mark.png" alt="智能客服" width="64" height="64" />
          <div class="brand-text">
            <div class="brand-name">智能客服</div>
            <div class="brand-slogan">让客服更简单 · 让业务更高效</div>
          </div>
        </header>

        <h1 class="headline">智能对话客服与工单系统</h1>
        <p class="subline">为中小商家提供专业的智能客服解决方案</p>

        <ul class="features">
          <li v-for="item in capabilities" :key="item.title" class="feature">
            <svg
              v-if="item.svg"
              class="feature-icon feature-icon--robot"
              viewBox="0 0 32 32"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <rect x="6.6" y="11" width="18.8" height="14.6" rx="4.6" />
              <path d="M16 11V7" />
              <circle cx="16" cy="4.9" r="1.5" />
              <path d="M6.6 16.4H4.9v3.8h1.7" />
              <path d="M25.4 16.4h1.7v3.8h-1.7" />
              <circle cx="12.4" cy="18.4" r="1.5" fill="currentColor" stroke="none" />
              <circle cx="19.6" cy="18.4" r="1.5" fill="currentColor" stroke="none" />
            </svg>
            <img
              v-else
              class="feature-icon"
              :src="item.icon"
              alt=""
              width="34"
              height="34"
              loading="eager"
              decoding="async"
            />
            <h2 class="feature-title">{{ item.title }}</h2>
            <p class="feature-desc">
              <span>{{ item.desc[0] }}</span>
              <span>{{ item.desc[1] }}</span>
            </p>
          </li>
        </ul>
      </section>

      <!-- 右侧：登录卡片 -->
      <section class="auth">
        <div class="card">
          <div class="tabs" role="tablist" aria-label="登录方式">
            <button
              type="button"
              class="tab"
              :class="{ 'is-active': mode === 'password' }"
              role="tab"
              :aria-selected="mode === 'password'"
              aria-controls="pane-password"
              @click="switchMode('password')"
            >
              账号登录
            </button>
            <button
              type="button"
              class="tab"
              :class="{ 'is-active': mode === 'sms' }"
              role="tab"
              :aria-selected="mode === 'sms'"
              aria-controls="pane-sms"
              @click="switchMode('sms')"
            >
              验证码登录
            </button>
          </div>

          <form
            v-if="mode === 'password'"
            id="pane-password"
            class="form"
            role="tabpanel"
            novalidate
            @submit.prevent="onSubmit"
          >
            <label
              class="field"
              :class="{ 'has-error': !!errors.username, 'zk-shake': shaking === 'username' }"
            >
              <span class="field-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                  <circle cx="12" cy="8" r="3.2" />
                  <path d="M5.4 19.2c.9-3.3 3.5-5.2 6.6-5.2s5.7 1.9 6.6 5.2" stroke-linecap="round" />
                </svg>
              </span>
              <input
                ref="usernameInput"
                v-model.trim="form.username"
                type="text"
                name="username"
                placeholder="请输入账号/手机号"
                autocomplete="username"
                aria-label="账号"
                :aria-invalid="!!errors.username"
                :aria-describedby="errors.username ? 'login-error' : undefined"
                @input="clearError('username')"
              />
            </label>

            <label
              class="field"
              :class="{ 'has-error': !!errors.password || authFailed, 'zk-shake': shaking === 'password' }"
            >
              <span class="field-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                  <rect x="4.6" y="10.2" width="14.8" height="9.8" rx="2.4" />
                  <path d="M8.4 10.2V7.9a3.6 3.6 0 0 1 7.2 0v2.3" stroke-linecap="round" />
                </svg>
              </span>
              <input
                ref="passwordInput"
                v-model="form.password"
                :type="passwordVisible ? 'text' : 'password'"
                name="password"
                placeholder="请输入密码"
                autocomplete="current-password"
                aria-label="密码"
                :aria-invalid="!!errors.password"
                :aria-describedby="errors.password ? 'login-error' : undefined"
                @input="clearError('password')"
              />
              <button
                type="button"
                class="eye"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                :aria-pressed="passwordVisible"
                @click="passwordVisible = !passwordVisible"
              >
                <transition name="zk-eye">
                  <svg v-if="!passwordVisible" key="off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path d="M2.6 12S6.2 5.9 12 5.9 21.4 12 21.4 12 17.8 18.1 12 18.1 2.6 12 2.6 12Z" />
                    <circle cx="12" cy="12" r="2.7" />
                    <path d="M4.2 19.8 19.8 4.2" stroke-linecap="round" />
                  </svg>
                  <svg v-else key="on" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path d="M2.6 12S6.2 5.9 12 5.9 21.4 12 21.4 12 17.8 18.1 12 18.1 2.6 12 2.6 12Z" />
                    <circle cx="12" cy="12" r="2.7" />
                  </svg>
                </transition>
              </button>
            </label>

            <div class="helper-row">
              <label class="remember">
                <input v-model="form.remember" type="checkbox" />
                <span class="checkbox" aria-hidden="true">
                  <svg viewBox="0 0 18 18" fill="none">
                    <path
                      d="m4.4 9.2 3 3 6.2-6.4"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </span>
                <span>记住账号</span>
              </label>
              <button type="button" class="link" @click="openInfo('forgot')">忘记密码?</button>
            </div>

            <!-- 错误提示常驻占位：出现时只做滑下淡入，不挤动下方按钮 -->
            <div class="error-slot">
              <transition name="zk-err">
                <p
                  v-if="errorMessage"
                  id="login-error"
                  class="form-error"
                  role="alert"
                  aria-live="assertive"
                  :title="errorMessage"
                >
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6" />
                    <path d="M12 7.6v5.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                    <circle cx="12" cy="16.4" r="1.05" fill="currentColor" />
                  </svg>
                  <span>{{ errorMessage }}</span>
                </p>
              </transition>
            </div>

            <!-- 提交按钮三连变：文字淡出 → 转圈 → 对勾画出来，成功停留后跳转 -->
            <button
              class="submit"
              :class="[`is-${btnState}`, { 'zk-shake': btnShaking }]"
              type="submit"
              :disabled="btnState !== 'idle'"
              :aria-busy="btnState === 'loading' ? 'true' : 'false'"
              :aria-label="submitLabel"
            >
              <transition name="zk-submit" mode="out-in">
                <span v-if="btnState === 'idle'" key="idle" class="submit-label">登录</span>
                <span v-else-if="btnState === 'loading'" key="loading" class="spinner" aria-hidden="true" />
                <svg v-else key="ok" class="submit-check" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="m5.6 12.4 4.2 4.2 8.6-8.8"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </transition>
            </button>
          </form>

          <div v-else id="pane-sms" class="form gate" role="tabpanel">
            <span class="gate-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                <rect x="6.6" y="2.6" width="10.8" height="18.8" rx="2.6" />
                <path d="M10.6 5.4h2.8" stroke-linecap="round" />
                <path d="m9.8 13.6 1.7 1.7 3.1-3.3" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
            <h3>验证码登录需先开通短信通道</h3>
            <p>
              当前环境尚未接入短信服务商，手机号验证码暂不可用。请使用账号密码登录，或联系租户管理员在系统设置中配置短信通道后启用。
            </p>
            <button type="button" class="gate-action" @click="switchMode('password')">返回账号登录</button>
          </div>

          <div class="divider"><span>其他登录方式</span></div>

          <button type="button" class="sso" aria-label="SSO 登录" @click="openInfo('sso')">
            <svg class="sso-shield" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 2.4 4.6 5.3v5.6c0 4.7 3.1 8.6 7.4 10.4 4.3-1.8 7.4-5.7 7.4-10.4V5.3L12 2.4Z"
                fill="currentColor"
              />
              <path
                d="m8.5 11.7 2.3 2.3 4.6-4.7"
                stroke="#fff"
                stroke-width="1.9"
                fill="none"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
          <p class="sso-label">SSO 登录</p>

          <p class="legal">
            <svg viewBox="0 0 24 24" class="legal-shield" aria-hidden="true">
              <path
                d="M12 2.4 4.6 5.3v5.6c0 4.7 3.1 8.6 7.4 10.4 4.3-1.8 7.4-5.7 7.4-10.4V5.3L12 2.4Z"
                fill="currentColor"
              />
              <path
                d="m8.6 11.7 2.2 2.2 4.5-4.6"
                stroke="#fff"
                stroke-width="1.9"
                fill="none"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span>登录即表示您已同意</span>
            <button type="button" class="link" @click="openInfo('terms')">《用户协议》</button>
            <span>和</span>
            <button type="button" class="link" @click="openInfo('privacy')">《隐私政策》</button>
          </p>
        </div>
      </section>
    </div>

    <el-dialog v-model="info.visible" :title="info.title" width="432px" class="zk-login-dialog" align-center>
      <div class="zk-login-dialog__body">
        <p v-for="(line, index) in info.lines" :key="index">{{ line }}</p>
      </div>
      <template #footer>
        <button type="button" class="zk-login-dialog__ok" @click="info.visible = false">我知道了</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api'
import { saveAuth } from '@/api/http'
import { roleHomePath } from '@/router'
import { haptic } from '@/composables/motion'

const REMEMBER_KEY = 'zk.login.remember'

const router = useRouter()
const route = useRoute()

const mode = ref('password')
const btnState = ref('idle') // idle / loading / success：提交按钮三连变的状态
const passwordVisible = ref(false)
const errorMessage = ref('')
const errors = reactive({ username: '', password: '' })
const usernameInput = ref(null)
const passwordInput = ref(null)
const shaking = ref('') // 正在摇头的输入框
const btnShaking = ref(false) // 提交失败时按钮摇头
const authFailed = ref(false)

let shakeTimer = 0
let btnShakeTimer = 0

const submitLabel = computed(() => {
  if (btnState.value === 'loading') return '正在登录'
  if (btnState.value === 'success') return '登录成功'
  return '登录'
})

/** 输错摇头：先摘掉、隔一帧再挂上，连续失败也能重新播（400ms，幅度递减） */
function shakeField(field) {
  shaking.value = ''
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      shaking.value = field
      window.clearTimeout(shakeTimer)
      shakeTimer = window.setTimeout(() => {
        shaking.value = ''
      }, 460)
    })
  })
  haptic(28)
}

/** 提交失败：按钮自己摇 400ms 后回到常态（同一套递减幅度） */
function shakeSubmit() {
  btnShaking.value = false
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      btnShaking.value = true
      window.clearTimeout(btnShakeTimer)
      btnShakeTimer = window.setTimeout(() => {
        btnShaking.value = false
      }, 460)
    })
  })
  haptic(28)
}

const form = reactive({
  username: '',
  password: '',
  remember: true
})

/** 左侧能力矩阵：与产品实际能力一一对应 */
const capabilities = [
  {
    title: '智能对话',
    desc: ['基于知识库自动问答', '高效解决客户问题'],
    svg: true
  },
  {
    title: '知识库管理',
    desc: ['多渠道知识沉淀', '快速配置更新'],
    icon: '/login/icon-kb.webp'
  },
  {
    title: '工单流转',
    desc: ['全流程跟踪', '高效协同处理'],
    icon: '/login/icon-order.webp'
  },
  {
    title: '转人工坐席',
    desc: ['灵活分配坐席', '提升服务体验'],
    icon: '/login/icon-agent.webp'
  }
]

/* ---------------- 交互辅助信息弹层 ---------------- */
const info = reactive({ visible: false, title: '', lines: [] })

const INFO_CONTENT = {
  forgot: {
    title: '忘记密码',
    lines: [
      '为保障租户数据安全，密码重置由管理员统一处理。',
      '请联系所在租户的管理员，在「系统设置 · 账号管理」中为你重置初始密码；重置后首次登录请立即修改。',
      '若你是租户管理员，可通过同一入口重置自身密码，或提交工单由平台运维协助处理。'
    ]
  },
  sso: {
    title: 'SSO 单点登录',
    lines: [
      'SSO 单点登录需接入企业身份源（OIDC / SAML 2.0）。',
      '请租户管理员在「系统设置 · 单点登录」中填写身份源地址、客户端标识与密钥，保存后本入口即可使用。',
      '未配置身份源时，请使用账号密码登录。'
    ]
  },
  terms: {
    title: '用户协议',
    lines: [
      '账号按租户独立开通，仅限授权人员在授权范围内使用，禁止转借或共享。',
      '坐席对会话、工单与客户资料的处理须遵守所在租户的服务规范；越权访问其他租户数据将被拒绝并记录审计日志。',
      '平台按套餐约定提供智能应答、坐席辅助、工单与统计能力，超出套餐范围的调用会被拦截并提示。'
    ]
  },
  privacy: {
    title: '隐私政策',
    lines: [
      '登录环节记录账号、登录时间、IP 与设备信息，用于身份校验与安全审计。',
      '访客会话、工单与知识库内容按租户隔离存储，仅用于服务交付、质检与统计，AI 调用仅发送完成应答所需的上下文。',
      '数据导出与删除由租户管理员发起，平台按租户级别执行，操作全程留痕。'
    ]
  }
}

function openInfo(kind) {
  const content = INFO_CONTENT[kind]
  if (!content) return
  info.title = content.title
  info.lines = content.lines
  info.visible = true
}

/* ---------------- 登录流程 ---------------- */
function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  errorMessage.value = ''
  errors.username = ''
  errors.password = ''
  authFailed.value = false
  if (next === 'password') {
    nextTick(() => usernameInput.value?.focus())
  }
}

function clearError(field) {
  errors[field] = ''
  authFailed.value = false
  if (errorMessage.value) errorMessage.value = ''
}

function validate() {
  errors.username = form.username ? '' : '请输入账号'
  if (!form.password) {
    errors.password = '请输入密码'
  } else if (form.password.length < 6) {
    errors.password = '密码至少 6 位'
  } else {
    errors.password = ''
  }
  if (errors.username || errors.password) {
    errorMessage.value = errors.username || errors.password
    return false
  }
  return true
}

function syncRemembered() {
  try {
    if (form.remember && form.username) {
      localStorage.setItem(REMEMBER_KEY, form.username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
  } catch {
    /* 隐私模式下忽略存储异常 */
  }
}

/** 仅允许跳转到当前角色所属入口（或旧路径，由路由按角色再重定向） */
function isRedirectAllowed(path, roleCode) {
  if (!path || !path.startsWith('/') || path.startsWith('//')) return false
  if (path.startsWith('/login') || path.startsWith('/403')) return false
  if (path.startsWith('/admin')) return roleCode === 'ADMIN'
  if (path.startsWith('/agent')) return roleCode === 'AGENT'
  if (path.startsWith('/user')) return roleCode === 'USER'
  return true
}

async function onSubmit() {
  if (btnState.value !== 'idle') return
  if (!validate()) {
    // 只摇第一个出错的框，其余保持红框 + 文字，避免整页一起抖
    if (errors.username) shakeField('username')
    else if (errors.password) shakeField('password')
    return
  }

  btnState.value = 'loading'
  errorMessage.value = ''
  authFailed.value = false
  try {
    const data = await login({ username: form.username, password: form.password })
    const token = data?.token || data?.accessToken
    if (!token) {
      errorMessage.value = '登录失败，请稍后重试'
      btnState.value = 'idle'
      return
    }
    syncRemembered()
    saveAuth(token, data.user || null)

    const roleCode = data.user?.roleCode
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = isRedirectAllowed(redirect, roleCode) ? redirect : roleHomePath(roleCode)
    // 成功：先画出对勾，停留 900ms 让用户看见结果，再跳转
    btnState.value = 'success'
    window.setTimeout(() => {
      ElMessage.success(`欢迎回来，${data.user?.nickname || form.username}`)
      router.replace(target)
    }, 900)
  } catch (error) {
    const status = error?.response?.status
    // 只有凭据被拒才标红输入框；网络/服务端错误不怪用户
    authFailed.value = status === 400 || status === 401 || status === 403
    errorMessage.value =
      error?.response?.data?.message || error?.message || '登录失败，请检查账号密码'
    btnState.value = 'idle'
    shakeSubmit()
  }
}

onMounted(() => {
  let saved = ''
  try {
    saved = localStorage.getItem(REMEMBER_KEY) || ''
  } catch {
    saved = ''
  }
  if (saved) {
    form.username = saved
    form.remember = true
  }
  nextTick(() => {
    if (saved) {
      passwordInput.value?.focus()
    } else {
      usernameInput.value?.focus()
    }
  })
})

onBeforeUnmount(() => {
  if (shakeTimer) window.clearTimeout(shakeTimer)
  if (btnShakeTimer) window.clearTimeout(btnShakeTimer)
})
</script>

<style scoped>
/* =========================================================
   登录页 · 设计稿 1536×1024 像素还原
   --s 为设计稿缩放系数：视口宽 1536 时恒为 1，其余尺寸按比例整体缩放
   ========================================================= */
.login-page {
  --lp-blue: #126cfe;
  --lp-blue-deep: #0b57d0;
  --lp-ink: #0b1b3c;
  --lp-line: #edf0f7;
  --lp-placeholder: #a9b6c8;
  /* 1 设计像素 = 多少真实像素：按宽/高两个方向取较小值，保证 1536×1024 设计稿等比缩放不溢出 */
  --u: min(1.4px, calc(100vw / 1536), calc(100vh / 1024));
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  isolation: isolate;
  overflow: hidden;
}

/* 背景：独立素材，等比铺满视口 */
.login-bg {
  position: fixed;
  inset: 0;
  z-index: -1;
  background-color: #eef5fd;
  background-image: url('/login/login-bg.webp');
  background-position: center center;
  background-size: cover;
  background-repeat: no-repeat;
}

/* 插画：独立素材，按设计稿位置（左 98、底 122、宽 640）摆放 */
.login-art {
  position: absolute;
  z-index: 0;
  left: calc(113 * var(--u));
  bottom: calc(154 * var(--u));
  width: calc(589 * var(--u));
  height: auto;
  pointer-events: none;
  user-select: none;
  -webkit-user-drag: none;
}

.login-shell {
  position: relative;
  width: calc(1536 * var(--u));
  max-width: 100%;
  margin: 0 auto;
  min-height: 100vh;
  min-height: 100dvh;
}

/* ---------------- 左侧：按设计稿坐标（1536×1024）定位 ---------------- */
.intro {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.brand {
  position: absolute;
  left: calc(105 * var(--u));
  top: calc(70 * var(--u));
  width: calc(54 * var(--u));
  height: calc(54 * var(--u));
}

.brand-mark {
  display: block;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 calc(8 * var(--u)) calc(16 * var(--u)) rgba(16, 104, 253, 0.18));
}

.brand-text {
  position: absolute;
  left: calc(69 * var(--u));
  top: calc(8.5 * var(--u));
  padding: 0;
}

.brand-name {
  font-size: calc(32.5 * var(--u));
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: calc(0.3 * var(--u));
  color: #071433;
  white-space: nowrap;
}

.brand-slogan {
  margin-top: calc(7 * var(--u));
  font-size: calc(14 * var(--u));
  line-height: calc(20 * var(--u));
  letter-spacing: calc(3.9 * var(--u));
  color: #8b98ac;
  white-space: nowrap;
}

.headline {
  position: absolute;
  left: calc(108 * var(--u));
  top: calc(213 * var(--u));
  margin: 0;
  font-size: calc(44 * var(--u));
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: calc(-0.6 * var(--u));
  color: var(--lp-ink);
  white-space: nowrap;
}

.subline {
  position: absolute;
  left: calc(108 * var(--u));
  top: calc(271 * var(--u));
  margin: 0;
  font-size: calc(21 * var(--u));
  line-height: 1.5;
  color: #66788f;
  white-space: nowrap;
}

.features {
  position: absolute;
  left: calc(108 * var(--u));
  top: calc(341 * var(--u));
  display: flex;
  margin: 0;
  padding: 0;
  list-style: none;
}

.feature {
  flex: 0 0 auto;
}

.feature:nth-child(1) {
  width: calc(194 * var(--u));
}
.feature:nth-child(2) {
  width: calc(178 * var(--u));
}
.feature:nth-child(3) {
  width: calc(170 * var(--u));
}
.feature:nth-child(4) {
  width: auto;
}

.feature-icon {
  display: block;
  width: calc(30 * var(--u));
  height: calc(30 * var(--u));
  object-fit: contain;
  filter: drop-shadow(0 calc(6 * var(--u)) calc(10 * var(--u)) rgba(16, 104, 253, 0.14));
}

.feature-icon--robot {
  color: #2f6bff;
  filter: none;
}

.feature-title {
  margin-top: calc(15 * var(--u));
  font-size: calc(17 * var(--u));
  line-height: 1.2;
  font-weight: 600;
  color: #15233b;
  white-space: nowrap;
}

.feature-desc {
  display: flex;
  flex-direction: column;
  margin-top: calc(9 * var(--u));
  font-size: calc(14 * var(--u));
  line-height: calc(21.5 * var(--u));
  color: #93a4b8;
  white-space: nowrap;
}

/* ---------------- 右侧登录卡片 ---------------- */
.auth {
  position: absolute;
  z-index: 2;
  left: calc(886 * var(--u));
  top: calc(50% + 11 * var(--u));
  transform: translateY(-50%);
  width: calc(556 * var(--u));
}

.card {
  width: 100%;
  background: #ffffff;
  border-radius: calc(10 * var(--u));
  padding: calc(65 * var(--u)) calc(53 * var(--u)) calc(46 * var(--u));
  box-shadow:
    0 calc(30 * var(--u)) calc(68 * var(--u)) calc(-40 * var(--u)) rgba(13, 72, 160, 0.28),
    0 calc(2 * var(--u)) calc(10 * var(--u)) rgba(13, 72, 160, 0.04);
}

/* 登录页五个区块以 60ms 依次浮现（每个 420ms，进场 ease-out，只播一次） */
.intro .brand,
.headline,
.subline,
.features,
.card {
  animation: lp-rise 420ms var(--zk-ease-soft) both;
  animation-delay: calc(var(--lp-i, 0) * 60ms);
}

.intro .brand {
  --lp-i: 0;
}

.headline {
  --lp-i: 1;
}

.subline {
  --lp-i: 2;
}

.features {
  --lp-i: 3;
}

.card {
  --lp-i: 4;
}

/* 登录方式 Tab */
.tabs {
  display: flex;
  align-items: flex-end;
  gap: calc(51 * var(--u));
  padding-left: calc(11 * var(--u));
  border-bottom: 1px solid #eef1f6;
}

.tab {
  position: relative;
  padding: 0 calc(7 * var(--u)) calc(16 * var(--u));
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: calc(20 * var(--u));
  line-height: calc(28 * var(--u));
  font-weight: 600;
  color: #3b465c;
  cursor: pointer;
  transition: color 160ms ease;
}

.tab:hover {
  color: var(--lp-blue);
}

.tab.is-active {
  color: var(--lp-blue);
}

.tab.is-active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--lp-blue);
}

/* 表单 */
.form {
  margin-top: calc(41 * var(--u));
}

.field {
  display: flex;
  align-items: center;
  gap: calc(18 * var(--u));
  height: calc(50 * var(--u));
  padding: 0 calc(14 * var(--u));
  border: 1px solid var(--lp-line);
  border-radius: calc(9 * var(--u));
  background: #ffffff;
  transition:
    border-color var(--zk-dur-base) var(--zk-ease-out),
    box-shadow var(--zk-dur-base) var(--zk-ease-out);
}

.field + .field {
  margin-top: calc(25 * var(--u));
}

.field:hover {
  border-color: #d3dded;
}

.field:focus-within {
  border-color: var(--lp-blue);
  box-shadow: 0 0 0 3px rgba(16, 104, 253, 0.12);
}

.field.has-error {
  border-color: #f0a9b8;
}

.field-icon {
  display: flex;
  flex: 0 0 calc(20 * var(--u));
  width: calc(20 * var(--u));
  height: calc(20 * var(--u));
  color: #8d9cb1;
  transition: color var(--zk-dur-base) var(--zk-ease-out);
}

/* 聚焦时图标跟着主色亮起，和边框同一节奏 */
.field:focus-within .field-icon {
  color: var(--lp-blue);
}

.field-icon svg {
  width: 100%;
  height: 100%;
}

.field input {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  font-family: inherit;
  font-size: calc(16 * var(--u));
  color: #1b2740;
  outline: none;
}

.field input::placeholder {
  color: var(--lp-placeholder);
}

.field input:-webkit-autofill {
  -webkit-box-shadow: 0 0 0 1000px #ffffff inset;
  -webkit-text-fill-color: #1b2740;
}

.eye {
  display: grid;
  flex: 0 0 calc(22 * var(--u));
  place-items: center;
  width: calc(22 * var(--u));
  height: calc(22 * var(--u));
  padding: 0;
  border: 0;
  background: transparent;
  color: #9aa8bb;
  cursor: pointer;
  transition: color 160ms ease;
}

.eye:hover {
  color: #63738c;
}

.eye svg {
  grid-area: 1 / 1;
  width: calc(20 * var(--u));
  height: calc(20 * var(--u));
}

/* 密码小眼睛：切换时像眨一下眼，160ms 内完成（模仿 83 密码小眼睛） */
.zk-eye-enter-active,
.zk-eye-leave-active {
  transition: opacity 160ms var(--zk-ease-out), transform 160ms var(--zk-ease-out);
}

.zk-eye-enter-from,
.zk-eye-leave-to {
  opacity: 0;
  transform: scaleY(0.2);
}

/* 记住账号 / 忘记密码 */
.helper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: calc(27 * var(--u));
}

.remember {
  display: inline-flex;
  align-items: center;
  gap: calc(12 * var(--u));
  font-size: calc(14 * var(--u));
  color: #4c586c;
  cursor: pointer;
  user-select: none;
}

.remember input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.checkbox {
  display: grid;
  flex: 0 0 calc(19 * var(--u));
  place-items: center;
  width: calc(19 * var(--u));
  height: calc(19 * var(--u));
  border: 1.5px solid #ccd6e3;
  border-radius: calc(5 * var(--u));
  background: #ffffff;
  color: #ffffff;
  transition:
    background-color 160ms ease,
    border-color 160ms ease;
}

.checkbox svg {
  width: calc(14 * var(--u));
  height: calc(14 * var(--u));
  opacity: 0;
  transform: scale(0.7);
  transition:
    opacity 140ms ease,
    transform 140ms ease;
}

.remember input:checked + .checkbox {
  border-color: var(--lp-blue);
  background: var(--lp-blue);
}

.remember input:checked + .checkbox svg {
  opacity: 1;
  transform: scale(1);
}

.remember input:focus-visible + .checkbox {
  box-shadow: 0 0 0 3px rgba(16, 104, 253, 0.18);
}

.link {
  padding: 0;
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: calc(14 * var(--u));
  color: var(--lp-blue);
  cursor: pointer;
  transition: color 160ms ease;
}

.link:hover {
  color: var(--lp-blue-deep);
  text-decoration: underline;
}

/* 错误提示：常驻 34px 占位，出现时只滑下淡入，布局高度不变 */
.error-slot {
  position: relative;
  height: calc(34 * var(--u));
}

.form-error {
  position: absolute;
  top: calc(3 * var(--u));
  right: 0;
  left: 0;
  display: flex;
  align-items: center;
  gap: calc(8 * var(--u));
  padding: calc(4 * var(--u)) calc(10 * var(--u));
  border: 1px solid #ffd6de;
  border-radius: calc(9 * var(--u));
  background: #fff5f7;
  font-size: calc(12.5 * var(--u));
  line-height: 1.5;
  color: #c81e3c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.form-error svg {
  flex: 0 0 calc(16 * var(--u));
  width: calc(16 * var(--u));
  height: calc(16 * var(--u));
}

/* 提示固定单行，超出省略：保证错误区高度恒定，按钮不会被挤动 */
.form-error span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zk-err-enter-active {
  transition: opacity var(--zk-dur-base) var(--zk-ease-out),
    transform 220ms var(--zk-ease-out);
}

.zk-err-leave-active {
  transition: opacity var(--zk-dur-fast) var(--zk-ease-in),
    transform var(--zk-dur-fast) var(--zk-ease-in);
}

.zk-err-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}

.zk-err-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 登录按钮 */
.submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: calc(8 * var(--u));
  width: 100%;
  height: calc(49 * var(--u));
  margin-top: calc(4 * var(--u));
  padding: 0;
  border: 0;
  border-radius: calc(9 * var(--u));
  background: var(--lp-blue);
  color: #ffffff;
  font-family: inherit;
  font-size: calc(18 * var(--u));
  font-weight: 600;
  letter-spacing: calc(2 * var(--u));
  cursor: pointer;
  box-shadow: 0 calc(12 * var(--u)) calc(24 * var(--u)) calc(-14 * var(--u)) rgba(18, 108, 254, 0.9);
  transition:
    background-color 160ms ease,
    box-shadow 160ms ease,
    transform 120ms ease;
}

.submit:hover:not(:disabled) {
  background: #0b5ce8;
  box-shadow: 0 14px 28px -14px rgba(16, 104, 253, 0.95);
}

.submit:active:not(:disabled) {
  background: #0a52cc;
  transform: translateY(1px);
}

.submit:disabled {
  cursor: not-allowed;
  opacity: 0.72;
  box-shadow: none;
}

/* 加载 / 成功属于主动反馈，不再压暗 */
.submit.is-loading:disabled,
.submit.is-success:disabled {
  opacity: 1;
}

/* 提交按钮三连变：文字、转圈、对勾在同一格里切换，按钮尺寸始终不变 */
.zk-submit-enter-active {
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out),
    transform 180ms var(--zk-ease-spring);
}

.zk-submit-leave-active {
  transition: opacity 110ms var(--zk-ease-in), transform 110ms var(--zk-ease-in);
}

.zk-submit-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.zk-submit-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 成功：底色转绿，对勾用描边动画画出来（350ms） */
.submit.is-success {
  background: var(--zk-green);
  box-shadow: none;
}

.submit-check {
  width: 22px;
  height: 22px;
  color: #ffffff;
}

.submit-check path {
  stroke-dasharray: 24;
  stroke-dashoffset: 24;
}

.submit.is-success .submit-check path {
  animation: zk-check-draw 350ms var(--zk-ease-out) forwards;
}

.spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: lp-spin 700ms linear infinite;
}

/* 验证码登录：未开通短信通道的明确说明 */
.gate {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 303px;
  text-align: center;
}

.gate-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(16, 104, 253, 0.07);
  color: var(--lp-blue);
}

.gate-icon svg {
  width: 24px;
  height: 24px;
}

.gate h3 {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #15233b;
}

.gate p {
  margin-top: 10px;
  max-width: 372px;
  font-size: 13px;
  line-height: 1.7;
  color: #7b889c;
}

.gate-action {
  margin-top: 18px;
  padding: 0;
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  color: var(--lp-blue);
  cursor: pointer;
}

.gate-action:hover {
  color: var(--lp-blue-deep);
  text-decoration: underline;
}

/* 其他登录方式 */
.divider {
  display: flex;
  align-items: center;
  gap: calc(18 * var(--u));
  margin-top: calc(40 * var(--u));
  font-size: calc(14 * var(--u));
  color: #97a3b5;
  white-space: nowrap;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1 1 auto;
  height: 1px;
  background: #eef1f6;
}

.sso {
  display: grid;
  place-items: center;
  width: calc(46 * var(--u));
  height: calc(46 * var(--u));
  margin: calc(19 * var(--u)) auto 0;
  border: 0;
  border-radius: 50%;
  background: rgba(16, 104, 253, 0.06);
  color: var(--lp-blue);
  cursor: pointer;
  transition:
    background-color 160ms ease,
    transform 160ms ease;
}

.sso:hover {
  background: rgba(16, 104, 253, 0.12);
  transform: translateY(-1px);
}

.sso-shield {
  width: calc(26 * var(--u));
  height: calc(26 * var(--u));
}

.sso-label {
  margin-top: calc(12 * var(--u));
  font-size: calc(13 * var(--u));
  line-height: calc(20 * var(--u));
  text-align: center;
  color: #6d7b8e;
}

.legal {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: calc(2 * var(--u));
  margin-top: calc(45 * var(--u));
  padding-bottom: 0;
  font-size: calc(14 * var(--u));
  line-height: calc(22 * var(--u));
  color: #9aa6b8;
  flex-wrap: wrap;
}

.legal .link {
  font-size: calc(14 * var(--u));
}

.legal-shield {
  width: calc(15 * var(--u));
  height: calc(15 * var(--u));
  margin-right: calc(6 * var(--u));
  color: #9aa6b8;
}

/* ---------------- 动效偏好 ---------------- */
@media (prefers-reduced-motion: reduce) {
  .intro .brand,
  .headline,
  .subline,
  .features,
  .card {
    animation: none;
  }
  .spinner {
    animation-duration: 1.4s;
  }
}

/* ---------------- 响应式：≤1023 由「设计稿缩放」切回居中单卡布局 ---------------- */
@media (max-width: 1023px) {
  .login-page {
    --u: 1px;
    overflow-x: hidden;
  }
  .login-shell {
    display: flex;
    width: 100%;
    justify-content: center;
    padding: 32px 24px 40px;
  }
  .intro {
    display: none;
  }
  .login-art {
    display: none;
  }
  .login-bg {
    background-image: url('/login/login-bg-sm.webp');
    background-position: center top;
  }
  .auth {
    position: static;
    transform: none;
    width: 100%;
    max-width: 520px;
  }
  .card {
    padding: 46px 40px 0;
    border-radius: 14px;
    max-width: 100%;
  }
  .gate {
    min-height: 280px;
  }
}

@media (max-width: 560px) {
  .login-shell {
    padding: 20px 16px 28px;
  }
  .card {
    padding: 36px 22px 0;
    border-radius: 12px;
  }
  .tabs {
    gap: 32px;
    min-width: 0;
  }
  .tab {
    font-size: 18px;
    line-height: 26px;
    padding-bottom: 12px;
  }
  .form {
    margin-top: 32px;
  }
  .helper-row {
    flex-wrap: wrap;
    gap: 10px;
    min-width: 0;
  }
  .field {
    gap: 14px;
  }
  .field input {
    font-size: 15px;
  }
  .divider {
    margin-top: 30px;
  }
  .legal {
    padding-bottom: 40px;
    font-size: 13px;
    line-height: 20px;
    min-width: 0;
  }
  .legal .link {
    font-size: 13px;
  }
}

/* ---------------- 弹层（telegraphed 到 body，样式为全局） ---------------- */
</style>

<style>
.zk-login-dialog.el-dialog {
  border-radius: 12px;
  padding: 8px 4px 4px;
}

.zk-login-dialog .el-dialog__title {
  font-size: 17px;
  font-weight: 600;
  color: #15233b;
}

.zk-login-dialog__body p {
  margin: 0 0 10px;
  font-size: 13.5px;
  line-height: 1.85;
  color: #5b6779;
}

.zk-login-dialog__body p:last-child {
  margin-bottom: 0;
}

.zk-login-dialog__ok {
  height: 36px;
  padding: 0 18px;
  border: 0;
  border-radius: 8px;
  background: #1068fd;
  color: #fff;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
}

.zk-login-dialog__ok:hover {
  background: #0b5ce8;
}

/* ---------------- 动效 ---------------- */
@keyframes lp-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes lp-spin {
  to {
    transform: rotate(360deg);
  }
}

</style>
