<script setup>
import { ref, onMounted, watch } from 'vue'
import { listContent } from '../api'
import PageShell from '../components/PageShell.vue'
import StateBlock from '../components/StateBlock.vue'
import PaginationBar from '../components/PaginationBar.vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

const route = useRoute(); const router = useRouter()
const categories = ['全部', '新闻动态', '通知通告', '学术活动']
const category = ref(route.query.category || '全部'); const keyword = ref(route.query.keyword || '')
const data = ref({ items: [], total: 0, page: 1, page_size: 20 }); const loading = ref(false); const error = ref('')
async function load(page = 1) {
  loading.value = true; error.value = ''
  try {
    const params = { page, page_size: 20, keyword: keyword.value }
    if (category.value !== '全部') params.category = category.value
    data.value = await listContent('news', params)
    router.replace({ query: { ...(category.value !== '全部' ? { category: category.value } : {}), ...(keyword.value ? { keyword: keyword.value } : {}) } })
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function search() { load(1) }
watch(() => route.query, () => { category.value = route.query.category || '全部'; keyword.value = route.query.keyword || '' }, { deep: true })
onMounted(() => load(1))
</script>

<template>
  <PageShell title="动态消息" subtitle="新闻动态、通知通告与学术活动">
    <div class="toolbar">
      <div class="tabs"><button v-for="item in categories" :key="item" :class="{ active: category === item }" @click="category = item; load(1)">{{ item }}</button></div>
      <form class="search" @submit.prevent="search"><input v-model="keyword" placeholder="搜索标题或正文"><button class="button button-dark">搜索</button></form>
    </div>
    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !data.items.length" empty-text="暂无该分类动态">
      <div class="content-list">
        <RouterLink v-for="item in data.items" :key="item.id" :to="`/news/${item.id}`" class="content-row">
          <div><span class="tag">{{ item.category }}</span><h2>{{ item.title }}</h2><p>{{ item.body || '暂无摘要' }}</p></div><time>{{ item.publishedAt }}</time>
        </RouterLink>
      </div>
    </StateBlock>
    <PaginationBar :page="data.page" :page-size="data.page_size" :total="data.total" @change="load" />
  </PageShell>
</template>
