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

- 开发期默认 **mock 开启**（`.env.development` 里 `VITE_USE_MOCK=1`）：后端未就绪时按接口规范的示例数据开发；联调时改成 `0` 并重启 dev server。
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
  router/index.js 全量路由表：首页/登录/关于（刘罡甫）；门户栏目页（钟坤宇）；/me /admin（杨峥，占位）
  layouts/PortalLayout.vue  门户公共布局（头部导航/登录区/页脚版权与三外链）
  components/     门户通用组件（钟坤宇）：PageShell / StateBlock / PaginationBar
  views/
    portal/       门户页面：HomeView、LoginView、AboutView（刘罡甫）；
                  NewsPage、NewsDetailPage、ContentPage、PeoplePage、PersonDetailPage（钟坤宇）
    common/       PlaceholderView（占位）、NotFoundView（404）
    （杨峥：/me、/admin 建议各自做子布局，如 views/me/、views/admin/）
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
