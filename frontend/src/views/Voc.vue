<template>
  <div class="zk-page">
    <PageHeader
      title="客户之声"
      description="把未命中问法聚成可运营的主题：每轮按提问次数倒序取一批问法，与已有主题做结构相似度匹配，达到阈值归入已有主题，否则新建自动主题；词云与渠道分布反映访客真实在问什么。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading || overviewLoading" @click="reloadAll">刷新</el-button>
        <el-button :icon="MagicStick" :loading="clustering" @click="runCluster">立即聚类</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建主题</el-button>
      </template>
    </PageHeader>

    <section class="metric-grid" aria-label="客户之声概览">
      <article class="metric-card indigo">
        <div class="metric-top">
          <span class="metric-label">未命中问法</span>
          <span class="metric-icon"><el-icon><ChatDotSquare /></el-icon></span>
        </div>
        <div class="metric-value">{{ formatNumber(overview.unmatchedTotal) }}<small>条</small></div>
        <div class="metric-foot">待处理 {{ formatNumber(overview.unmatchedPending) }} 条</div>
      </article>
      <article class="metric-card teal">
        <div class="metric-top">
          <span class="metric-label">聚类覆盖率</span>
          <span class="metric-icon"><el-icon><Opportunity /></el-icon></span>
        </div>
        <div class="metric-value">{{ clusterRateText }}<small /></div>
        <div class="metric-foot">已归入主题 {{ formatNumber(overview.clusteredCount) }} 条</div>
      </article>
      <article class="metric-card amber">
        <div class="metric-top">
          <span class="metric-label">热点话题</span>
          <span class="metric-icon"><el-icon><TrendCharts /></el-icon></span>
        </div>
        <div class="metric-value">{{ formatNumber(overview.hotTopicCount) }}<small>个</small></div>
        <div class="metric-foot">累计提问次数达到热点阈值</div>
      </article>
      <article class="metric-card indigo">
        <div class="metric-top">
          <span class="metric-label">主题总数</span>
          <span class="metric-icon"><el-icon><Collection /></el-icon></span>
        </div>
        <div class="metric-value">{{ formatNumber(overview.topicCount) }}<small>个</small></div>
        <div class="metric-foot">
          自动 {{ formatNumber(overview.autoTopicCount) }} · 人工 {{ formatNumber(overview.manualTopicCount) }}
        </div>
      </article>
      <article class="metric-card green">
        <div class="metric-top">
          <span class="metric-label">最近聚类</span>
          <span class="metric-icon"><el-icon><Timer /></el-icon></span>
        </div>
        <div class="metric-value metric-value--sm">{{ lastClusterText }}<small /></div>
        <div class="metric-foot">默认每 30 分钟自动执行一轮</div>
      </article>
      <article class="metric-card teal">
        <div class="metric-top">
          <span class="metric-label">词云词条</span>
          <span class="metric-icon"><el-icon><Histogram /></el-icon></span>
        </div>
        <div class="metric-value">{{ formatNumber(cloud.words.length) }}<small>个</small></div>
        <div class="metric-foot">统计 {{ formatNumber(cloud.questionCount) }} 条未转词条问法</div>
      </article>
    </section>

    <div class="insight-grid">
      <div class="zk-card cloud-card">
        <div class="panel-head">
          <div class="panel-title">访客词云</div>
          <span class="panel-hint">字号越大，说明越多人反复在问</span>
        </div>
        <div v-if="cloud.words.length" class="cloud-body">
          <span
            v-for="word in cloud.words"
            :key="word.term"
            class="cloud-word"
            :style="{ fontSize: cloudFont(word), opacity: cloudOpacity(word) }"
            :title="`${word.term}：${word.weight} 次提问 / ${word.questionCount} 条问法`"
          >
            {{ word.term }}
          </span>
        </div>
        <ZkEmpty
          v-else
          size="sm"
          title="暂无词云数据"
          hint="未命中问法转为知识词条后不再计入词云；可先执行一轮聚类或等待新的未命中产生"
        />
      </div>

      <div class="zk-card side-card">
        <div class="panel-head">
          <div class="panel-title">渠道分布</div>
          <span class="panel-hint">近 7 天新增问法趋势</span>
        </div>
        <div v-if="overview.channels.length" class="channel-list">
          <div v-for="item in overview.channels" :key="item.channel" class="channel-row">
            <div class="channel-top">
              <StatusPill
                :label="item.channelName || dictLabel(VOC_CHANNEL_DICT, item.channel)"
                :tone="dictTone(VOC_CHANNEL_DICT, item.channel)"
              />
              <span class="channel-num">{{ formatNumber(item.questionCount) }} 条 · {{ formatNumber(item.askCount) }} 次</span>
            </div>
            <div class="channel-bar">
              <i :style="{ width: channelWidth(item.questionCount) }" />
            </div>
          </div>
        </div>
        <div v-else class="channel-empty">暂无渠道数据</div>

        <div class="trend-wrap">
          <div class="trend-bars">
            <div v-for="item in overview.trend" :key="item.date" class="trend-col">
              <div class="trend-bar" :style="{ height: trendHeight(item.count) }" :title="`${item.date}：${item.count} 条`" />
              <span class="trend-label">{{ shortDate(item.date) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索主题名称"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.topicType" class="filter-select" placeholder="全部来源" clearable @change="search">
          <el-option v-for="item in VOC_TOPIC_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.status" class="filter-select" placeholder="全部状态" clearable @change="search">
          <el-option v-for="item in VOC_TOPIC_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :icon="Search" @click="search">查询</el-button>
        <div class="grow" />
        <el-button link type="primary" :icon="Sort" :disabled="total < 2" @click="openMerge">合并主题</el-button>
        <span class="toolbar-meta">共 <b class="zk-num">{{ total }}</b> 个主题</span>
      </div>

      <div class="table-scroll" role="region" aria-label="客户之声主题列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="6" class="table-skeleton" />
        <el-table v-else v-loading="loading" class="zk-table-fade-in" :data="rows" row-key="id" aria-label="主题列表">
          <el-table-column label="主题" min-width="220">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">{{ row.topicName }}</span>
                <span class="cell-hint">{{ row.sampleQuestion || row.keywords || '暂无代表问法' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="106">
            <template #default="{ row }">
              <StatusPill
                :label="row.topicTypeName || dictLabel(VOC_TOPIC_TYPE_DICT, row.topicType)"
                :tone="dictTone(VOC_TOPIC_TYPE_DICT, row.topicType)"
              />
            </template>
          </el-table-column>
          <el-table-column label="问法 / 提问次数" width="132">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ formatNumber(row.questionCount) }} 条问法</span>
                <span class="cell-hint">累计 {{ formatNumber(row.askTotal) }} 次</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="待处理 / 已转化" width="132">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ formatNumber(row.pendingCount) }} 待处理</span>
                <span class="cell-hint">{{ formatNumber(row.convertedCount) }} 已转词条</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="渠道" width="120">
            <template #default="{ row }">
              <span class="cell-hint">{{ channelsText(row.channels) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最近聚类" width="152">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ formatDateTime(row.lastClusterTime) }}</span>
                <span class="cell-hint">{{ Number(row.status) === 1 ? '参与自动归类' : '已停用' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="212" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDetail(row)">问法</el-button>
                <el-button link type="primary" class="op-btn" @click="openEdit(row)">编辑</el-button>
                <el-button
                  link
                  class="op-btn"
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  :loading="actionBusy === `status-${row.id}`"
                  @click="toggleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === `delete-${row.id}`"
                  @click="confirmDelete(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '主题加载失败' : '还没有主题'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : '点击「立即聚类」把未命中问法按语义相似度归成主题，也可手动新建主题'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else :loading="clustering" @click="runCluster">立即聚类</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>

      <div v-if="total > 0" class="zk-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          aria-label="主题分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      聚类只写「归属」，不改变问法的处理状态；主题停用后不再参与自动归类，删除主题会解除归属并在下一轮聚类重新归集 · 把高频主题一键转为知识词条，请到「知识运营」处理
    </div>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑主题' : '新建主题'" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="主题名称" prop="topicName">
          <el-input v-model="form.topicName" maxlength="64" show-word-limit placeholder="如：退款到账时效" />
        </el-form-item>
        <el-form-item label="代表关键词（选填）">
          <el-input v-model="form.keywords" maxlength="500" placeholder="英文逗号分隔，用于后续问法归类，如：退款,到账,多久" />
        </el-form-item>
        <el-form-item label="代表问法（选填）">
          <el-input v-model="form.sampleQuestion" maxlength="255" placeholder="如：退款多久能到账" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="排序号">
            <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
        <el-form-item label="运营备注（选填）">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="如：已提需求给知识运营补充词条 / 属于产品能力缺失，暂不沉淀"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSaving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mergeVisible" title="合并主题" width="520px" append-to-body>
      <p class="merge-hint">
        选择目标主题后，源主题下的问法会全部并入目标主题，源主题随即删除；下一轮聚类会按新主题重新归类。
      </p>
      <el-form label-position="top">
        <el-form-item label="目标主题">
          <el-select v-model="mergeTarget" class="w-full" filterable placeholder="选择要保留的主题">
            <el-option v-for="item in topicOptions" :key="item.id" :label="item.topicName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源主题（可多选）">
          <el-select v-model="mergeSources" class="w-full" multiple filterable placeholder="选择要并入的主题">
            <el-option
              v-for="item in mergeSourceOptions"
              :key="item.id"
              :label="item.topicName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeVisible = false">取消</el-button>
        <el-button type="primary" :loading="mergeSaving" @click="submitMerge">确认合并</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="620px" :with-header="false" append-to-body>
      <div v-if="detail.topic" class="detail-body">
        <div class="detail-head">
          <div>
            <div class="detail-title">{{ detail.topic.topicName }}</div>
            <div class="detail-sub">
              {{ detail.topic.topicTypeName }} · 累计 {{ formatNumber(detail.topic.askTotal) }} 次提问 · 最近聚类
              {{ formatDateTime(detail.topic.lastClusterTime) }}
            </div>
          </div>
          <StatusPill
            :label="detail.topic.topicTypeName"
            :tone="dictTone(VOC_TOPIC_TYPE_DICT, detail.topic.topicType)"
          />
        </div>

        <div class="detail-metrics">
          <div class="detail-metric">
            <span class="dm-value">{{ formatNumber(detail.topic.questionCount) }}</span>
            <span class="dm-label">问法条数</span>
          </div>
          <div class="detail-metric">
            <span class="dm-value">{{ formatNumber(detail.topic.pendingCount) }}</span>
            <span class="dm-label">待处理</span>
          </div>
          <div class="detail-metric">
            <span class="dm-value">{{ formatNumber(detail.topic.convertedCount) }}</span>
            <span class="dm-label">已转词条</span>
          </div>
          <div class="detail-metric">
            <span class="dm-value">{{ channelsText(detail.topic.channels) || '—' }}</span>
            <span class="dm-label">渠道分布</span>
          </div>
        </div>

        <div v-if="detail.topic.remark" class="detail-remark">运营备注：{{ detail.topic.remark }}</div>

        <div class="detail-section-title">归属问法（按提问次数倒序）</div>
        <el-table :data="detail.questions" size="small" aria-label="归属问法列表">
          <el-table-column label="问法" min-width="220">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ row.question }}</span>
                <span class="cell-hint">
                  相似度 {{ row.clusterScore == null ? '人工归集' : `${row.clusterScore}%` }} · 最近
                  {{ formatDateTime(row.lastAskTime) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="次数" width="70">
            <template #default="{ row }">
              <span class="cell-muted">{{ formatNumber(row.askCount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="94">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(VOC_QUESTION_STATUS_DICT, row.status)"
                :tone="dictTone(VOC_QUESTION_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <ZkEmpty size="sm" title="该主题下没有问法" hint="删除主题会解除归属，重新聚类后会再次归集" />
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotSquare,
  Collection,
  Histogram,
  MagicStick,
  Opportunity,
  Plus,
  Refresh,
  Search,
  Sort,
  Timer,
  TrendCharts
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  clusterVocTopics,
  createVocTopic,
  deleteVocTopic,
  getVocOverview,
  getVocTopic,
  getVocTopics,
  getVocWordCloud,
  mergeVocTopics,
  updateVocTopic,
  updateVocTopicStatus
} from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  VOC_CHANNEL_DICT,
  VOC_QUESTION_STATUS_DICT,
  VOC_TOPIC_STATUS_OPTIONS,
  VOC_TOPIC_TYPE_DICT,
  VOC_TOPIC_TYPE_OPTIONS,
  dictLabel,
  dictTone,
  formatDateTime,
  formatNumber,
  percent,
  shortDate
} from '@/utils/dict'

/* ---------- 概览 ---------- */
const overview = reactive({
  unmatchedTotal: 0,
  unmatchedPending: 0,
  clusteredCount: 0,
  clusterRate: 0,
  topicCount: 0,
  autoTopicCount: 0,
  manualTopicCount: 0,
  hotTopicCount: 0,
  channels: [],
  trend: [],
  lastClusterTime: null
})
const overviewLoading = ref(false)

const clusterRateText = computed(() => `${percent(Number(overview.clusterRate) || 0, 1)}%`)
const lastClusterText = computed(() =>
  overview.lastClusterTime ? formatDateTime(overview.lastClusterTime).slice(5, 16) : '尚未执行'
)

async function loadOverview() {
  overviewLoading.value = true
  try {
    const data = await getVocOverview()
    Object.assign(overview, {
      unmatchedTotal: Number(data?.unmatchedTotal ?? 0),
      unmatchedPending: Number(data?.unmatchedPending ?? 0),
      clusteredCount: Number(data?.clusteredCount ?? 0),
      clusterRate: Number(data?.clusterRate ?? 0),
      topicCount: Number(data?.topicCount ?? 0),
      autoTopicCount: Number(data?.autoTopicCount ?? 0),
      manualTopicCount: Number(data?.manualTopicCount ?? 0),
      hotTopicCount: Number(data?.hotTopicCount ?? 0),
      channels: Array.isArray(data?.channels) ? data.channels : [],
      trend: Array.isArray(data?.trend) ? data.trend : [],
      lastClusterTime: data?.lastClusterTime || null
    })
  } catch {
    // 概览失败不阻塞列表，指标保留上一次结果
  } finally {
    overviewLoading.value = false
  }
}

/* ---------- 词云 ---------- */
const cloud = ref({ questionCount: 0, totalWeight: 0, words: [] })
const cloudMax = computed(() => {
  const values = cloud.value.words.map((word) => Number(word.weight) || 0)
  return Math.max(1, ...values)
})

async function loadCloud() {
  try {
    const data = await getVocWordCloud({ limit: 60 })
    cloud.value = {
      questionCount: Number(data?.questionCount ?? 0),
      totalWeight: Number(data?.totalWeight ?? 0),
      words: Array.isArray(data?.words) ? data.words : []
    }
  } catch {
    cloud.value = { questionCount: 0, totalWeight: 0, words: [] }
  }
}

const cloudFont = (word) => {
  const ratio = (Number(word.weight) || 0) / cloudMax.value
  return `${(12 + 14 * Math.sqrt(ratio)).toFixed(1)}px`
}
const cloudOpacity = (word) => {
  const ratio = (Number(word.weight) || 0) / cloudMax.value
  return Math.max(0.45, Math.min(1, 0.45 + ratio * 0.55))
}

/* ---------- 渠道 / 趋势 ---------- */
const channelMax = computed(() => {
  const values = overview.channels.map((item) => Number(item.questionCount) || 0)
  return Math.max(1, ...values)
})
const channelWidth = (count) => `${Math.max(6, Math.round(((Number(count) || 0) / channelMax.value) * 100))}%`

const trendMax = computed(() => {
  const values = overview.trend.map((item) => Number(item.count) || 0)
  return Math.max(1, ...values)
})
const trendHeight = (count) => `${Math.max(3, Math.round(((Number(count) || 0) / trendMax.value) * 52))}px`

/* ---------- 主题列表 ---------- */
const { filters, rows, total, pageNum, pageSize, loading, loaded, error, load, search, changePage, changeSize } =
  usePagedList(getVocTopics, { topicType: '', status: '', keyword: '' })

const actionBusy = ref('')

function channelsText(channels) {
  if (!channels) return '—'
  return String(channels)
    .split(',')
    .map((item) => {
      const [code, count] = item.split(':')
      return `${dictLabel(VOC_CHANNEL_DICT, code, code)} ${count}`
    })
    .join(' · ')
}

/* ---------- 聚类 ---------- */
const clustering = ref(false)

async function runCluster() {
  clustering.value = true
  try {
    const data = await clusterVocTopics()
    if (data?.locked === false) {
      ElMessage.warning('另一轮聚类正在执行，请稍后再试')
    } else {
      ElMessage.success(
        `聚类完成：扫描 ${formatNumber(data?.scannedCount ?? 0)} 条 · 归入 ${formatNumber(
          data?.assignedCount ?? 0
        )} 条 · 新建主题 ${formatNumber(data?.createdCount ?? 0)} 个 · 耗时 ${formatNumber(data?.durationMs ?? 0)} ms`
      )
    }
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '聚类失败，请稍后重试')
  } finally {
    clustering.value = false
  }
}

function reloadAll() {
  loadOverview()
  loadCloud()
  load()
}

/* ---------- 新增 / 编辑 ---------- */
const formVisible = ref(false)
const formSaving = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ topicName: '', keywords: '', sampleQuestion: '', remark: '', sort: 0, status: 1 })
const formRules = {
  topicName: [
    { required: true, message: '请输入主题名称', trigger: 'blur' },
    { min: 2, max: 64, message: '主题名称长度须为 2-64 个字符', trigger: 'blur' }
  ]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { topicName: '', keywords: '', sampleQuestion: '', remark: '', sort: 0, status: 1 })
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    topicName: row.topicName || '',
    keywords: row.keywords || '',
    sampleQuestion: row.sampleQuestion || '',
    remark: row.remark || '',
    sort: Number(row.sort ?? 0),
    status: Number(row.status ?? 1)
  })
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  formSaving.value = true
  try {
    const payload = {
      topicName: form.topicName.trim(),
      keywords: form.keywords.trim() || null,
      sampleQuestion: form.sampleQuestion.trim() || null,
      remark: form.remark.trim() || null,
      sort: Number(form.sort) || 0,
      status: Number(form.status)
    }
    if (editingId.value) {
      await updateVocTopic(editingId.value, payload)
      ElMessage.success('主题已更新')
    } else {
      await createVocTopic(payload)
      ElMessage.success('主题已创建，可等待下一轮聚类自动归集问法')
    }
    formVisible.value = false
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    formSaving.value = false
  }
}

