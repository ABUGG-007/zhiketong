<template>
  <div class="zk-page">
    <PageHeader
      title="接入方式"
      description="配置保存在租户配置表，刷新控制台不会丢失；商家页面只需携带 appKey，主题与话术由服务端下发。"
    >
      <template #actions>
        <el-button
          type="primary"
          :icon="Check"
          :loading="saving"
          :disabled="loading || !dirty"
          @click="save"
        >
          {{ dirty ? '保存配置' : '已保存' }}
        </el-button>
        <el-button :icon="trial ? Close : Pointer" @click="toggleTrial">
          {{ trial ? '关闭试用' : '在本页试用' }}
        </el-button>
        <el-button :icon="TopRight" @click="openDemo">演示站点</el-button>
        <el-button :icon="CopyDocument" @click="copy(snippet)">复制接入代码</el-button>
      </template>
    </PageHeader>

    <!-- 接入状态 -->
    <div class="status-bar" role="status">
      <span class="status-item" :class="status === 1 ? 'is-ok' : 'is-off'"><i />
        {{ status === 1 ? '接入已启用' : '接入已停用' }}
      </span>
      <span class="status-divider" aria-hidden="true" />
      <span class="status-item">SDK v1.1 · 签名接入</span>
      <span class="status-divider" aria-hidden="true" />
      <span class="status-item">Redis 计数 · 多实例共享</span>
      <span class="status-divider" aria-hidden="true" />
      <span class="status-item" :class="{ 'is-dirty': dirty, 'zk-flash': dirtyFlash }">
        {{ dirty ? '有未保存修改' : '配置已同步' }}
      </span>
      <span class="status-right">
        <el-switch
          v-model="status"
          :active-value="1"
          :inactive-value="0"
          size="small"
          aria-label="接入开关"
        />
        <span class="status-switch-label">{{ status === 1 ? '启用' : '停用' }}</span>
        <span class="status-divider" aria-hidden="true" />
        {{ tenantName || '当前租户' }}
        <code>{{ tenantCode }}</code>
      </span>
    </div>

    <div class="embed-grid">
      <!-- 配置 -->
      <section class="zk-card config-card" aria-label="接入配置">
        <div class="tabs" role="tablist" aria-label="接入方式">
          <button
            v-for="item in channels"
            :key="item.key"
            type="button"
            role="tab"
            class="tab zk-press"
            :class="{ 'is-active': channel === item.key }"
            :aria-selected="channel === item.key"
            @click="channel = item.key"
          >
            {{ item.label }}
          </button>
        </div>

        <!-- JS 悬浮球 -->
        <div v-if="channel === 'js'" class="panel">
          <div class="field">
            <label class="field-label">主题色</label>
            <div class="swatches">
              <button
                v-for="item in colors"
                :key="item.value"
                type="button"
                class="swatch zk-press"
                :class="{ 'is-active': color === item.value }"
                :style="{ background: item.value }"
                :aria-label="item.name"
                :aria-pressed="color === item.value"
                @click="color = item.value"
              >
                <el-icon v-if="color === item.value"><Check /></el-icon>
              </button>
              <span class="swatch-name">{{ colorName }}</span>
            </div>
          </div>

          <div class="field-row">
            <div class="field">
              <label class="field-label">悬浮位置</label>
              <el-radio-group v-model="position" size="small">
                <el-radio-button value="right">右下角</el-radio-button>
                <el-radio-button value="left">左下角</el-radio-button>
              </el-radio-group>
            </div>
            <div class="field field--switch">
              <label class="field-label">未读红点</label>
              <el-switch v-model="badge" />
            </div>
          </div>

          <div class="field">
            <label class="field-label" for="embed-brand">客服名称</label>
            <el-input id="embed-brand" v-model="brand" maxlength="20" show-word-limit />
          </div>

          <div class="field">
            <label class="field-label" for="embed-welcome">欢迎语</label>
            <el-input
              id="embed-welcome"
              v-model="welcome"
              type="textarea"
              :rows="2"
              maxlength="80"
              show-word-limit
            />
          </div>

          <div class="field">
            <label class="field-label" for="embed-origins">允许的嵌入来源</label>
            <el-input
              id="embed-origins"
              v-model="allowedOrigins"
              placeholder="https://www.example.com, https://*.example.com"
              maxlength="500"
            />
            <p class="hint">留空不限制来源；正式环境建议只填商家域名，避免 appKey 被其他站点复用。</p>
          </div>

          <div class="field">
            <label class="field-label">单 IP 提问限流</label>
            <div class="inline-control">
              <el-input-number v-model="rateLimit" :min="1" :max="600" size="small" />
              <span class="hint-inline">次 / 分钟 · Redis 计数，多实例共享</span>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <label class="field-label">接入代码</label>
              <button type="button" class="copy-link zk-press" @click="copy(snippet)">复制</button>
            </div>
            <pre class="code"><code>{{ snippet }}</code></pre>
            <p class="hint">
              粘贴到商家网站 <code>&lt;/body&gt;</code> 之前。只需 <code>data-app-key</code>，主题色、位置、欢迎语由服务端配置下发；
              也可用 <code>data-color</code> 等属性在单个页面临时覆盖。
            </p>
          </div>
        </div>

        <!-- 独立链接 -->
        <div v-else-if="channel === 'link'" class="panel">
          <p class="panel-lead">
            无需开发，把专属链接投放到公众号、邮件或广告位，打开即是完整对话页。
          </p>
          <div class="field">
            <label class="field-label">对话页地址</label>
            <div class="copy-row">
              <code>{{ chatUrl }}</code>
              <el-button size="small" :icon="CopyDocument" @click="copy(chatUrl)">复制</el-button>
            </div>
          </div>
          <div class="field">
            <label class="field-label">演示站点</label>
            <div class="copy-row">
              <code>{{ demoUrl }}</code>
              <el-button size="small" :icon="TopRight" @click="openDemo">打开</el-button>
            </div>
          </div>
          <ul class="check-list">
            <li><el-icon><Check /></el-icon>自动适配手机与桌面宽度</li>
            <li><el-icon><Check /></el-icon>无需登录即可咨询常见问题</li>
            <li><el-icon><Check /></el-icon>可放在公众号菜单、邮件签名与广告落地页</li>
          </ul>
        </div>

        <!-- API 对接 -->
        <div v-else class="panel">
          <p class="panel-lead">
            已有小程序或 App 的商家：浏览器先申请短时令牌，服务端对接使用 HMAC 签名，仅知道接口地址无法调用。
          </p>
          <div class="api-endpoint">
            <span class="method method--post">POST</span>
            <code>/api/public/embed/token</code>
            <span class="api-desc">申请短时令牌</span>
          </div>
          <div class="api-endpoint">
            <span class="method">GET</span>
            <code>/api/public/embed/config</code>
            <span class="api-desc">读取悬浮球配置</span>
          </div>
          <div class="api-endpoint">
            <span class="method method--post">POST</span>
            <code>/api/public/embed/answer</code>
            <span class="api-desc">知识库问答</span>
          </div>

          <div class="field">
            <label class="field-label">接入凭证</label>
            <div class="cred">
              <div class="cred-row">
                <span class="cred-label">App Key</span>
                <code>{{ appKey }}</code>
                <button type="button" class="copy-link zk-press" @click="copy(appKey)">复制</button>
              </div>
              <div class="cred-row">
                <span class="cred-label">App Secret</span>
                <code>{{ secretVisible ? appSecret : maskedSecret }}</code>
                <button type="button" class="copy-link zk-press" @click="secretVisible = !secretVisible">
                  {{ secretVisible ? '隐藏' : '显示' }}
                </button>
                <button type="button" class="copy-link zk-press" @click="copy(appSecret)">复制</button>
              </div>
              <div class="cred-foot">
                <span>密钥只用于服务端 HMAC 签名，不要写进商家页面。重置后旧令牌与旧签名立即失效。</span>
                <el-button size="small" :loading="rotating" @click="rotateSecret">重置密钥</el-button>
              </div>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <label class="field-label">签名请求示例</label>
              <button type="button" class="copy-link zk-press" @click="copy(curl)">复制 cURL</button>
            </div>
            <pre class="code"><code>{{ curl }}</code></pre>
            <p class="hint">
              签名串：<code>appKey\ntimestamp\nnonce\ncontent</code>，结果为小写十六进制 HMAC-SHA256；
              时间戳 ±5 分钟，nonce 8-64 位且 5 分钟内不可重复。
            </p>
          </div>

          <div class="field">
            <label class="field-label">响应示例</label>
            <pre class="code"><code>{{ responseSample }}</code></pre>
          </div>
          <p class="hint">限流按「租户 + 来源 IP」在 Redis 计数（默认 30 次/分钟），多实例部署共享额度。</p>
        </div>
      </section>

      <!-- 实时预览 -->
      <section class="preview-card" aria-label="接入效果预览">
        <div class="preview-head">
          <div>
            <div class="preview-title">实时预览</div>
            <div class="preview-sub">示例站点「云栖山居」+ 真实悬浮球</div>
          </div>
          <div class="preview-actions">
            <el-button size="small" :icon="Refresh" @click="reloadPreview">刷新</el-button>
          </div>
        </div>

        <div class="browser">
          <div class="browser-bar">
            <span class="lights" aria-hidden="true"><i /><i /><i /></span>
            <span class="browser-url">
              <el-icon aria-hidden="true"><Lock /></el-icon>
              yunqi.demo/zhiketong
            </span>
            <span class="browser-live"><i />LIVE</span>
          </div>
          <iframe
            ref="previewFrame"
            :src="previewSrc"
            class="browser-frame"
            title="接入效果预览"
          />
        </div>

        <div class="preview-foot">
          <span>点击右下角气泡，体验真实知识库问答</span>
          <button type="button" class="link-btn zk-press" @click="openDemo">新窗口打开 →</button>
        </div>
      </section>
    </div>

    <!-- 三种接入方式 -->
    <section class="ways" aria-label="接入方式对比" v-reveal.group>
      <article v-for="way in ways" :key="way.key" class="way-card">
        <span class="way-icon"><el-icon><component :is="way.icon" /></el-icon></span>
        <div class="way-body">
          <div class="way-title">
            {{ way.title }}
            <span v-if="way.recommended" class="way-tag">推荐</span>
          </div>
          <p class="way-desc">{{ way.desc }}</p>
          <div class="way-scene">{{ way.scene }}</div>
        </div>
        <button type="button" class="way-go zk-press" @click="channel = way.key">
          {{ channel === way.key ? '正在配置' : '配置' }} →
        </button>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  Close,
  CopyDocument,
  Connection,
  Link,
  Lock,
  Monitor,
  Pointer,
  Promotion,
  Refresh,
  TopRight
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getEmbedConfig, rotateEmbedSecret, updateEmbedConfig } from '@/api'

