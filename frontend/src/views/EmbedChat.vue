<template>
  <div class="embed-chat" :style="{ '--ec-accent': accent }">
    <header class="ec-head">
      <span class="ec-avatar" aria-hidden="true">
        <img src="/brand-mark.png" alt="" />
      </span>
      <div class="ec-head-main">
        <div class="ec-brand">{{ brand }}</div>
        <div class="ec-status"><i class="ec-dot" />智能客服 · 通常即时回复</div>
      </div>
      <button v-if="isWidget" type="button" class="ec-close" aria-label="关闭对话" @click="closeWidget">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </header>

    <div v-if="accessError" class="ec-alert" role="alert">
      <span>{{ accessError }}</span>
      <button type="button" @click="loadConfig">重试</button>
    </div>

    <main ref="bodyRef" class="ec-body" aria-live="polite">
      <div class="ec-day">今天</div>

      <!-- 与站内会话共用一套气泡入场（更轻）：用户从右下、机器人从左下来 -->
      <TransitionGroup :name="msgMotion ? 'zk-msg' : 'zk-quiet'" tag="div" class="ec-list">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="ec-row"
          :class="[`is-${msg.role}`, msg.role === 'user' ? 'is-user' : 'is-peer']"
        >
          <div class="ec-bubble">
            <p v-for="(line, lineIndex) in msg.lines" :key="lineIndex">{{ line }}</p>
            <div v-if="msg.meta" class="ec-meta">{{ msg.meta }}</div>
          </div>
        </div>
      </TransitionGroup>

      <div v-if="typing" class="ec-row is-ai is-peer zk-rise">
        <div class="ec-bubble ec-typing" aria-label="正在输入">
          <i /><i /><i />
        </div>
      </div>

      <Transition name="zk-fade">
        <div v-if="showQuick" class="ec-quick">
          <button
            v-for="(question, index) in quickQuestions"
            :key="question"
            type="button"
            class="ec-chip zk-press"
            :style="{ '--chip-i': index }"
            @click="send(question)"
          >
            {{ question }}
          </button>
        </div>
      </Transition>

      <div v-if="suggestHuman" class="ec-transfer zk-rise">
        <div class="ec-transfer-title">需要人工客服？</div>
        <p>人工服务时间 9:00–21:00。演示环境可登录用户中心，体验完整的排队、坐席接入与工单流程。</p>
        <a class="ec-transfer-btn" :href="loginUrl" target="_blank" rel="noopener">登录用户中心转人工</a>
      </div>
    </main>

    <footer class="ec-foot">
      <form class="ec-form" @submit.prevent="send()">
        <input
          v-model="draft"
          class="ec-input"
          type="text"
          maxlength="300"
          placeholder="输入你的问题…"
          aria-label="输入问题"
          :disabled="typing"
        />
        <button
          type="submit"
          class="ec-send"
          :class="{ 'is-sending': sendPhase === 'sending', 'is-sent': sendPhase === 'sent' }"
          :disabled="!draft.trim() || typing"
          aria-label="发送"
        >
          <span class="ec-send-icon" aria-hidden="true">
            <svg class="ec-send-glyph" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 12h15M13 6l6 6-6 6" />
            </svg>
            <span class="ec-send-ring" />
            <svg class="ec-send-check" viewBox="0 0 16 16" width="16" height="16" fill="none">
              <path d="M3.6 8.6l2.9 2.9 5.9-6.4" />
            </svg>
          </span>
        </button>
      </form>
      <div class="ec-note">由智客通提供技术支持</div>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { prefersReducedMotion } from '@/composables/motion'

const route = useRoute()
const query = route.query

const isWidget = query.widget === '1'
const appKey = typeof query.appKey === 'string' ? query.appKey.trim() : ''
const presetBrand = typeof query.brand === 'string' ? query.brand : ''
const presetWelcome = typeof query.welcome === 'string' ? query.welcome : ''
const presetColor = /^#[0-9a-fA-F]{6}$/.test(String(query.color || '')) ? String(query.color) : ''

// 悬浮球会随链接传入已换取好的令牌；独立链接则用 appKey 现场换取
let token = typeof query.token === 'string' ? query.token : ''

const brand = ref(presetBrand || '智客通客服')
const accent = ref(presetColor || '#4f46e5')
const bodyRef = ref(null)
const draft = ref('')
const typing = ref(false)
const suggestHuman = ref(false)
const quickQuestions = ref([])
const asked = ref(false)
const accessError = ref('')

