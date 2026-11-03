<template>
  <section class="aa" aria-label="坐席辅助">
    <button type="button" class="aa-head" @click="open = !open">
      <span class="aa-title">
        <el-icon aria-hidden="true"><MagicStick /></el-icon>
        坐席辅助
      </span>
      <span class="aa-sub">{{ headHint }}</span>
      <el-icon class="aa-arrow" :class="{ 'is-open': open }" aria-hidden="true">
        <ArrowDown />
      </el-icon>
    </button>

    <!-- 展开/收起：面板从标题下方滑出，关闭按原路收回 -->
    <Transition name="aa-slide">
    <div v-if="open" class="aa-body">
      <el-tabs v-model="tab" stretch class="aa-tabs">
        <!-- 实时推荐答案 -->
        <el-tab-pane label="推荐答案" name="recommend">
          <div class="aa-toolbar">
            <span class="aa-hint">{{ recommend?.note || '根据访客最近一条消息匹配知识库' }}</span>
            <el-button
              size="small"
              text
              :icon="Refresh"
              :loading="loadingRecommend"
              @click="loadRecommend"
            >
              刷新
            </el-button>
          </div>

          <p v-if="recommend?.query" class="aa-query">当前诉求：{{ recommend.query }}</p>

          <TransitionGroup v-if="visibleItems.length" tag="ul" name="zk-list" class="aa-items">
            <li
              v-for="(item, index) in visibleItems"
              :key="item.kbId"
              class="aa-item"
              :style="{ '--aa-i': index }"
            >
              <div class="aa-item-head">
                <span class="aa-item-q">{{ item.question }}</span>
                <span class="aa-score zk-num">{{ scoreText(item.score) }}</span>
              </div>
              <p class="aa-item-a">{{ item.answer }}</p>
              <div class="aa-item-meta">
                <span>{{ item.answerSource }}</span>
                <span v-if="item.matchedType === 'SIMILAR'">命中问法：{{ item.matchedQuestion }}</span>
                <span v-if="item.hitKeywords?.length">关键词：{{ item.hitKeywords.join('、') }}</span>
              </div>
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="!canInsert"
                @click="insertAnswer(item)"
              >
                引用答案
              </el-button>
            </li>
          </TransitionGroup>

          <div v-else-if="recommend?.aiDraft" class="aa-ai">
            <div class="aa-item-head">
              <span class="aa-item-q">AI 起草草稿</span>
              <span v-if="recommend.model" class="aa-model">{{ recommend.model }}</span>
            </div>
            <p class="aa-item-a">{{ recommend.aiDraft }}</p>
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="!canInsert"
              @click="insert(recommend.aiDraft)"
            >
              引用草稿
            </el-button>
          </div>

          <div v-else-if="loadingRecommend" class="aa-placeholder">
            <ZkSkeleton variant="lines" :rows="3" />
            <span class="sr-only">推荐生成中…</span>
          </div>
          <p v-else-if="errorRecommend" class="aa-state">{{ errorRecommend }}</p>
          <p v-else class="aa-state">{{ recommend?.note || '暂无推荐' }}</p>
        </el-tab-pane>

        <!-- 进线摘要 -->
        <el-tab-pane label="进线摘要" name="brief">
          <div class="aa-toolbar">
            <span class="aa-hint">{{ brief?.note || '转人工时自动生成，坐席接起即懂来意' }}</span>
            <el-button
              size="small"
              text
              :icon="Refresh"
              :loading="loadingBrief"
              @click="regenerateBrief"
            >
              重新生成
            </el-button>
          </div>

          <div v-if="loadingBrief && !brief" class="aa-placeholder">
            <ZkSkeleton variant="lines" :rows="4" />
            <span class="sr-only">摘要生成中…</span>
          </div>
          <p v-else-if="errorBrief" class="aa-state">{{ errorBrief }}</p>
          <template v-else-if="brief">
            <div class="aa-tags">
              <el-tag size="small" :type="brief.source === 'AI' ? 'primary' : 'info'" effect="plain">
                {{ brief.source === 'AI' ? 'AI 生成' : brief.source === 'RULE' ? '规则兜底' : '坐席编写' }}
              </el-tag>
              <el-tag v-if="brief.model" size="small" type="info" effect="plain">{{ brief.model }}</el-tag>
            </div>
            <dl class="aa-sections">
              <div>
                <dt>客户诉求</dt>
                <dd>{{ brief.customerDemand || '—' }}</dd>
              </div>
              <div>
                <dt>服务过程</dt>
                <dd>{{ brief.serviceProcess || '—' }}</dd>
              </div>
              <div>
                <dt>处理结果</dt>
                <dd>{{ brief.result || '—' }}</dd>
              </div>
              <div>
                <dt>待办事项</dt>
                <dd>{{ brief.followUp || '—' }}</dd>
              </div>
            </dl>
            <div v-if="brief.keywords?.length" class="aa-tags">
              <el-tag v-for="word in brief.keywords" :key="word" size="small" effect="plain">
                {{ word }}
              </el-tag>
            </div>
            <div v-if="brief.recentOrders?.length" class="aa-orders">
              <p class="aa-orders-title">客户近期工单</p>
              <ul>
                <li v-for="order in brief.recentOrders" :key="order.id">
                  <span class="aa-order-no">{{ order.orderNo }}</span>
                  <span class="aa-order-title">{{ order.title }}</span>
                  <span class="aa-order-status">{{ order.statusName }} · {{ order.priority }}</span>
                </li>
              </ul>
            </div>
          </template>
        </el-tab-pane>

        <!-- 服务小结 -->
        <el-tab-pane label="服务小结" name="summary">
          <div class="aa-toolbar">
            <el-tag v-if="summary?.status" size="small" :type="summary.status === 'ADOPTED' ? 'success' : 'info'">
              {{ summary.status === 'ADOPTED' ? '已采纳归档' : '草稿' }}
            </el-tag>
            <span v-else class="aa-hint">会话结束前生成并归档</span>
            <el-button
              size="small"
              text
              :icon="MagicStick"
              :loading="generating"
              @click="generateSummary"
            >
              {{ summary ? '重新生成' : '生成小结' }}
            </el-button>
          </div>

          <div v-if="loadingSummary" class="aa-placeholder">
            <ZkSkeleton variant="lines" :rows="3" />
            <span class="sr-only">小结加载中…</span>
          </div>
          <p v-else-if="errorSummary" class="aa-state">{{ errorSummary }}</p>
          <el-form v-else label-position="top" size="small" class="aa-form">
            <el-form-item label="客户诉求">
              <el-input
                v-model="form.customerDemand"
                type="textarea"
                :rows="2"
                resize="none"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="服务过程">
              <el-input
                v-model="form.serviceProcess"
                type="textarea"
                :rows="3"
                resize="none"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="处理结果">
              <el-input
                v-model="form.result"
                type="textarea"
                :rows="2"
                resize="none"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="待办事项">
              <el-input
                v-model="form.followUp"
                type="textarea"
                :rows="2"
                resize="none"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="form.keywordsText" maxlength="255" placeholder="英文逗号分隔，最多 8 个" />
            </el-form-item>
            <div class="aa-actions">
              <el-button
                size="small"
                type="primary"
                :loading="saving"
                :disabled="!canSave"
                @click="adopt"
              >
                保存并归档
              </el-button>
              <span class="aa-hint">{{ summary?.source ? sourceText(summary.source) : 'AI 生成后需坐席确认' }}</span>
            </div>
          </el-form>
        </el-tab-pane>
        <!-- 客户信息卡（AS-06） -->
        <el-tab-pane label="客户资料" name="customer">
          <div class="aa-toolbar">
            <span class="aa-hint">{{ customer?.note || '聚合客户历史会话、工单与满意度' }}</span>
            <el-button
              size="small"
              text
              :icon="Refresh"
              :loading="loadingCustomer"
              @click="loadCustomer"
            >
              刷新
            </el-button>
          </div>

          <div v-if="loadingCustomer && !customer" class="aa-placeholder">
            <ZkSkeleton variant="list" :rows="2" />
            <span class="sr-only">客户资料加载中…</span>
          </div>
          <p v-else-if="errorCustomer" class="aa-state">{{ errorCustomer }}</p>
          <template v-else-if="customer">
            <div class="aa-customer-head">
              <span class="aa-customer-name">{{ customer.userName || '访客' }}</span>
              <el-tag v-if="customer.profile?.returning" size="small" type="warning" effect="plain">
                老客户
              </el-tag>
              <span v-if="customer.phone" class="aa-customer-contact zk-num">{{ customer.phone }}</span>
            </div>
            <div class="aa-stats">
              <div>
                <span class="zk-num">{{ customer.profile?.totalSessions ?? 0 }}</span>
                <em>累计会话</em>
              </div>
              <div>
                <span class="zk-num">{{ customer.profile?.humanSessions ?? 0 }}</span>
                <em>人工接待</em>
              </div>
              <div>
                <span class="zk-num">{{ ratingText }}</span>
                <em>平均满意度</em>
              </div>
              <div>
                <span class="zk-num">{{ customer.orderStats?.total ?? 0 }}</span>
                <em>工单总数</em>
              </div>
            </div>

            <p v-if="customer.current?.demand" class="aa-query">
              本次诉求：{{ customer.current.demand }}
            </p>
            <div v-if="customer.current?.keywords?.length" class="aa-tags">
              <el-tag v-for="word in customer.current.keywords" :key="word" size="small" effect="plain">
                {{ word }}
              </el-tag>
            </div>

            <div v-if="customer.recentSessions?.length" class="aa-orders">
              <p class="aa-orders-title">历史会话（最近 {{ customer.recentSessions.length }} 次）</p>
              <ul>
                <li v-for="item in customer.recentSessions" :key="item.id">
                  <span class="aa-order-no">{{ item.sessionNo }}</span>
                  <span class="aa-order-title">
                    {{ item.statusName }}{{ item.agentName ? ` · ${item.agentName}` : '' }}
                  </span>
                  <span class="aa-order-status">{{ item.rating ? `${item.rating} 星` : '未评价' }}</span>
                </li>
              </ul>
            </div>

            <div v-if="customer.recentOrders?.length" class="aa-orders">
              <p class="aa-orders-title">
                历史工单（处理中 {{ customer.orderStats?.open ?? 0 }} / 已完结 {{ customer.orderStats?.resolved ?? 0 }}）
              </p>
              <ul>
                <li v-for="item in customer.recentOrders" :key="item.id">
                  <span class="aa-order-no">{{ item.orderNo }}</span>
                  <span class="aa-order-title">{{ item.title }}</span>
                  <span class="aa-order-status">{{ item.statusName }}</span>
                </li>
              </ul>
            </div>

            <p
              v-if="!customer.recentSessions?.length && !customer.recentOrders?.length"
              class="aa-state"
            >
              暂无历史会话与工单
            </p>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>
    </Transition>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, MagicStick, Refresh } from '@element-plus/icons-vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  generateSessionSummary,
  getAgentCustomerCard,
  getAgentRecommendations,
  getSessionBrief,
  getSessionSummary,
  regenerateSessionBrief,
  saveSessionSummary
} from '@/api'

