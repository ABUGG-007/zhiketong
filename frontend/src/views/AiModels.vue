<template>
  <div class="zk-page ai-page">
    <PageHeader
      title="大模型配置"
      description="DeepSeek、通义千问、智谱、Kimi、豆包、OpenAI、OpenRouter 与自建中转统一接入；保存后对话立即生效，无需重启服务。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate(null)">新增配置</el-button>
      </template>
    </PageHeader>

    <!-- 当前生效配置 -->
    <section class="zk-card active-card" :class="{ 'is-fallback': !activeConfig }">
      <div class="active-main">
        <span class="active-dot" aria-hidden="true" />
        <div class="active-copy">
          <div class="active-title">
            <span>{{ activeConfig ? activeConfig.name : '内置 yml 配置' }}</span>
            <span class="flag" :class="activeConfig ? 'flag--default' : 'flag--off'">
              {{ activeConfig ? activeConfig.providerLabel : '回退' }}
            </span>
          </div>
          <div v-if="activeConfig" class="active-sub">
            {{ activeConfig.model }} · {{ activeConfig.baseUrl }} · Key
            <span class="zk-mono">{{ activeConfig.apiKeyMasked || '未配置' }}</span>
          </div>
          <div v-else class="active-sub">
            尚未启用任何数据库配置，对话使用 application.yml 的 zhiketong.ai；未注入 DEEPSEEK_API_KEY 时自动走兜底话术。
            新增一条配置并设为默认即可切换。
          </div>
        </div>
      </div>
      <div class="active-meta">
        {{ enabledCount }} 个启用 / 共 {{ configs.length }} 个配置
      </div>
    </section>

    <ZkEmpty
      v-if="error"
      title="大模型配置读取失败"
      :hint="error"
    >
      <el-button @click="loadAll">重新加载</el-button>
    </ZkEmpty>

    <ZkSkeleton v-else-if="!configsLoaded" variant="card" :rows="3" class="model-skeleton" />

    <ZkEmpty
      v-else-if="!configs.length"
      title="还没有大模型配置"
      hint="新增一条即可接入平台；平台与密钥仅保存在服务端，列表只返回脱敏值"
    >
      <el-button type="primary" @click="openCreate(null)">新增配置</el-button>
    </ZkEmpty>

    <!-- 配置卡片 -->
    <section v-else class="model-grid" :aria-busy="loading ? 'true' : 'false'">
      <article
        v-for="(item, index) in configs"
        :key="item.id"
        v-reveal="{ index: revealIndex(index) }"
        class="zk-card model-card zk-hover-lift zk-press"
        :class="{ 'is-default': item.isDefault === 1, 'is-off': item.enabled !== 1 }"
      >
        <header class="model-head">
          <div class="model-title">
            <span class="model-name">{{ item.name }}</span>
            <span class="model-provider">{{ item.providerLabel }}</span>
          </div>
          <div class="model-flags">
            <span v-if="item.isDefault === 1" class="flag flag--default">默认</span>
            <span class="flag" :class="item.enabled === 1 ? 'flag--on' : 'flag--off'">
              {{ item.enabled === 1 ? '已启用' : '已停用' }}
            </span>
          </div>
        </header>

        <dl class="model-meta">
          <div>
            <dt>模型</dt>
            <dd class="zk-mono">{{ item.model }}</dd>
          </div>
          <div>
            <dt>API Key</dt>
            <dd class="zk-mono">{{ apiKeyText(item) }}</dd>
          </div>
          <div class="meta-wide">
            <dt>接口地址</dt>
            <dd class="zk-mono">{{ item.baseUrl }}</dd>
          </div>
          <div class="meta-wide">
            <dt>参数</dt>
            <dd>温度 {{ formatTemp(item.temperature) }} · 输出 {{ item.maxTokens }} tokens · 超时 {{ item.timeoutMs }} ms</dd>
          </div>
        </dl>

        <div class="model-test" :class="testTone(item)">
          <template v-if="item.lastTestTime">
            <span class="test-dot" aria-hidden="true" />
            <span>{{ item.lastTestOk === 1 ? '连接正常' : '连接失败' }} · {{ item.lastTestLatencyMs || 0 }}ms · {{ formatDateTime(item.lastTestTime) }}</span>
            <span v-if="item.lastTestMessage" class="test-msg">{{ item.lastTestMessage }}</span>
          </template>
          <template v-else>尚未测试连接</template>
        </div>

        <footer class="model-actions">
          <el-button
            size="small"
            :loading="testingId === item.id"
            :icon="testOkId === item.id ? Check : null"
            :class="{ 'test-done': testOkId === item.id }"
            @click="runTest(item)"
          >
            测试连接
          </el-button>
          <el-button
            size="small"
            :disabled="item.isDefault === 1 || item.enabled !== 1 || (!item.apiKeyConfigured && !keyOptional(item.provider, item.baseUrl))"
            @click="makeDefault(item)"
          >
            设为默认
          </el-button>
          <el-button size="small" @click="openEdit(item)">编辑</el-button>
          <el-button size="small" link type="danger" @click="remove(item)">删除</el-button>
          <el-switch
            class="model-switch"
            size="small"
            :model-value="item.enabled === 1"
            :loading="togglingId === item.id"
            @change="(value) => toggleStatus(item, value)"
          />
        </footer>
      </article>
    </section>

    <!-- 平台速览 -->
    <section class="zk-card preset-card">
      <header class="zk-card-head">
        <div>
          <div class="zk-card-title">已支持平台</div>
          <div class="zk-card-desc">
            所有平台统一走 OpenAI 兼容协议；点击任一平台即可带出其接口地址与常用模型
          </div>
        </div>
      </header>
      <div class="preset-groups">
        <div v-for="group in presetGroups" :key="group.name" class="preset-group">
          <div class="preset-group-name">
            {{ group.name }}
            <span>{{ group.items.length }}</span>
          </div>
          <ul>
            <li v-for="preset in group.items" :key="preset.provider" @click="openCreate(preset)">
              <b>{{ preset.label }}</b>
              <span class="zk-mono">{{ preset.baseUrl }}</span>
            </li>
          </ul>
        </div>
      </div>
      <p class="panel-note">
        配置写入 MySQL 表 ai_model_config，接口为 /api/admin/ai/configs；API Key 仅服务端保存，列表只返回脱敏值。
      </p>
    </section>

    <!-- 新增 / 编辑抽屉 -->
    <el-drawer
      v-model="drawerOpen"
      :title="editingId ? '编辑大模型配置' : '新增大模型配置'"
      size="min(660px, 94vw)"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="model-form zk-drawer-stagger"
        @submit.prevent
      >
        <el-form-item label="平台" prop="provider">
          <el-select
            v-model="form.provider"
            filterable
            placeholder="选择平台预设"
            popper-class="ai-provider-popper"
            @change="onProviderChange"
          >
            <el-option-group v-for="group in presetGroups" :key="group.name" :label="group.name">
              <el-option
                v-for="preset in group.items"
                :key="preset.provider"
                :label="preset.label"
                :value="preset.provider"
              >
                <span>{{ preset.label }}</span>
                <span class="option-hint">{{ preset.defaultModel }}</span>
              </el-option>
            </el-option-group>
          </el-select>
          <div v-if="currentPreset" class="field-hint">
            {{ currentPreset.description }}
            <a v-if="currentPreset.keyUrl" :href="currentPreset.keyUrl" target="_blank" rel="noopener">
              申请 Key →
            </a>
          </div>
        </el-form-item>

        <div class="field-grid">
          <el-form-item label="配置名称" prop="name">
            <el-input v-model="form.name" maxlength="64" placeholder="如 主力-DeepSeek" />
          </el-form-item>
          <el-form-item label="模型名称" prop="model">
            <el-select
              v-model="form.model"
              filterable
              allow-create
              default-first-option
              placeholder="选择或直接输入模型名"
            >
              <el-option v-for="name in modelOptions" :key="name" :label="name" :value="name" />
            </el-select>
            <div class="field-hint">支持直接输入平台新增的模型名或推理接入点 ID</div>
          </el-form-item>
        </div>

        <el-form-item label="接口地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" class="zk-mono" placeholder="https://api.deepseek.com/v1" />
          <div class="field-hint">OpenAI 兼容根地址，系统自动拼接 /chat/completions</div>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingId ? `留空沿用原密钥（${editingKeyMasked || '已配置'}）` : 'sk-...'"
          />
          <div class="field-hint">
            {{ keyOptional(form.provider, form.baseUrl)
              ? '本地 / 内网推理服务无需填写 Key，留空即可直接对话'
              : '密钥仅保存在服务端，列表接口只返回脱敏值' }}
          </div>
        </el-form-item>

        <div class="field-grid field-grid--three">
          <el-form-item label="温度">
            <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" controls-position="right" />
          </el-form-item>
          <el-form-item label="最大输出 token">
            <el-input-number v-model="form.maxTokens" :min="1" :max="32768" :step="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="超时（毫秒）">
            <el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" :step="1000" controls-position="right" />
          </el-form-item>
        </div>

        <el-form-item label="附加请求头（可选）">
          <el-input
            v-model="form.extraHeaders"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder='{"HTTP-Referer":"https://your-site.com","X-Title":"Zhiketong"}'
          />
          <div class="field-hint">OpenRouter 等中转站要求来源头时填写，JSON 格式</div>
        </el-form-item>

        <div class="switch-row">
          <div class="switch-copy">
            <b>启用该配置</b>
            <span>启用后才参与对话；停用不会删除配置与密钥</span>
          </div>
          <el-switch v-model="form.enabled" />
        </div>
        <div class="switch-row">
          <div class="switch-copy">
            <b>设为默认模型</b>
            <span>对话实际使用默认配置，同一时间只有一条；保存时自动启用</span>
          </div>
          <el-switch v-model="form.isDefault" />
        </div>

        <el-form-item label="备注（可选）">
          <el-input v-model="form.remark" maxlength="255" placeholder="如 售后问答主力，预算 50 元/月" />
        </el-form-item>

        <div v-if="drawerTest" class="drawer-test" :class="drawerTest.success ? 'is-ok' : 'is-fail'">
          <b>{{ drawerTest.success ? '连接成功' : '连接失败' }}</b>
          <span class="zk-mono" v-if="drawerTest.success">{{ drawerTest.model }} · {{ drawerTest.latencyMs }}ms</span>
          <span class="drawer-test-msg">{{ drawerTest.message }}</span>
          <span v-if="drawerTest.sample" class="drawer-test-msg">返回：{{ drawerTest.sample }}</span>
        </div>
      </el-form>

      <template #footer>
        <div class="drawer-actions">
          <span class="drawer-note">保存后对话立即使用新配置</span>
          <el-button @click="drawerOpen = false">取消</el-button>
          <el-button :loading="drawerTesting" :disabled="!editingId" @click="testEditing">
            测试连接
          </el-button>
          <el-button
            type="primary"
            class="zk-save"
            :class="`is-${saveState}`"
            :disabled="saveState === 'busy'"
            @click="saveForm(false)"
          >
            <span class="zk-save-label">保存</span>
            <span class="zk-save-mark zk-save-spin" aria-hidden="true" />
            <svg class="zk-save-mark zk-save-tick" viewBox="0 0 16 16" aria-hidden="true">
              <path d="M3.2 8.4l2.9 2.9 6.7-6.7" />
            </svg>
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { prefersReducedMotion } from '@/composables/motion'
import {
  createAiModelConfig,
  deleteAiModelConfig,
  getAiModelConfigs,
  getAiProviderPresets,
  setDefaultAiModelConfig,
  testAiModelConfig,
  toggleAiModelConfig,
  updateAiModelConfig
} from '@/api'
import { formatDateTime } from '@/utils/dict'

