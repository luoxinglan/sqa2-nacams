<template>
  <div class="page-container home">
    <!-- 顶部横幅：LOGO + 研究所中英文名（需求 §3.1.2 首页标识信息） -->
    <section class="hero">
      <div class="hero-inner">
        <img :src="site.logoUrl" alt="研究所LOGO" class="hero-logo" />
        <div class="hero-text">
          <h1 class="hero-zh">{{ site.nameZh }}</h1>
          <p class="hero-en">{{ site.nameEn }}</p>
          <div class="hero-chips">
            <span v-for="d in DIRECTIONS" :key="d" class="hero-chip">{{ d }}</span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button type="primary" @click="$router.push('/about')">了解研究所</el-button>
          <el-button plain class="hero-btn-light" @click="$router.push('/about#contact')">联系我们</el-button>
        </div>
      </div>
    </section>

    <!-- 动态消息：实时更新（每分钟轮询 + 窗口聚焦即刷新） -->
    <section class="portal-section news-section">
      <div class="news-head">
        <h2 class="section-title">动态消息</h2>
        <div class="news-meta">
          <el-tooltip content="最新动态每分钟自动刷新，切换回本窗口时也会立即刷新" placement="top">
            <span class="live-hint"><el-icon><Refresh /></el-icon> 实时更新 · {{ lastRefresh || '加载中' }}</span>
          </el-tooltip>
          <router-link v-if="summary && summary.noticeCount > 0" to="/news" class="notice-count">
            <el-tag type="warning" effect="light" round>通知通告 {{ summary.noticeCount }} 条</el-tag>
          </router-link>
          <router-link to="/news" class="more-link">更多 &gt;&gt;</router-link>
        </div>
      </div>

      <el-skeleton v-if="!summary" :rows="5" animated style="padding: 8px 0" />
      <ul v-else class="news-list" v-loading="refreshing">
        <li v-for="item in summary.latestNews" :key="item.id">
          <router-link :to="`/news/${item.id}`" class="news-item">
            <el-tag :type="CATEGORY_STYLE[item.category] || 'info'" size="small" effect="plain">{{ item.category }}</el-tag>
            <span class="news-title">{{ item.title }}</span>
            <span class="news-info">{{ item.author }}</span>
            <span class="news-info">{{ item.publishTime }}</span>
          </router-link>
        </li>
      </ul>
    </section>

    <!-- 研究所简介摘要（全文在“关于我们”，后台可更新） -->
    <section class="portal-section">
      <h2 class="section-title">研究所简介</h2>
      <p class="intro-excerpt">{{ introExcerpt }}……</p>
      <div style="text-align: right">
        <el-button text type="primary" @click="$router.push('/about')">
          查看完整简介 <el-icon style="margin-left: 2px"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </section>

    <!-- 友情链接：计算机学院 / 研究生院 / 研究生管理系统（需求 §3.1.2 三外链） -->
    <section class="portal-section">
      <h2 class="section-title">友情链接</h2>
      <div class="link-cards">
        <a v-for="link in site.links" :key="link.url" :href="link.url" target="_blank" rel="noopener" class="link-card">
          <el-icon :size="26" color="#1b4e9b"><Link /></el-icon>
          <span class="link-name">{{ link.name }}</span>
          <span class="link-url">{{ link.url.replace('http://', '') }}</span>
        </a>
      </div>
    </section>

    <!-- 可选：照片墙/流媒体位（需求“可考虑设置”，如做由钟坤宇的门户栏目统一提供素材后接入）
    <section class="portal-section"><h2 class="section-title">照片墙</h2></section> -->
  </div>
</template>

<script setup>
// 门户首页（刘罡甫）——需求框架 §3.1.2：
// ① AIGC LOGO＋中英文名＋版权（版权在页脚全站展示）；② 动态消息实时变更；
// ③ 版式体现研究方向与学术严谨性；④ 计算机学院/研究生院/研究生管理系统三外链。
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Refresh, ArrowRight, Link } from '@element-plus/icons-vue'
import { getAbout, getHomeSummary } from '@/api/site'
import { useSiteStore } from '@/stores/site'

