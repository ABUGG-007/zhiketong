<template>
  <div class="zk-page chat-page">
    <PageHeader
      eyebrow="AI ASSISTANT"
      title="AI 聊天"
      description="用户体验端：消息发送到 POST /api/ai/chat，命中知识库直接回复，未命中调用大模型；无 Key 或超时时返回降级话术并打上标记。"
    >
      <template #actions>
        <div class="ws-chip" :class="wsTone">
          <i class="ws-dot" />
          {{ wsLabel }}
        </div>
        <el-button :icon="Delete" @click="resetChat">清空对话</el-button>
      </template>
    </PageHeader>

    <div class="chat-shell">
      <section class="zk-card chat-main">
        <header class="chat-head">
          <div class="bot-avatar">AI</div>
          <div class="bot-info">
            <div class="bot-name">智客通智能助手</div>
            <div class="bot-sub">
              知识库优先 · DeepSeek 兜底 · 当前对话 {{ userTurns }} 轮
            </div>
          </div>
          <div class="chat-head-right">
            <span class="model-chip">{{ currentModel }}</span>
            <span class="api-chip" :class="{ ok: apiOk }">
              {{ apiOk ? '接口正常' : '待调用' }}
            </span>
          </div>
        </header>

        <div
          ref="scrollRef"
          class="chat-scroll"
          role="log"
          aria-live="polite"
          aria-label="对话消息"
          :aria-busy="sending"
          @scroll="onScroll"
        >
          <div v-for="msg in messages" :key="msg.id" class="msg" :class="msg.role">
            <div v-if="msg.role === 'ai'" class="msg-avatar" aria-hidden="true">AI</div>
            <div class="msg-body">
              <div class="bubble" :class="{ 'bubble--ai': msg.role === 'ai', 'bubble--error': msg.error }">
                <span class="bubble-text">{{ msg.content }}</span>
                <span v-if="msg.fallback" class="fallback-tag" :title="msg.fallbackReason || 'AI 服务降级回复'">
                  降级回复
                </span>
              </div>
              <div class="msg-meta">
                <span class="msg-time">{{ msg.time }}</span>
                <span v-if="msg.model" class="meta-chip">{{ msg.model }}</span>
                <span v-if="msg.elapsedMs" class="meta-chip">{{ msg.elapsedMs }} ms</span>
                <span v-if="msg.fallback && msg.fallbackReason" class="meta-chip meta-chip--warn">
                  {{ msg.fallbackReason }}
                </span>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="msg-avatar msg-avatar--user" aria-hidden="true">
              {{ (user?.nickname || '我').slice(0, 1) }}
            </div>
          </div>

          <div v-if="sending" class="msg ai" role="status" aria-label="AI 正在思考">
            <div class="msg-avatar" aria-hidden="true">AI</div>
            <div class="msg-body">
              <div class="bubble bubble--ai">
                <span class="typing" aria-hidden="true"><i /><i /><i /></span>
                <span class="typing-text">正在思考…</span>
              </div>
            </div>
          </div>
        </div>

        <button
          v-if="showJump"
          type="button"
          class="jump-btn"
          aria-label="跳到最新消息"
          @click="scrollToBottom"
        >
          <el-icon aria-hidden="true"><ArrowDown /></el-icon>
          最新消息
        </button>

        <div class="chat-input">
          <div class="quick-row">
            <span class="quick-label">快捷问题</span>
            <button
              v-for="question in quickQuestions"
              :key="question"
              type="button"
              class="quick-chip"
              :disabled="sending"
              @click="send(question)"
            >
              {{ question }}
            </button>
          </div>
          <div class="input-row">
            <el-input
              ref="inputRef"
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="500"
              show-word-limit
              aria-label="输入消息内容"
              placeholder="输入问题后按 Enter 发送，Shift + Enter 换行"
              class="chat-textarea"
              @keydown.enter.exact.prevent="send()"
            />
            <el-button
              type="primary"
              class="send-btn"
              aria-label="发送消息"
              :loading="sending"
              :disabled="!input.trim() || sending"
              @click="send()"
            >
              <el-icon aria-hidden="true"><Promotion /></el-icon>
              <span>发送</span>
            </el-button>
          </div>
        </div>
      </section>

      <aside class="chat-side">
        <div class="zk-card side-card">
          <div class="side-title">对话能力</div>
          <ul class="cap-list">
            <li>
              <span class="cap-dot teal" />
              <div><b>知识库匹配</b><p>关键词 / 语义命中标准问答，直接回复</p></div>
            </li>
            <li>
              <span class="cap-dot indigo" />
              <div><b>大模型兜底</b><p>未命中时调用 DeepSeek，保留最近 6 轮上下文</p></div>
            </li>
            <li>
              <span class="cap-dot amber" />
              <div><b>降级保护</b><p>无 Key 或超时返回降级话术并标记</p></div>
            </li>
          </ul>
        </div>

        <div class="zk-card side-card">
          <div class="side-title">转人工</div>
          <p class="side-desc">AI 无法解决时可申请转接人工坐席，会话进入等待队列。</p>
          <el-button class="transfer-btn" @click="onTransfer" :icon="Service">申请转人工</el-button>
          <p class="side-note">真实排队接入将在 M4 里程碑开放</p>
        </div>

        <div class="zk-card side-card">
          <div class="side-title">连接状态</div>
          <div class="status-line">
            <span>POST /api/ai/chat</span>
            <span class="status-value" :class="apiOk ? 'ok' : 'idle'">
              {{ apiOk ? '200 OK' : '尚未调用' }}
            </span>
          </div>
          <div class="status-line">
            <span>WS /ws/chat</span>
            <span class="status-value" :class="wsState === 'open' ? 'ok' : 'idle'">
              {{ wsLabel }}
            </span>
          </div>
          <div class="status-line">
            <span>心跳间隔</span>
            <span class="status-value idle">20s</span>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Delete, Promotion, Service } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { sendAiMessage } from '@/api'
