import http from './http'

/**
 * 认证
 * 登录失败需要明确提示，因此不使用 silent
 */
export const login = (payload) => http.post('/auth/login', payload)

/* ---------- 健康检查（公开） ---------- */
export const getHealth = () => http.get('/health', { silent: true })

/**
 * 控制台只读过渡接口 /api/console/*
 * 直接查询本系统 MySQL 业务表，不返回模拟数据；
 * 正式模块接口按 M1～M6 里程碑替换。
 * silent: true —— 接口异常时由各页面展示空态/错误提示，避免弹窗刷屏。
 */
export const getOverview = () => http.get('/console/overview', { silent: true })
export const getKbEntries = (params) => http.get('/console/kb/entries', { params, silent: true })
export const getCategories = () => http.get('/console/categories', { silent: true })
export const getWorkOrders = (params) => http.get('/console/workorders', { params, silent: true })
export const getSessions = (params) => http.get('/console/sessions', { params, silent: true })
export const getUsers = (params) => http.get('/console/users', { params, silent: true })
export const getAgents = () => http.get('/console/agents', { silent: true })
export const getWaitingQueue = () => http.get('/console/waiting', { silent: true })

/* ---------- AI 对话（失败时需提示，同时在气泡中展示错误） ---------- */
export const sendAiMessage = (message) => http.post('/ai/chat', { message })
