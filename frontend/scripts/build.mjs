// 沙箱兼容引导：必须在 vite 之前完成（ESM 静态 import 会先求值 bootstrap）
import './bootstrap.mjs'
// 注册 esbuild 替身（纯 JS define 替换），随后再动态加载 vite
import './register-esbuild-shim.mjs'
import { root, resolve, buildOptions } from '../vite.shared.mjs'

const { build } = await import('vite')
const vue = (await import('@vitejs/plugin-vue')).default

await build({
  root,
  configFile: false,
  logLevel: 'info',
  plugins: [vue()],
  resolve,
  esbuild: false,
  build: buildOptions
})
