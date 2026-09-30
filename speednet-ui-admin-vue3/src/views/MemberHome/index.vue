<template>
  <div class="member-shell">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed, 'mobile-open': mobileMenuOpen }">
      <div class="brand">
        <div class="brand-mark">S</div>
        <div class="brand-copy"><strong>SpeedNet</strong><span>会员服务中心</span></div>
      </div>
      <nav class="side-nav" aria-label="会员导航">
        <button class="nav-item" :class="{ active: activePage === 'home' }" @click="openPage('home')">
          <el-icon><House /></el-icon><span>首页</span>
        </button>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('使用文档')">
          <el-icon><Document /></el-icon><span>使用文档</span>
        </button>
        <p class="nav-group">商店</p>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('购买订阅')">
          <el-icon><ShoppingBag /></el-icon><span>购买订阅</span><span class="new-badge">New</span>
        </button>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('节点状态')">
          <el-icon><Monitor /></el-icon><span>节点状态</span>
        </button>
        <p class="nav-group">财务</p>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('我的订单')">
          <el-icon><Tickets /></el-icon><span>我的订单</span>
        </button>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('我的邀请')">
          <el-icon><Share /></el-icon><span>我的邀请</span>
        </button>
        <p class="nav-group">用户</p>
        <button class="nav-item" :class="{ active: activePage === 'profile' }" @click="openPage('profile')">
          <el-icon><User /></el-icon><span>个人中心</span>
        </button>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('使用明细')">
          <el-icon><DataLine /></el-icon><span>使用明细</span>
        </button>
        <button class="nav-item disabled" title="即将开放" @click="comingSoon('我的工单')">
          <el-icon><ChatLineRound /></el-icon><span>我的工单</span>
        </button>
      </nav>
      <div class="sidebar-foot">
        <span class="status-dot"></span><span>会员服务</span>
        <button class="logout-link" title="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></button>
      </div>
    </aside>

    <div v-if="mobileMenuOpen" class="mobile-overlay" @click="mobileMenuOpen = false"></div>
    <div class="main-column">
      <header class="topbar">
        <button class="icon-button menu-button" aria-label="切换导航" @click="toggleMenu">
          <el-icon><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
        </button>
        <div class="top-actions">
          <button class="icon-button" aria-label="通知" title="通知功能即将开放" @click="comingSoon('通知')"><el-icon><Bell /></el-icon></button>
          <span class="top-divider"></span>
          <button class="account-button" @click="openPage('profile')">
            <span class="avatar">{{ avatarInitial }}</span>
            <span class="account-name">{{ profile?.nickname || '会员' }}</span>
          </button>
        </div>
      </header>

      <main class="page-content" v-loading="loading">
        <template v-if="activePage === 'home'">
          <div class="page-heading">
            <div><p class="eyebrow">MEMBER DASHBOARD</p><h1>首页</h1><p class="subheading">欢迎回来，{{ profile?.nickname || '会员' }}</p></div>
            <div class="heading-actions">
              <el-button class="secondary-action" @click="comingSoon('下载客户端')"><el-icon><Download /></el-icon> 下载客户端</el-button>
              <el-button type="primary" @click="comingSoon('续费订阅')"><el-icon><ShoppingBag /></el-icon> 续费订阅</el-button>
            </div>
          </div>

          <section class="metrics" aria-label="会员数据概览">
            <div class="metric-card"><span class="metric-label">钱包余额</span><strong>-- <small>CNY</small></strong><span class="metric-note">余额功能尚未开通</span></div>
            <div class="metric-card"><span class="metric-label">总流量 / 剩余流量</span><strong>-- <small>GB</small> / -- <small>GB</small></strong><span class="metric-note">暂无流量数据</span></div>
            <div class="metric-card"><span class="metric-label">会员积分</span><strong>{{ profile?.point ?? '--' }} <small>分</small></strong><span class="metric-note">当前会员积分</span></div>
            <div class="metric-card"><span class="metric-label">剩余天数</span><strong>-- <small>天</small></strong><span class="metric-note">暂无有效订阅</span></div>
          </section>

          <div class="feature-grid">
            <section class="panel subscription-panel">
              <div class="panel-heading"><h2>我的订阅</h2><button class="text-button" @click="refreshProfile"><el-icon><Refresh /></el-icon> 刷新</button></div>
              <div class="subscription-empty">
                <span class="empty-icon"><el-icon><ShoppingBag /></el-icon></span>
                <h3>暂无订阅套餐</h3>
                <p>订阅服务开放后，您可以在这里查看套餐、有效期和使用情况。</p>
                <el-button type="primary" plain @click="comingSoon('购买订阅')">了解订阅服务</el-button>
              </div>
              <div class="quick-tip"><span class="tip-bullet"></span><span>账户已就绪。订阅与流量数据将在服务接入后显示。</span></div>
            </section>

            <section class="panel import-panel">
              <div class="panel-heading"><h2>快捷导入订阅</h2></div>
              <div class="import-content">
                <p class="section-caption">订阅工具</p>
                <div class="import-actions">
                  <button v-for="item in importTools" :key="item.name" :style="{ '--button-color': item.color }" @click="comingSoon(item.name)">{{ item.name }}</button>
                </div>
                <div class="client-heading"><span>客户端下载</span></div>
                <div class="platforms">
                  <button v-for="item in platforms" :key="item.name" @click="comingSoon(`${item.name} 客户端`)">
                    <span class="platform-symbol">{{ item.symbol }}</span><span>{{ item.name }}</span>
                  </button>
                </div>
                <p class="muted-hint">订阅与客户端下载入口将在服务上线后开放。</p>
              </div>
            </section>
          </div>

          <div class="lower-grid">
            <section class="panel trend-panel">
              <div class="panel-heading"><h2>使用趋势</h2><span class="panel-meta">最近 30 天</span></div>
              <div class="chart-empty"><el-icon><DataLine /></el-icon><span>暂无使用记录</span><small>开通订阅后，使用趋势将在这里展示</small></div>
            </section>
            <section class="panel plan-panel">
              <span class="plan-icon"><el-icon><ShoppingBag /></el-icon></span>
              <h2>订阅套餐</h2>
              <p>探索适合您的会员服务</p>
              <button @click="comingSoon('订阅套餐')">敬请期待 <el-icon><ArrowRight /></el-icon></button>
            </section>
          </div>
        </template>

        <template v-else>
          <div class="page-heading"><div><p class="eyebrow">MEMBER PROFILE</p><h1>个人中心</h1><p class="subheading">查看当前会员资料</p></div></div>
          <section class="panel profile-panel">
            <div class="profile-top"><span class="profile-avatar">{{ avatarInitial }}</span><div><h2>{{ profile?.nickname || '会员' }}</h2><p>会员编号 #{{ profile?.id || '--' }}</p></div></div>
            <dl class="profile-list"><div><dt>邮箱</dt><dd>{{ profile?.email || '未绑定' }}</dd></div><div><dt>手机号</dt><dd>{{ profile?.mobile || '未绑定' }}</dd></div><div><dt>会员积分</dt><dd>{{ profile?.point ?? '--' }}</dd></div></dl>
            <div class="profile-actions"><el-button @click="refreshProfile">刷新资料</el-button><el-button @click="logout">退出登录</el-button></div>
          </section>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { House, Document, ShoppingBag, Monitor, Tickets, Share, User, DataLine, ChatLineRound, SwitchButton, Fold, Expand, Bell, Download, Refresh, ArrowRight } from '@element-plus/icons-vue'