/* ---------- 列表 ---------- */
const configs = ref([])
const presets = ref([])
const loading = ref(false)
const configsLoaded = ref(false)
const error = ref('')
const testingId = ref(null)
const togglingId = ref(null)
const testOkId = ref(null)

/** 卡片入场错开序号：超过 4 张后不再累加，保证最晚一张入场仍 ≤ 700ms */
const revealIndex = (index) => Math.min(index, 4)

/* 保存按钮三连变：文字淡出 → 转圈 → 对勾（合计 350ms） */
const saveState = ref('idle')
let saveTimer = 0
let testOkTimer = 0

function flashSaved() {
  if (prefersReducedMotion()) return
  saveState.value = 'done'
  saveTimer = window.setTimeout(() => {
    saveState.value = 'idle'
  }, 350)
}

/* 测试连接成功：对勾停留 300ms 后回到常态 */
function flashTestOk(id) {
  if (prefersReducedMotion()) return
  window.clearTimeout(testOkTimer)
  testOkId.value = id
  testOkTimer = window.setTimeout(() => {
    testOkId.value = null
  }, 300)
}

onBeforeUnmount(() => {
  window.clearTimeout(saveTimer)
  window.clearTimeout(testOkTimer)
})

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

const sleep = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms))

const presetGroups = computed(() => {
  const order = ['国内平台', '国际平台', '中转与自建']
  const map = new Map()
  presets.value.forEach((preset) => {
    const key = preset.category || '其他'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(preset)
  })
  const names = [...order.filter((name) => map.has(name)), ...[...map.keys()].filter((name) => !order.includes(name))]
  return names.map((name) => ({ name, items: map.get(name) }))
})

