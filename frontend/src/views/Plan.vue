<template>
  <div class="zk-page">
    <PageHeader
      title="套餐与用量"
      description="本月额度用量与近 30 天服务结果全部来自实时统计。额度接近上限时会提示，便于在影响接待前完成升级。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="TrendCharts" @click="scrollToPlans">查看套餐</el-button>
      </template>
    </PageHeader>

    <div v-if="error" class="zk-card plan-error">
      <el-icon aria-hidden="true"><WarningFilled /></el-icon>
      <div>
        <p>{{ error }}</p>
        <el-button @click="load">重新加载</el-button>
      </div>
    </div>

    <template v-else>
      <!-- 当前套餐 -->
      <section v-loading="loading" class="plan-hero" aria-label="当前套餐">
        <div class="hero-main">
          <div class="hero-eyebrow">
            <span class="hero-dot" aria-hidden="true" />
            {{ data?.tenantName || '当前租户' }}<span v-if="data?.tenantCode" class="hero-code">{{ data.tenantCode }}</span>
            · 统计周期 {{ data?.periodLabel || '—' }}
          </div>
          <h2 class="hero-name">{{ data?.planName || '—' }}</h2>
          <p class="hero-tagline">{{ data?.tagline || '正在加载套餐信息' }}</p>
          <p class="hero-meta">
            <span>服务到期 {{ data?.expireTime ? formatDateTime(data.expireTime) : '不限' }}</span>
            <span>用量快照 {{ data?.snapshotTime ? formatDateTime(data.snapshotTime) : '暂无' }}</span>
            <span>套餐额度由平台在「租户与套餐」维护</span>
          </p>
          <div class="hero-price">
            <span class="hero-amount">{{ priceText }}</span>
            <span v-if="hasPrice" class="hero-period">/ {{ data?.period }}</span>
          </div>
          <div class="hero-features">
            <span v-for="feature in data?.features || []" :key="feature" class="hero-chip">
              <el-icon aria-hidden="true"><CircleCheck /></el-icon>
              {{ feature }}
            </span>
          </div>
        </div>
        <div class="hero-side">
          <div class="hero-side-label">额度水位</div>
          <div class="hero-side-value" :class="overallTone">{{ overallText }}</div>
          <div class="hero-side-note">
            {{ alertItems.length ? `${alertItems.length} 项额度需要关注` : '所有额度均在安全区间' }}
          </div>
          <el-button class="hero-cta" @click="scrollToPlans">
            对比全部套餐
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </section>

      <!-- 额度用量 -->
      <section class="zk-card plan-block" aria-label="本月额度用量">
        <header class="block-head">
          <div>
            <h3 class="block-title">本月额度用量</h3>
            <p class="block-sub">按自然月统计，每月 1 日 00:00 重置；「不限」表示当前套餐未设置上限</p>
          </div>
          <span class="block-meta">更新于 {{ formatDateTime(data?.generatedAt) }}</span>
        </header>

        <div v-if="data?.items?.length" class="usage-grid" :aria-busy="loading ? 'true' : 'false'" v-reveal.group>
          <article v-for="item in data?.items || []" :key="item.code" class="usage-card">
            <div class="usage-head">
              <span class="usage-name">{{ item.name }}</span>
              <span class="usage-level" :class="`is-${item.level}`">{{ levelText(item) }}</span>
            </div>
            <div class="usage-numbers">
              <b class="zk-num"><CountNum :value="item.used" /></b>
              <span class="usage-limit">
                / {{ item.limit < 0 ? '不限' : formatQuota(item.limit) }} {{ item.unit }}
              </span>
            </div>
            <div
              class="usage-track"
              role="progressbar"
              :aria-valuenow="item.limit < 0 ? 0 : item.percent"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-label="`${item.name}额度使用 ${item.limit < 0 ? '不限' : item.percent + '%'}`"
            >
              <i :class="`is-${item.level}`" :style="{ width: usageReady ? barWidth(item) : '0%' }" />
            </div>
            <p class="usage-hint">{{ item.hint }}</p>
          </article>
        </div>
      </section>

      <!-- 运营 KPI -->
      <section class="zk-card plan-block" aria-label="近 30 天运营指标">
        <header class="block-head">
          <div>
            <h3 class="block-title">近 30 天服务结果</h3>
            <p class="block-sub">这些指标决定续费与升级判断：AI 是否真的在替你接住咨询</p>
          </div>
        </header>
        <div v-if="data?.kpis?.length" class="kpi-grid" v-reveal.group>
          <article v-for="kpi in data?.kpis || []" :key="kpi.code" class="kpi-card">
            <div class="kpi-name">{{ kpi.name }}</div>
            <div class="kpi-value" :class="`tone-${kpi.tone}`">
              <span class="zk-num">
                <CountNum :value="kpi.value" :decimals="String(kpi.value).includes('.') ? 1 : 0" />
              </span>
              <small v-if="kpi.unit && kpi.value !== '—'">{{ kpi.unit }}</small>
            </div>
            <p class="kpi-hint">{{ kpi.hint }}</p>
          </article>
        </div>
        <p class="kpi-foot">
          <el-icon aria-hidden="true"><InfoFilled /></el-icon>
          会话侧 AI 自主解决率越高，人工接待压力越小；工单侧 SLA 达标率与满意度决定客户续费意愿。
        </p>
      </section>

      <!-- 套餐对比 -->
      <section ref="plansRef" class="plan-block plans-block" aria-label="套餐对比">
        <header class="block-head">
          <div>
            <h3 class="block-title">套餐对比</h3>
            <p class="block-sub">{{ data?.billingNote || '价格与额度仅供演示评估；正式开通由商务确认，不会在本页产生扣费' }}</p>
          </div>
        </header>

        <div v-if="data?.plans?.length" class="plans-grid" v-reveal.group style="--zk-stagger: 50ms">
          <article
            v-for="plan in data?.plans || []"
            :key="plan.code"
            class="plan-card zk-hover-lift"
            :class="{ 'is-current': plan.currentPlan, 'is-recommended': plan.recommended && !plan.currentPlan }"
          >
            <div v-if="plan.recommended && !plan.currentPlan" class="plan-flag">推荐</div>
            <!-- 当前套餐徽标：从 0.96 弹出来，只在这一张卡上出现 -->
            <div v-if="plan.currentPlan" class="plan-current zk-pop">当前套餐</div>
            <div class="plan-head">
              <h4 class="plan-name">{{ plan.name }}</h4>
              <p class="plan-tagline">{{ plan.tagline }}</p>
            </div>
            <div class="plan-price">
              <span class="plan-amount">{{ plan.price === 0 ? '免费' : `¥${plan.price}` }}</span>
              <span v-if="plan.price > 0" class="plan-period">/ {{ plan.period }}</span>
            </div>
            <ul class="plan-quotas">
              <li v-for="quota in quotaRows(plan)" :key="quota.label">
                <span>{{ quota.label }}</span>
                <b class="zk-num">{{ quota.value }}</b>
              </li>
            </ul>
            <ul class="plan-features">
              <li v-for="feature in plan.features" :key="feature">
                <el-icon aria-hidden="true"><Check /></el-icon>
                {{ feature }}
              </li>
            </ul>
            <el-button
              class="plan-cta"
              :type="plan.currentPlan ? 'info' : plan.recommended ? 'primary' : 'default'"
              :plain="!plan.recommended && !plan.currentPlan"
              :disabled="plan.currentPlan"
              @click="requestUpgrade(plan)"
            >
              {{ planCta(plan) }}
            </el-button>
          </article>
        </div>
      </section>
    </template>

    <!-- 升级说明 -->
    <el-dialog
      v-model="upgradeVisible"
      :title="upgradeTarget?.price === 0 ? '试用说明' : '升级说明'"
      width="520px"
      append-to-body
    >
      <div v-if="upgradeTarget" class="upgrade">
        <p class="upgrade-lead">
          目标套餐：<b>{{ upgradeTarget.name }}</b>
          <span class="upgrade-price">
            {{ upgradeTarget.price === 0 ? '免费' : `¥${upgradeTarget.price} / ${upgradeTarget.period}` }}
          </span>
        </p>
        <ul class="upgrade-list">
          <li v-for="quota in quotaRows(upgradeTarget)" :key="quota.label">
            <span>{{ quota.label }}</span>
            <b>{{ quota.value }}</b>
          </li>
        </ul>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="提交后生成跟进工单"
          description="申请会以 P1 工单提交至工单中心，由平台商务跟进开通；开通前当前套餐与额度保持不变，不会产生扣费。"
        />
      </div>
      <template #footer>
        <el-button @click="upgradeVisible = false">关闭</el-button>
        <!-- 按钮三连变：申请文案 → 转圈 → 打勾「已提交」，同一个按钮位置不变 -->
        <el-button
          type="primary"
          class="upgrade-submit"
          :class="{ 'zk-pop': upgradeState === 'done' }"
          :loading="upgradeState === 'loading'"
          :disabled="upgradeState === 'done'"
          :icon="upgradeState === 'done' ? Check : undefined"
          @click="submitUpgradeRequest"
        >
          {{
            upgradeState === 'done'
              ? '已提交'
              : upgradeTarget?.price === 0
                ? '提交试用申请'
                : '提交升级申请'
          }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Check,
  CircleCheck,
  InfoFilled,
  Refresh,
  TrendCharts,
  WarningFilled
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useCountUp } from '@/composables/motion'
import { createWorkOrder, getPlanUsage } from '@/api'
import { formatDateTime, formatQuota } from '@/utils/dict'

