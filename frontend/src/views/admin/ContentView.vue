<template>
  <WorkPage
    title="内容管理"
    description="维护门户栏目内容与附件，保存后由公开页面读取展示。"
  >
    <template #actions>
      <el-button @click="importOpen = true">导入栏目内容</el-button>
      <el-button type="primary" @click="edit()">新增内容</el-button>
    </template>
    <div class="work-card">
      <form class="work-toolbar" @submit.prevent="load(true)">
        <el-select v-model="type" aria-label="内容栏目" @change="load(true)">
          <el-option
            v-for="(label, key) in CONTENT_TYPES"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="检索标题或正文"
          aria-label="内容关键词"
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
        <el-table :data="result.items" empty-text="此栏目暂无内容">
          <el-table-column prop="title" label="标题" min-width="260" />
          <el-table-column prop="category" label="分类" min-width="120" />
          <el-table-column prop="author" label="撰稿人" min-width="110" />
          <el-table-column
            prop="publishedAt"
            label="发布时间"
            min-width="165"
          />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link type="primary" @click="edit(row)">编辑</el-button>
              <el-button
                link
                type="danger"
                :disabled="task.busy.value"
                @click="remove(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </PagedTable>
    </div>
    <el-dialog
      v-model="dialog"
      :title="`${form.id ? '编辑' : '新增'}${CONTENT_TYPES[type]}`"
      width="min(920px, 96vw)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <div class="work-form-grid">
          <el-form-item
            label="标题"
            required
            :error="editor.fields.value.title"
          >
            <el-input v-model="form.title" aria-label="标题" maxlength="500" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-if="categories" v-model="form.category">
              <el-option
                v-for="c in categories"
                :key="c"
                :label="c"
                :value="c"
              />
            </el-select>
            <el-input v-else v-model="form.category" placeholder="栏目分类" />
          </el-form-item>
          <el-form-item label="撰稿人">
            <el-input v-model="form.author" />
          </el-form-item>
          <el-form-item label="发布时间">
            <el-date-picker
              v-model="form.publishedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm"
              format="YYYY-MM-DD HH:mm"
            />
          </el-form-item>
        </div>
        <el-form-item label="正文">
          <el-input
            v-model="form.body"
            type="textarea"
            :rows="8"
            placeholder="填写正文；需要图文排版时可使用 HTML"
          />
        </el-form-item>
        <FieldEditor
          :fields="extraFields"
          :model="form"
          :errors="editor.fields.value"
        />
        <el-divider />
        <h3>图片、视频与附件</h3>
        <p class="work-muted">
          上传图片或 MP4 视频会归入对应媒体列表，其他文件归入附件。文件最大
          50MB。
        </p>
        <label class="work-file">
          选择媒体或附件
          <input
            :key="fileKey"
            type="file"
            :disabled="media.busy.value"
            @change="uploadMedia"
          />
        </label>
        <p v-if="media.error.value" class="work-form-message" role="alert">
          {{ media.error.value }}
        </p>
        <div v-for="kind in ['images', 'videos', 'attachments']" :key="kind">
          <p v-for="(item, i) in form[kind]" :key="i" class="work-actions">
            <span>
              {{
                { images: '图片', videos: '视频', attachments: '附件' }[kind]
              }}：{{ item.name || item.url || item }}
            </span>
            <el-button link type="danger" @click="form[kind].splice(i, 1)">
              移除
            </el-button>
          </p>
        </div>
        <el-collapse>
          <el-collapse-item title="其他扩展字段">
            <p class="work-muted">
              保留原有扩展信息。可填写 JSON 对象，例如专利申请号、教材
              ISBN；已在表单中的字段优先使用表单值。
            </p>
            <el-input
              v-model="extras"
              type="textarea"
              :rows="6"
              aria-label="内容扩展字段"
            />
          </el-collapse-item>
        </el-collapse>
        <p v-if="editor.error.value" class="work-form-message" role="alert">
          {{ editor.error.value }}
        </p>
      </el-form>
      <template #footer>
        <el-button :disabled="editor.busy.value" @click="dialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="editor.busy.value"
          :disabled="media.busy.value"
          @click="save"
        >
          保存内容
        </el-button>
      </template>
    </el-dialog>
    <ImportPanel
      v-model="importOpen"
      :title="`导入${CONTENT_TYPES[type]}`"
      hint="表头 title,category,body,author,publishedAt，其他列作为扩展字段。"
      :uploader="(file) => api.upload(`/admin/content/${type}/import`, file)"
      @done="load()"
    />
  </WorkPage>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import FieldEditor from '@/components/internal/FieldEditor.vue'
