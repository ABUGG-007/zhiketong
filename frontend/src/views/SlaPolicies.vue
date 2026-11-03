<template>
  <div class="zk-page">
    <PageHeader
      title="SLA 时效策略"
      description="按优先级配置工单的三级时限（首次响应 / 每次回复间隔 / 解决），设定预警阈值与超时升级规则。新建工单按优先级匹配默认策略，策略以快照方式绑定，调整不影响在跑工单。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog(null)">新增策略</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-select v-model="filterPriority" class="filter-select" placeholder="全部优先级" clearable>
          <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filterStatus" class="filter-select" placeholder="全部状态" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ policies.length }}</b> 条策略 · 默认
          <b class="zk-num">{{ defaultCount }}</b> 条
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="SLA 策略列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="policies"
          row-key="id"
          aria-label="SLA 策略列表"
          :class="fadeClass"
        >
          <el-table-column label="策略" min-width="200">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">
                  {{ row.policyName }}
                  <el-tag v-if="Number(row.defaultFlag) === 1" size="small" type="success" effect="plain">默认</el-tag>
                </span>
                <span class="cell-muted">{{ row.description || '未填写策略说明' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="100">
            <template #default="{ row }">
              <StatusPill :label="dictLabel(PRIORITY_DICT, row.priority)" :tone="dictTone(PRIORITY_DICT, row.priority)" />
            </template>
          </el-table-column>
          <el-table-column label="首次响应" width="110">
            <template #default="{ row }">
              <span class="cell-muted">{{ levelText(row.firstResponseMinutes) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="回复间隔" width="110">
            <template #default="{ row }">
              <span class="cell-muted">{{ levelText(row.replyIntervalMinutes) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="解决时限" width="110">
            <template #default="{ row }">
              <span class="cell-muted">{{ levelText(row.resolveMinutes) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预警阈值" width="96">
            <template #default="{ row }">
              <span class="zk-num">{{ row.warnPercent ?? 80 }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="超时升级" width="110">
            <template #default="{ row }">
              <span class="cell-muted">{{ escalateText(row) }}</span>
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
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDialog(row)">编辑</el-button>
                <el-button
                  v-if="Number(row.defaultFlag) !== 1"
                  link
                  type="success"
                  class="op-btn"
                  :loading="actionBusy === `default-${row.id}`"
                  @click="makeDefault(row)"
                >
                  设为默认
                </el-button>
                <el-button
                  link
                  :type="Number(row.status) === 1 ? 'warning' : 'success'"
                  class="op-btn"
                  :loading="actionBusy === `status-${row.id}`"
                  @click="toggleStatus(row)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  class="op-btn"
                  :loading="actionBusy === `del-${row.id}`"
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
              :title="error || '暂无 SLA 策略'"
              :hint="error ? '加载失败，请稍后重试' : '还没有策略，点击右上角「新增策略」创建'"
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else type="primary" @click="openDialog(null)">新增策略</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      三级时限取 0 表示不启用该级，至少启用一级 · 预警阈值为「已消耗时长占时限的百分比」，达到即预警 · 同一优先级仅允许一条默认策略
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑 SLA 策略' : '新增 SLA 策略'"
      width="600px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-row">
          <el-form-item label="策略名称" prop="policyName">
            <el-input v-model="form.policyName" maxlength="64" show-word-limit placeholder="如：P1 高优先级时效" />
          </el-form-item>
          <el-form-item label="适用优先级" prop="priority">
            <el-select v-model="form.priority" class="full-select" placeholder="选择优先级">
              <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="首次响应时限（分钟）">
            <el-input-number v-model="form.firstResponseMinutes" :min="0" :max="525600" controls-position="right" />
          </el-form-item>
          <el-form-item label="每次回复间隔（分钟）">
            <el-input-number v-model="form.replyIntervalMinutes" :min="0" :max="525600" controls-position="right" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="解决时限（分钟）">
            <el-input-number v-model="form.resolveMinutes" :min="0" :max="525600" controls-position="right" />
          </el-form-item>
          <el-form-item label="预警阈值（%）">
            <el-input-number v-model="form.warnPercent" :min="1" :max="99" controls-position="right" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="超时升级延迟（分钟）">
            <el-input-number v-model="form.escalateMinutes" :min="0" :max="525600" controls-position="right" />
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item label="策略说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="说明该策略适用场景，如大促期间的加急时效"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="设为该优先级默认">
            <el-switch v-model="form.defaultFlag" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
        <p class="form-hint">升级延迟为 0 表示解决超时后立即升级通知管理员；升级角色当前固定为 ADMIN。</p>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { prefersReducedMotion } from '@/composables/motion'
import {
  createSlaPolicy,
  deleteSlaPolicy,
  getSlaPolicies,
  updateSlaPolicy,
  updateSlaPolicyStatus
} from '@/api'
import { PRIORITY_DICT, PRIORITY_OPTIONS, dictLabel, dictTone, formatMinutes } from '@/utils/dict'

const priorityOptions = PRIORITY_OPTIONS.filter((item) => item.value)

const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const actionBusy = ref('')
const policies = ref([])
const filterPriority = ref('')
const filterStatus = ref('')

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
watch(policies, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const defaultCount = computed(() => policies.value.filter((item) => Number(item.defaultFlag) === 1).length)

const levelText = (minutes) => (Number(minutes) > 0 ? formatMinutes(minutes) : '不启用')

const escalateText = (row) => {
  if (Number(row.resolveMinutes) <= 0) return '—'
  const delay = Number(row.escalateMinutes) || 0
  return delay > 0 ? `超时 ${formatMinutes(delay)}后` : '超时即升级'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (filterPriority.value) params.priority = filterPriority.value
    if (filterStatus.value !== '' && filterStatus.value !== null) params.status = filterStatus.value
    const list = await getSlaPolicies(params)
    policies.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e?.response?.data?.message || 'SLA 策略加载失败'
    policies.value = []
  } finally {
    loading.value = false
    loaded.value = true
  }
}

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  policyName: '',
  priority: 'P2',
  firstResponseMinutes: 30,
  replyIntervalMinutes: 0,
  resolveMinutes: 120,
  warnPercent: 80,
  escalateMinutes: 0,
  escalateRole: 'ADMIN',
  defaultFlag: 0,
  description: '',
  sort: 0,
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  policyName: [
    { required: true, message: '请填写策略名称', trigger: 'blur' },
    { min: 2, max: 64, message: '策略名称长度须为 2-64 个字符', trigger: 'blur' }
  ],
  priority: [{ required: true, message: '请选择适用优先级', trigger: 'change' }]
}

function openDialog(row) {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      id: row.id,
      policyName: row.policyName,
      priority: row.priority,
      firstResponseMinutes: row.firstResponseMinutes ?? 0,
      replyIntervalMinutes: row.replyIntervalMinutes ?? 0,
      resolveMinutes: row.resolveMinutes ?? 0,
      warnPercent: row.warnPercent ?? 80,
      escalateMinutes: row.escalateMinutes ?? 0,
      escalateRole: row.escalateRole || 'ADMIN',
      defaultFlag: Number(row.defaultFlag) === 1 ? 1 : 0,
      description: row.description || '',
      sort: row.sort ?? 0,
      status: Number(row.status) === 0 ? 0 : 1
    })
  }
  dialogVisible.value = true
}

async function submit() {
  if (saveState.value === 'busy') return
  try {
    await formRef.value.validate()
  } catch {
    shakeFirstError()
    return
  }
  if (
    Number(form.firstResponseMinutes) <= 0 &&
    Number(form.replyIntervalMinutes) <= 0 &&
    Number(form.resolveMinutes) <= 0
  ) {
    ElMessage.warning('至少需要启用一级时限（首响 / 回复间隔 / 解决）')
    return
  }
  saving.value = true
  saveState.value = 'busy'
  try {
    const payload = {
      policyName: form.policyName.trim(),
      priority: form.priority,
      firstResponseMinutes: form.firstResponseMinutes ?? 0,
      replyIntervalMinutes: form.replyIntervalMinutes ?? 0,
      resolveMinutes: form.resolveMinutes ?? 0,
      warnPercent: form.warnPercent ?? 80,
      escalateMinutes: form.escalateMinutes ?? 0,
      escalateRole: form.escalateRole || 'ADMIN',
      defaultFlag: form.defaultFlag,
      description: form.description.trim() || null,
      sort: form.sort ?? 0,
      status: form.status
    }
    if (form.id) {
      await updateSlaPolicy(form.id, payload)
    } else {
      await createSlaPolicy(payload)
    }
    ElMessage.success(form.id ? '策略已更新' : '策略已创建')
    flashSaved()
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
    if (saveState.value === 'busy') saveState.value = 'idle'
  }
}

async function makeDefault(row) {
  actionBusy.value = `default-${row.id}`
  try {
    await updateSlaPolicy(row.id, {
      policyName: row.policyName,
      priority: row.priority,
      firstResponseMinutes: row.firstResponseMinutes ?? 0,
      replyIntervalMinutes: row.replyIntervalMinutes ?? 0,
      resolveMinutes: row.resolveMinutes ?? 0,
      warnPercent: row.warnPercent ?? 80,
      escalateMinutes: row.escalateMinutes ?? 0,
      escalateRole: row.escalateRole || 'ADMIN',
      defaultFlag: 1,
      description: row.description || null,
      sort: row.sort ?? 0,
      status: Number(row.status) === 0 ? 0 : 1
    })
    ElMessage.success(`已将「${row.policyName}」设为 ${row.priority} 默认策略`)
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function toggleStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `status-${row.id}`
  try {
    await updateSlaPolicyStatus(row.id, target)
    ElMessage.success(target === 1 ? '策略已启用' : '策略已停用')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除策略「${row.policyName}」后不可恢复，已按该策略计时的历史工单仍按快照执行。`,
      '删除策略',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `del-${row.id}`
  try {
    await deleteSlaPolicy(row.id)
    ElMessage.success('策略已删除')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

onMounted(load)
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

.filter-select {
  width: 150px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.cell-muted {
  font-size: 12px;
  color: var(--zk-text-3);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-select {
  width: 100%;
}

.form-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--zk-text-3);
}

@media (max-width: 640px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
