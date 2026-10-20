# 智客通 · 前端演示应用

Vue 3 + Vite + Element Plus 实现的「智客通」智能客服 / 工单系统演示端，对接本地 Spring Boot 后端（`http://localhost:8080`）。

## 技术栈

| 说明 | 依赖 |
| --- | --- |
| 框架 | Vue 3（Composition API + `<script setup>`） |
| 路由 | vue-router 4（history 模式） |
| 请求 | axios（统一拦截 `{code,message,data}`） |
| UI | Element Plus + @element-plus/icons-vue（中文 locale） |
| 构建 | Vite 5（Rollup + @vitejs/plugin-vue） |
| 图表 | 纯 CSS 柱状图（未引入 ECharts） |
| 状态 | `reactive` + `localStorage`（未引入 Pinia） |

## 启动方式

```powershell
cd frontend

# 首次安装（缓存放在工程内，避免写全局 npm 目录）
npm install --cache .npm-cache --no-audit --no-fund

# 开发/演示启动（自动检测源码变更并构建，然后启动 5173 端口静态服务 + /api 代理）
npm run dev
# 打开 http://127.0.0.1:5173

# 生产构建
npm run build
# 产物：frontend/dist

# 只启动构建产物预览（4173 端口）
npm run preview
```

> 前端通过同源代理访问后端：`/api`、`/ws` → `http://localhost:8080`，浏览器侧不产生跨域。
> 也可以给 axios 指定 `VITE_API_BASE`（如 `http://localhost:8080/api`）直连后端。

## 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `123456` | 管理员 |
| `agent01` | `123456` | 客服坐席 |
| `user01` | `123456` | 普通用户 |

登录页提供三个账号的快捷填充按钮。

## 目录结构

```
frontend/
├── index.html
├── package.json
├── vite.shared.mjs           # 共享 Vite 配置（别名、代理、构建参数）
├── scripts/
│   ├── bootstrap.mjs         # 沙箱兼容：跳过 Vite 的 net use 探测
│   ├── esbuild-shim.mjs      # 纯 JS 的 esbuild 替身（仅实现 define 替换）
│   ├── esbuild-loader.mjs    # 模块解析钩子：把 esbuild 重定向到替身
│   ├── register-esbuild-shim.mjs
│   ├── build.mjs             # npm run build
│   ├── dev.mjs               # npm run dev
│   └── preview.mjs           # npm run preview
└── src/
    ├── main.js
    ├── App.vue
    ├── api/
    │   ├── http.js           # axios 实例、token 注入、401/403/404 统一处理
    │   └── index.js          # 接口函数
    ├── composables/usePagedList.js
    ├── layouts/AdminLayout.vue   # 深色侧栏 + 顶栏 + 用户菜单 + 健康检查
    ├── router/index.js           # 路由与登录守卫
    ├── components/
    │   ├── PageHeader.vue
    │   └── StatusPill.vue
    ├── views/
    │   ├── Login.vue             # 品牌左栏 + 登录卡片 + 演示账号
    │   ├── Dashboard.vue         # 指标卡 / CSS 趋势图 / 坐席 / 最近工单与会话
    │   ├── Knowledge.vue         # 知识库（搜索 + 分类概览 + 详情抽屉）
    │   ├── Orders.vue            # 工单（状态/优先级/关键词 + SLA 超时）
    │   ├── Sessions.vue          # 会话（状态筛选 + 生命周期时间线）
    │   ├── Agents.vue            # 坐席卡片 + 等待队列（等待时长每秒递增）
    │   ├── Users.vue             # 用户与角色
    │   └── Chat.vue              # AI 聊天（降级标记 / 快捷问题 / WS 状态）
    ├── styles/
    │   ├── index.css             # 设计变量 + Element Plus 主题覆盖
    │   └── list.css              # 列表页共享样式
    └── utils/dict.js             # 枚举字典、时间/评分/等待时长格式化
```

## 页面与路由

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录 | `POST /api/auth/login`，token + user 存 localStorage |
| `/dashboard` | 数据看板 | `GET /api/demo/overview` + `GET /api/health` |
| `/chat` | AI 聊天 | `POST /api/ai/chat`，`fallback=true` 显示「降级回复」标签 |
| `/sessions` | 会话管理 | `GET /api/demo/sessions` |
| `/orders` | 工单中心 | `GET /api/demo/workorders` |
| `/agents` | 坐席工作台 | `GET /api/demo/agents`、`GET /api/demo/waiting` |
| `/knowledge` | 知识库 | `GET /api/demo/kb/entries`、`GET /api/demo/categories` |
| `/users` | 用户管理 | `GET /api/demo/users` |

未登录访问受保护路由会跳转 `/login`；接口 401 会清除 token 并跳登录；403 提示无权限；未知路由重定向 `/dashboard`。

## 视觉规范

- 深色侧栏（`#0b1220 → #101a2e` 渐变）+ 浅灰内容区（`#f4f6fb`）
- 主色：青绿 `#14b8a6` + 靛蓝 `#4f46e5`（覆盖 Element Plus 默认蓝，见 `styles/index.css` 的 `--el-color-*`）
- 1440 宽优先，内容区最大 1440；窄屏（<1360 / <1100）自动降级排列
- 数字统一 `tabular-nums`，状态用自定义 `StatusPill` 彩色标签

## 关于构建环境（重要）

当前开发沙箱（Windows）**禁止 Node 以管道 stdio 创建子进程**，而 esbuild 的官方 JS API 必须启动常驻子进程，因此直接运行 `vite` CLI 会报：

```
Error: spawn EPERM
```

为绕开该限制，项目通过 `scripts/` 提供可复现的构建/启动脚本（不改 `node_modules`，不影响浏览器端代码）：

1. `scripts/bootstrap.mjs`：让 Vite 跳过 Windows 下 `exec('net use')` 的网络盘探测，走安全回退分支。
2. `scripts/esbuild-shim.mjs` + `esbuild-loader.mjs`：通过 Node `module.register()` 把 `esbuild` 模块重定向为纯 JS 替身，只实现 Vite `vite:define` 所需的 `transform`（`process.env.NODE_ENV`、`__VUE_*`、`import.meta.env.*` 替换）。
3. `configFile: false` + 内联配置：避免 Vite 用 esbuild 打包配置文件。
4. `esbuild: false` + `minify: false` + `cssMinify: false`：构建完全由 `@vue/compiler-sfc` 与 Rollup 完成，仅体积偏大（约 2.6 MB / gzip 0.59 MB），功能不受影响。

在**没有该限制的普通机器**上，也可以直接使用标准方式（本仓库同样支持）：

```powershell
npx vite build          # 需要能启动 esbuild 的环境
npx vite --port 5173    # 需要 vite.config.js（见下）
```

为保持单一配置来源，标准 `vite.config.js` 未保留；如需要请从 `vite.shared.mjs` 导出项自行生成。

## 已知问题 / 后续里程碑

- `/api/demo/*` 由后端并行实现，若返回 404，各页面会展示「接口未就绪」空态而不会报错崩溃。
- 知识库分类仅作为概览展示：契约中 `/api/demo/kb/entries` 只支持 `keyword` 筛选，暂未传 `categoryId`。
- 聊天页 WebSocket 仅用于展示连接状态与 20s PING 心跳，不参与业务；后端未启用 WS 时显示「WS 未连接」，不影响聊天。
- 转人工、工单流转、坐席接入等写操作按钮均标注「M4 开放」，点击仅提示。
- 当前构建关闭了压缩（原因见上）；如需更小体积，可在允许 esbuild 的环境里把 `vite.shared.mjs` 的 `minify` 改回 `'esbuild'`。
