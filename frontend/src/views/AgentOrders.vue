<template>
  <div class="zk-page">
    <PageHeader
      title="工单工作台"
      description="处理分配给你的工单，或从待分配池主动领取；受理、记录进展、解决与重开都会写入流转日志。"
    >
      <template #actions>
        <el-button :icon="Plus" type="primary" @click="createVisible = true">新建工单</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <el-tabs v-model="activeTab" class="order-tabs" @tab-change="switchTab">
        <el-tab-pane name="mine">
          <template #label>
            <span class="tab-label">分配给我的</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="pool">
          <template #label>
            <span class="tab-label">待分配池</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <div class="zk-toolbar">
        <el-select
          v-if="activeTab === 'mine'"
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          aria-label="按状态筛选"
          @change="search"
        >
          <el-option
            v-for="option in ORDER_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.priority"
          placeholder="全部优先级"
          clearable
          aria-label="按优先级筛选"
          @change="search"
        >
          <el-option
            v-for="option in PRIORITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.category"
          placeholder="全部分类"
          clearable
          aria-label="按分类筛选"
          @change="search"
        >
          <el-option
            v-for="option in ORDER_CATEGORY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索工单号 / 标题"
          aria-label="搜索工单"
          :prefix-icon="Search"
          clearable
          class="toolbar-search"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ total }}</b> 条
        </span>
      </div>

      <div
        class="table-scroll"
        :class="{ 'table-result-fade': resultFade }"
        role="region"
        aria-label="工单列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="10" :cols="8" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="rows"
          row-key="id"
          aria-label="工单列表"
          :row-class-name="rowClassName"
        >
          <el-table-column label="工单号" width="172">
            <template #default="{ row }">
              <span class="order-no">{{ row.orderNo || row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="order-title order-link" @click="openOrder(row)">
                {{ row.title }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="98">
            <template #default="{ row }">
              <span class="order-cat">{{ row.category || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(PRIORITY_DICT, row.priority)"
                :tone="dictTone(PRIORITY_DICT, row.priority)"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(ORDER_STATUS_DICT, row.status)"
                :tone="dictTone(ORDER_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column label="SLA 截止" width="164">
            <template #default="{ row }">
              <div class="sla-cell" :class="{ overdue: isOverdue(row) }">
                <span class="sla-time">{{ formatDateTime(row.slaDeadline) }}</span>
                <span v-if="isOverdue(row)" class="sla-flag">SLA 已超时</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="152">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOrder(row)">
                {{ activeTab === 'pool' ? '领取/查看' : '查看' }}
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="
                error
                  ? '工单加载失败'
                  : hasFilter
                    ? '没有匹配的工单'
                    : activeTab === 'pool'
                      ? '待分配池已清空'
                      : '暂无分配给你的工单'
              "
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : hasFilter
                    ? '当前筛选条件下没有工单，试试清空筛选'
                    : activeTab === 'pool'
                      ? '所有工单都已有人处理，稍后再来看看'
                      : '可到待分配池主动领取工单'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="reset">清空筛选</el-button>
              <el-button v-else-if="activeTab === 'mine'" type="primary" @click="switchToPool">
                去待分配池
              </el-button>
              <el-button v-else @click="load">刷新列表</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>

      <div class="zk-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          aria-label="工单分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      待分配池中的工单可在详情抽屉内直接受理；受理后进入「分配给我的」，状态变为处理中
    </div>

    <OrderDrawer v-model="drawerVisible" :order-id="activeId" @changed="load" />
    <OrderCreateDialog v-model="createVisible" :session-id="fromSessionId" @created="onCreated" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import OrderDrawer from '@/components/OrderDrawer.vue'
import OrderCreateDialog from '@/components/OrderCreateDialog.vue'
import { getAgentOrders, getOrderPool } from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import {
  ORDER_CATEGORY_OPTIONS,
  ORDER_STATUS_DICT,
  ORDER_STATUS_OPTIONS,
  PRIORITY_DICT,
  PRIORITY_OPTIONS,
  dictLabel,
  dictTone,
  formatDateTime
} from '@/utils/dict'

const route = useRoute()
const fromSessionId = computed(() => route.query.sessionId || null)

const activeTab = ref('mine')
const filters = reactive({ status: '', priority: '', category: '', keyword: '' })
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const drawerVisible = ref(false)
const createVisible = ref(false)
const activeId = ref(null)

const hasFilter = computed(() =>
  Boolean(filters.status || filters.priority || filters.category || filters.keyword)
)

const isOverdue = (row) => row?.overdueFlag === true || Number(row?.overdueFlag) === 1
const rowClassName = ({ row }) => (isOverdue(row) ? 'row-overdue' : '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filters.keyword) params.keyword = filters.keyword.trim()
    if (filters.priority) params.priority = filters.priority
    if (filters.category) params.category = filters.category
    if (activeTab.value === 'mine' && filters.status) params.status = filters.status
    const data = activeTab.value === 'mine'
      ? await getAgentOrders(params)
      : await getOrderPool(params)
    rows.value = data?.list || []
    total.value = Number(data?.total || 0)
  } catch (e) {
    rows.value = []
    total.value = 0
    error.value = e?.response?.data?.message || '加载失败，请稍后重试'
  } finally {
    loaded.value = true
    loading.value = false
  }
}

function switchTab() {
  pageNum.value = 1
  load()
}

function switchToPool() {
  activeTab.value = 'pool'
  switchTab()
}

function search() {
  pageNum.value = 1
  load()
}

function reset() {
  filters.status = ''
  filters.priority = ''
  filters.category = ''
  filters.keyword = ''
  pageNum.value = 1
  load()
}

function changePage(page) {
  pageNum.value = page
  load()
}

function changeSize(size) {
  pageSize.value = size
  pageNum.value = 1
  load()
}

function openOrder(row) {
  activeId.value = row.id
  drawerVisible.value = true
}

function onCreated() {
  createVisible.value = false
  load()
}

/* 首屏骨架屏占位；切换页签与筛选后结果区 160ms 淡入 */
const resultFade = ref(false)
let resultFadeTimer = 0

watch(loading, (now, before) => {
  if (!before || now || prefersReducedMotion()) return
  resultFade.value = false
  requestAnimationFrame(() => {
    resultFade.value = true
    window.clearTimeout(resultFadeTimer)
    resultFadeTimer = window.setTimeout(() => {
      resultFade.value = false
    }, 200)
  })
})

onBeforeUnmount(() => window.clearTimeout(resultFadeTimer))

onMounted(load)
</script>

<style scoped>
.order-tabs {
  padding: 4px 20px 0;
  border-bottom: 1px solid var(--zk-border);
}

.order-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.order-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.tab-label {
  font-size: 13px;
  font-weight: 600;
}

.toolbar-search {
  width: 210px;
}

.order-no {
  display: inline-block;
  white-space: nowrap;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.order-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--zk-text);
}

.order-link {
  border: none;
  background: none;
  padding: 0;
  text-align: left;
  cursor: pointer;
  transition: color var(--zk-dur-fast) var(--zk-ease-out);
}

.order-link:hover {
  color: var(--zk-indigo);
}

.order-link:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: 2px;
  border-radius: 4px;
}

.order-cat {
  display: inline-block;
  font-size: 12px;
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  padding: 2px 7px;
  white-space: nowrap;
}

.sla-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sla-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.sla-cell.overdue .sla-time {
  color: var(--zk-red);
  font-weight: 600;
}

.sla-flag {
  font-size: 10.5px;
  font-weight: 700;
  color: var(--zk-red);
  letter-spacing: 0.3px;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  outline-offset: -2px;
}

.table-scroll:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: -2px;
}

.table-scroll :deep(.el-table) {
  min-width: 980px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.table-skeleton {
  padding: 12px 16px 6px;
}

/* 筛选结果淡入：160ms，只动位移与透明度 */
.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

/* SLA 超时行：进场时一次性高亮，不循环闪烁 */
.table-scroll :deep(.el-table__row.row-overdue > td.el-table__cell) {
  animation: zk-flash 600ms var(--zk-ease-out) 1;
}

@media (max-width: 768px) {
  .toolbar-search {
    width: 100%;
  }
}
</style>
