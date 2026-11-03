/**
 * 界面截图采集（零依赖，Chrome DevTools Protocol）
 *
 * 用途：为 README/文档生成真实界面截图，避免手工截图与实现不一致。
 * 用法：node scripts/capture-screenshots.mjs [输出目录]
 * 环境变量：CHROME_PATH（默认取 Chrome 安装路径）、APP_BASE（默认 http://127.0.0.1:5173）、CDP_PORT
 *
 * 前置：前端（serve-frontend.mjs，5173）与后端（8080）均已启动，且数据库已初始化。
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'

const APP = process.env.APP_BASE || 'http://127.0.0.1:5173'
const API = process.env.API_BASE || 'http://127.0.0.1:8080'
const CHROME =
  process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const CDP_PORT = Number(process.env.CDP_PORT || 9333)
const OUT = path.resolve(process.argv[2] || 'docs/screenshots/readme')
// 可选：node scripts/capture-screenshots.mjs <目录> --only 01-login.png,02-admin-dashboard.png
const onlyArg = process.argv.indexOf('--only')
const ONLY = onlyArg > 0 ? String(process.argv[onlyArg + 1] || '').split(',').filter(Boolean) : []
// 可选：--viewport 1750x963 覆盖默认桌面视口，便于校验不同分辨率下的版式
const viewportArg = process.argv.indexOf('--viewport')
const VIEWPORT_OVERRIDE = (() => {
  if (viewportArg < 0) return null
  const [width, height] = String(process.argv[viewportArg + 1] || '').split('x').map(Number)
  if (!width || !height) return null
  return { width, height, mobile: false }
})()

const DESKTOP = VIEWPORT_OVERRIDE || { width: 1440, height: 900, mobile: false }
const MOBILE = { width: 390, height: 844, mobile: true }

/** 截图清单：role 为空表示未登录页面 */
const SHOTS = [
  { file: '01-login.png', path: '/login', viewport: DESKTOP, wait: 1600 },
  { file: '02-admin-dashboard.png', role: 'admin', path: '/admin/dashboard', wait: 2200 },
  { file: '03-admin-sessions.png', role: 'admin', path: '/admin/sessions', wait: 1800 },
  { file: '04-admin-knowledge.png', role: 'admin', path: '/admin/knowledge', wait: 1800 },
  { file: '05-admin-orders.png', role: 'admin', path: '/admin/orders', wait: 1800 },
  { file: '06-admin-quality.png', role: 'admin', path: '/admin/quality', wait: 2000 },
  { file: '07-admin-screen.png', role: 'admin', path: '/admin/screen', wait: 2600 },
  { file: '08-admin-voc.png', role: 'admin', path: '/admin/voc', wait: 2200 },
  { file: '09-admin-embed.png', role: 'admin', path: '/admin/embed', wait: 1800 },
  { file: '10-platform-tenants.png', role: 'platform01', path: '/platform/tenants', wait: 2000 },
  { file: '11-agent-workbench.png', role: 'agent01', path: '/agent/workbench', wait: 2200 },
  { file: '12-user-home.png', role: 'user01', path: '/user/home', wait: 2000 },
  { file: '13-embed-widget.png', path: '/embed/demo', wait: 2400 },
  {
    file: '14-mobile-dashboard.png',
    role: 'admin',
    path: '/admin/dashboard',
    viewport: MOBILE,
    wait: 2200
  }
]

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function loginToken(username, password = '123456') {
  const response = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ username, password })
  })
  const body = await response.json()
  if (!body || body.code !== 200 || !body.data?.token) {
    throw new Error(`登录失败：${username} -> ${JSON.stringify(body).slice(0, 200)}`)
  }
  return body.data
}

function connect(browserUrl) {
  const pending = new Map()
  let nextId = 1
  const socket = new WebSocket(browserUrl)
  const opened = new Promise((resolve, reject) => {
    socket.addEventListener('open', () => resolve())
    socket.addEventListener('error', (event) => reject(new Error(`CDP 连接失败：${event.message || ''}`)))
  })
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) reject(new Error(message.error.message))
      else resolve(message.result)
    }
  })
  const send = (method, params = {}, sessionId) =>
    new Promise((resolve, reject) => {
      const id = nextId++
      pending.set(id, { resolve, reject })
      socket.send(JSON.stringify({ id, method, params, sessionId }))
    })
  return { socket, send, opened }
}