const presetMap = computed(() => {
  const map = {}
  presets.value.forEach((preset) => {
    map[preset.provider] = preset
  })
  return map
})

const enabledCount = computed(() => configs.value.filter((item) => item.enabled === 1).length)

const activeConfig = computed(() => {
  const enabled = configs.value.filter((item) => item.enabled === 1)
  return enabled.find((item) => item.isDefault === 1) || enabled[0] || null
})

const formatTemp = (value) => (value === null || value === undefined ? '—' : Number(value).toFixed(2))

/* 本地推理平台无需 Key；自建网关地址在本机/内网时也允许留空，与后端 needsApiKey 保持一致 */
const KEYLESS_PROVIDERS = ['ollama', 'lmstudio', 'vllm']
const SELF_HOSTED_PROVIDERS = ['relay', 'custom']

function isLocalAddress(url) {
  if (!url) return false
  try {
    const host = new URL(String(url).trim()).hostname.toLowerCase()
    if (['localhost', '127.0.0.1', '0.0.0.0'].includes(host) || host.endsWith('.local')) return true
    if (host.startsWith('192.168.') || host.startsWith('10.')) return true
    if (host.startsWith('172.')) {
      const second = Number(host.split('.')[1])
      return second >= 16 && second <= 31
    }
    return false
  } catch {
    return false
  }
}

