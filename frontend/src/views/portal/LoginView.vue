<template>
  <div class="login-page">
    <!-- 登录页（验收 C-03）：体现研究所 LOGO、系统名、著作权标志 -->
    <div class="login-card">
      <div class="login-head">
        <img :src="site.logoUrl" alt="研究所LOGO" class="login-logo" />
        <h1 class="login-title">{{ site.sysName }}</h1>
        <p class="login-names">
          {{ site.nameZh }} · {{ site.nameEn }}
        </p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" aria-label="用户名" placeholder="请输入用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" aria-label="密码" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="serverError" :title="serverError" type="error" show-icon :closable="false" style="margin-bottom: 16px" />
        <el-button type="primary" class="login-btn" :loading="submitting" @click="submit">
          登 录
        </el-button>
      </el-form>

      <p class="login-tip">账号由系统管理员统一创建与发放；登录即视为签到，登出即视为签出。</p>
      <p v-if="isMock" class="login-demo">
        演示账号：admin / Admin@123（管理员）· director · librarian · teacher · student（密码 Bit@123456）
      </p>
    </div>

    <p class="login-copyright">{{ site.copyright }}</p>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useSiteStore } from '@/stores/site'
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/utils/internal'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()
const site = computed(() => siteStore.site)
const auth = useAuthStore()
const isMock = import.meta.env.VITE_USE_MOCK === '1'

const formRef = ref()
const submitting = ref(false)
const serverError = ref('')
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => siteStore.load())

async function submit() {
  if (submitting.value || !await formRef.value.validate().catch(() => false)) return
  submitting.value = true
  serverError.value = ''
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功，已自动签到')
    router.push(safeRedirect(route.query.redirect))
  } catch (e) {
    // 网络级错误已由请求层统一弹提示；此处兜底 422 字段错误
    if (e.fields && Object.keys(e.fields).length) {
      serverError.value = Object.values(e.fields).join('；')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  background:
    linear-gradient(rgba(12, 42, 94, 0.92), rgba(15, 50, 110, 0.94)),
    repeating-linear-gradient(0deg, transparent 0 31px, rgba(143, 195, 255, 0.06) 31px 32px),
    repeating-linear-gradient(90deg, transparent 0 31px, rgba(143, 195, 255, 0.06) 31px 32px),
    var(--nacams-navy);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 34px 36px 22px;
  box-shadow: 0 12px 40px rgba(4, 18, 46, 0.45);
}

.login-head {
  text-align: center;
  margin-bottom: 22px;
}

.login-logo {
  width: 64px;
  height: 64px;
}

.login-title {
  margin: 10px 0 6px;
  font-size: 21px;
  color: var(--nacams-navy);
  letter-spacing: 1px;
}

.login-names {
  margin: 0;
  font-size: 12px;
  color: var(--nacams-muted);
}

.login-btn {
  width: 100%;
  letter-spacing: 6px;
}

.login-tip {
  margin: 14px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}

.login-demo {
  margin: 6px 0 0;
  font-size: 12px;
  color: #b6c2d8;
  text-align: center;
}

.login-copyright {
  color: rgba(201, 214, 236, 0.85);
  font-size: 13px;
  margin: 0;
}
</style>
