// 沙箱兼容引导：必须在 vite 之前完成（ESM 静态 import 会先求值 bootstrap）
import './bootstrap.mjs'
// 注册 esbuild 替身（纯 JS define 替换），随后再动态加载 vite
import './register-esbuild-shim.mjs'
import fs from 'node:fs'
import path from 'node:path'
import { root, resolve, buildOptions } from '../vite.shared.mjs'

const { build } = await import('vite')
const vue = (await import('@vitejs/plugin-vue')).default

// 构建前强制清空产物目录：沙箱环境下 Vite 的 emptyOutDir 曾多次未生效，
// 导致历次构建的 hash 产物堆积（实测累积 80 轮、260MB），这里显式清理。
const outDir = path.join(root, buildOptions.outDir)
fs.rmSync(outDir, { recursive: true, force: true })

await build({
  root,
  configFile: false,
  logLevel: 'info',
  plugins: [vue()],
  resolve,
  esbuild: false,
  build: buildOptions
})
