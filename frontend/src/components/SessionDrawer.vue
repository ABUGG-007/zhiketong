<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    :size="drawerSize"
    append-to-body
    class="session-drawer"
    @closed="onClosed"
  >
    <div v-if="loading" class="sd-loading">
      <ZkSkeleton variant="card" :rows="4" />
    </div>

    <div v-else-if="detail" class="sd zk-drawer-stagger">
      <!-- 会话摘要 -->
      <header class="sd-head">
        <div class="sd-head-main">
          <span class="sd-no">{{ detail.sessionNo || `#${detail.id}` }}</span>
          <StatusPill
            :label="dictLabel(SESSION_STATUS_DICT, detail.status)"
            :tone="dictTone(SESSION_STATUS_DICT, detail.status)"
            dot
          />
        </div>
        <div class="sd-meta">
          <span>用户：{{ detail.userName || '—' }}</span>
          <span>接待：{{ detail.agentName || 'AI 接待' }}</span>
          <span>对话：{{ dialogTotal }} 条</span>
          <span>创建：{{ formatDateTime(detail.createTime) }}</span>
        </div>
        <div v-if="Number(detail.rating) > 0" class="sd-rating">
          <span class="sd-rating-label">满意度</span>
          <span class="sd-stars" aria-hidden="true">
            <span
              v-for="n in 5"
              :key="n"
              :class="{ 'is-empty': n > Number(detail.rating) }"
            >★</span>
          </span>
          <span class="sd-rating-num zk-num">{{ detail.rating }}</span>
          <span v-if="detail.ratingComment" class="sd-rating-comment">
            「{{ detail.ratingComment }}」
          </span>
        </div>
        <p v-else-if="detail.status === 'CLOSED'" class="sd-rating-empty">本次会话访客未评价</p>

        <!-- 管理员视角：会话生命周期 -->
        <ol
          v-if="role === 'ADMIN'"
          class="sd-timeline"
          aria-label="会话生命周期"
          v-reveal.group
          style="--zk-stagger: 40ms"
        >
          <li v-for="step in timeline" :key="step.key" :class="{ done: step.time }">
            <span class="sd-timeline-label">{{ step.label }}</span>
            <span class="sd-timeline-time">{{ step.time ? formatDateTime(step.time) : '未发生' }}</span>
          </li>
        </ol>
      </header>

      <!-- 消息记录 -->
      <div ref="scrollRef" class="sd-messages" role="log" aria-label="会话消息记录" @scroll="onScroll">
        <div v-if="!messages.length" class="sd-empty">
          <p>暂无消息记录</p>
          <span>会话创建后，用户与 AI / 坐席的往来消息会显示在这里</span>
        </div>

        <div
          v-for="msg in messages"
          :key="msg.id"
          class="sd-msg"
          :class="msg.role"
        >
          <div v-if="msg.role === 'system'" class="sd-system">{{ msg.content }}</div>
          <div v-else-if="msg.role === 'note'" class="sd-note">
            <span class="sd-note-tag">内部备注 · 仅坐席可见</span>
            <p class="sd-note-content">{{ msg.content }}</p>
            <span class="sd-note-time">{{ msg.name }} · {{ msg.time }}</span>
          </div>
          <template v-else>
            <div class="sd-msg-avatar" :class="`is-${msg.role}`" aria-hidden="true">
              {{ msg.role === 'user' ? 'U' : msg.role === 'agent' ? 'CS' : 'AI' }}
            </div>
            <div class="sd-msg-body">
              <div class="sd-msg-head">
                <span class="sd-msg-name">{{ msg.name }}</span>
                <span class="sd-msg-time">{{ msg.time }}</span>
                <span v-if="msg.matchedKbId" class="sd-tag">
                  {{ msg.aiModel === 'fallback-kb' ? '知识库推荐' : '知识库命中' }}
                </span>
              </div>
              <div class="sd-bubble" :class="`is-${msg.role}`">{{ msg.content }}</div>
            </div>
          </template>
        </div>
      </div>

      <!-- 坐席辅助：推荐答案 / 进线摘要 / 服务小结（AI 产出仅作草稿） -->
      <AgentAssistPanel
        v-if="assistable"
        :session-id="detail.id"
        :status="detail.status"
        :message-count="messages.length"
        :can-insert="canReply"
        @insert="insertAssist"
      />

      <!-- 坐席操作区 -->
      <footer v-if="canReply" class="sd-input">
        <div class="sd-qr">
          <div class="sd-qr-bar">
            <el-button size="small" text :icon="ChatDotRound" @click="toggleQuickReplies">
              快捷回复
            </el-button>
            <el-select
              v-model="qrCategory"
              size="small"
              placeholder="全部分类"
              clearable
              class="sd-qr-select"
            >
              <el-option v-for="item in qrCategories" :key="item" :label="item" :value="item" />
            </el-select>
            <el-input
              v-model="qrKeyword"
              size="small"
              placeholder="搜索话术"
              clearable
              class="sd-qr-search"
            />
            <el-button size="small" text type="primary" @click="openQrDialog(null)">
              新增个人话术
            </el-button>
            <el-button
              v-if="assistable"
              size="small"
              text
              :icon="EditPen"
              @click="noteOpen = !noteOpen"
            >
              内部备注
            </el-button>
          </div>
          <div v-if="qrOpen" class="sd-qr-panel">
            <p v-if="qrLoading" class="sd-qr-state">话术加载中…</p>
            <p v-else-if="qrError" class="sd-qr-state">
              {{ qrError }}
              <el-button size="small" text type="primary" @click="loadQuickReplies">重试</el-button>
            </p>
            <p v-else-if="!filteredQuickReplies.length" class="sd-qr-state">
              暂无匹配话术，可保存个人话术后复用
            </p>
            <ul v-else class="sd-qr-list" v-reveal.group style="--zk-stagger: 40ms">
              <li v-for="item in filteredQuickReplies" :key="item.id">
                <button type="button" class="sd-qr-item" @click="insertQuickReply(item)">
                  <span class="sd-qr-title">
                    {{ item.title }}
                    <span
                      class="sd-qr-scope"
                      :class="item.scope === 'PUBLIC' ? 'is-public' : 'is-personal'"
                    >{{ item.scope === 'PUBLIC' ? '公共' : '个人' }}</span>
                  </span>
                  <span class="sd-qr-content">{{ item.content }}</span>
                </button>
                <div class="sd-qr-actions">
                  <span class="sd-qr-count zk-num">用 {{ item.useCount }} 次</span>
                  <el-button v-if="item.editable" size="small" text @click="openQrDialog(item)">
                    编辑
                  </el-button>
                  <el-button
                    v-if="item.editable"
                    size="small"
                    text
                    type="danger"
                    @click="removeQuickReply(item)"
                  >
                    删除
                  </el-button>
                </div>
              </li>
            </ul>
          </div>
        </div>
        <div v-if="noteOpen && assistable" class="sd-note-panel">
          <el-input
            v-model="noteDraft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            show-word-limit
            placeholder="内部备注仅坐席可见，不会发送给访客"
            aria-label="内部备注"
          />
          <div class="sd-note-actions">
            <el-button
              size="small"
              type="primary"
              plain
              :loading="noteSaving"
              :disabled="!noteDraft.trim()"
              @click="saveNote"
            >
              保存备注
            </el-button>
          </div>
        </div>
        <div
          v-if="suggestions.length"
          class="sd-suggest"
          role="listbox"
          aria-label="输入联想"
          v-reveal.group
          style="--zk-stagger: 40ms"
        >
          <button
            v-for="item in suggestions"
            :key="item.kbId"
            type="button"
            class="sd-suggest-item"
            @click="applySuggestion(item)"
          >
            <span class="sd-suggest-q">{{ item.question }}</span>
            <span class="sd-suggest-score zk-num">{{ Math.round((item.score || 0) * 100) }}%</span>
          </button>
        </div>
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="回复访客，Enter 发送 / Shift + Enter 换行"
          aria-label="回复访客"
          @keydown.enter.exact.prevent="send"
        />
        <div class="sd-input-actions">
          <el-button
            type="danger"
            plain
            :icon="SwitchButton"
            :loading="closing"
            @click="onCloseSession"
          >
            结束会话
          </el-button>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="sending"
            :disabled="!draft.trim()"
            @click="send"
          >
            发送回复
          </el-button>
        </div>
        <p class="sd-input-tip">回复会通过 WebSocket 实时推送给访客，并写入会话消息记录</p>
      </footer>
      <footer v-else-if="readonlyTip" class="sd-readonly">
        <el-icon aria-hidden="true"><InfoFilled /></el-icon>
        <span>{{ readonlyTip }}</span>
      </footer>

      <!-- 个人话术维护 -->
      <el-dialog
        v-model="qrDialogOpen"
        :title="qrForm.id ? '编辑个人话术' : '新增个人话术'"
        width="480px"
        append-to-body
      >
        <el-form label-position="top">
          <el-form-item label="话术标题" required>
            <el-input v-model="qrForm.title" maxlength="64" show-word-limit placeholder="如：退款时效说明" />
          </el-form-item>
          <el-form-item label="话术分类">
            <el-input v-model="qrForm.category" maxlength="32" placeholder="缺省为「通用」" />
          </el-form-item>
          <el-form-item label="话术内容" required>
            <el-input
              v-model="qrForm.content"
              type="textarea"
              :rows="4"
              resize="none"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="qrDialogOpen = false">取消</el-button>
          <el-button type="primary" :loading="qrSaving" @click="saveQuickReply">保存</el-button>
        </template>
      </el-dialog>
    </div>

    <div v-else class="sd-error">
      <p>{{ error || '会话详情加载失败' }}</p>
      <el-button @click="load">重新加载</el-button>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, EditPen, InfoFilled, Promotion, SwitchButton } from '@element-plus/icons-vue'
