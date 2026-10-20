/**
 * 沙箱兼容引导（必须在 import vite 之前引入）
 *
 * 当前运行环境（DSH 沙箱 / Windows）禁止 Node 以管道 stdio 创建子进程，
 * 而 Vite 在 Windows 上会用 exec('net use') 探测网络映射盘，exec 会同步抛 EPERM，
 * 直接中断依赖解析（commonjs--resolver -> spawn EPERM）。
 *
 * Vite 的 optimizeSafeRealPathSync 逻辑是：
 *   1) 先尝试 fs.realpathSync.native('./')
 *   2) 若抛 EISDIR，则退回到纯 JS 的 fs.realpathSync 并直接返回（不再 exec）
 *
 * 这里让第一次 native 探测抛出 EISDIR（随后立刻恢复原生实现），
 * 使 Vite 走安全的回退分支，不触发任何子进程。
 *
 * 该 shim 只影响构建/启动脚本进程，不修改 node_modules，也不影响浏览器端代码。
 */
import fs from 'node:fs'

const nativeRealpath = fs.realpathSync.native
let shimUsed = false

fs.realpathSync.native = function realpathSyncNativeShim(...args) {
  // 仅第一次调用走 shim，之后恢复原生实现，避免影响其他依赖
  fs.realpathSync.native = nativeRealpath
  shimUsed = true
  const error = new Error('EISDIR: illegal operation on a directory, read')
  error.code = 'EISDIR'
  error.errno = -4068
  error.syscall = 'read'
  throw error
}

export const isShimActive = () => !shimUsed
export { shimUsed }
