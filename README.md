# 智客通 · 智能对话客服与工单管理系统

面向 B 端企业的智能客服 SaaS，对标网易七鱼：把「访客接入 → 机器人应答 → 转人工 → 坐席接待 → 工单流转 → 质检复盘 → 数据运营」串成一条完整链路，支持多租户售卖与自主运营。

后端 Spring Boot 2.7 + MyBatis-Plus + MySQL 8 + Redis 6 + WebSocket + JWT，前端 Vue3 + Element Plus，访客端为可嵌入的原生 H5。

![登录页](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/01-login.png)

## 能力概览

- **在线机器人**：知识库精准命中直答 → 参考知识增强生成 → 大模型自由生成 → 引导话术兜底四级应答；任务式多轮对话；未命中问题回流知识库运营。
- **知识库运营**：多级分类、相似问法独立管理、版本记录与回滚、AI 扩写相似问法、查重与冲突检测、命中/零命中健康度看板。
- **在线客服**：WebSocket 长连接、排队转人工、多策略会话分配（轮询 / 最小负载 / 技能组 / 熟客 / VIP）、一键转接、满意度评价。
- **坐席辅助**：访客与坐席双端输入联想、按诉求实时推荐答案（附引用与可信度，坐席确认后才对客）、进线摘要、服务小结、客户信息卡、快捷回复。
- **工单系统**：六状态状态机、表单与模板引擎、多级 SLA（首响 / 回复间隔 / 解决，支持停表与超时升级）、规则与自动化（自动分配 / 事件触发器 / 时间任务）。
- **智能质检**：语义、情感、服务规范、敏感词四类规则混合评分，定时全量扫描，人工复核与人机对比报表。
- **数据运营**：实时大屏（WebSocket 推送）、运营指标口径、告警规则与组长监听、客户之声 VOC 聚类与词云。
- **SaaS 能力**：`tenant_id` 多租户隔离（拦截器自动注入）、套餐额度与用量、平台租户运营端、操作审计。
- **商家接入**：JS 悬浮球 SDK、独立链接、开放 API（HMAC 签名 + 来源白名单 + 限流）三种形态。

## 界面一览

> 截图由 `scripts/capture-screenshots.mjs` 通过 Chrome DevTools Protocol 采集，与当前代码实现一致。
> 图片托管在 jsDelivr（`gcore.jsdelivr.net`），避免 GitHub raw 域名在部分网络下无法加载；若仍显示不出来，可直接打开 [docs/screenshots/readme](docs/screenshots/readme) 查看原图。

### 数据看板

汇总今日会话与工单、待接入、超时工单、在线坐席、知识条目与套餐用量，并给出近 7 日趋势与坐席实时状态。

![数据看板](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/02-admin-dashboard.png)

### 实时大屏

通过 WebSocket 推送排队人数、最长排队时长、在线坐席与 AI / 人工接待情况，支持全屏投屏与布局配置。

![实时大屏](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/07-admin-screen.png)

### 在线客服与坐席工作台

会话管理按状态、渠道与时间筛选会话，支持查看消息、转接、结束与满意度；坐席工作台聚合接待队列、会话详情、快捷回复与实时推荐答案。

| 会话管理 | 坐席工作台 |
|---|---|
| ![会话管理](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/03-admin-sessions.png) | ![坐席工作台](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/11-agent-workbench.png) |

### 知识库与工单

知识库支持多级分类、相似问法、命中测试与未命中转化；知识运营看板展示命中排行与健康度；工单中心提供六状态流转、SLA 计时、字段模板与流转日志。

| 知识库 | 知识运营 |
|---|---|
| ![知识库](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/04-admin-knowledge.png) | ![知识运营](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/17-admin-kb-ops.png) |

![工单中心](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/05-admin-orders.png)

### 智能质检与客户之声

质检按四类规则逐条打分并给出证据与建议，支持人工复核与人机对比；客户之声展示未命中问法的聚类主题、词云与渠道分布。

| 智能质检 | 客户之声 |
|---|---|
| ![智能质检](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/06-admin-quality.png) | ![客户之声](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/08-admin-voc.png) |

### 数据统计与告警

数据统计提供会话与工单量、转人工率、AI 自主解决率、首响与 3 分钟回复率等运营指标；告警中心集中查看规则触发、处理状态与处理时长。

| 数据统计 | 告警中心 |
|---|---|
| ![数据统计](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/15-admin-statistics.png) | ![告警中心](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/16-admin-alerts.png) |

### 平台运营、网页接入与访客端

平台运营端管理租户开通、套餐与额度水位；接入方式页维护 JS 悬浮球、独立链接与 API 三种形态，并提供实时预览；访客在自己网站通过悬浮球直接发起咨询。

| 租户与套餐 | 接入方式 |
|---|---|
| ![租户与套餐](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/10-platform-tenants.png) | ![接入方式](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/09-admin-embed.png) |

| 用户端 | 访客端悬浮球 |
|---|---|
| ![用户端](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/12-user-home.png) | ![访客端悬浮球](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/13-embed-widget.png) |

### 移动端

管理端与访客端在移动端自适应为单列布局。

