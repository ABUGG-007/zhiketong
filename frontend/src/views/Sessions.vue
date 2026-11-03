<template>
  <div class="zk-page">
    <PageHeader
      title="会话管理"
      description="查看 AI 接待、人工排队、人工中与已结束的会话。点击行或「详情」可打开会话记录与生命周期时间线。"
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
          aria-label="按会话状态筛选"
          @change="search"
        >
          <el-option
            v-for="option in SESSION_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">共 <b class="zk-num">{{ total }}</b> 条会话</span>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="会话列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="pageSize" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="会话列表"
          :row-class-name="rowClassName"
          :class="fadeClass"
          @row-click="openDetail"
        >
          <el-table-column label="会话号" min-width="148" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="order-no">{{ row.sessionNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                :tone="dictTone(SESSION_STATUS_DICT, row.status)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="用户" min-width="108" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="person">{{ row.userName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="接待坐席" min-width="112" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.agentName" class="person">{{ row.agentName }}</span>
              <span v-else class="zk-muted">AI / 未分配</span>
            </template>
          </el-table-column>
          <el-table-column label="对话数" min-width="82" align="right">
            <template #default="{ row }">
              <span class="zk-num msg-count">{{ row.messageCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="评分" min-width="118" align="right">
            <template #default="{ row }">
              <span
                v-if="Number(row.rating) > 0"
                class="rating-cell"
                role="img"
                :aria-label="`满意度评分 ${row.rating} 分，满分 5 分`"
              >
                <span class="stars" aria-hidden="true">
                  <span
                    v-for="n in 5"
                    :key="n"
                    :class="{ 'empty-star': n > Number(row.rating) }"
                  >★</span>
                </span>
                <span class="rating-num zk-num" aria-hidden="true">{{ row.rating }}</span>
              </span>
              <span v-else class="zk-muted">未评价</span>
            </template>
          </el-table-column>
          <el-table-column label="排队进入" min-width="156" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.queueEnterTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="156" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.endTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="156" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                class="view-btn"
                :aria-label="`查看会话 ${row.sessionNo} 的详情`"
                @click.stop="openDetail(row)"
              >
                详情
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无匹配的会话'"
              :hint="
                error
                  ? '请稍后重试，或刷新页面后重新加载'
                  : filters.status
                    ? '当前状态下没有会话，试试其他状态'
                    : '还没有会话，等待真实业务产生'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="filters.status" @click="reset">清空筛选条件</el-button>
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
          aria-label="会话分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      管理员可查看全量会话；「对话数」仅统计用户、AI 与坐席消息，转人工、接入等系统提示不计入
    </div>

    <SessionDrawer v-model="detailVisible" :session-id="activeId" role="ADMIN" @changed="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SessionDrawer from '@/components/SessionDrawer.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { getSessions } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  SESSION_STATUS_DICT,
  SESSION_STATUS_OPTIONS,
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
} = usePagedList(getSessions, { status: '' })

// 筛选 / 翻页结果到达时重播 160ms 淡入（只淡不位移，避免表格重排）
const listTick = ref(0)
watch(rows, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const detailVisible = ref(false)
const activeId = ref(null)

function rowClassName() {
  return 'is-clickable-row'
}

function openDetail(row) {
  activeId.value = row.id
  detailVisible.value = true
}

onMounted(load)
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

.order-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.person {
  font-size: 12.5px;
  color: var(--zk-text);
}

.msg-count {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text-2);
}

/* 评分：星星 + 数字，右侧对齐 */
.rating-cell {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.rating-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-amber);
}

.view-btn {
  min-height: 32px;
  padding: 0 8px;
  font-size: 12.5px;
  font-weight: 600;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
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
  min-width: 1216px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
</style>
