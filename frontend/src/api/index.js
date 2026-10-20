import http from './http'

/**
 * 认证
 * 登录失败需要明确提示，因此不使用 silent
 */
export const login = (payload) => http.post('/auth/login', payload)

/* ---------- 健康检查（公开） ---------- */
export const getHealth = () => http.get('/health', { silent: true })

/**
 * 演示只读接口 /api/demo/*
 * silent: true —— 接口未就绪时由各页面自行展示空态/错误提示，避免弹窗刷屏
 */
export const getOverview = () => http.get('/demo/overview', { silent: true })
export const getKbEntries = (params) => http.get('/demo/kb/entries', { params, silent: true })
export const getCategories = () => http.get('/demo/categories', { silent: true })
export const getWorkOrders = (params) => http.get('/demo/workorders', { params, silent: true })
export const getSessions = (params) => http.get('/demo/sessions', { params, silent: true })
export const getUsers = (params) => http.get('/demo/users', { params, silent: true })
export const getAgents = () => http.get('/demo/agents', { silent: true })
export const getWaitingQueue = () => http.get('/demo/waiting', { silent: true })

/* ---------- AI 对话（失败时需提示，同时在气泡中展示错误） ---------- */
export const sendAiMessage = (message) => http.post('/ai/chat', { message })
