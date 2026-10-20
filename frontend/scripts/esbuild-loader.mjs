/**
 * 模块解析钩子：把对 'esbuild' 的 import 重定向到 scripts/esbuild-shim.mjs。
 * 由 scripts/register-esbuild-shim.mjs 通过 module.register() 注册，
 * 不修改 node_modules，也不影响浏览器端产物。
 */

const SHIM_URL = new URL('./esbuild-shim.mjs', import.meta.url).href

export async function resolve(specifier, context, nextResolve) {
  if (specifier === 'esbuild') {
    return { url: SHIM_URL, shortCircuit: true }
  }
  return nextResolve(specifier, context)
}
