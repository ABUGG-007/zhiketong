<template>
  <div class="zk-page">
    <PageHeader
      title="技能组管理"
      description="按业务技能划分坐席。访客转人工可选择咨询类型，自动分配优先在同组坐席中挑选，组内无人时降级为全体在岗坐席。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog(null)">新增技能组</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <label for="skill-search" class="sr-only">搜索技能组</label>
        <el-input
          id="skill-search"
          v-model="keyword"
          class="el-input--wide"
          placeholder="搜索技能组名称 / 说明"
          aria-label="搜索技能组"
          :prefix-icon="Search"
          clearable
        />
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ filteredGroups.length }}</b> 个技能组 · 坐席
          <b class="zk-num">{{ totalMembers }}</b> 人次
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="技能组列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="6" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="filteredGroups"
          row-key="id"
          aria-label="技能组列表"
          :class="fadeClass"
        >
          <el-table-column label="技能组" min-width="200">
            <template #default="{ row }">
              <div class="group-cell">
                <span class="group-name">{{ row.name }}</span>
                <span class="group-desc">{{ row.description || '未填写组职责说明' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="组内坐席" min-width="260">
            <template #default="{ row }">
              <div v-if="row.members?.length" class="member-tags">
                <el-tag
                  v-for="member in row.members"
                  :key="member.userId"
                  size="small"
                  :type="member.level >= 4 ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ member.nickname || member.username }}
                  <span class="member-level">L{{ member.level }}</span>
                </el-tag>
              </div>
              <span v-else class="cell-muted">未分配坐席（按不限技能组分配）</span>
            </template>
          </el-table-column>
          <el-table-column label="坐席数" width="88">
            <template #default="{ row }">
              <span class="zk-num">{{ row.memberCount ?? 0 }}</span>
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
          <el-table-column label="排序" width="70">
            <template #default="{ row }">
              <span class="zk-num">{{ row.sort ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="152">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDialog(row)">编辑</el-button>
                <el-button
                  link
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  class="op-btn"
                  :loading="actionBusy === row.id"
                  @click="toggleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === row.id"
                  @click="confirmDelete(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无匹配的技能组'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : keyword
                    ? '当前关键词下没有技能组，试试清空搜索'
                    : '还没有技能组，点击右上角「新增技能组」创建'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="keyword" @click="keyword = ''">清空搜索</el-button>
              <el-button v-else type="primary" @click="openDialog(null)">新增技能组</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      停用或删除前需处理完排队中的会话 · 组内无坐席时该组按「不限技能组」处理，不会阻断访客转人工
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑技能组' : '新增技能组'"
      width="520px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="技能组名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="如：订单售后" />
        </el-form-item>
        <el-form-item label="组职责说明" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="255"
            show-word-limit
            placeholder="转人工时向访客展示，说明该组能解决的问题"
          />
        </el-form-item>
        <el-form-item label="组内坐席" prop="memberIds">
          <el-select
            v-model="form.memberIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择坐席，可多选"
            class="full-select"
          >
            <el-option
              v-for="agent in agentOptions"
              :key="agent.id"
              :label="agent.nickname || agent.username"
              :value="agent.id"
            />
          </el-select>
          <p class="form-hint">能力值沿用上次保存值，新加入坐席默认 L3；能力值越高，同负载下越优先分配</p>
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
  createSkillGroup,
  deleteSkillGroup,
  getSkillGroups,
  getUsers,
  updateSkillGroup,
  updateSkillGroupStatus
} from '@/api'
import { formatDateTime } from '@/utils/dict'

const groups = ref([])
const agentOptions = ref([])
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const keyword = ref('')
const saving = ref(false)
const actionBusy = ref(null)
const dialogVisible = ref(false)
const formRef = ref(null)

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
watch(groups, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const form = ref(createForm())
/** 已保存的能力值：编辑时沿用，避免每次保存都重置为默认值 */
const savedLevels = ref({})

const rules = {
  name: [{ required: true, message: '请填写技能组名称', trigger: 'blur' }]
}

const filteredGroups = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return groups.value
  return groups.value.filter((group) =>
    [group.name, group.description].filter(Boolean).some((field) =>
      String(field).toLowerCase().includes(text)
    )
  )
})

const totalMembers = computed(() =>
  groups.value.reduce((sum, group) => sum + Number(group.memberCount || 0), 0)
)

function createForm() {
  return { id: null, name: '', description: '', sort: 0, status: 1, memberIds: [] }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    groups.value = (await getSkillGroups({ withMembers: true })) || []
  } catch (err) {
    groups.value = []
    error.value = err?.message || '技能组加载失败，请稍后重试'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function loadAgents() {
  try {
    const page = await getUsers({ pageNum: 1, pageSize: 100, roleCode: 'AGENT', status: 1 })
    agentOptions.value = page?.records || []
  } catch {
    agentOptions.value = []
  }
}

function openDialog(row) {
  savedLevels.value = {}
  if (row) {
    const levels = {}
    for (const member of row.members || []) {
      levels[member.userId] = member.level
    }
    savedLevels.value = levels
    form.value = {
      id: row.id,
      name: row.name,
      description: row.description || '',
      sort: row.sort ?? 0,
      status: Number(row.status) === 1 ? 1 : 0,
      memberIds: (row.members || []).map((member) => member.userId)
    }
  } else {
    form.value = createForm()
  }
  dialogVisible.value = true
  if (!agentOptions.value.length) loadAgents()
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
      name: form.value.name.trim(),
      description: form.value.description.trim(),
      sort: form.value.sort ?? 0,
      status: form.value.status,
      memberIds: form.value.memberIds,
      levels: form.value.memberIds.map((userId) => ({
        userId,
        level: savedLevels.value[userId] ?? 3
      }))
    }
    if (form.value.id) {
      await updateSkillGroup(form.value.id, payload)
    } else {
      await createSkillGroup(payload)
    }
    ElMessage.success(form.value.id ? '技能组已更新' : '技能组已创建')
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
    await updateSkillGroupStatus(row.id, target)
    ElMessage.success(target === 1 ? '技能组已启用' : '技能组已停用')
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '状态更新失败')
  } finally {
    actionBusy.value = null
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除后该技能组的坐席关系一并清除，且访客无法再选择「${row.name}」。确认删除？`,
      '删除技能组',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = row.id
  try {
    await deleteSkillGroup(row.id)
    ElMessage.success('技能组已删除')
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = null
  }
}

onMounted(() => {
  load()
  loadAgents()
})
</script>

<style scoped>
/* 首屏骨架与真实表格同列，加载前后不跳 */
.table-skeleton {
  padding: 12px 14px;
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

.group-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.group-name {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.group-desc {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.member-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.member-level {
  margin-left: 4px;
  font-size: 10px;
  opacity: 0.75;
}

.cell-muted {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.full-select {
  width: 100%;
}

.form-hint {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--zk-text-3);
}
</style>