import StatusPill from '@/components/StatusPill.vue'
import AgentAssistPanel from '@/components/AgentAssistPanel.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  closeAgentSession,
  createQuickReply,
  createSessionNote,
  deleteQuickReply,
  getAgentSuggestions,
  getQuickReplies,
  getSessionDetail,
  getSessionMessages,
  sendSessionMessage,
  updateQuickReply,
  useQuickReply
} from '@/api'
import { SESSION_STATUS_DICT, dictLabel, dictTone, formatClock, formatDateTime } from '@/utils/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  sessionId: { type: [Number, String], default: null },
  role: { type: String, default: 'AGENT' }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const detail = ref(null)
const messages = ref([])
const loading = ref(false)
const error = ref('')
const draft = ref('')
const sending = ref(false)
const closing = ref(false)
const scrollRef = ref(null)

/* ---------- 快捷回复话术 ---------- */
const qrOpen = ref(false)
const qrLoading = ref(false)
const qrError = ref('')
const qrList = ref([])
const qrCategory = ref('')
const qrKeyword = ref('')
const qrDialogOpen = ref(false)
const qrSaving = ref(false)
const qrForm = ref(emptyQrForm())

/* ---------- 内部备注与输入联想 ---------- */
const noteOpen = ref(false)
const noteDraft = ref('')
const noteSaving = ref(false)
const suggestions = ref([])
let suggestTimer = null

