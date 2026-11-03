<template>
  <div class="zk-page chat-page">
    <PageHeader
      title="在线咨询"
      description="智能助手先答，复杂问题一键转人工，全程保留上下文；会话结束后可评价。"
    >
      <template #actions>
        <div class="ws-chip" :class="wsTone">
          <!-- 连接中/断线呼吸，连接正常时静止 -->
          <i class="ws-dot" :class="{ 'zk-dot-live': wsState !== 'open' }" aria-hidden="true" />
          {{ wsLabel }}
        </div>
        <el-button
          v-if="isClosed"
          type="primary"
          :icon="ChatDotRound"
          @click="startNewSession"
        >
          开始新会话
        </el-button>
        <el-button v-else :icon="SwitchButton" @click="onCloseSession">
          {{ isWaiting ? '取消排队' : '结束会话' }}
        </el-button>
      </template>
    </PageHeader>

    <div class="chat-shell">
      <section class="zk-card chat-main">
        <header class="chat-head">
          <div class="bot-avatar" :class="{ 'bot-avatar--human': isHuman }">
            {{ isHuman ? 'CS' : 'AI' }}
          </div>
          <div class="bot-info">
            <div class="bot-name">{{ headName }}</div>
            <div class="bot-sub">{{ headSub }}</div>
          </div>
          <div class="chat-head-right">
            <span class="model-chip">{{ sessionNo }}</span>
            <span class="api-chip" :class="{ ok: isLive, 'zk-flash': statusFlash }">{{ statusText }}</span>
          </div>
        </header>

        <div
          ref="scrollRef"
          class="chat-scroll"
          role="log"
          :aria-live="messages.length || loading ? 'polite' : 'off'"
          aria-label="对话消息"
          :aria-busy="sending"
          @scroll.passive="onScroll"
        >
          <div v-if="loading" class="chat-loading">
            <ZkSkeleton variant="chat" :rows="4" />
            <span class="sr-only">正在加载对话…</span>
          </div>

          <template v-else>
            <ZkEmpty
              v-if="showEmpty"
              class="chat-empty"
              title="还没有对话"
              hint="发送第一条消息，智能助手会先给出答案；复杂问题可以一键转人工。"
            >
              <el-button v-if="isClosed" type="primary" :icon="ChatDotRound" @click="startNewSession">
                开始新会话
              </el-button>
              <el-button v-else type="primary" :icon="Promotion" @click="focusInput">开始提问</el-button>
            </ZkEmpty>

            <!-- 用户气泡从右下、机器人与坐席从左下长出来；首屏旧消息不播入场 -->
            <TransitionGroup :name="msgMotion ? 'zk-msg' : 'zk-quiet'" tag="div" class="msg-list">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="msg"
              :class="[msg.role, msg.role === 'user' ? 'is-user' : msg.role === 'system' ? '' : 'is-peer']"
            >
              <div
                v-if="msg.role !== 'user' && msg.role !== 'system'"
                class="msg-avatar"
                :class="{ 'msg-avatar--agent': msg.role === 'agent' }"
                aria-hidden="true"
              >
                {{ msg.role === 'agent' ? 'CS' : 'AI' }}
              </div>

              <div v-if="msg.role === 'system'" class="system-line">
                <span>{{ msg.content }}</span>
              </div>

              <div v-else class="msg-body">
                <div v-if="msg.role === 'agent' && msg.senderName" class="msg-sender">
                  {{ msg.senderName }}
                </div>
                <div
                  class="bubble"
                  :class="{
                    'bubble--ai': msg.role === 'ai',
                    'bubble--agent': msg.role === 'agent',
                    'bubble--error': msg.error
                  }"
                >
                  <span class="bubble-text">{{ msg.content }}</span>
                  <span v-if="msg.matchedKbId" class="fallback-tag kb-tag">
                    {{ msg.aiModel === 'fallback-kb' ? '知识库推荐' : '知识库命中' }}
                  </span>
                  <Transition name="zk-fade">
                    <span v-if="msg.pending" class="fallback-tag sending-tag">发送中</span>
                  </Transition>
                </div>
                <div class="msg-meta">
                  <button
                    v-if="msg.error"
                    type="button"
                    class="retry-chip zk-press"
                    @click="retryMessage(msg)"
                  >
                    点击重试
                  </button>
                  <span class="msg-time">{{ msg.time }}</span>
                  <span v-if="modelLabel(msg.aiModel)" class="meta-chip">{{ modelLabel(msg.aiModel) }}</span>
                  <span v-if="msg.aiConfidence" class="meta-chip">
                    置信度 {{ confidenceText(msg.aiConfidence) }}
                  </span>
                </div>
              </div>

              <div
                v-if="msg.role === 'user'"
                class="msg-avatar msg-avatar--user"
                aria-hidden="true"
              >
                {{ (user?.nickname || '我').slice(0, 1) }}
              </div>
            </div>
            </TransitionGroup>

            <div v-if="isWaiting" class="queue-banner zk-rise">
              <div class="queue-main">
                <b>正在排队等待人工坐席</b>
                <span v-if="queue && queue.queuePosition">
                  当前第 {{ queue.queuePosition }} 位 · 预计等待 {{ formatWait(queue.estimatedWaitSeconds) }}
                </span>
                <span v-else>空闲坐席接入后会立即通知你</span>
              </div>
              <div class="queue-side">可继续留言，坐席接入后能看到完整记录</div>
            </div>

            <div v-if="isClosed" class="closed-banner zk-rise">
              <div class="queue-main">
                <b>本次会话已结束</b>
                <span>{{ closedSummary }}</span>
              </div>
              <el-button v-if="!session?.rating" size="small" type="primary" @click="openRating">
                评价本次服务
              </el-button>
              <span v-else class="rated-inline">已评价 {{ session.rating }} 星</span>
            </div>

            <!-- 思考态：气泡先升起，三点呼吸表示「正在生成」（motions 18） -->
            <div
              v-if="sending || peerTyping"
              class="msg ai zk-rise"
            >
              <div class="msg-avatar" aria-hidden="true">{{ isHuman ? 'CS' : 'AI' }}</div>
              <div class="msg-body">
                <div class="bubble bubble--ai">
                  <span class="typing" aria-hidden="true"><i /><i /><i /></span>
                  <span class="typing-text">
                    {{ peerTyping && isHuman ? '坐席正在输入…' : '正在思考…' }}
                  </span>
                </div>
              </div>
            </div>
            <div v-if="showMissHint" class="miss-banner zk-rise">
              <div class="miss-main">
                <b>AI 连续 {{ missStreak }} 次没能给出确定答案</b>
                <span>可以转人工坐席继续沟通，或提交工单留下处理记录</span>
              </div>
              <div class="miss-actions">
                <el-button size="small" type="primary" :icon="Service" @click="onTransfer">
                  转人工
                </el-button>
                <el-button size="small" :icon="Tickets" @click="onCreateTicket">提交工单</el-button>
              </div>
            </div>
          </template>
        </div>

        <!-- 新内容提示：从底部 12px 上滑回弹 300ms，未读数翻牌 -->
        <Transition name="zk-jump">
          <button
            v-if="showJump"
            type="button"
            class="jump-btn zk-press"
            :aria-label="jumpLabel"
            @click="jumpToLatest"
          >
            <el-icon aria-hidden="true"><ArrowDown /></el-icon>
            最新消息
            <span v-if="pendingCount" class="jump-count" aria-hidden="true">
              <Transition name="zk-flip">
                <b :key="pendingCount" class="jump-num">{{ pendingCount > 99 ? '99+' : pendingCount }}</b>
              </Transition>
            </span>
          </button>
        </Transition>

        <div class="chat-input">
          <template v-if="isClosed">
            <div class="input-locked">
              <span>本次会话已结束，历史记录仍可查看</span>
              <el-button size="small" type="primary" :icon="ChatDotRound" @click="startNewSession">
                开始新会话
              </el-button>
            </div>
          </template>
          <template v-else>
            <div v-if="!isHuman && !suggestions.length" class="quick-row">
              <span class="quick-label">快捷问题</span>
              <button
                v-for="(question, index) in quickQuestions"
                :key="question"
                type="button"
                class="quick-chip zk-press"
                :style="{ '--chip-i': index }"
                :disabled="sending"
                @click="send(question)"
              >
                {{ question }}
              </button>
            </div>
            <div v-if="suggestions.length" class="suggest-row" role="listbox" aria-label="可能想问">
              <span class="quick-label">猜你想问</span>
              <button
                v-for="(item, index) in suggestions"
                :key="item.kbId"
                type="button"
                class="suggest-chip zk-press"
                :class="{ 'is-out': chipLeaving === item.kbId }"
                :style="{ '--chip-i': index }"
                :disabled="sending"
                @click="applySuggestion(item)"
              >
                {{ item.question }}
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
                :disabled="sending"
                :placeholder="isWaiting ? '可先留言，坐席接入后会看到完整记录' : '输入问题后按 Enter 发送，Shift + Enter 换行'"
                class="chat-textarea"
                @keydown.enter.exact.prevent="send()"
              />
              <div class="input-actions">
                <el-button
                  v-if="canTransfer"
                  :icon="Service"
                  :loading="transferring"
                  class="transfer-inline"
                  @click="onTransfer"
                >
                  {{ isWaiting ? '排队中' : '转人工' }}
                </el-button>
                <!-- 发送三态：纸飞机 → 转圈（0.8s）→ 短对勾（500ms），宽度不变 -->
                <el-button
                  type="primary"
                  class="send-btn act-btn"
                  :class="{ 'is-sending': sending, 'is-sent': justSent }"
                  aria-label="发送消息"
                  :disabled="!input.trim() || sending"
                  @click="send()"
                >
                  <span class="act-icon" aria-hidden="true">
                    <el-icon class="act-glyph"><Promotion /></el-icon>
                    <span class="act-ring" />
                    <svg class="act-check" viewBox="0 0 16 16" fill="none">
                      <path d="M3.6 8.6l2.9 2.9 5.9-6.4" />
                    </svg>
                  </span>
                  <span class="act-label">发送</span>
                </el-button>
              </div>
            </div>
            <div v-if="isWaiting" class="input-tip">
              排队中仍可留言；若长时间无坐席接入，会话将超时自动结束
            </div>
          </template>
        </div>
      </section>

      <aside class="chat-side" v-reveal.group>
        <div class="zk-card side-card">
          <div class="side-title">本次会话</div>
          <div class="status-line">
            <span>会话编号</span>
            <span class="status-value idle">{{ sessionNo }}</span>
          </div>
          <div class="status-line">
            <span>当前状态</span>
            <span class="status-value" :class="[isLive ? 'ok' : 'idle', { 'zk-flash': statusFlash }]">{{ statusText }}</span>
          </div>
          <div class="status-line">
            <span>接待坐席</span>
            <span class="status-value idle">{{ agentName || 'AI 接待' }}</span>
          </div>
          <div v-if="session?.skillGroupName" class="status-line">
            <span>咨询类型</span>
            <span class="status-value idle">{{ session.skillGroupName }}</span>
          </div>
          <div class="status-line">
            <span>消息条数</span>
            <span class="status-value idle">{{ messages.length }}</span>
          </div>
        </div>

        <div class="zk-card side-card">
          <div class="side-title">转人工</div>
          <p class="side-desc">AI 无法解决时转接人工坐席，会话进入排队，坐席接入后自动带出完整上下文。</p>
          <el-select
            v-if="canTransfer && skillGroups.length"
            v-model="transferSkillGroupId"
            clearable
            size="small"
            placeholder="咨询类型（选填，按技能组分配）"
            class="transfer-select"
          >
            <el-option
              v-for="group in skillGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            >
              <span>{{ group.name }}</span>
              <span class="transfer-option-desc">{{ group.description }}</span>
            </el-option>
          </el-select>
          <el-button
            class="transfer-btn"
            :icon="Service"
            :loading="transferring"
            :disabled="!canTransfer"
            @click="onTransfer"
          >
            {{ transferButtonText }}
          </el-button>
          <p class="side-note">{{ transferNote }}</p>
        </div>

        <div class="zk-card side-card">
          <div class="side-title">仍未解决？</div>
          <p class="side-desc">
            提交工单后可在「我的工单」跟踪进度与 SLA；工单会自动关联本次会话，坐席无需你重复描述。
          </p>
          <el-button class="transfer-btn" :icon="Tickets" @click="onCreateTicket">提交工单</el-button>
        </div>

        <div class="zk-card side-card">
          <div class="side-title">连接状态</div>
          <div class="status-line">
            <span>会话接口</span>
            <span class="status-value" :class="isLive ? 'ok' : 'idle'">
              {{ isLive ? '正常' : '未连接' }}
            </span>
          </div>
          <div class="status-line">
            <span>实时通道</span>
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

    <el-dialog v-model="ratingVisible" title="服务评价" width="420px" append-to-body>
      <div class="rating-body">
        <p class="rating-title">请为本次服务打分</p>
        <el-rate
          v-model="ratingForm.rating"
          :max="5"
          show-score
          score-template="{value} 星"
          aria-label="服务满意度评分"
        />
        <el-input
          v-model="ratingForm.comment"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          resize="none"
          placeholder="补充说明（选填）"
          class="rating-input"
        />
      </div>
      <template #footer>
        <el-button @click="ratingVisible = false">稍后再说</el-button>
        <!-- 提交三连变：文字 → 转圈 → 对勾，再关闭弹窗 -->
        <el-button
          type="primary"
          class="act-btn"
          :class="{ 'is-sending': ratingPhase === 'sending', 'is-sent': ratingPhase === 'sent' }"
          :disabled="!ratingForm.rating || ratingSubmitting"
          @click="submitRating"
        >
          <span class="act-icon" aria-hidden="true">
            <el-icon class="act-glyph"><Promotion /></el-icon>
            <span class="act-ring" />
            <svg class="act-check" viewBox="0 0 16 16" fill="none">
              <path d="M3.6 8.6l2.9 2.9 5.9-6.4" />
            </svg>
          </span>
          <span class="act-label">提交评价</span>
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ChatDotRound, Promotion, Service, SwitchButton, Tickets } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import { prefersReducedMotion } from '@/composables/motion'
import {
  closeChatSession,
  createChatSession,
  getChatSkillGroups,
  getChatSuggestions,
  getCurrentSession,
  getSessionMessages,
  rateChatSession,
  sendSessionMessage,
  transferSession
} from '@/api'
import { getToken, getUser } from '@/api/http'
import {
  CLOSE_REASON_DICT,
  SESSION_STATUS_DICT,
  formatClock,
  formatWait,
  nowClock
} from '@/utils/dict'

