<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { getSiteInfo } from './api'

const site = ref({ name: '网络攻防对抗技术研究所', englishName: '', logo: '', copyright: '' })
const siteError = ref('')

onMounted(async () => {
  try {
    site.value = { ...site.value, ...(await getSiteInfo()) }
  } catch (error) {
    siteError.value = error.message
  }
})
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="container header-inner">
        <RouterLink class="brand" to="/">
          <img v-if="site.logo" :src="site.logo" alt="研究所 LOGO" class="brand-logo">
          <span class="brand-mark" aria-hidden="true">盾</span>
          <span>
            <strong>{{ site.name }}</strong>
            <small>{{ site.englishName || 'Research Institute Portal' }}</small>
          </span>
        </RouterLink>
        <nav class="main-nav" aria-label="主导航">
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/news">动态消息</RouterLink>
          <RouterLink to="/teaching">教学科研</RouterLink>
          <RouterLink to="/achievements">研究成果</RouterLink>
          <RouterLink to="/admissions">招生信息</RouterLink>
          <RouterLink to="/cooperations">国际合作</RouterLink>
          <RouterLink to="/teachers">师生风采</RouterLink>
        </nav>
      </div>
    </header>
    <p v-if="siteError" class="container global-notice">站点配置暂时无法加载，已使用基础名称：{{ siteError }}</p>
    <main><RouterView /></main>
    <footer class="site-footer">
      <div class="container footer-inner">
        <span>{{ site.copyright || '网络攻防对抗技术研究所 · 门户系统' }}</span>
        <span class="footer-links">
          <a href="https://cs.bit.edu.cn" target="_blank" rel="noreferrer">计算机学院</a>
          <a href="https://grd.bit.edu.cn" target="_blank" rel="noreferrer">研究生院</a>
          <a href="https://gms.bit.edu.cn" target="_blank" rel="noreferrer">研究生管理系统</a>
        </span>
      </div>
    </footer>
  </div>
</template>
