<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="ACCOUNTS"
      title="用户管理"
      description="系统账号、角色与登录状态。管理员可访问全部模块，坐席处理会话与工单，普通用户仅参与会话。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-select
          v-model="filters.roleCode"
          placeholder="全部角色"
          clearable
          aria-label="按角色筛选账号"
          @change="search"
        >
          <el-option
            v-for="option in ROLE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">共 <b class="zk-num">{{ total }}</b> 个账号</span>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="用户列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <el-table v-loading="loading" :data="rows" row-key="id" aria-label="用户列表">
          <el-table-column label="账号" width="146">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="user-avatar" :class="roleTone(row.roleCode)" aria-hidden="true">
                  {{ (row.nickname || row.username || '?').slice(0, 1) }}
                </span>
                <span class="user-name">{{ row.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="昵称" width="120">
            <template #default="{ row }">
              <span class="person">{{ row.nickname || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="112">
            <template #default="{ row }">
              <StatusPill
                :label="row.roleName || dictLabel(ROLE_DICT, row.roleCode)"
                :tone="dictTone(ROLE_DICT, row.roleCode)"
              />
            </template>
          </el-table-column>
          <el-table-column label="手机号" width="132">
            <template #default="{ row }">
              <span class="mono-cell">{{ row.phone || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="邮箱" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono-cell">{{ row.email || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.status) === 1 ? '启用' : '禁用'"
                :tone="Number(row.status) === 1 ? 'teal' : 'red'"
              />
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="152">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.lastLoginTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="152">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><User /></el-icon>
              </div>
              <p>{{ error || '暂无匹配的账号' }}</p>
              <span v-if="error" class="empty-hint">请确认后端 /api/console/users 已就绪</span>
              <span v-else-if="filters.roleCode" class="empty-hint">该角色下没有账号，试试其他角色</span>
              <span v-else class="empty-hint">还没有账号，等待 M1 真实创建</span>
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="filters.roleCode" @click="reset">清空角色筛选</el-button>
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
          aria-label="用户分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/console/users · 账号密码由 BCrypt 加密存储 · 新增/编辑功能后续里程碑完善
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Refresh, User } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getUsers } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  ROLE_DICT,
  ROLE_OPTIONS,
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
} = usePagedList(getUsers, { roleCode: '' })

const roleTone = (roleCode) =>
  roleCode === 'ADMIN' ? 'red' : roleCode === 'AGENT' ? 'indigo' : 'slate'

onMounted(load)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}

.user-avatar {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-size: 11.5px;
  font-weight: 700;
  border: 1px solid;
}

.user-avatar.red {
  color: var(--zk-red);
  background: var(--zk-red-soft);
  border-color: var(--zk-red-line);
}

.user-avatar.indigo {
  color: var(--zk-indigo-dark);
  background: var(--zk-indigo-soft);
  border-color: var(--zk-indigo-line);
}

.user-avatar.slate {
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border-color: var(--zk-border);
}

.user-name {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.person {
  font-size: 12.5px;
  color: var(--zk-text);
}

.mono-cell {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
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
  min-width: 1090px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}
</style>
