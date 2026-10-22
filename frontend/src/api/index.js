import http from './http'

/**
 * 认证
 * 登录失败需要明确提示，因此不使用 silent
 */
export const login = (payload) => http.post('/auth/login', payload)

/* ---------- 健康检查（公开） ---------- */
export const getHealth = () => http.get('/health', { silent: true })

/* ---------- 管理端：用户管理（M1，仅 ADMIN） ---------- */
export const getUsers = (params) => http.get('/admin/users', { params, silent: true })
export const createUser = (payload) => http.post('/admin/users', payload)
export const updateUser = (id, payload) => http.put(`/admin/users/${id}`, payload)
export const deleteUser = (id) => http.delete(`/admin/users/${id}`)
export const updateUserStatus = (id, status) =>
  http.put(`/admin/users/${id}/status`, { status })
export const resetUserPassword = (id, newPassword) =>
  http.put(`/admin/users/${id}/password`, { newPassword })

/* ---------- 管理端：角色与登录日志（M1，仅 ADMIN） ---------- */
export const getRoles = (params) => http.get('/admin/roles', { params, silent: true })
export const getLoginLogs = (params) =>
  http.get('/admin/login-logs', { params, silent: true })

/* ---------- 坐席本人状态（M1，仅 AGENT） ---------- */
export const agentOnline = (payload = {}) => http.post('/agent/online', payload)
export const agentOffline = () => http.post('/agent/offline')
export const agentSetStatus = (onlineStatus) =>
  http.put('/agent/status', { onlineStatus })

/* ---------- 坐席端自助数据（M1.5，仅 AGENT 本人） ---------- */
export const getAgentSessions = (params) =>
  http.get('/agent/sessions', { params, silent: true })
export const getAgentOrders = (params) =>
  http.get('/agent/orders', { params, silent: true })

/* ---------- 用户端自助数据（M1.5，仅 USER 本人） ---------- */
export const getUserSessions = (params) =>
  http.get('/user/sessions', { params, silent: true })
export const getUserOrders = (params) =>
  http.get('/user/orders', { params, silent: true })
export const getUserProfile = () => http.get('/user/profile', { silent: true })

/**
 * 控制台只读过渡接口 /api/console/*
 * 直接查询本系统 MySQL 业务表，不返回模拟数据；
 * 正式模块接口按里程碑替换。
 * silent: true —— 接口异常时由各页面展示空态/错误提示，避免弹窗刷屏。
 */
export const getOverview = () => http.get('/console/overview', { silent: true })
export const getKbEntries = (params) => http.get('/console/kb/entries', { params, silent: true })
export const getCategories = () => http.get('/console/categories', { silent: true })
export const getWorkOrders = (params) => http.get('/console/workorders', { params, silent: true })
export const getSessions = (params) => http.get('/console/sessions', { params, silent: true })
export const getAgents = () => http.get('/console/agents', { silent: true })
export const getWaitingQueue = () => http.get('/console/waiting', { silent: true })

/* ---------- AI 对话（失败时需提示，同时在气泡中展示错误） ---------- */
export const sendAiMessage = (message) => http.post('/ai/chat', { message })
