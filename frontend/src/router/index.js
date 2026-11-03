import { createRouter, createWebHistory } from 'vue-router'
import { clearAuth, getToken, getUser } from '@/api/http'

/** 角色默认落地页：登录成功、访问根路径、未知路径都走这里 */
export const ROLE_HOME = {
  PLATFORM_ADMIN: '/platform/tenants',
  ADMIN: '/admin/dashboard',
  AGENT: '/agent/workbench',
  USER: '/user/home'
}

export const roleHomePath = (roleCode) => ROLE_HOME[roleCode] || '/login'

const roleHome = () => roleHomePath(getUser()?.roleCode)

/** 旧路径 → 按当前角色映射到新入口 */
const legacyRedirect = (legacyPath) => () => {
  const roleCode = getUser()?.roleCode
  const map = {
    '/dashboard': { ADMIN: '/admin/dashboard', PLATFORM_ADMIN: '/platform/tenants' },
    '/sessions': {
      ADMIN: '/admin/sessions',
      AGENT: '/agent/sessions',
      USER: '/user/sessions'
    },
    '/orders': {
      ADMIN: '/admin/orders',
      AGENT: '/agent/orders',
      USER: '/user/orders'
    },
    '/agents': { ADMIN: '/admin/agents', AGENT: '/agent/workbench' },
    '/knowledge': {
      ADMIN: '/admin/knowledge',
      AGENT: '/agent/knowledge',
      USER: '/user/knowledge'
    },
    '/chat': {
      ADMIN: '/admin/dashboard',
      AGENT: '/agent/sessions',
      USER: '/user/chat'
    },
    '/users': { ADMIN: '/admin/users' },
    '/roles': { ADMIN: '/admin/roles' },
    '/login-logs': { ADMIN: '/admin/login-logs' },
    '/statistics': { ADMIN: '/admin/statistics' },
    '/settings': { ADMIN: '/admin/settings' }
  }
  return map[legacyPath]?.[roleCode] || roleHomePath(roleCode)
}

/* =========================================================
   /admin 管理端（ADMIN）
   ========================================================= */
