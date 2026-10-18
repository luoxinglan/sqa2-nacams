<template>
  <div>
    <p class="work-muted">
      照片不超过 2MB，宽高比为
      33:48。白色服装配淡蓝背景，其他服装配白背景；背景及着装仅提供建议。
    </p>
    <img
      v-if="preview"
      :src="preview"
      class="work-photo"
      alt="待检测照片预览"
    />
    <label class="work-file">
      选择照片
      <input
        type="file"
        accept="image/png,image/jpeg,image/webp,image/bmp"
        :disabled="task.busy.value"
        @change="select"
      />
    </label>
    <div class="work-actions">
      <el-button :disabled="!file" :loading="task.busy.value" @click="check">
        在线检测
      </el-button>
      <el-button
        v-if="mode"
        type="primary"
        :disabled="!result?.sizeOk || !result?.ratioOk"
        :loading="task.busy.value"
        @click="save"
      >
        {{ mode === 'self' ? '保存本人照片' : '使用这张照片' }}
      </el-button>
    </div>
    <p v-if="task.error.value" class="work-form-message" role="alert">
      {{ task.error.value }}
    </p>
    <div v-if="result" class="work-prose" role="status">
      <p>
        大小：{{ result.sizeOk ? '通过' : '未通过' }}　宽高比：{{
          result.ratioOk ? '通过' : '未通过'
        }}
      </p>
      <ul>
        <li v-for="advice in result.suggestions" :key="advice">{{ advice }}</li>
      </ul>
    </div>
  </div>
</template>
<script setup>
import { onUnmounted, ref } from 'vue'
import * as api from '@/api/internal'
import { useTask } from '@/composables/useInternal'
const props = defineProps({ mode: String }),
  emit = defineEmits(['saved'])
const task = useTask(),
  file = ref(null),
  result = ref(null),
  preview = ref('')
function select(e) {
  if (preview.value) URL.revokeObjectURL(preview.value)
  file.value = e.target.files[0] || null
  preview.value = file.value ? URL.createObjectURL(file.value) : ''
  result.value = null
  task.error.value = ''
}
onUnmounted(() => {
  if (preview.value) URL.revokeObjectURL(preview.value)
})
async function check() {
  if (!file.value) return
  if (file.value.size > 2 * 1024 * 1024) {
    task.error.value = '照片不得超过 2MB'
    result.value = {
      sizeOk: false,
      ratioOk: false,
      suggestions: ['请压缩后重新检测'],
    }
    return
  }
  const data = await task.run(() => api.checkPhoto(file.value))
  if (data) result.value = data.result
}
async function save() {
  if (!result.value?.sizeOk || !result.value?.ratioOk) return
  const data = await task.run(
    () =>
      props.mode === 'self'
        ? api.savePhoto(file.value)
        : api.upload('/admin/media', file.value),
    '照片已上传',
  )
  if (data) emit('saved', data.result)
}
</script>
