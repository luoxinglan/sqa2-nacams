<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { getHomeSummary } from '../api'
import StateBlock from '../components/StateBlock.vue'

const data = ref(null)
const loading = ref(true)
const error = ref('')
onMounted(async () => {
  try { data.value = await getHomeSummary() } catch (e) { error.value = e.message } finally { loading.value = false }
})
</script>

<template>
  <section class="hero">
    <div class="container hero-content">
      <p class="eyebrow">NETWORK ATTACK & DEFENSE</p>
      <h1>网络攻防对抗<br><em>技术研究所</em></h1>
      <p class="hero-copy">汇聚教学、科研与人才培养资源，探索可信、安全、可持续的网络空间技术。</p>
      <RouterLink class="button button-primary" to="/news">浏览研究所动态</RouterLink>
    </div>
    <div class="hero-orbit" aria-hidden="true"><span></span><span></span><span></span></div>
  </section>
  <section class="container home-section">
    <div class="section-title"><div><p class="eyebrow">LATEST UPDATES</p><h2>最新动态</h2></div><RouterLink to="/news">查看全部 →</RouterLink></div>
    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !(data?.news?.length)" empty-text="暂无最新动态">
      <div class="news-grid">
        <RouterLink v-for="item in data.news" :key="item.id" class="news-card" :to="`/news/${item.id}`">
          <span>{{ item.category || '研究所动态' }}</span><h3>{{ item.title }}</h3>
          <p>{{ item.body || '点击查看动态详情' }}</p><time>{{ item.publishedAt }}</time>
        </RouterLink>
      </div>
    </StateBlock>
  </section>
  <section class="container quick-links">
    <RouterLink to="/teaching"><strong>教学科研</strong><span>课程与项目</span></RouterLink>
    <RouterLink to="/achievements"><strong>研究成果</strong><span>教学与科研成果</span></RouterLink>
    <RouterLink to="/teachers"><strong>师生风采</strong><span>人才培养与团队</span></RouterLink>
  </section>
</template>