const channels = [
  { key: 'js', label: 'JS 悬浮球' },
  { key: 'link', label: '独立链接' },
  { key: 'api', label: 'API 对接' }
]

const colors = [
  { name: '靛蓝', value: '#4f46e5' },
  { name: '松绿', value: '#2f6f5f' },
  { name: '墨黑', value: '#17181c' },
  { name: '陶土', value: '#b4532f' }
]

const ways = [
  {
    key: 'js',
    title: 'JS 悬浮球',
    desc: '一行脚本挂载右下角客服，样式隔离，不污染商家页面。',
    scene: '官网、落地页、活动页',
    icon: Connection,
    recommended: true
  },
  {
    key: 'link',
    title: '独立链接',
    desc: '生成专属对话链接，无需改动网站，直接投放。',
    scene: '公众号菜单、邮件、广告位',
    icon: Link
  },
  {
    key: 'api',
    title: 'API 对接',
    desc: '调用公开问答接口，把知识库能力接入自有 App。',
    scene: '小程序、App、客服大屏',
    icon: Promotion
  }
]

const channel = ref('js')
const color = ref('#4f46e5')
const position = ref('right')
const badge = ref(true)
const brand = ref('智客通客服')
const welcome = ref('')
const tenantCode = ref('demo')
const tenantName = ref('')
const appKey = ref('')
const appSecret = ref('')
const secretVisible = ref(false)
const allowedOrigins = ref('')
const rateLimit = ref(30)
const status = ref(1)
const loading = ref(true)
const saving = ref(false)
const rotating = ref(false)
const dirty = ref(false)
/** 待保存状态变化时闪一下 */
const dirtyFlash = ref(false)