const router = useRouter()
const data = ref(null)
const loading = ref(false)
const error = ref('')
const plansRef = ref(null)
const upgradeVisible = ref(false)
const upgradeTarget = ref(null)
// 切换套餐按钮三连变：idle 文案 → loading 转圈 → done 打勾
const upgradeState = ref('idle')
const usageReady = ref(false)

/**
 * 数字滚动：v-for 里的额度 / KPI 指标无法逐个调用 useCountUp，这里包一层组件统一节奏。
 * 首播 700ms，数据刷新 500ms；prefers-reduced-motion 由 useCountUp 内置处理。
 */
const CountNum = defineComponent({
  name: 'CountNum',
  props: {
    value: { type: [Number, String], default: null },
    decimals: { type: Number, default: 0 },
    prefix: { type: String, default: '' },
    suffix: { type: String, default: '' }
  },
  setup(props) {
    const valid = computed(
      () => props.value !== null && props.value !== '' && Number.isFinite(Number(props.value))
    )
    const { display } = useCountUp(computed(() => (valid.value ? Number(props.value) : 0)), {
      duration: 700,
      updateDuration: 500,
      decimals: props.decimals,
      prefix: props.prefix,
      suffix: props.suffix
    })
    return () => h('span', valid.value ? display.value : '—')
  }
})

