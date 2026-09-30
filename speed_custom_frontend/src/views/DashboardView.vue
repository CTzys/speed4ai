<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ 'sidebar-open': mobileMenu }">
      <div class="sidebar-brand"><span class="brand-mark">S</span><span>SpeedNet <small>客户中心</small></span></div>
      <p class="sidebar-caption">主要功能</p>
      <nav aria-label="客户中心导航">
        <button v-for="item in menu" :key="item.key" class="nav-item" :class="{ active: current === item.key }" @click="select(item.key)"><component :is="item.icon" class="nav-icon" /><span>{{ item.label }}</span><span v-if="item.key !== 'home'" class="nav-chevron">›</span></button>
      </nav>
      <div class="sidebar-bottom"><div class="sidebar-help"><span class="help-icon">✦</span><strong>需要帮助？</strong><p>欢迎通过工单与我们联系</p><button @click="select('tickets')">前往工单 →</button></div><div class="sidebar-profile"><span class="profile-avatar">{{ member?.nickname?.charAt(0) || 'U' }}</span><div><strong>{{ member?.nickname || '客户' }}</strong><small>{{ member?.email || '' }}</small></div></div></div>
    </aside>
    <div v-if="mobileMenu" class="menu-scrim" @click="mobileMenu = false"></div>
    <div class="main-area">
      <header class="topbar"><button class="mobile-toggle" aria-label="打开菜单" @click="mobileMenu = !mobileMenu"><Menu /></button><div class="breadcrumbs">首页 <span>/</span> {{ currentLabel }}</div><div class="top-actions"><span class="status-pill"><span></span> 服务正常</span><el-dropdown trigger="click"><button class="top-profile"><span class="profile-avatar small">{{ member?.nickname?.charAt(0) || 'U' }}</span><span>{{ member?.nickname || '客户' }}</span><ArrowDown /></button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="select('profile')">个人资料</el-dropdown-item><el-dropdown-item divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></header>
      <main class="page-content">
        <template v-if="current === 'home'">
          <div class="page-heading"><div><p class="eyebrow">OVERVIEW · 总览</p><h1>仪表板</h1><p>账户概览与服务状态，一目了然。</p></div><span class="heading-date">{{ today }}</span></div>
          <div class="dashboard-grid"><div class="dashboard-main">
            <section class="welcome-card"><div class="welcome-copy"><div class="welcome-kicker">WELCOME BACK · 欢迎回来</div><h2>{{ member?.nickname || '客户' }}，你好 <span>👋</span></h2><p>在这里管理您的订阅、订单和服务。</p><div class="welcome-details"><div><span>账户状态</span><strong><i class="dot"></i> 正常</strong></div><div><span>当前套餐</span><strong>尚未开通</strong></div><div><span>注册邮箱</span><strong>{{ member?.email || '—' }}</strong></div></div></div><div class="welcome-decoration"><div class="orb orb-one"></div><div class="orb orb-two"></div><div class="orb-symbol">S</div></div></section>
            <section class="panel"><div class="panel-title"><div><span class="section-icon green-icon"><Monitor /></span><h3>快速开始</h3></div><span class="panel-subtitle">从选择套餐开始您的旅程</span></div><div class="platform-grid"><button class="platform-card windows" @click="select('plans')"><span class="platform-icon">▦</span><strong>订阅套餐</strong><small>查看可用的服务套餐</small><span class="card-arrow">↗</span></button><button class="platform-card android" @click="select('nodes')"><span class="platform-icon">◉</span><strong>节点列表</strong><small>了解可用节点与线路</small><span class="card-arrow">↗</span></button><button class="platform-card apple" @click="select('docs')"><span class="platform-icon">◈</span><strong>使用文档</strong><small>阅读连接与配置指南</small><span class="card-arrow">↗</span></button></div></section>
            <section class="panel"><div class="panel-title"><div><span class="section-icon purple-icon"><Link /></span><h3>订阅链接</h3></div><span class="panel-subtitle">开通套餐后即可获取</span></div><div class="empty-subscription"><span class="empty-sub-icon"><Link /></span><div><strong>暂无可用订阅</strong><p>购买套餐后，您的专属订阅链接将在这里显示。</p></div><el-button type="primary" plain @click="select('plans')">浏览套餐</el-button></div></section>
          </div><div class="dashboard-side"><section class="panel notice-panel"><div class="panel-title"><div><span class="section-icon blue-icon"><Bell /></span><h3>重要通知</h3></div></div><div class="notice-empty"><span>✦</span><strong>欢迎加入 SpeedNet</strong><p>您已成功登录客户中心。新公告将在这里展示。</p></div></section><section class="panel traffic-panel"><div class="panel-title"><div><span class="section-icon blue-icon"><TrendCharts /></span><h3>流量使用情况</h3></div></div><div class="traffic-meter"><div class="traffic-ring"><div><strong>0%</strong><span>已使用流量</span></div></div><p>尚未开通套餐</p></div><div class="traffic-stats"><div><span>总流量</span><strong>—</strong></div><div><span>剩余流量</span><strong>—</strong></div></div></section><section class="tip-card"><span>✧</span><div><strong>小提示</strong><p>开通套餐后，您可以在这里查看订阅与用量。</p></div></section></div></div>
        </template>
        <template v-else-if="current === 'profile'"><div class="page-heading"><div><p class="eyebrow">ACCOUNT · 账户</p><h1>个人资料</h1><p>查看您的客户账户信息。</p></div></div><section class="panel profile-panel"><span class="profile-avatar large">{{ member?.nickname?.charAt(0) || 'U' }}</span><div><h2>{{ member?.nickname }}</h2><p>{{ member?.email }}</p><el-tag type="success">账户正常</el-tag></div></section></template>
        <template v-else><div class="page-heading"><div><p class="eyebrow">SPEEDNET · 服务</p><h1>{{ currentLabel }}</h1><p>此功能将在后续开发阶段开放。</p></div></div><section class="panel feature-placeholder"><span>✦</span><h2>{{ currentLabel }}即将上线</h2><p>注册与登录功能已可用，套餐、订单和订阅模块将按开发文档逐步接入。</p><el-button type="primary" @click="select('home')">返回仪表板</el-button></section></template>
      </main>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { House, Cloudy, Coin, Tickets, ChatDotRound, TrendCharts, User, Document, Menu, ArrowDown, Bell, Link, Monitor, DataLine } from '@element-plus/icons-vue'