const user = getUser()
const router = useRouter()

/** 连续未命中次数：由后端 Redis 统一计数并随发送响应下发，前端禁止自行累加（AGENTS.md 5.1.4） */
const missStreak = ref(0)
/** 后端判定连续未命中已达建议转人工阈值（zhiketong.chat.fallback-transfer-threshold） */
const suggestTransfer = ref(false)

const scrollRef = ref(null)
const inputRef = ref(null)
const input = ref('')
const sending = ref(false)
const loading = ref(true)
const transferring = ref(false)
const messages = ref([])
const session = ref(null)
const queue = ref(null)
const showJump = ref(false)
const peerTyping = ref(false)
const ratingVisible = ref(false)
const ratingSubmitting = ref(false)
const ratingForm = reactive({ rating: 5, comment: '' })
/** 转人工可选咨询类型（服务端返回启用中的技能组） */
const skillGroups = ref([])
const transferSkillGroupId = ref(null)

/* ---- 动效状态：只影响观感，不参与业务判定 ---- */
/** 首屏批量消息不播入场，加载完成后一帧再开启 */
const msgMotion = ref(false)
/** 发送成功后的短对勾（500ms） */
const justSent = ref(false)
/** 评分提交三连变：idle / sending / sent */
const ratingPhase = ref('idle')
/** 不在底部时到达的新消息数，用于提示条翻牌 */
const pendingCount = ref(0)
const followBottom = ref(true)
/** 会话状态变化时闪一下 */
const statusFlash = ref(false)
/** 正在做 150ms 退场的那条联想词 */
const chipLeaving = ref(null)