// 用量条首次挂载从 0 推进到实际占比，数据切换走宽度过渡
watch(data, async (next) => {
  if (!next?.items?.length || usageReady.value) return
  await nextTick()
  requestAnimationFrame(() => {
    usageReady.value = true
  })
})

const hasPrice = computed(() => Number(data.value?.price) > 0)
const priceText = computed(() => {
  const price = Number(data.value?.price)
  if (!Number.isFinite(price)) return '—'
  return price === 0 ? '免费' : `¥${price}`
})

const alertItems = computed(() =>
  (data.value?.items || []).filter((item) => item.level === 'warning' || item.level === 'danger')
)

const overallTone = computed(() => {
  if (alertItems.value.some((item) => item.level === 'danger')) return 'is-danger'
  if (alertItems.value.length) return 'is-warning'
  return 'is-normal'
})

const overallText = computed(() => {
  if (!data.value) return '加载中'
  if (alertItems.value.some((item) => item.level === 'danger')) return '已超额度'
  if (alertItems.value.length) return '接近上限'
  return '额度充足'
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getPlanUsage()
  } catch (err) {
    error.value = err?.message || '套餐信息加载失败，请确认后端服务与权限'
    data.value = null
  } finally {
    loading.value = false
  }
}

function scrollToPlans() {
  nextTick(() => {
    plansRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
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

function quotaRows(plan) {
  return [
    { label: '坐席账号', value: formatQuota(plan.seatLimit) },
    { label: '每月会话', value: formatQuota(plan.sessionLimit) },
    { label: '每月工单', value: formatQuota(plan.orderLimit) },
    { label: '知识库条目', value: formatQuota(plan.kbLimit) },
    { label: 'AI 自主解决', value: formatQuota(plan.aiResolutionLimit) }
  ]
}

function planCta(plan) {
  if (plan.currentPlan) return '当前套餐'
  if (plan.price === 0) return '免费体验 14 天'
  const current = (data.value?.plans || []).find((item) => item.currentPlan)
  if (current && Number(plan.price) < Number(current.price)) return `切换为${plan.name}`
  return `升级到${plan.name}`
}

function requestUpgrade(plan) {
  if (plan.currentPlan) return
  upgradeTarget.value = plan
  upgradeState.value = 'idle'
  upgradeVisible.value = true
}

async function submitUpgradeRequest() {
  const target = upgradeTarget.value
  if (!target || upgradeState.value !== 'idle') return
  upgradeState.value = 'loading'
  try {
    // 升级诉求以工单形式进入真实流转，由平台侧分配与跟进
    await createWorkOrder({
      title: `套餐${target.price === 0 ? '试用' : '升级'}申请：${target.name}`,
      content: [
        `申请${target.price === 0 ? '试用' : '升级'}套餐：${target.name}（${target.price === 0 ? '免费' : `¥${target.price} / ${target.period}`}）。`,
        `目标额度：坐席账号 ${formatQuota(target.seatLimit)}、每月会话 ${formatQuota(target.sessionLimit)}、每月工单 ${formatQuota(target.orderLimit)}、知识库条目 ${formatQuota(target.kbLimit)}、AI 自主解决 ${formatQuota(target.aiResolutionLimit)}。`,
        `当前套餐：${data.value?.planName || '—'}。请平台商务联系租户确认开通与计费方式。`
      ].join('\n'),
      category: '订单问题',
      priority: 'P1',
      source: 'MANUAL',
      remark: '由「套餐与用量」页面发起'
    })
    upgradeState.value = 'done'
    ElMessage.success('申请已提交，可在工单中心跟踪处理进度')
    // 让「已提交」这一态被看见，再关闭并跳到工单中心
    await new Promise((resolve) => window.setTimeout(resolve, 720))
    if (upgradeVisible.value) {
      upgradeVisible.value = false
      router.push('/admin/orders')
    }
  } catch (error) {
    // 请求拦截器已统一提示；失败回到初始态，允许重试
    upgradeState.value = 'idle'
  }
}

onMounted(load)
</script>

<style scoped>
/* ---------- 当前套餐 ---------- */
.plan-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 24px;
  padding: 22px 24px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface);
  color: var(--zk-text);
}

.hero-eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  letter-spacing: 0.4px;
  color: var(--zk-text-3);
}