import { getToken, getUser } from '@/api/http'
import { nowClock } from '@/utils/dict'

const user = getUser()

const scrollRef = ref(null)
const inputRef = ref(null)
const input = ref('')
const sending = ref(false)
const apiOk = ref(false)
const currentModel = ref('deepseek-chat')
const messages = ref([])
const showJump = ref(false)

let messageSeq = 0
const nextId = () => {
  messageSeq += 1
  return `m-${Date.now()}-${messageSeq}`
}

const quickQuestions = ['怎么退款', '忘记密码怎么办', '如何开发票', '转人工']

const userTurns = computed(() => messages.value.filter((msg) => msg.role === 'user').length)

/* ---------------- 初始欢迎语 ---------------- */
function greetingMessage() {
  return {
    id: nextId(),
    role: 'ai',
    content:
      '你好，我是智客通智能助手。\n我可以帮你查询订单、处理退款、找回密码，也可以解答产品使用问题。如果问题比较复杂，随时可以申请转人工。',
    time: nowClock()
  }
}

function resetChat() {
  messages.value = [greetingMessage()]
  input.value = ''
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
      showJump.value = false
    }
  })
}

function onScroll() {
  const el = scrollRef.value
  if (!el) return
  showJump.value = el.scrollHeight - el.scrollTop - el.clientHeight > 120
}