let pollTimer = null
let lastMessageId = 0
let autoScroll = true

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const drawerSize = computed(() => (window.innerWidth < 720 ? '100%' : '560px'))
const drawerTitle = computed(() => {
  if (props.role === 'AGENT') return '会话工作台'
  return '会话详情'
})

const canReply = computed(
  () => props.role === 'AGENT' && detail.value?.status === 'HUMAN'
)

const assistable = computed(() => {
  if (props.role !== 'AGENT' && props.role !== 'ADMIN') return false
  return detail.value?.status === 'HUMAN' || detail.value?.status === 'CLOSED'
})

const qrCategories = computed(() => {
  const set = new Set()
  for (const item of qrList.value) {
    if (item.category) set.add(item.category)
  }
  return [...set]
})

const filteredQuickReplies = computed(() => {
  const keyword = qrKeyword.value.trim().toLowerCase()
  return qrList.value.filter((item) => {
    if (qrCategory.value && item.category !== qrCategory.value) return false
    if (!keyword) return true
    const text = `${item.title || ''}${item.content || ''}`.toLowerCase()
    return text.includes(keyword)
  })
})

const timeline = computed(() => {
  const item = detail.value || {}
  return [
    { key: 'create', label: '创建', time: item.createTime },
    { key: 'queue', label: '进入排队', time: item.queueEnterTime },
    { key: 'accept', label: '坐席接入', time: item.acceptTime },
    { key: 'end', label: '结束', time: item.endTime }
  ]
})

