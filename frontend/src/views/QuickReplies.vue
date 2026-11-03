<template>
  <div class="zk-page">
    <PageHeader
      title="快捷回复话术"
      description="维护坐席工作台可一键引用的话术。公共话术全员可用，个人话术由坐席自建；使用次数可用于识别高频话术。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog(null)">新增话术</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <label for="qr-search" class="sr-only">搜索话术</label>
        <el-input
          id="qr-search"
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索标题 / 内容"
          aria-label="搜索话术"
          :prefix-icon="Search"
          clearable
        />
        <el-select v-model="filters.scope" placeholder="全部范围" clearable aria-label="按范围筛选话术">
          <el-option label="公共话术" value="PUBLIC" />
          <el-option label="个人话术" value="PERSONAL" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable aria-label="按状态筛选话术">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ filteredReplies.length }}</b> 条话术
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="话术列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="6" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="filteredReplies"
          row-key="id"
          aria-label="话术列表"
          class="rows-stagger"
          :class="fadeClass"
        >
          <el-table-column label="话术" min-width="240">
            <template #default="{ row }">
              <div class="reply-cell">
                <span class="reply-title">{{ row.title }}</span>
                <span class="reply-content">{{ row.content }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.category || '通用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="范围" width="100">
            <template #default="{ row }">
              <StatusPill
                :label="row.scope === 'PUBLIC' ? '公共' : '个人'"
                :tone="row.scope === 'PUBLIC' ? 'indigo' : 'slate'"
              />
            </template>
          </el-table-column>
          <el-table-column label="使用次数" width="96">
            <template #default="{ row }">
              <span class="zk-num">{{ row.useCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.status) === 1 ? '启用' : '停用'"
                :tone="Number(row.status) === 1 ? 'teal' : 'red'"
              />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="152">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  v-if="row.editable"
                  link
                  type="primary"
                  class="op-btn"
                  @click="openDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="row.editable"
                  link
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  class="op-btn"
                  :loading="actionBusy === row.id"
                  @click="toggleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  v-if="row.editable"
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === row.id"
                  @click="confirmDelete(row)"
                >
                  删除
                </el-button>
                <span v-if="!row.editable" class="op-note">坐席个人话术</span>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无匹配的话术'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : hasFilter
                    ? '当前筛选条件下没有话术，试试放宽条件'
                    : '还没有话术，点击右上角「新增话术」创建公共话术'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="reset">清空筛选条件</el-button>
              <el-button v-else type="primary" @click="openDialog(null)">新增话术</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      公共话术面向全体坐席 · 个人话术由坐席在工作台自行维护，管理员可统一治理停用
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑话术' : '新增话术'"
      width="520px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="话术范围">
          <el-radio-group v-model="form.scope">
            <el-radio-button label="PUBLIC">公共</el-radio-button>
            <el-radio-button label="PERSONAL">个人</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="话术标题" prop="title">
          <el-input v-model="form.title" maxlength="64" show-word-limit placeholder="如：退款时效说明" />
        </el-form-item>
        <el-form-item label="话术分类">
          <el-input v-model="form.category" maxlength="32" placeholder="缺省为「通用」" />
        </el-form-item>
        <el-form-item label="话术内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            resize="none"
            maxlength="1000"
            show-word-limit
            placeholder="坐席点击后插入回复框的正文"
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          class="zk-save"
          :class="`is-${saveState}`"
          :disabled="saveState === 'busy'"
          @click="submit"
        >
          <span class="zk-save-label">保存</span>
          <span class="zk-save-mark zk-save-spin" aria-hidden="true" />
          <svg class="zk-save-mark zk-save-tick" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M3.2 8.4l2.9 2.9 6.7-6.7" />
          </svg>
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { prefersReducedMotion } from '@/composables/motion'
import {
  createQuickReply,
  deleteQuickReply,
  getQuickReplies,
  updateQuickReply,
  updateQuickReplyStatus
} from '@/api'
import { formatDateTime } from '@/utils/dict'

const replies = ref([])
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const saving = ref(false)
const actionBusy = ref(null)
const dialogVisible = ref(false)
const formRef = ref(null)
const filters = ref({ keyword: '', scope: '', status: '' })
const form = ref(createForm())

/* 保存按钮三连变：文字淡出 → 转圈 → 对勾（合计 350ms） */
const saveState = ref('idle')
let saveTimer = 0

function flashSaved() {
  if (prefersReducedMotion()) return
  saveState.value = 'done'
  saveTimer = window.setTimeout(() => {
    saveState.value = 'idle'
  }, 350)
}

onBeforeUnmount(() => window.clearTimeout(saveTimer))

/* 校验失败：第一个错误字段摇头（输错摇头） */
function shakeFirstError() {
  if (prefersReducedMotion()) return
  const el = formRef.value?.$el?.querySelector?.('.el-form-item.is-error')
  if (!el) return
  el.classList.remove('zk-shake')
  void el.offsetWidth
  el.classList.add('zk-shake')
  window.setTimeout(() => el.classList.remove('zk-shake'), 420)
}

// 筛选结果到达时重播 160ms 淡入（只淡不位移）
const listTick = ref(0)
watch(replies, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const rules = {
  title: [{ required: true, message: '请填写话术标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写话术内容', trigger: 'blur' }]
}

const hasFilter = computed(
  () => Boolean(filters.value.keyword) || Boolean(filters.value.scope) || filters.value.status === 0 || filters.value.status === 1
)

const filteredReplies = computed(() => {
  const text = filters.value.keyword.trim().toLowerCase()
  return replies.value.filter((item) => {
    if (filters.value.scope && item.scope !== filters.value.scope) return false
    if (filters.value.status === 0 || filters.value.status === 1) {
      if (Number(item.status) !== filters.value.status) return false
    }
    if (!text) return true
    return [item.title, item.content, item.category]
      .filter(Boolean)
      .some((field) => String(field).toLowerCase().includes(text))
  })
})

function createForm() {
  return { id: null, scope: 'PUBLIC', category: '通用', title: '', content: '', sort: 0, status: 1 }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    replies.value = (await getQuickReplies()) || []
  } catch (err) {
    replies.value = []
    error.value = err?.message || '话术加载失败，请稍后重试'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function reset() {
  filters.value = { keyword: '', scope: '', status: '' }
}

function openDialog(row) {
  form.value = row
    ? {
        id: row.id,
        scope: row.scope || 'PUBLIC',
        category: row.category || '通用',
        title: row.title,
        content: row.content,
        sort: row.sort ?? 0,
        status: Number(row.status) === 1 ? 1 : 0
      }
    : createForm()
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value || saveState.value === 'busy') return
  try {
    await formRef.value.validate()
  } catch {
    shakeFirstError()
    return
  }
  saving.value = true
  saveState.value = 'busy'
  try {
    const payload = {
      scope: form.value.scope,
      category: (form.value.category || '').trim() || '通用',
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      sort: form.value.sort ?? 0,
      status: form.value.status
    }
    if (form.value.id) {
      await updateQuickReply(form.value.id, payload)
    } else {
      await createQuickReply(payload)
    }
    ElMessage.success(form.value.id ? '话术已更新' : '话术已创建')
    flashSaved()
    dialogVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
    if (saveState.value === 'busy') saveState.value = 'idle'
  }
}

async function toggleStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = row.id
  try {
    await updateQuickReplyStatus(row.id, target)
    ElMessage.success(target === 1 ? '话术已启用' : '话术已停用')
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '状态更新失败')
  } finally {
    actionBusy.value = null
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除话术「${row.title}」？`, '删除话术', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  actionBusy.value = row.id
  try {
    await deleteQuickReply(row.id)
    ElMessage.success('话术已删除')
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = null
  }
}

onMounted(load)
</script>

<style scoped>
/* 首屏骨架与真实表格同列，加载前后不跳 */
.table-skeleton {
  padding: 12px 14px;
}

/* 首屏前 8 行按 40ms 依次浮现（只淡不位移，不打断扫读） */
.rows-stagger :deep(.el-table__body tr:nth-child(-n + 8)) {
  animation: zk-row-in 160ms var(--zk-ease-out) both;
}

.rows-stagger :deep(.el-table__body tr:nth-child(2)) {
  animation-delay: 40ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(3)) {
  animation-delay: 80ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(4)) {
  animation-delay: 120ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(5)) {
  animation-delay: 160ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(6)) {
  animation-delay: 200ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(7)) {
  animation-delay: 240ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(8)) {
  animation-delay: 280ms;
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

/* 保存按钮三连变：文字淡出 → 转圈 → 对勾（合计约 350ms） */
.zk-save {
  position: relative;
}

.zk-save .zk-save-label {
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out);
}

.is-busy .zk-save-label,
.is-done .zk-save-label {
  opacity: 0;
}

.zk-save-mark {
  position: absolute;
  inset: 0;
  margin: auto;
  width: 13px;
  height: 13px;
  opacity: 0;
  transform: scale(0.7);
  pointer-events: none;
  transition: opacity 160ms var(--zk-ease-out), transform 220ms var(--zk-ease-spring);
}

.is-busy .zk-save-spin,
.is-done .zk-save-tick {
  opacity: 1;
  transform: none;
}

.zk-save-spin {
  border: 1.6px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: zk-spin 780ms linear infinite;
}

.zk-save-tick path {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 24;
}

.is-done .zk-save-tick path {
  animation: zk-check-draw 300ms var(--zk-ease-out) forwards;
}

/* 字段聚焦：标签随输入框激活加深 */
:deep(.el-form-item:focus-within .el-form-item__label) {
  color: var(--zk-accent-hover);
  transition: color var(--zk-dur-fast) var(--zk-ease-out);
}

.reply-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.reply-title {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.reply-content {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11.5px;
  color: var(--zk-text-3);
}
</style>
