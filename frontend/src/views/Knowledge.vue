<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="KNOWLEDGE BASE"
      title="知识库"
      description="标准问答条目维护视图。AI 接待时按关键词与语义匹配，命中即直接回复；点击行可查看完整答案。"
      badge="演示数据"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <!-- 分类概览（/api/demo/categories） -->
      <div class="cat-strip">
        <span class="cat-strip-label">分类概览</span>
        <span class="cat-chip is-total">全部条目 <b>{{ allEntries }}</b></span>
        <span v-for="cat in categories" :key="cat.id" class="cat-chip">
          {{ cat.name }}
          <b>{{ cat.entryCount ?? 0 }}</b>
        </span>
        <span v-if="!categories.length" class="cat-strip-label">（分类接口未返回数据）</span>
      </div>

      <!-- 筛选 -->
      <div class="zk-toolbar">
        <el-input
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索问题 / 答案 / 关键词"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta">共 <b>{{ total }}</b> 条知识条目</span>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        class="kb-table"
        @row-click="openDetail"
      >
        <el-table-column label="分类" width="118">
          <template #default="{ row }">
            <span class="cat-name">{{ row.categoryName || '未分类' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="问题 / 答案" min-width="240">
          <template #default="{ row }">
            <div class="cell-question" :title="row.question">{{ row.question }}</div>
            <div class="cell-answer" :title="row.answer">{{ row.answer }}</div>
          </template>
        </el-table-column>
        <el-table-column label="关键词" width="210">
          <template #default="{ row }">
            <div class="kw-list">
              <span
                v-for="kw in splitKeywords(row.keywords).slice(0, 3)"
                :key="kw"
                class="kw-tag"
              >
                {{ kw }}
              </span>
              <span v-if="splitKeywords(row.keywords).length > 3" class="kw-more">
                +{{ splitKeywords(row.keywords).length - 3 }}
              </span>
              <span v-if="!splitKeywords(row.keywords).length" class="zk-muted">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="命中次数" width="112">
          <template #default="{ row }">
            <div class="hit-cell">
              <span class="hit-num">{{ row.hitCount ?? 0 }}</span>
              <span class="hit-bar">
                <i :style="{ width: hitPercent(row.hitCount) }" />
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <StatusPill
              :label="Number(row.status) === 1 ? '启用' : '停用'"
              :tone="Number(row.status) === 1 ? 'teal' : 'slate'"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.updateTime) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <div class="empty-icon"><el-icon><Collection /></el-icon></div>
            <p>{{ error || '暂无匹配的知识条目' }}</p>
            <span v-if="!error" class="empty-hint">试试更换关键词，或检查种子数据是否已导入</span>
            <el-button v-else size="small" @click="reload">重新加载</el-button>
          </div>
        </template>
      </el-table>

      <div class="zk-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i />
      数据来源 GET /api/demo/kb/entries · 分类来自 GET /api/demo/categories · 演示数据，后续里程碑完善
    </div>

    <!-- 答案详情 -->
    <el-drawer v-model="detailVisible" title="知识条目详情" size="480px">
      <div v-if="activeEntry" class="detail">
        <div class="detail-row">
          <span class="detail-label">分类</span>
          <span class="cat-name">{{ activeEntry.categoryName || '未分类' }}</span>
        </div>
        <div class="detail-block">
          <div class="detail-label">问题</div>
          <div class="detail-question">{{ activeEntry.question }}</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">标准答案</div>
          <div class="detail-answer">{{ activeEntry.answer }}</div>
        </div>
        <div class="detail-row">
          <span class="detail-label">关键词</span>
          <div class="kw-list">
            <span v-for="kw in splitKeywords(activeEntry.keywords)" :key="kw" class="kw-tag">
              {{ kw }}
            </span>
            <span v-if="!splitKeywords(activeEntry.keywords).length" class="zk-muted">—</span>
          </div>
        </div>
        <div class="detail-grid">
          <div>
            <div class="detail-label">命中次数</div>
            <div class="detail-num">{{ activeEntry.hitCount ?? 0 }}</div>
          </div>
          <div>
            <div class="detail-label">状态</div>
            <StatusPill
              :label="Number(activeEntry.status) === 1 ? '启用' : '停用'"
              :tone="Number(activeEntry.status) === 1 ? 'teal' : 'slate'"
            />
          </div>
          <div>
            <div class="detail-label">更新时间</div>
            <div class="detail-time">{{ formatDateTime(activeEntry.updateTime) }}</div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Collection, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getCategories, getKbEntries } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime } from '@/utils/dict'

const {
  filters,
  rows,
  total,
  pageNum,
  pageSize,
  loading,
  error,
  load,
  search,
  reset,
  changePage,
  changeSize
} = usePagedList(getKbEntries, { keyword: '' })

const categories = ref([])
const detailVisible = ref(false)
const activeEntry = ref(null)

const allEntries = computed(() => {
  const sum = categories.value.reduce((acc, cat) => acc + (Number(cat.entryCount) || 0), 0)
  return sum || total.value
})

const maxHits = computed(() =>
  Math.max(1, ...rows.value.map((row) => Number(row.hitCount) || 0))
)

function hitPercent(hit) {
  const value = Number(hit) || 0
  return `${Math.max(value > 0 ? 8 : 0, Math.round((value / maxHits.value) * 100))}%`
}

function splitKeywords(keywords) {
  if (!keywords) return []
  return String(keywords)
    .split(/[,，;；\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function openDetail(row) {
  activeEntry.value = row
  detailVisible.value = true
}

async function loadCategories() {
  try {
    const data = await getCategories()
    categories.value = Array.isArray(data) ? data : []
  } catch {
    categories.value = []
  }
}

function reload() {
  load()
  loadCategories()
}

onMounted(reload)
</script>

<style scoped>
.cat-name {
  font-size: 12.5px;
  color: #475569;
  font-weight: 500;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.kb-table :deep(.el-table__row) {
  cursor: pointer;
}

/* 抽屉详情 */
.detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-label {
  font-size: 11px;
  color: var(--zk-text-3);
  letter-spacing: 0.4px;
  margin-bottom: 6px;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-question {
  font-size: 15px;
  font-weight: 600;
  color: var(--zk-text);
  line-height: 1.6;
}

.detail-answer {
  font-size: 13px;
  line-height: 1.9;
  color: var(--zk-text-2);
  background: #f8fafd;
  border: 1px solid var(--zk-border);
  border-radius: 10px;
  padding: 12px 14px;
  white-space: pre-wrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  padding-top: 16px;
  border-top: 1px dashed var(--zk-border);
}

.detail-num {
  font-size: 20px;
  font-weight: 700;
  color: #0f766e;
  font-variant-numeric: tabular-nums;
}

.detail-time {
  font-size: 12.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}
</style>
