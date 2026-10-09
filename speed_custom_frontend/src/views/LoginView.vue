<template>
  <main class="auth-page">
    <div class="auth-art"><div class="brand"><span class="brand-mark">S</span><span>SpeedNet</span></div><div class="auth-art-content"><p class="overline">连接世界 · 自由探索</p><h1>一个账户，<br>开启轻松连接。</h1><p>随时查看订阅、订单与服务状态，所有信息都在这里。</p><div class="art-orbit"><span>✦</span></div></div><div class="art-footer">安全 · 稳定 · 简单</div></div>
    <div class="auth-form-area"><div class="auth-top-link">还没有账户？<router-link to="/register">立即注册 →</router-link></div><div class="auth-card"><div class="small-brand"><span class="brand-mark">S</span> SpeedNet</div><p class="overline green">欢迎回来</p><h2>登录客户中心</h2><p class="muted">输入注册邮箱和密码，继续使用您的账户。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱地址" prop="email"><el-input v-model="form.email" size="large" placeholder="you@example.com" autocomplete="email" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" autocomplete="current-password" /></el-form-item>
        <el-button class="full-button" type="primary" size="large" :loading="loading" native-type="submit">登录账户 <span aria-hidden="true">→</span></el-button>
      </el-form><p class="auth-hint">忘记密码？请联系管理员重置。自助找回将在后续版本提供。</p>
    </div><div class="auth-bottom"><router-link to="/">← 返回网站首页</router-link><br><br>© 2026 SpeedNet · 客户中心</div></div>
  </main>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { api, saveSession } from '../api'

const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ email: '', password: '' })
const loading = ref(false)
const rules: FormRules = {
  email: [{ required: true, type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    const session = await api.login(form.email.trim(), form.password)
    saveSession(session)
    ElMessage.success('登录成功')
    await router.replace('/dashboard')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败') }
  finally { loading.value = false }
}
</script>
