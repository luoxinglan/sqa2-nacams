<template>
  <el-result
    icon="warning"
    title="无权访问此页面"
    sub-title="当前账号没有此项权限，如需调整请联系系统管理员。"
  >
    <template #extra>
      <router-link to="/me">
        <el-button type="primary">返回工作台</el-button>
      </router-link>
    </template>
  </el-result>
</template>