const welcomeText = ref(
  presetWelcome || '你好，我是智客通智能客服。发票、退款、物流等常见问题可以直接问我。'
)

const messages = ref([{ role: 'ai', lines: [welcomeText.value], meta: '' }])

/* ---- 动效状态：只影响观感 ---- */
/** 首屏的欢迎语不播入场，挂载后一帧再开启 */
const msgMotion = ref(false)
/** 发送三态：idle / sending / sent */
const sendPhase = ref('idle')

let sendTimer = null
let followRaf = 0
let lastFollowTop = -1

const loginUrl = computed(() => {
  const target = encodeURIComponent('/user/chat')
  return `/login?redirect=${target}`
})

const showQuick = computed(
  () => !asked.value && !typing.value && quickQuestions.value.length > 0
)

function splitLines(text) {
  return String(text || '')
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
}

/** 跟随滚动：每帧只追剩余距离的 18%，不直接跳 scrollTop（motions 18） */
function followStep() {
  followRaf = 0
  const el = bodyRef.value
  if (!el) return
  if (lastFollowTop >= 0 && el.scrollTop < lastFollowTop - 2) {
    lastFollowTop = -1
    return
  }
  const gap = el.scrollHeight - el.scrollTop - el.clientHeight
  if (gap <= 1) {
    lastFollowTop = -1
    return
  }
  if (prefersReducedMotion()) {
    el.scrollTop = el.scrollHeight
    lastFollowTop = -1
    return
  }
  const next = el.scrollTop + gap * 0.18
  lastFollowTop = next
  el.scrollTop = next
  followRaf = requestAnimationFrame(followStep)
}

function scrollToBottom() {
  nextTick(() => {
    if (followRaf) return
    followRaf = requestAnimationFrame(followStep)
  })
}

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['X-ZKT-Embed-Token'] = token
  return headers
}

async function embedFetch(path, options) {
  const response = await fetch(`/api/public/embed${path}`, options)
  return response.json()
}

/**
 * 用 appKey 换取短时令牌；服务端同时返回保存的外观配置。
 */
async function requestToken() {
  if (!appKey) {
    throw new Error('缺少接入标识，请从控制台重新复制接入代码')
  }
  const payload = await embedFetch('/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ appKey })
  })
  if (payload?.code !== 200 || !payload.data) {
    throw new Error(payload?.message || '接入校验失败')
  }
  token = payload.data.token || ''
  applyConfig(payload.data.config)
}

/**
 * 服务端配置作为默认值；链接中显式传入的外观（控制台预览）优先级更高。
 */
function applyConfig(config) {
  if (!config) return
  if (!presetBrand && config.brandName) brand.value = config.brandName
  if (!presetColor && /^#[0-9a-fA-F]{6}$/.test(String(config.themeColor || ''))) {
    accent.value = config.themeColor
  }
  if (!presetWelcome && config.welcome) {
    welcomeText.value = config.welcome
    if (messages.value.length === 1 && messages.value[0].role === 'ai') {
      messages.value[0].lines = splitLines(config.welcome)
    }
  }
  quickQuestions.value = Array.isArray(config.quickQuestions)
    ? config.quickQuestions.slice(0, 4)
    : []
}

async function loadConfig() {
  accessError.value = ''
  try {
    if (!token) await requestToken()
    const payload = await embedFetch('/config', { headers: authHeaders() })
    if (payload?.code === 200 && payload.data) {
      applyConfig(payload.data)
      return
    }
    // 令牌过期：用 appKey 续期后重试一次
    if (payload?.code === 401 && appKey) {
      await requestToken()
      const retry = await embedFetch('/config', { headers: authHeaders() })
      if (retry?.code === 200 && retry.data) {
        applyConfig(retry.data)
        return
      }
      throw new Error(retry?.message || '接入校验失败')
    }
    throw new Error(payload?.message || '接入配置读取失败')
  } catch (error) {
    accessError.value = error?.message || '网络连接失败，请稍后重试'
    quickQuestions.value = []
  }
}

async function ask(content, retried) {
  const payload = await embedFetch('/answer', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ content })
  })
  if (payload?.code === 401 && !retried && appKey) {
    await requestToken()
    return ask(content, true)
  }
  return payload
}

/** 应答来源标注：与站内会话共用同一套编排（直答 / 参考生成 / 自由生成 / 兜底） */
function answerMeta(data) {
  switch (data.answerSource) {
    case 'kb-direct':
      return `知识库命中 · ${Math.round(Number(data.score || 0) * 100)}%`
    case 'ai-reference':
      return '知识库参考 · AI 生成'
    case 'ai-free':
      return 'AI 生成'
    case 'kb-suggest':
      return '知识库推荐 · 建议转人工'
    default:
      return data.suggestHuman ? '未命中知识库' : ''
  }
}

