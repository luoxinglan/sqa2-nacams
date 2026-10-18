<template>
  <WorkPage
    title="签到签出统计"
    description="登录即签到，退出即签出。所长可查看全员，全职教师可查看本人名下学生。"
  >
    <div class="work-card">
      <div class="work-toolbar">
        <el-radio-group v-model="query.scope" @change="load(true)">
          <el-radio-button v-if="auth.isDirector" value="all">
            全部人员
          </el-radio-button>
          <el-radio-button v-if="isFullTeacher(auth.user)" value="mine">
            名下学生
          </el-radio-button>
        </el-radio-group>
        <el-button @click="load()">刷新统计</el-button>
      </div>
      <PagedTable
        :query="query"
        :total="result.total"
        :loading="loading"
        :error="loadError"
        @load="load()"
      >
        <el-table :data="result.items" empty-text="暂无符合权限范围的签到记录">
          <el-table-column prop="name" label="姓名" min-width="150" />
          <el-table-column label="签到时间" min-width="180">
            <template #default="{ row }">
              {{ displayTime(row.signinAt) }}
            </template>
          </el-table-column>
          <el-table-column label="签出时间" min-width="180">
            <template #default="{ row }">
              {{ displayTime(row.signoutAt) }}
            </template>
          </el-table-column>
          <el-table-column label="会话状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.signoutAt ? 'info' : 'success'">
                {{ row.signoutAt ? '已签出' : '未签出' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </PagedTable>
    </div>
  </WorkPage>
</template>
<script setup>
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import { useAuthStore } from '@/stores/auth'
import { usePaged } from '@/composables/useInternal'
import { signinStats } from '@/api/internal'
import { displayTime, isFullTeacher } from '@/utils/internal'
const auth = useAuthStore()
const { query, result, loading, loadError, load } = usePaged(signinStats, {
  scope: auth.isDirector ? 'all' : 'mine',
})
</script>