function keyOptional(provider, baseUrl) {
  const name = String(provider || '').toLowerCase()
  if (KEYLESS_PROVIDERS.includes(name)) return true
  if (SELF_HOSTED_PROVIDERS.includes(name)) return isLocalAddress(baseUrl)
  return false
}

function apiKeyText(item) {
  if (item.apiKeyConfigured) return item.apiKeyMasked
  return keyOptional(item.provider, item.baseUrl) ? '无需 Key' : '未配置'
}

function testTone(item) {
  if (!item.lastTestTime) return ''
  return item.lastTestOk === 1 ? 'is-ok' : 'is-fail'
}

async function loadConfigs() {
  loading.value = true
  error.value = ''
  try {
    configs.value = (await getAiModelConfigs()) || []
  } catch {
    configs.value = []
    error.value = '大模型配置读取失败，请确认后端服务已启动且当前账号为管理员'
  } finally {
    loading.value = false
    configsLoaded.value = true
  }
}

async function loadPresets() {
  try {
    presets.value = (await getAiProviderPresets()) || []
  } catch {
    presets.value = []
  }
}

async function loadAll() {
  await Promise.all([loadConfigs(), loadPresets()])
}

async function runTest(item) {
  if (testingId.value) return
  testingId.value = item.id
  const startedAt = performance.now()
  let passed = false
  try {
    const result = await testAiModelConfig(item.id)
    // 转圈至少 0.8s：请求过快时也能看清「正在测试」的过程
    await sleep(Math.max(0, 800 - (performance.now() - startedAt)))
    if (result?.success) {
      passed = true
      ElMessage.success(`连接成功 · ${result.latencyMs}ms`)
    } else {
      ElMessage.warning(result?.message || '连接失败')
    }
    await loadConfigs()
  } catch (err) {
    ElMessage.error(err?.message || '测试请求失败')
  } finally {
    testingId.value = null
    // 转圈结束后把按钮切成对勾并停留 300ms，明确告知「通了」
    if (passed) flashTestOk(item.id)
  }
}