const origin = window.location.origin
const trial = ref(false)
const previewFrame = ref(null)
const previewKey = ref(Date.now())
let scriptEl = null
let trialApi = null
let applyTimer = null
// 回填服务端配置时抑制“有未保存修改”标记
let hydrating = false

const colorName = computed(
  () => colors.find((item) => item.value === color.value)?.name || '自定义'
)

const maskedSecret = computed(() => {
  const secret = appSecret.value || ''
  if (secret.length <= 8) return '••••••••'
  return `${secret.slice(0, 4)}••••••••${secret.slice(-4)}`
})

function sanitize(text) {
  return String(text || '').replace(/"/g, '“').replace(/\s+/g, ' ').trim()
}

const snippet = computed(() => {
  const lines = [
    `<script src="${origin}/embed/zhiketong-widget.js"`,
    `        data-app-key="${appKey.value}"`,
    `        defer><\/script>`
  ]
  return lines.join('\n')
})

const chatUrl = computed(
  () => `${origin}/embed/chat?widget=1&appKey=${encodeURIComponent(appKey.value)}`
)
const demoUrl = computed(
  () => `${origin}/embed/demo?appKey=${encodeURIComponent(appKey.value)}`
)

const previewSrc = computed(() => {
  const params = new URLSearchParams({
    embed: '1',
    appKey: appKey.value,
    color: color.value,
    position: position.value,
    brand: sanitize(brand.value),
    welcome: sanitize(welcome.value),
    badge: badge.value ? '1' : '0',
    v: String(previewKey.value)
  })
  return `/embed/demo?${params.toString()}`
})

const curl = computed(() =>
  [
    `curl -X POST ${origin}/api/public/embed/answer \\`,
    `  -H "Content-Type: application/json" \\`,
    `  -H "X-ZKT-App-Key: ${appKey.value}" \\`,
    `  -H "X-ZKT-Timestamp: 1760958000000" \\`,
    `  -H "X-ZKT-Nonce: 3f9c1a7b2d5e4c60" \\`,
    `  -H "X-ZKT-Sign: <HMAC-SHA256(appSecret, appKey\\ntimestamp\\nnonce\\ncontent)>" \\`,
    `  -d '{"content":"可以开专票吗"}'`
  ].join('\n')
)

const responseSample = `{
  "code": 200,
  "message": "操作成功",
  "data": {
    "answer": "可以。在「订单详情 → 申请发票」提交抬头即可。",
    "matched": true,
    "score": 0.92,
    "suggestHuman": false,
    "sourceQuestion": "发票可以开专票吗"
  }
}`

async function copy(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const area = document.createElement('textarea')
      area.value = text
      area.style.position = 'fixed'
      area.style.opacity = '0'
      document.body.appendChild(area)
      area.select()
      document.execCommand('copy')
      document.body.removeChild(area)
    }
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.warning('复制失败，请手动选择代码')
  }
}

