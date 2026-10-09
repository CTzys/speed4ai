<template>
  <main class="auth-page">
    <div class="auth-art"><div class="brand"><span class="brand-mark">S</span><span>SpeedNet</span></div><div class="auth-art-content"><p class="overline">加入 SpeedNet</p><h1>新的旅程，<br>从这里开始。</h1><p>创建账户后，即可进入专属客户中心。</p><div class="art-orbit"><span>✦</span></div></div><div class="art-footer">安全 · 稳定 · 简单</div></div>
    <div class="auth-form-area"><div class="auth-top-link">已有账户？<router-link to="/login">返回登录 →</router-link></div><div class="auth-card"><div class="small-brand"><span class="brand-mark">S</span> SpeedNet</div><p class="overline green">创建账户</p><h2>注册客户中心</h2><p class="muted">验证您的邮箱并设置密码，几步即可完成。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱地址" prop="email"><el-input v-model="form.email" size="large" placeholder="you@example.com" autocomplete="email" /></el-form-item>
        <el-form-item label="邮箱验证码" prop="code"><div class="code-row"><el-input v-model="form.code" size="large" maxlength="6" inputmode="numeric" placeholder="六位验证码" /><el-button size="large" :loading="sending" :disabled="remaining > 0" @click="sendCode">{{ remaining > 0 ? `${remaining} 秒` : '获取验证码' }}</el-button></div></el-form-item>
        <el-form-item label="设置密码" prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="至少 8 位" autocomplete="new-password" /></el-form-item>
        <el-form-item label="确认密码" prop="confirm"><el-input v-model="form.confirm" size="large" type="password" show-password placeholder="再次输入密码" autocomplete="new-password" /></el-form-item>
        <el-button class="full-button" type="primary" size="large" :loading="loading" native-type="submit">创建账户 <span aria-hidden="true">→</span></el-button>
      </el-form><p class="auth-hint">注册即表示您同意遵守本站服务规则。</p>
    </div><div class="auth-bottom"><router-link to="/">← 返回网站首页</router-link><br><br>© 2026 SpeedNet · 客户中心</div></div>
  </main>
</template>
<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { api, saveSession } from '../api'

const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ email: '', code: '', password: '', confirm: '' })
const loading = ref(false), sending = ref(false), remaining = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
const rules: FormRules = {
  email: [{ required: true, type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  code: [{ required: true, pattern: /^\d{6}$/, message: '请输入六位数字验证码', trigger: 'blur' }],
  password: [{ required: true, min: 8, max: 72, message: '密码长度应为 8 到 72 位', trigger: 'blur' }],
  confirm: [{ required: true, validator: (_rule, value, callback) => callback(value === form.password ? undefined : new Error('两次密码输入不一致')), trigger: 'blur' }]
}
async function sendCode() {
  if (!(await formRef.value?.validateField('email').catch(() => false))) return
  sending.value = true
  try {
    const localCode = await api.sendCode(form.email.trim())
    if (localCode) {
      form.code = localCode
      await ElMessageBox.alert(localCode, '测试环境验证码（已自动填写）', { confirmButtonText: '知道了' })
    }
    else ElMessage.success('验证码已发送，请查收邮件')
    remaining.value = 60
    timer = setInterval(() => { remaining.value--; if (remaining.value <= 0 && timer) clearInterval(timer) }, 1000)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '发送失败') }
  finally { sending.value = false }
}
async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    const session = await api.register(form.email.trim(), form.code, form.password)
    saveSession(session)
    ElMessage.success('注册成功，欢迎来到 SpeedNet')
    await router.replace('/dashboard')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '注册失败') }
  finally { loading.value = false }
}
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>
