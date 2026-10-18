<template>
  <WorkPage
    :title="manage ? '下载资料管理' : '下载专区'"
    description="开题、中期、答辩、学位论文、专利、会议、上级文件及其他资料。登录后可浏览下载。"
  >
    <template #actions>
      <el-button v-if="manage" type="primary" @click="edit()">
        上传资料
      </el-button>
    </template>
    <div class="work-card">
      <form class="work-toolbar" @submit.prevent="load(true)">
        <el-select
          v-model="query.category"
          aria-label="资料分类"
          clearable
          placeholder="全部分类"
          @change="load(true)"
        >
          <el-option
            v-for="c in DOWNLOAD_CATEGORIES"
            :key="c"
            :label="c"
            :value="c"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          aria-label="资料关键词"
          placeholder="检索资料标题"
          clearable
        />
        <el-button type="primary" native-type="submit">查询</el-button>
      </form>
      <p v-if="task.error.value" class="work-form-message" role="alert">
        {{ task.error.value }}
      </p>
      <PagedTable
        :query="query"
        :total="result.total"
        :loading="loading"
        :error="loadError"
        @load="load()"
      >
        <el-table :data="result.items" empty-text="此栏目暂无资料">
          <el-table-column prop="title" label="资料标题" min-width="230" />
          <el-table-column prop="category" label="分类" width="110" />
          <el-table-column prop="fileName" label="文件名" min-width="190" />
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ fileSize(row.size) }}</template>
          </el-table-column>
          <el-table-column label="操作" :width="manage ? 210 : 100">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="task.busy.value || !row.fileName"
                @click="download(row)"
              >
                下载
              </el-button>
              <template v-if="manage">
                <el-button link type="primary" @click="edit(row)">
                  编辑
                </el-button>
                <el-button
                  link
                  type="danger"
                  :disabled="task.busy.value"
                  @click="remove(row)"
                >
                  删除
                </el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </PagedTable>
    </div>
    <el-dialog
      v-model="dialog"
      :title="form.id ? '编辑下载资料' : '上传下载资料'"
      width="min(600px, 94vw)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="标题" :error="editor.fields.value.title">
          <el-input v-model="form.title" maxlength="500" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category">
            <el-option
              v-for="c in DOWNLOAD_CATEGORIES"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :precision="0" />
        </el-form-item>
        <label class="work-file">
          {{ form.id ? '替换文件（不选则保留）' : '选择资料文件' }}
          <input
            :key="fileKey"
            type="file"
            @change="form.file = $event.target.files[0] || null"
          />
        </label>
        <p class="work-muted">文件最大 50MB。</p>
        <p v-if="editor.error.value" class="work-form-message" role="alert">
          {{ editor.error.value }}
        </p>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="editor.busy.value" @click="save">
          保存
        </el-button>
      </template>
    </el-dialog>
  </WorkPage>
</template>
<script setup>
import { reactive, ref } from 'vue'
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import { usePaged, useTask, confirmAction } from '@/composables/useInternal'
import * as api from '@/api/internal'
import { DOWNLOAD_CATEGORIES, fileSize } from '@/utils/internal'
defineProps({ manage: Boolean })
const { query, result, loading, loadError, load, task } = usePaged(
  api.downloads,
  { category: '', keyword: '' },
)
const dialog = ref(false),
  form = reactive({}),
  editor = useTask(),
  fileKey = ref(0)
function edit(row) {
  Object.assign(form, {
    id: row?.id,
    title: row?.title || '',
    category: row?.category || '开题',
    sortOrder: row?.sortOrder || 0,
    file: null,
  })
  editor.error.value = ''
  editor.fields.value = {}
  fileKey.value++
  dialog.value = true
}
async function save() {
  if (!form.title.trim() || (!form.id && !form.file)) {
    editor.error.value = '请填写标题并选择资料文件'
    return
  }
  if (form.file?.size > 50 * 1024 * 1024) {
    editor.error.value = '文件不得超过 50MB'
    return
  }
  if (
    await editor.run(
      () =>
        api.saveDownload(form.id, {
          title: form.title,
          category: form.category,
          sortOrder: form.sortOrder,
          file: form.file,
        }),
      '资料已保存',
    )
  ) {
    dialog.value = false
    load()
  }
}
async function remove(row) {
  if (await confirmAction(`删除下载条目“${row.title}”？`))
    if (await task.run(() => api.deleteDownload(row.id), '已删除')) load()
}
async function download(row) {
  await task.run(() =>
    api.downloadFile(`/downloads/${row.id}/file`, row.fileName || row.title),
  )
}
</script>
