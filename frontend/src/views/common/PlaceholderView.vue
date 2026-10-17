<template>
  <div class="page-container" style="padding-top: 12px">
    <div class="portal-section">
      <el-empty :description="`「${$route.meta.title}」开发中`">
        <template #image>
          <el-icon :size="64" color="#8ba0c9"><SetUp /></el-icon>
        </template>
        <p class="owner-line">
          负责人：<strong>{{ $route.meta.owner || '待认领' }}</strong>
        </p>
        <p v-if="$route.meta.desc" class="desc-line">{{ $route.meta.desc }}</p>
        <p class="desc-line muted">接手时：把 <code>router/index.js</code> 中本路由的 PlaceholderView 换成自己的组件（路径不要改）。</p>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
// 占位页：路由骨架先行，各负责人替换为自己的视图组件
import { SetUp } from '@element-plus/icons-vue'
</script>

<style scoped>
.owner-line {
  font-size: 15px;
}

.desc-line {
  color: #4b5563;
  font-size: 13px;
  margin: 6px 0;
}

.muted {
  color: #9ca3af;
}
</style>
