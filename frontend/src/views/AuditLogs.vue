<template>
  <div class="zk-page">
    <PageHeader :title="pageTitle" :description="pageDescription">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-if="isPlatform"
          v-model="filters.tenantId"
          class="tenant-input"
          placeholder="租户 ID"
          aria-label="按租户 ID 筛选"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <label for="audit-search" class="sr-only">搜索审计记录</label>
        <el-input
          id="audit-search"
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索操作对象 / 操作人 / 摘要"
          aria-label="搜索操作对象、操作人或摘要"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="filters.module"
          class="module-select"
          placeholder="全部模块"
          aria-label="按模块筛选"
          @change="search"
        >
          <el-option
            v-for="option in AUDIT_MODULE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.action"
          class="action-select"
          placeholder="全部动作"
          aria-label="按动作筛选"
          @change="search"
        >
          <el-option
            v-for="option in AUDIT_ACTION_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          class="audit-date"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          aria-label="按操作日期范围筛选"
          unlink-panels
          @change="handleDateChange"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetAll">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ total }}</b> 条记录
        </span>
      </div>

      <div
        class="table-scroll"
        role="region"
        aria-label="操作审计列表"
        tabindex="0"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="pageSize" :cols="8" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="操作审计列表"
          class="rows-stagger"
          :class="fadeClass"
        >
          <el-table-column label="操作时间" width="158">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="isPlatform" label="租户" width="164">
            <template #default="{ row }">
              <div class="tenant-cell">
                <span class="tenant-name">{{ row.tenantName || '未命名租户' }}</span>
                <span class="tenant-code">#{{ row.tenantId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="模块" width="120">
            <template #default="{ row }">
              <StatusPill
                :label="row.moduleName || dictLabel(AUDIT_MODULE_DICT, row.module)"
                :tone="dictTone(AUDIT_MODULE_DICT, row.module)"
              />
            </template>
          </el-table-column>
          <el-table-column label="动作" width="104">
            <template #default="{ row }">
              <StatusPill
                :label="row.actionName || dictLabel(AUDIT_ACTION_DICT, row.action)"
                :tone="dictTone(AUDIT_ACTION_DICT, row.action)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="操作对象" min-width="196">
            <template #default="{ row }">
              <div class="target-cell">
                <span class="target-name">{{ row.targetName || '—' }}</span>
                <span class="target-sub">{{ targetHint(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="变更摘要" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="detail-text">{{ row.detail || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作人" width="140">
            <template #default="{ row }">
              <div class="operator-cell">
                <span class="operator-name">{{ row.operatorName || '未知' }}</span>
                <span class="operator-role">{{ dictLabel(ROLE_DICT, row.operatorRole, '—') }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源 IP" width="132">
            <template #default="{ row }">
              <span class="mono-cell">{{ row.ip || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="82" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" class="op-btn" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无匹配的审计记录'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : hasFilter
                    ? '当前筛选条件下没有记录，试试放宽日期或清空关键词'
                    : '还没有操作记录，保存一次配置后即可看到留痕'
              "
            >
              <el-button v-if="error" @click="load">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="resetAll">清空筛选</el-button>
              <el-button v-else @click="load">刷新列表</el-button>
            </ZkEmpty>
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
          aria-label="操作审计分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      审计日志只追加不修改：记录操作人、操作时间与变更前后值；新增只存变更后快照，删除只存变更前快照，密码与密钥等敏感字段统一脱敏为 ******
    </div>

    <el-drawer v-model="drawerVisible" size="640px" title="审计详情" append-to-body>
      <div v-if="detail" class="detail zk-drawer-stagger">
        <div class="detail-head">
          <div class="detail-title">
            <StatusPill
              :label="detail.moduleName || dictLabel(AUDIT_MODULE_DICT, detail.module)"
              :tone="dictTone(AUDIT_MODULE_DICT, detail.module)"
            />
            <StatusPill
              :label="detail.actionName || dictLabel(AUDIT_ACTION_DICT, detail.action)"
              :tone="dictTone(AUDIT_ACTION_DICT, detail.action)"
              dot
            />
          </div>
          <p class="detail-summary">{{ detail.detail || '无补充说明' }}</p>
        </div>

        <dl class="meta-grid">
          <div><dt>操作对象</dt><dd>{{ detail.targetName || '—' }}</dd></div>
          <div><dt>对象类型</dt><dd>{{ targetHint(detail) }}</dd></div>
          <div><dt>操作人</dt><dd>{{ detail.operatorName || '未知' }}</dd></div>
          <div><dt>操作角色</dt><dd>{{ dictLabel(ROLE_DICT, detail.operatorRole, '—') }}</dd></div>
          <div><dt>操作时间</dt><dd>{{ formatDateTime(detail.createTime) }}</dd></div>
          <div><dt>来源 IP</dt><dd>{{ detail.ip || '—' }}</dd></div>
          <div v-if="isPlatform"><dt>归属租户</dt><dd>{{ detail.tenantName || '—' }}（#{{ detail.tenantId }}）</dd></div>
          <div><dt>请求</dt><dd>{{ requestText(detail) }}</dd></div>
        </dl>

        <section class="snapshot-block" aria-label="变更快照">
          <div class="snapshot-head">
            <h4>变更快照</h4>
            <span class="snapshot-hint">敏感字段已脱敏</span>
          </div>
          <div class="snapshot-grid">
            <article class="snapshot-card">
              <header>变更前</header>
              <pre v-if="prettyBefore">{{ prettyBefore }}</pre>
              <p v-else class="snapshot-empty">{{ emptyBeforeText }}</p>
            </article>
            <article class="snapshot-card">
              <header>变更后</header>
              <pre v-if="prettyAfter">{{ prettyAfter }}</pre>
              <p v-else class="snapshot-empty">{{ emptyAfterText }}</p>
            </article>
          </div>
        </section>

        <p v-if="detail.userAgent" class="agent-line">
          客户端：{{ detail.userAgent }}
        </p>
      </div>
      <div v-else class="detail-loading">
        <ZkSkeleton v-if="detailLoading" variant="lines" :rows="8" />
        <ZkEmpty v-else title="审计详情加载失败" hint="该记录可能已被清理，或当前账号无权查看，可关闭后重试" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import {
  getAdminAuditLogDetail,
  getAdminAuditLogs,
  getPlatformAuditLogDetail,
  getPlatformAuditLogs
} from '@/api'
import { getUser } from '@/api/http'
import { usePagedList } from '@/composables/usePagedList'
import {
  AUDIT_ACTION_DICT,
  AUDIT_ACTION_OPTIONS,
  AUDIT_MODULE_DICT,
  AUDIT_MODULE_OPTIONS,
  ROLE_DICT,
  dictLabel,
  dictTone,
  formatDateTime
} from '@/utils/dict'

// 平台管理员与租户管理员共用本页：仅数据范围（是否跨租户）与筛选项不同
const isPlatform = getUser()?.roleCode === 'PLATFORM_ADMIN'

const pageTitle = isPlatform ? '操作审计（平台）' : '操作审计'
const pageDescription = isPlatform
  ? '平台侧关键写操作的统一留痕：开通与变更租户、套餐调整、续期、删除等，可按租户、模块、动作与日期检索变更前后值。'
  : '本租户关键写操作的统一留痕：账号、知识库、大模型、接入配置与工单规则，可按模块、动作与日期检索变更前后值。'

const initialFilters = isPlatform
  ? { tenantId: '', keyword: '', module: '', action: '', startDate: '', endDate: '' }
  : { keyword: '', module: '', action: '', startDate: '', endDate: '' }

const {
  filters,
  rows,
  total,
  pageNum,
  pageSize,
  loading,
  loaded,
  error,
  load,
  search,
  reset,
  changePage,
  changeSize
} = usePagedList(isPlatform ? getPlatformAuditLogs : getAdminAuditLogs, initialFilters)

// 筛选 / 翻页结果到达时重播 160ms 淡入（只淡不位移，避免表格重排）
const listTick = ref(0)
watch(rows, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

const fetchDetail = isPlatform ? getPlatformAuditLogDetail : getAdminAuditLogDetail

const dateRange = ref([])

const hasFilter = computed(
  () =>
    Boolean(filters.keyword) ||
    Boolean(filters.module) ||
    Boolean(filters.action) ||
    Boolean(filters.tenantId) ||
    Boolean(filters.startDate) ||
    Boolean(filters.endDate)
)

function handleDateChange(value) {
  if (Array.isArray(value) && value.length === 2) {
    filters.startDate = value[0] || ''
    filters.endDate = value[1] || ''
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  search()
}

function resetAll() {
  dateRange.value = []
  reset()
}

/** 对象类型 · ID，列表与详情共用 */
function targetHint(row) {
  if (!row) return '—'
  const type = row.targetType || '未分类'
  return row.targetId ? `${type} · #${row.targetId}` : type
}

function requestText(row) {
  const method = row?.requestMethod
  const uri = row?.requestUri
  if (!method && !uri) return '—'
  return [method, uri].filter(Boolean).join(' ')
}

/* ---------- 详情抽屉 ---------- */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function openDetail(row) {
  drawerVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await fetchDetail(row.id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

/** 快照 JSON 美化，解析失败时原样展示，避免详情空白 */
function prettyJson(value) {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}

const prettyBefore = computed(() => prettyJson(detail.value?.beforeData))
const prettyAfter = computed(() => prettyJson(detail.value?.afterData))
const emptyBeforeText = computed(() =>
  detail.value?.action === 'CREATE' ? '新增操作无变更前快照' : '未记录变更前快照'
)
const emptyAfterText = computed(() =>
  detail.value?.action === 'DELETE' ? '删除操作无变更后快照' : '未记录变更后快照'
)

onMounted(load)
</script>

<style scoped>
/* 首屏骨架与真实表格同列，加载前后不跳 */
.table-skeleton {
  padding: 12px 14px;
}

/* 首屏前 10 行按 30ms 依次浮现（只淡不位移，不打断扫读） */
.rows-stagger :deep(.el-table__body tr:nth-child(-n + 10)) {
  animation: zk-row-in 160ms var(--zk-ease-out) both;
}

.rows-stagger :deep(.el-table__body tr:nth-child(2)) {
  animation-delay: 30ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(3)) {
  animation-delay: 60ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(4)) {
  animation-delay: 90ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(5)) {
  animation-delay: 120ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(6)) {
  animation-delay: 150ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(7)) {
  animation-delay: 180ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(8)) {
  animation-delay: 210ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(9)) {
  animation-delay: 240ms;
}

.rows-stagger :deep(.el-table__body tr:nth-child(10)) {
  animation-delay: 270ms;
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

.tenant-input {
  width: 108px;
}

.module-select {
  width: 132px;
}

.action-select {
  width: 118px;
}

.audit-date {
  width: 264px;
}

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
  min-width: 1040px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.tenant-cell,
.target-cell,
.operator-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tenant-name,
.target-name,
.operator-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.tenant-code,
.target-sub,
.operator-role {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.tenant-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.detail-text {
  font-size: 12px;
  color: var(--zk-text-2);
}

.mono-cell {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

/* ---------- 详情抽屉 ---------- */
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--zk-border);
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-summary {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--zk-text-2);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px 16px;
  margin: 0;
}

.meta-grid dt {
  font-size: 11.5px;
  color: var(--zk-text-3);
  margin-bottom: 2px;
}

.meta-grid dd {
  margin: 0;
  font-size: 12.5px;
  color: var(--zk-text);
  word-break: break-all;
}

.snapshot-block {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  overflow: hidden;
}

.snapshot-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: var(--zk-surface-2);
  border-bottom: 1px solid var(--zk-border);
}

.snapshot-head h4 {
  margin: 0;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.snapshot-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1px;
  background: var(--zk-border);
}

.snapshot-card {
  background: var(--zk-surface);
  min-width: 0;
}

.snapshot-card header {
  padding: 7px 12px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--zk-text-2);
  border-bottom: 1px solid var(--zk-border);
}

.snapshot-card pre {
  margin: 0;
  padding: 10px 12px;
  max-height: 320px;
  overflow: auto;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-2);
  white-space: pre-wrap;
  word-break: break-all;
}

.snapshot-empty {
  margin: 0;
  padding: 14px 12px;
  font-size: 12px;
  color: var(--zk-text-3);
}

.agent-line {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
  word-break: break-all;
}

.detail-loading {
  padding: 40px 0;
  font-size: 12.5px;
  color: var(--zk-text-3);
  text-align: center;
}

@media (max-width: 768px) {
  .tenant-input,
  .module-select,
  .action-select,
  .audit-date {
    width: 100%;
  }
}
</style>
