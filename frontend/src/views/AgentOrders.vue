<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="MY ORDERS"
      title="我的工单"
      description="仅展示本人受理的工单。工单数据由 M5 工单模块产生，SLA 截止时间已过的工单会标红提示。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ total }}</b> 条工单
        </span>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="我的工单列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="我的工单列表"
          :row-class-name="rowClassName"
        >
          <el-table-column label="工单号" width="150">
            <template #default="{ row }">
              <span class="order-no">{{ row.orderNo || row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="order-title">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="98">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(PRIORITY_DICT, row.priority)"
                :tone="dictTone(PRIORITY_DICT, row.priority)"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="106">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(ORDER_STATUS_DICT, row.status)"
                :tone="dictTone(ORDER_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column label="SLA 截止" width="172">
            <template #default="{ row }">
              <div class="sla-cell" :class="{ overdue: isOverdue(row) }">
                <span class="sla-time">{{ formatDateTime(row.slaDeadline) }}</span>
                <span v-if="isOverdue(row)" class="sla-flag">SLA 已超时</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><Tickets /></el-icon>
              </div>
              <p>{{ error || '暂无本人工单' }}</p>
              <span v-if="error" class="empty-hint">请确认后端 /api/agent/orders 已就绪</span>
              <span v-else class="empty-hint">暂无本人工单，M5 产生数据后展示</span>
              <el-button v-if="error" @click="load">重新加载</el-button>
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
          aria-label="我的工单分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/agent/orders · 仅展示本人受理的工单 · M5 工单模块产生数据后展示
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Refresh, Tickets } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getAgentOrders } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  ORDER_STATUS_DICT,
  PRIORITY_DICT,
  dictLabel,
  dictTone,
  formatDateTime
} from '@/utils/dict'

const {
  rows,
  total,
  pageNum,
  pageSize,
  loading,
  error,
  load,
  changePage,
  changeSize
} = usePagedList(getAgentOrders, {})

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
  border-radius: 0 0 var(--zk-radius-lg) var(--zk-radius-lg);
  outline-offset: -2px;
}

.table-scroll:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: -2px;
}

.table-scroll :deep(.el-table) {
  min-width: 900px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
</style>
