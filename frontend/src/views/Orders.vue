<template>
  <div class="zk-page">
    <PageHeader
      title="工单中心"
      description="从创建、分配、处理到关闭的完整流转记录；SLA 即将超时的工单会被标记，点击标题查看详情与可执行操作。"
    >
      <template #actions>
        <el-button :icon="Plus" type="primary" @click="createVisible = true">新建工单</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          aria-label="按工单状态筛选"
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
          aria-label="按问题分类筛选"
          @change="search"
        >
          <el-option
            v-for="option in ORDER_CATEGORY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-checkbox
          v-model="filters.overdueOnly"
          true-value="true"
          false-value=""
          @change="search"
        >
          仅看超时
        </el-checkbox>
        <label for="order-search" class="sr-only">搜索工单</label>
        <el-input
          id="order-search"
          v-model="filters.keyword"
          placeholder="搜索工单号 / 标题"
          aria-label="搜索工单"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">共 <b class="zk-num">{{ total }}</b> 条工单</span>
      </div>

      <div
        class="table-scroll"
        :class="{ 'table-result-fade': resultFade }"
        role="region"
        aria-label="工单列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="10" :cols="9" class="table-skeleton" />
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
              <span class="order-no">{{ row.orderNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="190" show-overflow-tooltip>
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
          <el-table-column label="处理人" width="94">
            <template #default="{ row }">
              <span v-if="row.assigneeName" class="assignee">{{ row.assigneeName }}</span>
              <span v-else class="zk-muted">未分配</span>
            </template>
          </el-table-column>
          <el-table-column label="SLA 截止" width="158">
            <template #default="{ row }">
              <div class="sla-cell" :class="{ overdue: isOverdue(row) }">
                <span class="sla-time">{{ formatDateTime(row.slaDeadline) }}</span>
                <span v-if="isOverdue(row)" class="sla-flag">SLA 已超时</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="146">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="86" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOrder(row)">详情</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '工单加载失败' : hasFilter ? '没有匹配的工单' : '还没有工单'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : hasFilter
                    ? '当前筛选条件下没有工单，试试放宽条件'
                    : '点击右上角「新建工单」创建第一条工单'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="reset">清空筛选条件</el-button>
              <el-button v-else type="primary" @click="createVisible = true">新建工单</el-button>
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
      工单流转遵循固定状态机：待分配 → 已分配 → 处理中 → 已解决 → 已关闭；已解决/已关闭可重开，每次变更均记录流转日志
    </div>

    <OrderDrawer v-model="drawerVisible" :order-id="activeId" @changed="load" />
    <OrderCreateDialog v-model="createVisible" @created="onCreated" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import OrderDrawer from '@/components/OrderDrawer.vue'
import OrderCreateDialog from '@/components/OrderCreateDialog.vue'
import { getWorkOrders } from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import { usePagedList } from '@/composables/usePagedList'
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

const {
  filters,
  rows,
  total,
  pageNum,
  pageSize,
  loading,
  loaded,
  error,
  load,
  search,
  reset,
  changePage,
  changeSize
} = usePagedList(getWorkOrders, {
  status: '',
  priority: '',
  category: '',
  overdueOnly: '',
  keyword: ''
})

const drawerVisible = ref(false)
const createVisible = ref(false)
const activeId = ref(null)

function openOrder(row) {
  activeId.value = row.id
  drawerVisible.value = true
}

function onCreated() {
  createVisible.value = false
  load()
}

const hasFilter = computed(() =>
  Boolean(filters.status || filters.priority || filters.category || filters.overdueOnly || filters.keyword)
)

const isOverdue = (row) =>
  row?.overdueFlag === true || Number(row?.overdueFlag) === 1

const rowClassName = ({ row }) => (isOverdue(row) ? 'row-overdue' : '')

/* 首屏骨架屏占位；筛选/翻页后结果区 160ms 淡入，表格不做逐行错开 */
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

.assignee {
  font-size: 12.5px;
  color: var(--zk-text);
}

/* SLA 截止：文字 + 标记，不单靠颜色传达状态 */
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

/* 表格横向滚动：窄屏滚动而不是挤压列宽 */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: 0 0 var(--zk-radius-lg) var(--zk-radius-lg);
  outline-offset: -2px;
}

.table-scroll:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: -2px;
}

.table-scroll :deep(.el-table) {
  min-width: 1000px;
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
</style>
