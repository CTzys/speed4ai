<template>
  <main class="member-register">
    <section class="card">
      <p class="eyebrow">会员中心</p>
      <h1>注册会员</h1>
      <el-tabs v-model="mode" stretch @tab-change="resetForm">
        <el-tab-pane label="手机号注册" name="mobile" />
        <el-tab-pane label="邮箱注册" name="email" />
      </el-tabs>
      <p class="description">
        {{ mode === 'mobile' ? '首次验证手机号即完成注册；已注册的手机号可直接验证登录。' : '输入邮箱并完成验证码验证，即可注册会员。' }}
      </p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item :label="mode === 'mobile' ? '手机号' : '邮箱地址'" prop="account">
            <el-input v-model="form.account" :inputmode="mode === 'mobile' ? 'tel' : 'email'" :maxlength="mode === 'mobile' ? 11 : 254" :placeholder="mode === 'mobile' ? '请输入 11 位手机号' : '请输入邮箱地址'" size="large" />
          </el-form-item>
          <el-form-item :label="mode === 'mobile' ? '短信验证码' : '邮箱验证码'" prop="code">
            <div class="code-row">
              <el-input v-model="form.code" inputmode="numeric" maxlength="6" placeholder="请输入验证码" size="large" />
              <el-button :disabled="seconds > 0 || sending" :loading="sending" size="large" @click="sendCode">
                {{ seconds > 0 ? `${seconds} 秒后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="submitting" native-type="submit">注册会员</el-button>
      </el-form>

    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { memberApi, setMemberSession } from '@/utils/memberAuth'
import type { MemberSession } from '@/utils/memberAuth'

const router = useRouter()
const mode = ref<'mobile' | 'email'>('mobile')
const formRef = ref<FormInstance>()
const form = reactive({ account: '', code: '' })
const rules = computed<FormRules>(() => ({
  account: [{ required: true, pattern: mode.value === 'mobile' ? /^1\d{10}$/ : /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: mode.value === 'mobile' ? '请输入正确的手机号' : '请输入正确的邮箱地址', trigger: 'blur' }],
  code: [{ required: true, pattern: mode.value === 'mobile' ? /^\d{4,6}$/ : /^\d{6}$/, message: mode.value === 'mobile' ? '请输入 4 至 6 位数字验证码' : '请输入 6 位数字验证码', trigger: 'blur' }]
}))
const sending = ref(false)
const submitting = ref(false)
const seconds = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

async function sendCode() {
  if (!(await formRef.value?.validateField('account').catch(() => false))) return
  sending.value = true
  try {
    if (mode.value === 'mobile') {
      await memberApi<boolean>('/member/auth/send-sms-code', { method: 'POST', body: JSON.stringify({ mobile: form.account, scene: 1 }) })
    } else {
      const testCode = await memberApi<string | null>('/member/auth/send-email-code', { method: 'POST', body: JSON.stringify({ email: form.account.trim() }) })
      if (testCode) {
        await ElMessageBox.alert(testCode, '本地测试验证码', {
          confirmButtonText: '我知道了',
          closeOnClickModal: false
        })
      } else {
        ElMessage.success('验证码已发送，请查收邮件')
      }
    }
    if (mode.value === 'mobile') ElMessage.success('验证码已发送')
    seconds.value = 60
    timer = setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0 && timer) clearInterval(timer)
    }, 1000)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    if (mode.value === 'mobile') {
      const session = await memberApi<MemberSession>('/member/auth/sms-login', { method: 'POST', body: JSON.stringify({ mobile: form.account, code: form.code }) })
      setMemberSession(session)
    } else {
      const session = await memberApi<MemberSession>('/member/auth/email-register', { method: 'POST', body: JSON.stringify({ email: form.account.trim(), code: form.code }) })
      setMemberSession(session)
    }
    await router.replace('/member-home')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证失败')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.account = ''
  form.code = ''
  seconds.value = 0
  if (timer) clearInterval(timer)
  formRef.value?.clearValidate()
}

onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.member-register { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f5f7fb; }
.card { width: min(100%, 440px); padding: 40px; border-radius: 20px; background: white; box-shadow: 0 16px 48px #16213a12; }
.eyebrow { color: #2563eb; font-weight: 700; margin: 0 0 8px; }
h1 { font-size: 30px; margin: 0 0 16px; }
.description { color: #64748b; line-height: 1.7; margin: 16px 0 28px; }
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-input { flex: 1; }
.code-row .el-button { flex-shrink: 0; }
.submit { width: 100%; margin-top: 12px; }
.success { text-align: center; padding: 20px 0; }
.success h2 { margin: 14px 0 8px; }
.success p { color: #64748b; margin-bottom: 24px; }
@media (max-width: 480px) { .card { padding: 28px 22px; } }
</style>