let messageSeq = 0
let typingTimer = null
let pollTimer = null
let suggestTimer = null
let suggestSeq = 0
let followRaf = 0
let lastFollowTop = -1
let jumpUntil = 0
let sentTimer = null
let statusTimer = null
let chipTimer = null
/** 距底小于该值才跟随滚动（motions 18） */
const NEAR_BOTTOM = 24

const nextId = () => {
  messageSeq += 1
  return `local-${Date.now()}-${messageSeq}`
}

const quickQuestions = ['怎么退款', '忘记密码怎么办', '如何开发票', '发票怎么开']

/** 访客输入联想（R-111）：只返回知识库问法与可信度，不返回答案内容 */
const suggestions = ref([])

const isClosed = computed(() => session.value?.status === 'CLOSED')
const isWaiting = computed(() => session.value?.status === 'WAITING')
const isHuman = computed(() => session.value?.status === 'HUMAN')
const isLive = computed(() => Boolean(session.value?.id) && !isClosed.value)
const canTransfer = computed(() => isLive.value && !isHuman.value && !isWaiting.value)
const statusText = computed(() => SESSION_STATUS_DICT[session.value?.status]?.label || '未建立会话')
const sessionNo = computed(() => session.value?.sessionNo || '未建立会话')
const agentName = computed(() => session.value?.agentName || '')
const headName = computed(() => {
  if (isHuman.value) return `${agentName.value || '人工坐席'} 正在为你服务`
  if (isWaiting.value) return '正在为你寻找人工坐席'
  if (isClosed.value) return '本次会话已结束'
  return '智客通智能助手'
})
const headSub = computed(() => {
  if (isHuman.value) return `会话 ${sessionNo.value} · 人工接待 · 坐席已同步历史对话`
  if (isWaiting.value) return `会话 ${sessionNo.value} · 排队中 · 可继续留言`
  if (isClosed.value) return `会话 ${sessionNo.value} · 历史记录可回看`
  return `会话 ${sessionNo.value} · 知识库优先 · 未命中转大模型`
})
const closedSummary = computed(() => {
  const reason = CLOSE_REASON_DICT[session.value?.closeReason] || '会话已结束'
  return session.value?.endTime ? `${reason} · ${formatClock(session.value.endTime)}` : reason
})
const transferButtonText = computed(() => {
  if (isHuman.value) return '已由人工接待'
  if (isWaiting.value) return queue.value?.queuePosition ? `排队第 ${queue.value.queuePosition} 位` : '排队中'
  if (isClosed.value) return '会话已结束'
  return '申请转人工'
})
const transferNote = computed(() => {
  if (isHuman.value) return '坐席已接入，直接发送消息即可'
  if (isWaiting.value) return '请保持页面开启，接入后会实时通知'
  if (isClosed.value) return '如需继续咨询，请开始新会话'
  return '转人工会保留当前对话记录，无需重复描述'
})
/** 没有任何消息时给出「从这开始」的入口，而不是一片空白 */
const showEmpty = computed(() => !loading.value && messages.value.length === 0)
const jumpLabel = computed(() =>
  pendingCount.value ? `跳到最新消息，${pendingCount.value} 条未读` : '跳到最新消息'
)

