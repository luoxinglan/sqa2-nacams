<template>
  <WorkPage
    title="备份与恢复"
    description="查看备份计划，创建快照或在确认后恢复已有备份。"
  >
    <template #actions>
      <el-button @click="load">刷新列表</el-button>
      <el-button type="primary" :loading="task.busy.value" @click="backup">
        立即备份
      </el-button>
    </template>
    <div class="work-card">
      <h2>备份信息</h2>
      <LoadError :message="loader.error.value" @retry="load" />
      <el-descriptions v-if="info" :column="1" border>
        <el-descriptions-item label="执行计划">
          {{ info.schedule }}（{{ info.zone }}）
        </el-descriptions-item>
        <el-descriptions-item label="存放位置">
          {{ info.location }}
        </el-descriptions-item>
        <el-descriptions-item label="保留策略">
          {{ info.retention }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        v-loading="loader.busy.value"
        :data="info?.items || []"
        empty-text="暂无备份"
      >
        <el-table-column prop="fileName" label="备份文件" min-width="230" />
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">
            {{ displayTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ fileSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="task.busy.value"
              @click="download(row)"
            >
              下载备份
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="task.error.value" class="work-form-message" role="alert">
        {{ task.error.value }}
      </p>
    </div>
    <div class="work-card">
      <h2>从备份恢复</h2>
      <p class="work-muted">
        恢复将覆盖当前业务数据。系统先备份当前状态，恢复成功后全部账号须使用备份中的密码重新登录。备份含敏感档案，请妥善保管。
      </p>
      <label class="work-file">
        选择备份文件
        <input
          type="file"
          accept=".json,application/json"
          :disabled="restoreTask.busy.value"
          @change="file = $event.target.files[0] || null"
        />
      </label>
      <el-checkbox v-model="acknowledged">
        我已确认备份来源，并理解恢复将覆盖当前数据
      </el-checkbox>
      <p v-if="restoreTask.error.value" class="work-form-message" role="alert">
        {{ restoreTask.error.value }}
      </p>
      <div style="margin-top: 18px">
        <el-button
          type="danger"
          :disabled="!file || !acknowledged"
          :loading="restoreTask.busy.value"
          @click="restore"
        >
          确认并恢复
        </el-button>
      </div>
    </div>
  </WorkPage>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkPage from '@/components/internal/WorkPage.vue'
import LoadError from '@/components/internal/LoadError.vue'
import { useTask, confirmAction } from '@/composables/useInternal'
import { useAuthStore } from '@/stores/auth'
import { displayTime, fileSize } from '@/utils/internal'
import * as api from '@/api/internal'
const task = useTask(),
  loader = useTask(),
  restoreTask = useTask(),
  info = ref(null),
  file = ref(null),
  acknowledged = ref(false),
  auth = useAuthStore(),
  router = useRouter()
async function load() {
  const data = await loader.run(api.backups)
  if (data) info.value = data.result
}
async function backup() {
  if (await task.run(api.createBackup, '备份已创建')) load()
}
async function download(row) {
  await task.run(() =>
    api.downloadFile(
      `/admin/backups/${encodeURIComponent(row.fileName)}/file`,
      row.fileName,
    ),
  )
}
async function restore() {
  if (!file.value || !acknowledged.value) return
  if (
    !(await confirmAction(
      `将使用“${file.value.name}”覆盖当前数据并使所有登录失效，确认恢复？`,
      '最后确认恢复',
    ))
  )
    return
  if (
    await restoreTask.run(
      () => api.upload('/admin/restore', file.value, { confirm: true }),
      '恢复成功，请重新登录',
    )
  ) {
    auth.clearSession()
    router.replace('/login')
  }
}
onMounted(load)
</script>