/* ---------- 启停 / 删除 ---------- */
async function toggleStatus(row) {
  const next = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `status-${row.id}`
  try {
    await updateVocTopicStatus(row.id, next)
    ElMessage.success(next === 1 ? '主题已启用' : '主题已停用，不再参与自动归类')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除主题「${row.topicName}」后，其下 ${formatNumber(row.questionCount)} 条问法将解除归属，下一轮聚类会重新归集。确认删除？`,
      '删除主题',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `delete-${row.id}`
  try {
    await deleteVocTopic(row.id)
    ElMessage.success('主题已删除')
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

/* ---------- 合并 ---------- */
const mergeVisible = ref(false)
const mergeSaving = ref(false)
const mergeTarget = ref(null)
const mergeSources = ref([])
const topicOptions = ref([])

const mergeSourceOptions = computed(() => topicOptions.value.filter((item) => item.id !== mergeTarget.value))

async function openMerge() {
  try {
    const data = await getVocTopics({ pageNum: 1, pageSize: 100 })
    topicOptions.value = Array.isArray(data?.list) ? data.list : []
  } catch {
    topicOptions.value = []
  }
  if (topicOptions.value.length < 2) {
    ElMessage.warning('至少需要两个主题才能合并')
    return
  }
  mergeTarget.value = topicOptions.value[0].id
  mergeSources.value = []
  mergeVisible.value = true
}

async function submitMerge() {
  if (!mergeTarget.value) {
    ElMessage.warning('请选择目标主题')
    return
  }
  const sources = mergeSources.value.filter((id) => id !== mergeTarget.value)
  if (!sources.length) {
    ElMessage.warning('请至少选择一个源主题')
    return
  }
  mergeSaving.value = true
  try {
    await mergeVocTopics({ targetTopicId: mergeTarget.value, sourceTopicIds: sources })
    ElMessage.success(`已合并 ${sources.length} 个主题`)
    mergeVisible.value = false
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '合并失败，请稍后重试')
  } finally {
    mergeSaving.value = false
  }
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const detail = ref({ topic: null, questions: [] })

async function openDetail(row) {
  try {
    const data = await getVocTopic(row.id)
    detail.value = {
      topic: data?.topic || null,
      questions: Array.isArray(data?.questions) ? data.questions : []
    }
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '主题详情加载失败')
  }
}

onMounted(reloadAll)
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 13px 14px 12px;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out);
}

.metric-card:hover {
  border-color: var(--zk-border-strong);
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-label {
  font-size: 12px;
  color: var(--zk-text-3);
}

.metric-icon {
  width: 22px;
  height: 22px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
}

.metric-value {
  display: flex;
  align-items: baseline;
  min-height: 30px;
  margin-top: 9px;
  font-size: 25px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.metric-value--sm {
  font-size: 17px;
  letter-spacing: 0;
}

.metric-value small {
  margin-left: 4px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-3);
}

.metric-foot {
  margin-top: 3px;
  font-size: 11px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card.indigo .metric-icon {
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}

.metric-card.amber .metric-icon {
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
}

.metric-card.teal .metric-icon {
  color: #15803d;
  background: #f0fdf4;
}

.metric-card.green .metric-icon {
  color: var(--zk-green);
  background: var(--zk-green-soft);
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 13px 14px 8px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.panel-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.cloud-body {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 12px;
  padding: 8px 14px 18px;
  line-height: 1.5;
}

.cloud-word {
  color: var(--zk-accent-hover);
  font-weight: 600;
  letter-spacing: 0.2px;
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out);
}

.cloud-word:hover {
  opacity: 1 !important;
}

.channel-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 4px 14px 10px;
}

.channel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.channel-num {
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.channel-bar {
  margin-top: 5px;
  height: 5px;
  border-radius: 999px;
  background: var(--zk-surface-3);
  overflow: hidden;
}

.channel-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--zk-accent);
}

.channel-empty {
  padding: 10px 14px 14px;
  font-size: 12px;
  color: var(--zk-text-3);
}

.trend-wrap {
  padding: 8px 14px 16px;
  border-top: 1px dashed var(--zk-border);
}

.trend-bars {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  align-items: end;
  gap: 6px;
  height: 74px;
}

.trend-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  height: 100%;
}

.trend-bar {
  width: 100%;
  max-width: 26px;
  border-radius: 3px 3px 2px 2px;
  background: linear-gradient(180deg, var(--zk-accent) 0%, var(--zk-accent-soft) 100%);
  transition: height var(--zk-dur-base) var(--zk-ease-out);
}

.trend-label {
  font-size: 10.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.filter-select {
  width: 126px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 1080px;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.cell-muted {
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.cell-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
  line-height: 1.5;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.merge-hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

.w-full {
  width: 100%;
}

.detail-body {
  padding: 4px 4px 12px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--zk-border);
}

.detail-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--zk-text);
}

.detail-sub {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;
}

.detail-metric {
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  padding: 9px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.dm-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dm-label {
  font-size: 11px;
  color: var(--zk-text-3);
}

.detail-remark {
  margin-bottom: 12px;
  padding: 9px 10px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-amber-soft);
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

.detail-section-title {
  margin: 6px 0 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text-2);
}

@media (max-width: 1080px) {
  .insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
