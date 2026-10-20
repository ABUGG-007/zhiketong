import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/http'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: {
          title: '数据看板',
          short: '看板',
          subtitle: '今日服务概况 · 实时指标与最近动态',
          icon: 'DataAnalysis',
          group: '运营',
          order: 1
        }
      },
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/Chat.vue'),
        meta: {
          title: 'AI 聊天',
          short: 'AI 聊天',
          subtitle: '用户体验端 · 知识库命中与降级话术演示',
          icon: 'ChatDotRound',
          group: '服务',
          order: 2
        }
      },
      {
        path: 'sessions',
        name: 'sessions',
        component: () => import('@/views/Sessions.vue'),
        meta: {
          title: '会话管理',
          short: '会话',
          subtitle: 'AI 接待 / 人工排队 / 已结束会话全量视图',
          icon: 'ChatLineSquare',
          group: '服务',
          order: 3
        }
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/Orders.vue'),
        meta: {
          title: '工单中心',
          short: '工单',
          subtitle: '按状态、优先级与关键词检索工单，跟踪 SLA 时限',
          icon: 'Tickets',
          group: '服务',
          order: 4
        }
      },
      {
        path: 'agents',
        name: 'agents',
        component: () => import('@/views/Agents.vue'),
        meta: {
          title: '坐席工作台',
          short: '坐席',
          subtitle: '在线状态、并发负载与等待队列',
          icon: 'Headset',
          group: '服务',
          order: 5
        }
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/Knowledge.vue'),
        meta: {
          title: '知识库',
          short: '知识库',
          subtitle: '标准问答条目维护 · 命中次数排行',
          icon: 'Collection',
          group: '知识',
          order: 6
        }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/Users.vue'),
        meta: {
          title: '用户管理',
          short: '用户',
          subtitle: '系统账号、角色与登录状态',
          icon: 'User',
          group: '系统',
          order: 7
        }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · 智客通` : '智客通 · 智能客服工作台'

  const logged = Boolean(getToken())
  if (!to.meta?.public && !logged) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (to.name === 'login' && logged) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
