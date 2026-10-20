<template>
  <div class="login">
    <!-- 品牌左栏 -->
    <section class="hero">
      <!-- 左侧能力展示：装饰性内容，屏幕阅读器跳过 -->
      <div class="hero-grid" aria-hidden="true" />
      <div class="hero-glow hero-glow--teal" aria-hidden="true" />
      <div class="hero-glow hero-glow--indigo" aria-hidden="true" />

      <div class="hero-inner">
        <div class="brand">
          <div class="brand-logo">智</div>
          <div>
            <div class="brand-name">智客通</div>
            <div class="brand-sub">智能客服 · 工单协同平台</div>
          </div>
        </div>

        <div class="hero-copy">
          <div class="hero-eyebrow">
            <span class="pulse" /> AI + 人工一体化客户服务
          </div>
          <h1>
            让每一条客户消息<br />
            <span class="accent">都有专业回应</span>
          </h1>
          <p class="hero-desc">
            知识库自动应答、排队转人工、工单闭环与 SLA 追踪，一个工作台完成客服全流程。
          </p>
        </div>

        <ul class="capabilities">
          <li v-for="item in capabilities" :key="item.title">
            <span class="cap-icon" :class="item.tone">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <div>
              <div class="cap-title">{{ item.title }}</div>
              <div class="cap-desc">{{ item.desc }}</div>
            </div>
          </li>
        </ul>

        <div class="hero-footer">
          <span>Spring Boot 2.7</span>
          <span class="dot-sep" />
          <span>Vue 3 + Vite</span>
          <span class="dot-sep" />
          <span>MySQL 8.0 · Redis</span>
        </div>
      </div>
    </section>

    <!-- 右侧登录 -->
    <section class="panel">
      <div class="panel-inner">
        <div class="mobile-brand">
          <div class="brand-logo">智</div>
          <span class="brand-name">智客通</span>
        </div>

        <div class="panel-head">
          <div class="zk-eyebrow">WELCOME BACK</div>
          <h2>登录智客通工作台</h2>
          <p>使用初始化账号进入，首次登录后请立即修改密码</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          :aria-describedby="errorMessage ? 'login-error' : undefined"
          :aria-busy="loading"
          @submit.prevent="onSubmit"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model.trim="form.username"
              size="large"
              placeholder="请输入账号"
              autocomplete="username"
              autofocus
              aria-label="账号"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              aria-label="密码"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <div v-if="errorMessage" id="login-error" class="form-error" role="alert" aria-live="assertive">
            <el-icon aria-hidden="true"><WarningFilled /></el-icon>
            <span>{{ errorMessage }}</span>
          </div>

          <el-button
            class="submit-btn"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
          >
            {{ loading ? '登录中…' : '登录工作台' }}
          </el-button>
        </el-form>

        <div class="panel-foot" role="status" aria-live="polite">
          <span class="foot-dot" :class="healthTone" aria-hidden="true" />
          <span>{{ healthText }}</span>
          <span class="foot-sep">·</span>
          <span>接口 {{ apiHint }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Collection,
  Headset,
  Lock,
  Tickets,
  User,
  WarningFilled
} from '@element-plus/icons-vue'
import { getHealth, login } from '@/api'
import { saveAuth } from '@/api/http'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const capabilities = [
  {
    title: '知识库智能应答',
    desc: '关键词 + 语义匹配，常见问题秒级回复',
    icon: Collection,
    tone: 'teal'
  },
  {
    title: '人机协同接待',
    desc: 'AI 无法解决时排队转接人工坐席',
    icon: Headset,
    tone: 'indigo'
  },
  {
    title: '工单闭环与 SLA',
    desc: '优先级 SLA 计时、超时预警与全链路日志',
    icon: Tickets,
    tone: 'amber'
  }
]

const apiHint = computed(() => {
  const env = import.meta.env.VITE_API_BASE
  return env || '/api（经由 Vite 代理 → localhost:8080）'
})

/* 后端健康状态（登录页也能看到服务是否在线） */
const health = ref({ status: 'loading' })
const healthTone = computed(() => {
  if (health.value.status === 'loading') return 'slate'
  if (health.value.status === 'UP') return 'teal'
  if (health.value.status === 'DEGRADED') return 'amber'
  return 'red'
})
const healthText = computed(() => {
  if (health.value.status === 'loading') return '正在检测后端服务…'
  if (health.value.status === 'UP') return '后端服务正常'
  if (health.value.status === 'DEGRADED') return '依赖服务降级'
  return '后端服务未连接（请确认 8080 已启动）'
})

