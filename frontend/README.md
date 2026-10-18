# frontend —— SQA2 网络攻防对抗技术研究所管理系统（前端）

技术栈（分工卡 §1 定稿）：**Vue 3 ＋ Vite ＋ Vue Router ＋ Pinia ＋ Element Plus ＋ Axios**。
（2026-10-17 版本集成说明：本工程由组长建立统一骨架，钟坤宇的门户栏目页已并入本结构；
Element Plus/Pinia/Axios 为定稿技术栈必装项，工程配置以本文件所在 package.json 为准。）

## 快速开始

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 （/api 代理到本机 8080 后端）
npm run build      # 产出 dist/
```

- 开发期默认连接真实后端（`VITE_USE_MOCK=0`）。先按 `backend/README.md` 启动后端并创建账号，内部端需要真实认证。
- 仅独立演示原门户假数据时，可在被忽略的 `.env.development.local` 中设置 `VITE_USE_MOCK=1`；该旧 mock 不覆盖内部端，请勿将其结果当作真实借阅或用户操作。
- mock 只拦截**已注册**的接口（`src/api/mock.js`），没注册的照常请求真实后端。
- `src/api/portal.js` 是钟坤宇的 fetch 封装（公开页专用，同样遵守统一响应包约定），其页面 import 自此处。

## 目录结构与分工落位

```
src/
  api/            请求层（三人共用）
    http.js       axios 封装：统一响应包解包、401 跳登录、422 字段错误（ApiError.fields）、mock 开关
    mock.js       mock 注册表 + 演示账号 + 公开接口假数据（其他接口往这里 mockOn 追加）
    site.js       站点/首页/关于（后端 /site/info /home/summary /about）
    auth.js       登录/登出/当前用户
    portal.js     门户栏目公开接口 fetch 封装（钟坤宇：/news /teachers /students /achievements 等）
  stores/         Pinia：auth.js（登录态）、site.js（站点元数据缓存）
  router/index.js 全量路由表：首页/登录/关于（刘罡甫）；门户栏目页（钟坤宇）；/me /admin（杨峥）
  layouts/PortalLayout.vue  门户公共布局（头部导航/登录区/页脚版权与三外链）
  components/     门户通用组件（钟坤宇）：PageShell / StateBlock / PaginationBar
  views/
    portal/       门户页面：HomeView、LoginView、AboutView（刘罡甫）；
                  NewsPage、NewsDetailPage、ContentPage、PeoplePage、PersonDetailPage（钟坤宇）
    common/       PlaceholderView（占位）、NotFoundView（404）
    me/           注册用户工作台、档案、图书、借阅与诚信、提醒、下载、帮助、统计（杨峥）
    admin/        用户、内容、图书、备份恢复管理；下载管理复用 DownloadsView（杨峥）
  styles/
    main.css      全局主题（学术蓝，Element Plus 主色覆写）
    portal.css    门户栏目页样式（钟坤宇原 styles.css，全局类名）
```

## 约定（对齐《接口规范_SQA2_20261016.md》＝已冻结 v1.0）

1. **所有请求走 `src/api/`**，不要在组件里直接裸写 axios/fetch；返回值就是 `data`（统一响应包已解包）。
2. 错误处理：`code=422` 时 `e.fields` 是 `{字段名: 错误原因}`，在表单上逐字段提示（全局不弹窗）；其他错误 http.js 已统一 ElMessage 弹出。
3. 分页：请求 `?page=1&page_size=20`；响应 `data = {items, total, page, page_size}`。
4. 时间字段后端为 `publishedAt`（"YYYY-MM-DD HH:mm"）；`/site/info` 字段为 `name/englishName/logo/copyright/links{cs,grd,gms}`；`/home/summary` 为 `{news:[...], notificationCount}`——**新增代码以后端响应为准**，不确定先看后端 PortalController 或接口文档。
5. **B-07 隐私**：脱敏在服务端做，前端拿到什么显示什么，不要在前端"隐藏但渲染"敏感字段。
6. 登录态：`useAuthStore()`（token 持久化在 localStorage）；登录即签到、登出即签出由后端记录。
7. 样式：全局类名先查 `styles/portal.css` 与 `styles/main.css`，避免重名；门户栏目页优先复用 portal.css 既有类。

## 演示账号（mock 环境；真实账号由后端导入脚本生成）

| 用户名 | 密码 | 身份 |
|---|---|---|
| admin | Admin@123 | 系统管理员 |
| director | Bit@123456 | 所长（isDirector） |
| librarian | Bit@123456 | 图书管理教师（isLibrarian） |
| teacher / student | Bit@123456 | 全职教师 / 学生 |

## 备注

- `public/logo.svg` 为 AIGC 生成的占位 LOGO，终版 AIGC LOGO 定稿后替换该文件（或改后端 `app.site.logo` 配置）。
- 站名、外链、版权等站点元数据统一来自 `GET /site/info`（后端部署期配置），前端不要写死在业务组件里。

## 内部端使用与验证（杨峥，2026-10-18）

登录后进入 `/me`。用户账号统一由管理员创建，不提供自行注册。所长和图书管理教师是独立标记，系统管理员不会自动获得图书域权限；进入受保护路由会重新核验 `/auth/me`。

| 地址 | 功能 | 权限 |
|---|---|---|
| `/me`、`/me/profile` | 工作概览、档案、改密、本人照片检测与保存 | 已登录 |
| `/me/books`、`/me/borrows` | 目录查询、申请、待审/驳回/借出历史、只读诚信记录 | 已登录 |
| `/me/notifications`、`/me/downloads`、`/me/help` | 提醒已读、八类资料鉴权下载、手册与索引 | 已登录 |
| `/me/statistics` | 全员／名下学生签到签出 | 所长／全职教师 |
| `/admin/users` | 用户维护、停用、重置密码、CSV/XLSX 导入与逐行结果 | 系统管理员 |
| `/admin/content`、`/admin/downloads` | 门户内容、媒体、栏目导入、私有下载资料维护 | 系统管理员 |
| `/admin/library` | 图书维护与导入、申请审核、归还、违规确认、上限设置 | 图书管理教师 |
| `/admin/backups` | 备份列表、创建、下载、二次确认恢复 | 系统管理员 |

下载通过 Axios 携带 Bearer 令牌读取二进制响应，不把 token 放入 URL。完整本人档案只保存在内存，浏览器仅缓存基础身份字段。签出网络失败时保留会话供重试，改密及恢复成功后清除会话并重新登录。

```powershell
npm ci
npm test
npx playwright install chromium
npm run test:e2e
npm run build
```

已安装 Microsoft Edge 的 Windows 可用 `$env:PLAYWRIGHT_CHANNEL='msedge'` 运行浏览器测试。测试独占 `127.0.0.1:5174`，以受控 HTTP 夹具覆盖成功、业务冲突、字段错误和鉴权失败；这些测试验证界面与请求契约，不能替代 MySQL 部署联调或全部浏览器兼容验收。

测试结果与演示步骤见 `docs/内部端交付与验证记录_SQA2_杨峥_20261018.md`；对应需求、设计、用户手册和测试章节 DOCX 位于 `docs/交付文档/`，由陈彬统稿。
