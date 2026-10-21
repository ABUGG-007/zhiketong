<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="ROLES"
      title="角色管理"
      description="V1.0 固定三角色：管理员 / 客服坐席 / 普通用户。角色仅用于分配用户与接口权限判定，不提供自定义角色。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadRoles">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert
      class="role-note"
      type="info"
      show-icon
      :closable="false"
      title="V1.0 固定三角色，角色仅用于分配用户"
    >
      <template #default>
        新增自定义角色不会自动获得任何接口权限（权限校验只识别 ADMIN / AGENT / USER），
        因此本页不提供新增与删除角色的操作；角色状态由种子数据维护，本页只读展示。
      </template>
    </el-alert>

    <div class="zk-card">
      <div class="zk-toolbar">
        <label for="role-search" class="sr-only">搜索角色</label>
        <el-input
          id="role-search"
          v-model="keyword"
          class="el-input--wide"
          placeholder="搜索角色编码 / 名称 / 描述"
          aria-label="搜索角色"
          :prefix-icon="Search"
          clearable
        />
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          aria-label="按角色状态筛选"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ filteredRoles.length }}</b> 个角色
        </span>
      </div>

      <div
        class="role-grid"
        role="list"
        aria-label="角色列表"
        :aria-busy="loading ? 'true' : 'false'"
        v-loading="loading"
      >
        <article
          v-for="role in filteredRoles"
          :key="role.id"
          class="role-card"
          role="listitem"
        >
          <header class="role-head">
            <span class="role-code" :class="roleTone(role.roleCode)">{{ role.roleCode }}</span>
            <StatusPill
              :label="Number(role.status) === 1 ? '启用' : '禁用'"
              :tone="Number(role.status) === 1 ? 'teal' : 'red'"
            />
          </header>

          <h2 class="role-name">{{ role.roleName }}</h2>
          <p class="role-desc">{{ role.description || '暂无角色描述' }}</p>

          <div class="role-permission">
            <span class="perm-label">权限范围</span>
            <span class="perm-value">{{ permissionHint(role.roleCode) }}</span>
          </div>

          <div class="role-meta">
            <div class="meta-item">
              <span class="meta-label">用户数</span>
              <span class="meta-value zk-num">{{ role.userCount ?? 0 }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">排序</span>
              <span class="meta-value zk-num">{{ role.sort ?? '—' }}</span>
            </div>
            <div class="meta-item meta-item--wide">
              <span class="meta-label">创建时间</span>
              <span class="meta-value">{{ formatDateTime(role.createTime) }}</span>
            </div>
          </div>
        </article>

        <div v-if="!filteredRoles.length" class="role-empty">
          <div class="table-empty">
            <div class="empty-icon" aria-hidden="true">
              <el-icon><Avatar /></el-icon>
            </div>
            <p>{{ error || '暂无匹配的角色' }}</p>
            <span v-if="error" class="empty-hint">请确认后端 /api/admin/roles 已就绪</span>
            <span v-else-if="hasFilter" class="empty-hint">当前筛选条件下没有角色，试试清空条件</span>
            <span v-else class="empty-hint">角色表还没有数据，或种子数据尚未导入</span>
            <el-button v-if="error" @click="loadRoles">重新加载</el-button>
            <el-button v-else-if="hasFilter" @click="clearFilters">清空筛选条件</el-button>
            <el-button v-else @click="loadRoles">刷新列表</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      数据来源 GET /api/admin/roles（不分页，按 sort 升序） · 内置三角色不可删除 · 仅 ADMIN 可见本页
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Avatar, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import { getRoles } from '@/api'
import { formatDateTime } from '@/utils/dict'

const roles = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')

const hasFilter = computed(
  () => Boolean(keyword.value) || statusFilter.value === 0 || statusFilter.value === 1
)

const filteredRoles = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return roles.value.filter((role) => {
    if (statusFilter.value === 0 || statusFilter.value === 1) {
      if (Number(role.status) !== statusFilter.value) return false
    }
    if (!text) return true
    return [role.roleCode, role.roleName, role.description]
      .filter(Boolean)
      .some((field) => String(field).toLowerCase().includes(text))
  })
})

const roleTone = (roleCode) =>
  roleCode === 'ADMIN' ? 'red' : roleCode === 'AGENT' ? 'indigo' : 'slate'

const permissionHint = (roleCode) => {
  if (roleCode === 'ADMIN') return '全部模块（用户/角色/日志/知识库/工单/会话）'
  if (roleCode === 'AGENT') return '会话、工单、知识库；坐席自助上下线'
  if (roleCode === 'USER') return 'AI 聊天与会话；其余模块只读或不可见'
  return '自定义角色，未授予任何接口权限'
}

function clearFilters() {
  keyword.value = ''
  statusFilter.value = ''
}

async function loadRoles() {
  loading.value = true
  error.value = ''
  try {
    const data = await getRoles()
    roles.value = Array.isArray(data) ? data : []
  } catch (err) {
    roles.value = []
    error.value =
      err?.response?.status === 403
        ? '当前账号无权访问角色管理（仅 ADMIN）'
        : err?.response?.status === 404
          ? '接口返回 404：/api/admin/roles 尚未部署'
          : err?.message || '角色数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadRoles)
</script>

<style scoped>
.role-note {
  margin-bottom: 16px;
  border-radius: var(--zk-radius);
}

.role-note :deep(.el-alert__description) {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.7;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 20px;
  min-height: 220px;
}

.role-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface-2);
  transition: border-color var(--zk-dur-base) var(--zk-ease-out),
    box-shadow var(--zk-dur-base) var(--zk-ease-out),
    transform var(--zk-dur-base) var(--zk-ease-out);
}

.role-card:hover {
  transform: translateY(-2px);
  border-color: var(--zk-border-strong);
  box-shadow: var(--zk-shadow-md);
  background: var(--zk-surface);
}

.role-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.role-code {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 2px 10px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.4px;
  border: 1px solid;
}

.role-code.red {
  color: var(--zk-red);
  background: var(--zk-red-soft);
  border-color: var(--zk-red-line);
}

.role-code.indigo {
  color: var(--zk-indigo-dark);
  background: var(--zk-indigo-soft);
  border-color: var(--zk-indigo-line);
}

.role-code.slate {
  color: var(--zk-text-2);
  background: var(--zk-surface);
  border-color: var(--zk-border);
}

.role-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: var(--zk-text);
}

.role-desc {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
  min-height: 42px;
}

.role-permission {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--zk-surface);
  border: 1px dashed var(--zk-border-strong);
}

.perm-label {
  font-size: 10.5px;
  letter-spacing: 0.4px;
  color: var(--zk-text-3);
}

.perm-value {
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

.role-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--zk-border);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.meta-item--wide {
  grid-column: 1 / -1;
}

.meta-label {
  font-size: 11px;
  color: var(--zk-text-3);
}

.meta-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.meta-item--wide .meta-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--zk-text-2);
}

.role-empty {
  grid-column: 1 / -1;
}

@media (max-width: 1180px) {
  .role-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .role-grid {
    grid-template-columns: minmax(0, 1fr);
    padding: 16px;
  }
}
</style>