const props = defineProps({
  sessionId: { type: [Number, String], default: null },
  status: { type: String, default: '' },
  messageCount: { type: Number, default: 0 },
  canInsert: { type: Boolean, default: false }
})
const emit = defineEmits(['insert'])

const open = ref(true)
const tab = ref('recommend')

const recommend = ref(null)
const brief = ref(null)
const summary = ref(null)
const customer = ref(null)

const loadingRecommend = ref(false)
const loadingBrief = ref(false)
const loadingSummary = ref(false)
const loadingCustomer = ref(false)
const generating = ref(false)
const saving = ref(false)

const errorRecommend = ref('')
const errorBrief = ref('')
const errorSummary = ref('')
const errorCustomer = ref('')

const form = reactive(emptyForm())

/** 已被采纳的建议：从列表里收缩移除，剩余条目平滑补位 */
const dismissedKbIds = ref([])
const visibleItems = computed(() =>
  (recommend.value?.items || []).filter((item) => !dismissedKbIds.value.includes(item.kbId))
)

const headHint = computed(() =>
  props.status === 'CLOSED' ? '会话已结束，可查看摘要与小结' : 'AI 建议仅作草稿，须坐席确认'
)

const ratingText = computed(() => {
  const avg = customer.value?.profile?.avgRating
  if (avg == null) return '—'
  return Number(avg).toFixed(1)
})

