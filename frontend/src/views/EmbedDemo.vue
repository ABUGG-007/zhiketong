<template>
  <div class="demo-site" :class="{ 'is-embedded': embedded }">
    <div v-if="!embedded" class="demo-flag">
      <span class="demo-flag-dot" aria-hidden="true" />
      {{ notice || '智客通网页接入演示' }}
      <router-link to="/admin/embed">返回控制台 →</router-link>
    </div>

    <header class="site-head">
      <div class="site-inner">
        <router-link to="/embed/demo" class="site-brand">
          <span class="site-mark" aria-hidden="true">云</span>
          <span class="site-name">云栖山居</span>
        </router-link>
        <nav class="site-nav" aria-label="示例站点导航">
          <a href="#rooms">客房</a>
          <a href="#story">山居</a>
          <a href="#contact">到达</a>
        </nav>
        <a class="site-cta zk-press" href="#rooms">立即预订</a>
      </div>
    </header>

    <main>
      <section class="hero">
        <div class="site-inner hero-inner">
          <div class="hero-copy">
            <p class="hero-kicker">莫干山 · 海拔 420 米</p>
            <h1>把周末，<br />交给山里。</h1>
            <p class="hero-desc">
              十二间客房散落在竹海与溪谷之间。没有电视，只有整面山的窗。含双早与手冲咖啡。
            </p>
            <div class="hero-actions">
              <a class="btn btn--dark zk-press" href="#rooms">查看房型</a>
              <a class="btn btn--ghost zk-press" href="#story">了解山居</a>
            </div>
            <dl class="hero-facts">
              <div><dt>12</dt><dd>间山景客房</dd></div>
              <div><dt>4.9</dt><dd>住客评分</dd></div>
              <div><dt>30s</dt><dd>客服平均响应</dd></div>
            </dl>
          </div>

          <div class="hero-art" aria-hidden="true">
            <svg viewBox="0 0 520 420" role="presentation">
              <rect width="520" height="420" rx="16" fill="#eef0ea" />
              <circle cx="382" cy="96" r="34" fill="#f6e7c8" />
              <path d="M0 300 L132 176 L240 268 L322 196 L520 330 L520 420 L0 420 Z" fill="#cfd8cb" />
              <path d="M0 338 L110 250 L206 320 L300 246 L520 362 L520 420 L0 420 Z" fill="#a9b9a8" />
              <path d="M0 372 L150 312 L286 366 L420 318 L520 372 L520 420 L0 420 Z" fill="#7e9381" />
              <g stroke="#6f8573" stroke-width="2" stroke-linecap="round" opacity="0.55">
                <path d="M96 340 v-46" />
                <path d="M96 320 l-16 -18" />
                <path d="M96 312 l18 -20" />
                <path d="M418 352 v-40" />
                <path d="M418 334 l-14 -16" />
                <path d="M418 328 l16 -18" />
              </g>
            </svg>
            <span class="hero-tag">竹海景观 · 含双早</span>
          </div>
        </div>
      </section>

      <section id="rooms" class="rooms">
        <div class="site-inner">
          <div class="section-head">
            <div>
              <p class="section-kicker">房型</p>
              <h2>三间值得早起的房间</h2>
            </div>
            <span class="section-note">价格含双早，周五至周日 +120 元</span>
          </div>

          <div class="room-grid" v-reveal.group>
            <article v-for="room in rooms" :key="room.name" class="room-card">
              <div class="room-cover" :style="{ background: room.tint }">
                <span class="room-window" aria-hidden="true" />
                <span class="room-badge">{{ room.badge }}</span>
              </div>
              <div class="room-body">
                <div class="room-top">
                  <h3>{{ room.name }}</h3>
                  <span class="room-price"><b>¥{{ room.price }}</b> / 晚</span>
                </div>
                <p>{{ room.desc }}</p>
                <div class="room-foot">
                  <span>{{ room.size }} · {{ room.bed }}</span>
                  <a href="#contact" class="zk-press">查看详情</a>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section id="story" class="story">
        <div class="site-inner story-inner">
          <blockquote>
            “我们保留了老屋的木梁，<br />把剩下的交给山。”
          </blockquote>
          <p>
            云栖山居由一栋 1980 年代的林场宿舍改造。施工时留下的老木头被做成前台与书架，
            院子里那棵百年银杏，是每天早餐最好的背景。
          </p>
        </div>
      </section>
    </main>

    <footer id="contact" class="site-foot">
      <div class="site-inner foot-inner">
        <div>
          <div class="foot-name">云栖山居</div>
          <div class="foot-meta">浙江省湖州市德清县莫干山镇 · 预订后发送详细路线</div>
        </div>
        <div class="foot-note">
          本页面为智客通接入演示站点，右下角客服由
          <span>智客通悬浮球</span>
          提供。
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const embedded = computed(() => route.query.embed === '1')
const notice = ref('')

