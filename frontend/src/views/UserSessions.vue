<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="MY SESSIONS"
      title="我的会话"
      description="仅展示本人发起的会话。会话数据由 M3 会话模块产生，当前按创建时间倒序分页展示。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
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
        <el-table v-loading="loading" :data="rows" row-key="id" aria-label="我的会话列表">
          <el-table-column label="会话号" width="164">
            <template #default="{ row }">
              <span class="session-no">{{ row.sessionNo || row.id }}</span>
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
          <el-table-column label="评分" width="140" align="right">
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
          <el-table-column label="消息数" width="96" align="right">
            <template #default="{ row }">
              <span class="zk-num msg-count">{{ row.messageCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="166">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><ChatLineSquare /></el-icon>
              </div>
              <p>{{ error || '暂无本人会话' }}</p>
              <span v-if="error" class="empty-hint">请确认后端 /api/user/sessions 已就绪</span>
              <span v-else class="empty-hint">暂无本人会话，M3 产生数据后展示</span>
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
          aria-label="我的会话分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/user/sessions · 仅展示本人数据 · M3 会话模块产生数据后展示
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { ChatLineSquare, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getUserSessions } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  SESSION_STATUS_DICT,
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
} = usePagedList(getUserSessions, {})

onMounted(load)
</script>

<style scoped>
.session-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
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
  min-width: 720px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
</style>