function openDemo() {
  window.open(demoUrl.value, '_blank', 'noopener')
}

function reloadPreview() {
  previewKey.value = Date.now()
}

function loadWidgetScript() {
  return new Promise((resolve, reject) => {
    if (window.ZhiketongWidget) {
      resolve(window.ZhiketongWidget)
      return
    }
    scriptEl = document.createElement('script')
    scriptEl.src = '/embed/zhiketong-widget.js'
    scriptEl.async = true
    scriptEl.setAttribute('data-auto-init', 'false')
    scriptEl.addEventListener('load', () => resolve(window.ZhiketongWidget))
    scriptEl.addEventListener('error', reject)
    document.body.appendChild(scriptEl)
  })
}

async function mountTrial() {
  if (!appKey.value) {
    ElMessage.warning('接入配置尚未加载完成，请稍后重试')
    return
  }
  try {
    const api = await loadWidgetScript()
    if (!api) return
    api.destroy()
    // 控制台内试用：用当前草稿值覆盖服务端配置，便于保存前先看效果
    api.init({
      appKey: appKey.value,
      color: color.value,
      position: position.value,
      brand: brand.value,
      welcome: welcome.value,
      badge: badge.value
    })
    trialApi = api
    trial.value = true
  } catch (error) {
    ElMessage.error('悬浮球加载失败，请检查构建产物')
  }
}