// 与列表「对话数」保持同一口径：只统计用户/AI/坐席对话，欢迎语与系统提示不计入
const dialogTotal = computed(() => {
  const counted = detail.value?.messageCount
  if (counted != null) return Number(counted)
  return messages.value.filter((msg) => msg.role !== 'system' && msg.aiModel !== 'SYSTEM').length
})

const readonlyTip = computed(() => {
  if (!detail.value) return ''
  if (props.role === 'AGENT' && detail.value.status === 'WAITING') {
    return '该会话仍在排队中，请在「坐席工作台」完成接入后再回复'
  }
  if (detail.value.status === 'CLOSED') {
    return '会话已结束，消息记录只读'
  }
  if (props.role !== 'AGENT') {
    return '当前角色仅可查看会话记录'
  }
  return ''
})

function mapMessage(raw) {
  const sender = raw?.senderType || 'AI'
  const role =
    sender === 'USER'
      ? 'user'
      : sender === 'AGENT'
        ? 'agent'
        : sender === 'SYSTEM'
          ? 'system'
          : sender === 'INTERNAL_NOTE'
            ? 'note'
            : 'ai'
  const fallbackName =
    role === 'user'
      ? '访客'
      : role === 'agent'
        ? raw?.senderName || '坐席'
        : role === 'ai'
          ? 'AI 助手'
          : role === 'note'
            ? raw?.senderName || '内部备注'
            : '系统'
  return {
    id: raw?.id || `local-${Math.random().toString(36).slice(2, 8)}`,
    role,
    name: raw?.senderName || fallbackName,
    content: raw?.content || '',
    time: formatClock(raw?.createTime) || '',
    matchedKbId: raw?.matchedKbId || null,
    aiModel: raw?.aiModel || ''
  }
}

function mergeMessages(list) {
  for (const raw of list) {
    if (!raw?.id) continue
    if (messages.value.some((item) => String(item.id) === String(raw.id))) continue
    messages.value.push(mapMessage(raw))
    lastMessageId = Math.max(lastMessageId, Number(raw.id) || 0)
  }
  messages.value.sort((a, b) => Number(a.id) - Number(b.id))
}

async function loadMessages(incremental = false) {
  if (!props.sessionId) return
  try {
    const params = incremental
      ? { pageNum: 1, pageSize: 100, afterId: lastMessageId, markRead: true }
      : { pageNum: 1, pageSize: 100, markRead: true }
    const page = await getSessionMessages(props.sessionId, params)
    const list = page?.list || []
    if (incremental) {
      if (list.length) mergeMessages(list)
    } else {
      messages.value = []
      lastMessageId = 0
      mergeMessages(list)
    }
    if (autoScroll) scrollToBottom()
  } catch {
    /* 轮询失败不打断当前视图 */
  }
}

