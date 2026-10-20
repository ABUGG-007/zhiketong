<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="SESSIONS"
      title="会话管理"
      description="查看 AI 接待、人工排队、人工中与已结束的会话。可点击行或使用「查看」按钮打开会话生命周期时间线。"
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
        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="会话列表"
          :row-class-name="rowClassName"
          @row-click="openDetail"
        >
          <el-table-column label="会话号" width="132">
            <template #default="{ row }">
              <span class="order-no">{{ row.sessionNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="104">
            <template #default="{ row }">
              <StatusPill
                :label="dictLabel(SESSION_STATUS_DICT, row.status)"
                :tone="dictTone(SESSION_STATUS_DICT, row.status)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="用户" width="92" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="person">{{ row.userName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="接待坐席" width="96">
            <template #default="{ row }">
              <span v-if="row.agentName" class="person">{{ row.agentName }}</span>
              <span v-else class="zk-muted">AI / 未分配</span>
            </template>
          </el-table-column>
          <el-table-column label="消息数" width="78" align="right">
            <template #default="{ row }">
              <span class="zk-num msg-count">{{ row.messageCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="评分" width="112" align="right">
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
          <el-table-column label="排队进入" width="140">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.queueEnterTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结束时间" width="140">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.endTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="140">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="76">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                class="view-btn"
                :aria-label="`查看会话 ${row.sessionNo} 的详情`"
                @click.stop="openDetail(row)"
              >
                查看
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><ChatLineSquare /></el-icon>
              </div>
              <p>{{ error || '暂无匹配的会话' }}</p>
              <span v-if="error" class="empty-hint">请确认后端 /api/console/sessions 已就绪</span>
              <span v-else-if="filters.status" class="empty-hint">当前状态下没有会话，试试其他状态</span>
              <span v-else class="empty-hint">还没有会话，等待真实业务产生</span>
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="filters.status" @click="reset">清空筛选条件</el-button>
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
          aria-label="会话分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/console/sessions · 支持 status 筛选 · 只读过渡接口，正式接口按 M1～M6 替换
    </div>

    <el-drawer v-model="detailVisible" title="会话详情" size="440px">
      <div v-if="activeSession" class="session-detail">
        <div class="detail-head">
          <div class="detail-no">{{ activeSession.sessionNo }}</div>
          <StatusPill
            :label="dictLabel(SESSION_STATUS_DICT, activeSession.status)"
            :tone="dictTone(SESSION_STATUS_DICT, activeSession.status)"
            dot
          />
        </div>

        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">用户</span>
            <span class="detail-value">{{ activeSession.userName || '—' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">接待坐席</span>
            <span class="detail-value">{{ activeSession.agentName || 'AI / 未分配' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">消息数</span>
            <span class="detail-value zk-num">{{ activeSession.messageCount ?? 0 }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">满意度评分</span>
            <span class="detail-value">
              <span
                v-if="Number(activeSession.rating) > 0"
                class="rating-cell"
                role="img"
                :aria-label="`满意度评分 ${activeSession.rating} 分，满分 5 分`"
              >
                <span class="stars" aria-hidden="true">
                  <span
                    v-for="n in 5"
                    :key="n"
                    :class="{ 'empty-star': n > Number(activeSession.rating) }"
                  >★</span>
                </span>
                <span class="rating-num zk-num" aria-hidden="true">{{ activeSession.rating }}</span>
              </span>
              <span v-else class="zk-muted">未评价</span>
            </span>
          </div>
        </div>

        <div class="timeline-block">
          <div class="detail-label">生命周期</div>
          <div class="timeline">
            <div
              v-for="step in timeline"
              :key="step.key"
              class="timeline-item"
              :class="{ done: step.time, pending: !step.time }"
            >
              <span class="timeline-dot" aria-hidden="true" />
              <div class="timeline-body">
                <div class="timeline-label">{{ step.label }}</div>
                <div class="timeline-time">
                  {{ step.time ? formatDateTime(step.time) : '未发生' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChatLineSquare, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
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
  error,
  load,
  search,
  reset,
  changePage,
  changeSize
} = usePagedList(getSessions, { status: '' })

const detailVisible = ref(false)
const activeSession = ref(null)

const timeline = computed(() => {
  const session = activeSession.value || {}
  return [
    { key: 'create', label: '会话创建', time: session.createTime },
    { key: 'queue', label: '进入排队', time: session.queueEnterTime },
    { key: 'accept', label: '坐席接入', time: session.acceptTime },
    { key: 'end', label: '会话结束', time: session.endTime }
  ]
})

function rowClassName() {
  return 'is-clickable-row'
}

function openDetail(row) {
  activeSession.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
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
  min-width: 1110px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

/* 抽屉 */
.session-detail {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--zk-border);
}

.detail-no {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--zk-text);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.detail-label {
  font-size: 11px;
  color: var(--zk-text-3);
  letter-spacing: 0.4px;
}

.detail-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.timeline-block {
  border-top: 1px dashed var(--zk-border);
  padding-top: 18px;
}

.timeline {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 18px 0;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 1px;
  background: var(--zk-border);
}

.timeline-dot {
  width: 11px;
  height: 11px;
  flex: 0 0 11px;
  border-radius: 50%;
  margin-top: 3px;
  border: 2px solid var(--zk-border-strong);
  background: var(--zk-surface);
}

.timeline-item.done .timeline-dot {
  border-color: var(--zk-teal);
  background: var(--zk-teal);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.14);
}

.timeline-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.timeline-item.pending .timeline-label {
  color: var(--zk-text-3);
  font-weight: 500;
}

.timeline-time {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}
</style>