.hero-code {
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--zk-surface-3);
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10.5px;
  color: var(--zk-text-2);
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin: 8px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.hero-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #10b981;
}

.hero-name {
  margin: 8px 0 4px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.2px;
  color: var(--zk-text);
}

.hero-tagline {
  margin: 0;
  font-size: 13px;
  color: var(--zk-text-2);
}

.hero-price {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 14px;
}

.hero-amount {
  font-size: 30px;
  font-weight: 600;
  color: var(--zk-accent-hover);
  font-variant-numeric: tabular-nums;
}

.hero-period {
  font-size: 12.5px;
  color: var(--zk-text-3);
}

.hero-features {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.hero-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-3);
  border: 1px solid var(--zk-border);
  font-size: 11.5px;
  color: var(--zk-text-2);
}

.hero-side {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding-left: 24px;
  border-left: 1px solid var(--zk-border);
}

.hero-side-label {
  font-size: 11px;
  letter-spacing: 0.3px;
  color: var(--zk-text-3);
}

.hero-side-value {
  font-size: 20px;
  font-weight: 600;
}

.hero-side-value.is-normal {
  color: #15803d;
}
.hero-side-value.is-warning {
  color: #b45309;
}
.hero-side-value.is-danger {
  color: #be123c;
}

.hero-side-note {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.hero-cta {
  margin-top: 10px;
  justify-content: space-between;
}

/* ---------- 通用块 ---------- */
.plan-block {
  margin-top: 16px;
  padding: 18px 20px 20px;
}

.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.block-title {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--zk-text);
}