const adminRoutes = [
  {
    path: 'dashboard',
    name: 'admin-dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: {
      title: '数据看板',
      short: '看板',
      subtitle: '今日服务概况 · 实时指标与最近动态',
      icon: 'DataAnalysis',
      group: '运营',
      order: 1,
      roles: ['ADMIN']
    }
  },
  {
    path: 'screen',
    name: 'admin-screen',
    component: () => import('@/views/OpsScreen.vue'),
    meta: {
      title: '实时大屏',
      short: '大屏',
      subtitle: '排队人数、排队时长与坐席负载实时推送',
      icon: 'Monitor',
      group: '运营',
      order: 1.5,
      roles: ['ADMIN']
    }
  },
  {
    path: 'plan',
    name: 'admin-plan',
    component: () => import('@/views/Plan.vue'),
    meta: {
      title: '套餐与用量',
      short: '套餐',
      subtitle: '本月额度水位 · 近 30 天服务结果 · 套餐对比',
      icon: 'Wallet',
      group: '运营',
      order: 2,
      roles: ['ADMIN']
    }
  },
  {
    path: 'statistics',
    name: 'admin-statistics',
    component: () => import('@/views/Statistics.vue'),
    meta: {
      title: '数据统计',
      short: '统计',
      subtitle: '服务量、工单分布、知识命中与坐席效率多维分析',
      icon: 'DataLine',
      group: '运营',
      order: 3,
      roles: ['ADMIN']
    }
  },
  {
    path: 'sessions',
    name: 'admin-sessions',
    component: () => import('@/views/Sessions.vue'),
    meta: {
      title: '会话管理',
      short: '会话',
      subtitle: 'AI 接待 / 人工排队 / 已结束会话全量视图',
      icon: 'ChatLineSquare',
      group: '服务',
      order: 2,
      roles: ['ADMIN']
    }
  },
  {
    path: 'orders',
    name: 'admin-orders',
    component: () => import('@/views/Orders.vue'),
    meta: {
      title: '工单中心',
      short: '工单',
      subtitle: '按状态、优先级与关键词检索工单，跟踪 SLA 时限',
      icon: 'Tickets',
      group: '服务',
      order: 3,
      roles: ['ADMIN']
    }
  },
  {
    path: 'agents',
    name: 'admin-agents',
    component: () => import('@/views/Agents.vue'),
    meta: {
      title: '坐席工作台',
      short: '坐席',
      subtitle: '在线状态、并发负载与等待队列',
      icon: 'Headset',
      group: '服务',
      order: 4,
      roles: ['ADMIN']
    }
  },
  {
    path: 'monitor',
    name: 'admin-monitor',
    component: () => import('@/views/Monitor.vue'),
    meta: {
      title: '会话监听',
      short: '监听',
      subtitle: '实时巡视进行中会话 · 强插 / 接管 / 改派',
      icon: 'Position',
      group: '服务',
      order: 4.55,
      roles: ['ADMIN']
    }
  },
  {
    path: 'skill-groups',
    name: 'admin-skill-groups',
    component: () => import('@/views/SkillGroups.vue'),
    meta: {
      title: '技能组',
      short: '技能组',
      subtitle: '按业务技能划分坐席 · 转人工咨询类型与分配',
      icon: 'UserFilled',
      group: '服务',
      order: 4.6,
      roles: ['ADMIN']
    }
  },
  {
    path: 'quick-replies',
    name: 'admin-quick-replies',
    component: () => import('@/views/QuickReplies.vue'),
    meta: {
      title: '快捷回复话术',
      short: '话术',
      subtitle: '公共话术维护 · 坐席一键引用',
      icon: 'ChatLineRound',
      group: '服务',
      order: 4.7,
      roles: ['ADMIN']
    }
  },
  {
    path: 'order-forms',
    name: 'admin-order-forms',
    component: () => import('@/views/OrderForms.vue'),
    meta: {
      title: '工单表单',
      short: '表单',
      subtitle: '自定义字段与工单模板 · 结构化建单',
      icon: 'Grid',
      group: '服务',
      order: 4.8,
      roles: ['ADMIN']
    }
  },
  {
    path: 'sla-policies',
    name: 'admin-sla-policies',
    component: () => import('@/views/SlaPolicies.vue'),
    meta: {
      title: 'SLA 时效策略',
      short: 'SLA',
      subtitle: '按优先级配置三级时限 · 预警阈值与超时升级',
      icon: 'Timer',
      group: '服务',
      order: 4.9,
      roles: ['ADMIN']
    }
  },
  {
    path: 'order-rules',
    name: 'admin-order-rules',
    component: () => import('@/views/OrderRules.vue'),
    meta: {
      title: '工单规则与自动化',
      short: '规则',
      subtitle: '自动分配 / 事件触发器 / 时间自动化 · 命中即执行并留痕',
      icon: 'SetUp',
      group: '服务',
      order: 4.95,
      roles: ['ADMIN']
    }
  },
  {
    path: 'knowledge',
    name: 'admin-knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: {
      title: '知识库',
      short: '知识库',
      subtitle: '标准问答条目维护 · 命中次数排行',
      icon: 'Collection',
      group: '知识',
      order: 5,
      roles: ['ADMIN']
    }
  },
  {
    path: 'kb-ops',
    name: 'admin-kb-ops',
    component: () => import('@/views/KbOps.vue'),
    meta: {
      title: '知识运营',
      short: '知识运营',
      subtitle: '未命中闭环 · 健康度 · 查重冲突 · 知识包导入',
      icon: 'DataAnalysis',
      group: '知识',
      order: 5.5,
      roles: ['ADMIN']
    }
  },
  {
    path: 'voc',
    name: 'admin-voc',
    component: () => import('@/views/Voc.vue'),
    meta: {
      title: '客户之声',
      short: '客户之声',
      subtitle: '未命中问法聚类 · 热点话题 · 访客词云',
      icon: 'Opportunity',
      group: '知识',
      order: 5.55,
      roles: ['ADMIN']
    }
  },
  {
    path: 'quality',
    name: 'admin-quality',
    component: () => import('@/views/Quality.vue'),
    meta: {
      title: '智能质检',
      short: '质检',
      subtitle: '全量自动质检 · 评分卡与合格率',
      icon: 'Aim',
      group: '质量',
      order: 5.6,
      roles: ['ADMIN']
    }
  },
  {
    path: 'quality-rules',
    name: 'admin-quality-rules',
    component: () => import('@/views/QualityRules.vue'),
    meta: {
      title: '质检规则',
      short: '质检规则',
      subtitle: '语义 / 情感 / 服务规范 / 敏感词四类规则',
      icon: 'SetUp',
      group: '质量',
      order: 5.65,
      roles: ['ADMIN']
    }
  },
  {
    path: 'alerts',
    name: 'admin-alerts',
    component: () => import('@/views/Alerts.vue'),
    meta: {
      title: '告警中心',
      short: '告警',
      subtitle: '待处理告警 · 确认闭环 · 手动扫描',
      icon: 'Bell',
      group: '质量',
      order: 5.7,
      roles: ['ADMIN']
    }
  },
  {
    path: 'alert-rules',
    name: 'admin-alert-rules',
    component: () => import('@/views/AlertRules.vue'),
    meta: {
      title: '告警规则',
      short: '告警规则',
      subtitle: '指标阈值 / 冷却 / 通知对象可配置',
      icon: 'AlarmClock',
      group: '质量',
      order: 5.75,
      roles: ['ADMIN']
    }
  },
  {
    path: 'embed',
    name: 'admin-embed',
    component: () => import('@/views/Embed.vue'),
    meta: {
      title: '接入方式',
      short: '接入',
      subtitle: '网页悬浮球 / 独立链接 / API 对接',
      icon: 'Connection',
      group: '接入',
      order: 4.5,
      roles: ['ADMIN']
    }
  },
  {
    path: 'users',
    name: 'admin-users',
    component: () => import('@/views/Users.vue'),
    meta: {
      title: '用户管理',
      short: '用户',
      subtitle: '系统账号、角色与登录状态',
      icon: 'User',
      group: '系统',
      order: 6,
      roles: ['ADMIN']
    }
  },
  {
    path: 'roles',
    name: 'admin-roles',
    component: () => import('@/views/Roles.vue'),
    meta: {
      title: '角色管理',
      short: '角色',
      subtitle: 'V1.0 固定三角色 · 角色仅用于分配用户',
      icon: 'Key',
      group: '系统',
      order: 7,
      roles: ['ADMIN']
    }
  },
  {
    path: 'login-logs',
    name: 'admin-login-logs',
    component: () => import('@/views/LoginLogs.vue'),
    meta: {
      title: '登录日志',
      short: '登录日志',
      subtitle: '登录成功 / 失败记录、来源 IP 与客户端',
      icon: 'Document',
      group: '系统',
      order: 8,
      roles: ['ADMIN']
    }
  },
  {
    path: 'audit-logs',
    name: 'admin-audit-logs',
    component: () => import('@/views/AuditLogs.vue'),
    meta: {
      title: '操作审计',
      short: '审计',
      subtitle: '本租户写操作留痕 · 变更前后值可追溯',
      icon: 'Memo',
      group: '系统',
      order: 8.5,
      roles: ['ADMIN']
    }
  },
  {
    path: 'ai-models',
    name: 'admin-ai-models',
    component: () => import('@/views/AiModels.vue'),
    meta: {
      title: '大模型配置',
      short: '大模型',
      subtitle: '多平台接入 · 默认模型切换 · 连通性测试',
      icon: 'Cpu',
      group: '系统',
      order: 9,
      roles: ['ADMIN']
    }
  },
  {
    path: 'settings',
    name: 'admin-settings',
    component: () => import('@/views/SystemSettings.vue'),
    meta: {
      title: '系统设置',
      short: '设置',
      subtitle: '账号安全、接待参数与运行状态',
      icon: 'Setting',
      group: '系统',
      order: 10,
      roles: ['ADMIN']
    }
  }
]

