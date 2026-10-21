<template>
  <div class="zk-page">
    <PageHeader
      eyebrow="ACCOUNTS"
      title="用户管理"
      description="系统账号管理：新增、编辑、启用/禁用、重置密码与逻辑删除。内置 admin 与当前登录账号受保护。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <label for="user-search" class="sr-only">搜索用户</label>
        <el-input
          id="user-search"
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索账号 / 昵称 / 手机号"
          aria-label="搜索用户"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="filters.roleCode"
          placeholder="全部角色"
          clearable
          aria-label="按角色筛选账号"
          @change="search"
        >
          <el-option
            v-for="option in roleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          aria-label="按状态筛选账号"
          @change="search"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
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
          <el-table-column label="账号" width="150">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="user-avatar" :class="roleTone(row.roleCode)" aria-hidden="true">
                  {{ (row.nickname || row.username || '?').slice(0, 1) }}
                </span>
                <span class="user-name">{{ row.username }}</span>
                <span v-if="isSelf(row)" class="self-tag" title="当前登录账号">我</span>
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
          <el-table-column label="邮箱" min-width="170" show-overflow-tooltip>
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
          <el-table-column label="操作" width="248">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  link
                  type="primary"
                  class="op-btn"
                  :aria-label="`编辑用户 ${row.username}`"
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="canToggleStatus(row)"
                  link
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  class="op-btn"
                  :loading="actionBusy === row.id"
                  :aria-label="`${Number(row.status) === 1 ? '禁用' : '启用'}账号 ${row.username}`"
                  @click="toggleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="primary"
                  class="op-btn"
                  :aria-label="`重置用户 ${row.username} 的密码`"
                  @click="openReset(row)"
                >
                  重置密码
                </el-button>
                <el-button
                  v-if="canDelete(row)"
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === row.id"
                  :aria-label="`删除用户 ${row.username}`"
                  @click="confirmDelete(row)"
                >
                  删除
                </el-button>
                <span v-if="isBuiltinAdmin(row)" class="op-note">内置</span>
                <span v-else-if="isSelf(row)" class="op-note">当前</span>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty">
              <div class="empty-icon" aria-hidden="true">
                <el-icon><User /></el-icon>
              </div>
              <p>{{ error || '暂无匹配的账号' }}</p>
              <span v-if="error" class="empty-hint">请确认后端 /api/admin/users 已就绪</span>
              <span v-else-if="hasFilter" class="empty-hint">当前筛选条件下没有账号，试试放宽条件</span>
              <span v-else class="empty-hint">还没有账号，点击右上角「新增用户」创建</span>
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="reset">清空筛选条件</el-button>
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
      数据来源 /api/admin/users（GET/POST/PUT/DELETE） · 密码 BCrypt 加密存储 · 内置 admin 与当前账号不可禁用/删除
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="min(520px, 92vw)"
      :close-on-click-modal="false"
      @closed="onFormClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        class="user-form"
        @submit.prevent
      >
        <el-form-item label="账号" prop="username">
          <el-input
            v-model.trim="form.username"
            :disabled="isEdit"
            maxlength="32"
            autocomplete="off"
            placeholder="3-32 位字母、数字或下划线"
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="32"
            autocomplete="new-password"
            placeholder="6-32 位，需同时包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input
            v-model.trim="form.nickname"
            maxlength="32"
            placeholder="不填默认与账号相同"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select
            v-model="form.roleCode"
            :disabled="editingBuiltinAdmin"
            class="full-width"
            aria-label="用户角色"
          >
            <el-option
              v-for="option in roleOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <span v-if="editingBuiltinAdmin" class="field-hint">内置 admin 不可降级为其他角色</span>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model.trim="form.phone" maxlength="11" placeholder="选填" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model.trim="form.email" maxlength="64" placeholder="选填" />
          </el-form-item>
        </div>
        <el-form-item v-if="!editingBuiltinAdmin && !editingSelf" label="状态">
          <el-radio-group v-model="form.status" aria-label="账号状态">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else label="状态">
          <span class="field-hint">
            {{ editingBuiltinAdmin ? '内置 admin 不可禁用' : '当前登录账号不可禁用自己' }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog
      v-model="resetVisible"
      title="重置密码"
      width="min(440px, 92vw)"
      :close-on-click-modal="false"
      @closed="onResetClosed"
    >
      <el-form
        ref="resetFormRef"
        :model="resetForm"
        :rules="resetRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="账号">
          <el-input :model-value="resetTarget?.username || ''" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            maxlength="32"
            autocomplete="new-password"
            placeholder="6-32 位，需同时包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="resetForm.confirmPassword"
            type="password"
            show-password
            maxlength="32"
            autocomplete="new-password"
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-tip">重置成功后该用户会被强制下线，需使用新密码重新登录。</p>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitReset">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, User } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import {
  createUser,
  deleteUser,
  getRoles,
  getUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus
} from '@/api'
import { getUser } from '@/api/http'
import { usePagedList } from '@/composables/usePagedList'
import {
  ROLE_DICT,
  ROLE_OPTIONS,
  dictLabel,
  dictTone,
  formatDateTime
} from '@/utils/dict'

