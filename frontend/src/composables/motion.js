import { computed, onBeforeUnmount, onMounted, ref, unref, watch } from 'vue'

/**
 * 全局动效基础设施
 *
 * 这里只提供三件事，页面不重复实现：
 *   1. v-reveal 指令：元素进视口时依次浮现（只播一次，可按组错开）
 *   2. useCountUp：指标数字从 0 滚到实际值，与柱子/进度条同步
 *   3. useReducedMotion / prefersReducedMotion：统一读取系统「减少动效」设置
 *
 * 参数口径与 ui-motion 的 foundations.md 对齐：
 * 错开 30–60ms、上浮 8–40px、进场 ease-out、退场 ease-in、回弹超调 ≤8%。
 */

const REDUCED_QUERY = '(prefers-reduced-motion: reduce)'

/** 系统是否开启了「减少动效」。每次调用都读实时值，避免缓存过期。 */
export function prefersReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia(REDUCED_QUERY).matches
}

/** 响应式的「减少动效」开关：系统设置变化时组件能立刻降级。 */
export function useReducedMotion() {
  const reduced = ref(prefersReducedMotion())
  let media = null
  const onChange = (event) => {
    reduced.value = event.matches
  }

  onMounted(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    media = window.matchMedia(REDUCED_QUERY)
    reduced.value = media.matches
    if (media.addEventListener) media.addEventListener('change', onChange)
    else if (media.addListener) media.addListener(onChange)
  })

  onBeforeUnmount(() => {
    if (!media) return
    if (media.removeEventListener) media.removeEventListener('change', onChange)
    else if (media.removeListener) media.removeListener(onChange)
    media = null
  })

  return reduced
}

/**
 * 首次挂载后翻转的开关。
 * 用来让「柱子长出来」「卡片依次浮现」这类一次性的入场动画只播一次，
 * 后续数据刷新走平滑过渡（Data Morph），不再从头播。
 */
export function useEnteredOnce(delay = 0) {
  const entered = ref(false)
  let raf = 0
  let timer = 0

  onMounted(() => {
    const tick = () => {
      raf = requestAnimationFrame(() => {
        entered.value = true
      })
    }
    if (delay > 0) timer = window.setTimeout(tick, delay)
    else tick()
  })

  onBeforeUnmount(() => {
    if (raf) cancelAnimationFrame(raf)
    if (timer) window.clearTimeout(timer)
  })

  return entered
}

/* ------------------------------------------------------------------ */
/* v-reveal 指令                                                       */
/* ------------------------------------------------------------------ */

const observers = new WeakMap()

function markIn(el) {
  el.classList.add('is-in')
}

function applyReveal(el, options, index) {
  const opts = options || {}
  const order = Number.isFinite(opts.index) ? opts.index : index
  if (Number.isFinite(order) && order > 0) el.style.setProperty('--zk-i', String(order))
  if (Number.isFinite(opts.y)) el.style.setProperty('--zk-reveal-y', `${opts.y}px`)
  el.classList.add('zk-reveal')
}

function observeOne(el, opts) {
  const options = opts || {}
  // 减少动效 / 浏览器不支持：直接显示，不能把内容藏起来
  if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
    requestAnimationFrame(() => markIn(el))
    return
  }

  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        markIn(entry.target)
        if (options.once !== false) io.unobserve(entry.target)
      })
    },
    {
      threshold: Number.isFinite(options.threshold) ? options.threshold : 0.15,
      rootMargin: options.rootMargin || '0px 0px -6% 0px'
    }
  )
  io.observe(el)
  observers.set(el, io)

  // 首屏已在视口内的元素：下一帧就点亮，不等滚动（避免白屏一瞬）
  const rect = el.getBoundingClientRect()
  const vh = window.innerHeight || 0
  if (rect.top < vh * 0.96 && rect.bottom > 0) {
    requestAnimationFrame(() => markIn(el))
  }
}

