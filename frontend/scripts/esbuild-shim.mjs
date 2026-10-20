/**
 * 纯 JS 的 esbuild API 替身（仅在 DSH 沙箱下由 loader 注入）
 *
 * 背景：本沙箱禁止 Node 通过管道 stdio 启动子进程，esbuild 官方 JS API 必须启动
 * 常驻子进程（pipe 通信），因此任何 esbuild 调用都会抛 `spawn EPERM`。
 *
 * Vite 5 在「构建」时只有一处真正依赖 esbuild：vite:define 插件用它做 define 替换
 * （替换 process.env.NODE_ENV、__VUE_OPTIONS_API__ 等）。这里用纯 JS 实现同一语义：
 *   - 按 define 键做「标识符边界」匹配，长键优先，单次扫描替换（不会二次替换）
 *   - sourcemap: false 时返回空 map 字符串，与 esbuild 行为一致
 *   - 其余 API（build/context）显式报错，避免静默失效
 */

const VERSION = '0.21.5'

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 单次扫描完成所有 define 替换（键按长度倒序，保证 import.meta.env.X 优先于 import.meta.env） */
function replaceDefines(code, define) {
  const keys = Object.keys(define || {})
  if (!keys.length) return code
  keys.sort((a, b) => b.length - a.length)

  const pattern = new RegExp(
    `(?<![\\w$.])(?:${keys.map(escapeRegExp).join('|')})(?![\\w$])`,
    'g'
  )

  return code.replace(pattern, (match) => {
    const value = define[match]
    // define 值在 Vite 中已是 JS 表达式字符串（如 "production"、{}、undefined）
    return value === undefined ? 'undefined' : String(value)
  })
}

function unsupported(api) {
  const error = new Error(
    `[sandbox] esbuild.${api} 在当前环境不可用：沙箱禁止通过管道 stdio 启动子进程。` +
      '请使用 scripts/build.mjs 方式构建（已关闭 esbuild 与压缩）。'
  )
  error.code = 'ESBUILD_UNAVAILABLE_IN_SANDBOX'
  return error
}

export async function transform(code, options = {}) {
  const resultCode = replaceDefines(String(code), options.define)
  return {
    code: resultCode,
    map: options.sourcemap ? '' : '',
    warnings: []
  }
}

export function transformSync(code, options = {}) {
  return {
    code: replaceDefines(String(code), options.define),
    map: options.sourcemap ? '' : '',
    warnings: []
  }
}

export async function formatMessages(messages = []) {
  return messages.map((message) => {
    const location = message?.location
    const where = location ? ` (${location.file || ''}:${location.line || 0})` : ''
    return `${message?.text || ''}${where}`
  })
}

export async function build() {
  throw unsupported('build')
}

export async function context() {
  throw unsupported('context')
}

export async function stop() {
  /* no-op */
}

export const version = VERSION

export default {
  transform,
  transformSync,
  formatMessages,
  build,
  context,
  stop,
  version
}
