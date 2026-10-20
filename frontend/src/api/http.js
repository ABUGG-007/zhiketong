import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'zkt_token'
export const USER_KEY = 'zkt_user'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const getUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}
export const saveAuth = (token, user) => {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
}
export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

// 同源前缀 /api，由 vite server.proxy / preview.proxy 转发到 Spring Boot:8080
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

let redirecting = false

async function redirectToLogin() {
  if (redirecting) return
  redirecting = true
  clearAuth()
  try {
    // 动态引入，避免 http <-> router 循环依赖
    const mod = await import('@/router')
    const router = mod.default
    const current = router.currentRoute.value
    if (current.name !== 'login') {
      await router.replace({ name: 'login', query: { redirect: current.fullPath } })
    }
    ElMessage.warning('登录状态已失效，请重新登录')
  } catch {
    window.location.href = '/login'
  } finally {
    setTimeout(() => {
      redirecting = false
    }, 800)
  }
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    const silent = Boolean(response.config?.silent)
    // 统一返回体 {code, message, data}
    if (body && typeof body === 'object' && !Array.isArray(body) && 'code' in body) {
      const code = Number(body.code)
      const message = body.message || '请求失败'
      if (code === 200) return body.data
      if (code === 401) {
        redirectToLogin()
        return Promise.reject(Object.assign(new Error(message), { code }))
      }
      if (code === 403) {
        if (!silent) ElMessage.error('无权限访问该资源')
        return Promise.reject(Object.assign(new Error(message), { code }))
      }
      if (code === 404) {
        if (!silent) ElMessage.warning(message || '接口不存在或数据已被删除')
        return Promise.reject(Object.assign(new Error(message), { code }))
      }
      if (!silent) ElMessage.error(message)
      return Promise.reject(Object.assign(new Error(message), { code }))
    }
    return body
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data
    const serverMsg = typeof body === 'object' && body ? body.message : ''
    const silent = Boolean(error?.config?.silent)
    let message = serverMsg
    if (status === 401) {
      redirectToLogin()
      return Promise.reject(error)
    }
    if (silent) return Promise.reject(error)
    if (status === 403) {
      ElMessage.error('无权限访问该资源')
    } else if (status === 404) {
      ElMessage.warning(message || '接口暂未开放（404）')
    } else if (status >= 500) {
      ElMessage.error(message || '服务器异常，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查后端服务')
    } else {
      ElMessage.error(message || '无法连接后端服务（http://localhost:8080）')
    }
    return Promise.reject(error)
  }
)

export default http
