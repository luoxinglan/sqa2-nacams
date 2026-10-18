<template>
  <WorkPage
    title="用户帮助"
    description="在线阅读使用手册，或按标题和正文检索操作指引。"
  >
    <div class="work-card">
      <form class="work-toolbar" @submit.prevent="load">
        <el-input
          v-model="keyword"
          placeholder="输入帮助关键词"
          aria-label="帮助关键词"
          clearable
        />
        <el-button
          native-type="submit"
          type="primary"
          :loading="task.busy.value"
        >
          查询索引
        </el-button>
        <el-button @click="showAll">全部手册</el-button>
      </form>
      <LoadError :message="task.error.value" @retry="load" />
      <template v-if="data">
        <div v-if="data.keyword">
          <p class="work-muted">
            “{{ data.keyword }}” 的查询结果：{{ data.matches.length }} 条
          </p>
          <el-empty
            v-if="!data.matches.length"
            description="没有匹配条目，请更换关键词"
          />
          <article
            v-for="(entry, i) in data.matches"
            :key="i"
            class="work-notice"
          >
            <div>
              <h2>{{ entry.title }}</h2>
              <p class="work-prose">{{ entry.body }}</p>
            </div>
          </article>
        </div>
        <div v-else class="work-help-index">
          <nav aria-label="手册目录">
            <section v-for="group in data.tree" :key="group.category">
              <h2>{{ group.category }}</h2>
              <button
                v-for="entry in group.items"
                :key="entry.title"
                @click="selected = entry"
              >
                {{ entry.title }}
              </button>
            </section>
          </nav>
          <article v-if="selected">
            <h2>{{ selected.title }}</h2>
            <p class="work-prose">{{ selected.body }}</p>
            <p v-if="selected.title.includes('照片')" class="work-muted">
              大小及宽高比为强制要求，未通过时无法保存；背景色和着装建议供参考。
            </p>
          </article>
          <el-empty v-else description="暂无帮助内容" />
        </div>
      </template>
    </div>
  </WorkPage>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import WorkPage from '@/components/internal/WorkPage.vue'
import LoadError from '@/components/internal/LoadError.vue'
import { help } from '@/api/internal'
import { useTask } from '@/composables/useInternal'
const task = useTask(),
  keyword = ref(''),
  data = ref(null),
  selected = ref(null)
async function load() {
  const response = await task.run(() => help(keyword.value))
  if (response) {
    data.value = response.result
    selected.value = data.value.tree?.[0]?.items?.[0] || null
  }
}
function showAll() {
  keyword.value = ''
  load()
}
onMounted(load)
</script>
