<template>
  <el-alert
    v-if="message"
    :title="message"
    type="error"
    show-icon
    :closable="false"
    class="work-alert"
  >
    <template #default>
      <el-button link type="primary" @click="$emit('retry')">
        重新加载
      </el-button>
    </template>
  </el-alert>
</template>
<script setup>
defineProps({ message: String })
defineEmits(['retry'])
</script>