const currentUser = getUser()

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
} = usePagedList(getUsers, { keyword: '', roleCode: '', status: '' })

const hasFilter = computed(
  () =>
    Boolean(filters.keyword) ||
    Boolean(filters.roleCode) ||
    filters.status === 0 ||
    filters.status === 1
)

/* ---------- 角色下拉（真实 /admin/roles，失败回退内置三角色） ---------- */
const roleOptions = ref(ROLE_OPTIONS.filter((option) => option.value))

async function loadRoleOptions() {
  try {
    const list = await getRoles()
    if (Array.isArray(list) && list.length) {
      roleOptions.value = list.map((role) => ({
        value: role.roleCode,
        label: role.roleName || dictLabel(ROLE_DICT, role.roleCode)
      }))
    }
  } catch {
    /* 回退内置三角色，不阻断页面 */
  }
}

/* ---------- 行级权限 ---------- */
const isSelf = (row) => Number(row?.id) === Number(currentUser?.id)
const isBuiltinAdmin = (row) => row?.username === 'admin'
const canToggleStatus = (row) => !isBuiltinAdmin(row) && !isSelf(row)
const canDelete = (row) => !isBuiltinAdmin(row) && !isSelf(row)
const roleTone = (roleCode) =>
  roleCode === 'ADMIN' ? 'red' : roleCode === 'AGENT' ? 'indigo' : 'slate'

const actionBusy = ref(null)

/* ---------- 新增 / 编辑 ---------- */
const formVisible = ref(false)
const formRef = ref(null)
const editingRow = ref(null)
const submitting = ref(false)

const emptyForm = () => ({
  username: '',
  password: '',
  nickname: '',
  roleCode: 'USER',
  phone: '',
  email: '',
  status: 1
})

const form = reactive(emptyForm())

const isEdit = computed(() => Boolean(editingRow.value))
const editingBuiltinAdmin = computed(
  () => isEdit.value && isBuiltinAdmin(editingRow.value)
)
const editingSelf = computed(() => isEdit.value && isSelf(editingRow.value))

const optionalPattern = (pattern, message) => (rule, value, callback) => {
  if (!value) return callback()
  if (!pattern.test(value)) return callback(new Error(message))
  return callback()
}

const formRules = computed(() => {
  const rules = {
    username: [
      { required: true, message: '请输入账号', trigger: 'blur' },
      {
        pattern: /^[a-zA-Z0-9_]{3,32}$/,
        message: '账号需为 3-32 位字母、数字或下划线',
        trigger: 'blur'
      }
    ],
    nickname: [{ max: 32, message: '昵称最多 32 个字符', trigger: 'blur' }],
    roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
    phone: [{ validator: optionalPattern(/^1[3-9]\d{9}$/, '手机号格式不正确'), trigger: 'blur' }],
    email: [
      {
        validator: optionalPattern(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, '邮箱格式不正确'),
        trigger: 'blur'
      }
    ]
  }
  if (!isEdit.value) {
    rules.password = [
      { required: true, message: '请输入初始密码', trigger: 'blur' },
      {
        pattern: /^(?=.*[A-Za-z])(?=.*\d)[^\s]{6,32}$/,
        message: '密码需 6-32 位且同时包含字母和数字',
        trigger: 'blur'
      }
    ]
  }
  return rules
})

