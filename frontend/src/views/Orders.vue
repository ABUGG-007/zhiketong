<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="WORK ORDERS"
      title="工单中心"
      description="按状态、优先级与关键词检索工单。SLA 截止时间已过的工单会在左侧标红，处理人展示当前受理坐席。"
      badge="演示数据"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable @change="search">
          <el-option
            v-for="option in ORDER_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="filters.priority" placeholder="全部优先级" clearable @change="search">
          <el-option
            v-for="option in PRIORITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索工单号 / 标题 / 分类"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta">共 <b>{{ total }}</b> 条工单</span>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        :row-class-name="rowClassName"
      >
        <el-table-column label="工单号" width="128">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="order-title">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="92">
          <template #default="{ row }">
            <span class="order-cat">{{ row.category || '—' }}</span>
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
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <StatusPill
              :label="dictLabel(ORDER_STATUS_DICT, row.status)"
              :tone="dictTone(ORDER_STATUS_DICT, row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="98">
          <template #default="{ row }">
            <span v-if="row.assigneeName" class="assignee">{{ row.assigneeName }}</span>
            <span v-else class="zk-muted">未分配</span>
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
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <div class="empty-icon"><el-icon><Tickets /></el-icon></div>
            <p>{{ error || '暂无匹配的工单' }}</p>
            <span v-if="!error" class="empty-hint">调整筛选条件后重试，或等待种子数据导入</span>
            <el-button v-else size="small" @click="load">重新加载</el-button>
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
      数据来源 GET /api/demo/workorders · 支持 status / priority / keyword 筛选 · 演示数据，后续里程碑完善
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
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

const isOverdue = (row) =>
  row?.overdueFlag === true || Number(row?.overdueFlag) === 1

const rowClassName = ({ row }) => (isOverdue(row) ? 'row-overdue' : '')

onMounted(load)
</script>

<style scoped>
.order-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: #475569;
}

.order-title {
  font-size: 13px;
  color: var(--zk-text);
  font-weight: 500;
}

.order-cat {
  font-size: 12px;
  color: #475569;
  background: #f4f6fb;
  border: 1px solid #e7ebf3;
  border-radius: 6px;
  padding: 2px 7px;
  white-space: nowrap;
}

.assignee {
  font-size: 12.5px;
  color: #1f2b3d;
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
  color: #be123c;
  font-weight: 600;
}

.sla-flag {
  font-size: 10.5px;
  font-weight: 700;
  color: #be123c;
  letter-spacing: 0.3px;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}
</style>
