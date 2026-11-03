<template>
  <div class="zk-page">
    <PageHeader
      title="质检规则"
      description="四类质检规则：语义类与情感类由大模型判定，服务规范类与敏感词类由规则引擎按关键词判定。停用规则只影响后续质检，历史评分卡保留当时的判定结果。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog(null)">新增规则</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-model="keyword"
          class="el-input--wide"
          placeholder="搜索规则名称 / 编码"
          :prefix-icon="Search"
          clearable
        />
        <el-select v-model="filterType" class="filter-select" placeholder="全部类型" clearable @change="load">
          <el-option v-for="item in QUALITY_RULE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filterStatus" class="filter-select" placeholder="全部状态" clearable @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="grow" />
        <span class="toolbar-meta">
          共 <b class="zk-num">{{ rules.length }}</b> 条 · 启用 <b class="zk-num">{{ enabledCount }}</b> 条
        </span>
      </div>

      <div
        class="table-scroll"
        :class="{ 'table-result-fade': resultFade }"
        role="region"
        aria-label="质检规则列表"
        tabindex="0"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="7" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="visibleRules"
          row-key="id"
          aria-label="质检规则列表"
          :row-class-name="rowClassName"
        >
          <el-table-column label="规则" min-width="240">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">
                  {{ row.ruleName }}
                  <el-tag v-if="row.builtin" size="small" type="info" effect="plain">内置</el-tag>
                </span>
                <span class="cell-code">{{ row.ruleCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <StatusPill
                :label="row.ruleTypeName || dictLabel(QUALITY_RULE_TYPE_DICT, row.ruleType)"
                :tone="dictTone(QUALITY_RULE_TYPE_DICT, row.ruleType)"
              />
            </template>
          </el-table-column>
          <el-table-column label="严重程度" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="row.severityName || dictLabel(QUALITY_SEVERITY_DICT, row.severity)"
                :tone="dictTone(QUALITY_SEVERITY_DICT, row.severity)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="扣分" width="84">
            <template #default="{ row }">
              <span class="zk-num">{{ Number(row.deductScore) > 0 ? `-${row.deductScore}` : '仅提示' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="判定口径" min-width="260">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ caliberText(row) }}</span>
                <span v-if="row.description" class="cell-hint">{{ row.description }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="82">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.status) === 1 ? '启用' : '停用'"
                :tone="Number(row.status) === 1 ? 'teal' : 'red'"
                :class="{ 'zk-flash': changedId === row.id }"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDialog(row)">编辑</el-button>
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
              :title="error ? '质检规则加载失败' : '暂无质检规则'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : '点击右上角「新增规则」创建，或重新执行建表脚本导入默认规则'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else type="primary" @click="openDialog(null)">新增规则</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      语义类 / 情感类规则需在「大模型配置」中启用可用模型：模型不可用时该组规则跳过判定，不会默认判为命中 · 服务规范类规则只作用于坐席消息，纯机器人会话记为不适用 · 扣分填 0 表示只记录不计分
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑质检规则' : '新增质检规则'"
      width="620px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <div class="form-row">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" maxlength="64" show-word-limit placeholder="如：未使用问候语" />
          </el-form-item>
          <el-form-item label="规则类型" prop="ruleType">
            <el-select v-model="form.ruleType" class="full-select" :disabled="form.builtin" placeholder="选择类型">
              <el-option v-for="item in QUALITY_RULE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="严重程度">
            <el-select v-model="form.severity" class="full-select">
              <el-option v-for="item in QUALITY_SEVERITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="命中扣分（0 表示只提示）">
            <el-input-number v-model="form.deductScore" :min="0" :max="30" controls-position="right" />
          </el-form-item>
        </div>

        <el-form-item v-if="form.ruleCode === 'SLOW_REPLY'" label="允许的最大回复间隔（分钟）" prop="thresholdValue">
          <el-input-number v-model="form.thresholdValue" :min="1" :max="1440" controls-position="right" />
        </el-form-item>

        <template v-else-if="!isAiType">
          <el-form-item label="关键词（英文逗号分隔）" prop="keywords">
            <el-input
              v-model="form.keywords"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              resize="none"
              placeholder="如：你好,您好,hi,hello"
            />
          </el-form-item>
          <el-form-item label="匹配方式">
            <el-select v-model="form.matchMode" class="full-select">
              <el-option v-for="item in QUALITY_MATCH_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item v-else label="判定说明">
          <p class="form-note">
            语义类与情感类规则交给大模型判定，无需配置关键词。请在上方「严重程度」与「命中扣分」中定义命中后的扣分力度。
          </p>
        </el-form-item>

        <el-form-item label="规则说明与判定口径">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="说明该规则的判定边界，便于后续复核人员对齐口径"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="排序号（越小越靠前）">
            <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
        <p v-if="form.builtin" class="form-hint">
          内置规则的编码被质检引擎识别（如「回复超时」按阈值计时、「未使用问候语」只看首条坐席消息），因此类型不可修改；名称、扣分、关键词与状态可按业务调整。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  createQualityRule,
  deleteQualityRule,
  getQualityRules,
  updateQualityRule,
  updateQualityRuleStatus
} from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import {
  QUALITY_MATCH_MODE_OPTIONS,
  QUALITY_RULE_TYPE_DICT,
  QUALITY_RULE_TYPE_OPTIONS,
  QUALITY_SEVERITY_DICT,
  QUALITY_SEVERITY_OPTIONS,
  dictLabel,
  dictTone
} from '@/utils/dict'

const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const actionBusy = ref('')
const rules = ref([])
const keyword = ref('')
const filterType = ref('')
const filterStatus = ref('')

/* 首屏骨架屏；筛选结果 160ms 淡入；停用/删除的行先淡出再移除 */
const resultFade = ref(false)
const changedId = ref(null)
const removingId = ref(null)
let resultFadeTimer = 0
let changedTimer = 0
let keywordTimer = 0

const rowClassName = ({ row }) => (row.id === removingId.value ? 'row-removing' : '')

function triggerResultFade() {
  if (prefersReducedMotion()) return
  resultFade.value = false
  requestAnimationFrame(() => {
    resultFade.value = true
    window.clearTimeout(resultFadeTimer)
    resultFadeTimer = window.setTimeout(() => {
      resultFade.value = false
    }, 200)
  })
}

function markChanged(id) {
  changedId.value = id
  window.clearTimeout(changedTimer)
  changedTimer = window.setTimeout(() => {
    changedId.value = null
  }, 700)
}

/* 行先 160ms 淡出，数据刷新后再解除标记，避免列表跳位与闪现 */
async function removeRowAfterFade(id) {
  removingId.value = id
  await new Promise((resolve) => {
    window.setTimeout(resolve, prefersReducedMotion() ? 0 : 160)
  })
  await load()
  removingId.value = null
}

watch(loading, (now, before) => {
  if (before && !now) triggerResultFade()
})

// 关键词是本地过滤：停止输入 200ms 后再淡入，避免逐字闪烁
watch(keyword, () => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(triggerResultFade, 200)
})

