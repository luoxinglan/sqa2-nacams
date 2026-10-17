<script setup>
import { ref, onMounted, watch } from 'vue'
import { listContent } from '../api'
import PageShell from '../components/PageShell.vue'
import StateBlock from '../components/StateBlock.vue'
import PaginationBar from '../components/PaginationBar.vue'

const props = defineProps({ mode: { type: String, required: true } })
const config = {
  teaching: { title: '教学科研', subtitle: '课程信息与科研项目信息', types: ['courses', 'projects'] },
  research: { title: '科研项目', subtitle: '研究所项目与科研工作', types: ['projects'] },
  achievements: { title: '研究成果', subtitle: '教学与科研成果分类展示', types: ['achievements'], filters: ['全部', '教学成果', '教学论文', '教材', '教改', '科研成果', '科研论文', '专利', '著作权'] },
  admissions: { title: '招生信息', subtitle: '本科、硕士、博士与留学生招生栏目', types: ['admissions'], filters: ['全部', '本科', '硕士', '博士', '留学生'] },
  cooperations: { title: '国际合作', subtitle: '大学、企业与合作项目', types: ['cooperations'] }
}
const meta = config[props.mode]; const selected = ref(meta.filters?.[0] || meta.types[0]); const type = ref(meta.types[0])
const data = ref({ items: [], total: 0, page: 1, page_size: 20 }); const loading = ref(false); const error = ref('')
async function load(page = 1) {
  loading.value = true; error.value = ''
  try {
    const params = { page, page_size: 20 }
    if (props.mode === 'teaching') type.value = selected.value
    if (props.mode === 'achievements' && selected.value !== '全部') params.type = selected.value
    if (props.mode === 'admissions' && selected.value !== '全部') params.level = selected.value
    data.value = await listContent(type.value, params)
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
watch(selected, () => load(1)); onMounted(() => load())
function field(item, names) { for (const name of names) if (item[name] !== undefined && item[name] !== '') return item[name]; return '—' }
</script>

<template>
  <PageShell :title="meta.title" :subtitle="meta.subtitle">
    <div v-if="meta.filters" class="tabs section-tabs"><button v-for="item in meta.filters" :key="item" :class="{ active: selected === item }" @click="selected = item">{{ item }}</button></div>
    <div v-if="mode === 'teaching'" class="tabs section-tabs"><button v-for="item in meta.types" :key="item" :class="{ active: type === item }" @click="type = item; load(1)">{{ item === 'courses' ? '课程信息' : '项目成果' }}</button></div>
    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !data.items.length" empty-text="暂无可展示记录">
      <div v-if="mode === 'cooperations'" class="table-wrap"><table><thead><tr><th>大学/企业名称</th><th>所属国家</th><th>合作项目</th></tr></thead><tbody><tr v-for="item in data.items" :key="item.id"><td>{{ field(item, ['name', 'title', 'organization']) }}</td><td>{{ field(item, ['country', 'nation']) }}</td><td>{{ field(item, ['project', 'cooperationProject', 'body']) }}</td></tr></tbody></table></div>
      <div v-else class="card-grid"><article v-for="item in data.items" :key="item.id" class="info-card"><span class="tag">{{ item.category || (type === 'courses' ? '课程' : '') }}</span><h2>{{ item.title || item.name }}</h2><p>{{ item.body || item.description || '暂无简介' }}</p><dl><template v-if="mode === 'achievements' && item.category === '专利'"><dt>申请号</dt><dd>{{ field(item, ['applicationNo', 'application_no']) }}</dd></template><template v-if="mode === 'admissions'"><dt>联系人</dt><dd>{{ field(item, ['contact', 'contactName']) }}</dd></template><template v-if="mode === 'teaching'"><dt>主讲/负责人</dt><dd>{{ field(item, ['teacher', 'leader']) }}</dd></template></dl></article></div>
    </StateBlock>
    <PaginationBar :page="data.page" :page-size="data.page_size" :total="data.total" @change="load" />
  </PageShell>
</template>