import { clearMemberSession, getMemberSession, memberApi } from '@/utils/memberAuth'

interface MemberProfile { id: number; nickname: string; mobile?: string; email?: string; point?: number }
const router = useRouter()
const loading = ref(true)
const profile = ref<MemberProfile | null>(null)
const activePage = ref<'home' | 'profile'>('home')
const sidebarCollapsed = ref(false)
const mobileMenuOpen = ref(false)
const avatarInitial = computed(() => (profile.value?.nickname || 'M').slice(0, 1).toUpperCase())
const importTools = [
  { name: '复制链接', color: '#1677ef' }, { name: '二维码导入', color: '#149ab1' },
  { name: 'Surge 订阅', color: '#6d28d9' }, { name: 'Shadowrocket 订阅', color: '#7044c4' },
  { name: 'Quantumult X 订阅', color: '#df3754' }, { name: 'Clash 订阅', color: '#f18a14' }
]
const platforms = [
  { name: 'Windows', symbol: '⊞' }, { name: 'Android', symbol: '◉' },
  { name: 'iOS', symbol: '●' }, { name: 'macOS', symbol: '⌘' },
  { name: 'Linux', symbol: '◎' }, { name: 'OpenWrt', symbol: '⚙' }
]

function comingSoon(name: string) { ElMessage.info(`${name}功能即将开放`) }
function openPage(page: 'home' | 'profile') { activePage.value = page; mobileMenuOpen.value = false }
function toggleMenu() {
  if (window.innerWidth <= 760) mobileMenuOpen.value = !mobileMenuOpen.value
  else sidebarCollapsed.value = !sidebarCollapsed.value
}
async function refreshProfile() {
  loading.value = true
  try { profile.value = await memberApi<MemberProfile>('/member/user/get') }
  catch { clearMemberSession(); await router.replace('/member-register') }
  finally { loading.value = false }
}
onMounted(async () => {
  if (!getMemberSession()) { await router.replace('/member-register'); return }
  await refreshProfile()
})
async function logout() {
  try { await memberApi('/member/auth/logout', { method: 'POST' }) }
  finally { clearMemberSession(); await router.replace('/member-register') }
}
</script>