async function waitForChrome() {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${CDP_PORT}/json/version`)
      if (response.ok) return await response.json()
    } catch {
      /* 端口尚未监听 */
    }
    await sleep(150)
  }
  throw new Error('Chrome 调试端口未就绪')
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const auth = {}
  for (const name of ['admin', 'agent01', 'user01', 'platform01']) {
    auth[name] = await loginToken(name)
  }

  // 悬浮球演示页需要带 appKey，从管理端接入配置读取，避免写死租户数据
  try {
    const configResponse = await fetch(`${API}/api/admin/embed/config`, {
      headers: { Authorization: `Bearer ${auth.admin.token}` }
    })
    const configBody = await configResponse.json()
    const appKey = configBody?.data?.appKey
    if (appKey) {
      const shot = SHOTS.find((item) => item.file === '13-embed-widget.png')
      if (shot) shot.path = `/embed/demo?appKey=${appKey}`
    }
  } catch (error) {
    console.warn('[shot] 读取接入配置失败，悬浮球演示页可能缺少 appKey：', error.message)
  }

  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'zkt-shot-'))
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      `--remote-debugging-port=${CDP_PORT}`,
      `--user-data-dir=${profile}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--hide-scrollbars',
      '--disable-gpu',
      '--disable-extensions',
      '--disable-background-networking',
      '--force-device-scale-factor=1',
      'about:blank'
    ],
    { stdio: 'ignore', windowsHide: true }
  )

  try {
    const version = await waitForChrome()
    const { socket, send, opened } = connect(version.webSocketDebuggerUrl)
    await opened

    const { targetId } = await send('Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true })
    const call = (method, params) => send(method, params, sessionId)

    await call('Page.enable')
    await call('Runtime.enable')

    const evaluate = async (expression) => {
      const result = await call('Runtime.evaluate', { expression, awaitPromise: true })
      return result?.result?.value
    }

    const waitReady = async () => {
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const state = await evaluate('document.readyState')
        if (state === 'complete') return
        await sleep(120)
      }
    }

    // 先落在同源页面，便于写 localStorage
    await call('Emulation.setDeviceMetricsOverride', { ...DESKTOP, deviceScaleFactor: 1 })
    await call('Page.navigate', { url: `${APP}/login` })
    await waitReady()
    await sleep(500)

    const results = []
    const shots = ONLY.length ? SHOTS.filter((shot) => ONLY.includes(shot.file)) : SHOTS
    for (const shot of shots) {
      const viewport = shot.viewport || DESKTOP
      await call('Emulation.setDeviceMetricsOverride', {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: viewport.mobile
      })

      if (shot.role) {
        const data = auth[shot.role]
        await evaluate(
          `localStorage.setItem('zkt_token', ${JSON.stringify(data.token)});` +
            `localStorage.setItem('zkt_user', ${JSON.stringify(JSON.stringify(data.user))});` +
            `void 0`
        )
      } else {
        await evaluate('localStorage.clear(); void 0')
      }

      await call('Page.navigate', { url: `${APP}${shot.path}` })
      await waitReady()
      await sleep(shot.wait || 1500)

      const { data } = await call('Page.captureScreenshot', {
        format: 'png',
        fromSurface: true,
        captureBeyondViewport: false
      })
      const bytes = Buffer.from(data, 'base64')
      const target = path.join(OUT, shot.file)
      fs.writeFileSync(target, bytes)
      const currentUrl = await evaluate('location.pathname')
      results.push({ file: shot.file, bytes: bytes.length, url: currentUrl })
      console.log(
        `[shot] ${shot.file.padEnd(28)} ${String(bytes.length).padStart(7)} B  ${currentUrl}`
      )
    }

    await send('Target.closeTarget', { targetId })
    socket.close()

    const wrong = results.filter((item) => {
      const shot = SHOTS.find((entry) => entry.file === item.file)
      const expectedPath = shot ? shot.path.split('?')[0] : ''
      return shot && expectedPath !== item.url
    })
    if (wrong.length) {
      console.warn('[shot] 注意：以下页面发生了重定向（可能被权限拦截）：')
      for (const item of wrong) console.warn(`       ${item.file} -> ${item.url}`)
    }
    console.log(`[shot] 共生成 ${results.length} 张截图，目录：${OUT}`)
  } finally {
    chrome.kill('SIGKILL')
    await sleep(300)
    fs.rmSync(profile, { recursive: true, force: true })
  }
}

main().catch((error) => {
  console.error('[shot] 采集失败：', error.message)
  process.exit(1)
})