onBeforeUnmount(() => {
  window.clearTimeout(resultFadeTimer)
  window.clearTimeout(changedTimer)
  window.clearTimeout(keywordTimer)
})

const enabledCount = computed(() => rules.value.filter((item) => Number(item.status) === 1).length)

const visibleRules = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return rules.value
  return rules.value.filter(
    (item) =>
      String(item.ruleName || '').toLowerCase().includes(text) ||
      String(item.ruleCode || '').toLowerCase().includes(text)
  )
})

const isAiType = computed(() => form.ruleType === 'SEMANTIC' || form.ruleType === 'SENTIMENT')

/** 判定口径：按规则类型给出可读描述，避免直接暴露原始字段 */
function caliberText(row) {
  if (row.ruleType === 'SEMANTIC' || row.ruleType === 'SENTIMENT') {
    return '大模型语义判定'
  }
  if (row.ruleCode === 'SLOW_REPLY') {
    return `回复间隔超过 ${row.thresholdValue} 分钟即命中`
  }
  const words = String(row.keywords || '')
    .split(',')
    .filter(Boolean)
  const preview = words.slice(0, 4).join(' / ')
  const suffix = words.length > 4 ? ` 等 ${words.length} 个词` : ''
  const mode = row.matchMode === 'ABSENT' ? '未出现即命中' : '出现即命中'
  return `${mode}：${preview}${suffix}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (filterType.value) params.ruleType = filterType.value
    if (filterStatus.value !== '' && filterStatus.value !== null) params.status = filterStatus.value
    const list = await getQualityRules(params)
    rules.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e?.response?.data?.message || '质检规则加载失败'
    rules.value = []
  } finally {
    loaded.value = true
    loading.value = false
  }
}

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  ruleCode: '',
  ruleName: '',
  ruleType: 'SERVICE',
  severity: 'MEDIUM',
  deductScore: 5,
  keywords: '',
  matchMode: 'CONTAINS',
  thresholdValue: 3,
  description: '',
  sort: 200,
  status: 1,
  builtin: false
})

const form = reactive(emptyForm())

const formRules = {
  ruleName: [
    { required: true, message: '请填写规则名称', trigger: 'blur' },
    { min: 2, max: 64, message: '规则名称长度须为 2-64 个字符', trigger: 'blur' }
  ],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  thresholdValue: [
    {
      validator: (rule, value, callback) => {
        if (form.ruleCode === 'SLOW_REPLY' && !(Number(value) > 0)) {
          callback(new Error('请填写大于 0 的阈值分钟数'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  keywords: [
    {
      // 语义/情感类由大模型判定，回复超时按阈值计时，两者均不需关键词
      validator: (rule, value, callback) => {
        if (isAiType.value || form.ruleCode === 'SLOW_REPLY') {
          callback()
          return
        }
        if (!String(value || '').trim()) {
          callback(new Error('服务规范类与敏感词类规则必须配置关键词'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openDialog(row) {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      id: row.id,
      ruleCode: row.ruleCode,
      ruleName: row.ruleName,
      ruleType: row.ruleType,
      severity: row.severity || 'MEDIUM',
      deductScore: Number(row.deductScore) || 0,
      keywords: row.keywords || '',
      matchMode: row.matchMode || 'CONTAINS',
      thresholdValue: Number(row.thresholdValue) || 3,
      description: row.description || '',
      sort: row.sort ?? 200,
      status: Number(row.status) === 0 ? 0 : 1,
      builtin: Boolean(row.builtin)
    })
  }
  dialogVisible.value = true
}

async function submit() {
  formRef.value?.clearValidate()
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      ruleName: form.ruleName.trim(),
      ruleType: form.ruleType,
      severity: form.severity,
      deductScore: form.deductScore ?? 0,
      keywords: isAiType.value ? null : form.keywords.trim() || null,
      matchMode: form.matchMode,
      thresholdValue: form.ruleCode === 'SLOW_REPLY' ? form.thresholdValue : 0,
      description: form.description.trim() || null,
      sort: form.sort ?? 0,
      status: form.status
    }
    if (form.id) {
      await updateQualityRule(form.id, payload)
    } else {
      await createQualityRule(payload)
    }
    ElMessage.success(form.id ? '规则已更新' : '规则已创建')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `status-${row.id}`
  try {
    await updateQualityRuleStatus(row.id, target)
    ElMessage.success(target === 1 ? '规则已启用' : '规则已停用，后续质检不再评估该规则')
    await load()
    markChanged(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除规则「${row.ruleName}」后不再参与质检，已生成的评分卡保留当时的命中明细。`,
      '删除规则',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `del-${row.id}`
  try {
    await deleteQualityRule(row.id)
    ElMessage.success('规则已删除')
    await removeRowAfterFade(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.filter-select {
  width: 132px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 1080px;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

/* 筛选结果淡入：160ms，只动位移与透明度 */
.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

/* 删除/停用后行先淡出 160ms 再移除，列表不突然跳位 */
.table-scroll :deep(.el-table__row.row-removing > td.el-table__cell) {
  opacity: 0;
  transition: opacity 160ms var(--zk-ease-in);
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

.cell-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.cell-muted {
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.cell-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
  line-height: 1.5;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-select {
  width: 100%;
}

.form-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

.form-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

@media (max-width: 640px) {
  .form-row {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }
}
</style>
