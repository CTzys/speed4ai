<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ 'sidebar-open': mobileMenu }">
      <div class="sidebar-brand">
        <span class="brand-mark">S</span
        ><span>SpeedNet <small>客户中心</small></span>
      </div>
      <p class="sidebar-caption">主要功能</p>
      <nav aria-label="客户中心导航">
        <button
          v-for="item in menu"
          :key="item.key"
          class="nav-item"
          :class="{ active: current === item.key }"
          @click="select(item.key)"
        >
          <component :is="item.icon" class="nav-icon" /><span>{{
            item.label
          }}</span
          ><el-tag
            v-if="item.key === 'tickets' && ticketUnread"
            type="danger"
            size="small"
            >{{ ticketUnread }}</el-tag
          ><span v-if="item.key !== 'home'" class="nav-chevron">›</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-help">
          <span class="help-icon">✦</span><strong>需要帮助？</strong>
          <p>欢迎通过工单与我们联系</p>
          <button @click="select('tickets')">前往工单 →</button>
        </div>
        <div class="sidebar-profile">
          <span class="profile-avatar">{{
            member?.nickname?.charAt(0) || "U"
          }}</span>
          <div>
            <strong>{{ member?.nickname || "客户" }}</strong
            ><small>{{ member?.email || "" }}</small>
          </div>
        </div>
      </div>
    </aside>
    <div v-if="mobileMenu" class="menu-scrim" @click="mobileMenu = false"></div>
    <div class="main-area">
      <header class="topbar">
        <button
          class="mobile-toggle"
          aria-label="打开菜单"
          @click="mobileMenu = !mobileMenu"
        >
          <Menu />
        </button>
        <div class="breadcrumbs">首页 <span>/</span> {{ currentLabel }}</div>
        <div class="top-actions">
          <span class="status-pill"><span></span> 客户中心</span
          ><el-dropdown trigger="click"
            ><button class="top-profile">
              <span class="profile-avatar small">{{
                member?.nickname?.charAt(0) || "U"
              }}</span
              ><span>{{ member?.nickname || "客户" }}</span
              ><ArrowDown /></button
            ><template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item @click="select('profile')"
                  >个人资料</el-dropdown-item
                ><el-dropdown-item divided @click="logout"
                  >退出登录</el-dropdown-item
                ></el-dropdown-menu
              ></template
            ></el-dropdown
          >
        </div>
      </header>
      <main class="page-content">
        <HomeOverview
          v-if="current === 'home'"
          :member="member"
          :preview="route.query.preview === '1' && isDev"
          @navigate="select"
          @support="openSupport"
        />
        <PackageCenter
          v-if="['plans', 'orders', 'usage', 'nodes'].includes(current)"
          :tab="current"
          :preview="route.query.preview === '1' && isDev"
          @navigate="select"
          @support="openSupport"
        />
        <TicketCenter
          v-if="current === 'tickets'"
          :context="ticketContext"
          @read="refreshTicketUnread"
        />
        <template v-if="current === 'profile'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">ACCOUNT · 账户</p>
              <h1>个人资料</h1>
              <p>查看您的客户账户信息。</p>
            </div>
          </div>
          <section class="panel profile-panel">
            <span class="profile-avatar large">{{
              member?.nickname?.charAt(0) || "U"
            }}</span>
            <div>
              <h2>{{ member?.nickname }}</h2>
              <p>{{ member?.email }}</p>
              <el-tag type="success">账户正常</el-tag>
            </div>
          </section></template
        >
        <template
          v-else-if="
            !['home', 'plans', 'orders', 'usage', 'nodes', 'tickets'].includes(
              current,
            )
          "
          ><div class="page-heading">
            <div>
              <p class="eyebrow">SPEEDNET · 服务</p>
              <h1>{{ currentLabel }}</h1>
              <p>此功能将在后续开发阶段开放。</p>
            </div>
          </div>
          <section class="panel feature-placeholder">
            <span>✦</span>
            <h2>{{ currentLabel }}即将上线</h2>
            <p>
              注册与登录功能已可用，套餐、订单和订阅模块将按开发文档逐步接入。
            </p>
            <el-button type="primary" @click="select('home')"
              >返回仪表板</el-button
            >
          </section></template
        >
      </main>
    </div>
  </div>
</template>
<script setup lang="ts">
import TicketCenter from "../components/TicketCenter.vue";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  House,
  Cloudy,
  Coin,
  Tickets,
  ChatDotRound,
  TrendCharts,
  User,
  Document,
  Menu,
  ArrowDown,
  Bell,
  Link,
  Monitor,
  DataLine,
} from "@element-plus/icons-vue";
import { tickets, api, clearSession, type Member } from "../api";
import HomeOverview from "../components/HomeOverview.vue";
import PackageCenter from "../components/PackageCenter.vue";
const ticketUnread = ref(0);
let unreadTimer: ReturnType<typeof setInterval> | undefined;
async function refreshTicketUnread() {
  try {
    ticketUnread.value = await tickets.unread();
  } catch {
    /* Navigation remains usable while service is unavailable. */
  }
}
onMounted(() => {
  if (!(import.meta.env.DEV && route.query.preview === "1")) {
    void refreshTicketUnread();
    unreadTimer = setInterval(() => {
      if (!document.hidden) void refreshTicketUnread();
    }, 30000);
  }
});
onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer);
});
const ticketContext = ref<{ orderId?: number; subscriptionId?: number }>();
function openSupport(context: { orderId?: number; subscriptionId?: number }) {
  ticketContext.value = context;
  select("tickets");
}
const isDev = import.meta.env.DEV;

const router = useRouter();
const route = useRoute();
const member = ref<Member | null>(null);
const mobileMenu = ref(false);
const current = computed(() => {
  const tab = route.query.tab;
  return typeof tab === "string" && menu.some((item) => item.key === tab)
    ? tab
    : "home";
});
const today = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(new Date());
const menu = [
  { key: "home", label: "首页", icon: House },
  { key: "plans", label: "订阅套餐", icon: Cloudy },
  { key: "nodes", label: "节点列表", icon: DataLine },
  { key: "orders", label: "订单管理", icon: Tickets },
  { key: "tickets", label: "工单系统", icon: ChatDotRound },
  { key: "usage", label: "流量明细", icon: TrendCharts },
  { key: "profile", label: "个人资料", icon: User },
  { key: "docs", label: "使用文档", icon: Document },
];
const currentLabel = computed(
  () => menu.find((item) => item.key === current.value)?.label || "仪表板",
);
function select(key: string) {
  if (key !== "tickets") ticketContext.value = undefined;
  void router.push({ path: "/dashboard", query: { ...route.query, tab: key } });
  mobileMenu.value = false;
  window.scrollTo(0, 0);
}
async function logout() {
  try {
    await api.logout();
  } catch {
    /* Always discard the local session. */
  }
  clearSession();
  await router.replace("/login");
}
onMounted(async () => {
  if (import.meta.env.DEV && route.query.preview === "1") {
    member.value = {
      id: 0,
      nickname: "演示用户",
      email: "demo@example.com",
      avatar: null,
    };
    return;
  }
  try {
    member.value = await api.me();
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "获取账户信息失败",
    );
    clearSession();
    await router.replace("/login");
  }
});
</script>