/* ---------------- 会话与消息 ---------------- */
function readErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function mapServerMessage(raw) {
  if (!raw) return null
  const sender = raw.senderType || 'AI'
  const role = sender === 'USER' ? 'user' : sender === 'AGENT' ? 'agent' : sender === 'SYSTEM' ? 'system' : 'ai'
  return {
    id: raw.id ? `srv-${raw.id}` : nextId(),
    serverId: raw.id || null,
    role,
    senderName: raw.senderName || '',
    content: raw.content || '',
    time: formatClock(raw.createTime) || nowClock(),
    matchedKbId: raw.matchedKbId || null,
    aiConfidence: raw.aiConfidence,
    aiModel: raw.aiModel || '',
    createdAt: raw.createTime || ''
  }
}

function appendServerMessage(raw) {
  const mapped = mapServerMessage(raw)
  if (!mapped) return
  if (mapped.serverId && messages.value.some((item) => item.serverId === mapped.serverId)) return
  messages.value.push(mapped)
  // 用户没在看底部时只记未读数（提示条翻牌），不强拉视图
  if (followBottom.value || loading.value) scrollToBottom()
  else pendingCount.value += 1
}

function confidenceText(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return `${Math.round(n * 100)}%`
}

/** 展示给用户的应答来源，内部标记不直接暴露 */
function modelLabel(value) {
  if (!value || value === 'SYSTEM' || value === 'kb-direct' || value === 'fallback-kb' || value === 'fallback-template') {
    return ''
  }
  return String(value).startsWith('deepseek') ? 'DeepSeek 大模型' : value
}

async function loadMessages(sessionId) {
  try {
    const page = await getSessionMessages(sessionId, { pageNum: 1, pageSize: 100, markRead: true })
    const list = page?.list || []
    messages.value = list.map(mapServerMessage).filter(Boolean)
  } catch {
    messages.value = []
  }
}

async function createNewSession(silent) {
  const data = await createChatSession()
  session.value = data?.session || null
  messages.value = []
  queue.value = null
  missStreak.value = 0
  suggestTransfer.value = false
  if (data?.welcomeMessage) appendServerMessage(data.welcomeMessage)
  if (!silent) ElMessage.success('已开始新会话')
}

async function loadSession() {
  loading.value = true
  try {
    const current = await getCurrentSession().catch(() => null)
    if (current?.id) {
      session.value = current
      syncQueue(current)
      await loadMessages(current.id)
    } else {
      await createNewSession(true)
    }
  } catch (error) {
    ElMessage.error(readErrorMessage(error, '加载会话失败，请刷新重试'))
  } finally {
    loading.value = false
    snapToBottom()
    // 首屏这批历史消息已经在 DOM 里，之后再开启入场动画
    nextTick(() => {
      msgMotion.value = true
    })
  }
}

async function startNewSession() {
  if (loading.value) return
  const current = await getCurrentSession().catch(() => null)
  if (current?.id) {
    session.value = current
    syncQueue(current)
    // 整批换回历史消息不逐条播入场，避免一屏消息同时弹
    msgMotion.value = false
    await loadMessages(current.id)
    nextTick(() => {
      msgMotion.value = true
    })
    ElMessage.info('已回到进行中的会话')
    return
  }
  loading.value = true
  try {
    await createNewSession(false)
  } catch (error) {
    ElMessage.error(readErrorMessage(error, '创建会话失败，请稍后重试'))
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function syncQueue(current) {
  if (current?.status === 'WAITING') {
    queue.value = {
      queuePosition: null,
      queueSize: null,
      estimatedWaitSeconds: null,
      queueEnterTime: current.queueEnterTime || null
    }
  } else {
    queue.value = null
  }
}

function gapToBottom(el) {
  return el.scrollHeight - el.scrollTop - el.clientHeight
}

/** 跟随滚动：每帧只追剩余距离的 18%，不直接 scrollTop = scrollHeight（motions 18） */
function followStep() {
  followRaf = 0
  const el = scrollRef.value
  if (!el) return
  // 追随过程中用户往上滑：立刻让位，改为提示条
  if (lastFollowTop >= 0 && el.scrollTop < lastFollowTop - 2) {
    lastFollowTop = -1
    followBottom.value = false
    showJump.value = true
    return
  }
  const gap = gapToBottom(el)
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

/** 有新内容时平滑追到底部（用户正在看底部才跟随） */
function scrollToBottom() {
  followBottom.value = true
  showJump.value = false
  pendingCount.value = 0
  nextTick(() => {
    if (followRaf) return
    followRaf = requestAnimationFrame(followStep)
  })
}

/** 首屏定位：进入页面时直接落到底部，不播滚动动画 */
function snapToBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
    followBottom.value = true
    showJump.value = false
    pendingCount.value = 0
  })
}

