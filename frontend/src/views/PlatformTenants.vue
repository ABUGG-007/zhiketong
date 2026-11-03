<template>
  <div class="zk-page">
    <PageHeader
      title="租户与套餐"
      description="平台侧统一开通与运维租户：一次提交同时创建租户、商户管理员与网页接入配置，套餐额度由这里维护并即时生效。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">开通租户</el-button>
      </template>
    </PageHeader>

    <!-- 套餐能力概览：数据来自 tenant_plan，点击可按套餐筛选租户 -->
    <section class="plan-strip" aria-label="套餐能力概览">
      <article
        v-for="(plan, index) in plans"
        :key="plan.code"
        v-reveal="{ index: revealIndex(index) }"
        class="plan-chip zk-hover-lift zk-press"
        :class="{ 'is-active': filters.planCode === plan.code }"
        role="button"
        tabindex="0"
        :aria-pressed="filters.planCode === plan.code"
        @click="filterByPlan(plan.code)"
        @keydown.enter.prevent="filterByPlan(plan.code)"
        @keydown.space.prevent="filterByPlan(plan.code)"
      >
        <div class="plan-chip-head">
          <span class="plan-chip-name">{{ plan.name }}</span>
          <span class="plan-chip-price">{{ plan.price === 0 ? '免费' : `¥${plan.price}/${plan.period}` }}</span>
        </div>
        <p class="plan-chip-quota">
          坐席 {{ formatQuota(plan.seatLimit) }} · 会话 {{ formatQuota(plan.sessionLimit) }}/月 ·
          知识库 {{ formatQuota(plan.kbLimit) }}
        </p>
        <p class="plan-chip-tagline">{{ plan.tagline }}</p>
      </article>
      <p v-if="!plans.length" class="plan-strip-empty">套餐配置加载中…</p>
    </section>

    <div class="zk-card">
      <div class="zk-toolbar">
        <label for="tenant-search" class="sr-only">搜索租户</label>
        <el-input
          id="tenant-search"
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索租户名称 / 编码 / 联系人"
          aria-label="搜索租户"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
        />
        <el-select v-model="filters.status" placeholder="全部状态" class="status-select" aria-label="按状态筛选">
          <el-option
            v-for="option in TENANT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="filters.planCode" placeholder="全部套餐" class="plan-select" aria-label="按套餐筛选">
          <el-option label="全部套餐" value="" />
          <el-option v-for="plan in plans" :key="plan.code" :label="plan.name" :value="plan.code" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetAll">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta" aria-live="polite">
          共 <b class="zk-num">{{ total }}</b> 个租户
        </span>
      </div>

      <div class="table-scroll" role="region" aria-label="租户列表" tabindex="0">
        <ZkSkeleton v-if="!loaded" variant="table" :rows="pageSize" :cols="7" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          :data="rows"
          row-key="id"
          aria-label="租户列表"
          :class="fadeClass"
        >
          <el-table-column label="租户" min-width="220">
            <template #default="{ row }">
              <div class="tenant-cell">
                <span class="tenant-name">{{ row.tenantName }}</span>
                <span class="tenant-code">{{ row.tenantCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="套餐" width="112">
            <template #default="{ row }">
              <StatusPill
                :key="`plan-${row.planCode}`"
                class="plan-pill"
                :label="row.planName || row.planCode || '—'"
                tone="indigo"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusPill
                :key="`status-${statusOf(row)}`"
                class="status-pop"
                :label="dictLabel(TENANT_STATUS_DICT, statusOf(row))"
                :tone="dictTone(TENANT_STATUS_DICT, statusOf(row))"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="套餐额度" min-width="220">
            <template #default="{ row }">
              <div class="quota-cell">
                <span>坐席 {{ formatQuota(row.seatLimit) }} · 知识库 {{ formatQuota(row.kbLimit) }}</span>
                <span class="quota-sub">
                  会话 {{ formatQuota(row.sessionLimit) }}/月 · 工单 {{ formatQuota(row.orderLimit) }}/月
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="到期时间" width="172">
            <template #default="{ row }">
              <div class="expire-cell">
                <span class="cell-time">{{ row.expireTime ? formatDateTime(row.expireTime) : '不限' }}</span>
                <span class="expire-hint" :class="expireTone(row)">{{ expireText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="联系人" width="150">
            <template #default="{ row }">
              <div class="contact-cell">
                <span>{{ row.contactName || '未填写' }}</span>
                <span class="cell-muted">{{ row.contactPhone || '—' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" class="op-btn" @click="openDetail(row.id)">详情</el-button>
                <el-button link type="primary" class="op-btn" @click="openEdit(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="(command) => onRowCommand(command, row)">
                  <el-button link type="primary" class="op-btn" :loading="busyId === row.id">
                    更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="plan">变更套餐</el-dropdown-item>
                      <el-dropdown-item command="renew">续期</el-dropdown-item>
                      <el-dropdown-item command="snapshot">刷新用量快照</el-dropdown-item>
                      <el-dropdown-item command="status" divided>
                        {{ row.status === 'ENABLED' ? '停用租户' : '启用租户' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除租户</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error || '暂无匹配的租户'"
              :hint="
                error
                  ? '加载失败，请稍后重试'
                  : hasFilter
                    ? '当前筛选条件下没有租户，试试清空搜索或切换状态'
                    : '还没有租户，点击右上角「开通租户」创建第一个'
              "
            >
              <el-button v-if="error" @click="reload">重新加载</el-button>
              <el-button v-else-if="hasFilter" @click="resetAll">清空筛选</el-button>
              <el-button v-else type="primary" @click="openCreate">开通租户</el-button>
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
          aria-label="租户分页"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      停用或标记到期后该租户账号立即无法登录；降级套餐时若存量坐席或知识条目超过新套餐额度会被拒绝，避免业务当场不可用
    </div>

    <!-- 租户详情 -->
    <el-drawer v-model="drawerVisible" size="560px" :title="detail?.tenantName || '租户详情'" append-to-body>
      <div v-if="detail" class="detail zk-drawer-stagger">
        <div class="detail-head">
          <div>
            <div class="detail-name">{{ detail.tenantName }}</div>
            <div class="detail-code">{{ detail.tenantCode }}</div>
          </div>
          <StatusPill
            :key="`d-status-${statusOf(detail)}`"
            class="status-pop"
            :label="dictLabel(TENANT_STATUS_DICT, statusOf(detail))"
            :tone="dictTone(TENANT_STATUS_DICT, statusOf(detail))"
            dot
          />
        </div>

        <dl class="meta-grid">
          <div><dt>当前套餐</dt><dd>{{ detail.planName }}（{{ detail.planCode }}）</dd></div>
          <div><dt>服务到期</dt><dd>{{ detail.expireTime ? formatDateTime(detail.expireTime) : '不限' }}</dd></div>
          <div><dt>租户管理员</dt><dd>{{ detail.adminUsername || '—' }}<span v-if="detail.adminNickname"> · {{ detail.adminNickname }}</span></dd></div>
          <div><dt>联系人</dt><dd>{{ detail.contactName || '未填写' }} {{ detail.contactPhone || '' }}</dd></div>
          <div><dt>联系邮箱</dt><dd>{{ detail.contactEmail || '未填写' }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatDateTime(detail.createTime) }}</dd></div>
          <div><dt>用量快照</dt><dd>{{ detail.snapshotTime ? formatDateTime(detail.snapshotTime) : '暂无快照' }}</dd></div>
          <div><dt>备注</dt><dd>{{ detail.remark || '—' }}</dd></div>
        </dl>

        <div class="quota-block">
          <div class="quota-block-head">
            <h4>本月额度水位</h4>
            <el-button
              link
              type="primary"
              :loading="snapshotLoading"
              @click="refreshSnapshot(detail.id)"
            >
              刷新快照
            </el-button>
          </div>
          <p class="quota-block-sub">拦截判断读取实时用量，快照仅用于趋势与对账，因此可能略滞后于当前值</p>
          <div class="quota-list">
            <article v-for="item in detail.quotas || []" :key="item.code" class="quota-card">
              <div class="quota-head">
                <span class="quota-name">{{ item.name }}</span>
                <span class="quota-level" :class="`is-${item.level}`">{{ levelText(item) }}</span>
              </div>
              <div class="quota-numbers">
                <b class="zk-num"><QuotaCount :value="Number(item.used) || 0" /></b>
                <span class="quota-limit">/ {{ item.limit < 0 ? '不限' : formatNumber(item.limit) }} {{ item.unit }}</span>
              </div>
              <div class="quota-track">
                <i :class="`is-${item.level}`" :style="{ width: barWidth(item) }" />
              </div>
              <p class="quota-hint">{{ item.hint }}</p>
            </article>
          </div>
        </div>

        <div class="drawer-actions">
          <el-button @click="openEdit(detail)">编辑资料</el-button>
          <el-button @click="openPlan(detail)">变更套餐</el-button>
          <el-button @click="openRenew(detail)">续期</el-button>
          <el-button
            :type="detail.status === 'ENABLED' ? 'warning' : 'success'"
            plain
            @click="toggleStatus(detail)"
          >
            {{ detail.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
        </div>
      </div>
      <div v-else class="detail-loading">
        <ZkSkeleton v-if="detailLoading" variant="lines" :rows="9" />
        <ZkEmpty v-else title="租户详情加载失败" hint="该租户可能已被删除，或当前账号无权查看" />
      </div>
    </el-drawer>

    <!-- 开通 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑租户资料' : '开通租户'"
      width="640px"
      append-to-body
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <div class="form-section-title">租户信息</div>
        <div class="form-grid">
          <el-form-item label="租户名称" prop="tenantName">
            <el-input v-model="form.tenantName" maxlength="50" show-word-limit placeholder="如：杭州云客科技有限公司" />
          </el-form-item>
          <el-form-item label="租户编码" prop="tenantCode">
            <el-input
              v-model="form.tenantCode"
              :disabled="Boolean(form.id)"
              maxlength="32"
              placeholder="留空自动生成，如 yunke"
            />
          </el-form-item>
        </div>
        <el-form-item v-if="!form.id" label="套餐" prop="planCode">
          <el-select v-model="form.planCode" placeholder="选择套餐" class="full-select">
            <el-option v-for="plan in plans" :key="plan.code" :label="plan.name" :value="plan.code">
              <div class="plan-option">
                <span>{{ plan.name }}</span>
                <span class="plan-option-quota">
                  坐席 {{ formatQuota(plan.seatLimit) }} · 知识库 {{ formatQuota(plan.kbLimit) }}
                </span>
              </div>
            </el-option>
          </el-select>
          <p class="form-hint">开通后可在「变更套餐」中调整；降级需存量用量不超新套餐额度</p>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" maxlength="32" placeholder="对接人姓名" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" maxlength="11" placeholder="11 位手机号" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="联系邮箱" prop="contactEmail">
            <el-input v-model="form.contactEmail" maxlength="100" placeholder="用于开通与续费通知" />
          </el-form-item>
          <el-form-item label="服务到期时间" prop="expireTime">
            <el-date-picker
              v-model="form.expireTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="留空按套餐周期自动计算"
              class="full-select"
            />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="200"
            show-word-limit
            placeholder="签约信息、特殊约定等"
          />
        </el-form-item>

        <template v-if="!form.id">
          <div class="form-section-title">商户管理员账号</div>
          <div class="form-grid">
            <el-form-item label="登录名" prop="adminUsername">
              <el-input v-model="form.adminUsername" maxlength="32" placeholder="3-32 位字母、数字或下划线" />
            </el-form-item>
            <el-form-item label="初始密码" prop="adminPassword">
              <el-input
                v-model="form.adminPassword"
                type="password"
                show-password
                maxlength="32"
                placeholder="6-32 位，须含字母与数字"
              />
            </el-form-item>
          </div>
          <el-form-item label="管理员昵称" prop="adminNickname">
            <el-input v-model="form.adminNickname" maxlength="32" placeholder="默认与登录名一致" />
          </el-form-item>
          <p class="form-hint">开通后同时生成网页接入配置（appKey / appSecret）与当月用量快照，无需再补数据</p>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          type="primary"
          class="zk-save"
          :class="`is-${saveState}`"
          :disabled="saveState === 'busy'"
          @click="submitForm"
        >
          <span class="zk-save-label">{{ form.id ? '保存' : '开通租户' }}</span>
          <span class="zk-save-mark zk-save-spin" aria-hidden="true" />
          <svg class="zk-save-mark zk-save-tick" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M3.2 8.4l2.9 2.9 6.7-6.7" />
          </svg>
        </el-button>
      </template>
    </el-dialog>

    <!-- 变更套餐 -->
    <el-dialog v-model="planVisible" title="变更套餐" width="640px" append-to-body>
      <div v-if="activeTenant" class="plan-dialog">
        <p class="plan-dialog-lead">
          {{ activeTenant.tenantName }} · 当前套餐 <b>{{ activeTenant.planName }}</b>
        </p>
        <div class="plan-options">
          <article
            v-for="plan in plans"
            :key="plan.code"
            class="plan-option-card"
            :class="{ 'is-selected': planTarget === plan.code, 'is-current': plan.code === activeTenant.planCode }"
            role="button"
            tabindex="0"
            @click="planTarget = plan.code"
            @keydown.enter.prevent="planTarget = plan.code"
          >
            <div class="plan-option-head">
              <span class="plan-option-name">{{ plan.name }}</span>
              <span class="plan-option-price">{{ plan.price === 0 ? '免费' : `¥${plan.price}/${plan.period}` }}</span>
            </div>
            <p class="plan-option-tagline">{{ plan.tagline }}</p>
            <ul class="plan-option-quotas">
              <li><span>坐席</span><b>{{ formatQuota(plan.seatLimit) }}</b></li>
              <li><span>每月会话</span><b>{{ formatQuota(plan.sessionLimit) }}</b></li>
              <li><span>每月工单</span><b>{{ formatQuota(plan.orderLimit) }}</b></li>
              <li><span>知识库</span><b>{{ formatQuota(plan.kbLimit) }}</b></li>
              <li><span>AI 应答</span><b>{{ formatQuota(plan.aiResolutionLimit) }}</b></li>
            </ul>
            <span v-if="plan.code === activeTenant.planCode" class="plan-option-flag">当前</span>
          </article>
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="升级即时生效，降级按存量校验"
          description="降级时若已有坐席数或知识条目数超过目标套餐额度，接口会拒绝并提示具体超限项。"
        />
      </div>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="planSaving"
          :disabled="!planTarget || planTarget === activeTenant?.planCode"
          @click="submitPlan"
        >
          确认变更
        </el-button>
      </template>
    </el-dialog>

    <!-- 续期 -->
    <el-dialog v-model="renewVisible" title="设置到期时间" width="460px" append-to-body>
      <div v-if="activeTenant" class="renew">
        <p class="renew-lead">{{ activeTenant.tenantName }} · 当前到期 {{ activeTenant.expireTime ? formatDateTime(activeTenant.expireTime) : '不限' }}</p>
        <el-date-picker
          v-model="renewTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择新的到期时间"
          class="full-select"
          :disabled-date="isPastDate"
        />
        <p class="form-hint">新时间必须晚于当前时间；若租户已到期，续期后状态会自动恢复为正常</p>
      </div>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" :loading="renewSaving" :disabled="!renewTime" @click="submitRenew">
          确认续期
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { prefersReducedMotion, useCountUp } from '@/composables/motion'
import {
  changePlatformTenantPlan,
  createPlatformTenant,
  deletePlatformTenant,
  getPlatformPlans,
  getPlatformTenantDetail,
  getPlatformTenants,
  refreshPlatformTenantUsage,
  renewPlatformTenant,
  updatePlatformTenant,
  updatePlatformTenantStatus
} from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  TENANT_STATUS_DICT,
  TENANT_STATUS_OPTIONS,
  dictLabel,
  dictTone,
  formatDateTime,
  formatNumber,
  formatQuota
} from '@/utils/dict'

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
} = usePagedList(getPlatformTenants, { keyword: '', status: '', planCode: '' })

const plans = ref([])
const busyId = ref(null)

/** 租户 / 套餐卡片入场错开序号：超过 4 个后不再累加，最晚一个仍 ≤ 700ms */
const revealIndex = (index) => Math.min(index, 4)

/* 用量数字滚动：v-for 里无法直接调用组合式，用局部组件包一层 */
const QuotaCount = defineComponent({
  props: { value: { type: Number, default: 0 } },
  setup(props) {
    const source = computed(() => props.value)
    const { display } = useCountUp(source, { duration: 700, updateDuration: 420 })
    return () => h('span', display.value)
  }
})

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
watch(rows, () => {
  listTick.value += 1
})
const fadeClass = computed(() => (listTick.value % 2 ? 'list-fade-a' : 'list-fade-b'))

/* ---------- 列表辅助 ---------- */
const hasFilter = computed(
  () => Boolean(filters.keyword) || Boolean(filters.status) || Boolean(filters.planCode)
)

/** 到期但未被停用的租户按「已到期」展示，与后端 expired 计算口径一致 */
function statusOf(tenant) {
  if (!tenant) return 'ENABLED'
  if (tenant.status === 'DISABLED') return 'DISABLED'
  return tenant.expired ? 'EXPIRED' : tenant.status || 'ENABLED'
}

function reload() {
  load()
  loadPlans()
}

function resetAll() {
  reset()
}

function filterByPlan(code) {
  filters.planCode = filters.planCode === code ? '' : code
  search()
}

function expireText(row) {
  if (!row.expireTime) return '长期有效'
  const remain = remainDays(row.expireTime)
  if (remain === null) return '—'
  if (remain < 0) return `已过期 ${Math.abs(remain)} 天`
  if (remain === 0) return '今日到期'
  return `剩余 ${remain} 天`
}

function expireTone(row) {
  if (!row.expireTime) return 'is-normal'
  const remain = remainDays(row.expireTime)
  if (remain === null) return ''
  if (remain < 0) return 'is-danger'
  if (remain <= 14) return 'is-warning'
  return 'is-normal'
}

function remainDays(value) {
  const text = String(value).replace(/-/g, '/')
  const target = new Date(text)
  if (Number.isNaN(target.getTime())) return null
  const today = new Date()
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const end = new Date(target.getFullYear(), target.getMonth(), target.getDate())
  return Math.round((end - start) / 86400000)
}

function levelText(item) {
  if (item.limit < 0) return '不限'
  if (item.level === 'danger') return '已超额'
  if (item.level === 'warning') return '接近上限'
  return '正常'
}

function barWidth(item) {
  if (item.limit < 0) return '4%'
  return `${Math.min(100, Math.max(item.percent || 0, item.used > 0 ? 3 : 0))}%`
}

/* ---------- 套餐字典 ---------- */
async function loadPlans() {
  try {
    plans.value = (await getPlatformPlans()) || []
  } catch {
    plans.value = []
  }
}

/* ---------- 详情抽屉 ---------- */
const drawerVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const snapshotLoading = ref(false)

async function openDetail(id) {
  drawerVisible.value = true
  await loadDetail(id)
}

async function loadDetail(id) {
  detailLoading.value = true
  try {
    detail.value = await getPlatformTenantDetail(id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function refreshSnapshot(id) {
  snapshotLoading.value = true
  try {
    await refreshPlatformTenantUsage(id)
    await loadDetail(id)
    await load()
    ElMessage.success('用量快照已刷新')
  } catch (err) {
    ElMessage.error(err?.message || '快照刷新失败，请稍后重试')
  } finally {
    snapshotLoading.value = false
  }
}

/* ---------- 开通 / 编辑 ---------- */
const formVisible = ref(false)
const formRef = ref(null)
const saving = ref(false)
const form = ref(createForm())

const formRules = {
  tenantName: [{ required: true, message: '请填写租户名称', trigger: 'blur' }],
  tenantCode: [
    {
      pattern: /^$|^[a-z0-9_-]{2,32}$/,
      message: '租户编码须为 2-32 位小写字母、数字、下划线或连字符',
      trigger: 'blur'
    }
  ],
  planCode: [{ required: true, message: '请选择套餐', trigger: 'change' }],
  contactPhone: [
    { pattern: /^$|^1[3-9]\d{9}$/, message: '联系电话格式不正确', trigger: 'blur' }
  ],
  contactEmail: [{ type: 'email', message: '联系邮箱格式不正确', trigger: 'blur' }],
  adminUsername: [
    { required: true, message: '请填写租户管理员登录名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,32}$/,
      message: '登录名须为 3-32 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  adminPassword: [
    { required: true, message: '请填写初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为 6-32 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).*$/,
      message: '密码须同时包含字母和数字',
      trigger: 'blur'
    }
  ]
}

function createForm() {
  return {
    id: null,
    tenantName: '',
    tenantCode: '',
    planCode: 'trial',
    expireTime: '',
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    remark: '',
    adminUsername: '',
    adminPassword: '',
    adminNickname: ''
  }
}

function resetForm() {
  form.value = createForm()
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  form.value.planCode = plans.value[0]?.code || 'trial'
  formVisible.value = true
}

function openEdit(tenant) {
  if (!tenant) return
  form.value = {
    ...createForm(),
    id: tenant.id,
    tenantName: tenant.tenantName || '',
    tenantCode: tenant.tenantCode || '',
    planCode: tenant.planCode || '',
    expireTime: tenant.expireTime || '',
    contactName: tenant.contactName || '',
    contactPhone: tenant.contactPhone || '',
    contactEmail: tenant.contactEmail || '',
    remark: tenant.remark || ''
  }
  formVisible.value = true
}

async function submitForm() {
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
    if (form.value.id) {
      const payload = {
        tenantName: form.value.tenantName,
        contactName: form.value.contactName,
        contactPhone: form.value.contactPhone,
        contactEmail: form.value.contactEmail,
        remark: form.value.remark
      }
      if (form.value.expireTime) payload.expireTime = form.value.expireTime
      await updatePlatformTenant(form.value.id, payload)
      ElMessage.success('租户资料已更新')
      if (detail.value?.id === form.value.id) await loadDetail(form.value.id)
    } else {
      const created = await createPlatformTenant({
        tenantName: form.value.tenantName,
        tenantCode: form.value.tenantCode.trim(),
        planCode: form.value.planCode,
        contactName: form.value.contactName,
        contactPhone: form.value.contactPhone,
        contactEmail: form.value.contactEmail,
        remark: form.value.remark,
        adminUsername: form.value.adminUsername,
        adminPassword: form.value.adminPassword,
        adminNickname: form.value.adminNickname,
        ...(form.value.expireTime ? { expireTime: form.value.expireTime } : {})
      })
      ElMessage.success(`租户已开通，管理员账号 ${created?.adminUsername || form.value.adminUsername}`)
    }
    flashSaved()
    formVisible.value = false
    reload()
  } catch (err) {
    // 统一拦截器已提示，403/409 的业务原因在此保留给用户
    if (err?.message) ElMessage.error(err.message)
  } finally {
    saving.value = false
    if (saveState.value === 'busy') saveState.value = 'idle'
  }
}

/* ---------- 变更套餐 ---------- */
const planVisible = ref(false)
const planSaving = ref(false)
const planTarget = ref('')
const activeTenant = ref(null)

function openPlan(tenant) {
  if (!tenant) return
  activeTenant.value = tenant
  planTarget.value = tenant.planCode || ''
  planVisible.value = true
}

async function submitPlan() {
  if (!activeTenant.value || !planTarget.value) return
  planSaving.value = true
  try {
    await changePlatformTenantPlan(activeTenant.value.id, planTarget.value)
    ElMessage.success('套餐已变更并即时生效')
    planVisible.value = false
    if (detail.value?.id === activeTenant.value.id) await loadDetail(activeTenant.value.id)
    await load()
  } catch (err) {
    if (err?.message) ElMessage.error(err.message)
  } finally {
    planSaving.value = false
  }
}

/* ---------- 续期 ---------- */
const renewVisible = ref(false)
const renewSaving = ref(false)
const renewTime = ref('')

function openRenew(tenant) {
  if (!tenant) return
  activeTenant.value = tenant
  renewTime.value = tenant.expireTime || ''
  renewVisible.value = true
}

function isPastDate(date) {
  const today = new Date()
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return date.getTime() < start.getTime()
}

async function submitRenew() {
  if (!activeTenant.value || !renewTime.value) return
  renewSaving.value = true
  try {
    await renewPlatformTenant(activeTenant.value.id, renewTime.value)
    ElMessage.success('到期时间已更新')
    renewVisible.value = false
    if (detail.value?.id === activeTenant.value.id) await loadDetail(activeTenant.value.id)
    await load()
  } catch (err) {
    if (err?.message) ElMessage.error(err.message)
  } finally {
    renewSaving.value = false
  }
}

/* ---------- 启停与删除 ---------- */
async function toggleStatus(tenant) {
  if (!tenant) return
  const nextStatus = tenant.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = nextStatus === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      nextStatus === 'DISABLED'
        ? `停用后「${tenant.tenantName}」内所有账号将无法登录，已签发令牌在自然到期前仍可访问。确认停用？`
        : `启用后「${tenant.tenantName}」内账号可正常登录。确认启用？`,
      `${actionText}租户`,
      { type: 'warning', confirmButtonText: `确认${actionText}`, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busyId.value = tenant.id
  try {
    await updatePlatformTenantStatus(tenant.id, nextStatus)
    ElMessage.success(`已${actionText}`)
    if (detail.value?.id === tenant.id) await loadDetail(tenant.id)
    await load()
  } catch (err) {
    if (err?.message) ElMessage.error(err.message)
  } finally {
    busyId.value = null
  }
}

async function removeTenant(tenant) {
  if (!tenant) return
  try {
    await ElMessageBox.confirm(
      `删除「${tenant.tenantName}」将同时停用其接入配置，租户内账号与业务数据将按租户级清理。该操作不可撤销，确认删除？`,
      '删除租户',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  busyId.value = tenant.id
  try {
    await deletePlatformTenant(tenant.id)
    ElMessage.success('租户已删除')
    if (detail.value?.id === tenant.id) {
      drawerVisible.value = false
      detail.value = null
    }
    await load()
  } catch (err) {
    if (err?.message) ElMessage.error(err.message)
  } finally {
    busyId.value = null
  }
}

function onRowCommand(command, row) {
  if (command === 'plan') openPlan(row)
  else if (command === 'renew') openRenew(row)
  else if (command === 'status') toggleStatus(row)
  else if (command === 'delete') removeTenant(row)
  else if (command === 'snapshot') refreshSnapshot(row.id)
}

onMounted(() => {
  load()
  loadPlans()
})
</script>

<style scoped>
/* 首屏骨架与真实表格同列，加载前后不跳 */
.table-skeleton {
  padding: 12px 14px;
}

/* 套餐标签与状态切换：换套餐 / 换状态时轻弹一下，给「已生效」一个可见反馈 */
.plan-pill {
  animation: zk-pop-in 320ms var(--zk-ease-spring);
}

.status-pop {
  animation: zk-pop-in 260ms var(--zk-ease-spring);
}

/* 筛选结果淡入 160ms：只淡不位移，避免表格重新布局 */
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

/* ---------- 套餐概览条 ---------- */
.plan-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.plan-chip {
  padding: 12px 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface);
  cursor: pointer;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out),
    box-shadow var(--zk-dur-fast) var(--zk-ease-out),
    transform var(--zk-dur-base) var(--zk-ease-out);
}

.plan-chip:hover {
  border-color: var(--zk-border-strong);
}

.plan-chip:focus-visible {
  outline: 2px solid var(--zk-accent);
  outline-offset: 2px;
}

.plan-chip.is-active {
  border-color: var(--zk-accent);
  box-shadow: inset 0 0 0 1px var(--zk-accent);
}

.plan-chip-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.plan-chip-name {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.plan-chip-price {
  font-size: 11.5px;
  color: var(--zk-accent-hover);
  font-variant-numeric: tabular-nums;
}

.plan-chip-quota {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.plan-chip-tagline {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.55;
  color: var(--zk-text-3);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.plan-strip-empty {
  margin: 0;
  font-size: 12px;
  color: var(--zk-text-3);
}

/* ---------- 工具条与表格 ---------- */
.status-select {
  width: 132px;
}

.plan-select {
  width: 148px;
}

.tenant-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tenant-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.tenant-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.quota-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.quota-sub {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.expire-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-time {
  font-size: 12.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.expire-hint {
  font-size: 11px;
  color: var(--zk-text-3);
}

.expire-hint.is-warning {
  color: #b45309;
}

.expire-hint.is-danger {
  color: #be123c;
}

.contact-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.cell-muted {
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.op-btn {
  padding: 0 4px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  outline-offset: -2px;
}

.table-scroll :deep(.el-table) {
  min-width: 1080px;
}

/* ---------- 抽屉详情 ---------- */
.detail-loading {
  min-height: 200px;
  display: grid;
  place-items: center;
  color: var(--zk-text-3);
  font-size: 12.5px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--zk-border);
}

.detail-name {
  font-size: 17px;
  font-weight: 650;
  color: var(--zk-text);
}

.detail-code {
  margin-top: 3px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin: 16px 0 0;
}

.meta-grid dt {
  font-size: 11px;
  color: var(--zk-text-3);
}

.meta-grid dd {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: var(--zk-text);
  line-height: 1.55;
  word-break: break-all;
}

.quota-block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--zk-border);
}

.quota-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.quota-block-head h4 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.quota-block-sub {
  margin: 4px 0 12px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.quota-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quota-card {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 10px 12px;
  background: var(--zk-surface);
}

.quota-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.quota-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-text-2);
}

.quota-level {
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--zk-surface-2);
  color: var(--zk-text-3);
}

.quota-level.is-warning {
  color: #b45309;
  background: #fff7e6;
}

.quota-level.is-danger {
  color: #be123c;
  background: #fff1f4;
}

.quota-numbers {
  margin: 6px 0;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.quota-numbers b {
  font-size: 18px;
  font-weight: 700;
  color: var(--zk-text);
}

.quota-limit {
  font-size: 11px;
  color: var(--zk-text-3);
}

.quota-track {
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.quota-track i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  transform-origin: left center;
  transition: width var(--zk-dur-slow) var(--zk-ease-out);
  /* 入场时从左侧长出来：只动 transform，不触发重排 */
  animation: zk-bar-grow-x 420ms var(--zk-ease-emphasis) both;
}

@keyframes zk-bar-grow-x {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

.quota-track i.is-warning {
  background: var(--zk-amber-edge);
}

.quota-track i.is-danger {
  background: var(--zk-red-edge);
}

.quota-hint {
  margin: 6px 0 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--zk-text-3);
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--zk-border);
}

/* ---------- 表单 ---------- */
.form-section-title {
  margin: 4px 0 10px;
  padding-left: 8px;
  border-left: 2px solid var(--zk-accent);
  font-size: 12.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.full-select {
  width: 100%;
}

.form-hint {
  margin: 2px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.plan-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.plan-option-quota {
  font-size: 11px;
  color: var(--zk-text-3);
}

/* ---------- 套餐选择 ---------- */
.plan-dialog-lead {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--zk-text-2);
}

.plan-dialog-lead b {
  color: var(--zk-text);
}

.plan-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.plan-option-card {
  position: relative;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 12px;
  background: var(--zk-surface);
  cursor: pointer;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out),
    box-shadow var(--zk-dur-fast) var(--zk-ease-out);
}

.plan-option-card:hover {
  border-color: var(--zk-border-strong);
}

.plan-option-card:focus-visible {
  outline: 2px solid var(--zk-accent);
  outline-offset: 2px;
}

.plan-option-card.is-selected {
  border-color: var(--zk-accent);
  box-shadow: inset 0 0 0 1px var(--zk-accent);
}

.plan-option-card.is-current {
  background: var(--zk-surface-2);
}

.plan-option-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.plan-option-name {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

.plan-option-price {
  font-size: 11px;
  color: var(--zk-accent-hover);
  font-variant-numeric: tabular-nums;
}

.plan-option-tagline {
  margin: 4px 0 8px;
  min-height: 32px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--zk-text-3);
}

.plan-option-quotas {
  list-style: none;
  margin: 0;
  padding: 8px 0 0;
  border-top: 1px dashed var(--zk-border);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.plan-option-quotas li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--zk-text-3);
}

.plan-option-quotas b {
  font-weight: 650;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.plan-option-flag {
  position: absolute;
  top: -8px;
  right: 10px;
  padding: 1px 7px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-ink-800);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
}

/* ---------- 续期 ---------- */
.renew-lead {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

@media (max-width: 900px) {
  .plan-options {
    grid-template-columns: 1fr;
  }

  .quota-list,
  .meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .status-select,
  .plan-select {
    width: 100%;
  }

  .detail {
    padding-bottom: 12px;
  }
}
</style>