/**
 * 回填服务端配置；hydrating 用于抑制回填过程中触发“未保存修改”。
 */
function applyServerConfig(data) {
  if (!data) return
  hydrating = true
  tenantCode.value = data.tenantCode || tenantCode.value
  tenantName.value = data.tenantName || ''
  appKey.value = data.appKey || ''
  appSecret.value = data.appSecret || ''
  brand.value = data.brandName || brand.value
  welcome.value = data.welcome || ''
  color.value = /^#[0-9a-fA-F]{6}$/.test(String(data.themeColor || ''))
    ? data.themeColor
    : color.value
  position.value = data.position === 'left' ? 'left' : 'right'
  badge.value = Boolean(data.badge)
  allowedOrigins.value = data.allowedOrigins || ''
  rateLimit.value = Number(data.rateLimitPerMinute) || 30
  status.value = Number(data.status) === 0 ? 0 : 1
  nextTick(() => {
    dirty.value = false
    hydrating = false
  })
}

async function loadConfig() {
  loading.value = true
  try {
    const data = await getEmbedConfig()
    applyServerConfig(data)
    reloadPreview()
  } catch (error) {
    ElMessage.error(error?.message || '接入配置读取失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (saving.value) return
  if (!brand.value.trim()) {
    ElMessage.warning('客服名称不能为空')
    return
  }
  saving.value = true
  try {
    const data = await updateEmbedConfig({
      brandName: brand.value.trim(),
      welcome: welcome.value.trim(),
      themeColor: color.value,
      position: position.value,
      badge: badge.value,
      allowedOrigins: allowedOrigins.value.trim(),
      rateLimitPerMinute: Number(rateLimit.value) || 30,
      status: status.value
    })
    applyServerConfig(data)
    ElMessage.success('配置已保存，商家页面刷新后生效')
    reloadPreview()
  } catch (error) {
    // 请求拦截器已统一提示，这里只负责结束 loading
  } finally {
    saving.value = false
  }
}

async function rotateSecret() {
  if (rotating.value || !appKey.value) return
  try {
    await ElMessageBox.confirm(
      '重置后已签发的浏览器令牌与商家服务端的签名配置立即失效，需要同步更新对接方密钥。确定继续？',
      '重置签名密钥',
      { type: 'warning', confirmButtonText: '立即重置', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  rotating.value = true
  try {
    const data = await rotateEmbedSecret()
    if (data?.appKey) appKey.value = data.appKey
    if (data?.appSecret) {
      appSecret.value = data.appSecret
      secretVisible.value = true
    }
    ElMessage.success('密钥已重置，请同步更新服务端签名配置')
  } catch (error) {
    // 请求拦截器已统一提示
  } finally {
    rotating.value = false
  }
}

// 外观、白名单、限流、开关任一改动都标记为待保存
watch(
  [color, position, badge, brand, welcome, allowedOrigins, rateLimit, status],
  () => {
    if (hydrating) return
    dirty.value = true
  }
)

// 出现「有未保存修改」时闪一下，避免用户在长页面里漏掉状态变化
let dirtyTimer = null
watch(dirty, (value) => {
  if (!value) return
  dirtyFlash.value = true
  if (dirtyTimer) window.clearTimeout(dirtyTimer)
  dirtyTimer = window.setTimeout(() => {
    dirtyFlash.value = false
  }, 700)
})

function unmountTrial() {
  if (trialApi) trialApi.destroy()
  trialApi = null
  trial.value = false
}

function toggleTrial() {
  if (trial.value) {
    unmountTrial()
    ElMessage.info('已关闭本页悬浮球')
  } else {
    mountTrial()
    ElMessage.success('已在本页挂载悬浮球，见右下角')
  }
}

// 配置变更后延迟刷新预览，避免每次按键都重载 iframe
watch([color, position, badge, brand, welcome], () => {
  if (hydrating) return
  if (channel.value !== 'js') return
  if (applyTimer) window.clearTimeout(applyTimer)
  applyTimer = window.setTimeout(() => {
    reloadPreview()
    if (trial.value) mountTrial()
  }, 600)
})

onMounted(() => {
  loadConfig()
  // 预加载脚本，点击「在本页试用」时无需等待
  loadWidgetScript().catch(() => {})
})

onBeforeUnmount(() => {
  unmountTrial()
  if (scriptEl && scriptEl.parentNode) scriptEl.parentNode.removeChild(scriptEl)
  if (applyTimer) window.clearTimeout(applyTimer)
  if (dirtyTimer) window.clearTimeout(dirtyTimer)
})
</script>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 9px 14px;
  margin-bottom: 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface);
  font-size: 12px;
  color: var(--zk-text-3);
}

.status-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-item.is-ok {
  color: var(--zk-text-2);
}

.status-item.is-ok i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.status-item.is-off {
  color: var(--zk-text-3);
}

.status-item.is-off i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d4d4d8;
}

.status-item.is-dirty {
  color: #b45309;
}

.status-switch-label {
  font-size: 11.5px;
  color: var(--zk-text-2);
}

.status-divider {
  width: 1px;
  height: 10px;
  background: var(--zk-border);
}

.status-right {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-right code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--zk-text-2);
  background: var(--zk-surface-3);
  padding: 1px 6px;
  border-radius: 4px;
}

.embed-grid {
  display: grid;
  grid-template-columns: minmax(0, 400px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

/* ---------- 配置 ---------- */
.config-card {
  overflow: hidden;
}

.tabs {
  display: flex;
  gap: 2px;
  padding: 8px;
  border-bottom: 1px solid var(--zk-border);
  background: var(--zk-surface-2);
}

.tab {
  flex: 1;
  height: 30px;
  border: 0;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  color: var(--zk-text-3);
  font-size: 12.5px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
}

.tab:hover {
  color: var(--zk-text);
}

.tab.is-active {
  background: var(--zk-surface);
  color: var(--zk-text);
  box-shadow: inset 0 0 0 1px var(--zk-border);
}

.panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-lead {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--zk-text-2);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.field--switch {
  align-items: flex-start;
}

.field-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-label {
  font-size: 12px;
  color: var(--zk-text-3);
}

.inline-control {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.hint-inline {
  font-size: 11px;
  color: var(--zk-text-3);
}

.cred {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: #fafafa;
  padding: 4px 10px 8px;
}

.cred-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--zk-border);
}

.cred-row:last-of-type {
  border-bottom: 0;
}

.cred-label {
  flex: 0 0 74px;
  font-size: 11px;
  color: var(--zk-text-3);
}

.cred-row code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--zk-text-2);
}