async function makeDefault(item) {
  try {
    await ElMessageBox.confirm(
      `将「${item.name}」设为默认后，所有 AI 对话立即使用该配置。确定继续？`,
      '设为默认模型',
      { type: 'warning', confirmButtonText: '设为默认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await setDefaultAiModelConfig(item.id)
    ElMessage.success('已设为默认模型，对话立即生效')
    await loadConfigs()
  } catch {
    // 拦截器已提示
  }
}

async function toggleStatus(item, enabled) {
  if (togglingId.value) return
  togglingId.value = item.id
  try {
    await toggleAiModelConfig(item.id, enabled)
    ElMessage.success(enabled ? '已启用该配置' : '已停用该配置')
  } catch {
    // 拦截器已提示
  } finally {
    togglingId.value = null
    await loadConfigs()
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`删除后无法恢复，确定删除「${item.name}」？`, '删除配置', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteAiModelConfig(item.id)
    ElMessage.success('配置已删除')
    await loadConfigs()
  } catch {
    // 拦截器已提示
  }
}

/* ---------- 抽屉表单 ---------- */
const drawerOpen = ref(false)
const formRef = ref(null)
const editingId = ref(null)
const editingKeyMasked = ref('')
const saving = ref(false)
const drawerTesting = ref(false)
const drawerTest = ref(null)

const form = reactive({
  name: '',
  provider: 'deepseek',
  baseUrl: '',
  apiKey: '',
  model: '',
  temperature: 0.7,
  maxTokens: 800,
  timeoutMs: 15000,
  extraHeaders: '',
  enabled: true,
  isDefault: false,
  remark: ''
})

const currentPreset = computed(() => presetMap.value[form.provider] || null)

const modelOptions = computed(() => {
  const list = [...(currentPreset.value?.models || [])]
  if (form.model && !list.includes(form.model)) list.unshift(form.model)
  return list
})

const rules = computed(() => ({
  provider: [{ required: true, message: '请选择平台', trigger: 'change' }],
  name: [
    { required: true, message: '请填写配置名称', trigger: 'blur' },
    { max: 64, message: '配置名称不能超过 64 个字符', trigger: 'blur' }
  ],
  baseUrl: [
    { required: true, message: '请填写接口地址', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/i, message: '接口地址需以 http:// 或 https:// 开头', trigger: 'blur' }
  ],
  model: [{ required: true, message: '请填写模型名称', trigger: 'change' }],
  apiKey: editingId.value || keyOptional(form.provider, form.baseUrl)
    ? []
    : [{ required: true, message: '请填写 API Key', trigger: 'blur' }]
}))

function resetForm() {
  editingId.value = null
  editingKeyMasked.value = ''
  drawerTest.value = null
  formRef.value?.clearValidate()
  form.name = ''
  form.provider = 'deepseek'
  form.baseUrl = presetMap.value.deepseek?.baseUrl || 'https://api.deepseek.com/v1'
  form.apiKey = ''
  form.model = presetMap.value.deepseek?.defaultModel || 'deepseek-chat'
  form.temperature = 0.7
  form.maxTokens = 800
  form.timeoutMs = 15000
  form.extraHeaders = ''
  form.enabled = true
  form.isDefault = false
  form.remark = ''
}

function applyPreset(preset) {
  if (!preset) return
  form.provider = preset.provider
  form.baseUrl = preset.baseUrl || form.baseUrl
  form.model = preset.defaultModel || form.model
  form.extraHeaders = preset.extraHeaders || ''
  if (!form.name) form.name = preset.label
}

function openCreate(preset) {
  resetForm()
  applyPreset(preset || presetMap.value.deepseek)
  drawerOpen.value = true
}

function openEdit(item) {
  resetForm()
  editingId.value = item.id
  editingKeyMasked.value = item.apiKeyMasked || ''
  form.name = item.name
  form.provider = item.provider
  form.baseUrl = item.baseUrl
  form.apiKey = ''
  form.model = item.model
  form.temperature = item.temperature === null || item.temperature === undefined ? 0.7 : Number(item.temperature)
  form.maxTokens = item.maxTokens || 800
  form.timeoutMs = item.timeoutMs || 15000
  form.extraHeaders = item.extraHeaders || ''
  form.enabled = item.enabled === 1
  form.isDefault = item.isDefault === 1
  form.remark = item.remark || ''
  drawerOpen.value = true
}

function onProviderChange(provider) {
  const preset = presetMap.value[provider]
  if (!preset) return
  const knownUrls = new Set(presets.value.map((item) => item.baseUrl).filter(Boolean))
  // 当前地址为空或仍是某个预设地址时视为「跟随平台」，直接切换；手填地址不覆盖
  if (!form.baseUrl || knownUrls.has(form.baseUrl)) {
    form.baseUrl = preset.baseUrl || form.baseUrl
  }
  form.model = preset.defaultModel || form.model
  form.extraHeaders = preset.extraHeaders || ''
  // 名称未被手动修改（仍等于某个预设名称）时跟随平台，避免出现「平台是 LM Studio、名称是 DeepSeek」
  const knownLabels = presets.value.map((item) => item.label)
  if (!form.name || knownLabels.includes(form.name)) {
    form.name = preset.label
  }
}

async function saveForm(silent) {
  if (saveState.value === 'busy') return false
  try {
    await formRef.value?.validate()
  } catch {
    shakeFirstError()
    return false
  }
  saving.value = true
  saveState.value = 'busy'
  try {
    const payload = {
      name: form.name.trim(),
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      model: form.model.trim(),
      temperature: Number(form.temperature),
      maxTokens: Number(form.maxTokens),
      timeoutMs: Number(form.timeoutMs),
      extraHeaders: form.extraHeaders.trim(),
      enabled: Boolean(form.enabled),
      isDefault: Boolean(form.isDefault),
      remark: form.remark.trim()
    }
    if (editingId.value) {
      await updateAiModelConfig(editingId.value, payload)
    } else {
      await createAiModelConfig(payload)
    }
    if (!silent) {
      ElMessage.success(editingId.value ? '配置已保存，对话立即生效' : '配置已创建')
    }
    if (!silent) {
      // 对勾停留 350ms 再收起抽屉，避免「点了没反馈」就消失
      flashSaved()
      if (!prefersReducedMotion()) await sleep(360)
      drawerOpen.value = false
    }
    await loadConfigs()
    return true
  } catch {
    return false
  } finally {
    saving.value = false
    if (saveState.value === 'busy') saveState.value = 'idle'
  }
}

async function testEditing() {
  if (!editingId.value || drawerTesting.value) return
  drawerTesting.value = true
  try {
    // 先保存当前表单，确保测试的是最新地址与密钥
    const saved = await saveForm(true)
    if (!saved) return
    drawerTest.value = await testAiModelConfig(editingId.value)
    await loadConfigs()
  } catch (err) {
    drawerTest.value = { success: false, message: err?.message || '测试请求失败' }
  } finally {
    drawerTesting.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
}

/* 首屏骨架：与配置卡片同宽同高，加载前后不跳 */
.model-skeleton {
  padding: 16px 18px;
  margin-bottom: 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface);
}

/* 测试连接成功：对勾轻弹 300ms，不做位移 */
.test-done {
  animation: zk-pop-in 300ms var(--zk-ease-spring);
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

/* ---------- 当前生效 ---------- */
.active-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 18px;
  margin-bottom: 14px;
  border-left: 3px solid var(--zk-green-line);
}

.active-card.is-fallback {
  border-left-color: var(--zk-amber-line);
}

.active-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.active-dot {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--zk-green);
  box-shadow: 0 0 0 3px var(--zk-green-soft);
}

.active-card.is-fallback .active-dot {
  background: var(--zk-amber);
  box-shadow: 0 0 0 3px var(--zk-amber-soft);
}

.active-copy {
  min-width: 0;
}

.active-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.active-sub {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
  word-break: break-all;
}

.active-meta {
  flex-shrink: 0;
  font-size: 11.5px;
  color: var(--zk-text-3);
  white-space: nowrap;
}

/* ---------- 配置卡片 ---------- */
.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.model-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px 14px;
}

