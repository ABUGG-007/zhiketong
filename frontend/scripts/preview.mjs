import './bootstrap.mjs'
import './register-esbuild-shim.mjs'
import { preview } from 'vite'
import { root, previewOptions } from '../vite.shared.mjs'

// 生产构建 + Vite preview（静态产物 + /api、/ws 代理），沙箱下最稳定的启动方式
await preview({
  root,
  configFile: false,
  logLevel: 'info',
  preview: previewOptions
})