const rooms = [
  { name: '听竹 · 大床房', price: 680, badge: '最受欢迎', size: '38㎡', bed: '1.8m 大床', desc: '正对竹海，落地窗与独立浴缸，适合两个人的周末。', tint: '#e7ece4' },
  { name: '望山 · 双床房', price: 780, badge: '亲子友好', size: '45㎡', bed: '1.35m 双床', desc: '带 6 米山景阳台，可加儿童床，含两份手作早餐。', tint: '#eae6dc' },
  { name: '栖云 · 套房', price: 1280, badge: '仅 2 间', size: '62㎡', bed: '2.0m 大床', desc: '独立起居室与壁炉，转角窗能看到整片山谷。', tint: '#e3e8ea' }
]

let scriptEl = null

/**
 * 演示站点：服务端配置为准，链接参数仅用于控制台未保存前的实时预览。
 */
function mountWidget() {
  const api = window.ZhiketongWidget
  if (!api) return
  const q = route.query
  const appKey = typeof q.appKey === 'string' ? q.appKey.trim() : ''
  if (!appKey) {
    notice.value = '缺少 appKey，请从控制台「接入方式」重新打开演示站点'
    return
  }
  const options = { appKey }
  if (typeof q.color === 'string' && /^#[0-9a-fA-F]{6}$/.test(q.color)) options.color = q.color
  if (q.position === 'left' || q.position === 'right') options.position = q.position
  if (typeof q.brand === 'string' && q.brand) options.brand = q.brand
  if (typeof q.welcome === 'string' && q.welcome) options.welcome = q.welcome
  if (q.badge === '0' || q.badge === '1') options.badge = q.badge === '1'
  api.init(options)
}

onMounted(() => {
  if (window.ZhiketongWidget) {
    mountWidget()
    return
  }
  scriptEl = document.createElement('script')
  scriptEl.src = '/embed/zhiketong-widget.js'
  scriptEl.async = true
  scriptEl.setAttribute('data-auto-init', 'false')
  scriptEl.addEventListener('load', mountWidget)
  document.body.appendChild(scriptEl)
})

onBeforeUnmount(() => {
  if (window.ZhiketongWidget) window.ZhiketongWidget.destroy()
  if (scriptEl && scriptEl.parentNode) scriptEl.parentNode.removeChild(scriptEl)
  scriptEl = null
})
</script>

<style scoped>
.demo-site {
  --site-ink: #1d211c;
  --site-muted: #6d746a;
  --site-line: #e3e4df;
  --site-bg: #fbfaf7;
  min-height: 100vh;
  background: var(--site-bg);
  color: var(--site-ink);
  font-family: 'Inter', 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}

.is-embedded {
  min-height: 100%;
}

.demo-flag {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 20px;
  background: #17181c;
  color: #d4d4d8;
  font-size: 11.5px;
}

.demo-flag a {
  margin-left: auto;
  color: #fff;
}

.demo-flag-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #4ade80;
}

.site-inner {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 28px;
}

.site-head {
  position: sticky;
  top: 0;
  z-index: 5;
  background: rgba(251, 250, 247, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--site-line);
}

.is-embedded .site-head {
  position: static;
}

.site-head .site-inner {
  height: 62px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.site-brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.site-mark {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #2f6f5f;
  color: #fff;
  font-size: 12px;
  display: grid;
  place-items: center;
}

.site-name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
}

.site-nav {
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 13px;
  color: var(--site-muted);
}

.site-nav a:hover {
  color: var(--site-ink);
}

.site-cta {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: #2f6f5f;
  color: #fff;
  font-size: 12.5px;
}

.hero {
  padding: 56px 0 48px;
  border-bottom: 1px solid var(--site-line);
}

.hero-inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.02fr);
  gap: 48px;
  align-items: center;
}

.hero-kicker {
  font-size: 11.5px;
  letter-spacing: 2px;
  color: var(--site-muted);
  margin-bottom: 14px;
}

