<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="WORK ORDERS"
      title="工单中心"
      description="按状态、优先级与关键词检索工单。SLA 截止时间已过的工单会在左侧标红，处理人展示当前受理坐席。"
    >
      <template #actions>
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
        <label for="order-search" class="sr-only">搜索工单</label>
        <el-input
          id="order-search"
          v-model="filters.keyword"
          placeholder="搜索工单号 / 标题 / 分类"
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
        role="region"
        aria-label="工单列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="工单列表"
          :row-class-name="rowClassName"
        >
          <el-table-column label="工单号" width="124">
            <template #default="{ row }">
              <span class="order-no">{{ row.orderNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="order-title">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="88">
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

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><Tickets /></el-icon>
              </div>
              <p>{{ error || '暂无匹配的工单' }}</p>
              <span v-if="error" class="empty-hint">请确认后端 /api/console/workorders 已就绪</span>
              <span v-else-if="hasFilter" class="empty-hint">当前筛选条件下没有工单，试试放宽条件</span>
              <span v-else class="empty-hint">还没有工单，等待真实业务产生</span>
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="reset">清空筛选条件</el-button>
              <el-button v-else @click="load">刷新列表</el-button>
            </div>
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
      数据来源 GET /api/console/workorders · 支持 status / priority / keyword 筛选 · 只读过渡接口，正式接口按 M1～M6 替换
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { Refresh, Search, Tickets } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getWorkOrders } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
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
  error,
  load,
  search,
  reset,
  changePage,
  changeSize
} = usePagedList(getWorkOrders, { status: '', priority: '', keyword: '' })

const hasFilter = computed(() =>
  Boolean(filters.status || filters.priority || filters.keyword)
)

const isOverdue = (row) =>
  row?.overdueFlag === true || Number(row?.overdueFlag) === 1

const rowClassName = ({ row }) => (isOverdue(row) ? 'row-overdue' : '')

onMounted(load)
</script>

<style scoped>
.order-no {
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

.order-cat {
  display: inline-block;
  font-size: 12px;
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  border-radius: 6px;
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
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
</style>
