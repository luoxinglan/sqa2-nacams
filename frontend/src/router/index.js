// 全站路由表（刘罡甫建骨架，2026-10-17）。
// 已实现：首页/登录页/关于我们（刘罡甫）；
// 占位页带 meta.owner 标注负责人——把 PlaceholderView 换成自己的组件即可，路由路径不要改。
import { createRouter, createWebHistory } from 'vue-router'
import PortalLayout from '@/layouts/PortalLayout.vue'
import PlaceholderView from '@/views/common/PlaceholderView.vue'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/portal/LoginView.vue'), meta: { title: '登录' } },

  {
    path: '/',
    component: PortalLayout,
    children: [
      { path: '', name: 'home', component: () => import('@/views/portal/HomeView.vue'), meta: { title: '首页', nav: 'home' } },
      { path: 'about', name: 'about', component: () => import('@/views/portal/AboutView.vue'), meta: { title: '关于我们', nav: 'about' } },

      // —— 门户栏目（钟坤宇）：替换组件即可 ——

      { path: 'news', name: 'news', component: PlaceholderView, meta: { title: '动态消息', owner: '钟坤宇', desc: '新闻动态 / 通知通告 / 学术活动 三子栏目（GET /news、/news/{id}）', nav: 'news' } },
      { path: 'news/:id', name: 'news-detail', component: PlaceholderView, meta: { title: '动态详情', owner: '钟坤宇', desc: '详情＋图片/流媒体/附件（GET /news/{id}）' } },
      { path: 'teachers', name: 'teachers', component: PlaceholderView, meta: { title: '师资队伍', owner: '钟坤宇', desc: '全职/兼职教师（GET /teachers，B-07 脱敏）', nav: 'about' } },
      { path: 'students', name: 'students', component: PlaceholderView, meta: { title: '学生信息', owner: '钟坤宇', desc: '在读/毕业学生（GET /students，B-07 脱敏）', nav: 'about' } },
      { path: 'teaching', name: 'teaching', component: PlaceholderView, meta: { title: '教学科研', owner: '钟坤宇', desc: '课程信息 / 项目信息（GET /courses、/projects）', nav: 'teaching' } },
      { path: 'achievements', name: 'achievements', component: PlaceholderView, meta: { title: '研究成果', owner: '钟坤宇', desc: '教学 4 类 + 科研 4 类表格（GET /achievements）', nav: 'achievements' } },
      { path: 'admissions', name: 'admissions', component: PlaceholderView, meta: { title: '招生信息', owner: '钟坤宇', desc: '本科/硕士/博士/留学生（GET /admissions）', nav: 'admissions' } },
      { path: 'cooperations', name: 'cooperations', component: PlaceholderView, meta: { title: '国际合作', owner: '钟坤宇', desc: '合作表格（GET /cooperations）', nav: 'cooperations' } },
    ],
  },

  // —— 内部端（杨峥）：注册用户端 + 管理员端，建议各自再做子布局 ——

  { path: '/me', component: PlaceholderView, meta: { title: '内部管理', owner: '杨峥', desc: '签到签出/图书借阅/下载专区/用户帮助（可做 /me 子路由）', requiresAuth: true } },
  { path: '/admin', component: PlaceholderView, meta: { title: '管理后台', owner: '杨峥', desc: '用户管理/内容管理/图书管理/照片检测/备份恢复（可做 /admin 子路由）', requiresAuth: true } },

  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/common/NotFoundView.vue'), meta: { title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (to.hash) return { el: to.hash, top: 80 }
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !localStorage.getItem('sqa2.token')) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 网络攻防对抗技术研究所管理系统`
    : '网络攻防对抗技术研究所管理系统'
})

export default router
