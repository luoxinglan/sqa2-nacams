<template>
  <WorkPage
    title="用户管理"
    description="创建和维护教师、学生账号；所长和图书管理教师由系统管理员指定。"
  >
    <template #actions>
      <el-button @click="importOpen = true">批量导入</el-button>
      <el-button type="primary" @click="edit()">新增用户</el-button>
    </template>
    <div class="work-card">
      <form class="work-toolbar" @submit.prevent="load(true)">
        <el-input
          v-model="query.keyword"
          placeholder="检索姓名或用户名"
          aria-label="用户关键词"
          clearable
        />
        <el-select
          v-model="query.role"
          aria-label="用户角色"
          clearable
          placeholder="全部角色"
          @change="load(true)"
        >
          <el-option label="注册用户" value="user" />
          <el-option label="系统管理员" value="admin" />
        </el-select>
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
        <el-table :data="result.items" empty-text="暂无用户">
          <el-table-column prop="name" label="姓名" min-width="110" />
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column label="工号／学号" min-width="130">
            <template #default="{ row }">
              {{ row.employeeNo || row.studentNo || '无（校外兼职）' }}
            </template>
          </el-table-column>
          <el-table-column label="身份与权限" min-width="200">
            <template #default="{ row }">
              <el-tag size="small">
                {{
                  row.role === 'admin'
                    ? '管理员'
                    : row.identityType === 'teacher'
                      ? '教师'
                      : '学生'
                }}
              </el-tag>
              <el-tag v-if="row.isDirector" type="warning" size="small">
                所长
              </el-tag>
              <el-tag v-if="row.isLibrarian" type="success" size="small">
                图书管理
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              {{ row.active ? '有效' : '已停用' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="145">
            <template #default="{ row }">
              <el-button link type="primary" @click="edit(row)">编辑</el-button>
              <el-button
                link
                type="danger"
                :disabled="!row.active || task.busy.value"
                @click="remove(row)"
              >
                停用
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </PagedTable>
    </div>
    <el-dialog
      v-model="dialog"
      :title="form.id ? '编辑用户' : '新增用户'"
      width="min(920px, 96vw)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <h2>账号与权限</h2>
        <div class="work-form-grid">
          <el-form-item
            label="用户名"
            required
            :error="editor.fields.value.username"
          >
            <el-input
              v-model="form.username"
              aria-label="用户名"
              autocomplete="off"
            />
          </el-form-item>
          <el-form-item
            :label="form.id ? '重置密码（留空保留）' : '初始密码'"
            :required="!form.id"
          >
            <el-input
              v-model="form.password"
              :aria-label="form.id ? '重置密码（留空保留）' : '初始密码'"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="人员身份">
            <el-select
              v-model="form.identityType"
              :disabled="Boolean(form.id)"
              @change="identityChanged"
            >
              <el-option label="教师" value="teacher" />
              <el-option label="学生" value="student" />
              <el-option
                v-if="form.id && form.identityType === 'staff'"
                label="系统管理账号"
                value="staff"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="系统角色">
            <el-select v-model="form.role">
              <el-option label="注册用户" value="user" />
              <el-option label="系统管理员" value="admin" />
            </el-select>
          </el-form-item>
        </div>
        <div class="work-toolbar">
          <el-checkbox v-model="form.active">账号有效</el-checkbox>
          <el-checkbox
            v-if="form.identityType === 'teacher'"
            v-model="form.isDirector"
          >
            所长
          </el-checkbox>
          <el-checkbox
            v-if="form.identityType === 'teacher'"
            v-model="form.isLibrarian"
          >
            图书管理教师
          </el-checkbox>
          <el-checkbox v-model="form.nameExempt">留学生姓名豁免</el-checkbox>
        </div>
        <template v-if="form.identityType === 'teacher'">
          <div class="work-form-grid">
            <el-form-item label="教师类别">
              <el-select v-model="form.teacherType" @change="teacherChanged">
                <el-option label="全职教师" value="full" />
                <el-option label="校外兼职" value="external" />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="form.teacherType === 'full'"
              label="工号"
              required
              :error="editor.fields.value.employeeNo"
            >
              <el-input
                v-model="form.employeeNo"
                placeholder="10 位：61201 / 72201 / 74201 开头"
              />
            </el-form-item>
            <template v-else>
              <el-form-item
                label="聘书编号"
                required
                :error="editor.fields.value.appointmentNo"
              >
                <el-input v-model="form.appointmentNo" />
              </el-form-item>
              <el-form-item
                label="聘期"
                required
                :error="editor.fields.value.appointmentTerm"
              >
                <el-input v-model="form.appointmentTerm" />
              </el-form-item>
            </template>
          </div>
        </template>
        <FieldEditor
          :fields="fields"
          :model="form"
          :errors="editor.fields.value"
        />
        <el-collapse>
          <el-collapse-item title="照片在线检测与上传">
            <img
              v-if="form.photo"
              :src="form.photo"
              class="work-photo"
              alt="用户照片"
            />
            <PhotoPanel
              :key="photoKey"
              mode="admin"
              @saved="form.photo = $event.url"
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
        <el-button type="primary" :loading="editor.busy.value" @click="save">
          保存用户
        </el-button>
      </template>
    </el-dialog>
    <ImportPanel
      v-model="importOpen"
      title="批量导入用户"
      hint="表头使用英文 API 字段名，账号需有 password，教师需有 employeeNo 或聘书信息，学生需有 studentNo。"
      :uploader="(file) => api.upload('/admin/users/import', file)"
      @done="load()"
    >
      <p class="work-muted">
        最小表头：username,password,identityType,name,teacherType,employeeNo,studentNo,status。校外兼职使用
        teacherType=external、appointmentNo、appointmentTerm。导师可填所外教师姓名。
      </p>
    </ImportPanel>
  </WorkPage>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import WorkPage from '@/components/internal/WorkPage.vue'
import PagedTable from '@/components/internal/PagedTable.vue'
import FieldEditor from '@/components/internal/FieldEditor.vue'
import PhotoPanel from '@/components/internal/PhotoPanel.vue'
import ImportPanel from '@/components/internal/ImportPanel.vue'
import { usePaged, useTask, confirmAction } from '@/composables/useInternal'
import {
  COMMON_FIELDS,
  TEACHER_FIELDS,
  STUDENT_FIELDS,
  userPayload,
} from '@/utils/profileFields'
import * as api from '@/api/internal'
const { query, result, loading, loadError, load, task } = usePaged(api.users, {
  keyword: '',
  role: '',
})
const dialog = ref(false),
  importOpen = ref(false),
  editor = useTask(),
  form = reactive({}),
  photoKey = ref(0)
const fields = computed(() => [
  ...COMMON_FIELDS,
  ...(form.identityType === 'teacher'
    ? TEACHER_FIELDS
    : form.identityType === 'student'
      ? STUDENT_FIELDS
      : []),
])
function edit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(
    form,
    {
      username: '',
      password: '',
      identityType: 'student',
      role: 'user',
      active: true,
      teacherType: 'full',
      status: '在读',
      isDirector: false,
      isLibrarian: false,
      nameExempt: false,
    },
    row || {},
    { password: '' },
  )
  editor.error.value = ''
  editor.fields.value = {}
  photoKey.value++
  dialog.value = true
}
function identityChanged() {
  if (form.identityType === 'student') {
    form.isDirector = false
    form.isLibrarian = false
  }
}
function teacherChanged() {
  if (form.teacherType === 'external') form.employeeNo = ''
  else {
    form.appointmentNo = ''
    form.appointmentTerm = ''
  }
}
async function save() {
  if (
    !form.username ||
    !form.name ||
    (!form.id && !form.password) ||
    (form.password && form.password.length < 8)
  ) {
    editor.error.value = '请填写用户名、中文姓名和至少 8 个字符的初始／重置密码'
    return
  }
  const keys = [
    'username',
    'role',
    'identityType',
    'active',
    'isDirector',
    'isLibrarian',
    'nameExempt',
    'photo',
    ...fields.value.map((f) => f.key),
    ...(form.identityType === 'teacher'
      ? ['teacherType', 'employeeNo', 'appointmentNo', 'appointmentTerm']
      : []),
  ]
  const data = userPayload(form, keys)
  if (form.password)
    data[form.id ? 'resetPassword' : 'password'] = form.password
  if (await editor.run(() => api.saveUser(form.id, data), '用户已保存')) {
    dialog.value = false
    form.password = ''
    load()
  }
}
async function remove(row) {
  if (
    await confirmAction(
      `停用账号“${row.username}”？账号将无法登录，历史借阅、签到与诚信记录保留。`,
      '停用用户',
    )
  )
    if (await task.run(() => api.deleteUser(row.id), '账号已停用')) load()
}
</script>
