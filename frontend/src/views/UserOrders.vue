<template>
  <div class="zk-page">
    <PageHeader
      title="我的工单"
      description="查看你提交的问题处理进度；待受理、处理中、已解决一目了然，解决后可确认关闭并评价。"
    >
      <template #actions>
        <el-button :icon="Plus" type="primary" @click="createVisible = true">提交工单</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-select
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
        role="region"
        aria-label="我的工单列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="pageSize" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="我的工单列表"
          :row-class-name="rowClassName"
          :class="fadeClass"
        >
          <el-table-column label="工单号" width="140">
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
          <el-table-column label="处理人" width="100">
            <template #default="{ row }">
              <span v-if="row.assigneeName" class="assignee">{{ row.assigneeName }}</span>
              <span v-else class="zk-muted">待分配</span>
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
          <el-table-column label="操作" width="86" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOrder(row)">查看</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无本人工单'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : hasFilter
                    ? '当前筛选条件下没有工单'
                    : '遇到 AI 解决不了的问题，可以提交工单由人工跟进'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="reset">清空筛选</el-button>
              <el-button v-else type="primary" @click="createVisible = true">提交工单</el-button>
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
          aria-label="我的工单分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      工单状态会实时更新；已解决的工单可确认关闭并评价，如问题仍未解决可选择重开
    </div>

    <OrderDrawer v-model="drawerVisible" :order-id="activeId" @changed="load" />
    <OrderCreateDialog
      v-model="createVisible"
      :session-id="fromSessionId"
      :default-title="defaultTitle"
      :default-content="defaultContent"
      @created="onCreated"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import OrderDrawer from '@/components/OrderDrawer.vue'
import OrderCreateDialog from '@/components/OrderCreateDialog.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { getUserOrders } from '@/api'
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

const route = useRoute()
const fromSessionId = computed(() => route.query.sessionId || null)
const defaultTitle = computed(() => String(route.query.title || ''))
const defaultContent = computed(() => String(route.query.content || ''))
/** 用户中心指标卡跳转时携带状态筛选，进入即按该状态过滤 */
const initialStatus = String(route.query.status || '')
const initialPriority = String(route.query.priority || '')

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
} = usePagedList(getUserOrders, {
  status: initialStatus,
  priority: initialPriority,
  keyword: ''
})

// 筛选 / 翻页结果到达时重播 160ms 淡入（只淡不位移，避免表格重排）
const listTick = ref(0)
watch(rows, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const drawerVisible = ref(false)
const createVisible = ref(false)
const activeId = ref(null)

const hasFilter = computed(() => Boolean(filters.status || filters.priority || filters.keyword))
const isOverdue = (row) => row?.overdueFlag === true || Number(row?.overdueFlag) === 1
const rowClassName = ({ row }) => (isOverdue(row) ? 'row-overdue' : '')

function openOrder(row) {
  activeId.value = row.id
  drawerVisible.value = true
}

function onCreated() {
  createVisible.value = false
  load()
}

onMounted(() => {
  load()
  // 从在线咨询带着会话与问题、或从用户中心点击「提交工单」跳转过来时，直接打开建单表单
  if (
    fromSessionId.value ||
    defaultTitle.value ||
    defaultContent.value ||
    route.query.create === '1'
  ) {
    createVisible.value = true
  }
})
</script>

<style scoped>
/* 首屏骨架与真实表格同列，加载前后不跳 */
.table-skeleton {
  padding: 12px 14px;
}

/* 筛选结果淡入 160ms：只淡不位移，避免表格重新布局 */
.list-fade-a {
  animation: zk-fade-a 160ms var(--zk-ease-out);
}

.list-fade-b {
  animation: zk-fade-b 160ms var(--zk-ease-out);
}

@keyframes zk-fade-a {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes zk-fade-b {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.toolbar-search {
  width: 210px;
}

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

.assignee {
  font-size: 12.5px;
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
  min-width: 960px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

@media (max-width: 768px) {
  .toolbar-search {
    width: 100%;
  }
}
</style>
