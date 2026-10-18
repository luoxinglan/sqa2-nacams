<template>
  <WorkPage
    title="个人档案"
    description="维护联系方式、英文姓名和照片；身份及学籍资料由管理员统一管理。"
  >
    <div class="work-card">
      <h2>身份资料</h2>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">
          {{ auth.user?.name }}
        </el-descriptions-item>
        <el-descriptions-item label="账号">
          {{ auth.user?.username }}
        </el-descriptions-item>
        <el-descriptions-item label="身份">
          {{
            auth.user?.identityType === 'teacher'
              ? '教师'
              : auth.user?.identityType === 'student'
                ? '学生'
                : '系统管理账号'
          }}
        </el-descriptions-item>
        <el-descriptions-item label="工号／学号">
          {{ auth.user?.employeeNo || auth.user?.studentNo || '无' }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
    <div class="work-card">
      <h2>可修改资料</h2>
      <el-form label-position="top" @submit.prevent="save">
        <FieldEditor
          :fields="SELF_FIELDS"
          :model="form"
          :errors="task.fields.value"
        />
        <p v-if="task.error.value" class="work-form-message" role="alert">
          {{ task.error.value }}
        </p>
        <el-button
          type="primary"
          native-type="submit"
          :loading="task.busy.value"
        >
          保存档案
        </el-button>
      </el-form>
    </div>
    <div class="work-grid">
      <div class="work-card">
        <h2>照片在线检测</h2>
        <img
          v-if="auth.user?.photo"
          :src="auth.user.photo"
          class="work-photo"
          alt="当前档案照片"
        />
        <PhotoPanel mode="self" @saved="auth.setUser" />
      </div>
      <div class="work-card">
        <h2>修改密码</h2>
        <el-form label-position="top" @submit.prevent="password">
          <el-form-item label="原密码">
            <el-input
              v-model="secrets.old"
              type="password"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input
              v-model="secrets.new"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="secrets.confirm"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <p class="work-muted">密码至少 8 个字符。保存成功后需重新登录。</p>
          <p v-if="security.error.value" class="work-form-message" role="alert">
            {{ security.error.value }}
          </p>
          <el-button
            type="primary"
            native-type="submit"
            :loading="security.busy.value"
          >
            更新密码
          </el-button>
        </el-form>
      </div>
    </div>
  </WorkPage>
</template>
<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import WorkPage from '@/components/internal/WorkPage.vue'
import FieldEditor from '@/components/internal/FieldEditor.vue'
import PhotoPanel from '@/components/internal/PhotoPanel.vue'
import { SELF_FIELDS, userPayload } from '@/utils/profileFields'
import { useAuthStore } from '@/stores/auth'
import { useTask } from '@/composables/useInternal'
import { saveProfile, changePassword } from '@/api/internal'
const auth = useAuthStore(),
  router = useRouter(),
  task = useTask(),
  security = useTask()
const form = reactive(
  Object.fromEntries(SELF_FIELDS.map((f) => [f.key, auth.user?.[f.key] || ''])),
)
const secrets = reactive({ old: '', new: '', confirm: '' })
async function save() {
  const response = await task.run(
    () =>
      saveProfile(
        userPayload(
          form,
          SELF_FIELDS.map((f) => f.key),
        ),
      ),
    '个人档案已保存',
  )
  if (response) auth.setUser(response.result)
}
async function password() {
  if (
    !secrets.old ||
    secrets.new.length < 8 ||
    secrets.new !== secrets.confirm
  ) {
    security.error.value = '请输入原密码，新密码至少 8 个字符且两次输入一致'
    return
  }
  if (
    await security.run(
      () => changePassword({ old: secrets.old, new: secrets.new }),
      '密码已修改，请重新登录',
    )
  ) {
    auth.clearSession()
    router.replace('/login')
  }
}
</script>
