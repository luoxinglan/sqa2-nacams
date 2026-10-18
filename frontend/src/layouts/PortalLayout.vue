<template>
  <div class="portal">
    <!-- 顶部：LOGO + 研究所中英文名 + 栏目导航 + 登录区 -->
    <header class="portal-header">
      <div class="page-container header-inner">
        <router-link to="/" class="brand">
          <img :src="site.logoUrl" alt="研究所LOGO" class="brand-logo" />
          <span class="brand-names">
            <span class="brand-zh">{{ site.nameZh }}</span>
            <span class="brand-en">{{ site.nameEn }}</span>
          </span>
        </router-link>

        <el-menu mode="horizontal" router :default-active="activeNav" class="portal-menu" :ellipsis="false">
          <el-menu-item index="home" route="/">首页</el-menu-item>
          <el-menu-item index="about" route="/about">关于我们</el-menu-item>
          <el-menu-item index="news" route="/news">动态消息</el-menu-item>
          <el-menu-item index="teaching" route="/teaching">教学科研</el-menu-item>
          <el-menu-item index="achievements" route="/achievements">研究成果</el-menu-item>
          <el-menu-item index="admissions" route="/admissions">招生信息</el-menu-item>
          <el-menu-item index="cooperations" route="/cooperations">国际合作</el-menu-item>
          <el-sub-menu v-if="auth.isLoggedIn" index="internal"><template #title>内部服务</template><el-menu-item index="me" route="/me">内部管理</el-menu-item><el-menu-item index="downloads" route="/me/downloads">下载专区</el-menu-item><el-menu-item index="help" route="/me/help">用户帮助</el-menu-item></el-sub-menu>
        </el-menu>

        <div class="auth-area">
          <template v-if="auth.isLoggedIn">
            <el-dropdown @command="onUserCommand">
              <span class="user-chip">
                <el-icon><User /></el-icon>
                {{ auth.user?.name || auth.user?.username }}
                <el-tag v-if="auth.isAdmin" size="small" type="danger" effect="plain">管理员</el-tag>
                <el-tag v-else-if="auth.isDirector" size="small" type="warning" effect="plain">所长</el-tag>
                <el-tag v-else-if="auth.isLibrarian" size="small" type="success" effect="plain">图书管理</el-tag>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="me">内部管理</el-dropdown-item>
                  <el-dropdown-item v-if="auth.isAdmin" command="admin">管理后台</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" plain @click="$router.push('/login')">
            <el-icon style="margin-right: 4px"><User /></el-icon>登录
          </el-button>
        </div>
      </div>
    </header>

    <main class="portal-main">
      <router-view />
    </main>

    <!-- 页脚：版权信息 + 三外链 + 地址（首页版权要求由页脚全站体现） -->
    <footer class="portal-footer">
      <div class="page-container footer-inner">
        <div class="footer-links">
          <span class="footer-label">友情链接：</span>
          <a v-for="link in site.links" :key="link.url" :href="link.url" target="_blank" rel="noopener">
            {{ link.name }}
          </a>
        </div>
        <p class="footer-copyright">{{ site.copyright }}</p>
        <p class="footer-addr">北京市海淀区中关村南大街 5 号 · 邮编 100081 · 北京理工大学</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
// 门户公共布局（刘罡甫）。登录后导航栏增加“内部管理”入口（需求 §3.2：登录后栏目变更为 …+内部管理）
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User } from '@element-plus/icons-vue'
import { useSiteStore } from '@/stores/site'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()
const auth = useAuthStore()
const site = computed(() => siteStore.site)

onMounted(() => siteStore.load())

const activeNav = computed(() => route.meta.nav || 'home')

async function onUserCommand(cmd) {
  if (cmd === 'logout') {
    try { await auth.logout(); router.push('/') } catch { /* 请求层提示失败，保留会话供重试 */ }
  } else if (cmd === 'me') {
    router.push('/me')
  } else if (cmd === 'admin') {
    router.push('/admin')
  }
}
</script>

<style scoped>
.portal {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.portal-header {
  background: #fff;
  border-bottom: 1px solid #e5e9f2;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 28px;
  height: 72px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.brand-logo {
  width: 44px;
  height: 44px;
}

.brand-names {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-zh {
  font-size: 18px;
  font-weight: 700;
  color: var(--nacams-navy);
  letter-spacing: 1px;
}

.brand-en {
  font-size: 11px;
  color: var(--nacams-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.portal-menu {
  flex: 1;
  border-bottom: none;
  justify-content: center;
}

.portal-menu :deep(.el-menu-item) {
  font-size: 15px;
}

.auth-area {
  flex-shrink: 0;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--nacams-text);
  font-size: 14px;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid #e5e9f2;
  background: #f8fafd;
}

.portal-main {
  flex: 1;
  padding: 24px 0 40px;
}

.portal-footer {
  background: var(--nacams-navy);
  color: #c9d6ec;
  padding: 22px 0;
  font-size: 13px;
}

.footer-inner p {
  margin: 6px 0;
}

.footer-links {
  display: flex;
  gap: 18px;
  align-items: center;
  flex-wrap: wrap;
}

.footer-links a {
  color: #c9d6ec;
}

.footer-links a:hover {
  color: #fff;
}

.footer-label {
  opacity: 0.75;
}

.footer-copyright {
  margin-top: 10px;
}

.footer-addr {
  opacity: 0.75;
}

@media (max-width: 1280px) {
  .header-inner { height: auto; min-height: 72px; flex-wrap: wrap; gap: 12px; padding-top: 12px; padding-bottom: 12px; }
  .portal-menu { order: 3; flex: 0 0 100%; justify-content: flex-start; overflow-x: auto; }
  .auth-area { margin-left: auto; }
}
@media (max-width: 600px) {
  .brand-zh { font-size: 16px; letter-spacing: 0; }
  .brand-en { max-width: 260px; font-size: 10px; }
}
</style>