/**
 * v-reveal                 单个元素进视口浮现
 * v-reveal="{ index: 2 }"  指定错开序号（也接受 :index）
 * v-reveal="{ y: 16 }"     自定义上浮距离
 * v-reveal.group           容器下的直接子元素依次浮现（间隔 --zk-stagger）
 */
export const vReveal = {
  mounted(el, binding) {
    const opts = binding.value || {}
    if (binding.modifiers && binding.modifiers.group) {
      Array.from(el.children).forEach((child, index) => {
        if (!(child instanceof HTMLElement)) return
        if (child.dataset.zkReveal === 'off') return
        applyReveal(child, opts, index)
        observeOne(child, opts)
      })
      return
    }
    applyReveal(el, opts, 0)
    observeOne(el, opts)
  },
  unmounted(el) {
    const io = observers.get(el)
    if (io) {
      io.disconnect()
      observers.delete(el)
    }
    Array.from(el.children || []).forEach((child) => {
      const childIo = observers.get(child)
      if (childIo) {
        childIo.disconnect()
        observers.delete(child)
      }
    })
  }
}

/** app.use(installMotion) —— 注册全局指令 */
export function installMotion(app) {
  app.directive('reveal', vReveal)
}

/* ------------------------------------------------------------------ */
/* useCountUp                                                          */
/* ------------------------------------------------------------------ */

const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3)

/**
 * 指标数字滚动。
 *
 * @param {import('vue').Ref<number>|import('vue').ComputedRef<number>} source 目标值
 * @param {object} options
 *   duration   首播时长（默认 700ms，与柱子生长同步）
 *   updateDuration 数据刷新时的时长（默认与 duration 相同）
 *   decimals   小数位
 *   prefix/suffix 前后缀（如 '¥'、'%'）
 *   separator  千分位（默认 true）
 *   countOnMount 首播是否从 0 开始（默认 true）
 * @returns {{ display: import('vue').ComputedRef<string>, current: import('vue').Ref<number> }}
 */
export function useCountUp(source, options = {}) {
  const {
    duration = 700,
    updateDuration = duration,
    decimals = 0,
    prefix = '',
    suffix = '',
    separator = true,
    countOnMount = true
  } = options

  const current = ref(0)
  let raf = 0

  const format = (value) => {
    const safe = Number.isFinite(value) ? value : 0
    let text = decimals > 0 ? safe.toFixed(decimals) : String(Math.round(safe))
    if (separator && decimals <= 0) {
      text = text.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    }
    return `${prefix}${text}${suffix}`
  }

  const display = computed(() => format(current.value))

  const stop = () => {
    if (raf) cancelAnimationFrame(raf)
    raf = 0
  }

  const animateTo = (to, ms) => {
    stop()
    const target = Number.isFinite(to) ? to : 0
    const from = current.value
    if (from === target) {
      current.value = target
      return
    }
    if (prefersReducedMotion() || !ms) {
      current.value = target
      return
    }
    const startedAt = performance.now()
    const step = (now) => {
      const progress = Math.min(1, (now - startedAt) / ms)
      current.value = from + (target - from) * easeOutCubic(progress)
      if (progress < 1) raf = requestAnimationFrame(step)
      else raf = 0
    }
    raf = requestAnimationFrame(step)
  }

  const read = () => Number(unref(source)) || 0

  onMounted(() => {
    if (!countOnMount) {
      current.value = read()
      return
    }
    current.value = 0
    requestAnimationFrame(() => animateTo(read(), duration))
  })

  watch(
    () => read(),
    (next) => animateTo(next, updateDuration)
  )

  onBeforeUnmount(stop)

  return { display, current }
}

/**
 * 触屏轻震（可选反馈）：只在支持且未开启减少动效时触发。
 * iOS Safari 不支持 vibrate，调用前已做存在性判断。
 */
export function haptic(ms = 12) {
  if (prefersReducedMotion()) return
  if (typeof navigator === 'undefined' || typeof navigator.vibrate !== 'function') return
  try {
    navigator.vibrate(ms)
  } catch {
    /* 忽略：震动的失败不影响主流程 */
  }
}