.cred-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 8px;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.swatches {
  display: flex;
  align-items: center;
  gap: 8px;
}

.swatch {
  width: 26px;
  height: 26px;
  border-radius: var(--zk-radius-sm);
  border: 2px solid transparent;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.28);
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  transition: transform var(--zk-dur-fast) var(--zk-ease-out);
}

.swatch:hover {
  outline: 2px solid var(--zk-border-strong);
  outline-offset: 1px;
}

.swatch.is-active {
  border-color: var(--zk-surface);
  outline: 2px solid var(--zk-border-strong);
  outline-offset: 1px;
}

.swatch-name {
  margin-left: 2px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.copy-link {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--zk-accent-hover);
  font-size: 11.5px;
  cursor: pointer;
  font-family: inherit;
}

.copy-link:hover {
  text-decoration: underline;
}

.code {
  margin: 0;
  padding: 11px 12px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: #fafafa;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 11px;
  line-height: 1.75;
  color: #3f3f46;
  overflow-x: auto;
  white-space: pre;
  tab-size: 2;
}

.code code {
  font-family: inherit;
}

.hint {
  font-size: 11px;
  line-height: 1.65;
  color: var(--zk-text-3);
}

.hint code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10.5px;
  background: var(--zk-surface-3);
  padding: 1px 4px;
  border-radius: var(--zk-radius-xs);
}