/* =========================================================
   /agent 坐席端（AGENT）
   ========================================================= */
const agentRoutes = [
  {
    path: 'workbench',
    name: 'agent-workbench',
    component: () => import('@/views/Agents.vue'),
    meta: {
      title: '坐席工作台',
      short: '工作台',
      subtitle: '在线状态、并发负载与等待队列',
      icon: 'Headset',
      group: '坐席',
      order: 1,
      roles: ['AGENT']
    }
  },
  {
    path: 'sessions',
    name: 'agent-sessions',
    component: () => import('@/views/AgentSessions.vue'),
    meta: {
      title: '我的会话',
      short: '会话',
      subtitle: '仅展示分配到本人的会话',
      icon: 'ChatLineSquare',
      group: '坐席',
      order: 2,
      roles: ['AGENT']
    }
  },
  {
    path: 'orders',
    name: 'agent-orders',
    component: () => import('@/views/AgentOrders.vue'),
    meta: {
      title: '我的工单',
      short: '工单',
      subtitle: '仅展示本人受理的工单与 SLA 时限',
      icon: 'Tickets',
      group: '坐席',
      order: 3,
      roles: ['AGENT']
    }
  },
  {
    path: 'alerts',
    name: 'agent-alerts',
    component: () => import('@/views/AgentAlerts.vue'),
    meta: {
      title: '我的告警',
      short: '告警',
      subtitle: '本人会话告警事件与个人告警规则',
      icon: 'Bell',
      group: '坐席',
      order: 5,
      roles: ['AGENT']
    }
  },
  {
    path: 'knowledge',
    name: 'agent-knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: {
      title: '知识库',
      short: '知识库',
      subtitle: '标准问答条目维护 · 命中测试与命中排行',
      icon: 'Collection',
      group: '知识',
      order: 4,
      roles: ['AGENT']
    }
  }
]

