<script setup>
defineProps({
  loading: Boolean,
  error: { type: String, default: '' },
  empty: Boolean,
  emptyText: { type: String, default: '暂无数据' }
})
</script>

<template>
  <div v-if="loading" class="state-block"><span class="spinner"></span>正在加载...</div>
  <div v-else-if="error" class="state-block state-error"><strong>加载失败</strong><span>{{ error }}</span></div>
  <div v-else-if="empty" class="state-block"><strong>{{ emptyText }}</strong><span>后端暂无可展示的记录</span></div>
  <slot v-else />
</template>
