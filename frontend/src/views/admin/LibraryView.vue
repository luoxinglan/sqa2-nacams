<template>
  <WorkPage
    title="图书管理"
    description="图书管理教师负责目录维护、借阅审核、归还登记、违规确认与借阅配置。"
  >
    <div class="work-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="图书目录" name="books">
          <div class="work-toolbar">
            <el-input
              v-model="books.query.keyword"
              placeholder="编号、书名或作者"
              aria-label="管理图书关键词"
              @keyup.enter="books.load(true)"
            />
            <el-button @click="books.load(true)">查询</el-button>
            <el-button type="primary" @click="editBook()">新增图书</el-button>
            <el-button @click="importOpen = true">批量导入</el-button>
          </div>
          <PagedTable
            :query="books.query"
            :total="books.result.value.total"
            :loading="books.loading.value"
            :error="books.loadError.value"
            @load="books.load()"
          >
            <el-table :data="books.result.value.items">
              <el-table-column prop="code" label="编号" width="110" />
              <el-table-column prop="title" label="书名" min-width="230" />
              <el-table-column prop="author" label="作者" min-width="130" />
              <el-table-column prop="location" label="位置" min-width="120" />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button type="primary" link @click="editBook(row)">
                    编辑
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    :disabled="task.busy.value"
                    @click="removeBook(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </PagedTable>
        </el-tab-pane>
        <el-tab-pane label="借阅申请" name="requests">
          <div class="work-toolbar">
            <el-select
              v-model="requests.query.status"
              aria-label="申请状态"
              @change="requests.load(true)"
            >
              <el-option label="待审核" value="pending" />
              <el-option label="已批准" value="approved" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
            <el-button @click="requests.load()">刷新申请</el-button>
          </div>
          <PagedTable
            :query="requests.query"
            :total="requests.result.value.total"
            :loading="requests.loading.value"
            :error="requests.loadError.value"
            @load="requests.load()"
          >
            <el-table
              :data="requests.result.value.items"
              empty-text="暂无此状态的申请"
            >
              <el-table-column prop="userName" label="借阅人" min-width="100" />
              <el-table-column prop="bookTitle" label="图书" min-width="200" />
              <el-table-column label="申请时间" min-width="165">
                <template #default="{ row }">
                  {{ displayTime(row.requestedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  {{ requestStatus(row.status) }}
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="审核说明" min-width="150" />
              <el-table-column label="操作" width="185">
                <template #default="{ row }">
                  <template v-if="row.status === 'pending'">
                    <el-button
                      link
                      type="primary"
                      :disabled="task.busy.value"
                      @click="review(row, true)"
                    >
                      批准并借出
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      :disabled="task.busy.value"
                      @click="review(row, false)"
                    >
                      驳回
                    </el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </PagedTable>
        </el-tab-pane>
        <el-tab-pane label="借阅与归还" name="loans">
          <div class="work-toolbar">
            <el-select
              v-model="loans.query.status"
              aria-label="借阅状态"
              clearable
              placeholder="全部状态"
              @change="loans.load(true)"
            >
              <el-option
                v-for="s in ['在借', '已超期', '已归还']"
                :key="s"
                :label="s"
                :value="s"
              />
            </el-select>
            <el-button @click="loans.load()">刷新借阅</el-button>
          </div>
          <PagedTable
            :query="loans.query"
            :total="loans.result.value.total"
            :loading="loans.loading.value"
            :error="loans.loadError.value"
            @load="loans.load()"
          >
            <el-table :data="loans.result.value.items">
              <el-table-column prop="userName" label="借阅人" min-width="100" />
              <el-table-column prop="bookTitle" label="图书" min-width="200" />
              <el-table-column label="应还时间" min-width="165">
                <template #default="{ row }">
                  {{ displayTime(row.dueAt) }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column
                prop="overdueDays"
                label="逾期天数"
                width="100"
              />
              <el-table-column label="操作" width="130">
                <template #default="{ row }">
                  <el-button
                    v-if="!row.returnedAt"
                    link
                    type="primary"
                    :disabled="task.busy.value"
                    @click="returnLoan(row)"
                  >
                    登记归还
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </PagedTable>
        </el-tab-pane>
        <el-tab-pane label="诚信记录" name="violations">
          <p class="work-muted">先沟通确认，再记录违规。已确认记录永久保留。</p>
          <div class="work-toolbar">
            <el-select
              v-model="records.query.status"
              aria-label="违规状态"
              @change="records.load(true)"
            >
              <el-option label="待确认" value="pending" />
              <el-option label="已确认" value="confirmed" />
            </el-select>
            <el-input-number
              v-model="userIdFilter"
              :min="1"
              :precision="0"
              placeholder="用户 ID"
              aria-label="违规用户 ID"
            />
            <el-button @click="filterViolations">查询</el-button>
            <el-button @click="clearViolationFilter">清空筛选</el-button>
          </div>
          <PagedTable
            :query="records.query"
            :total="records.result.value.total"
            :loading="records.loading.value"
            :error="records.loadError.value"
            @load="records.load()"
          >
            <el-table :data="records.result.value.items">
              <el-table-column prop="userName" label="借阅人" min-width="100" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" />
              <el-table-column prop="reason" label="原因" min-width="200" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  {{ requestStatus(row.status) }}
                </template>
              </el-table-column>
              <el-table-column label="确认时间" min-width="165">
                <template #default="{ row }">
                  {{ displayTime(row.confirmedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status === 'pending'"
                    link
                    type="danger"
                    :disabled="task.busy.value"
                    @click="confirmRecord(row)"
                  >
                    确认违规
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </PagedTable>
        </el-tab-pane>
        <el-tab-pane label="借阅设置" name="config">
          <LoadError :message="settings.error.value" @retry="loadConfig" />
          <el-form label-position="top" style="max-width: 440px">
            <el-form-item label="每人同时在借上限">
              <el-input-number
                v-model="config.maxBorrow"
                aria-label="每人同时在借上限"
                :min="1"
                :max="20"
                :precision="0"
              />
            </el-form-item>
            <p class="work-muted">
              默认 2 本。借期 {{ config.loanDays ?? '—' }} 天，到期前
              {{ config.remindBeforeWorkdays ?? '—' }}
              个工作日发送站内提醒。归还冷却期 5 个自然日，不提供续借。
            </p>
            <el-button
              type="primary"
              :disabled="!configLoaded"
              :loading="settings.busy.value"
              @click="saveConfig"
            >
              保存上限
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <p v-if="task.error.value" class="work-form-message" role="alert">
        {{ task.error.value }}
      </p>
    </div>
    <el-dialog
      v-model="bookDialog"
      :title="book.id ? '编辑图书' : '新增图书'"
      width="min(660px, 94vw)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <FieldEditor
          :fields="bookFields"
          :model="book"
          :errors="editor.fields.value"
        />
        <p v-if="editor.error.value" class="work-form-message" role="alert">
          {{ editor.error.value }}
        </p>
      </el-form>
      <template #footer>
        <el-button @click="bookDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="editor.busy.value"
          @click="saveBook"
        >
          保存图书
        </el-button>
      </template>
    </el-dialog>
    <ImportPanel
      v-model="importOpen"
      title="批量导入图书"
      hint="表头 code,title,author,purchaseYear,publicationYear,location；支持老师原表的图书编号、图书名称表头。"
      :uploader="
        (file) => api.upload('/admin/books/import', file, { purchaseYear })
      "
      @done="books.load()"
    >
      <el-form-item label="B001 旧编号换算用购买年份">
        <el-input-number
          v-model="purchaseYear"
          :min="1900"
          :max="2100"
          :precision="0"
        />
      </el-form-item>
      <p class="work-muted">
        旧编号缺少购买年份，请填写有依据的年份；使用演示年份时应在导入记录中注明。
      </p>
    </ImportPanel>
  </WorkPage>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import LoadError from '@/components/internal/LoadError.vue'
import FieldEditor from '@/components/internal/FieldEditor.vue'
import ImportPanel from '@/components/internal/ImportPanel.vue'
import { usePaged, useTask, confirmAction } from '@/composables/useInternal'
import { displayTime, requestStatus } from '@/utils/internal'
import * as api from '@/api/internal'
const tab = ref('books'),
  task = useTask(),
  settings = useTask(),
  editor = useTask()
const books = usePaged(api.listBooks, { keyword: '' }),
  requests = usePaged(api.borrowRequests, { status: 'pending' }),
  loans = usePaged(api.borrows, { status: '' }),
  records = usePaged(api.violations, { status: 'pending', userId: undefined })
const config = reactive({}),
  configLoaded = ref(false),
  bookDialog = ref(false),
  importOpen = ref(false),
  book = reactive({}),
  purchaseYear = ref(undefined),
  userIdFilter = ref(undefined)
const bookFields = [
  {
    key: 'code',
    label: '图书编号',
    hint: '7 位：购买年份 4 位＋顺序 3 位',
    required: true,
  },
  { key: 'title', label: '书名', required: true },
  { key: 'author', label: '作者' },
  { key: 'purchaseYear', label: '购买年份', kind: 'number', required: true },
  { key: 'publicationYear', label: '出版年份', kind: 'number' },
  { key: 'location', label: '存放位置' },
  { key: 'legacyCode', label: '原始编号' },
]
function filterViolations() {
  records.query.userId = userIdFilter.value
  records.load(true)
}
async function refresh() {
  await Promise.all([
    books.load(),
    requests.load(),
    loans.load(),
    records.load(),
  ])
}
function editBook(row) {
  Object.keys(book).forEach((k) => delete book[k])
  for (const f of bookFields)
    book[f.key] = row?.[f.key] ?? (f.kind === 'number' ? undefined : '')
  book.id = row?.id
  editor.error.value = ''
  editor.fields.value = {}
  bookDialog.value = true
}
async function saveBook() {
  if (
    !/^\d{7}$/.test(book.code) ||
    !book.title?.trim() ||
    !book.purchaseYear ||
    Number(book.code.slice(0, 4)) !== book.purchaseYear
  ) {
    editor.error.value = '请填写书名，图书编号须为 7 位且前 4 位与购买年份一致'
    return
  }
  const { id, ...data } = book
  if (await editor.run(() => api.saveBook(id, data), '图书已保存')) {
    bookDialog.value = false
    books.load()
  }
}
async function removeBook(row) {
  if (
    await confirmAction(`删除《${row.title}》？有借阅历史的图书可能无法删除。`)
  )
    if (await task.run(() => api.deleteBook(row.id), '图书已删除')) books.load()
}
async function review(row, approved) {
  let reason = ''
  if (approved) {
    if (
      !(await confirmAction(
        `批准 ${row.userName} 借阅《${row.bookTitle}》并登记借出？`,
        '审核借阅',
      ))
    )
      return
  } else {
    try {
      const input = await ElMessageBox.prompt(
        '请填写驳回原因',
        '驳回借阅申请',
        {
          inputValidator: (v) => Boolean(v?.trim()) || '请填写原因',
          inputPlaceholder: '原因不超过 500 字',
          confirmButtonText: '驳回',
          cancelButtonText: '取消',
        },
      )
      reason = input.value
    } catch {
      return
    }
  }
  if (
    await task.run(
      () => api.reviewRequest(row.id, approved, reason),
      approved ? '已批准并登记借出' : '已驳回申请',
    )
  )
    refresh()
}
async function returnLoan(row) {
  if (
    await confirmAction(
      `确认已收到 ${row.userName} 归还的《${row.bookTitle}》？登记后进入 5 个自然日冷却期。`,
      '登记归还',
    )
  )
    if (await task.run(() => api.returnBook(row.id), '已登记归还')) refresh()
}
async function confirmRecord(row) {
  if (
    await confirmAction(
      `确认已沟通 ${row.userName} 并核实《${row.bookTitle}》违规事实？确认后永久记入诚信记录，不可删除。`,
      '确认诚信违规',
    )
  )
    if (await task.run(() => api.confirmViolation(row.id), '已确认记录'))
      records.load()
}
async function loadConfig() {
  const data = await settings.run(api.libraryConfig)
  if (data) {
    Object.assign(config, data.result)
    configLoaded.value = true
  }
}
async function saveConfig() {
  if (
    !Number.isInteger(config.maxBorrow) ||
    config.maxBorrow < 1 ||
    config.maxBorrow > 20
  ) {
    settings.error.value = '上限须为 1 至 20 的正整数'
    return
  }
  const data = await settings.run(
    () => api.saveLibraryConfig({ maxBorrow: config.maxBorrow }),
    '借阅上限已保存',
  )
  if (data) Object.assign(config, data.result)
}
function clearViolationFilter() {
  userIdFilter.value = undefined
  filterViolations()
}
onMounted(loadConfig)
</script>
