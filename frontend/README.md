# NACAMS 门户前端

这是钟坤宇负责的 Vue 3 门户栏目实现，使用 Vite 构建，页面通过 `/api/v1` 公开接口读取数据。

## 启动

需要 Node.js 18+：

```powershell
cd frontend
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，`/api` 请求会代理到后端 `http://localhost:8080`。也可以通过 `VITE_API_BASE` 指定完整接口前缀。

## 页面

- `/`：首页动态摘要
- `/news`、`/news/:id`：动态消息列表与详情
- `/teaching`：课程与项目
- `/research`：科研项目
- `/achievements`：八类研究成果
- `/admissions`：四类招生信息
- `/cooperations`：国际合作
- `/teachers`、`/students`：师生公开展示及详情

动态详情中的图片、视频和附件由后端内容接口返回后渲染。下载专区、管理员内容维护、登录和图书借阅属于其他成员负责范围，本前端不重复实现。

## 构建

```powershell
npm run build
```