.model-card.is-default {
  border-color: var(--zk-accent-line);
  box-shadow: inset 0 0 0 1px var(--zk-accent-line), var(--zk-shadow-sm);
}

.model-card.is-off {
  background: var(--zk-surface-2);
}

.model-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.model-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.model-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-provider {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.model-flags {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.flag {
  padding: 2px 7px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-xs);
  background: var(--zk-surface-2);
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--zk-text-3);
  white-space: nowrap;
}

.flag--default {
  color: var(--zk-accent-hover);
  border-color: var(--zk-accent-line);
  background: var(--zk-accent-soft);
}

.flag--on {
  color: var(--zk-green);
  border-color: var(--zk-green-line);
  background: var(--zk-green-soft);
}

.flag--off {
  color: var(--zk-text-3);
}

.model-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  margin: 0;
}

.model-meta > div {
  min-width: 0;
}

.model-meta dt {
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.model-meta dd {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--zk-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-wide {
  grid-column: 1 / -1;
}

.model-test {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 7px 9px;
  border: 1px dashed var(--zk-border);
  border-radius: var(--zk-radius-xs);
  background: var(--zk-surface-2);
  font-size: 11px;
  color: var(--zk-text-3);
}

.model-test.is-ok {
  color: var(--zk-green);
  border-style: solid;
  border-color: var(--zk-green-line);
  background: var(--zk-green-soft);
}

.model-test.is-fail {
  color: var(--zk-red);
  border-style: solid;
  border-color: var(--zk-red-line);
  background: var(--zk-red-soft);
}

.test-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: currentColor;
}

.test-msg {
  width: 100%;
  color: var(--zk-text-3);
  line-height: 1.6;
  word-break: break-all;
}

.model-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--zk-border);
}