/* ---------------- 发送消息 ---------------- */
async function send(text) {
  const content = (typeof text === 'string' ? text : input.value).trim()
  if (!content || sending.value) return

  input.value = ''
  messages.value.push({
    id: nextId(),
    role: 'user',
    content,
    time: nowClock()
  })
  sending.value = true
  scrollToBottom()

  const startedAt = performance.now()
  try {
    const data = await sendAiMessage(content)
    const elapsed = Number.isFinite(Number(data?.elapsedMs))
      ? Number(data.elapsedMs)
      : Math.round(performance.now() - startedAt)
    if (data?.model) currentModel.value = data.model
    messages.value.push({
      id: nextId(),
      role: 'ai',
      content: data?.content || '（AI 未返回内容，请重试）',
      model: data?.model || '',
      elapsedMs: elapsed,
      fallback: Boolean(data?.fallback),
      fallbackReason: data?.fallbackReason || '',
      time: nowClock()
    })
    apiOk.value = true
  } catch (error) {
    const status = error?.response?.status
    messages.value.push({
      id: nextId(),
      role: 'ai',
      error: true,
      content:
        status === 404
          ? 'AI 接口尚未部署（POST /api/ai/chat 返回 404），请确认后端已启动并加载 AI 模块。'
          : '抱歉，AI 服务暂时不可用，请稍后重试。你也可以点击右侧「申请转人工」由人工坐席接入。',
      time: nowClock()
    })
  } finally {
    sending.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}

function onTransfer() {
  ElMessage({
    message: '转人工排队功能将在 M4 里程碑开放',
    type: 'info',
    duration: 2600
  })
}

/* ---------------- WebSocket 状态（仅用于展示在线状态） ---------------- */
const wsState = ref('connecting')
const wsTone = computed(() => {
  if (wsState.value === 'open') return 'teal'
  if (wsState.value === 'connecting') return 'amber'
  return 'slate'
})
const wsLabel = computed(() => {
  if (wsState.value === 'open') return 'WS 已连接'
  if (wsState.value === 'connecting') return 'WS 连接中'
  if (wsState.value === 'closed') return 'WS 未连接'
  return 'WS 未启用'
})

let socket = null
let pingTimer = null
let retryTimer = null
let retryCount = 0

function wsUrl() {
  const token = getToken()
  const base = import.meta.env.VITE_WS_BASE
  if (base) {
    return `${base.replace(/\/$/, '')}/ws/chat?token=${encodeURIComponent(token)}`
  }
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws/chat?token=${encodeURIComponent(token)}`
}

function stopTimers() {
  if (pingTimer) window.clearInterval(pingTimer)
  if (retryTimer) window.clearTimeout(retryTimer)
  pingTimer = null
  retryTimer = null
}

function connectWs() {
  if (!getToken()) {
    wsState.value = 'disabled'
    return
  }
  try {
    socket = new WebSocket(wsUrl())
  } catch {
    wsState.value = 'closed'
    return
  }

  wsState.value = 'connecting'

  socket.onopen = () => {
    wsState.value = 'open'
    retryCount = 0
    stopTimers()
    // 协议约定：每 20s 发送一次 PING 心跳
    pingTimer = window.setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(
          JSON.stringify({
            type: 'PING',
            requestId: `ping-${Date.now()}`,
            timestamp: Date.now(),
            data: {}
          })
        )
      }
    }, 20000)
  }

  socket.onmessage = () => {
    // 仅用于展示连接状态，不解析业务消息
  }

  socket.onerror = () => {
    wsState.value = 'closed'
  }

  socket.onclose = () => {
    wsState.value = 'closed'
    stopTimers()
    // 最多重试 2 次，避免控制台刷错误
    if (retryCount < 2) {
      retryCount += 1
      retryTimer = window.setTimeout(connectWs, 4000 * retryCount)
    }
  }
}

onMounted(() => {
  resetChat()
  connectWs()
})

onBeforeUnmount(() => {
  stopTimers()
  if (socket) {
    socket.onclose = null
    socket.onerror = null
    try {
      socket.close()
    } catch {
      /* noop */
    }
    socket = null
  }
})
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
}

/* WS 状态徽标 */
.ws-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
}

.ws-chip.teal {
  color: #0f766e;
  background: #edfdf9;
  border-color: #b9f0e6;
}
.ws-chip.amber {
  color: #b45309;
  background: #fff8ea;
  border-color: #fbe0b0;
}
.ws-chip.slate {
  color: #64748b;
  background: #f4f6fa;
  border-color: #e2e8f0;
}

.ws-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.ws-chip.teal .ws-dot {
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.16);
}

/* 主布局 */
.chat-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 306px;
  gap: 16px;
  align-items: start;
  flex: 1;
}

.chat-main {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 196px);
  min-height: 560px;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--zk-border);
  background: linear-gradient(180deg, #fbfcfe, #ffffff);
  flex: 0 0 auto;
}

.bot-avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #14b8a6, #0ea5a4 45%, #4f46e5);
  box-shadow: 0 8px 18px -10px rgba(20, 184, 166, 0.9);
}

.bot-info {
  flex: 1;
  min-width: 0;
}

.bot-name {
  font-size: 14.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.bot-sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.chat-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-chip {
  font-size: 11px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: #4338ca;
  background: #eef0ff;
  border: 1px solid #d8dafb;
  border-radius: 999px;
  padding: 3px 9px;
}

.api-chip {
  font-size: 11px;
  color: #64748b;
  background: #f4f6fa;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 3px 9px;
}

.api-chip.ok {
  color: #0f766e;
  background: #e6fbf7;
  border-color: #bff0e7;
}

/* 消息区 */
.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 22px 20px 12px;
  background:
    radial-gradient(90% 60% at 100% 0%, #f6fbff 0%, transparent 60%),
    linear-gradient(180deg, #fdfefe 0%, #fbfcfe 100%);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  max-width: 86%;
}

.msg.user {
  align-self: flex-end;
  flex-direction: row;
  justify-content: flex-end;
}

.msg.ai {
  align-self: flex-start;
}

.msg-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #14b8a6, #4f46e5);
  box-shadow: 0 6px 14px -8px rgba(20, 184, 166, 0.9);
}

.msg-avatar--user {
  background: linear-gradient(140deg, #4f46e5, #818cf8);
  box-shadow: 0 6px 14px -8px rgba(79, 70, 229, 0.9);
}

.msg-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.msg.user .msg-body {
  align-items: flex-end;
}

.bubble {
  position: relative;
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--zk-text);
  background: #ffffff;
  border: 1px solid var(--zk-border);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble--ai {
  background: linear-gradient(180deg, #ffffff, #fbfdff);
  border-top-left-radius: 5px;
}

.msg.user .bubble {
  background: linear-gradient(135deg, #4f46e5, #5b54ea);
  border-color: transparent;
  color: #ffffff;
  border-top-right-radius: 5px;
  box-shadow: 0 8px 18px -12px rgba(79, 70, 229, 0.9);
}

.bubble--error {
  background: #fff7f8;
  border-color: #fbd0da;
  color: #9f1239;
}

.bubble-text {
  display: inline;
}

.fallback-tag {
  display: inline-block;
  margin-left: 8px;
  vertical-align: 1px;
  font-size: 10px;
  font-weight: 700;
  color: #b45309;
  background: #fff7e6;
  border: 1px solid #fde3b4;
  border-radius: 999px;
  padding: 1px 7px;
  white-space: nowrap;
  cursor: help;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 0 3px;
}

.msg.user .msg-meta {
  justify-content: flex-end;
}

.msg-time {
  font-size: 10.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.meta-chip {
  font-size: 10px;
  color: #64748b;
  background: #f4f6fa;
  border: 1px solid #e7ebf3;
  border-radius: 999px;
  padding: 1px 6px;
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.meta-chip--warn {
  color: #b45309;
  background: #fff7e6;
  border-color: #fde3b4;
  font-family: inherit;
}

.typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 8px;
}

.typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #14b8a6;
  animation: bounce 1.2s ease-in-out infinite;
}

.typing i:nth-child(2) {
  animation-delay: 0.15s;
}
.typing i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes bounce {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }
  50% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

.typing-text {
  font-size: 12.5px;
  color: var(--zk-text-3);
}

/* 输入区 */
.chat-input {
  flex: 0 0 auto;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--zk-border);
  background: #ffffff;
}

.quick-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.quick-label {
  font-size: 11px;
  color: var(--zk-text-3);
  letter-spacing: 0.3px;
}

.quick-chip {
  font-size: 12px;
  color: #4338ca;
  background: #f6f7ff;
  border: 1px solid #e0e2fb;
  border-radius: 999px;
  padding: 4px 11px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s ease;
}

.quick-chip:hover:not(:disabled) {
  background: #eef0ff;
  border-color: #c7cbf7;
  transform: translateY(-1px);
}

.quick-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.chat-textarea {
  flex: 1;
}

.chat-textarea :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 10px 12px;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  box-shadow: 0 0 0 1px #dfe5ef inset;
  resize: none;
}

.send-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 10px;
  font-weight: 600;
  gap: 5px;
}

/* 侧栏 */
.chat-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-card {
  padding: 16px 18px;
}

.side-title {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 12px;
}

.side-title::before {
  content: '';
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: linear-gradient(180deg, #14b8a6, #4f46e5);
}

.cap-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cap-list li {
  display: flex;
  gap: 9px;
  align-items: flex-start;
}

.cap-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 6px;
  flex: 0 0 7px;
}

.cap-dot.teal {
  background: #14b8a6;
}
.cap-dot.indigo {
  background: #6366f1;
}
.cap-dot.amber {
  background: #f59e0b;
}

.cap-list b {
  font-size: 12.5px;
  color: var(--zk-text);
  font-weight: 600;
}

.cap-list p {
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--zk-text-3);
}

.side-desc {
  font-size: 12px;
  line-height: 1.75;
  color: var(--zk-text-2);
}

.transfer-btn {
  width: 100%;
  margin-top: 12px;
  height: 38px;
  border-radius: 9px;
  border-color: #c7d2fe;
  color: #4338ca;
  background: #f6f7ff;
  font-weight: 600;
}

.transfer-btn:hover {
  background: #eef0ff;
  border-color: #a5b4fc;
  color: #3730a3;
}

.side-note {
  margin-top: 9px;
  font-size: 10.5px;
  color: var(--zk-text-3);
  text-align: center;
}

.status-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
  font-size: 11.5px;
  color: var(--zk-text-2);
  border-bottom: 1px dashed #f1f5f9;
}

.status-line:last-child {
  border-bottom: none;
}

.status-value {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 5px;
}

.status-value.ok {
  color: #0f766e;
  background: #e6fbf7;
}

.status-value.idle {
  color: #64748b;
  background: #f4f6fa;
}

@media (max-width: 1200px) {
  .chat-shell {
    grid-template-columns: minmax(0, 1fr);
  }
  .chat-side {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .chat-side .side-card {
    flex: 1;
    min-width: 240px;
  }
  .chat-main {
    height: calc(100vh - 180px);
  }
}

@media (max-width: 640px) {
  .msg {
    max-width: 96%;
  }
  .chat-head-right {
    display: none;
  }
}
/* ---------- v2：可访问性、回到底部与移动端 ---------- */
.chat-main {
  position: relative;
  height: calc(100vh - 196px);
  height: calc(100dvh - 196px);
}

.chat-scroll {
  scrollbar-gutter: stable;
  overscroll-behavior: contain;
}

.chat-scroll:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: -2px;
}

.jump-btn {
  position: absolute;
  right: 20px;
  bottom: 132px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--zk-border-strong);
  background: #fff;
  color: var(--zk-text-2);
  font-size: 12px;
  font-weight: 550;
  cursor: pointer;
  box-shadow: var(--zk-shadow-md);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    border-color var(--zk-dur-fast) var(--zk-ease-out),
    transform var(--zk-dur-fast) var(--zk-ease-out);
}

.jump-btn:hover {
  background: var(--zk-surface-2);
  border-color: var(--zk-teal-line);
  transform: translateY(-1px);
}

.jump-btn:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: 2px;
}

.quick-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 4px 12px;
}

.quick-chip:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: 2px;
}

.send-btn {
  height: 44px;
  min-width: 104px;
  border-radius: 11px;
}

.chat-textarea :deep(.el-textarea__inner) {
  min-height: 64px;
  padding: 10px 12px;
}

.bubble {
  line-height: 1.75;
}

@media (max-width: 640px) {
  .chat-shell {
    gap: 12px;
  }

  .chat-main {
    height: calc(100dvh - 172px);
    min-height: 480px;
  }

  .chat-head {
    padding: 12px 14px;
  }

  .chat-scroll {
    padding: 16px 14px 10px;
    gap: 14px;
  }

  .msg {
    max-width: 95%;
  }

  .bubble {
    font-size: 13.5px;
    line-height: 1.7;
  }

  .chat-input {
    padding: 10px 12px 12px;
  }

  .quick-row {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
    scrollbar-width: thin;
  }

  .quick-chip {
    flex: 0 0 auto;
  }

  .input-row {
    flex-direction: column;
    align-items: stretch;
  }

  .send-btn {
    width: 100%;
    height: 44px;
  }

  .chat-side .side-card {
    min-width: 100%;
  }

  .jump-btn {
    right: 12px;
    bottom: 158px;
  }
}
</style>