async function send(preset) {
  const content = (typeof preset === 'string' ? preset : draft.value).trim()
  if (!content || typing.value) return
  draft.value = ''
  asked.value = true
  suggestHuman.value = false
  accessError.value = ''
  messages.value.push({ role: 'user', lines: [content], meta: '' })
  typing.value = true
  sendPhase.value = 'sending'
  scrollToBottom()

  try {
    const payload = await ask(content, false)
    if (payload?.code === 200 && payload.data) {
      const data = payload.data
      const meta = answerMeta(data)
      messages.value.push({ role: 'ai', lines: splitLines(data.answer), meta })
      suggestHuman.value = Boolean(data.suggestHuman)
    } else {
      messages.value.push({
        role: 'ai',
        lines: [payload?.message || '服务繁忙，请稍后再试。'],
        meta: ''
      })
    }
    // 应答回来先给 500ms 短对勾，再回到常态
    sendPhase.value = 'sent'
    if (sendTimer) window.clearTimeout(sendTimer)
    sendTimer = window.setTimeout(() => {
      sendPhase.value = 'idle'
    }, 500)
  } catch (error) {
    messages.value.push({
      role: 'ai',
      lines: ['网络连接中断，请检查网络后重试。'],
      meta: ''
    })
  } finally {
    typing.value = false
    if (sendPhase.value === 'sending') sendPhase.value = 'idle'
    scrollToBottom()
  }
}

function closeWidget() {
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: 'zkt:close' }, '*')
  }
}

onMounted(() => {
  loadConfig()
  // 首屏的欢迎语已经在 DOM 里，之后再开启入场动画
  nextTick(() => {
    msgMotion.value = true
  })
})

onBeforeUnmount(() => {
  if (sendTimer) window.clearTimeout(sendTimer)
  if (followRaf) cancelAnimationFrame(followRaf)
  followRaf = 0
})
</script>

<style scoped>
.embed-chat {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  font-family: 'Inter', 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  color: #17181c;
}

.ec-head {
  flex: 0 0 52px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 14px;
  background: #fff;
  border-bottom: 1px solid #e8e8ea;
}

.ec-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 6px;
  background: #fff;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.ec-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.ec-head-main {
  flex: 1;
  min-width: 0;
  line-height: 1.3;
}

.ec-brand {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ec-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  color: #8b8e95;
}

.ec-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #10b981;
}

.ec-close {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #8b8e95;
  cursor: pointer;
}

.ec-close:hover {
  background: #f4f4f5;
  color: #17181c;
}

.ec-alert {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  background: #fef3c7;
  border-bottom: 1px solid #fde68a;
  font-size: 11.5px;
  line-height: 1.6;
  color: #92400e;
}

.ec-alert span {
  flex: 1;
  min-width: 0;
}

.ec-alert button {
  flex: 0 0 auto;
  border: 1px solid #d97706;
  background: transparent;
  border-radius: 5px;
  padding: 2px 8px;
  font-size: 11px;
  font-family: inherit;
  color: #92400e;
  cursor: pointer;
}

.ec-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ec-day {
  align-self: center;
  font-size: 10px;
  color: #a1a1aa;
  padding: 2px 0 4px;
}

/* 气泡列表：保持原有纵向间距，由 TransitionGroup 逐条插入 */
.ec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ec-row {
  display: flex;
}

.ec-row.is-user {
  justify-content: flex-end;
}

.ec-bubble {
  max-width: 86%;
  padding: 8px 11px;
  border-radius: 8px 8px 8px 2px;
  background: #fff;
  border: 1px solid #e8e8ea;
  font-size: 12.5px;
  line-height: 1.65;
  color: #33353b;
  word-break: break-word;
}

.ec-bubble p + p {
  margin-top: 4px;
}

.is-user .ec-bubble {
  border-radius: 8px 8px 2px 8px;
  background: var(--ec-accent);
  border-color: var(--ec-accent);
  color: #fff;
}

.ec-meta {
  margin-top: 4px;
  font-size: 10px;
  color: #a1a1aa;
}

.is-user .ec-meta {
  color: rgba(255, 255, 255, 0.72);
}

.ec-typing {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 11px 12px;
}

