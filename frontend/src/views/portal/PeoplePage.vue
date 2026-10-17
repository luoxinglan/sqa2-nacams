<script setup>
import { ref, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { listPeople, mediaUrl } from '@/api/portal'
import PageShell from '@/components/PageShell.vue'
import StateBlock from '@/components/StateBlock.vue'
import PaginationBar from '@/components/PaginationBar.vue'

const props = defineProps({ kind: { type: String, required: true } }); const teacher = props.kind === 'teachers'
const filter = ref(teacher ? '全部' : '在读'); const year = ref(''); const keyword = ref('')
const data = ref({ items: [], total: 0, page: 1, page_size: 20 }); const loading = ref(false); const error = ref('')
async function load(page = 1) { loading.value = true; error.value = ''; try { const params = { page, page_size: 20, keyword: keyword.value }; if (teacher && filter.value !== '全部') params.type = filter.value; if (!teacher && filter.value !== '全部') params.status = filter.value; if (!teacher && year.value) params.year = year.value; data.value = await listPeople(props.kind, params) } catch (e) { error.value = e.message } finally { loading.value = false } }
function search() { load(1) }; watch(filter, () => load(1)); onMounted(() => load())
</script>

<template>
  <PageShell :title="teacher ? '师资队伍' : '学生风采'" :subtitle="teacher ? '全职教师与校外兼职教师' : '在读、毕业与结业肄业学生'">
    <div class="toolbar"><div class="tabs"><button v-for="item in (teacher ? ['全部', '全职', '兼职'] : ['在读', '毕业', '结业肄业'])" :key="item" :class="{ active: filter === item }" @click="filter = item">{{ item }}</button></div><form class="search" @submit.prevent="search"><input v-if="!teacher" v-model="year" placeholder="入学年份"><input v-model="keyword" placeholder="姓名或编号"><button class="button button-dark">搜索</button></form></div>
    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !data.items.length" empty-text="暂无公开档案">
      <div class="people-grid"><RouterLink v-for="person in data.items" :key="person.id" class="person-card" :to="`/${kind}/${person.id}`"><img v-if="person.photo" :src="mediaUrl(person.photo)" :alt="person.name"><div v-else class="avatar-placeholder">{{ person.name?.slice(0, 1) }}</div><div><h2>{{ person.name }}</h2><p>{{ teacher ? (person.title || person.teacherType === 'external' ? '教师' : '全职教师') : `${person.status || '学生'} · ${person.studentNo || ''}` }}</p><small>{{ person.email || '公开档案' }}</small></div></RouterLink></div>
    </StateBlock>
    <PaginationBar :page="data.page" :page-size="data.page_size" :total="data.total" @change="load" />
  </PageShell>
</template>