.model-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.model-switch {
  margin-left: auto;
}

/* ---------- 平台速览 ---------- */
.preset-card {
  padding: 16px 18px 14px;
}

.preset-groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.preset-group-name {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 650;
  color: var(--zk-text);
}

.preset-group-name span {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--zk-surface-3);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--zk-text-3);
}

.preset-group ul {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.preset-group li {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 8px;
  border-radius: var(--zk-radius-xs);
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.preset-group li:hover {
  background: var(--zk-accent-soft);
}

.preset-group li b {
  font-size: 12px;
  font-weight: 550;
  color: var(--zk-text);
}

.preset-group li span {
  font-size: 10.5px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-note {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--zk-border);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
}

/* ---------- 抽屉表单 ---------- */
.model-form {
  padding-bottom: 4px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.field-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field-hint {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.field-hint a {
  color: var(--zk-accent);
  text-decoration: none;
}

.option-hint {
  float: right;
  margin-left: 12px;
  font-size: 11px;
  color: var(--zk-text-3);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 11px 14px;
  margin: 2px 0 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
}

.switch-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.switch-copy b {
  font-size: 12.5px;
  color: var(--zk-text);
}

.switch-copy span {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.drawer-test {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  margin-top: 4px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.drawer-test.is-ok {
  border-color: var(--zk-green-line);
  background: var(--zk-green-soft);
}

.drawer-test.is-fail {
  border-color: var(--zk-red-line);
  background: var(--zk-red-soft);
}

.drawer-test b {
  font-size: 12.5px;
  color: var(--zk-text);
}

.drawer-test.is-ok b {
  color: var(--zk-green);
}

.drawer-test.is-fail b {
  color: var(--zk-red);
}

.drawer-test-msg {
  line-height: 1.6;
  word-break: break-all;
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.drawer-note {
  margin-right: auto;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

/* 平台下拉共 29 个预设：提高面板高度、分组标题吸顶、常显滚动条，避免误以为只有国内平台 */
:global(.ai-provider-popper .el-select-dropdown__wrap) {
  max-height: 428px;
}

:global(.ai-provider-popper .el-scrollbar__bar.is-vertical) {
  opacity: 0.9;
}

:global(.ai-provider-popper .el-select-group__title) {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--el-bg-color-overlay, #ffffff);
}

@media (max-width: 1200px) {
  .preset-groups {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .model-grid,
  .preset-groups,
  .field-grid,
  .field-grid--three {
    grid-template-columns: 1fr;
  }

  .active-card {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
