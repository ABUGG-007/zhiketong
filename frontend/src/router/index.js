import { createRouter, createWebHistory } from 'vue-router'
import { clearAuth, getToken, getUser } from '@/api/http'

/** 角色默认落地页：登录成功、访问根路径、未知路径都走这里 */
export const ROLE_HOME = {
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
    '/dashboard': { ADMIN: '/admin/dashboard' },
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
      AGENT: '/agent/chat',
      USER: '/user/chat'
    },
    '/users': { ADMIN: '/admin/users' },
    '/roles': { ADMIN: '/admin/roles' },
    '/login-logs': { ADMIN: '/admin/login-logs' }
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
      icon: 'Avatar',
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
    path: 'knowledge',
    name: 'agent-knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: {
      title: '知识库',
      short: '知识库',
      subtitle: '标准问答条目查询 · 命中次数排行',
      icon: 'Collection',
      group: '知识',
      order: 4,
      roles: ['AGENT']
    }
  },
  {
    path: 'chat',
    name: 'agent-chat',
    component: () => import('@/views/Chat.vue'),
    meta: {
      title: 'AI 咨询',
      short: 'AI 咨询',
      subtitle: '体验 AI 知识库应答与降级话术',
      icon: 'ChatDotRound',
      group: '服务',
      order: 5,
      roles: ['AGENT']
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
      icon: 'HomeFilled',
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
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { title: '无权访问' }
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
