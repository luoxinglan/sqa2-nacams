<template>
  <WorkPage
    title="我的借阅与诚信"
    description="查看借阅状态、应还日期及经图书管理教师确认的诚信记录。"
  >
    <template #actions>
      <router-link to="/me/books">
        <el-button type="primary">查找图书</el-button>
      </router-link>
    </template>
    <div class="work-card">
      <h2>借阅记录</h2>
      <PagedTable
        :query="loans.query"
        :total="loans.result.value.total"
        :loading="loans.loading.value"
        :error="loans.loadError.value"
        @load="loans.load()"
      >
        <el-table :data="loans.result.value.items" empty-text="暂无借阅记录">
          <el-table-column prop="bookCode" label="图书编号" width="110" />
          <el-table-column prop="bookTitle" label="图书" min-width="200" />
          <el-table-column label="状态" width="115">
            <template #default="{ row }">
              <el-tag
                :type="
                  row.status === '已超期'
                    ? 'danger'
                    : row.status === '已归还'
                      ? 'info'
                      : 'primary'
                "
              >
                {{ requestStatus(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请／借出时间" min-width="165">
            <template #default="{ row }">
              {{ displayTime(row.borrowedAt || row.requestedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="应还时间" min-width="165">
            <template #default="{ row }">
              {{ displayTime(row.dueAt) }}
              <el-tag v-if="row.dueReminder" size="small" type="warning">
                即将到期
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="审核说明" min-width="150" />
          <el-table-column label="归还时间" min-width="165">
            <template #default="{ row }">
              {{ displayTime(row.returnedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="剩余／逾期" min-width="130">
            <template #default="{ row }">
              {{
                row.returnedAt
                  ? '已归还'
                  : row.overdueDays > 0
                    ? `逾期 ${row.overdueDays} 天`
                    : row.remainingDays != null
                      ? `剩余 ${row.remainingDays} 天`
                      : '—'
              }}
            </template>
          </el-table-column>
        </el-table>
      </PagedTable>
      <p class="work-muted">
        不提供续借；请线下归还给图书管理教师，由其登记归还。
      </p>
    </div>
    <div class="work-card">
      <h2>诚信记录</h2>
      <el-alert
        v-if="records.result.value.confirmedCount != null"
        class="work-alert"
        :type="records.result.value.suspended ? 'error' : 'info'"
        :title="`已确认 ${records.result.value.confirmedCount} 次；${records.result.value.suspended ? '已停止借阅资格' : '超过 3 次（第 4 次起）停止借阅资格'}。记录永久保留。`"
        :closable="false"
      />
      <PagedTable
        :query="records.query"
        :total="records.result.value.total"
        :loading="records.loading.value"
        :error="records.loadError.value"
        @load="records.load()"
      >
        <el-table :data="records.result.value.items" empty-text="暂无诚信记录">
          <el-table-column prop="bookTitle" label="图书" min-width="180" />
          <el-table-column prop="reason" label="原因" min-width="240" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              {{ requestStatus(row.status) }}
            </template>
          </el-table-column>
          <el-table-column label="确认时间" min-width="165">
            <template #default="{ row }">
              {{ displayTime(row.confirmedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="confirmedByName" label="确认人" width="110" />
        </el-table>
      </PagedTable>
    </div>
  </WorkPage>
</template>
<script setup>
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import { usePaged } from '@/composables/useInternal'
import { myBorrows, myViolations } from '@/api/internal'
import { displayTime, requestStatus } from '@/utils/internal'
const loans = usePaged(myBorrows),
  records = usePaged(myViolations)
</script>
