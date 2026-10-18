<template>
  <WorkPage
    title="站内提醒"
    description="到期提醒、借阅事项与诚信记录通知会保存在这里。"
  >
    <template #actions>
      <el-button @click="load()">刷新提醒</el-button>
    </template>
    <div class="work-card">
      <p class="work-muted" v-if="result.unread != null">
        未读 {{ result.unread }} 条
      </p>
      <PagedTable
        :query="query"
        :total="result.total"
        :loading="loading"
        :error="loadError"
        @load="load()"
      >
        <el-empty
          v-if="!loading && !result.items.length"
          description="暂无站内提醒"
        />
        <article
          v-for="item in result.items"
          :key="item.id"
          class="work-notice"
        >
          <div>
            <h2>
              <el-tag v-if="!item.isRead" size="small" class="work-alert">
                未读
              </el-tag>
              {{ item.title }}
            </h2>
            <p class="work-prose">{{ item.body }}</p>
            <small class="work-muted">{{ displayTime(item.createdAt) }}</small>
          </div>
          <el-button
            v-if="!item.isRead"
            size="small"
            :loading="task.busy.value"
            @click="mark(item.id)"
          >
            标记已读
          </el-button>
        </article>
      </PagedTable>
      <p v-if="task.error.value" class="work-form-message" role="alert">
        {{ task.error.value }}
      </p>
    </div>
  </WorkPage>
</template>
<script setup>
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import { usePaged } from '@/composables/useInternal'
import { notifications, readNotification } from '@/api/internal'
import { displayTime } from '@/utils/internal'
const { query, result, loading, loadError, load, task } =
  usePaged(notifications)
async function mark(id) {
  if (await task.run(() => readNotification(id))) load()
}
</script>