import ImportPanel from '@/components/internal/ImportPanel.vue'
import { usePaged, useTask, confirmAction } from '@/composables/useInternal'
import { CONTENT_TYPES, CONTENT_CATEGORIES } from '@/utils/internal'
import * as api from '@/api/internal'
const type = ref('news'),
  dialog = ref(false),
  importOpen = ref(false),
  form = reactive({}),
  extras = ref('{}'),
  fileKey = ref(0)
const { query, result, loading, loadError, load, task } = usePaged(
  (params) => api.content(type.value, params),
  { keyword: '' },
)
const editor = useTask(),
  media = useTask(),
  categories = computed(() => CONTENT_CATEGORIES[type.value])
const schemas = {
  courses: {
    code: '课程编号',
    nature: '课程性质',
    studentLevel: '学生层次',
    term: '授课时间',
    teacher: '主讲教师',
  },
  projects: { nature: '项目性质', leader: '项目负责人' },
  admissions: {
    technicalRequirements: '技术要求',
    workingTime: '工作时间',
    otherRequirements: '其他要求',
    contact: '联系人',
    contactInfo: '联系方式',
  },
  cooperations: {
    organization: '大学或企业名称',
    country: '所属国家',
    project: '合作项目',
  },
  achievements: {
    applicationNo: '专利申请号',
    applicant: '申请人',
    patentName: '发明创造名称',
    registrationNo: '登记号',
    copyrightHolder: '著作权人',
    softwareName: '软件名称',
    isbn: 'ISBN',
    publisher: '出版社',
    year: '年份',
  },
}
const extraFields = computed(() =>
  Object.entries(schemas[type.value] || {}).map(([key, label]) => ({
    key,
    label,
  })),
)
function edit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(
    form,
    {
      title: '',
      category: categories.value?.[0] || '',
      body: '',
      author: '',
      publishedAt: '',
      images: [],
      videos: [],
      attachments: [],
    },
    JSON.parse(JSON.stringify(row || {})),
  )
  for (const kind of ['images', 'videos', 'attachments'])
    form[kind] = Array.isArray(form[kind])
      ? form[kind]
      : form[kind]
        ? [form[kind]]
        : []
  const other = { ...(row || {}) }
  for (const key of [
    'id',
    'type',
    ...Object.keys(form).filter((k) =>
      [
        'title',
        'category',
        'body',
        'author',
        'publishedAt',
        'images',
        'videos',
        'attachments',
      ].includes(k),
    ),
    ...extraFields.value.map((f) => f.key),
  ])
    delete other[key]
  extras.value = JSON.stringify(other, null, 2)
  editor.error.value = ''
  editor.fields.value = {}
  media.error.value = ''
  fileKey.value++
  dialog.value = true
}
async function uploadMedia(e) {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 50 * 1024 * 1024) {
    media.error.value = '文件不得超过 50MB'
    return
  }
  const data = await media.run(() => api.upload('/admin/media', file))
  if (data) {
    const saved = data.result
    if (file.type.startsWith('image/')) form.images.push(saved.url)
    else if (file.type === 'video/mp4') form.videos.push(saved.url)
    else form.attachments.push(saved.url)
  }
  e.target.value = ''
}
async function save() {
  if (
    !form.title.trim() ||
    (categories.value && !categories.value.includes(form.category))
  ) {
    editor.error.value = '请填写标题并选择有效分类'
    return
  }
  let other
  try {
    other = JSON.parse(extras.value)
    if (!other || typeof other !== 'object' || Array.isArray(other))
      throw Error()
  } catch {
    editor.error.value = '扩展字段必须为合法 JSON 对象'
    return
  }
  const { id, type: ignoredType, ...body } = form
  if (
    await editor.run(
      () => api.saveContent(type.value, id, { ...other, ...body }),
      '内容已保存',
    )
  ) {
    dialog.value = false
    load()
  }
}
async function remove(row) {
  if (await confirmAction(`删除“${row.title}”？该条内容将从门户移除。`))
    if (
      await task.run(() => api.deleteContent(type.value, row.id), '内容已删除')
    )
      load()
}
</script>