const canSave = computed(() => {
  if (props.status !== 'HUMAN' && props.status !== 'CLOSED') return false
  return Boolean(
    form.customerDemand.trim() ||
      form.serviceProcess.trim() ||
      form.result.trim() ||
      form.followUp.trim()
  )
})

function emptyForm() {
  return {
    customerDemand: '',
    serviceProcess: '',
    result: '',
    followUp: '',
    keywordsText: ''
  }
}

function fillForm(data) {
  form.customerDemand = data?.customerDemand || ''
  form.serviceProcess = data?.serviceProcess || ''
  form.result = data?.result || ''
  form.followUp = data?.followUp || ''
  form.keywordsText = (data?.keywords || []).join(', ')
}

function scoreText(score) {
  return `${Math.round((Number(score) || 0) * 100)}%`
}

function sourceText(source) {
  if (source === 'AI') return 'AI 生成'
  if (source === 'RULE') return '规则兜底'
  return '坐席编写'
}

function insert(text) {
  if (!props.canInsert || !text) return
  emit('insert', text)
}

/** 采纳一条建议：先回填到回复框，再让这条从建议列表里收缩移除 */
function insertAnswer(item) {
  if (!props.canInsert || !item) return
  emit('insert', item.answer)
  if (!dismissedKbIds.value.includes(item.kbId)) {
    dismissedKbIds.value = [...dismissedKbIds.value, item.kbId]
  }
}

