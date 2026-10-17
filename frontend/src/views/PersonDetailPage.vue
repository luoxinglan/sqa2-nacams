<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { getPerson, mediaUrl } from '../api'
import PageShell from '../components/PageShell.vue'
import StateBlock from '../components/StateBlock.vue'

const props = defineProps({ kind: { type: String, required: true } }); const route = useRoute(); const person = ref(null); const loading = ref(true); const error = ref('')
onMounted(async () => { try { person.value = await getPerson(props.kind, route.params.id) } catch (e) { error.value = e.message } finally { loading.value = false } })
</script>

<template>
  <PageShell :title="kind === 'teachers' ? '教师详情' : '学生详情'">
    <RouterLink class="back-link" :to="`/${kind}`">← 返回列表</RouterLink>
    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !person" empty-text="公开档案不存在">
      <article class="profile-card"><img v-if="person.photo" :src="mediaUrl(person.photo)" :alt="person.name"><div v-else class="avatar-placeholder large">{{ person.name?.slice(0, 1) }}</div><div><p class="eyebrow">{{ person.identityType === 'teacher' ? (person.teacherType === 'external' ? '校外兼职教师' : '全职教师') : person.status }}</p><h2>{{ person.name }}</h2><p>{{ person.title || person.category || '研究所成员' }}</p><dl class="profile-fields"><template v-for="(value, key) in person" :key="key"><template v-if="!['id', 'identityType', 'photo', 'name', 'password', 'profile_json', 'mobile', 'officePhone', 'birthDate'].includes(key) && value !== '' && value !== null"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></template></dl></div></article>
    </StateBlock>
  </PageShell>
</template>
