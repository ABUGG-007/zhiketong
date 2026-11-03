import { fileURLToPath, URL } from 'node:url'

/**
 * 共享配置：build / dev / preview 三个脚本共用
 *
 * 注意：当前运行环境禁止 Node 以管道 stdio 创建子进程（Windows 命名管道被拦截），
 * esbuild 无法启动其服务进程，因此关闭 esbuild（esbuild: false），压缩改用纯 JS 的 terser；
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

/**
 * 第三方依赖按库拆包：首屏只需 Vue + Router + Element Plus + axios 几个长期缓存块，
 * 业务视图各自按路由懒加载，局部改动不会让整包缓存失效。
 */
function manualChunks(id) {
  if (!id.includes('node_modules')) return
  if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
  if (id.includes('vue-router') || /node_modules[\\/](@vue|vue)[\\/]/.test(id)) return 'vue'
  if (id.includes('axios')) return 'axios'
  return 'vendor'
}

export const buildOptions = {
  outDir: 'dist',
  emptyOutDir: true,
  sourcemap: false,
  // 沙箱禁止 esbuild 子进程，压缩交给纯 JS 的 terser
  minify: 'terser',
  terserOptions: {
    compress: {
      drop_console: ['log', 'debug', 'info'],
      passes: 2
    },
    mangle: true,
    format: { comments: false }
  },
  cssCodeSplit: true,
  // esbuild 不可用，CSS 交给 lightningcss（原生模块，无需子进程）
  cssMinify: 'lightningcss',
  target: 'es2020',
  chunkSizeWarningLimit: 900,
  reportCompressedSize: true,
  rollupOptions: {
    output: { manualChunks }
  }
}
