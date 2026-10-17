// 全站路由表（刘罡甫建骨架并做版本集成，2026-10-17）。
// 已实现：首页/登录页/关于我们（刘罡甫）；动态消息/详情、教学科研、研究成果、招生信息、国际合作、
// 师资/学生列表与详情（钟坤宇，移植入统一骨架）。
// 仍为占位：/me、/admin（杨峥）——替换 PlaceholderView 为自己的组件即可，路径不要改。
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

      // —— 门户栏目（钟坤宇）——

      { path: 'news', name: 'news', component: () => import('@/views/portal/NewsPage.vue'), meta: { title: '动态消息', nav: 'news' } },
      { path: 'news/:id', name: 'news-detail', component: () => import('@/views/portal/NewsDetailPage.vue'), meta: { title: '动态详情' } },
      { path: 'teachers', name: 'teachers', component: () => import('@/views/portal/PeoplePage.vue'), props: { kind: 'teachers' }, meta: { title: '师资队伍', nav: 'about' } },
      { path: 'teachers/:id', name: 'teacher-detail', component: () => import('@/views/portal/PersonDetailPage.vue'), props: { kind: 'teachers' }, meta: { title: '教师详情' } },
      { path: 'students', name: 'students', component: () => import('@/views/portal/PeoplePage.vue'), props: { kind: 'students' }, meta: { title: '学生信息', nav: 'about' } },
      { path: 'students/:id', name: 'student-detail', component: () => import('@/views/portal/PersonDetailPage.vue'), props: { kind: 'students' }, meta: { title: '学生详情' } },
      { path: 'teaching', name: 'teaching', component: () => import('@/views/portal/ContentPage.vue'), props: { mode: 'teaching' }, meta: { title: '教学科研', nav: 'teaching' } },
      { path: 'research', name: 'research', component: () => import('@/views/portal/ContentPage.vue'), props: { mode: 'research' }, meta: { title: '科研项目', nav: 'teaching' } },
      { path: 'achievements', name: 'achievements', component: () => import('@/views/portal/ContentPage.vue'), props: { mode: 'achievements' }, meta: { title: '研究成果', nav: 'achievements' } },
      { path: 'admissions', name: 'admissions', component: () => import('@/views/portal/ContentPage.vue'), props: { mode: 'admissions' }, meta: { title: '招生信息', nav: 'admissions' } },
      { path: 'cooperations', name: 'cooperations', component: () => import('@/views/portal/ContentPage.vue'), props: { mode: 'cooperations' }, meta: { title: '国际合作', nav: 'cooperations' } },
    ],
  },

  // —— 内部端（杨峥）：注册用户端 + 管理员端，建议各自再做子布局 ——

  { path: '/me', component: PlaceholderView, meta: { title: '内部管理', owner: '杨峥', desc: '签到签出/图书借阅/下载专区/用户帮助/站内提醒（可做 /me 子路由，接口见规范 §3.1/3.3/3.4）', requiresAuth: true } },
  { path: '/admin', component: PlaceholderView, meta: { title: '管理后台', owner: '杨峥', desc: '用户管理/内容管理/图书管理/照片检测/备份恢复（可做 /admin 子路由，接口见规范 §3.3/3.5/3.6/3.8）', requiresAuth: true } },

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
