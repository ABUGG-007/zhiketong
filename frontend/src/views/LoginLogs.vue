<template>
  <div class="zk-page">
    <PageHeader
      title="登录日志"
      description="登录成功与失败记录，可按账号/IP、结果与日期范围筛选。失败记录会注明原因。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <label for="log-search" class="sr-only">搜索登录日志</label>
        <el-input
          id="log-search"
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索账号 / IP"
          aria-label="搜索账号或 IP"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="filters.success"
          placeholder="全部结果"
          clearable
          aria-label="按登录结果筛选"
          @change="search"
        >
          <el-option label="登录成功" :value="1" />
          <el-option label="登录失败" :value="0" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          class="log-date"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          aria-label="按登录日期范围筛选"
          unlink-panels
          @change="handleDateChange"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetAll">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">共 <b class="zk-num">{{ total }}</b> 条记录</span>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="登录日志列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="pageSize" :cols="6" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="登录日志列表"
          class="rows-stagger"
          :class="fadeClass"
        >
          <el-table-column label="登录时间" width="158">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="账号" width="126">
            <template #default="{ row }">
              <span class="account">{{ row.username || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来源 IP" width="132">
            <template #default="{ row }">
              <span class="mono-cell">{{ row.ip || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.success) === 1 ? '成功' : '失败'"
                :tone="Number(row.success) === 1 ? 'teal' : 'red'"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="message-text">{{ row.message || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="客户端 User-Agent" min-width="220">
            <template #default="{ row }">
              <el-tooltip
                :content="row.userAgent || '无客户端信息'"
                placement="top"
                :show-after="200"
              >
                <span class="ua-text">{{ row.userAgent || '—' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无匹配的登录记录'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : hasFilter
                    ? '当前筛选条件下没有日志，试试放宽日期或结果'
                    : '还没有登录日志，登录一次后即可看到记录'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="resetAll">清空筛选条件</el-button>
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
          aria-label="登录日志分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      记录每次登录的账号、来源 IP、客户端与结果；失败记录连续出现时建议及时检查账号安全
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { getLoginLogs } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime } from '@/utils/dict'

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
} = usePagedList(getLoginLogs, { keyword: '', success: '', startDate: '', endDate: '' })

// 筛选 / 翻页结果到达时重播 160ms 淡入（只淡不位移，避免表格重排）
const listTick = ref(0)
watch(rows, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const dateRange = ref([])

const hasFilter = computed(
  () =>
    Boolean(filters.keyword) ||
    filters.success === 0 ||
    filters.success === 1 ||
    Boolean(filters.startDate) ||
    Boolean(filters.endDate)
)

function handleDateChange(value) {
  if (Array.isArray(value) && value.length === 2) {
    filters.startDate = value[0] || ''
    filters.endDate = value[1] || ''
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  search()
}

function resetAll() {
  dateRange.value = []
  reset()
}

onMounted(load)
</script>

<style scoped>
/* 首屏骨架与真实表格同列，加载前后不跳 */
.table-skeleton {
  padding: 12px 14px;
}

/* 首屏前 10 行按 30ms 依次浮现（只淡不位移，不打断扫读） */
.rows-stagger :deep(.el-table__body tr:nth-child(-n + 10)) {
  animation: zk-row-in 160ms var(--zk-ease-out) both;
}

.rows-stagger :deep(.el-table__body tr:nth-child(2)) {
  animation-delay: 30ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(3)) {
  animation-delay: 60ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(4)) {
  animation-delay: 90ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(5)) {
  animation-delay: 120ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(6)) {
  animation-delay: 150ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(7)) {
  animation-delay: 180ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(8)) {
  animation-delay: 210ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(9)) {
  animation-delay: 240ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(10)) {
  animation-delay: 270ms;
}

@keyframes zk-row-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 筛选结果淡入 160ms：只淡不位移 */
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

.log-date {
  width: 272px;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.account {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.mono-cell {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.message-text {
  font-size: 12px;
  color: var(--zk-text-2);
}

.ua-text {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11.5px;
  color: var(--zk-text-3);
  cursor: help;
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
  .log-date {
    width: 100%;
  }
}
</style>
