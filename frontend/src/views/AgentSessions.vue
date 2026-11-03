<template>
  <div class="zk-page">
    <PageHeader
      title="我的会话"
      description="分配给你的会话都在这里。打开会话可查看完整历史记录并直接回复；结束后访客会收到评价邀请。"
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
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ total }}</b> 条会话
        </span>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="我的会话列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="pageSize" :cols="6" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="我的会话列表"
          :row-class-name="rowClassName"
          :class="fadeClass"
          @row-click="openSession"
        >
          <el-table-column label="会话号" width="164">
            <template #default="{ row }">
              <span class="session-no">{{ row.sessionNo || row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用户" width="120">
            <template #default="{ row }">
              <span class="person">{{ row.userName || '访客' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                :tone="dictTone(SESSION_STATUS_DICT, row.status)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="评分" width="124" align="right">
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
          <el-table-column label="对话数" width="88" align="right">
            <template #default="{ row }">
              <span class="zk-num msg-count">{{ row.messageCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="158">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="104" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openSession(row)">
                {{ row.status === 'HUMAN' ? '回复' : '详情' }}
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无本人会话'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : filters.status
                    ? '当前状态下没有会话'
                    : '暂无分配给你的会话；待分配会话可在「坐席工作台」接入'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else @click="resetFilters">查看全部会话</el-button>
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
          aria-label="我的会话分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      仅展示分配给本人的会话；回复会实时推送给访客，结束会话后访客可对本次服务评分
    </div>

    <SessionDrawer
      v-model="drawerVisible"
      :session-id="activeId"
      role="AGENT"
      @changed="load"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import SessionDrawer from '@/components/SessionDrawer.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { getAgentSessions } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  SESSION_STATUS_DICT,
  SESSION_STATUS_OPTIONS,
  dictLabel,
  dictTone,
  formatDateTime
} from '@/utils/dict'

// 坐席只能看到本人接待的会话（人工中 / 已结束），排队中由工作台负责接入
const statusOptions = SESSION_STATUS_OPTIONS.filter((option) =>
  ['HUMAN', 'CLOSED'].includes(option.value)
)

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
} = usePagedList(getAgentSessions, { status: '' })

// 筛选 / 翻页结果到达时重播 160ms 淡入（只淡不位移，避免表格重排）
const listTick = ref(0)
watch(rows, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const drawerVisible = ref(false)
const activeId = ref(null)

function rowClassName() {
  return 'is-clickable-row'
}

function openSession(row) {
  activeId.value = row.id
  drawerVisible.value = true
}

function resetFilters() {
  reset()
}

let refreshTimer = null

onMounted(() => {
  load()
  // 会话状态可能被访客或系统改变，30 秒静默刷新一次列表
  refreshTimer = window.setInterval(() => load(), 30000)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
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

.session-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.person {
  font-size: 12.5px;
  color: var(--zk-text);
}

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

.msg-count {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text-2);
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
  min-width: 860px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
</style>