/** 点提示条：平滑滚到底，减少动效时直接到位 */
function jumpToLatest() {
  const el = scrollRef.value
  if (!el) return
  pendingCount.value = 0
  showJump.value = false
  followBottom.value = true
  lastFollowTop = -1
  jumpUntil = Date.now() + 600
  el.scrollTo({
    top: el.scrollHeight,
    behavior: prefersReducedMotion() ? 'auto' : 'smooth'
  })
}

function onScroll() {
  const el = scrollRef.value
  if (!el) return
  // 程序内滚动（跟随/平滑跳转）期间不判定「用户上滑」，交给 followStep 处理
  if (followRaf || Date.now() < jumpUntil) return
  const near = gapToBottom(el) < NEAR_BOTTOM
  followBottom.value = near
  showJump.value = !near
  if (near) pendingCount.value = 0
}

/* ---------------- 发送消息 ---------------- */
function newClientMsgId() {
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

async function send(text) {
  const content = (typeof text === 'string' ? text : input.value).trim()
  if (!content || sending.value || isClosed.value) return
  if (!session.value?.id) {
    ElMessage.warning('会话尚未建立，请稍后重试')
    return
  }

  suggestions.value = []
  input.value = ''
  const localId = nextId()
  messages.value.push({
    id: localId,
    role: 'user',
    content,
    time: nowClock(),
    pending: true
  })
  sending.value = true
  justSent.value = false
  scrollToBottom()

  try {
    const data = await sendSessionMessage(session.value.id, {
      content,
      contentType: 'TEXT',
      clientMsgId: newClientMsgId()
    })
    const target = messages.value.find((item) => item.id === localId)
    if (target) {
      // 只补 serverId，不改本地 key：改了 key 会让已发出的气泡重新播一次入场
      target.pending = false
      target.serverId = data?.message?.id || null
    }
    // 成功后按钮给 500ms 短对勾，再回到常态
    justSent.value = true
    if (sentTimer) window.clearTimeout(sentTimer)
    sentTimer = window.setTimeout(() => {
      justSent.value = false
    }, 500)
    if (data?.aiReply) appendServerMessage(data.aiReply)
    if (typeof data?.fallbackCount === 'number') {
      missStreak.value = data.fallbackCount
    }
    suggestTransfer.value = Boolean(data?.suggestTransfer)
    if (data?.sessionStatus) {
      session.value = { ...session.value, status: data.sessionStatus }
      if (data.sessionStatus !== 'WAITING') queue.value = null
    }
  } catch {
    // 失败只在气泡上给「点击重试」，不弹窗（motions 44）
    const target = messages.value.find((item) => item.id === localId)
    if (target) {
      target.pending = false
      target.error = true
    }
  } finally {
    sending.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}

/** 失败气泡点击重试：移除失败气泡后按原文本重发，不影响用户正在输入的草稿 */
function retryMessage(msg) {
  if (sending.value || isClosed.value || !msg) return
  if (!session.value?.id) return
  const draft = input.value
  messages.value = messages.value.filter((item) => item.id !== msg.id)
  send(msg.content)
  if (draft) input.value = draft
}

async function onTransfer() {
  if (!session.value?.id) return
  if (isWaiting.value) {
    ElMessage.info(queue.value?.queuePosition ? `当前排队第 ${queue.value.queuePosition} 位` : '正在排队，请稍候')
    return
  }
  if (isHuman.value) {
    ElMessage.info('已有坐席接入')
    return
  }
  if (isClosed.value) {
    ElMessage.info('会话已结束，请开始新会话后再转人工')
    return
  }
  transferring.value = true
  try {
    const data = await transferSession(
      session.value.id,
      '用户主动申请转人工',
      transferSkillGroupId.value || null
    )
    missStreak.value = 0
    suggestTransfer.value = false
    session.value = { ...session.value, status: data?.status || 'WAITING' }
    if (data?.status === 'WAITING') {
      queue.value = {
        queuePosition: data.queuePosition || null,
        queueSize: data.queueSize || null,
        estimatedWaitSeconds: data.estimatedWaitSeconds || null
      }
      ElMessage.success('已进入人工排队，请稍候')
    } else if (data?.status === 'HUMAN') {
      queue.value = null
      ElMessage.success('已接入人工坐席')
    }
  } catch (error) {
    ElMessage.error(readErrorMessage(error, '转人工失败，请稍后重试'))
  } finally {
    transferring.value = false
  }
}

/* ---------------- 访客输入联想（R-111） ---------------- */
function scheduleSuggestions(value) {
  if (suggestTimer) window.clearTimeout(suggestTimer)
  const keyword = typeof value === 'string' ? value.trim() : ''
  // 少于 2 个字、非接待中或正在发送时不请求，避免无效查询与联想闪动
  if (keyword.length < 2 || isClosed.value || isHuman.value || sending.value || !session.value?.id) {
    suggestions.value = []
    return
  }
  suggestTimer = window.setTimeout(async () => {
    const current = ++suggestSeq
    try {
      const data = await getChatSuggestions({ query: keyword, limit: 5 })
      // 响应乱序或输入已变化时丢弃旧结果
      if (current !== suggestSeq || input.value.trim() !== keyword) return
      suggestions.value = data?.items || []
    } catch {
      if (current === suggestSeq) suggestions.value = []
    }
  }, 300)
}

function applySuggestion(item) {
  if (chipLeaving.value !== null) return
  // 按下后先让这条联想词 150ms 缩小淡出，再把问法回填输入框（motions 74）
  chipLeaving.value = item?.kbId ?? null
  if (chipTimer) window.clearTimeout(chipTimer)
  chipTimer = window.setTimeout(() => {
    chipLeaving.value = null
    input.value = item?.question || ''
    suggestions.value = []
    nextTick(() => inputRef.value?.focus())
  }, 150)
}

/** 空状态下把焦点交给输入框，用户可以直接开问 */
function focusInput() {
  nextTick(() => inputRef.value?.focus())
}

// 会话状态变化时闪一下，让状态切换被看见（首屏建立会话不闪）
watch(
  () => session.value?.status,
  (next, prev) => {
    if (!next || !prev || next === prev) return
    statusFlash.value = true
    if (statusTimer) window.clearTimeout(statusTimer)
    statusTimer = window.setTimeout(() => {
      statusFlash.value = false
    }, 720)
  }
)

watch(input, (value) => scheduleSuggestions(value))

/* ---------------- 工单升级：AI 无法解决时留痕跟进 ---------------- */
const showMissHint = computed(
  () => suggestTransfer.value && missStreak.value > 0 && !isHuman.value && !isWaiting.value && !isClosed.value
)

function onCreateTicket() {
  const lastUser = [...messages.value]
    .reverse()
    .find((item) => item.role === 'user' && !item.pending)
  const raw = (lastUser?.content || '').trim()
  const title = raw.length >= 4 ? raw.slice(0, 60) : '在线咨询问题跟进'
  const content =
    raw.length >= 10 ? raw : `${raw || '用户在在线咨询中提出问题'}，需要进一步跟进处理。`
  router.push({
    path: '/user/orders',
    query: {
      ...(session.value?.id ? { sessionId: String(session.value.id) } : {}),
      title,
      content
    }
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

  socket.onmessage = (event) => {
    handleWsMessage(event)
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

/* ---------------- WebSocket 业务消息 ---------------- */
function handleWsMessage(event) {
  let payload
  try {
    payload = JSON.parse(event.data)
  } catch {
    return
  }
  if (!payload || payload.type === 'PONG') return
  const data = payload.data || {}
  if (data.sessionId && session.value?.id && String(data.sessionId) !== String(session.value.id)) return

  switch (payload.type) {
    case 'AI_MESSAGE':
    case 'AGENT_MESSAGE':
      if (data.id && data.senderType) appendServerMessage(data)
      break
    case 'TRANSFER_QUEUED':
    case 'QUEUE_STATUS':
      session.value = session.value ? { ...session.value, status: 'WAITING' } : session.value
      queue.value = {
        queuePosition: data.queuePosition || null,
        queueSize: data.queueSize || null,
        estimatedWaitSeconds: data.estimatedWaitSeconds || null
      }
      break
    case 'TRANSFER_ACCEPTED':
      session.value = {
        ...(session.value || {}),
        status: 'HUMAN',
        agentId: data.agent?.id || session.value?.agentId,
        agentName: data.agent?.nickname || session.value?.agentName || '人工坐席',
        acceptTime: data.acceptTime || session.value?.acceptTime
      }
      queue.value = null
      if (data.welcomeMessage) {
        messages.value.push({
          id: nextId(),
          role: 'agent',
          senderName: data.agent?.nickname || '人工坐席',
          content: data.welcomeMessage,
          time: nowClock()
        })
      }
      ElMessage.success('人工坐席已接入')
      scrollToBottom()
      break
    case 'SESSION_CLOSED':
      session.value = session.value
        ? { ...session.value, status: 'CLOSED', closeReason: data.closeReason, endTime: data.endTime }
        : session.value
      queue.value = null
      ElMessage.info(data.summary || '会话已结束')
      scrollToBottom()
      break
    case 'SYSTEM_NOTICE':
      if (data.content) {
        ElMessage({
          message: data.content,
          type: data.level === 'WARN' ? 'warning' : 'info',
          duration: 3000
        })
      }
      break
    case 'TYPING':
      if (data.senderType && data.senderType !== 'USER') {
        peerTyping.value = Boolean(data.typing)
        if (typingTimer) window.clearTimeout(typingTimer)
        if (peerTyping.value) {
          typingTimer = window.setTimeout(() => {
            peerTyping.value = false
          }, 3000)
        }
      }
      break
    default:
      break
  }
}

/* ---------------- 断线补偿：WS 不可用时轮询增量 ---------------- */
function startPolling() {
  if (pollTimer) return
  pollTimer = window.setInterval(async () => {
    if (wsState.value === 'open' || !session.value?.id || isClosed.value) return
    try {
      const lastId = [...messages.value].reverse().find((item) => item.serverId)?.serverId || 0
      const page = await getSessionMessages(session.value.id, {
        pageNum: 1,
        pageSize: 50,
        afterId: lastId
      })
      for (const item of page?.list || []) appendServerMessage(item)
    } catch {
      /* 轮询失败下一轮静默重试 */
    }
  }, 15000)
}

/* ---------------- 结束与评价 ---------------- */
function openRating() {
  ratingForm.rating = session.value?.rating || 5
  ratingForm.comment = session.value?.ratingComment || ''
  ratingVisible.value = true
}

async function submitRating() {
  if (!session.value?.id || !ratingForm.rating || ratingSubmitting.value) return
  ratingSubmitting.value = true
  ratingPhase.value = 'sending'
  try {
    const updated = await rateChatSession(session.value.id, {
      rating: ratingForm.rating,
      ratingComment: ratingForm.comment.trim() || null
    })
    session.value = updated?.status
      ? updated
      : { ...session.value, rating: ratingForm.rating, ratingComment: ratingForm.comment }
    // 三连变的最后一帧：对勾停 500ms 再关闭弹窗
    ratingPhase.value = 'sent'
    await new Promise((resolve) => window.setTimeout(resolve, 500))
    ratingVisible.value = false
    ElMessage.success('感谢你的评价')
  } catch (error) {
    ElMessage.error(readErrorMessage(error, '评价提交失败，请稍后重试'))
  } finally {
    ratingSubmitting.value = false
    ratingPhase.value = 'idle'
  }
}

async function onCloseSession() {
  if (!session.value?.id || isClosed.value) return
  // 排队中允许取消排队，避免用户既不能结束也不能新建会话
  const waiting = isWaiting.value
  try {
    await ElMessageBox.confirm(
      waiting
        ? '取消排队后本次会话将结束，你可以稍后重新发起咨询，是否继续？'
        : '结束后将无法继续发送消息，是否确认结束本次会话？',
      waiting ? '取消排队' : '结束会话',
      {
        confirmButtonText: waiting ? '取消排队' : '结束会话',
        cancelButtonText: waiting ? '继续等待' : '继续咨询',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  try {
    const updated = await closeChatSession(session.value.id, { closeReason: 'USER_CLOSED' })
    session.value = updated?.status ? updated : { ...session.value, status: 'CLOSED' }
    queue.value = null
    ElMessage.success(waiting ? '已取消排队' : '会话已结束')
    // 未接入的排队会话没有实际服务过程，不弹出评价
    if (!waiting) openRating()
  } catch (error) {
    ElMessage.error(readErrorMessage(error, waiting ? '取消排队失败，请稍后重试' : '结束会话失败，请稍后重试'))
  }
}

onMounted(() => {
  connectWs()
  startPolling()
  loadSession()
  loadSkillGroups()
})

async function loadSkillGroups() {
  try {
    skillGroups.value = (await getChatSkillGroups()) || []
  } catch {
    // 咨询类型加载失败不阻断转人工，未选择时按不限技能组分配
    skillGroups.value = []
  }
}

onBeforeUnmount(() => {
  stopTimers()
  if (typingTimer) window.clearTimeout(typingTimer)
  if (pollTimer) window.clearInterval(pollTimer)
  if (suggestTimer) window.clearTimeout(suggestTimer)
  if (sentTimer) window.clearTimeout(sentTimer)
  if (statusTimer) window.clearTimeout(statusTimer)
  if (chipTimer) window.clearTimeout(chipTimer)
  if (followRaf) cancelAnimationFrame(followRaf)
  followRaf = 0
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
  border-radius: var(--zk-radius-sm);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
}

.ws-chip.teal {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
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
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
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
  background: #fff;
  flex: 0 0 auto;
}

.bot-avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: var(--zk-radius);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--zk-accent);
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
  color: var(--zk-text-2);
  background: var(--zk-surface-3);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  padding: 3px 9px;
}

.api-chip {
  font-size: 11px;
  color: #64748b;
  background: #f4f6fa;
  border: 1px solid #e2e8f0;
  border-radius: var(--zk-radius-sm);
  padding: 3px 9px;
}

.api-chip.ok {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

/* 消息区 */
.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 22px 20px 12px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* TransitionGroup 容器：保持消息纵向排布与原有间距 */
.msg-list {
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
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: var(--zk-accent);
}

.msg-avatar--user {
  background: #17181c;
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
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--zk-text);
  background: #ffffff;
  border: 1px solid var(--zk-border);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble--ai {
  background: #fff;
  border-top-left-radius: 2px;
}

.msg.user .bubble {
  background: var(--zk-accent);
  border-color: var(--zk-accent);
  color: #ffffff;
  border-top-right-radius: 2px;
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
  border-radius: var(--zk-radius-sm);
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
  border-radius: var(--zk-radius-sm);
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
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zk-accent);
  /* 思考点：1.2s 呼吸，缩放 0.75→1 并上浮 4px（motions 18） */
  animation: zk-typing-dot 1.2s var(--zk-ease-in-out) infinite;
}

.typing i:nth-child(2) {
  animation-delay: 0.15s;
}
.typing i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes zk-typing-dot {
  0%,
  100% {
    transform: translateY(0) scale(0.75);
    opacity: 0.45;
  }
  30% {
    transform: translateY(-4px) scale(1);
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
  color: var(--zk-text-2);
  background: #fff;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  padding: 4px 11px;
  cursor: pointer;
  font-family: inherit;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    border-color var(--zk-dur-fast) var(--zk-ease-out), color var(--zk-dur-fast) var(--zk-ease-out);
}

.quick-chip:hover:not(:disabled) {
  background: var(--zk-accent-soft);
  border-color: var(--zk-accent-line);
  color: var(--zk-accent-hover);
}

.quick-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 访客输入联想：只展示知识库问法，点击后回填输入框由访客确认发送 */
.suggest-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.suggest-chip {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #4338ca;
  background: #f6f7ff;
  border: 1px solid #c7d2fe;
  border-radius: var(--zk-radius-sm);
  padding: 4px 11px;
  cursor: pointer;
  font-family: inherit;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    border-color var(--zk-dur-fast) var(--zk-ease-out);
}

.suggest-chip:hover:not(:disabled) {
  background: #eef0ff;
  border-color: #a5b4fc;
}

.suggest-chip:disabled {
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
  border-radius: var(--zk-radius-xs);
  padding: 10px 12px;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  resize: none;
}

.send-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: var(--zk-radius-sm);
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
  width: 2px;
  height: 12px;
  border-radius: 1px;
  background: var(--zk-accent);
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
  background: var(--zk-accent);
}
.cap-dot.indigo {
  background: #10b981;
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

.transfer-select {
  width: 100%;
  margin-bottom: 8px;
}

.transfer-option-desc {
  float: right;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--zk-text-3);
}

.transfer-btn {
  width: 100%;
  margin-top: 12px;
  height: 38px;
  border-radius: var(--zk-radius-sm);
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
  border-radius: var(--zk-radius-sm);
}

.status-value.ok {
  color: #15803d;
  background: #f0fdf4;
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
  border-radius: var(--zk-radius-sm);
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

/* ---------- 真实会话闭环扩展 ---------- */
.bot-avatar--human {
  background: #17181c;
}

.msg-avatar--agent {
  background: #17181c;
}

.msg-sender {
  font-size: 11px;
  color: var(--zk-text-3);
  padding-left: 2px;
}

.bubble--agent {
  background: #fff;
  border-color: var(--zk-border);
  border-top-left-radius: 2px;
}

.msg.system {
  align-self: center;
  max-width: 92%;
  justify-content: center;
}

.system-line {
  font-size: 11.5px;
  color: var(--zk-text-3);
  background: #f4f6fa;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  padding: 4px 14px;
  text-align: center;
}

.chat-loading {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;
}

.sk-bar.short {
  width: 62%;
}

.queue-banner,
.closed-banner {
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--zk-radius-lg);
  font-size: 12px;
}

.queue-banner {
  background: var(--zk-amber-soft);
  border: 1px solid var(--zk-amber-line);
  color: #92400e;
}

.closed-banner {
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  color: var(--zk-text-2);
}

.queue-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.queue-main b {
  font-size: 12.5px;
}

.queue-side {
  font-size: 11px;
  color: #b45309;
  white-space: nowrap;
}

.rated-inline {
  font-size: 11.5px;
  color: #15803d;
  font-weight: 600;
  white-space: nowrap;
}

.kb-tag {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.sending-tag {
  color: #64748b;
  background: #f4f6fa;
  border-color: #e2e8f0;
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.transfer-inline {
  height: 44px;
  border-radius: var(--zk-radius-sm);
}

.input-tip {
  margin-top: 8px;
  font-size: 11px;
  color: #b45309;
}

.input-locked {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px dashed #d9dfea;
  border-radius: var(--zk-radius-lg);
  background: #f8fafc;
  font-size: 12.5px;
  color: var(--zk-text-3);
}

.rating-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rating-title {
  margin: 0;
  font-size: 13px;
  color: var(--zk-text-2);
}

.rating-input {
  margin-top: 2px;
}

/* 连续未命中提示：琥珀色提醒，不阻断继续输入 */
.miss-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 4px;
  padding: 10px 14px;
  border: 1px solid #fde3b4;
  border-left: 3px solid var(--zk-amber-edge);
  border-radius: var(--zk-radius-lg);
  background: #fffdf6;
}

.miss-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.miss-main b {
  font-size: 12.5px;
  color: #92400e;
}

.miss-main span {
  font-size: 11.5px;
  color: #b45309;
}

.miss-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

@media (max-width: 640px) {
  .queue-banner,
  .closed-banner,
  .miss-banner {
    flex-direction: column;
    align-items: flex-start;
  }

  .input-actions {
    width: 100%;
  }

  .input-actions .el-button {
    flex: 1;
  }
}

/* ---------- 动效层：发送三态、失败重试、提示条翻牌、联想词 ---------- */
/* 消息方向由 motion.css 的 .zk-msg-enter-from.is-user / .is-peer 决定，页面不再自造关键帧 */

/* 发送失败：气泡旁给「点击重试」，重发原文本，不弹窗 */
.retry-chip {
  border: 1px solid #f3bccb;
  background: #fff1f4;
  color: #9f1239;
  border-radius: var(--zk-radius-sm);
  padding: 1px 7px;
  font-size: 10.5px;
  font-weight: 600;
  font-family: inherit;
  line-height: 1.6;
  cursor: pointer;
}

.retry-chip:hover {
  background: #ffe4ea;
  border-color: #eaa4b7;
}

/* 发送按钮三态：图标区固定 14px，切换时按钮宽度不跳 */
.act-icon {
  position: relative;
  display: inline-block;
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
}

.act-glyph,
.act-ring,
.act-check {
  position: absolute;
  inset: 0;
  transition: opacity 140ms var(--zk-ease-out), transform 200ms var(--zk-ease-spring);
}

.act-glyph {
  display: grid;
  place-items: center;
  font-size: 14px;
}

.act-ring {
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.32);
  border-top-color: #fff;
  opacity: 0;
  transform: scale(0.7);
}

.act-check {
  stroke: #fff;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0;
  transform: scale(0.7);
}

.act-btn.is-sending .act-glyph,
.act-btn.is-sent .act-glyph {
  opacity: 0;
  transform: scale(0.7);
}

.act-btn.is-sending .act-ring {
  opacity: 1;
  transform: scale(1);
  animation: zk-send-spin 0.8s cubic-bezier(0.55, 0, 0.45, 1) infinite;
}

.act-btn.is-sent .act-check {
  opacity: 1;
  transform: scale(1);
}

.act-btn.is-sent .act-check path {
  stroke-dasharray: 24;
  animation: zk-check-draw 320ms var(--zk-ease-out) both;
}

@keyframes zk-send-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 发送中/刚成功时保持主色，避免禁用态把反馈冲淡 */
.act-btn.is-sending.is-disabled,
.act-btn.is-sent.is-disabled {
  background: var(--zk-accent);
  border-color: var(--zk-accent);
  color: #fff;
  opacity: 0.92;
}

/* 未读数字翻牌：旧数字往上走，新数字从下滑入 */
.jump-count {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 16px;
  overflow: hidden;
}

.jump-num {
  display: block;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  color: #fff;
  background: var(--zk-accent);
  border-radius: 8px;
  padding: 0 5px;
}

.zk-flip-enter-active {
  transition: transform 220ms var(--zk-ease-out), opacity 200ms var(--zk-ease-out);
}

.zk-flip-leave-active {
  position: absolute;
  inset: 0;
  transition: transform 200ms var(--zk-ease-in), opacity 180ms var(--zk-ease-in);
}

.zk-flip-enter-from {
  opacity: 0;
  transform: translate3d(0, 100%, 0);
}

.zk-flip-leave-to {
  opacity: 0;
  transform: translate3d(0, -100%, 0);
}

/* 提示条：从底部 12px 上滑回弹 300ms，关闭按原路回去 */
.zk-jump-enter-active {
  transition: opacity 200ms var(--zk-ease-out), transform 300ms var(--zk-ease-spring);
}

.zk-jump-leave-active {
  transition: opacity 140ms var(--zk-ease-in), transform 140ms var(--zk-ease-in);
}

.zk-jump-enter-from,
.zk-jump-leave-to {
  opacity: 0;
  transform: translate3d(0, 12px, 0) scale(0.97);
}

/* 快捷问题 / 联想词：40ms 错开浮现 */
.quick-chip,
.suggest-chip {
  animation: zk-rise-sm 240ms var(--zk-ease-soft) backwards;
  animation-delay: calc(var(--chip-i, 0) * 40ms);
}

/* 点选联想词：150ms 缩小淡出后再回填输入框 */
.suggest-chip.is-out {
  opacity: 0;
  transform: scale(0.94);
  transition: opacity 150ms var(--zk-ease-in), transform 150ms var(--zk-ease-in);
}

/* 无消息时的引导 */
.chat-empty {
  margin: 10px 0;
}

@media (max-width: 640px) {
  .msg-list {
    gap: 14px;
  }
}
</style>
