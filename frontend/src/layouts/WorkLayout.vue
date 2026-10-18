<template>
  <div class="work-app">
    <aside class="work-sidebar">
      <router-link to="/me" class="work-brand">
        <img :src="site.site.logoUrl" alt="研究所标志" />
        <span>
          研究所工作台
          <small>网络攻防对抗技术研究所</small>
        </span>
      </router-link>
      <div>
        <p class="work-nav-caption">个人工作</p>
        <nav class="work-nav" aria-label="注册用户菜单">
          <router-link v-for="item in personal" :key="item[0]" :to="item[0]">
            {{ item[1] }}
            <el-tag
              v-if="item[0] === '/me/notifications' && unread"
              size="small"
              type="warning"
            >
              {{ unread }}
            </el-tag>
          </router-link>
        </nav>
      </div>
      <div v-if="management.length">
        <p class="work-nav-caption">管理与统计</p>
        <nav class="work-nav" aria-label="管理菜单">
          <router-link v-for="item in management" :key="item[0]" :to="item[0]">
            {{ item[1] }}
          </router-link>
        </nav>
      </div>
      <div class="work-side-bottom">
        登录自动签到 · 退出自动签出
        <br />
        借阅与提醒以系统记录为准
      </div>
    </aside>
    <div class="work-main">
      <header class="work-topbar">
        <div class="work-mobile-title">{{ $route.meta.title }}</div>
        <div class="work-actions">
          <router-link to="/">返回门户</router-link>
          <el-divider direction="vertical" />
          <span>{{ auth.user?.name || auth.user?.username }}</span>
          <el-tag size="small">
            {{
              auth.isAdmin
                ? '系统管理员'
                : auth.isLibrarian
                  ? '图书管理教师'
                  : auth.isDirector
                    ? '所长'
                    : '注册用户'
            }}
          </el-tag>
          <el-button size="small" :loading="signout.busy.value" @click="logout">
            退出并签出
          </el-button>
        </div>
      </header>
      <main class="work-main-content"><router-view /></main>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSiteStore } from '@/stores/site'
import { notifications } from '@/api/internal'
import { useTask } from '@/composables/useInternal'
import { canAccess } from '@/utils/internal'
import '@/styles/internal.css'
const auth = useAuthStore(),
  site = useSiteStore(),
  router = useRouter(),
  route = useRoute()
const unread = ref(0),
  signout = useTask()
const personal = [
  ['/me', '工作概览'],
  ['/me/profile', '个人档案'],
  ['/me/books', '图书目录'],
  ['/me/borrows', '我的借阅与诚信'],
  ['/me/notifications', '站内提醒'],
  ['/me/downloads', '下载专区'],
  ['/me/help', '用户帮助'],
]
const management = computed(() => [
  ...(canAccess(auth.user, 'statistics')
    ? [['/me/statistics', '签到签出统计']]
    : []),
  ...(auth.isLibrarian ? [['/admin/library', '图书管理']] : []),
  ...(auth.isAdmin
    ? [
        ['/admin/users', '用户管理'],
        ['/admin/content', '内容管理'],
        ['/admin/downloads', '下载资料管理'],
        ['/admin/backups', '备份与恢复'],
      ]
    : []),
])
let timer
async function refreshUnread() {
  if (document.hidden) return
  try {
    unread.value = (await notifications({ page: 1, page_size: 1 })).unread || 0
  } catch {
    /* 页面本身提供失败重试，轮询不抛未处理异常 */
  }
}
onMounted(() => {
  site.load()
  refreshUnread()
  timer = setInterval(refreshUnread, 60000)
})
onUnmounted(() => clearInterval(timer))
watch(() => route.path, refreshUnread)
async function logout() {
  const ok = await signout.run(() => auth.logout(), '已退出并签出')
  if (ok) router.replace('/login')
}
</script>
