/**
 * 智客通前端静态服务（零依赖）
 *
 * 背景：本机沙箱禁止 Node 以管道 stdio 启动子进程，Vite 的 esbuild 服务进程会被拦截，
 * 因此前端先由纯 JS 构建脚本产出 frontend/dist，这里用 Node 原生 http 模块托管，
 * 并把 /api、/ws 代理到 Spring Boot。
 *
 * 启动：node serve-frontend.mjs
 * 访问：http://127.0.0.1:5173
 *
 * 性能处理：
 *   - 静态资源启动时预热 brotli（q9）与 gzip（q9），按 Accept-Encoding 择优返回；
 *   - 带 hash 的 assets 返回一年 immutable 缓存，index.html 走 no-cache + ETag(304)；
 *   - 到后端的 HTTP 代理复用 keep-alive 连接池，避免每次请求重建 TCP。
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DIST = path.join(__dirname, 'frontend', 'dist')
const BACKEND_HOST = '127.0.0.1'
const BACKEND_PORT = Number(process.env.BACKEND_PORT || 8080)
const PORT = Number(process.env.FRONTEND_PORT || 5173)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8'
}

const COMPRESSIBLE = new Set(['.html', '.js', '.mjs', '.css', '.json', '.svg', '.txt', '.map'])
const assetCache = new Map()
const proxyAgent = new http.Agent({ keepAlive: true, keepAliveMsecs: 15000, maxSockets: 64 })

if (!fs.existsSync(path.join(DIST, 'index.html'))) {
  console.error('[前端] 未找到 frontend/dist/index.html，请先在 frontend 目录执行构建：')
  console.error('       node scripts/build.mjs')
  process.exit(1)
}

function proxyHttp(req, res) {
  const proxyReq = http.request(
    {
      host: BACKEND_HOST,
      port: BACKEND_PORT,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: `${BACKEND_HOST}:${BACKEND_PORT}` },
      agent: proxyAgent
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
      proxyRes.pipe(res)
    }
  )
  proxyReq.setTimeout(30000, () => proxyReq.destroy(new Error('backend timeout')))
  proxyReq.on('error', (error) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    }
    res.end(JSON.stringify({ code: 502, message: '后端服务不可用：' + error.message }))
  })
  req.pipe(proxyReq)
}

function cacheControlFor(file) {
  const rel = path.relative(DIST, file).split(path.sep).join('/')
  if (rel.startsWith('assets/')) return 'public, max-age=31536000, immutable'
  if (rel.endsWith('.html')) return 'no-cache'
  return 'public, max-age=3600'
}

function loadAsset(file) {
  const stat = fs.statSync(file)
  const ext = path.extname(file).toLowerCase()
  const cached = assetCache.get(file)
  if (cached && cached.mtimeMs === stat.mtimeMs && cached.size === stat.size) return cached
  const raw = fs.readFileSync(file)
  const entry = {
    raw,
    mtimeMs: stat.mtimeMs,
    size: stat.size,
    etag: `W/"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`,
    br: null,
    gz: null
  }
  if (COMPRESSIBLE.has(ext) && raw.length >= 1024) {
    try {
      entry.br = zlib.brotliCompressSync(raw, {
        params: { [zlib.constants.BROTLI_PARAM_QUALITY]: 9, [zlib.constants.BROTLI_PARAM_SIZE_HINT]: raw.length }
      })
    } catch {
      entry.br = null
    }
    try {
      entry.gz = zlib.gzipSync(raw, { level: 9 })
    } catch {
      entry.gz = null
    }
  }
  assetCache.set(file, entry)
  return entry
}

function pickBody(req, entry) {
  const accept = String(req.headers['accept-encoding'] || '')
  if (entry.br && /\bbr\b/.test(accept)) return { encoding: 'br', body: entry.br }
  if (entry.gz && /\bgzip\b/.test(accept)) return { encoding: 'gzip', body: entry.gz }
  return { encoding: null, body: entry.raw }
}

function serveStatic(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { Allow: 'GET, HEAD' })
    res.end()
    return
  }

  let urlPath
  try {
    urlPath = decodeURIComponent((req.url || '/').split('?')[0])
  } catch {
    res.writeHead(400)
    res.end('Bad Request')
    return
  }
  const relative = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '')
  let target = path.join(DIST, relative)
  if (!target.startsWith(DIST)) {
    res.writeHead(403)
    res.end('Forbidden')
    return
  }

  // SPA history 路由回退到 index.html
  try {
    if (!fs.statSync(target).isFile()) target = path.join(DIST, 'index.html')
  } catch {
    target = path.join(DIST, 'index.html')
  }

  const entry = (() => {
    try {
      return loadAsset(target)
    } catch (error) {
      // 资源在服务运行期间被重新构建删除时，返回 404 而不是让进程崩溃
      return null
    }
  })()
  if (!entry) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Not Found')
    return
  }
  const cacheControl = cacheControlFor(target)

  if (req.headers['if-none-match'] === entry.etag) {
    res.writeHead(304, { ETag: entry.etag, 'Cache-Control': cacheControl })
    res.end()
    return
  }

  const { encoding, body } = pickBody(req, entry)
  const headers = {
    'Content-Type': MIME[path.extname(target).toLowerCase()] || 'application/octet-stream',
    'Cache-Control': cacheControl,
    ETag: entry.etag,
    Vary: 'Accept-Encoding',
    'Content-Length': body.length
  }
  if (encoding) headers['Content-Encoding'] = encoding
  res.writeHead(200, headers)
  if (req.method === 'HEAD') {
    res.end()
    return
  }
  res.end(body)
}

/** 启动后异步预热全部可压缩资源，首个真实请求直接命中缓存 */
function warmup() {
  const walk = (dir) => {
    for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, item.name)
      if (item.isDirectory()) walk(full)
      else if (COMPRESSIBLE.has(path.extname(full).toLowerCase())) loadAsset(full)
    }
  }
  try {
    walk(DIST)
    const rawKb = [...assetCache.values()].reduce((sum, a) => sum + a.raw.length, 0) / 1024
    const brKb = [...assetCache.values()].reduce((sum, a) => sum + (a.br ? a.br.length : a.raw.length), 0) / 1024
    console.log(
      `[前端] 已预压缩 ${assetCache.size} 个静态资源：${rawKb.toFixed(0)} KB → brotli ${brKb.toFixed(0)} KB`
    )
  } catch (error) {
    console.warn('[前端] 预压缩失败，按需压缩兜底：', error.message)
  }
}