function clearFormValidate() {
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate() {
  editingRow.value = null
  Object.assign(form, emptyForm())
  formVisible.value = true
  clearFormValidate()
}

function openEdit(row) {
  editingRow.value = row
  Object.assign(form, {
    username: row.username || '',
    password: '',
    nickname: row.nickname || '',
    roleCode: row.roleCode || 'USER',
    phone: row.phone || '',
    email: row.email || '',
    status: Number(row.status) === 0 ? 0 : 1
  })
  formVisible.value = true
  clearFormValidate()
}

function onFormClosed() {
  editingRow.value = null
  Object.assign(form, emptyForm())
}

async function submitForm() {
  if (!formRef.value || submitting.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      const payload = {
        nickname: form.nickname || form.username
      }
      if (form.phone) payload.phone = form.phone
      if (form.email) payload.email = form.email
      if (!editingBuiltinAdmin.value) {
        payload.roleCode = form.roleCode
      }
      if (!editingBuiltinAdmin.value && !editingSelf.value) {
        payload.status = form.status
      }
      await updateUser(editingRow.value.id, payload)
      ElMessage.success('用户信息已更新')
    } else {
      const payload = {
        username: form.username,
        password: form.password,
        nickname: form.nickname || form.username,
        roleCode: form.roleCode,
        status: form.status
      }
      if (form.phone) payload.phone = form.phone
      if (form.email) payload.email = form.email
      await createUser(payload)
      ElMessage.success('用户创建成功')
    }
    formVisible.value = false
    load()
  } catch {
    // 后端错误信息已由 http 拦截器用 ElMessage 展示
  } finally {
    submitting.value = false
  }
}

/* ---------- 启用 / 禁用 ---------- */
async function toggleStatus(row) {
  if (!canToggleStatus(row) || actionBusy.value) return
  const target = Number(row.status) === 1 ? 0 : 1
  if (target === 0) {
    try {
      await ElMessageBox.confirm(
        `禁用「${row.username}」后该账号会立即下线且无法登录，确定继续？`,
        '禁用账号',
        {
          type: 'warning',
          confirmButtonText: '禁用',
          cancelButtonText: '取消',
          confirmButtonClass: 'el-button--danger'
        }
      )
    } catch {
      return
    }
  }
  actionBusy.value = row.id
  try {
    await updateUserStatus(row.id, target)
    ElMessage.success(target === 1 ? '账号已启用' : '账号已禁用')
    load()
  } catch {
    /* 已由拦截器提示 */
  } finally {
    actionBusy.value = null
  }
}

/* ---------- 删除 ---------- */
async function confirmDelete(row) {
  if (!canDelete(row) || actionBusy.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」？删除后该账号立即失效且不可登录，历史数据保留。`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  actionBusy.value = row.id
  try {
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    if (rows.value.length === 1 && pageNum.value > 1) {
      changePage(pageNum.value - 1)
    } else {
      load()
    }
  } catch {
    /* 已由拦截器提示 */
  } finally {
    actionBusy.value = null
  }
}

/* ---------- 重置密码 ---------- */
const resetVisible = ref(false)
const resetFormRef = ref(null)
const resetTarget = ref(null)
const resetSubmitting = ref(false)
const resetForm = reactive({ newPassword: '', confirmPassword: '' })

const resetRules = computed(() => ({
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)[^\s]{6,32}$/,
      message: '新密码需 6-32 位且同时包含字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === resetForm.newPassword ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}))

function openReset(row) {
  resetTarget.value = row
  Object.assign(resetForm, { newPassword: '', confirmPassword: '' })
  resetVisible.value = true
  nextTick(() => resetFormRef.value?.clearValidate())
}

function onResetClosed() {
  resetTarget.value = null
  Object.assign(resetForm, { newPassword: '', confirmPassword: '' })
}

async function submitReset() {
  if (!resetFormRef.value || resetSubmitting.value) return
  try {
    await resetFormRef.value.validate()
  } catch {
    return
  }
  resetSubmitting.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetForm.newPassword)
    ElMessage.success(`已重置「${resetTarget.value.username}」的密码，该用户需重新登录`)
    resetVisible.value = false
  } catch {
    /* 已由拦截器提示 */
  } finally {
    resetSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadRoleOptions()
})
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

.self-tag {
  font-size: 10px;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 5px;
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border: 1px solid var(--zk-teal-line);
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

/* 行内操作 */
.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: nowrap;
}

.op-btn {
  min-height: 32px;
  padding: 0 6px;
  font-size: 12.5px;
  font-weight: 600;
}

.op-note {
  font-size: 10.5px;
  color: var(--zk-text-3);
  padding: 0 4px;
  white-space: nowrap;
}

/* 表格横向滚动 */
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
  min-width: 1330px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--zk-surface-2);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

/* 表单 */
.user-form :deep(.el-form-item),
.user-form :deep(.el-form-item__label) {
  font-weight: 500;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-width {
  width: 100%;
}

.field-hint {
  display: block;
  font-size: 11.5px;
  color: var(--zk-text-3);
  margin-top: 4px;
  line-height: 1.6;
}

.dialog-tip {
  font-size: 12px;
  line-height: 1.7;
  color: var(--zk-text-3);
  padding: 0 2px;
}

@media (max-width: 640px) {
  .form-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