const DIRECTIONS = ['网络攻防对抗', '网络靶场与安全评测', '漏洞挖掘与利用', '复杂网络安全']
const CATEGORY_STYLE = { 新闻动态: 'primary', 通知通告: 'warning', 学术活动: 'success' }
const REFRESH_INTERVAL = 60_000

const siteStore = useSiteStore()
const site = computed(() => siteStore.site)

const summary = ref(null)
const lastRefresh = ref('')
const refreshing = ref(false)
const introHtml = ref('')

async function loadSummary() {
  refreshing.value = true
  try {
    summary.value = await getHomeSummary()
    lastRefresh.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } finally {
    refreshing.value = false
  }
}

function onFocus() {
  loadSummary()
}

let timer = null
onMounted(() => {
  siteStore.load()
  loadSummary()
  getAbout().then((d) => (introHtml.value = d.intro || '')).catch(() => {})
  timer = setInterval(loadSummary, REFRESH_INTERVAL)
  window.addEventListener('focus', onFocus)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('focus', onFocus)
})

const introExcerpt = computed(() =>
  (introHtml.value || '').replace(/<[^>]+>/g, '').trim().slice(0, 130)
)
</script>

<style scoped>
.hero {
  border-radius: 12px;
  margin-bottom: 24px;
  color: #fff;
  background:
    radial-gradient(ellipse at 85% -20%, rgba(43, 156, 201, 0.35), transparent 55%),
    linear-gradient(135deg, #0c2a5e 0%, #16418f 60%, #1b4e9b 100%);
  box-shadow: 0 6px 24px rgba(12, 42, 94, 0.35);
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(0deg, transparent 0 39px, rgba(255, 255, 255, 0.04) 39px 40px),
    repeating-linear-gradient(90deg, transparent 0 39px, rgba(255, 255, 255, 0.04) 39px 40px);
  pointer-events: none;
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 26px;
  padding: 46px 44px;
}

.hero-logo {
  width: 88px;
  height: 88px;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.3));
}

.hero-text {
  flex: 1;
}

.hero-zh {
  margin: 0 0 6px;
  font-size: 34px;
  letter-spacing: 3px;
}

.hero-en {
  margin: 0 0 14px;
  font-size: 13px;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  color: rgba(226, 238, 255, 0.85);
}

.hero-chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-chip {
  font-size: 13px;
  padding: 4px 12px;
  border: 1px solid rgba(159, 216, 255, 0.5);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
}

.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hero-btn-light {
  --el-button-text-color: #e6efff;
  --el-button-border-color: rgba(159, 216, 255, 0.6);
  --el-button-hover-text-color: #fff;
  --el-button-hover-border-color: #9fd8ff;
  --el-button-bg-color: transparent;
  background: transparent;
}

.news-section {
  margin-bottom: 24px;
}

.news-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 6px;
}

.news-head .section-title {
  margin-bottom: 0;
}

.news-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
}

.live-hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--nacams-cyan);
  cursor: help;
}

.notice-count {
  text-decoration: none;
}

.more-link {
  color: var(--nacams-muted);
}

.more-link:hover {
  color: var(--el-color-primary);
}

.news-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.news-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 6px;
  border-bottom: 1px dashed #edf0f6;
  color: var(--nacams-text);
}

.news-item:hover .news-title {
  color: var(--el-color-primary);
}

.news-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.news-info {
  color: var(--nacams-muted);
  font-size: 13px;
  flex-shrink: 0;
}

.intro-excerpt {
  margin: 0 0 8px;
  line-height: 1.9;
  color: #374151;
}

.link-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.link-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 22px 16px;
  border: 1px solid #e5e9f2;
  border-radius: 10px;
  color: var(--nacams-text);
  transition: all 0.2s;
}

.link-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 16px rgba(27, 78, 155, 0.12);
  transform: translateY(-2px);
}

.link-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--nacams-navy);
}

.link-url {
  font-size: 12px;
  color: var(--nacams-muted);
}
</style>
