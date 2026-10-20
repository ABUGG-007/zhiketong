/**
 * 智客通前端静态服务（零依赖）
 *
 * 背景：当前 Windows 沙箱中 Node 无法以管道 stdio 启动子进程，Vite 的 esbuild
 * 服务进程会被系统拦截；因此前端已通过纯 JS 构建脚本产出 frontend/dist，
 * 这里用 Node 原生 http 模块托管 dist，并把 /api、/ws 代理到 Spring Boot。
 *
 * 启动：node serve-frontend.mjs
 * 访问：http://127.0.0.1:5173
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
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
      headers: { ...req.headers, host: `${BACKEND_HOST}:${BACKEND_PORT}` }
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
      proxyRes.pipe(res)
    }
  )
  proxyReq.on('error', (error) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    }
    res.end(JSON.stringify({ code: 502, message: '后端服务不可用：' + error.message }))
  })
  req.pipe(proxyReq)
}

function serveStatic(req, res) {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0])
  const relative = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '')
  const file = path.join(DIST, relative)
  if (!file.startsWith(DIST)) {
    res.writeHead(403)
    res.end('Forbidden')
    return
  }

  let target = file
  if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
    // SPA history 路由回退到 index.html
    target = path.join(DIST, 'index.html')
  }
  const ext = path.extname(target).toLowerCase()
  res.writeHead(200, {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=3600'
  })
  fs.createReadStream(target).pipe(res)
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
  const proxyReq = http.request({
    host: BACKEND_HOST,
    port: BACKEND_PORT,
    path: req.url,
    method: req.method,
    headers: req.headers
  })
  proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
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

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[前端] 智客通前端已启动: http://127.0.0.1:${PORT}`)
  console.log(`[前端] 静态目录: ${DIST}`)
  console.log(`[前端] /api、/ws 已代理到 http://${BACKEND_HOST}:${BACKEND_PORT}`)
})
