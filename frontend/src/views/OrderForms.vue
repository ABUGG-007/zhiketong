<template>
  <div class="zk-page">
    <PageHeader
      title="工单表单"
      description="自定义字段与工单模板构成结构化建单能力：管理员维护字段与模板，创建工单时按模板收集订单号、金额等业务信息，字段值按快照留存，模板调整不影响历史工单。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="activeTab === 'fields'" type="primary" :icon="Plus" @click="openFieldDialog(null)">
          新增字段
        </el-button>
        <el-button v-else type="primary" :icon="Plus" @click="openTemplateDialog(null)">
          新增模板
        </el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <el-tabs v-model="activeTab" class="form-tabs">
        <el-tab-pane label="字段配置" name="fields">
          <div class="zk-toolbar">
            <label for="field-search" class="sr-only">搜索字段</label>
            <el-input
              id="field-search"
              v-model="keyword"
              class="el-input--wide"
              placeholder="搜索字段名称 / 编码"
              aria-label="搜索字段"
              :prefix-icon="Search"
              clearable
            />
            <div class="grow" />
            <span class="toolbar-meta" aria-live="polite">
              共 <b class="zk-num">{{ filteredFields.length }}</b> 个字段 · 启用
              <b class="zk-num">{{ enabledFieldCount }}</b> 个
            </span>
          </div>

          <div
            class="table-scroll"
            :class="{ 'table-result-fade': resultFade }"
            role="region"
            aria-label="字段列表"
            tabindex="0"
          >
            <ZkSkeleton v-if="!loaded" variant="table" :rows="7" :cols="8" class="table-skeleton" />
            <el-table
              v-else
              v-loading="loading"
              class="zk-table-fade-in"
              :data="filteredFields"
              row-key="id"
              aria-label="字段列表"
              :row-class-name="rowClassName"
            >
              <el-table-column label="字段" min-width="220">
                <template #default="{ row }">
                  <div class="cell-stack">
                    <span class="cell-strong">{{ row.fieldName }}</span>
                    <span class="cell-muted">{{ row.fieldCode }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <span class="cell-muted">{{ row.fieldTypeLabel || row.fieldType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="选项" min-width="180">
                <template #default="{ row }">
                  <div v-if="row.options?.length" class="tag-wrap">
                    <el-tag v-for="option in row.options" :key="option" size="small" effect="plain">
                      {{ option }}
                    </el-tag>
                  </div>
                  <span v-else class="cell-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="必填" width="78">
                <template #default="{ row }">
                  <span class="cell-muted">{{ Number(row.required) === 1 ? '必填' : '选填' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="88">
                <template #default="{ row }">
                  <StatusPill
                    :label="Number(row.status) === 1 ? '启用' : '停用'"
                    :tone="Number(row.status) === 1 ? 'teal' : 'red'"
                    :class="{ 'zk-flash': changedId === row.id }"
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
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <div class="row-actions">
                    <el-button link type="primary" class="op-btn" @click="openFieldDialog(row)">编辑</el-button>
                    <el-button
                      link
                      :type="Number(row.status) === 1 ? 'warning' : 'success'"
                      class="op-btn"
                      :loading="actionBusy === `field-${row.id}`"
                      @click="toggleFieldStatus(row)"
                    >
                      {{ Number(row.status) === 1 ? '停用' : '启用' }}
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      class="op-btn"
                      :loading="actionBusy === `field-${row.id}`"
                      @click="confirmDeleteField(row)"
                    >
                      删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>

              <template #empty>
                <ZkEmpty
                  size="sm"
                  :title="error ? '字段配置加载失败' : keyword ? '没有匹配的字段' : '暂无自定义字段'"
                  :hint="
                    error
                      ? '请稍后重新加载，或确认后端服务是否正常'
                      : keyword
                        ? '当前关键词下没有字段，试试清空搜索'
                        : '点击右上角「新增字段」创建第一个字段'
                  "
                >
                  <el-button v-if="error" @click="load">重新加载</el-button>
                  <el-button v-else-if="!keyword" type="primary" @click="openFieldDialog(null)">
                    新增字段
                  </el-button>
                </ZkEmpty>
              </template>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="工单模板" name="templates">
          <div class="zk-toolbar">
            <label for="template-search" class="sr-only">搜索模板</label>
            <el-input
              id="template-search"
              v-model="keyword"
              class="el-input--wide"
              placeholder="搜索模板名称 / 说明"
              aria-label="搜索模板"
              :prefix-icon="Search"
              clearable
            />
            <div class="grow" />
            <span class="toolbar-meta" aria-live="polite">
              共 <b class="zk-num">{{ filteredTemplates.length }}</b> 个模板
            </span>
          </div>

          <div
            class="table-scroll"
            :class="{ 'table-result-fade': resultFade }"
            role="region"
            aria-label="模板列表"
            tabindex="0"
          >
            <ZkSkeleton v-if="!loaded" variant="table" :rows="6" :cols="7" class="table-skeleton" />
            <el-table
              v-else
              v-loading="loading"
              class="zk-table-fade-in"
              :data="filteredTemplates"
              row-key="id"
              aria-label="模板列表"
              :row-class-name="rowClassName"
            >
              <el-table-column label="模板" min-width="220">
                <template #default="{ row }">
                  <div class="cell-stack">
                    <span class="cell-strong">{{ row.templateName }}</span>
                    <span class="cell-muted">{{ row.description || '未填写模板说明' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="绑定字段" min-width="260">
                <template #default="{ row }">
                  <div v-if="row.fields?.length" class="tag-wrap">
                    <el-tag
                      v-for="field in row.fields"
                      :key="field.id"
                      size="small"
                      :type="Number(field.required) === 1 ? 'danger' : 'info'"
                      effect="plain"
                    >
                      {{ field.fieldName }}
                    </el-tag>
                  </div>
                  <span v-else class="cell-muted">未绑定字段</span>
                </template>
              </el-table-column>
              <el-table-column label="默认分类" width="110">
                <template #default="{ row }">
                  <span class="cell-muted">{{ row.category || '建单时选择' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="默认优先级" width="110">
                <template #default="{ row }">
                  <span class="cell-muted">{{ row.defaultPriority || 'P2' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="默认技能组" width="130">
                <template #default="{ row }">
                  <span class="cell-muted">{{ row.skillGroupName || '不限' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="88">
                <template #default="{ row }">
                  <StatusPill
                    :label="Number(row.status) === 1 ? '启用' : '停用'"
                    :tone="Number(row.status) === 1 ? 'teal' : 'red'"
                    :class="{ 'zk-flash': changedId === row.id }"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <div class="row-actions">
                    <el-button link type="primary" class="op-btn" @click="openTemplateDialog(row)">编辑</el-button>
                    <el-button
                      link
                      :type="Number(row.status) === 1 ? 'warning' : 'success'"
                      class="op-btn"
                      :loading="actionBusy === `template-${row.id}`"
                      @click="toggleTemplateStatus(row)"
                    >
                      {{ Number(row.status) === 1 ? '停用' : '启用' }}
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      class="op-btn"
                      :loading="actionBusy === `template-${row.id}`"
                      @click="confirmDeleteTemplate(row)"
                    >
                      删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>

              <template #empty>
                <ZkEmpty
                  size="sm"
                  :title="error ? '工单模板加载失败' : keyword ? '没有匹配的模板' : '暂无工单模板'"
                  :hint="
                    error
                      ? '请稍后重新加载，或确认后端服务是否正常'
                      : keyword
                        ? '当前关键词下没有模板，试试清空搜索'
                        : '点击右上角「新增模板」创建第一个模板'
                  "
                >
                  <el-button v-if="error" @click="load">重新加载</el-button>
                  <el-button v-else-if="!keyword" type="primary" @click="openTemplateDialog(null)">
                    新增模板
                  </el-button>
                </ZkEmpty>
              </template>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      字段编码创建后不可修改 · 被模板引用的字段需先从模板移除 · 停用字段仅隐藏新建表单，历史工单仍按快照展示
    </div>

    <el-dialog
      v-model="fieldDialogVisible"
      :title="fieldForm.id ? '编辑字段' : '新增字段'"
      width="560px"
      append-to-body
    >
      <el-form ref="fieldFormRef" :model="fieldForm" :rules="fieldRules" label-position="top">
        <div class="form-row">
          <el-form-item label="字段名称" prop="fieldName">
            <el-input v-model="fieldForm.fieldName" maxlength="64" show-word-limit placeholder="如：订单号" />
          </el-form-item>
          <el-form-item label="字段编码" prop="fieldCode">
            <el-input
              v-model="fieldForm.fieldCode"
              maxlength="32"
              :disabled="Boolean(fieldForm.id)"
              placeholder="如：order_no"
            />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="字段类型" prop="fieldType">
            <el-select v-model="fieldForm.fieldType" class="full-select" @change="onFieldTypeChange">
              <el-option
                v-for="item in FIELD_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="fieldForm.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item v-if="fieldForm.fieldType === 'SELECT'" label="下拉选项" prop="options">
          <el-select
            v-model="fieldForm.options"
            multiple
            filterable
            allow-create
            default-first-option
            class="full-select"
            placeholder="输入选项后回车，最多 20 项"
          >
            <el-option v-for="option in fieldForm.options" :key="option" :label="option" :value="option" />
          </el-select>
          <p class="form-hint">选项顺序即下拉展示顺序，单个选项不超过 32 个字符</p>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="默认值">
            <el-input v-model="fieldForm.defaultValue" maxlength="255" placeholder="建单时预填，可留空" />
          </el-form-item>
          <el-form-item label="输入提示">
            <el-input v-model="fieldForm.placeholder" maxlength="100" placeholder="如：请输入 12 位订单号" />
          </el-form-item>
        </div>
        <el-form-item label="字段说明">
          <el-input
            v-model="fieldForm.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="展示在表单字段下方，说明填写要求"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="是否必填">
            <el-switch
              v-model="fieldForm.required"
              :active-value="1"
              :inactive-value="0"
              active-text="必填"
              inactive-text="选填"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              v-model="fieldForm.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="fieldDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="fieldSaving" @click="submitField">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="templateDialogVisible"
      :title="templateForm.id ? '编辑工单模板' : '新增工单模板'"
      width="600px"
      append-to-body
    >
      <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-position="top">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="templateForm.templateName" maxlength="64" show-word-limit placeholder="如：退款工单" />
        </el-form-item>
        <el-form-item label="绑定字段" prop="fieldIds">
          <el-select
            v-model="templateForm.fieldIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            class="full-select"
            placeholder="选择模板需要填写的字段"
          >
            <el-option
              v-for="field in enabledFields"
              :key="field.id"
              :label="`${field.fieldName}（${field.fieldCode}）`"
              :value="field.id"
            />
          </el-select>
          <p class="form-hint">字段顺序即建单表单顺序，单个模板最多绑定 30 个字段</p>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="默认问题分类">
            <el-select v-model="templateForm.category" clearable class="full-select" placeholder="建单时选择">
              <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认优先级">
            <el-select v-model="templateForm.defaultPriority" clearable class="full-select" placeholder="按 P2 处理">
              <el-option
                v-for="item in priorityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="默认技能组">
          <el-select v-model="templateForm.skillGroupId" clearable class="full-select" placeholder="不限制技能组">
            <el-option
              v-for="group in skillGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模板说明">
          <el-input
            v-model="templateForm.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="说明该模板适用场景，创建工单时展示"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="排序号">
            <el-input-number
              v-model="templateForm.sort"
              :min="0"
              :max="9999"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              v-model="templateForm.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateSaving" @click="submitTemplate">保存</el-button>
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
  createOrderField,
  createOrderTemplate,
  deleteOrderField,
  deleteOrderTemplate,
  getOrderFields,
  getOrderTemplates,
  getSkillGroups,
  updateOrderField,
  updateOrderFieldStatus,
  updateOrderTemplate,
  updateOrderTemplateStatus
} from '@/api'
import { prefersReducedMotion } from '@/composables/motion'
import { ORDER_CATEGORIES, PRIORITY_OPTIONS, formatDateTime } from '@/utils/dict'

const FIELD_TYPE_OPTIONS = [
  { label: '文本', value: 'TEXT' },
  { label: '多行文本', value: 'MULTILINE' },
  { label: '下拉', value: 'SELECT' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' }
]

const categories = ORDER_CATEGORIES
const priorityOptions = PRIORITY_OPTIONS.filter((item) => item.value)

const activeTab = ref('fields')
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const keyword = ref('')
const actionBusy = ref('')

/* 首屏骨架屏；切换页签/搜索后结果区 160ms 淡入；停用与删除的行先淡出再移除 */
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

watch(activeTab, triggerResultFade)

watch(keyword, () => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(triggerResultFade, 200)
})

onBeforeUnmount(() => {
  window.clearTimeout(resultFadeTimer)
  window.clearTimeout(changedTimer)
  window.clearTimeout(keywordTimer)
})

const fields = ref([])
const templates = ref([])
const skillGroups = ref([])

const enabledFields = computed(() => fields.value.filter((item) => Number(item.status) === 1))
const enabledFieldCount = computed(() => enabledFields.value.length)

const filteredFields = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return fields.value
  return fields.value.filter(
    (item) =>
      item.fieldName?.toLowerCase().includes(value) || item.fieldCode?.toLowerCase().includes(value)
  )
})

const filteredTemplates = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return templates.value
  return templates.value.filter(
    (item) =>
      item.templateName?.toLowerCase().includes(value) ||
      item.description?.toLowerCase().includes(value)
  )
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [fieldList, templateList, groups] = await Promise.all([
      getOrderFields({}),
      getOrderTemplates({}),
      getSkillGroups({ withMembers: false }).catch(() => [])
    ])
    fields.value = Array.isArray(fieldList) ? fieldList : []
    templates.value = Array.isArray(templateList) ? templateList : []
    skillGroups.value = Array.isArray(groups) ? groups : []
  } catch (e) {
    error.value = e?.response?.data?.message || '表单配置加载失败'
  } finally {
    loaded.value = true
    loading.value = false
  }
}

/* ---------- 字段 ---------- */
const fieldDialogVisible = ref(false)
const fieldSaving = ref(false)
const fieldFormRef = ref(null)

const emptyFieldForm = () => ({
  id: null,
  fieldCode: '',
  fieldName: '',
  fieldType: 'TEXT',
  options: [],
  required: 0,
  defaultValue: '',
  placeholder: '',
  description: '',
  sort: 0,
  status: 1
})

const fieldForm = reactive(emptyFieldForm())

const fieldRules = computed(() => ({
  fieldCode: [
    { required: !fieldForm.id, message: '请填写字段编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]{1,31}$/,
      message: '小写字母开头，可含数字与下划线，长度 2-32 位',
      trigger: 'blur'
    }
  ],
  fieldName: [
    { required: true, message: '请填写字段名称', trigger: 'blur' },
    { min: 2, max: 64, message: '字段名称长度须为 2-64 个字符', trigger: 'blur' }
  ],
  fieldType: [{ required: true, message: '请选择字段类型', trigger: 'change' }],
  options: [
    {
      validator: (rule, value, callback) => {
        if (fieldForm.fieldType !== 'SELECT') return callback()
        if (!Array.isArray(value) || !value.length) return callback(new Error('下拉字段至少配置一个选项'))
        return callback()
      },
      trigger: 'change'
    }
  ]
}))

function openFieldDialog(row) {
  Object.assign(fieldForm, emptyFieldForm())
  if (row) {
    Object.assign(fieldForm, {
      id: row.id,
      fieldCode: row.fieldCode,
      fieldName: row.fieldName,
      fieldType: row.fieldType,
      options: [...(row.options || [])],
      required: Number(row.required) === 1 ? 1 : 0,
      defaultValue: row.defaultValue || '',
      placeholder: row.placeholder || '',
      description: row.description || '',
      sort: row.sort ?? 0,
      status: Number(row.status) === 0 ? 0 : 1
    })
  }
  fieldDialogVisible.value = true
}

function onFieldTypeChange() {
  if (fieldForm.fieldType !== 'SELECT') {
    fieldForm.options = []
  }
}

async function submitField() {
  try {
    await fieldFormRef.value.validate()
  } catch {
    return
  }
  fieldSaving.value = true
  try {
    const payload = {
      fieldCode: fieldForm.fieldCode.trim(),
      fieldName: fieldForm.fieldName.trim(),
      fieldType: fieldForm.fieldType,
      options: fieldForm.fieldType === 'SELECT' ? fieldForm.options : null,
      required: fieldForm.required,
      defaultValue: fieldForm.defaultValue.trim() || null,
      placeholder: fieldForm.placeholder.trim() || null,
      description: fieldForm.description.trim() || null,
      sort: fieldForm.sort ?? 0,
      status: fieldForm.status
    }
    if (fieldForm.id) {
      await updateOrderField(fieldForm.id, payload)
    } else {
      await createOrderField(payload)
    }
    ElMessage.success(fieldForm.id ? '字段已更新' : '字段已创建')
    fieldDialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    fieldSaving.value = false
  }
}

async function toggleFieldStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `field-${row.id}`
  try {
    await updateOrderFieldStatus(row.id, target)
    ElMessage.success(target === 1 ? '字段已启用' : '字段已停用')
    await load()
    markChanged(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDeleteField(row) {
  try {
    await ElMessageBox.confirm(
      `删除字段「${row.fieldName}」后不可恢复，历史工单中的字段值仍会保留。`,
      '删除字段',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `field-${row.id}`
  try {
    await deleteOrderField(row.id)
    ElMessage.success('字段已删除')
    await removeRowAfterFade(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请先在模板中移除该字段')
  } finally {
    actionBusy.value = ''
  }
}

/* ---------- 模板 ---------- */
const templateDialogVisible = ref(false)
const templateSaving = ref(false)
const templateFormRef = ref(null)

const emptyTemplateForm = () => ({
  id: null,
  templateName: '',
  category: '',
  defaultPriority: '',
  skillGroupId: null,
  fieldIds: [],
  description: '',
  sort: 0,
  status: 1
})

const templateForm = reactive(emptyTemplateForm())

const templateRules = {
  templateName: [
    { required: true, message: '请填写模板名称', trigger: 'blur' },
    { min: 2, max: 64, message: '模板名称长度须为 2-64 个字符', trigger: 'blur' }
  ]
}

function openTemplateDialog(row) {
  Object.assign(templateForm, emptyTemplateForm())
  if (row) {
    Object.assign(templateForm, {
      id: row.id,
      templateName: row.templateName,
      category: row.category || '',
      defaultPriority: row.defaultPriority || '',
      skillGroupId: row.skillGroupId || null,
      fieldIds: [...(row.fieldIds || [])],
      description: row.description || '',
      sort: row.sort ?? 0,
      status: Number(row.status) === 0 ? 0 : 1
    })
  }
  templateDialogVisible.value = true
}

async function submitTemplate() {
  try {
    await templateFormRef.value.validate()
  } catch {
    return
  }
  templateSaving.value = true
  try {
    const payload = {
      templateName: templateForm.templateName.trim(),
      category: templateForm.category || null,
      defaultPriority: templateForm.defaultPriority || null,
      skillGroupId: templateForm.skillGroupId || null,
      fieldIds: templateForm.fieldIds,
      description: templateForm.description.trim() || null,
      sort: templateForm.sort ?? 0,
      status: templateForm.status
    }
    if (templateForm.id) {
      await updateOrderTemplate(templateForm.id, payload)
    } else {
      await createOrderTemplate(payload)
    }
    ElMessage.success(templateForm.id ? '模板已更新' : '模板已创建')
    templateDialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请稍后重试')
  } finally {
    templateSaving.value = false
  }
}

async function toggleTemplateStatus(row) {
  const target = Number(row.status) === 1 ? 0 : 1
  actionBusy.value = `template-${row.id}`
  try {
    await updateOrderTemplateStatus(row.id, target)
    ElMessage.success(target === 1 ? '模板已启用' : '模板已停用')
    await load()
    markChanged(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    actionBusy.value = ''
  }
}

async function confirmDeleteTemplate(row) {
  try {
    await ElMessageBox.confirm(
      `删除模板「${row.templateName}」后不可恢复，已创建的工单字段值仍会保留。`,
      '删除模板',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionBusy.value = `template-${row.id}`
  try {
    await deleteOrderTemplate(row.id)
    ElMessage.success('模板已删除')
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
.form-tabs :deep(.el-tabs__header) {
  margin-bottom: 4px;
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
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.cell-muted {
  font-size: 12px;
  color: var(--zk-text-3);
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