.block-sub {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.block-meta {
  flex: 0 0 auto;
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.plan-error {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px;
  color: var(--zk-text-2);
}

.plan-error p {
  margin: 0 0 10px;
}

/* ---------- 额度 ---------- */
.usage-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
}

.usage-card {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 12px 14px 11px;
  background: var(--zk-surface);
  transition: opacity 380ms var(--zk-ease-soft), transform 480ms var(--zk-ease-spring),
    border-color var(--zk-dur-fast) var(--zk-ease-out);
  transition-delay: calc(var(--zk-i, 0) * var(--zk-stagger, 45ms)), 0ms, 0ms;
}

.usage-card:hover {
  border-color: var(--zk-border-strong);
}

.usage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.usage-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text-2);
}

.usage-level {
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--zk-surface-2);
  color: var(--zk-text-3);
}

.usage-level.is-warning {
  color: #b45309;
  background: #fff7e6;
}

.usage-level.is-danger {
  color: #be123c;
  background: #fff1f4;
}

.usage-numbers {
  margin: 8px 0 7px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.usage-numbers b {
  font-size: 21px;
  font-weight: 700;
  color: var(--zk-text);
}

.usage-limit {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.usage-track {
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.usage-track i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  /* 既有宽度进度条：首次从 0 推进，数据切换走 460ms 过渡 */
  transition: width 460ms var(--zk-ease-emphasis);
}

.usage-track i.is-warning {
  background: var(--zk-amber-edge);
}

.usage-track i.is-danger {
  background: var(--zk-red-edge);
}

.usage-hint {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.55;
  color: var(--zk-text-3);
}

/* ---------- KPI ---------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
}

.kpi-card {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 13px 14px;
  background: var(--zk-surface);
}

.kpi-name {
  font-size: 12px;
  color: var(--zk-text-3);
  letter-spacing: 0.2px;
}

.kpi-value {
  margin: 6px 0 6px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--zk-text);
}

.kpi-value span {
  font-size: 24px;
  font-weight: 700;
}

.kpi-value small {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.kpi-value.tone-teal span {
  color: #15803d;
}
.kpi-value.tone-indigo span {
  color: var(--zk-indigo);
}
.kpi-value.tone-amber span {
  color: #b45309;
}
.kpi-value.tone-red span {
  color: #be123c;
}
.kpi-value.tone-slate span {
  color: var(--zk-text-2);
}

.kpi-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.55;
  color: var(--zk-text-3);
}

.kpi-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px dashed var(--zk-border);
  font-size: 11.5px;
  color: var(--zk-text-3);
}

/* ---------- 套餐对比 ---------- */
.plans-block {
  margin-top: 20px;
}

.plans-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 14px;
  align-items: start;
}

.plan-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  padding: 18px 18px 16px;
  background: var(--zk-surface);
  /* 入场只动 opacity / transform；错开延迟只给 opacity，悬停抬起立即可响应 */
  transition: opacity 380ms var(--zk-ease-soft), transform 480ms var(--zk-ease-spring),
    border-color var(--zk-dur-base) var(--zk-ease-out),
    box-shadow var(--zk-dur-base) var(--zk-ease-out);
  transition-delay: calc(var(--zk-i, 0) * var(--zk-stagger, 50ms)), 0ms, 0ms, 0ms;
}