async function loadRecommend() {
  if (!props.sessionId) return
  loadingRecommend.value = true
  errorRecommend.value = ''
  try {
    const data = await getAgentRecommendations(props.sessionId, { limit: 3 })
    recommend.value = data || null
  } catch (err) {
    errorRecommend.value = err?.message || '推荐加载失败，请稍后重试'
  } finally {
    loadingRecommend.value = false
  }
}

async function loadBrief() {
  if (!props.sessionId) return
  loadingBrief.value = true
  errorBrief.value = ''
  try {
    brief.value = (await getSessionBrief(props.sessionId)) || null
  } catch (err) {
    errorBrief.value = err?.message || '进线摘要加载失败，请稍后重试'
  } finally {
    loadingBrief.value = false
  }
}

async function regenerateBrief() {
  if (!props.sessionId || loadingBrief.value) return
  loadingBrief.value = true
  errorBrief.value = ''
  try {
    brief.value = (await regenerateSessionBrief(props.sessionId)) || null
    ElMessage.success('进线摘要已更新')
  } catch (err) {
    errorBrief.value = err?.message || '重新生成失败，请稍后重试'
  } finally {
    loadingBrief.value = false
  }
}

async function loadSummary() {
  if (!props.sessionId) return
  loadingSummary.value = true
  errorSummary.value = ''
  try {
    summary.value = (await getSessionSummary(props.sessionId)) || null
    fillForm(summary.value)
  } catch (err) {
    errorSummary.value = err?.message || '服务小结加载失败，请稍后重试'
  } finally {
    loadingSummary.value = false
  }
}

async function generateSummary() {
  if (!props.sessionId || generating.value) return
  generating.value = true
  errorSummary.value = ''
  try {
    summary.value = (await generateSessionSummary(props.sessionId)) || null
    fillForm(summary.value)
    ElMessage.success('服务小结草稿已生成，请确认后归档')
  } catch (err) {
    errorSummary.value = err?.message || '生成失败，请稍后重试'
  } finally {
    generating.value = false
  }
}

