<template>
  <WorkPage
    title="图书目录"
    description="按编号、书名或作者检索图书，提交申请后由图书管理教师审核登记。"
  >
    <div class="work-card">
      <form class="work-toolbar" @submit.prevent="load(true)">
        <el-input
          v-model="query.keyword"
          aria-label="图书检索"
          placeholder="图书编号、书名或作者"
          clearable
        />
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="load()">刷新目录</el-button>
        <router-link to="/me/borrows">我的借阅与诚信记录</router-link>
      </form>
      <el-alert
        v-if="receipt"
        type="success"
        :closable="false"
        class="work-alert"
        :title="`《${receipt.title}》申请已提交，回执状态：${requestStatus(receipt.status)}。`"
        description="请等待图书管理教师审核；批准登记后借出。"
      />
      <el-alert
        v-if="task.error.value"
        :title="task.error.value"
        type="error"
        :closable="false"
        class="work-alert"
      />
      <PagedTable
        :query="query"
        :total="result.total"
        :loading="loading"
        :error="loadError"
        @load="load()"
      >
        <el-table :data="result.items" empty-text="暂无匹配图书">
          <el-table-column prop="code" label="编号" width="110" />
          <el-table-column prop="title" label="书名" min-width="210" />
          <el-table-column prop="author" label="作者" min-width="120" />
          <el-table-column
            prop="publicationYear"
            label="出版年份"
            width="100"
          />
          <el-table-column prop="location" label="所在位置" min-width="130" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === '可借' ? 'success' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                :disabled="row.status !== '可借' || task.busy.value"
                @click="borrow(row)"
              >
                申请借阅
              </el-button>
              <small v-if="row.availableAt">
                可借日期 {{ row.availableAt }}
              </small>
            </template>
          </el-table-column>
        </el-table>
      </PagedTable>
    </div>
  </WorkPage>
</template>
<script setup>
import { ref } from 'vue'
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import { usePaged, confirmAction } from '@/composables/useInternal'
import { listBooks, borrowBook } from '@/api/internal'
import { requestStatus } from '@/utils/internal'
const { query, result, loading, loadError, load, task } = usePaged(listBooks, {
  keyword: '',
})
const receipt = ref(null)
async function borrow(row) {
  if (
    !(await confirmAction(
      `申请借阅《${row.title}》？申请经审核登记后方可借出。`,
      '申请借阅',
    ))
  )
    return
  const data = await task.run(() => borrowBook(row.id))
  if (data) {
    receipt.value = { ...data.result, title: row.title }
    await load()
  }
}
</script>