async function load() {
  if (!props.sessionId) return
  loading.value = true
  error.value = ''
  try {
    const data = await getSessionDetail(props.sessionId)
    detail.value = data || null
    await loadMessages(false)
  } catch (err) {
    detail.value = null
    messages.value = []
    error.value = err?.message || '会话详情加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function onScroll() {
  const el = scrollRef.value
  if (!el) return
  autoScroll = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

async function send() {
  const content = draft.value.trim()
  if (!content || sending.value || !canReply.value) return
  sending.value = true
  try {
    const data = await sendSessionMessage(props.sessionId, {
      content,
      contentType: 'TEXT',
      clientMsgId: `agent-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    })
    draft.value = ''
    autoScroll = true
    if (data?.message) mergeMessages([data.message])
    if (data?.sessionStatus && detail.value) {
      detail.value = { ...detail.value, status: data.sessionStatus }
    }
    await loadMessages(true)
    emit('changed')
  } catch (err) {
    ElMessage.error(err?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function onCloseSession() {
  try {
    await ElMessageBox.confirm(
      '结束后访客将无法继续发送消息，并会收到评价邀请。确认结束本次会话？',
      '结束会话',
      { type: 'warning', confirmButtonText: '确认结束', cancelButtonText: '继续沟通' }
    )
  } catch {
    return
  }
  closing.value = true
  try {
    await closeAgentSession(props.sessionId, { closeReason: 'AGENT_CLOSED' })
    ElMessage.success('会话已结束')
    if (detail.value) detail.value = { ...detail.value, status: 'CLOSED' }
    emit('changed')
  } catch (err) {
    ElMessage.error(err?.message || '结束会话失败，请稍后重试')
  } finally {
    closing.value = false
  }
}

function emptyQrForm() {
  return { id: null, category: '通用', title: '', content: '', sort: 0 }
}

async function loadQuickReplies() {
  if (props.role !== 'AGENT') return
  qrLoading.value = true
  qrError.value = ''
  try {
    qrList.value = (await getQuickReplies({ status: 1 })) || []
  } catch (err) {
    qrList.value = []
    qrError.value = err?.message || '话术加载失败，请稍后重试'
  } finally {
    qrLoading.value = false
  }
}

function toggleQuickReplies() {
  qrOpen.value = !qrOpen.value
  if (qrOpen.value && !qrList.value.length) loadQuickReplies()
}

async function insertQuickReply(item) {
  try {
    const data = await useQuickReply(item.id)
    const text = data?.content || item.content
    draft.value = draft.value.trim() ? `${draft.value.trimEnd()}\n${text}` : text
    item.useCount = data?.useCount ?? (Number(item.useCount) || 0) + 1
  } catch (err) {
    ElMessage.error(err?.message || '话术引用失败，请稍后重试')
  }
}

function insertAssist(text) {
  const value = String(text || '').trim()
  if (!value) return
  draft.value = draft.value.trim() ? `${draft.value.trimEnd()}\n${value}` : value
}

/**
 * 坐席端输入联想（R-112）：输入 2 个字以上才请求，300ms 防抖，避免每键一次请求。
 */
function scheduleSuggestions() {
  if (suggestTimer) window.clearTimeout(suggestTimer)
  const keyword = draft.value.trim()
  if (!canReply.value || keyword.length < 2) {
    suggestions.value = []
    return
  }
  suggestTimer = window.setTimeout(async () => {
    try {
      const data = await getAgentSuggestions(props.sessionId, { query: keyword, limit: 5 })
      suggestions.value = data?.items || []
    } catch {
      suggestions.value = []
    }
  }, 300)
}

function applySuggestion(item) {
  if (!item) return
  insertAssist(item.answer || item.question || '')
  suggestions.value = []
}

async function saveNote() {
  const content = noteDraft.value.trim()
  if (!content || noteSaving.value) return
  noteSaving.value = true
  try {
    await createSessionNote(props.sessionId, content)
    noteDraft.value = ''
    ElMessage.success('内部备注已保存，仅坐席可见')
    await loadMessages(true)
  } catch (err) {
    ElMessage.error(err?.message || '备注保存失败，请稍后重试')
  } finally {
    noteSaving.value = false
  }
}

function openQrDialog(item) {
  qrForm.value = item
    ? {
        id: item.id,
        category: item.category || '通用',
        title: item.title || '',
        content: item.content || '',
        sort: item.sort || 0
      }
    : emptyQrForm()
  qrDialogOpen.value = true
}

async function saveQuickReply() {
  const form = qrForm.value
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写话术标题与内容')
    return
  }
  qrSaving.value = true
  try {
    const payload = {
      scope: 'PERSONAL',
      category: (form.category || '').trim() || '通用',
      title: form.title.trim(),
      content: form.content.trim(),
      sort: form.sort || 0,
      status: 1
    }
    if (form.id) {
      await updateQuickReply(form.id, payload)
    } else {
      await createQuickReply(payload)
    }
    ElMessage.success(form.id ? '话术已更新' : '话术已保存')
    qrDialogOpen.value = false
    await loadQuickReplies()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败，请稍后重试')
  } finally {
    qrSaving.value = false
  }
}

async function removeQuickReply(item) {
  try {
    await ElMessageBox.confirm(`确认删除话术「${item.title}」？`, '删除话术', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteQuickReply(item.id)
    ElMessage.success('话术已删除')
    await loadQuickReplies()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败，请稍后重试')
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    if (detail.value?.status === 'HUMAN' || detail.value?.status === 'WAITING') {
      loadMessages(true)
    }
  }, 5000)
}

function stopPolling() {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = null
}

function onClosed() {
  stopPolling()
  detail.value = null
  messages.value = []
  draft.value = ''
  lastMessageId = 0
  qrOpen.value = false
  qrKeyword.value = ''
  qrCategory.value = ''
  qrDialogOpen.value = false
  noteOpen.value = false
  noteDraft.value = ''
  suggestions.value = []
  if (suggestTimer) window.clearTimeout(suggestTimer)
}

watch(
  () => draft.value,
  () => scheduleSuggestions()
)

watch(
  () => [props.modelValue, props.sessionId],
  ([open]) => {
    if (open && props.sessionId) {
      lastMessageId = 0
      autoScroll = true
      suggestions.value = []
      load()
      startPolling()
    } else {
      stopPolling()
    }
  }
)

onBeforeUnmount(() => {
  stopPolling()
  if (suggestTimer) window.clearTimeout(suggestTimer)
})
</script>

<style scoped>
.sd {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.sd-loading {
  padding: 8px;
}

.sd-head {
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--zk-border);
}

.sd-head-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sd-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.sd-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.sd-rating {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--zk-text-2);
}

.sd-rating-label {
  color: var(--zk-text-3);
}

.sd-stars {
  color: #f59e0b;
  letter-spacing: 1px;
}

.sd-stars .is-empty {
  color: #e2e8f0;
}

.sd-rating-num {
  font-weight: 650;
  color: #b45309;
}

.sd-rating-comment {
  color: var(--zk-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sd-rating-empty {
  margin: 8px 0 0;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.sd-timeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.sd-timeline li {
  padding: 6px 8px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
}

.sd-timeline-label {
  display: block;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.sd-timeline-time {
  display: block;
  margin-top: 2px;
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  color: var(--zk-text-3);
}

.sd-timeline li.done {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.sd-timeline li.done .sd-timeline-label {
  color: #15803d;
  font-weight: 600;
}

.sd-timeline li.done .sd-timeline-time {
  color: var(--zk-text-2);
}

/* ---------- 消息 ---------- */
.sd-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 2px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sd-empty {
  margin: auto;
  text-align: center;
  color: var(--zk-text-3);
  font-size: 12px;
}

.sd-empty p {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--zk-text-2);
}

.sd-msg {
  display: flex;
  gap: 8px;
}

.sd-msg.user {
  flex-direction: row-reverse;
}

.sd-msg-system {
  width: 100%;
}

.sd-system {
  text-align: center;
  font-size: 11px;
  color: var(--zk-text-3);
  background: var(--zk-surface-2);
  border-radius: var(--zk-radius-sm);
  padding: 3px 12px;
  width: fit-content;
  margin: 0 auto;
}

.sd-msg-avatar {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 10.5px;
  font-weight: 600;
  color: #fff;
  background: var(--zk-accent);
}

.sd-msg-avatar.is-user {
  background: #17181c;
}

.sd-msg-avatar.is-agent {
  background: #b45309;
}

.sd-msg-body {
  max-width: 78%;
  min-width: 0;
}

.sd-msg.user .sd-msg-body {
  text-align: right;
}

.sd-msg-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.sd-msg.user .sd-msg-head {
  justify-content: flex-end;
}

.sd-msg-name {
  font-weight: 600;
  color: var(--zk-text-2);
}

.sd-tag {
  font-size: 9.5px;
  padding: 0 4px;
  border-radius: 3px;
  color: #15803d;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.sd-bubble {
  display: inline-block;
  text-align: left;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  color: var(--zk-text);
}

.sd-bubble.is-user {
  background: var(--zk-accent);
  border-color: var(--zk-accent);
  color: #fff;
}

.sd-bubble.is-agent {
  background: #fff8ea;
  border-color: #fbe0b0;
  color: #713f12;
}

/* ---------- 内部备注（仅坐席可见） ---------- */
.sd-msg.note {
  width: 100%;
}

.sd-note {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 7px 10px;
  border: 1px dashed #e3c98a;
  border-radius: var(--zk-radius-sm);
  background: #fffbeb;
}

.sd-note-tag {
  width: fit-content;
  padding: 0 5px;
  border-radius: 3px;
  font-size: 9.5px;
  font-weight: 600;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fde68a;
}

.sd-note-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #78350f;
  white-space: pre-wrap;
  word-break: break-word;
}

.sd-note-time {
  font-size: 10px;
  color: #a16207;
}

/* ---------- 快捷回复 ---------- */
.sd-qr {
  margin-bottom: 8px;
}

.sd-qr-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.sd-qr-select {
  width: 110px;
}

.sd-qr-search {
  width: 130px;
}

.sd-qr-panel {
  margin-top: 6px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
}

.sd-qr-state {
  margin: 0;
  padding: 12px;
  text-align: center;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.sd-qr-list {
  list-style: none;
  margin: 0;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sd-qr-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px;
  border-radius: 6px;
}

.sd-qr-list li:hover {
  background: #fff;
}

.sd-qr-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 5px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.sd-qr-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-text);
}

.sd-qr-scope {
  padding: 0 4px;
  border-radius: 3px;
  font-size: 9.5px;
  font-weight: 500;
}

.sd-qr-scope.is-public {
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.sd-qr-scope.is-personal {
  color: #7c3aed;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
}

.sd-qr-content {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--zk-text-3);
}

.sd-qr-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 2px;
}

.sd-qr-count {
  font-size: 10px;
  color: var(--zk-text-3);
}

/* ---------- 输入区 ---------- */
.sd-input {
  padding-top: 12px;
  border-top: 1px solid var(--zk-border);
}

.sd-input-actions {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.sd-input-tip {
  margin: 6px 0 0;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

/* ---------- 内部备注录入 ---------- */
.sd-note-panel {
  margin-bottom: 8px;
  padding: 8px;
  border: 1px dashed #e3c98a;
  border-radius: var(--zk-radius-sm);
  background: #fffbeb;
}

.sd-note-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

/* ---------- 坐席输入联想 ---------- */
.sd-suggest {
  margin-bottom: 6px;
  max-height: 168px;
  overflow-y: auto;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: #fff;
  box-shadow: var(--zk-shadow-sm);
}

.sd-suggest-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 9px;
  border: 0;
  border-bottom: 1px solid var(--zk-border);
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.sd-suggest-item:last-child {
  border-bottom: 0;
}

.sd-suggest-item:hover {
  background: var(--zk-surface-2);
}

.sd-suggest-q {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--zk-text);
}

.sd-suggest-score {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.sd-readonly {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-top: 1px solid var(--zk-border);
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.sd-error {
  padding: 24px 8px;
  text-align: center;
  color: var(--zk-text-2);
  font-size: 13px;
}

.sd-error p {
  margin: 0 0 12px;
}

@media (max-width: 720px) {
  .sd-msg-body {
    max-width: 82%;
  }

  .sd-timeline {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
