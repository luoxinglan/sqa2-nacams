<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { getNews, mediaUrl } from '../api'
import PageShell from '../components/PageShell.vue'
import StateBlock from '../components/StateBlock.vue'

const route = useRoute(); const item = ref(null); const loading = ref(true); const error = ref('')
onMounted(async () => { try { item.value = await getNews(route.params.id) } catch (e) { error.value = e.message } finally { loading.value = false } })
function values(value) { return Array.isArray(value) ? value : value ? [value] : [] }
</script>

<template>
  <PageShell title="动态详情">
    <RouterLink class="back-link" to="/news">← 返回动态列表</RouterLink>
    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !item" empty-text="动态不存在">
      <article class="detail-card">
        <span class="tag">{{ item.category }}</span><h2>{{ item.title }}</h2><p class="detail-meta">{{ item.author || '研究所' }} · {{ item.publishedAt }}</p>
        <div class="detail-body" v-html="item.body || '暂无正文'"></div>
        <div v-if="values(item.images).length" class="media-gallery"><img v-for="image in values(item.images)" :key="image" :src="mediaUrl(image)" alt="动态配图"></div>
        <div v-if="values(item.videos).length" class="media-list"><a v-for="video in values(item.videos)" :key="video" :href="mediaUrl(video)" target="_blank" rel="noreferrer">打开视频</a></div>
        <div v-if="values(item.attachments).length" class="attachment-list"><a v-for="file in values(item.attachments)" :key="file" :href="mediaUrl(file)" target="_blank" rel="noreferrer">下载附件</a></div>
      </article>
    </StateBlock>
  </PageShell>
</template>
