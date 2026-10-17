<template>
  <div class="page-container about">
    <!-- 关于我们（需求 §3.1.3）：研究所简介 / 师资队伍 / 学生信息 / 联系我们 四栏目。
         简介与联系我们为后台可更新的文字内容（刘罡甫）；师资队伍、学生信息为独立页面（钟坤宇）。 -->
    <section id="intro" class="portal-section">
      <h2 class="section-title">研究所简介</h2>
      <el-skeleton v-if="loading" :rows="4" animated />
      <div v-else class="rich-text" v-html="about.intro"></div>
    </section>

    <div class="sub-nav">
      <router-link to="/teachers" class="sub-card">
        <el-icon :size="26" color="#1b4e9b"><User /></el-icon>
        <span class="sub-name">师资队伍</span>
        <span class="sub-desc">全职教师 · 兼职教师</span>
      </router-link>
      <router-link to="/students" class="sub-card">
        <el-icon :size="26" color="#1b4e9b"><Notebook /></el-icon>
        <span class="sub-name">学生信息</span>
        <span class="sub-desc">在读学生 · 毕业学生</span>
      </router-link>
      <a href="#contact" class="sub-card" @click.prevent="scrollToContact">
        <el-icon :size="26" color="#1b4e9b"><Location /></el-icon>
        <span class="sub-name">联系我们</span>
        <span class="sub-desc">地址 · 邮箱 · 电话</span>
      </a>
    </div>

    <section id="contact" class="portal-section contact-section">
      <h2 class="section-title">联系我们</h2>
      <el-skeleton v-if="loading" :rows="3" animated />
      <div v-else class="rich-text contact-text" v-html="about.contact"></div>
    </section>
  </div>
</template>

<script setup>
// 关于我们（刘罡甫）：简介/联系我们从 GET /about 取，管理员后台更新后前台即时反映
import { onMounted, ref } from 'vue'
import { User, Notebook, Location } from '@element-plus/icons-vue'
import { getAbout } from '@/api/site'

const about = ref({ intro: '', contact: '' })
const loading = ref(true)

onMounted(async () => {
  try {
    about.value = await getAbout()
  } finally {
    loading.value = false
  }
})

function scrollToContact() {
  document.getElementById('contact')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped>
.about {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.sub-nav {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.sub-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 16px;
  background: #fff;
  border: 1px solid #e5e9f2;
  border-radius: 10px;
  color: var(--nacams-text);
  transition: all 0.2s;
}

.sub-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 16px rgba(27, 78, 155, 0.12);
  transform: translateY(-2px);
}

.sub-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--nacams-navy);
}

.sub-desc {
  font-size: 12px;
  color: var(--nacams-muted);
}

.contact-section {
  scroll-margin-top: 88px;
}

.contact-text :deep(p) {
  text-indent: 0;
}
</style>
