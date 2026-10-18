<template>
  <WorkPage
    title="工作概览"
    :description="`${auth.user?.name || ''}，欢迎回到研究所。登录已由系统自动签到，请在离开时退出并签出。`"
  >
    <LoadError :message="task.error.value" @retry="load" />
    <div class="work-metrics" v-loading="task.busy.value">
      <div class="work-metric">
        <span>我的借阅记录</span>
        <strong>{{ summary.borrows ?? '—' }}</strong>
        <router-link to="/me/borrows">查看借阅</router-link>
      </div>
      <div class="work-metric">
        <span>未读站内提醒</span>
        <strong>{{ summary.unread ?? '—' }}</strong>
        <router-link to="/me/notifications">查看提醒</router-link>
      </div>
      <div class="work-metric">
        <span>已确认诚信记录</span>
        <strong>{{ summary.violations ?? '—' }}</strong>
        <router-link to="/me/borrows">查看记录</router-link>
      </div>
    </div>
    <div class="work-card">
      <h2>常用服务</h2>
      <div class="work-quicklinks">
        <router-link to="/me/books">查找与申请图书</router-link>
        <router-link to="/me/downloads">下载培养与会议资料</router-link>
        <router-link to="/me/profile">维护个人档案与照片</router-link>
        <router-link to="/me/help">阅读用户手册</router-link>
      </div>
    </div>
    <div class="work-card">
      <h2>借阅提示</h2>
      <p class="work-muted">
        借阅申请由图书管理教师审核登记后借出；默认同时在借上限为 2
        本，由图书管理教师配置。不提供续借。
      </p>
      <p class="work-muted">
        归还后须间隔 5 个自然日。到期前 3
        个工作日发送站内提醒。违规记录经图书管理教师确认后永久保留，已确认超过 3
        次将停止借阅资格。
      </p>
    </div>
  </WorkPage>
</template>
<script setup>
import { onMounted, reactive } from 'vue'
import WorkPage from '@/components/internal/WorkPage.vue'
import LoadError from '@/components/internal/LoadError.vue'
import { useAuthStore } from '@/stores/auth'
import { useTask } from '@/composables/useInternal'
import * as api from '@/api/internal'
const auth = useAuthStore(),
  task = useTask(),
  summary = reactive({})
async function load() {
  await task.run(async () => {
    const [b, n, v] = await Promise.all([
      api.myBorrows({ page_size: 1 }),
      api.notifications({ page_size: 1 }),
      api.myViolations({ page_size: 1 }),
    ])
    Object.assign(summary, {
      borrows: b.total,
      unread: n.unread,
      violations: v.confirmedCount,
    })
  })
}
onMounted(load)
</script>
