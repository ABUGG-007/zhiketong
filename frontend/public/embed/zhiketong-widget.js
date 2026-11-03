/*!
 * 智客通 · 网页客服悬浮球 v1.1
 * 零依赖，单个 <script> 即可接入。加载后自动在页面右下角挂载悬浮球，
 * 点击展开由 /embed/chat 提供的对话面板（iframe 隔离样式与脚本）。
 *
 *   <script src="http://127.0.0.1:5173/embed/zhiketong-widget.js"
 *           data-app-key="zk_demo_5f2a1c9e" defer></script>
 *
 * 主题色、位置、客服名称、欢迎语默认读取服务端接入配置（控制台可改，刷新不丢失）；
 * 确需在单个页面临时覆盖时，再传对应的 data 属性。SDK 会用 appKey 向平台换取
 * 短时令牌，appSecret 永远不出现在浏览器，因此仅知道脚本地址无法调用问答接口。
 *
 * 支持的 data 属性：
 *   data-app-key   接入 Key（必填，控制台「API 对接」中查看）
 *   data-color     临时覆盖主题色，如 #2f6f5f
 *   data-position  right | left，临时覆盖悬浮位置
 *   data-brand     临时覆盖客服名称
 *   data-welcome   临时覆盖欢迎语
 *   data-badge     1 显示未读红点，0 不显示
 *   data-auto-init false 时不自动挂载，改为手动调用 init()
 *
 * 手动 API：window.ZhiketongWidget.init(options) / open() / close() / toggle() / destroy()
 */
