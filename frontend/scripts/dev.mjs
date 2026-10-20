import './bootstrap.mjs'
import './register-esbuild-shim.mjs'
import { existsSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 演示启动脚本
 *
 * 说明：当前沙箱禁止 esbuild 启动子进程，Vite dev server 的依赖优化与 define 替换
 * 都依赖 esbuild，因此这里改为「构建产物 + Vite preview 静态服务」：
 *   - 页面、路由、/api 与 /ws 代理全部可用（与 dev server 行为一致）
 *   - 唯一区别是没有 HMR；源码改动后重新执行本命令会自动重新构建
 */

const frontendRoot = fileURLToPath(new URL('..', import.meta.url))
const distIndex = join(frontendRoot, 'dist', 'index.html')

function newestMtime(target) {
  const stats = statSync(target)
  if (!stats.isDirectory()) return stats.mtimeMs
  let newest = stats.mtimeMs
  for (const entry of readdirSync(target)) {
    if (entry === 'node_modules' || entry === 'dist' || entry === '.npm-cache') continue
    newest = Math.max(newest, newestMtime(join(target, entry)))
  }
  return newest
}

function needsBuild() {
  if (!existsSync(distIndex)) return true
  const distTime = statSync(distIndex).mtimeMs
  const sources = ['src', 'index.html', 'vite.shared.mjs']
  for (const source of sources) {
    const target = join(frontendRoot, source)
    if (!existsSync(target)) continue
    if (newestMtime(target) > distTime) return true
  }
  return false
}

if (needsBuild()) {
  console.log('[zhiketong] 检测到源码变更或缺少 dist，先执行构建…')
  await import('./build.mjs')
}

const { preview } = await import('vite')
const { root, serverOptions } = await import('../vite.shared.mjs')

await preview({
  root,
  configFile: false,
  logLevel: 'info',
  preview: { ...serverOptions }
})
