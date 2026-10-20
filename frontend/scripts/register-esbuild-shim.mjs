/**
 * 注册 esbuild 替身模块解析钩子。
 * 必须在动态 import('vite') 之前调用（ESM 静态 import 会先完成模块图解析）。
 */
import { register } from 'node:module'

register('./esbuild-loader.mjs', import.meta.url)

export {}
