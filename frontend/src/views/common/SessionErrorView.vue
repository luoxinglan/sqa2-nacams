<template>
  <el-result
    icon="error"
    title="登录状态核验失败"
    sub-title="暂时无法连接服务器，请稍后重新尝试。"
  >
    <template #extra>
      <el-button type="primary" @click="retry">重新核验</el-button>
      <router-link to="/">返回门户</router-link>
    </template>
  </el-result>
</template>
<script setup>
import { useRoute, useRouter } from 'vue-router'
import { safeRedirect } from '@/utils/internal'
const route = useRoute(),
  router = useRouter()
function retry() {
  router.replace(safeRedirect(route.query.redirect))
}
</script>