async function onSubmit() {
  if (loading.value) return
  const formEl = formRef.value
  if (!formEl) return
  try {
    await formEl.validate()
  } catch {
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const data = await login({ username: form.username, password: form.password })
    const token = data?.token || data?.accessToken
    if (!token) {
      errorMessage.value = '登录响应缺少 token，请检查后端返回结构'
      return
    }
    saveAuth(token, data.user || null)
    ElMessage.success(`欢迎回来，${data.user?.nickname || form.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect.startsWith('/') ? redirect : '/dashboard')
  } catch (error) {
    errorMessage.value =
      error?.response?.data?.message || error?.message || '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const data = await getHealth()
    const mysqlUp = data?.checks?.mysql?.status === 'UP'
    const redisUp = data?.checks?.redis?.status === 'UP'
    health.value = { status: data?.status === 'UP' && mysqlUp && redisUp ? 'UP' : 'DEGRADED' }
  } catch {
    health.value = { status: 'DOWN' }
  }
})
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  background: #ffffff;
}

/* ---------------- 左侧品牌 ---------------- */
.hero {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 0% 0%, #17244a 0%, transparent 58%),
    linear-gradient(155deg, #080e1a 0%, #0f172a 46%, #101c33 100%);
  color: #e2e8f0;
  display: flex;
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(148, 163, 184, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(120% 100% at 20% 0%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(120% 100% at 20% 0%, #000 0%, transparent 78%);
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
  pointer-events: none;
}

.hero-glow--teal {
  width: 420px;
  height: 420px;
  background: #0f766e;
  top: -120px;
  left: -80px;
}

.hero-glow--indigo {
  width: 460px;
  height: 460px;
  background: #4338ca;
  bottom: -180px;
  right: -80px;
  opacity: 0.45;
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 30px;
  padding: 44px 56px 36px;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(140deg, #14b8a6, #0e9f9c 48%, #4f46e5);
  color: #fff;
  font-size: 19px;
  font-weight: 700;
  display: grid;
  place-items: center;
  box-shadow: 0 10px 24px -12px rgba(20, 184, 166, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #f8fafc;
}

.brand-sub {
  font-size: 11px;
  color: #8194ae;
  letter-spacing: 0.6px;
  margin-top: 3px;
}

.hero-copy {
  max-width: 560px;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  letter-spacing: 1px;
  color: #7dd3c8;
  background: rgba(20, 184, 166, 0.1);
  border: 1px solid rgba(20, 184, 166, 0.24);
  padding: 5px 11px;
  border-radius: 999px;
  margin-bottom: 18px;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2dd4bf;
  box-shadow: 0 0 0 4px rgba(45, 212, 191, 0.16);
  animation: pulse 2.2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgba(45, 212, 191, 0.16);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(45, 212, 191, 0.06);
  }
}

.hero-copy h1 {
  font-size: 38px;
  line-height: 1.32;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: #f8fafc;
}

.hero-copy h1 .accent {
  background: linear-gradient(100deg, #5eead4 0%, #a5b4fc 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-desc {
  margin-top: 16px;
  font-size: 13.5px;
  line-height: 1.85;
  color: #94a3b8;
  max-width: 480px;
}

.capabilities {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
  max-width: 520px;
}

.capabilities li {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 11px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(148, 163, 184, 0.13);
}

.cap-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 17px;
}

.cap-icon.teal {
  color: #5eead4;
  background: rgba(20, 184, 166, 0.14);
  border: 1px solid rgba(20, 184, 166, 0.24);
}

.cap-icon.indigo {
  color: #a5b4fc;
  background: rgba(79, 70, 229, 0.16);
  border: 1px solid rgba(99, 102, 241, 0.26);
}

.cap-icon.amber {
  color: #fcd34d;
  background: rgba(245, 158, 11, 0.13);
  border: 1px solid rgba(245, 158, 11, 0.24);
}

.cap-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #e2e8f0;
}

.cap-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #8ba0bd;
}

.chat-preview {
  max-width: 470px;
  border-radius: 16px;
  padding: 14px 16px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.15);
  backdrop-filter: blur(8px);
  display: grid;
  gap: 10px;
}

.chat-line {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.chat-line--user {
  justify-content: flex-end;
}

.chat-bubble {
  font-size: 12px;
  line-height: 1.7;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.14);
  color: #cbd5e1;
  max-width: 100%;
}

.chat-bubble--ai {
  background: rgba(20, 184, 166, 0.1);
  border: 1px solid rgba(20, 184, 166, 0.24);
  color: #d7f4ef;
  border-top-left-radius: 4px;
}

.ai-avatar {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border-radius: 8px;
  background: linear-gradient(140deg, #14b8a6, #4f46e5);
  color: #fff;
  font-size: 9.5px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.chat-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 7px;
  flex-wrap: wrap;
}

.hit-chip {
  font-size: 10px;
  color: #5eead4;
  background: rgba(20, 184, 166, 0.14);
  border: 1px solid rgba(20, 184, 166, 0.2);
  border-radius: 999px;
  padding: 2px 7px;
}

.chat-time {
  font-size: 10px;
  color: #64748b;
}

.hero-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: #64748b;
  letter-spacing: 0.4px;
  flex-wrap: wrap;
}

.dot-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #3b4a61;
}

/* ---------------- 右侧登录 ---------------- */
.panel {
  flex: 0 0 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: #ffffff;
  border-left: 1px solid var(--zk-border);
}

.panel-inner {
  width: 100%;
  max-width: 372px;
}

.mobile-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 26px;
}

.panel-head {
  margin-bottom: 26px;
}

.panel-head h2 {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--zk-text);
}

.panel-head p {
  margin-top: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--zk-text-2);
}

.panel-head b {
  color: var(--zk-indigo);
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 600;
  color: #475569;
  padding-bottom: 6px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dfe5ef inset;
  padding: 2px 12px;
}

.form-error {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #be123c;
  background: #fff1f4;
  border: 1px solid #fbd0da;
  border-radius: 8px;
  padding: 8px 11px;
  margin-bottom: 14px;
}

.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.demo-block {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px dashed var(--zk-border-strong);
}

.demo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}

.demo-hint {
  font-weight: 400;
  color: var(--zk-text-3);
  font-size: 11px;
}

.demo-chips {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.demo-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 9px 10px;
  border-radius: 10px;
  border: 1px solid var(--zk-border);
  background: #fbfcfe;
  cursor: pointer;
  transition: all 0.16s ease;
  font-family: inherit;
}

.demo-chip:hover {
  border-color: #c7d2fe;
  background: #f6f7ff;
  transform: translateY(-1px);
  box-shadow: 0 8px 18px -14px rgba(79, 70, 229, 0.8);
}

.chip-role {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 999px;
}

.chip-role.red {
  color: #be123c;
  background: #fff0f3;
}
.chip-role.indigo {
  color: #4338ca;
  background: #eef0ff;
}
.chip-role.slate {
  color: #475569;
  background: #f1f5fa;
}

.chip-user {
  font-size: 12px;
  color: #1f2b3d;
  font-weight: 600;
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.panel-foot {
  margin-top: 26px;
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: var(--zk-text-3);
  flex-wrap: wrap;
}

.foot-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
}
.foot-dot.teal {
  background: #14b8a6;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.14);
}
.foot-dot.amber {
  background: #f59e0b;
}
.foot-dot.red {
  background: #e11d48;
}
.foot-dot.slate {
  background: #94a3b8;
}

.foot-sep {
  color: #cbd5e1;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1180px) {
  .hero-inner {
    padding: 36px 40px 30px;
  }
  .hero-copy h1 {
    font-size: 32px;
  }
  .panel {
    flex-basis: 440px;
    padding: 40px 32px;
  }
}

@media (max-width: 960px) {
  .hero {
    display: none;
  }
  .panel {
    flex: 1;
    border-left: none;
  }
  .mobile-brand {
    display: flex;
  }
  .panel-inner {
    max-width: 400px;
  }
}

@media (max-height: 820px) and (min-width: 961px) {
  .chat-preview {
    display: none;
  }
  .hero-inner {
    gap: 22px;
  }
  .hero-copy h1 {
    font-size: 32px;
  }
}
/* ---------------- v2：对比度、焦点与移动端 ---------------- */
.login {
  min-height: 100vh;
  min-height: 100dvh;
}

.panel {
  overflow-y: auto;
}

.hero-desc {
  color: #a7b6c9;
}

.cap-desc {
  color: #a3b3c8;
}

.chat-time {
  color: #94a3b8;
}

.hero-footer {
  color: #94a3b8;
}

.demo-chip {
  min-height: 48px;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out),
    background-color var(--zk-dur-fast) var(--zk-ease-out),
    transform var(--zk-dur-fast) var(--zk-ease-out),
    box-shadow var(--zk-dur-fast) var(--zk-ease-out);
}

.demo-chip:hover {
  transform: translateY(-1px);
}

.demo-chip:active {
  transform: translateY(0);
}

.demo-chip:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: 2px;
}

.login-form :deep(.el-input__wrapper) {
  transition: box-shadow var(--zk-dur-fast) var(--zk-ease-out);
}

@media (max-width: 640px) {
  .panel {
    padding: 28px 20px;
    align-items: flex-start;
  }

  .panel-inner {
    max-width: 100%;
  }

  .panel-head h2 {
    font-size: 22px;
  }

  .demo-chips {
    gap: 6px;
  }

  .demo-chip {
    padding: 8px;
  }

  .hero-inner {
    padding: 28px 22px;
  }
}

@media (max-width: 380px) {
  .demo-chips {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