.copy-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px 7px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: #fafafa;
}

.copy-row code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--zk-text-2);
}

.check-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 12px;
  color: var(--zk-text-2);
}

.check-list li {
  display: flex;
  align-items: center;
  gap: 7px;
}

.check-list .el-icon {
  color: var(--zk-green);
  font-size: 13px;
}

.api-endpoint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
  font-size: 11.5px;
}

.method {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-green-soft);
  color: var(--zk-green);
  font-size: 10px;
  font-weight: 600;
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.method--post {
  background: var(--zk-accent-soft);
  color: var(--zk-accent-hover);
}

.api-endpoint code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--zk-text-2);
}

.api-desc {
  margin-left: auto;
  color: var(--zk-text-3);
  font-size: 11px;
}

/* ---------- 预览 ---------- */
.preview-card {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface-2);
  overflow: hidden;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: var(--zk-surface);
  border-bottom: 1px solid var(--zk-border);
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
}

.preview-sub {
  margin-top: 1px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.preview-actions {
  display: flex;
  gap: 6px;
}

.browser {
  margin: 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: #fff;
  overflow: hidden;
}

.browser-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 34px;
  padding: 0 12px;
  background: #fafafa;
  border-bottom: 1px solid var(--zk-border);
}

.lights {
  display: inline-flex;
  gap: 4px;
}

.lights i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #e4e4e7;
}

.browser-url {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.browser-url .el-icon {
  font-size: 10px;
}

.browser-live {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 9.5px;
  color: var(--zk-text-3);
  letter-spacing: 0.4px;
}

.browser-live i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #10b981;
}

.browser-frame {
  display: block;
  width: 100%;
  height: 588px;
  border: 0;
  background: #fbfaf7;
}

.preview-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 14px 12px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.link-btn {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--zk-accent-hover);
  font-size: 11.5px;
  cursor: pointer;
  font-family: inherit;
}

.link-btn:hover {
  text-decoration: underline;
}

/* ---------- 方式对比 ---------- */
.ways {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.way-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface);
}

.way-icon {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: grid;
  place-items: center;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-3);
  color: var(--zk-text-2);
  font-size: 14px;
}

.way-body {
  flex: 1;
  min-width: 0;
}

.way-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
}

.way-tag {
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--zk-accent-soft);
  color: var(--zk-accent-hover);
  font-size: 9.5px;
  font-weight: 500;
}

.way-desc {
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.way-scene {
  margin-top: 6px;
  font-size: 10.5px;
  color: var(--zk-text-3);
}

.way-go {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 11px;
  color: var(--zk-accent-hover);
  cursor: pointer;
  font-family: inherit;
}

.way-go:hover {
  text-decoration: underline;
}

@media (max-width: 1000px) {
  .embed-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  /* 单列时配置优先：复制代码是主任务，预览紧随其后 */
  .browser-frame {
    height: 520px;
  }
}

@media (max-width: 760px) {
  .ways {
    grid-template-columns: minmax(0, 1fr);
  }
  .field-row {
    flex-direction: column;
    gap: 12px;
  }
  .browser-frame {
    height: 460px;
  }
  .preview-head {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .status-bar {
    row-gap: 4px;
  }
  .status-divider {
    display: none;
  }
  .status-right {
    margin-left: 0;
    width: 100%;
  }
  .browser {
    margin: 10px;
  }
  .browser-frame {
    height: 420px;
  }
}
</style>