.hero h1 {
  font-family: 'Songti SC', 'Noto Serif SC', Georgia, serif;
  font-size: 46px;
  line-height: 1.24;
  font-weight: 600;
  letter-spacing: 2px;
}

.hero-desc {
  margin-top: 16px;
  max-width: 400px;
  font-size: 13px;
  line-height: 1.85;
  color: var(--site-muted);
}

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}

.btn {
  display: inline-flex;
  align-items: center;
  height: 38px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 13px;
}

.btn--dark {
  background: #23281f;
  color: #fff;
}

.btn--dark:hover {
  background: #2f362a;
}

.btn--ghost {
  border: 1px solid #cfd2c8;
  color: #3d4239;
}

.btn--ghost:hover {
  background: #f2f1ec;
}

.hero-facts {
  display: flex;
  gap: 32px;
  margin: 30px 0 0;
}

.hero-facts dt {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.hero-facts dd {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--site-muted);
}

.hero-art {
  position: relative;
}

.hero-art svg {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 16px;
}

.hero-tag {
  position: absolute;
  left: 16px;
  bottom: 16px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  font-size: 11px;
  color: #3d4239;
}

.rooms {
  padding: 44px 0 52px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.section-kicker {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--site-muted);
  margin-bottom: 6px;
}

.section-head h2 {
  font-family: 'Songti SC', 'Noto Serif SC', Georgia, serif;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 1px;
}

.section-note {
  font-size: 11.5px;
  color: var(--site-muted);
}

.room-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.room-card {
  border: 1px solid var(--site-line);
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
}

.room-cover {
  position: relative;
  height: 132px;
}

.room-window {
  position: absolute;
  left: 22px;
  top: 20px;
  width: 52%;
  height: 62%;
  border: 1px solid rgba(29, 33, 28, 0.16);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.42);
}

/* 窗框：让色块读起来是“山的窗”，而不是缺失的图片 */
.room-window::before,
.room-window::after {
  content: '';
  position: absolute;
  background: rgba(29, 33, 28, 0.12);
}

.room-window::before {
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1px;
}

.room-window::after {
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
}

.room-badge {
  position: absolute;
  left: 12px;
  bottom: 12px;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  font-size: 10px;
  color: #3d4239;
}

.room-body {
  padding: 14px 16px 16px;
}

.room-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.room-top h3 {
  font-size: 14px;
  font-weight: 600;
}

.room-price {
  font-size: 11px;
  color: var(--site-muted);
  white-space: nowrap;
}

.room-price b {
  font-size: 15px;
  color: var(--site-ink);
  font-variant-numeric: tabular-nums;
}

.room-body p {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--site-muted);
}

.room-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--site-line);
  font-size: 11px;
  color: var(--site-muted);
}

.room-foot a {
  color: #2f6f5f;
}

.story {
  padding: 52px 0;
  background: #f1f0ea;
  border-top: 1px solid var(--site-line);
  border-bottom: 1px solid var(--site-line);
}

.story-inner {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 40px;
  align-items: center;
}

.story blockquote {
  margin: 0;
  font-family: 'Songti SC', 'Noto Serif SC', Georgia, serif;
  font-size: 26px;
  line-height: 1.55;
  letter-spacing: 1px;
}

.story p {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--site-muted);
}

.site-foot {
  padding: 30px 0 36px;
}

.foot-inner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.foot-name {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}

.foot-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--site-muted);
}

.foot-note {
  max-width: 300px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--site-muted);
  text-align: right;
}

.foot-note span {
  color: #2f6f5f;
}

@media (max-width: 900px) {
  .site-nav {
    display: none;
  }
  .hero-inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 28px;
  }
  .hero h1 {
    font-size: 38px;
  }
  .hero-art {
    order: -1;
  }
  .room-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .story-inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  .story blockquote {
    font-size: 22px;
  }
  .foot-inner {
    flex-direction: column;
    align-items: flex-start;
  }
  .foot-note {
    text-align: left;
  }
}

@media (max-width: 520px) {
  .site-inner {
    padding: 0 18px;
  }
  .hero {
    padding: 32px 0 34px;
  }
  .hero h1 {
    font-size: 32px;
  }
  .hero-facts {
    gap: 22px;
  }
  .demo-flag {
    padding: 0 14px;
  }
}
</style>
