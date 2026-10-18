// 全站路由表（刘罡甫建骨架并做版本集成，2026-10-17）。
// 已实现：首页/登录页/关于我们（刘罡甫）；动态消息/详情、教学科研、研究成果、招生信息、国际合作、
// 师资/学生列表与详情（钟坤宇，移植入统一骨架）。
// 内部端：/me、/admin（杨峥，2026-10-18），复用统一认证和请求层。
import { createRouter, createWebHistory } from 'vue-router'
import PortalLayout from '@/layouts/PortalLayout.vue'
import WorkLayout from '@/layouts/WorkLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { canAccess } from '@/utils/internal'

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

  { path: '/me', component: WorkLayout, meta: { requiresAuth: true }, children: [
    { path: '', component: () => import('@/views/me/OverviewView.vue'), meta: { title: '工作概览' } },
    { path: 'profile', component: () => import('@/views/me/ProfileView.vue'), meta: { title: '个人档案' } },
    { path: 'books', component: () => import('@/views/me/BooksView.vue'), meta: { title: '图书目录' } },
    { path: 'borrows', component: () => import('@/views/me/BorrowsView.vue'), meta: { title: '我的借阅与诚信' } },
    { path: 'notifications', component: () => import('@/views/me/NotificationsView.vue'), meta: { title: '站内提醒' } },
    { path: 'downloads', component: () => import('@/views/me/DownloadsView.vue'), meta: { title: '下载专区' } },
    { path: 'help', component: () => import('@/views/me/HelpView.vue'), meta: { title: '用户帮助' } },
    { path: 'statistics', component: () => import('@/views/me/StatisticsView.vue'), meta: { title: '签到签出统计', access: 'statistics' } },
  ] },
  { path: '/admin', component: WorkLayout, meta: { requiresAuth: true }, children: [
    { path: '', redirect: () => useAuthStore().isAdmin ? '/admin/users' : useAuthStore().isLibrarian ? '/admin/library' : '/me' },
    { path: 'users', component: () => import('@/views/admin/UsersView.vue'), meta: { title: '用户管理', access: 'admin' } },
    { path: 'content', component: () => import('@/views/admin/ContentView.vue'), meta: { title: '内容管理', access: 'admin' } },
    { path: 'downloads', component: () => import('@/views/me/DownloadsView.vue'), props: { manage: true }, meta: { title: '下载资料管理', access: 'admin' } },
    { path: 'library', component: () => import('@/views/admin/LibraryView.vue'), meta: { title: '图书管理', access: 'librarian' } },
    { path: 'backups', component: () => import('@/views/admin/BackupsView.vue'), meta: { title: '备份与恢复', access: 'admin' } },
  ] },
  { path: '/forbidden', component: () => import('@/views/common/ForbiddenView.vue'), meta: { title: '无权访问' } },
  { path: '/session-error', component: () => import('@/views/common/SessionErrorView.vue'), meta: { title: '登录核验失败' } },

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

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !localStorage.getItem('sqa2.token')) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAuth) {
    const auth = useAuthStore()
    try { await auth.refresh() } catch (e) { return { path: e.code === 401 ? '/login' : '/session-error', query: { redirect: to.fullPath } } }
    if (!canAccess(auth.user, to.meta.access)) return '/forbidden'
  }
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 网络攻防对抗技术研究所管理系统`
    : '网络攻防对抗技术研究所管理系统'
})

export default router