![移动端](https://gcore.jsdelivr.net/gh/ABUGG-007/zhiketong@main/docs/screenshots/readme/14-mobile-dashboard.png)

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7、Spring Security + JWT、Spring WebSocket、Spring Scheduler |
| 持久层 | MyBatis-Plus 3.5、MySQL 8.0 |
| 缓存 | Redis 6（坐席状态、排队、会话上下文、限流、分布式锁） |
| AI | OpenAI 兼容协议 HTTP 调用（DeepSeek 等），管理端可配置，失败自动降级 |
| 前端 | Vue3 + Element Plus + Vite |
| 访客端 | 原生 H5 + WebSocket 悬浮球 SDK |

## 目录结构

```
zhiketong/
├── src/main/java/com/zhiketong/
│   ├── controller/ service/ mapper/ entity/ dto/ enums/
│   ├── ai/                  # 大模型接入与提示词
│   ├── websocket/           # WS 处理器与会话注册中心
│   ├── security/            # JWT、权限、租户上下文
│   ├── task/                # SLA、质检、告警、聚类等定时任务
│   └── config/ common/
├── src/main/resources/      # application.yml、停用词
├── frontend/                # Vue3 + Element Plus（admin / agent / user / platform 四套入口）
│   └── scripts/build.mjs    # 构建脚本（纯 JS，无需 esbuild 子进程）
├── sql/                     # 建表与增量脚本 01→20
├── docs/                    # 产品路线图、数据库设计、接口与 WS 协议、界面截图
├── scripts/                 # 冒烟测试与界面截图采集
├── serve-frontend.mjs       # 前端静态服务（brotli/gzip 预压缩 + /api、/ws 代理）
└── pom.xml
```

## 快速开始

### 1. 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 8（Spring Boot 2.7 编译目标） |
| Maven | 3.6+ |
| MySQL | 8.0+ |
| Redis | 6.x |
| Node.js | 18+ |

### 2. 初始化数据库

```powershell
$env:MYSQL_PWD='你的MySQL密码'
$mysql = 'mysql'   # 或 MySQL 安装目录下的 bin\mysql.exe
& $mysql -uroot --default-character-set=utf8mb4 -e "source sql/01_schema.sql"
& $mysql -uroot --default-character-set=utf8mb4 -e "source sql/02_data.sql"
# 依次执行增量脚本 03 → 20（新增表结构、索引与默认规则）
Get-ChildItem sql -Filter '*.sql' | Where-Object { $_.Name -match '^(0[3-9]|1[0-9]|20)_' } |
  Sort-Object Name | ForEach-Object { & $mysql -uroot --default-character-set=utf8mb4 -e "source sql/$($_.Name)" }
Remove-Item Env:MYSQL_PWD
```

> `01_schema.sql` 会重建同名表，仅用于开发环境；演示数据 `sql/demo/02_demo_data.sql` 默认不导入。

### 3. 启动 Redis 与后端

```powershell
# 本机 Redis 6 在 WSL 中（6380，密码 246790）；按实际环境调整 application.yml 或环境变量
wsl -d Ubuntu-22.04 -u root service redis-server start

# WSL 在无进程时会回收发行版（Redis 随之断开，健康检查显示 DEGRADED）：
# 开发期保持一个常驻的 WSL 会话即可，或把 Redis 部署在常驻服务器上
wsl -d Ubuntu-22.04 -- sleep infinity

mvn spring-boot:run
# 或：mvn clean package -DskipTests && java -jar target/zhiketong-server.jar
```

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/api/health` | 健康检查（MySQL / Redis） |
| `http://localhost:8080/swagger-ui.html` | 在线接口文档 |

数据库、Redis、JWT、大模型等配置均在 `src/main/resources/application.yml`，敏感项支持环境变量覆盖（`MYSQL_PASSWORD`、`REDIS_PORT`、`JWT_SECRET`、`DEEPSEEK_API_KEY` 等，见 `.env.example`）。大模型也可在管理端「大模型配置」中维护，保存即生效。

### 4. 启动前端

```powershell
cd frontend
npm install --cache .npm-cache
node scripts/build.mjs
cd ..
node serve-frontend.mjs
```

访问 `http://127.0.0.1:5173`。

### 5. 默认账号

密码均为 `123456`，首次登录后请修改。

| 账号 | 角色 | 入口 |
|------|------|------|
| admin | ADMIN | `/admin/dashboard` |
| agent01 | AGENT | `/agent/workbench` |
| user01 | USER | `/user/home` |
| platform01 | PLATFORM_ADMIN | `/platform/tenants` |

## 实时通信与接口

- WebSocket：`/ws/chat?token={jwt}`，握手阶段校验身份，坐席与访客共用连接，消息 JSON 含 `type`、`content`、`timestamp`、`sessionId`。
- REST：统一返回 `{code, message, data}`，分页统一 `PageResult`，接口前缀见 `docs/接口与WebSocket协议.md`。

## 文档

- [docs/产品推进路线图.md](docs/产品推进路线图.md) —— 对标清单、阶段排期与取舍
- [docs/数据库设计.md](docs/数据库设计.md) —— 表结构与 Redis 键设计
- [docs/接口与WebSocket协议.md](docs/接口与WebSocket协议.md) —— REST 与实时协议
- [docs/UI优化说明.md](docs/UI优化说明.md) —— 设计令牌与响应式规则
- [项目开发文档.md](项目开发文档.md) —— 架构、规范与验收说明

## 工程说明

- 前端构建关闭 esbuild（沙箱下无法启动其子进程），改用纯 JS 的 terser 压缩、lightningcss 压缩 CSS，并按 Vue / Element Plus / axios / 业务视图分包，路由懒加载；产物由 `serve-frontend.mjs` 预压缩（brotli + gzip）并返回长期缓存头。
- 后端默认 info 日志级别、SQL 日志走 slf4j；需要排查时用 `LOG_LEVEL_SQL=debug`、`LOG_LEVEL_APP=debug` 打开。
- 界面截图可用 `node scripts/capture-screenshots.mjs docs/screenshots/readme` 重新采集（需前后端已启动），避免文档截图与实现脱节。