import { api, clearSession, type Member } from '../api'

const router = useRouter()
const route = useRoute()
const member = ref<Member | null>(null)
const mobileMenu = ref(false)
const current = ref('home')
const today = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const menu = [
  { key: 'home', label: '首页', icon: House }, { key: 'plans', label: '订阅套餐', icon: Cloudy },
  { key: 'nodes', label: '节点列表', icon: DataLine }, { key: 'orders', label: '订单管理', icon: Tickets },
  { key: 'tickets', label: '工单系统', icon: ChatDotRound }, { key: 'usage', label: '流量明细', icon: TrendCharts },
  { key: 'profile', label: '个人资料', icon: User }, { key: 'docs', label: '使用文档', icon: Document }
]
const currentLabel = computed(() => menu.find(item => item.key === current.value)?.label || '仪表板')
function select(key: string) { current.value = key; mobileMenu.value = false; window.scrollTo(0, 0) }
async function logout() {
  try { await api.logout() } catch { /* Always discard the local session. */ }
  clearSession()
  await router.replace('/login')
}
onMounted(async () => {
  if (import.meta.env.DEV && route.query.preview === '1') {
    member.value = { id: 0, nickname: '演示用户', email: 'demo@example.com', avatar: null }
    return
  }
  try { member.value = await api.me() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '获取账户信息失败'); clearSession(); await router.replace('/login') }
})
</script>