/* =========================================================
   /platform 平台运营端（PLATFORM_ADMIN，S4-B）
   ========================================================= */
const platformRoutes = [
  {
    path: 'tenants',
    name: 'platform-tenants',
    component: () => import('@/views/PlatformTenants.vue'),
    meta: {
      title: '租户与套餐',
      short: '租户',
      subtitle: '开通租户 · 套餐额度 · 用量水位',
      icon: 'OfficeBuilding',
      group: '平台',
      order: 1,
      roles: ['PLATFORM_ADMIN']
    }
  },
  {
    path: 'audit-logs',
    name: 'platform-audit-logs',
    component: () => import('@/views/AuditLogs.vue'),
    meta: {
      title: '操作审计',
      short: '审计',
      subtitle: '跨租户写操作留痕 · 谁在何时改了什么',
      icon: 'Memo',
      group: '平台',
      order: 2,
      roles: ['PLATFORM_ADMIN']
    }
  }
]

/* =========================================================
   /user 用户端（USER）
   ========================================================= */
const userRoutes = [
  {
    path: 'home',
    name: 'user-home',
    component: () => import('@/views/UserHome.vue'),
    meta: {
      title: '首页',
      short: '首页',
      subtitle: '我的会话与工单概览',
      icon: 'House',
      group: '用户',
      order: 1,
      roles: ['USER']
    }
  },
  {
    path: 'chat',
    name: 'user-chat',
    component: () => import('@/views/Chat.vue'),
    meta: {
      title: 'AI 咨询',
      short: 'AI 咨询',
      subtitle: '智能助手在线答疑',
      icon: 'ChatDotRound',
      group: '服务',
      order: 2,
      roles: ['USER']
    }
  },
  {
    path: 'sessions',
    name: 'user-sessions',
    component: () => import('@/views/UserSessions.vue'),
    meta: {
      title: '我的会话',
      short: '会话',
      subtitle: '仅展示本人发起的会话',
      icon: 'ChatLineSquare',
      group: '用户',
      order: 3,
      roles: ['USER']
    }
  },
  {
    path: 'orders',
    name: 'user-orders',
    component: () => import('@/views/UserOrders.vue'),
    meta: {
      title: '我的工单',
      short: '工单',
      subtitle: '仅展示本人提交的工单与处理进度',
      icon: 'Tickets',
      group: '用户',
      order: 4,
      roles: ['USER']
    }
  },
  {
    path: 'knowledge',
    name: 'user-knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: {
      title: '知识库',
      short: '知识库',
      subtitle: '常见问题自助查询',
      icon: 'Collection',
      group: '知识',
      order: 5,
      roles: ['USER']
    }
  },
  {
    path: 'profile',
    name: 'user-profile',
    component: () => import('@/views/UserProfile.vue'),
    meta: {
      title: '个人资料',
      short: '资料',
      subtitle: '当前账号信息（只读）',
      icon: 'User',
      group: '用户',
      order: 6,
      roles: ['USER']
    }
  }
]

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/embed/chat',
    name: 'embed-chat',
    component: () => import('@/views/EmbedChat.vue'),
    meta: { public: true, title: '在线客服' }
  },
  {
    path: '/embed/demo',
    name: 'embed-demo',
    component: () => import('@/views/EmbedDemo.vue'),
    meta: { public: true, title: '接入演示' }
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { title: '无权访问' }
  },
  {
    path: '/platform',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/platform/tenants',
    meta: { roles: ['PLATFORM_ADMIN'], portalKind: 'platform' },
    children: platformRoutes
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { roles: ['ADMIN'], portalKind: 'admin' },
    children: adminRoutes
  },
  {
    path: '/agent',
    component: () => import('@/layouts/PortalLayout.vue'),
    redirect: '/agent/workbench',
    meta: { roles: ['AGENT'], portalKind: 'agent' },
    children: agentRoutes
  },
  {
    path: '/user',
    component: () => import('@/layouts/PortalLayout.vue'),
    redirect: '/user/home',
    meta: { roles: ['USER'], portalKind: 'user' },
    children: userRoutes
  },

  /* 旧路径兼容：按当前角色重定向到新入口 */
  { path: '/dashboard', redirect: legacyRedirect('/dashboard') },
  { path: '/sessions', redirect: legacyRedirect('/sessions') },
  { path: '/orders', redirect: legacyRedirect('/orders') },
  { path: '/agents', redirect: legacyRedirect('/agents') },
  { path: '/knowledge', redirect: legacyRedirect('/knowledge') },
  { path: '/chat', redirect: legacyRedirect('/chat') },
  { path: '/users', redirect: legacyRedirect('/users') },
  { path: '/roles', redirect: legacyRedirect('/roles') },
  { path: '/login-logs', redirect: legacyRedirect('/login-logs') },
  { path: '/statistics', redirect: legacyRedirect('/statistics') },
  { path: '/settings', redirect: legacyRedirect('/settings') },

  { path: '/', redirect: roleHome },
  { path: '/:pathMatch(.*)*', redirect: roleHome }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · 智客通` : '智客通 · 智能客服工作台'

  const logged = Boolean(getToken())

  // 未登录：除公开页外一律去登录页
  if (!to.meta?.public && !logged) {
    return {
      name: 'login',
      query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : {}
    }
  }

  // 已登录访问登录页：按角色落地（无有效角色则清理登录态，避免死循环）
  if (to.name === 'login' && logged) {
    const home = roleHome()
    if (home === '/login') {
      clearAuth()
      return true
    }
    return home
  }

  // 页面级角色校验：不在 meta.roles 内 → 403（不静默回看板）
  const requiredRoles = to.meta?.roles
  if (Array.isArray(requiredRoles) && requiredRoles.length) {
    const roleCode = getUser()?.roleCode
    if (!roleCode || !requiredRoles.includes(roleCode)) {
      return { name: 'forbidden', query: { from: to.fullPath } }
    }
  }

  return true
})

export default router