(function () {
  'use strict'

  if (window.ZhiketongWidget && window.ZhiketongWidget.__zkt) {
    return
  }

  var current = document.currentScript
  if (!current) {
    var scripts = document.getElementsByTagName('script')
    current = scripts[scripts.length - 1]
  }

  var origin = window.location.origin
  try {
    if (current && current.src) {
      origin = new URL(current.src, window.location.href).origin
    }
  } catch (error) {
    origin = window.location.origin
  }

  var STYLE_ID = 'zkt-widget-style'
  var ROOT_ID = 'zkt-widget-root'
  var DEFAULT_COLOR = '#4f46e5'
  var DEFAULT_BRAND = '智客通客服'
  var instance = null

  var CSS = [
    '#zkt-widget-root{position:fixed;right:20px;bottom:20px;z-index:2147483000;',
    'font-family:Inter,"HarmonyOS Sans SC","PingFang SC","Microsoft YaHei",system-ui,-apple-system,sans-serif;',
    '--zkt-color:#4f46e5;--zkt-dur-base:200ms;--zkt-dur-panel:280ms;',
    '--zkt-ease-out:cubic-bezier(0,0,.2,1);--zkt-ease-in:cubic-bezier(.4,0,1,1);',
    '--zkt-ease-panel:cubic-bezier(.34,1.28,.64,1);line-height:1}',
    '#zkt-widget-root.zkt-left{right:auto;left:20px}',
    '.zkt-bubble{position:relative;display:grid;place-items:center;width:54px;height:54px;border:0;border-radius:50%;',
    'background:var(--zkt-color);color:#fff;cursor:pointer;padding:0;',
    'box-shadow:0 10px 24px -8px rgba(16,18,24,.36),0 2px 6px rgba(16,18,24,.12);',
    'transition:transform 180ms var(--zkt-ease-out),box-shadow 180ms var(--zkt-ease-out)}',
    '.zkt-bubble:hover{transform:translateY(-2px) scale(1.03);box-shadow:0 14px 30px -10px rgba(16,18,24,.4),0 2px 6px rgba(16,18,24,.12)}',
    '.zkt-bubble:active{transform:scale(.96);transition-duration:90ms}',
    '.zkt-bubble:focus-visible{outline:2px solid #fff;outline-offset:2px;box-shadow:0 0 0 4px var(--zkt-color)}',
    '.zkt-bubble svg{width:24px;height:24px;display:block}',
    /* 未读角标：来消息时像翻牌一样翻进来，清零时缩小淡出 */
    '.zkt-dot{position:absolute;top:3px;right:3px;width:9px;height:9px;border-radius:50%;background:#f43f5e;border:2px solid #fff;',
    'animation:zkt-dot-in 320ms cubic-bezier(.34,1.4,.64,1) both}',
    '.zkt-dot.is-out{animation:zkt-dot-out 220ms var(--zkt-ease-in) both}',
    '@keyframes zkt-dot-in{0%{opacity:0;transform:scale(.4) rotate(-28deg)}',
    '60%{opacity:1;transform:scale(1.16) rotate(5deg)}100%{opacity:1;transform:scale(1) rotate(0)}}',
    '@keyframes zkt-dot-out{from{opacity:1;transform:scale(1)}to{opacity:0;transform:scale(0)}}',
    '.zkt-panel{position:absolute;right:0;bottom:70px;width:380px;height:600px;max-height:calc(100vh - 108px);',
    'background:#fff;border-radius:14px;overflow:hidden;',
    'box-shadow:0 28px 70px -24px rgba(16,18,24,.4),0 0 0 1px rgba(16,18,24,.06);',
    'opacity:0;visibility:hidden;transform:translateY(10px) scale(.94);transform-origin:bottom right;',
    'transition:opacity var(--zkt-dur-base) var(--zkt-ease-in),transform var(--zkt-dur-base) var(--zkt-ease-in),',
    'visibility var(--zkt-dur-base)}',
    '.zkt-left .zkt-panel{right:auto;left:0;transform-origin:bottom left}',
    /* 展开：从悬浮球位置长出来，280ms 轻微回弹；收起按原路回去 */
    '.zkt-panel.is-open{opacity:1;visibility:visible;transform:none;',
    'transition:opacity 220ms var(--zkt-ease-out),transform var(--zkt-dur-panel) var(--zkt-ease-panel),visibility 0s}',
    '.zkt-panel iframe{display:block;width:100%;height:100%;border:0;background:#fff}',
    '@media (max-width:520px){#zkt-widget-root{right:14px;bottom:14px}#zkt-widget-root.zkt-left{left:14px}',
    '.zkt-panel{position:fixed;left:10px;right:10px;bottom:82px;width:auto;height:calc(100vh - 104px);',
    'height:calc(100dvh - 104px);max-height:none;border-radius:16px}}',
    '@media (prefers-reduced-motion:reduce){.zkt-bubble,.zkt-panel{transition:none}',
    '.zkt-dot,.zkt-dot.is-out{animation:none}}'
  ].join('')

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return
    var style = document.createElement('style')
    style.id = STYLE_ID
    style.textContent = CSS
    document.head.appendChild(style)
  }

  function isHexColor(value) {
    return /^#[0-9a-fA-F]{6}$/.test(String(value || ''))
  }

  /**
   * 规整初始化参数：显式传入的值视为页面级覆盖，其余留给服务端配置填充。
   */
  function normalize(options) {
    var opts = options || {}
    return {
      appKey: String(opts.appKey || '').trim(),
      color: isHexColor(opts.color) ? opts.color : DEFAULT_COLOR,
      position: opts.position === 'left' ? 'left' : 'right',
      brand: opts.brand || DEFAULT_BRAND,
      welcome: typeof opts.welcome === 'string' ? opts.welcome : '',
      badge: opts.badge === true || opts.badge === '1' || opts.badge === 1,
      explicitColor: isHexColor(opts.color),
      explicitPosition: opts.position === 'left' || opts.position === 'right',
      explicitBrand: typeof opts.brand === 'string' && opts.brand !== '',
      explicitWelcome: typeof opts.welcome === 'string' && opts.welcome !== '',
      explicitBadge: opts.badge !== undefined && opts.badge !== null,
      token: ''
    }
  }

  /**
   * 用服务端配置补全未显式覆盖的字段。
   */
  function mergeConfig(opts, config) {
    if (!config) return
    if (!opts.explicitColor && isHexColor(config.themeColor)) opts.color = config.themeColor
    if (!opts.explicitPosition && (config.position === 'left' || config.position === 'right')) {
      opts.position = config.position
    }
    if (!opts.explicitBrand && config.brandName) opts.brand = config.brandName
    if (!opts.explicitWelcome && typeof config.welcome === 'string') opts.welcome = config.welcome
    if (!opts.explicitBadge && typeof config.badge === 'boolean') opts.badge = config.badge
  }

  function chatUrl(opts) {
    var params = []
    function push(key, value) {
      if (value !== undefined && value !== null && value !== '') {
        params.push(key + '=' + encodeURIComponent(value))
      }
    }
    push('widget', '1')
    push('appKey', opts.appKey)
    push('token', opts.token)
    push('brand', opts.brand)
    push('welcome', opts.welcome)
    push('color', opts.color)
    return origin + '/embed/chat?' + params.join('&')
  }

  /**
   * 用 appKey 换取短时令牌，同时拿到服务端保存的外观配置。
   * 失败时不阻塞悬浮球展示，对话页会再次尝试并给出明确提示。
   */
  function loadRemote() {
    var target = instance
    var appKey = target && target.opts.appKey
    if (!appKey) {
      console.warn('[ZhiketongWidget] 缺少 data-app-key，无法加载服务端接入配置')
      return
    }
    fetch(origin + '/api/public/embed/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ appKey: appKey })
    })
      .then(function (response) {
        return response.json()
      })
      .then(function (payload) {
        // 实例已被 destroy / 重新 init 时丢弃旧响应
        if (!instance || instance !== target) return
        if (!payload || payload.code !== 200 || !payload.data) {
          throw new Error((payload && payload.message) || '接入配置加载失败')
        }
        var data = payload.data
        target.opts.token = data.token || ''
        mergeConfig(target.opts, data.config)
        applyOptions()
      })
      .catch(function (error) {
        console.warn('[ZhiketongWidget] 接入配置加载失败：' + (error && error.message))
      })
  }

  /**
   * 把当前参数渲染到已挂载的 DOM 上（服务端配置返回后需要二次刷新）。
   */
  function applyOptions() {
    if (!instance) return
    var opts = instance.opts
    instance.root.style.setProperty('--zkt-color', opts.color)
    instance.root.className = opts.position === 'left' ? 'zkt-left' : ''
    instance.panel.setAttribute('aria-label', opts.brand + '在线客服')
    instance.frame.title = opts.brand + '在线客服'
    instance.frame.setAttribute('data-src', chatUrl(opts))

    var dot = instance.bubble.querySelector('.zkt-dot')
    // 面板已经打开时不再挂未读角标，避免聊天中途冒出红点
    if (opts.badge && !dot && !instance.open) {
      dot = document.createElement('span')
      dot.className = 'zkt-dot'
      dot.setAttribute('aria-hidden', 'true')
      instance.bubble.appendChild(dot)
    } else if (!opts.badge && dot) {
      removeDot(dot)
    }
  }

  /** 角标清零：先缩小淡出 220ms 再移除，不瞬间消失 */
  function removeDot(dot) {
    if (!dot || dot.classList.contains('is-out')) return
    dot.classList.add('is-out')
    window.setTimeout(function () {
      if (dot.parentNode) dot.parentNode.removeChild(dot)
    }, 220)
  }

  function create(options) {
    var opts = normalize(options)
    injectStyle()
    destroy()

    var root = document.createElement('div')
    root.id = ROOT_ID
    if (opts.position === 'left') root.className = 'zkt-left'
    root.style.setProperty('--zkt-color', opts.color)

    var panel = document.createElement('div')
    panel.className = 'zkt-panel'
    panel.id = 'zkt-widget-panel'
    panel.setAttribute('role', 'dialog')
    panel.setAttribute('aria-label', opts.brand + '在线客服')
    panel.setAttribute('aria-hidden', 'true')

    var frame = document.createElement('iframe')
    frame.title = opts.brand + '在线客服'
    frame.setAttribute('allow', 'clipboard-write')
    // 懒加载：首次展开时才请求对话页，避免影响商家页面首屏
    frame.setAttribute('data-src', chatUrl(opts))
    panel.appendChild(frame)

    var bubble = document.createElement('button')
    bubble.type = 'button'
    bubble.className = 'zkt-bubble'
    bubble.setAttribute('aria-label', '打开在线客服')
    bubble.setAttribute('aria-expanded', 'false')
    bubble.setAttribute('aria-controls', 'zkt-widget-panel')
    bubble.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M21 11.5a8.4 8.4 0 0 1-8.5 8.5 8.9 8.9 0 0 1-3.8-.8L3 21l1.9-5.4A8.4 8.4 0 0 1 4 11.5 ' +
      '8.4 8.4 0 0 1 12.5 3 8.4 8.4 0 0 1 21 11.5Z"/></svg>'

    if (opts.badge) {
      var dot = document.createElement('span')
      dot.className = 'zkt-dot'
      dot.setAttribute('aria-hidden', 'true')
      bubble.appendChild(dot)
    }

    root.appendChild(panel)
    root.appendChild(bubble)
    document.body.appendChild(root)

    instance = {
      root: root,
      panel: panel,
      frame: frame,
      bubble: bubble,
      opts: opts,
      open: false,
      onKey: null
    }

    bubble.addEventListener('click', function () {
      api.toggle()
    })

    instance.onKey = function (event) {
      if (event.key === 'Escape' && instance && instance.open) {
        api.close()
        bubble.focus()
      }
    }
    document.addEventListener('keydown', instance.onKey)

    window.addEventListener('message', onMessage)
    loadRemote()
    return instance
  }

  function onMessage(event) {
    if (!instance || event.source !== instance.frame.contentWindow) return
    var data = event.data
    if (data && data.type === 'zkt:close') {
      api.close()
    }
  }

  function open() {
    if (!instance) return
    var frame = instance.frame
    if (!frame.getAttribute('src')) {
      frame.setAttribute('src', frame.getAttribute('data-src'))
    }
    var dot = instance.bubble.querySelector('.zkt-dot')
    // 打开即视为已读：角标先缩小淡出
    if (dot) removeDot(dot)
    instance.panel.classList.add('is-open')
    instance.panel.setAttribute('aria-hidden', 'false')
    instance.bubble.setAttribute('aria-expanded', 'true')
    instance.bubble.setAttribute('aria-label', '关闭在线客服')
    instance.open = true
  }

  function close() {
    if (!instance) return
    instance.panel.classList.remove('is-open')
    instance.panel.setAttribute('aria-hidden', 'true')
    instance.bubble.setAttribute('aria-expanded', 'false')
    instance.bubble.setAttribute('aria-label', '打开在线客服')
    instance.open = false
  }

  function destroy() {
    if (!instance) return
    document.removeEventListener('keydown', instance.onKey)
    window.removeEventListener('message', onMessage)
    if (instance.root && instance.root.parentNode) {
      instance.root.parentNode.removeChild(instance.root)
    }
    instance = null
  }

  var api = {
    __zkt: true,
    init: function (options) {
      create(options)
      return api
    },
    open: function () {
      open()
      return api
    },
    close: function () {
      close()
      return api
    },
    toggle: function () {
      if (!instance) return api
      if (instance.open) close()
      else open()
      return api
    },
    destroy: function () {
      destroy()
      return api
    },
    get isOpen() {
      return Boolean(instance && instance.open)
    }
  }

  window.ZhiketongWidget = api

  function autoInit() {
    if (!current || current.getAttribute('data-auto-init') === 'false') return
    var d = current.dataset || {}
    api.init({
      appKey: d.appKey,
      color: d.color,
      position: d.position,
      brand: d.brand,
      welcome: d.welcome,
      badge: d.badge
    })
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', autoInit)
  } else {
    autoInit()
  }
})()
