import { fileURLToPath, URL } from 'node:url'

/**
 * 共享配置：build / dev / preview 三个脚本共用
 *
 * 注意：当前运行环境禁止 Node 以管道 stdio 创建子进程（Windows 命名管道被拦截），
 * esbuild 无法启动其服务进程，因此统一关闭 esbuild（esbuild: false + minify: false），
 * 项目全部为纯 JS，由 @vue/compiler-sfc 与 Rollup（纯 JS 实现）完成编译打包。
 */

export const root = fileURLToPath(new URL('.', import.meta.url))
export const backend = 'http://localhost:8080'

export const resolve = {
  alias: {
    '@': fileURLToPath(new URL('./src', import.meta.url))
  }
}

const proxy = {
  '/api': {
    target: backend,
    changeOrigin: true
  },
  '/ws': {
    target: backend.replace('http', 'ws'),
    ws: true,
    changeOrigin: true
  }
}

export const serverOptions = {
  host: '127.0.0.1',
  port: 5173,
  strictPort: false,
  proxy
}

export const previewOptions = {
  host: '127.0.0.1',
  port: 4173,
  strictPort: false,
  proxy
}

export const buildOptions = {
  outDir: 'dist',
  emptyOutDir: true,
  sourcemap: false,
  minify: false,
  cssMinify: false,
  target: 'es2020',
  chunkSizeWarningLimit: 4000
}