<style scoped>
.member-shell { --line: #e7ecf5; --ink: #202a3a; --muted: #8290a3; min-height: 100vh; display: flex; background: #fbfcff; color: var(--ink); }
button { font: inherit; cursor: pointer; }
.sidebar { width: 260px; min-width: 260px; min-height: 100vh; background: #fff; border-right: 1px solid var(--line); display: flex; flex-direction: column; transition: width .2s ease, min-width .2s ease; z-index: 20; }
.brand { height: 76px; padding: 0 28px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--line); white-space: nowrap; overflow: hidden; }
.brand-mark { width: 34px; height: 34px; flex: none; display: grid; place-items: center; border-radius: 10px; background: linear-gradient(135deg,#2777f5,#55afff); color: #fff; font-weight: 800; font-size: 21px; transform: rotate(-8deg); }
.brand-copy { display: flex; flex-direction: column; line-height: 1.15; }.brand-copy strong { font-size: 17px; }.brand-copy span { color: var(--muted); font-size: 10px; letter-spacing: 1px; margin-top: 4px; }
.side-nav { padding: 24px 15px; flex: 1; overflow-y: auto; }.nav-group { margin: 31px 12px 12px; color: #8795a9; font-size: 12px; font-weight: 700; }
.nav-item { width: 100%; min-height: 46px; border: 0; background: transparent; color: #384762; display: flex; align-items: center; gap: 14px; padding: 0 15px; border-radius: 9px; text-align: left; font-size: 14px; font-weight: 600; white-space: nowrap; }.nav-item .el-icon { font-size: 18px; color: #74839c; }.nav-item:hover { background: #f4f7fd; }.nav-item.active { color: #2472ee; background: #edf4ff; }.nav-item.active .el-icon { color: #2472ee; }.nav-item.disabled { color: #536078; }.new-badge { margin-left: auto; padding: 3px 7px; background: #139db3; color: white; border-radius: 12px; font-size: 10px; }
.sidebar-foot { min-height: 62px; display: flex; align-items: center; gap: 9px; padding: 0 26px; border-top: 1px solid var(--line); color: #74839c; font-size: 12px; }.status-dot { width: 7px; height: 7px; background: #19b79d; border-radius: 50%; }.logout-link { margin-left: auto; border: 0; background: transparent; color: #73829a; font-size: 18px; }.logout-link:hover { color: #ea5555; }
.sidebar.collapsed { width: 76px; min-width: 76px; }.sidebar.collapsed .brand { padding: 0 21px; }.sidebar.collapsed .brand-copy,.sidebar.collapsed .nav-item span,.sidebar.collapsed .nav-group,.sidebar.collapsed .sidebar-foot span:not(.status-dot) { display: none; }.sidebar.collapsed .nav-item { justify-content: center; padding: 0; }.sidebar.collapsed .sidebar-foot { padding: 0 12px; }
.main-column { min-width: 0; flex: 1; }.topbar { height: 76px; background: white; border-bottom: 1px solid var(--line); display: flex; align-items: center; justify-content: space-between; padding: 0 28px; }.icon-button { border: 0; background: transparent; color: #8291a9; width: 37px; height: 37px; display: grid; place-items: center; font-size: 18px; border-radius: 8px; }.icon-button:hover { background: #f1f5fc; }.top-actions { display: flex; align-items: center; gap: 17px; }.top-divider { height: 22px; border-left: 1px solid var(--line); }.account-button { display: flex; align-items: center; gap: 10px; border: 0; background: transparent; color: #273349; font-weight: 700; }.avatar { width: 33px; height: 33px; display: grid; place-items: center; background: #e7f3ff; color: #2472ee; border-radius: 50%; }
.page-content { max-width: 1820px; margin: 0 auto; padding: 30px; }.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; border-bottom: 1px solid var(--line); padding-bottom: 27px; margin-bottom: 30px; }.eyebrow { color: #7185a6; font-size: 11px; font-weight: 700; letter-spacing: 1.2px; margin: 0 0 7px; }.page-heading h1 { font-size: 26px; line-height: 1.25; margin: 0; }.subheading { color: var(--muted); font-size: 13px; margin: 8px 0 0; }.heading-actions { display: flex; gap: 10px; padding-top: 4px; }.heading-actions .el-button { height: 41px; margin: 0; font-weight: 600; }.heading-actions .el-icon { margin-right: 6px; }.secondary-action { color: #2777f5; border-color: #d5e3fb; }
.metrics { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 22px; margin-bottom: 30px; }.metric-card,.panel { background: #fff; border: 1px solid #e4eaf4; border-radius: 13px; box-shadow: 0 5px 18px #293e6508; }.metric-card { min-height: 134px; display: flex; flex-direction: column; justify-content: center; padding: 22px 24px; }.metric-label { color: #8290a4; font-size: 13px; font-weight: 600; }.metric-card strong { font-size: 27px; line-height: 1.3; margin: 8px 0 3px; letter-spacing: -.4px; white-space: nowrap; }.metric-card strong small { font-size: 15px; }.metric-note { color: #8795a8; font-size: 12px; }
.feature-grid { display: grid; grid-template-columns: minmax(300px, 1fr) minmax(420px, 1.34fr); gap: 24px; }.panel-heading { min-height: 58px; padding: 0 23px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); }.panel-heading h2 { margin: 0; font-size: 17px; }.text-button { border: 0; color: #7586a0; background: transparent; display: flex; align-items: center; gap: 5px; }.text-button:hover { color: #2777f5; }
.subscription-empty { min-height: 290px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 28px; }.empty-icon { width: 54px; height: 54px; display: grid; place-items: center; border-radius: 15px; background: #eff5ff; color: #4385ed; font-size: 27px; }.subscription-empty h3 { margin: 15px 0 4px; font-size: 17px; }.subscription-empty p { max-width: 350px; color: #8a97a9; font-size: 13px; line-height: 1.65; }.subscription-empty .el-button { margin-top: 10px; }.quick-tip { min-height: 69px; margin: 0 24px; border-top: 1px solid var(--line); display: flex; align-items: center; gap: 10px; color: #8290a4; font-size: 12px; }.tip-bullet { width: 8px; height: 8px; background: #4d97f4; border-radius: 50%; }
.import-content { padding: 24px; }.section-caption { margin: 0 0 14px; color: #8492a5; font-size: 12px; font-weight: 600; }.import-actions { display: flex; flex-wrap: wrap; gap: 9px; min-height: 104px; align-content: start; }.import-actions button { background: var(--button-color); border: 0; border-radius: 6px; color: #fff; padding: 11px 13px; font-weight: 600; font-size: 13px; }.import-actions button:hover { filter: brightness(.94); }.client-heading { margin: 18px 0 18px; color: #8a97a8; font-size: 13px; font-weight: 600; text-align: center; border-bottom: 1px solid var(--line); line-height: 0; }.client-heading span { background: #fff; padding: 0 12px; }.platforms { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 12px; }.platforms button { border: 0; background: transparent; color: #718096; display: flex; flex-direction: column; align-items: center; gap: 8px; font-size: 12px; }.platform-symbol { height: 65px; width: 100%; max-width: 76px; display: grid; place-items: center; border: 1px solid #d9e1ef; border-radius: 6px; color: #1b2b49; font-size: 31px; line-height: 1; }.platforms button:hover .platform-symbol { border-color: #4385ed; }.muted-hint { color: #a3adbc; font-size: 11px; text-align: center; margin: 20px 0 0; }
.lower-grid { display: grid; grid-template-columns: minmax(300px, 1.8fr) minmax(220px, .55fr); gap: 24px; margin-top: 24px; }.trend-panel { min-height: 262px; }.panel-meta { color: #9aa7b8; font-size: 12px; }.chart-empty { min-height: 205px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #a0adc0; }.chart-empty .el-icon { font-size: 32px; color: #bdc9d9; }.chart-empty small { font-size: 12px; }.plan-panel { min-height: 262px; padding: 30px; display: flex; flex-direction: column; align-items: flex-start; }.plan-icon { display: grid; place-items: center; height: 48px; width: 48px; border-radius: 12px; background: #eef5ff; color: #2878ee; font-size: 26px; }.plan-panel h2 { margin: 20px 0 2px; font-size: 18px; }.plan-panel p { color: #8996a8; margin: 0; font-size: 13px; }.plan-panel button { margin-top: auto; border: 0; background: transparent; color: #2d78ea; padding: 0; display: flex; align-items: center; gap: 5px; font-weight: 700; }
.profile-panel { max-width: 740px; padding: 28px; }.profile-top { display: flex; align-items: center; gap: 16px; padding-bottom: 26px; border-bottom: 1px solid var(--line); }.profile-avatar { width: 58px; height: 58px; display: grid; place-items: center; border-radius: 50%; background: #e6f0ff; color: #2374ed; font-size: 24px; font-weight: 700; }.profile-top h2 { margin: 0 0 5px; }.profile-top p { margin: 0; color: #9aa7b8; }.profile-list { margin: 0; }.profile-list div { display: flex; justify-content: space-between; gap: 20px; padding: 19px 0; border-bottom: 1px solid var(--line); }.profile-list dt { color: #8290a4; }.profile-list dd { margin: 0; font-weight: 600; }.profile-actions { display: flex; gap: 10px; margin-top: 25px; }
.mobile-overlay { display: none; }
@media (max-width: 1200px) { .metrics { grid-template-columns: repeat(2,minmax(0,1fr)); }.feature-grid { grid-template-columns: 1fr; }.lower-grid { grid-template-columns: 1fr 280px; } }
@media (max-width: 760px) { .sidebar { position: fixed; left: 0; top: 0; bottom: 0; transform: translateX(-100%); box-shadow: 12px 0 28px #1c31501a; }.sidebar.mobile-open { transform: translateX(0); }.mobile-overlay { display: block; position: fixed; inset: 0; background: #15274466; z-index: 10; }.topbar { height: 64px; padding: 0 16px; }.page-content { padding: 20px 16px; }.page-heading { flex-direction: column; padding-bottom: 20px; }.heading-actions { width: 100%; }.heading-actions .el-button { flex: 1; }.metrics { gap: 12px; margin-bottom: 18px; }.metric-card { padding: 16px; min-height: 118px; }.metric-card strong { font-size: 21px; }.feature-grid,.lower-grid { gap: 16px; }.lower-grid { grid-template-columns: 1fr; }.platforms { grid-template-columns: repeat(3,minmax(0,1fr)); row-gap: 18px; }.account-name { display: none; } }
@media (max-width: 400px) { .metrics { grid-template-columns: 1fr; }.import-actions button { font-size: 12px; }.platforms { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