async function adopt() {
  if (!props.sessionId || saving.value) return
  if (!canSave.value) {
    ElMessage.warning('请先填写服务小结内容')
    return
  }
  const keywords = form.keywordsText
    .split(/[,，;；、]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 8)
  saving.value = true
  try {
    summary.value = await saveSessionSummary(props.sessionId, {
      customerDemand: form.customerDemand.trim(),
      serviceProcess: form.serviceProcess.trim(),
      result: form.result.trim(),
      followUp: form.followUp.trim(),
      keywords
    })
    fillForm(summary.value)
    ElMessage.success('服务小结已归档')
  } catch (err) {
    ElMessage.error(err?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function loadCustomer() {
  if (!props.sessionId) return
  loadingCustomer.value = true
  errorCustomer.value = ''
  try {
    customer.value = (await getAgentCustomerCard(props.sessionId)) || null
  } catch (err) {
    customer.value = null
    errorCustomer.value = err?.message || '客户资料加载失败，请稍后重试'
  } finally {
    loadingCustomer.value = false
  }
}

function loadAll() {
  loadRecommend()
  loadBrief()
  loadSummary()
  loadCustomer()
}

watch(
  () => props.sessionId,
  (value) => {
    if (!value) return
    recommend.value = null
    brief.value = null
    summary.value = null
    customer.value = null
    dismissedKbIds.value = []
    Object.assign(form, emptyForm())
    loadAll()
  }
)

// 新消息到达时自动刷新推荐答案（其他页签按需手动刷新）
watch(
  () => props.messageCount,
  (value, oldValue) => {
    if (!open.value || tab.value !== 'recommend') return
    if (Number(value) > Number(oldValue || 0)) loadRecommend()
  }
)

onMounted(loadAll)
</script>

<style scoped>
.aa {
  border-top: 1px solid var(--zk-border);
  background: var(--zk-surface-2);
}

.aa-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 4px;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.aa-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.aa-sub {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.aa-arrow {
  font-size: 12px;
  color: var(--zk-text-3);
  transition: transform var(--zk-dur-base) var(--zk-ease-out);
}

.aa-arrow.is-open {
  transform: rotate(180deg);
}

/* 面板滑入：从标题下方落下，收起按原路回去 */
.aa-slide-enter-active {
  transition: opacity 200ms var(--zk-ease-out), transform 320ms var(--zk-ease-emphasis);
}

.aa-slide-leave-active {
  transition: opacity 140ms var(--zk-ease-in), transform 160ms var(--zk-ease-in);
}

.aa-slide-enter-from {
  opacity: 0;
  transform: translate3d(0, -8px, 0);
}

.aa-slide-leave-to {
  opacity: 0;
  transform: translate3d(0, -6px, 0);
}

.aa-placeholder {
  padding: 6px 0 2px;
}

.aa-body {
  max-height: 300px;
  overflow-y: auto;
  padding-bottom: 8px;
}

.aa-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.aa-tabs :deep(.el-tabs__item) {
  font-size: 12px;
  height: 32px;
  line-height: 32px;
}

.aa-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.aa-hint {
  font-size: 10.5px;
  color: var(--zk-text-3);
  line-height: 1.5;
}

.aa-query {
  margin: 0 0 6px;
  font-size: 11.5px;
  color: var(--zk-text-2);
  word-break: break-word;
}

.aa-customer-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.aa-customer-name {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.aa-customer-contact {
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.aa-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 8px;
}

.aa-stats div {
  padding: 6px 4px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: #fff;
  text-align: center;
}

.aa-stats span {
  display: block;
  font-size: 14px;
  font-weight: 650;
  color: var(--zk-text);
}

.aa-stats em {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  font-style: normal;
  color: var(--zk-text-3);
}

.aa-state {
  margin: 0;
  padding: 14px 0;
  text-align: center;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.aa-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 建议逐条浮现：45ms 错开；采纳后由 zk-list 过渡收缩移除、其余条目补位 */
.aa-item {
  animation: zk-rise-sm 240ms var(--zk-ease-soft) backwards;
  animation-delay: calc(var(--aa-i, 0) * 45ms);
}

.aa-item,
.aa-ai {
  padding: 8px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: #fff;
}

.aa-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.aa-item-q {
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-text);
}

.aa-score {
  flex: 0 0 auto;
  font-size: 10.5px;
  font-weight: 650;
  color: #15803d;
}

.aa-item-a {
  margin: 5px 0 6px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}

.aa-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 10px;
  margin-bottom: 6px;
  font-size: 10px;
  color: var(--zk-text-3);
}

.aa-model {
  font-size: 10px;
  color: var(--zk-text-3);
}

.aa-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}

.aa-sections {
  margin: 0 0 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.aa-sections dt {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--zk-text-3);
}

.aa-sections dd {
  margin: 2px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.aa-orders {
  padding: 6px 8px;
  border: 1px dashed var(--zk-border);
  border-radius: var(--zk-radius-sm);
}

.aa-orders-title {
  margin: 0 0 4px;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--zk-text-3);
}

.aa-orders ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.aa-orders li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--zk-text-2);
}

.aa-order-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
  color: var(--zk-text-3);
}

.aa-order-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aa-order-status {
  flex: 0 0 auto;
  font-size: 10px;
  color: var(--zk-text-3);
}

.aa-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.aa-form :deep(.el-form-item__label) {
  font-size: 11px;
  color: var(--zk-text-3);
  padding-bottom: 2px;
}

.aa-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 720px) {
  .aa-body {
    max-height: 240px;
  }
}
</style>