.plan-card:hover {
  border-color: var(--zk-border-strong);
}

/* v-reveal 落位后会写 transform: none，这里按同一参数补回悬停抬起与按下反馈 */
@media (hover: hover) and (pointer: fine) {
  .plan-card.is-in:hover {
    transform: translateY(-2px);
    box-shadow: var(--zk-shadow-md);
  }

  .plan-card.is-in:active {
    transform: translateY(0) scale(0.98);
    transition-duration: var(--zk-dur-instant);
  }
}

.plan-card.is-current {
  border-color: var(--zk-teal-dark);
  box-shadow: inset 0 0 0 1px var(--zk-teal-dark);
}

.plan-card.is-recommended {
  border-color: var(--zk-indigo);
}

.plan-flag {
  position: absolute;
  top: -9px;
  right: 14px;
  padding: 2px 9px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-accent);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

/* 当前套餐徽标：与「推荐」错开左右，避免叠在一起；等卡片浮现完再弹出来 */
.plan-current {
  position: absolute;
  top: -9px;
  left: 14px;
  padding: 2px 9px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-teal-dark);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
  animation-delay: calc(var(--zk-i, 0) * var(--zk-stagger, 50ms) + 260ms);
}

.plan-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--zk-text);
}

.plan-tagline {
  margin: 4px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
  min-height: 36px;
}

.plan-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 10px 0 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--zk-border);
}

.plan-amount {
  font-size: 25px;
  font-weight: 750;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.plan-period {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.plan-quotas {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.plan-quotas li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--zk-text-2);
}

.plan-quotas li b {
  color: var(--zk-text);
  font-weight: 650;
}

.plan-features {
  list-style: none;
  margin: 0 0 16px;
  padding: 12px 0 0;
  border-top: 1px dashed var(--zk-border);
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.plan-features li {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--zk-text-2);
}

.plan-features .el-icon {
  margin-top: 2px;
  color: var(--zk-teal-dark);
  flex: 0 0 auto;
}

.plan-cta {
  width: 100%;
}

/* ---------- 升级弹窗 ---------- */
/* 按钮三态切换时宽度不跳，文字/转圈/对勾在同一位置完成 */
.upgrade-submit {
  min-width: 132px;
}

.upgrade-lead {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--zk-text);
}

.upgrade-price {
  margin-left: 8px;
  font-weight: 700;
  color: var(--zk-teal-dark);
}

.upgrade-list {
  list-style: none;
  margin: 0 0 14px;
  padding: 12px 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}

.upgrade-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--zk-text-2);
}

.upgrade-list b {
  color: var(--zk-text);
}

@media (max-width: 1180px) {
  .plans-grid {
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }
}

@media (max-width: 860px) {
  .plan-hero {
    grid-template-columns: 1fr;
  }

  .hero-side {
    padding-left: 0;
    padding-top: 16px;
    border-left: none;
    border-top: 1px dashed rgba(148, 163, 184, 0.22);
  }
}

@media (max-width: 640px) {
  .plan-hero {
    padding: 18px 16px;
  }

  .plans-grid {
    grid-template-columns: 1fr;
  }

  .usage-grid,
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .upgrade-list {
    grid-template-columns: 1fr;
  }
}
</style>