const server = http.createServer((req, res) => {
  if ((req.url || '').startsWith('/api/')) {
    proxyHttp(req, res)
    return
  }
  serveStatic(req, res)
})

// WebSocket 代理：/ws/chat?token=... -> ws://127.0.0.1:8080
server.on('upgrade', (req, socket, head) => {
  if (!(req.url || '').startsWith('/ws')) {
    socket.destroy()
    return
  }
  // 客户端异常断开（ECONNRESET）不能冒泡成未捕获异常，否则整个静态服务进程会退出
  socket.on('error', () => socket.destroy())
  const proxyReq = http.request({
    host: BACKEND_HOST,
    port: BACKEND_PORT,
    path: req.url,
    method: req.method,
    headers: req.headers
  })
  proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
    proxySocket.on('error', () => socket.destroy())
    proxySocket.on('close', () => socket.destroy())
    socket.write(`HTTP/1.1 101 Switching Protocols\r\n`)
    for (const [key, value] of Object.entries(proxyRes.headers)) {
      socket.write(`${key}: ${value}\r\n`)
    }
    socket.write('\r\n')
    if (proxyHead && proxyHead.length) socket.write(proxyHead)
    if (head && head.length) proxySocket.write(head)
    proxySocket.pipe(socket)
    socket.pipe(proxySocket)
  })
  proxyReq.on('error', () => socket.destroy())
  proxyReq.end()
})

// 畸形请求或客户端提前断开时只结束当前连接，不终止进程
server.on('clientError', (err, socket) => {
  if (socket.writable && !socket.destroyed) {
    socket.end('HTTP/1.1 400 Bad Request\r\n\r\n')
  } else {
    socket.destroy()
  }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[前端] 智客通前端已启动: http://127.0.0.1:${PORT}`)
  console.log(`[前端] 静态目录: ${DIST}`)
  console.log(`[前端] /api、/ws 已代理到 http://${BACKEND_HOST}:${BACKEND_PORT}`)
  setImmediate(warmup)
})
