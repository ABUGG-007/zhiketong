<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="ACCOUNTS"
      title="用户管理"
      description="系统账号、角色与登录状态。管理员可访问全部模块，坐席处理会话与工单，普通用户仅参与会话。"
      badge="演示数据"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-select v-model="filters.roleCode" placeholder="全部角色" clearable @change="search">
          <el-option
            v-for="option in ROLE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta">共 <b>{{ total }}</b> 个账号</span>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="账号" width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-avatar" :class="roleTone(row.roleCode)">
                {{ (row.nickname || row.username || '?').slice(0, 1) }}
              </span>
              <span class="user-name">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="昵称" width="126">
          <template #default="{ row }">
            <span class="person">{{ row.nickname || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="118">
          <template #default="{ row }">
            <StatusPill
              :label="row.roleName || dictLabel(ROLE_DICT, row.roleCode)"
              :tone="dictTone(ROLE_DICT, row.roleCode)"
            />
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="138">
          <template #default="{ row }">
            <span class="mono-cell">{{ row.phone || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="196" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-cell">{{ row.email || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="94">
          <template #default="{ row }">
            <StatusPill
              :label="Number(row.status) === 1 ? '启用' : '禁用'"
              :tone="Number(row.status) === 1 ? 'teal' : 'red'"
            />
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.lastLoginTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <div class="empty-icon"><el-icon><User /></el-icon></div>
            <p>{{ error || '暂无匹配的账号' }}</p>
            <span v-if="!error" class="empty-hint">切换角色筛选后重试</span>
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
      数据来源 GET /api/demo/users · 账号密码由 BCrypt 加密存储 · 演示数据，新增/编辑功能后续里程碑完善
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
  color: #be123c;
  background: #fff0f3;
  border-color: #fbd0da;
}
.user-avatar.indigo {
  color: #4338ca;
  background: #eef0ff;
  border-color: #d8dafb;
}
.user-avatar.slate {
  color: #475569;
  background: #f1f5fa;
  border-color: #e2e8f0;
}

.user-name {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2b3d;
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
</style>
