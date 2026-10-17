<script setup>
import { computed } from 'vue'
const props = defineProps({ page: Number, pageSize: Number, total: Number })
const emit = defineEmits(['change'])
const pages = computed(() => Math.max(1, Math.ceil((props.total || 0) / (props.pageSize || 20))))
function change(page) {
  if (page >= 1 && page <= pages.value && page !== props.page) emit('change', page)
}
</script>

<template>
  <div v-if="total > 0" class="pagination">
    <button :disabled="page <= 1" @click="change(page - 1)">上一页</button>
    <span>第 {{ page }} / {{ pages }} 页，共 {{ total }} 条</span>
    <button :disabled="page >= pages" @click="change(page + 1)">下一页</button>
  </div>
</template>
