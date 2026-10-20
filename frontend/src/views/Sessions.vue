<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="SESSIONS"
      title="会话管理"
      description="查看 AI 接待、人工排队、人工中与已结束的会话。点击任意行可查看会话生命周期时间线。"
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
            v-for="option in SESSION_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta">共 <b>{{ total }}</b> 条会话</span>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        class="session-table"
        @row-click="openDetail"
      >
        <el-table-column label="会话号" width="142">
          <template #default="{ row }">
            <span class="order-no">{{ row.sessionNo }}</span>
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
        <el-table-column label="用户" width="104" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="person">{{ row.userName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接待坐席" width="104">
          <template #default="{ row }">
            <span v-if="row.agentName" class="person">{{ row.agentName }}</span>
            <span v-else class="zk-muted">AI / 未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="消息数" width="86" align="right">
          <template #default="{ row }">
            <span class="zk-num msg-count">{{ row.messageCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="132">
          <template #default="{ row }">
            <span v-if="Number(row.rating) > 0" class="stars">
              <span
                v-for="n in 5"
                :key="n"
                :class="{ 'empty-star': n > Number(row.rating) }"
              >★</span>
            </span>
            <span v-else class="zk-muted">未评价</span>
          </template>
        </el-table-column>
        <el-table-column label="排队进入" width="154">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.queueEnterTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="154">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="154">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <div class="empty-icon"><el-icon><ChatLineSquare /></el-icon></div>
            <p>{{ error || '暂无匹配的会话' }}</p>
            <span v-if="!error" class="empty-hint">切换状态筛选，或等待种子会话数据导入</span>
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
      数据来源 GET /api/demo/sessions · 支持 status 筛选 · 演示数据，后续里程碑完善
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
              <span v-if="Number(activeSession.rating) > 0" class="stars">
                <span
                  v-for="n in 5"
                  :key="n"
                  :class="{ 'empty-star': n > Number(activeSession.rating) }"
                >★</span>
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
              <span class="timeline-dot" />
              <div class="timeline-body">
                <div class="timeline-label">{{ step.label }}</div>
                <div class="timeline-time">{{ step.time ? formatDateTime(step.time) : '未发生' }}</div>
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
  color: #475569;
}

.person {
  font-size: 12.5px;
  color: #1f2b3d;
}

.msg-count {
  font-size: 12.5px;
  font-weight: 600;
  color: #475569;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.session-table :deep(.el-table__row) {
  cursor: pointer;
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
  background: #e2e8f0;
}

.timeline-dot {
  width: 11px;
  height: 11px;
  flex: 0 0 11px;
  border-radius: 50%;
  margin-top: 3px;
  border: 2px solid #cbd5e1;
  background: #ffffff;
}

.timeline-item.done .timeline-dot {
  border-color: #14b8a6;
  background: #14b8a6;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.14);
}

.timeline-label {
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2b3d;
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