.ec-typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #b4b4bb;
  /* 思考点：1.2s 呼吸，缩放 0.75→1 并上浮 4px（motions 18） */
  animation: ec-typing-dot 1.2s var(--zk-ease-in-out, ease-in-out) infinite;
}

.ec-typing i:nth-child(2) {
  animation-delay: 0.15s;
}
.ec-typing i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes ec-typing-dot {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0) scale(0.75);
  }
  30% {
    opacity: 1;
    transform: translateY(-4px) scale(1);
  }
}

.ec-quick {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-top: 2px;
  padding-left: 2px;
}

.ec-chip {
  max-width: 100%;
  text-align: left;
  padding: 6px 10px;
  border-radius: 4px;
  border: 1px solid #e8e8ea;
  background: #fff;
  color: #55585f;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 120ms ease, color 120ms ease, background-color 120ms ease;
  /* 快捷问题 40ms 错开浮现 */
  animation: zk-rise-sm 240ms var(--zk-ease-soft, ease-out) backwards;
  animation-delay: calc(var(--chip-i, 0) * 40ms);
}

.ec-chip:hover {
  border-color: var(--ec-accent);
  color: var(--ec-accent);
  background: #fff;
}

.ec-transfer {
  margin-top: 4px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e8e8ea;
  border-radius: 6px;
}

.ec-transfer-title {
  font-size: 12px;
  font-weight: 600;
}

.ec-transfer p {
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #8b8e95;
}

.ec-transfer-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  margin-top: 8px;
  padding: 0 10px;
  border-radius: 6px;
  background: var(--ec-accent);
  color: #fff;
  font-size: 11.5px;
  font-weight: 500;
}

.ec-transfer-btn:hover {
  filter: brightness(0.95);
}

.ec-foot {
  flex: 0 0 auto;
  padding: 10px 12px 8px;
  background: #fff;
  border-top: 1px solid #e8e8ea;
}

.ec-form {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ec-input {
  flex: 1;
  min-width: 0;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e4e4e7;
  border-radius: 4px;
  background: #fff;
  font-size: 12.5px;
  font-family: inherit;
  color: #17181c;
  outline: none;
  transition: border-color 120ms ease, box-shadow 120ms ease;
}

.ec-input::placeholder {
  color: #a1a1aa;
}

.ec-input:focus {
  border-color: var(--ec-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ec-accent) 14%, transparent);
}

.ec-send {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: var(--ec-accent);
  color: #fff;
  cursor: pointer;
  transition: opacity 120ms ease;
}

.ec-send:disabled {
  background: #e4e4e7;
  color: #a1a1aa;
  cursor: not-allowed;
}

/* 发送三态：箭头 → 转圈（0.8s）→ 短对勾（500ms），图标区固定 16px 不跳宽 */
.ec-send-icon {
  position: relative;
  display: inline-block;
  width: 16px;
  height: 16px;
}

.ec-send-glyph,
.ec-send-ring,
.ec-send-check {
  position: absolute;
  inset: 0;
  transition: opacity 140ms var(--zk-ease-out, ease-out),
    transform 200ms var(--zk-ease-spring, ease-out);
}

.ec-send-glyph {
  display: block;
}

.ec-send-ring {
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.34);
  border-top-color: #fff;
  opacity: 0;
  transform: scale(0.7);
}

.ec-send-check {
  stroke: #fff;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0;
  transform: scale(0.7);
}

.ec-send.is-sending .ec-send-glyph,
.ec-send.is-sent .ec-send-glyph {
  opacity: 0;
  transform: scale(0.7);
}

.ec-send.is-sending .ec-send-ring {
  opacity: 1;
  transform: scale(1);
  animation: ec-send-spin 0.8s cubic-bezier(0.55, 0, 0.45, 1) infinite;
}

.ec-send.is-sent .ec-send-check {
  opacity: 1;
  transform: scale(1);
}

.ec-send.is-sent .ec-send-check path {
  stroke-dasharray: 24;
  animation: zk-check-draw 320ms var(--zk-ease-out, ease-out) both;
}

@keyframes ec-send-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 发送中/刚成功时保持主题色，不让禁用态把反馈冲淡 */
.ec-send.is-sending:disabled,
.ec-send.is-sent:disabled {
  background: var(--ec-accent);
  color: #fff;
  cursor: default;
}

.ec-note {
  margin-top: 6px;
  text-align: center;
  font-size: 10px;
  color: #a1a1aa;
}

@media (max-width: 520px) {
  .ec-body {
    padding: 12px 12px 6px;
  }
  .ec-bubble {
    max-width: 90%;
  }
}
</style>
