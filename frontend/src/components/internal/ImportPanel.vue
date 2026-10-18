<template>
  <el-dialog
    v-model="open"
    :title="title"
    width="min(780px, 94vw)"
    :close-on-click-modal="false"
  >
    <p class="work-muted">
      {{ hint }}文件使用 UTF-8 CSV 或 XLSX
      格式；逐行处理，失败行请按原因修正后重试。
    </p>
    <slot />
    <label class="work-file">
      选择导入文件
      <input
        type="file"
        accept=".csv,.xlsx"
        :disabled="task.busy.value"
        @change="select"
      />
    </label>
    <p v-if="file">已选：{{ file.name }}</p>
    <el-alert
      v-if="task.error.value"
      :title="task.error.value"
      type="error"
      :closable="false"
    />
    <template v-if="report">
      <p role="status">{{ reportSummary }}</p>
      <el-table :data="report.rows || []" max-height="320" border>
        <el-table-column prop="sheet" label="工作表" width="100" />
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.ok ? 'success' : 'danger'">
              {{ row.skipped ? '跳过' : row.ok ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="230" />
      </el-table>
    </template>
    <template #footer>
      <el-button :disabled="task.busy.value" @click="open = false">
        关闭
      </el-button>
      <el-button
        type="primary"
        :loading="task.busy.value"
        :disabled="!file"
        @click="submit"
      >
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useTask } from '@/composables/useInternal'
const open = defineModel({ type: Boolean })
const props = defineProps({
  title: { type: String, default: '批量导入' },
  hint: String,
  uploader: Function,
})
const emit = defineEmits(['done'])
const task = useTask(),
  file = ref(null),
  report = ref(null)
const reportSummary = computed(() => {
  if (!report.value) return ''
  const { total, success, failed, skipped } = report.value
  return `共 ${total} 行，成功 ${success} 行，失败 ${failed} 行${skipped ? `，跳过 ${skipped} 行` : ''}。`
})
watch(open, () => {
  file.value = null
  report.value = null
  task.error.value = ''
})
function select(e) {
  file.value = e.target.files[0] || null
  report.value = null
}
async function submit() {
  const data = await task.run(() => props.uploader(file.value))
  if (data) {
    report.value = data.result
    file.value = null
    emit('done')
  }
}
</script>
