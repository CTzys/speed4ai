<template>
  <div class="overview">
    <header class="overview-heading">
      <div>
        <p class="overline">YOUR EVERYDAY CONNECTION</p>
        <h1>
          {{ greeting }}，{{ member?.nickname || "欢迎回来"
          }}<span class="greeting-dot">.</span>
        </h1>
        <p class="heading-caption">让连接更简单，让每一天更自由。</p>
      </div>
      <div class="heading-tools">
        <span class="today-label">{{ today }}</span
        ><button
          class="refresh-button"
          aria-label="刷新首页"
          :disabled="loading"
          @click="load()"
        >
          <Refresh :class="{ spinning: loading }" /><span>刷新</span>
        </button>
      </div>
    </header>

    <section class="connection-hero" aria-label="订阅概览">
      <div class="hero-copy">
        <div class="hero-label"><span></span> SPEEDNET · 连接你的日常</div>
        <h2>世界很大，<br />连接就在此刻。</h2>
        <p>管理你的订阅与服务，下一段旅程从这里开始。</p>
        <div class="hero-actions">
          <button class="hero-primary" @click="emit('navigate', 'plans')">
            {{
              current && ![4, 5].includes(current.status)
                ? "管理我的套餐"
                : "探索订阅套餐"
            }}<ArrowRight /></button
          ><button class="hero-secondary" @click="emit('navigate', 'nodes')">
            查看节点<TopRight />
          </button>
        </div>
        <div class="hero-footnote"><Lock />你的账户与服务，尽在一处。</div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="orbit orbit-one"></div>
        <div class="orbit orbit-two"></div>
        <div class="orbit orbit-three"></div>
        <div class="globe">
          <svg viewBox="0 0 240 240" fill="none">
            <circle cx="120" cy="120" r="100" />
            <ellipse cx="120" cy="120" rx="47" ry="100" />
            <ellipse cx="120" cy="120" rx="83" ry="100" />
            <path
              d="M20 120h200M32 74h176M32 166h176M60 39h120M60 201h120M120 20v200"
            />
          </svg>
        </div>
        <div class="orbit-pin pin-one"></div>
        <div class="orbit-pin pin-two"></div>
        <div class="art-label"><span></span> YOUR WORLD, CONNECTED</div>
      </div>
    </section>

    <div class="overview-metrics" aria-label="账户统计">
      <article class="metric-card">
        <span class="metric-icon mint"><Connection /></span>
        <div>
          <span class="metric-label">我的订阅</span
          ><strong>{{
            loading
              ? "加载中"
              : subscriptionError
                ? "暂时无法读取"
                : current?.planName || "尚未开通"
          }}</strong
          ><small>{{
            subscriptionError
              ? "请刷新重试"
              : current
                ? subscriptionStatuses[current.status]
                : "找到适合你的套餐"
          }}</small>
        </div>
      </article>
      <article class="metric-card">
        <span class="metric-icon blue"><DataAnalysis /></span>
        <div>
          <span class="metric-label">剩余流量</span
          ><strong
            >{{
              loading || subscriptionError || !current
                ? "—"
                : traffic(remaining)
            }}<em v-if="current && !subscriptionError && !loading">
              GiB</em
            ></strong
          ><small>{{
            current && !subscriptionError
              ? `本周期已用 ${traffic(current.usedBytes)} GiB`
              : "开通后查看流量额度"
          }}</small>
        </div>
      </article>
      <article class="metric-card">
        <span class="metric-icon amber"><Calendar /></span>
        <div>
          <span class="metric-label">有效期至</span
          ><strong>{{
            loading || subscriptionError || !current
              ? "—"
              : date(current.expiryTime)
          }}</strong
          ><small>{{
            current && !subscriptionError ? expiryHint : "随时开始新的连接"
          }}</small>
        </div>
      </article>
      <button
        class="metric-card metric-action"
        @click="emit('navigate', 'orders')"
      >
        <span class="metric-icon lavender"><Tickets /></span>
        <div>
          <span class="metric-label">待办订单</span
          ><strong
            >{{ loading || orderError ? "—" : pendingOrders.length
            }}<em v-if="!loading && !orderError"> 笔</em></strong
          ><small>查看付款与开通进度 <ArrowRight /></small>
        </div>
      </button>
    </div>

    <div class="overview-main">
      <section class="overview-panel subscription-panel">
        <div class="section-heading">
          <div>
            <span class="section-kicker">MY SUBSCRIPTION</span>
            <h2>我的连接计划</h2>
          </div>
          <button class="text-button" @click="emit('navigate', 'plans')">
            管理套餐<ArrowRight />
          </button>
        </div>
        <el-skeleton v-if="loading" :rows="4" animated />
        <div v-else-if="subscriptionError" class="section-empty">
          <Warning />
          <h3>订阅信息暂时无法加载</h3>
          <p>{{ subscriptionError }}</p>
          <button class="soft-button" @click="load()">重新加载</button>
        </div>
        <template v-else-if="current">
          <div class="subscription-content">
            <div class="subscription-info">
              <span :class="['service-state', { warning: !ready }]"
                ><i></i>{{ subscriptionStatuses[current.status] }} ·
                {{ syncStatuses[current.syncStatus] }}</span
              >
              <h3>{{ current.planName }}</h3>
              <p>
                {{
                  [4, 5].includes(current.status)
                    ? "选择新套餐，继续你的连接。"
                    : "一份订阅，连接你喜欢的世界。"
                }}
              </p>
              <div class="plan-details">
                <div>
                  <span>套餐周期额度</span
                  ><strong
                    >{{ traffic(current.baseBytes) }} <small>GiB</small></strong
                  >
                </div>
                <div>
                  <span>剩余补充流量</span
                  ><strong
                    >{{ traffic(current.extraBytes) }}
                    <small>GiB</small></strong
                  >
                </div>
                <div>
                  <span>节点上限</span
                  ><strong>{{ current.nodeLimit }} <small>个</small></strong>
                </div>
              </div>
            </div>
            <div class="usage-meter">
              <div
                class="usage-ring"
                role="progressbar"
                aria-label="本周期流量使用比例"
                :aria-valuenow="Math.round(usagePercent)"
                :aria-valuemin="0"
                :aria-valuemax="100"
                :style="{ '--usage': `${usagePercent}%` }"
              >
                <div>
                  <strong>{{ Math.round(usagePercent) }}<small>%</small></strong
                  ><span>本周期已使用</span>
                </div>
              </div>
              <span class="meter-caption"
                >{{ traffic(current.usedBytes) }} /
                {{ traffic(current.totalBytes) }} GiB</span
              >
            </div>
          </div>
          <div class="reset-note">
            <Clock /><span>{{
              current.nextResetTime
                ? `下次流量重置：${date(current.nextResetTime)}`
                : "流量按当前套餐规则使用"
            }}</span
            ><button class="text-button" @click="emit('navigate', 'usage')">
              流量详情<ArrowRight />
            </button>
          </div>
          <el-alert
            v-if="current.lastError"
            :title="current.lastError"
            type="warning"
            :closable="false"
            class="subscription-alert"
          />
          <div class="subscription-actions">
            <button
              class="solid-button"
              :disabled="!ready || copying"
              @click="copyLink"
            >
              <CopyDocument />{{
                copying
                  ? "复制中…"
                  : ready
                    ? "复制订阅链接"
                    : current.status === 1 &&
                        [0, 1].includes(current.syncStatus)
                      ? "节点配置中…"
                      : "订阅暂不可用"
              }}</button
            ><button class="soft-button" @click="emit('navigate', 'plans')">
              续订 / 升级</button
            ><button
              class="text-button support-link"
              @click="emit('support', { subscriptionId: current.id })"
            >
              <ChatDotRound />反馈使用问题
            </button>
          </div>
        </template>
        <div v-else class="section-empty no-subscription">
          <span class="empty-icon"><Connection /></span>
          <h3>你的连接计划，从这里开始</h3>
          <p>选择适合自己的套餐，开通后即可管理订阅、节点与流量。</p>
          <button class="solid-button" @click="emit('navigate', 'plans')">
            选择订阅套餐<ArrowRight />
          </button>
        </div>
      </section>

      <section class="overview-panel quick-panel">
        <div class="section-heading">
          <div>
            <span class="section-kicker">QUICK ACCESS</span>
            <h2>常用功能</h2>
          </div>
          <span class="small-decoration">↗</span>
        </div>
        <div class="quick-menu">
          <button
            v-for="item in shortcuts"
            :key="item.tab"
            @click="emit('navigate', item.tab)"
          >
            <span :class="['quick-icon', item.color]"
              ><component :is="item.icon" /></span
            ><span
              ><strong>{{ item.title }}</strong
              ><small>{{ item.description }}</small></span
            ><ArrowRight />
          </button>
        </div>
        <div class="quick-note">
          <Lock /><span>订阅链接包含账户授权信息，请妥善保管。</span>
        </div>
      </section>
    </div>

    <div class="overview-lower">
      <section class="overview-panel">
        <div class="section-heading">
          <div>
            <span class="section-kicker">RECENT ACTIVITY</span>
            <h2>最近订单</h2>
          </div>
          <button class="text-button" @click="emit('navigate', 'orders')">
            全部订单<ArrowRight />
          </button>
        </div>
        <el-skeleton v-if="loading" :rows="3" animated />
        <div v-else-if="orderError" class="compact-empty">
          <p>订单信息暂时无法加载</p>
          <button class="text-button" @click="load()">重试<Refresh /></button>
        </div>
        <div v-else-if="!orders.length" class="compact-empty">
          <span class="empty-icon"><Tickets /></span>
          <h3>还没有订单记录</h3>
          <p>购买套餐后，可以在这里跟进开通进度。</p>
          <button class="text-button" @click="emit('navigate', 'plans')">
            浏览套餐<ArrowRight />
          </button>
        </div>
        <div v-else class="activity-list">
          <button
            v-for="order in orders.slice(0, 3)"
            :key="order.id"
            class="activity-row"
            @click="emit('navigate', 'orders')"
          >
            <span class="activity-icon"><Tickets /></span
            ><span class="activity-copy"
              ><strong>{{ order.plan_name }}</strong
              ><small>{{ order.number }}</small></span
            ><span class="activity-value"
              ><strong>{{ money(order.amount) }}</strong
              ><small :class="['order-state', order.status]">{{
                orderStatuses[order.status] || "待确认"
              }}</small></span
            ><ArrowRight />
          </button>
        </div>
      </section>
      <section class="overview-panel help-panel">
        <div class="section-heading">
          <div>
            <span class="section-kicker">WE’RE HERE TO HELP</span>
            <h2>
              服务与支持
              <span v-if="unread && !ticketError" class="unread-chip"
                >{{ unread }} 条新回复</span
              >
            </h2>
          </div>
          <button class="text-button" @click="emit('navigate', 'tickets')">
            全部工单<ArrowRight />
          </button>
        </div>
        <el-skeleton v-if="loading" :rows="3" animated />
        <div v-else-if="ticketError" class="compact-empty">
          <p>工单信息暂时无法加载</p>
          <button class="text-button" @click="emit('navigate', 'tickets')">
            进入工单系统<ArrowRight />
          </button>
        </div>
        <div v-else-if="recentTickets.length" class="activity-list">
          <button
            v-for="ticket in recentTickets"
            :key="ticket.id"
            class="activity-row"
            @click="emit('navigate', 'tickets')"
          >
            <span class="activity-icon ticket-icon"><ChatDotRound /></span
            ><span class="activity-copy"
              ><strong>{{ ticket.title }}</strong
              ><small
                >{{ date(ticket.update_time) }} · {{ ticket.number }}</small
              ></span
            ><span class="ticket-status">{{
              ticketStatuses[ticket.status] || "待确认"
            }}</span
            ><ArrowRight />
          </button>
        </div>
        <div v-else class="support-empty">
          <span class="support-symbol"><ChatDotRound /></span>
          <h3>需要帮助？我们在这里。</h3>
          <p>连接、订单或账户问题，都可以通过工单与我们联系。</p>
          <button class="soft-button" @click="emit('navigate', 'tickets')">
            前往工单<TopRight />
          </button>
        </div>
      </section>
    </div>
    <footer class="overview-footer">
      <span class="footer-brand">SpeedNet<span> / </span>让连接成为日常。</span
      ><span>账户 · 订阅 · 服务</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  ArrowRight,
  TopRight,
  Calendar,
  ChatDotRound,
  Clock,
  Connection,
  CopyDocument,
  DataAnalysis,
  Lock,
  Refresh,
  Tickets,
  Warning,
} from "@element-plus/icons-vue";
import {
  packages,
  tickets,
  ticketStatuses,
  type CurrentPackage,
  type Member,
  type PackageOrder,
  type Ticket,
} from "../api";
const props = defineProps<{ member: Member | null; preview?: boolean }>();
const emit = defineEmits<{
  navigate: [string];
  support: [{ subscriptionId?: number; orderId?: number }];
}>();
const current = ref<CurrentPackage | null>(null),
  orders = ref<PackageOrder[]>([]),
  recentTickets = ref<Ticket[]>([]),
  unread = ref(0),
  loading = ref(true),
  copying = ref(false);
const subscriptionError = ref(""),
  orderError = ref(""),
  ticketError = ref("");
const now = ref(new Date());
let clockTimer: ReturnType<typeof setInterval> | undefined;
let disposed = false;
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let inFlight = false;
const greeting = computed(() => {
  const hour = Number(
    new Intl.DateTimeFormat("zh-CN", {
      hour: "numeric",
      hourCycle: "h23",
      timeZone: "Asia/Shanghai",
    }).format(now.value),
  );
  return hour < 6
    ? "夜深了"
    : hour < 12
      ? "早上好"
      : hour < 18
        ? "下午好"
        : "晚上好";
});
const today = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
    timeZone: "Asia/Shanghai",
  }).format(now.value),
);
const subscriptionStatuses: Record<number, string> = {
    0: "待生效",
    1: "生效中",
    2: "已暂停",
    3: "流量耗尽",
    4: "已到期",
    5: "已结束",
  },
  syncStatuses: Record<number, string> = {
    0: "待配置",
    1: "配置中",
    2: "已核对",
    3: "配置失败",
  };
const orderStatuses: Record<string, string> = {
  pending: "待支付",
  paid: "权益处理中",
  completed: "已完成",
  failed: "权益待处理",
  cancelled: "已取消 / 过期",
};
const pendingOrders = computed(() =>
  orders.value.filter((order) =>
    ["pending", "paid", "failed"].includes(order.status),
  ),
);
const remaining = computed(() =>
  current.value
    ? Math.max(0, current.value.totalBytes - current.value.usedBytes) +
      current.value.extraBytes
    : 0,
);
const usagePercent = computed(() =>
  current.value
    ? Math.min(
        100,
        Math.max(
          0,
          (current.value.usedBytes / Math.max(1, current.value.totalBytes)) *
            100,
        ),
      )
    : 0,
);
const ready = computed(() =>
  Boolean(
    !subscriptionError.value &&
    current.value?.status === 1 &&
    current.value.syncStatus === 2,
  ),
);
const expiryHint = computed(() => {
  if (!current.value) return "";
  const days = Math.ceil(
    (new Date(current.value.expiryTime).getTime() - now.value.getTime()) /
      86400000,
  );
  return days <= 0
    ? "订阅已到期，欢迎重新开通"
    : Number.isFinite(days)
      ? `还有 ${days} 天到期`
      : "以订阅有效期为准";
});
const shortcuts = [
  {
    tab: "nodes",
    title: "节点与连接",
    description: "查看节点，获取订阅配置",
    icon: Connection,
    color: "mint",
  },
  {
    tab: "usage",
    title: "流量使用",
    description: "查看额度与周期使用量",
    icon: DataAnalysis,
    color: "blue",
  },
  {
    tab: "orders",
    title: "订单管理",
    description: "付款、开通与权益处理",
    icon: Tickets,
    color: "lavender",
  },
  {
    tab: "tickets",
    title: "工单支持",
    description: "提交问题，跟进客服回复",
    icon: ChatDotRound,
    color: "amber",
  },
];
const traffic = (value: number) =>
  Number.isFinite(value) ? (Math.max(0, value) / 1073741824).toFixed(1) : "—";
const money = (value: number) => `¥${(value / 100).toFixed(2)}`;
function date(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? "待确认"
    : new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(parsed);
}
const message = (reason: unknown) =>
  reason instanceof Error ? reason.message : "请稍后刷新重试";
async function load(background = false) {
  if (inFlight || disposed) return;
  if (props.preview) {
    loading.value = false;
    return;
  }
  if (!background) loading.value = true;
  inFlight = true;
  const [subscription, order, support, replies] = await Promise.allSettled([
    packages.current(),
    packages.orders(),
    tickets.page({ page: 1, size: 3 }),
    tickets.unread(),
  ]);
  inFlight = false;
  if (disposed) return;
  subscriptionError.value =
    subscription.status === "rejected" ? message(subscription.reason) : "";
  orderError.value = order.status === "rejected" ? message(order.reason) : "";
  ticketError.value =
    support.status === "rejected" ? message(support.reason) : "";
  if (subscription.status === "fulfilled") current.value = subscription.value;
  if (order.status === "fulfilled") orders.value = order.value;
  if (support.status === "fulfilled") recentTickets.value = support.value.list;
  unread.value = replies.status === "fulfilled" ? replies.value : 0;
  loading.value = false;
  scheduleRefresh();
}
function scheduleRefresh() {
  if (refreshTimer) clearTimeout(refreshTimer);
  if (disposed || props.preview) return;
  const waiting =
    current.value &&
    current.value.status === 1 &&
    [0, 1].includes(current.value.syncStatus);
  const fulfilling = orders.value.some((order) => order.status === "paid");
  refreshTimer = setTimeout(
    () => {
      if (!document.hidden) void load(true);
      else scheduleRefresh();
    },
    waiting || fulfilling ? 5000 : 30000,
  );
}
async function copyLink() {
  if (!ready.value || copying.value) return;
  copying.value = true;
  try {
    const link = await packages.link();
    await navigator.clipboard.writeText(
      new URL(link.path, window.location.origin).href,
    );
    ElMessage.success("订阅链接已复制");
  } catch (error) {
    ElMessage.error(message(error));
  } finally {
    copying.value = false;
  }
}
onMounted(() => {
  void load();
  clockTimer = setInterval(() => {
    now.value = new Date();
  }, 60000);
});
onUnmounted(() => {
  disposed = true;
  if (refreshTimer) clearTimeout(refreshTimer);
  if (clockTimer) clearInterval(clockTimer);
});
</script>

<style scoped>
.overview {
  --ink: #213e36;
  --muted: #88968f;
  --line: #e8efeb;
  --accent: #34845e;
  color: var(--ink);
  max-width: 1370px;
  margin: 0 auto;
}
.overview button {
  transition:
    background 0.18s,
    transform 0.18s,
    box-shadow 0.18s;
}
.overview button:focus-visible {
  outline: 3px solid #72b692;
  outline-offset: 4px;
}
.overview button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.overview-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin: 3px 0 28px;
}
.overline {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2.1px;
  color: #799389;
  margin: 0 0 11px;
}
.overview h1 {
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -1px;
  line-height: 1.3;
  margin: 0;
}
.greeting-dot {
  color: #53a478;
  margin-left: 5px;
}
.heading-caption {
  color: var(--muted);
  font-size: 13px;
  margin: 9px 0 0;
}
.heading-tools {
  display: flex;
  align-items: center;
  gap: 19px;
}
.today-label {
  color: #8d9b93;
  font-size: 12px;
  white-space: nowrap;
}
.refresh-button {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 12px;
  color: #5b7768;
}
.refresh-button svg {
  width: 14px;
  height: 14px;
}
.connection-hero {
  min-height: 290px;
  position: relative;
  isolation: isolate;
  background: #244e40;
  border-radius: 19px;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  border: 1px solid #37604d;
}
.connection-hero::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(ellipse at 82% 90%, #89bc6c50, transparent 53%),
    linear-gradient(100deg, #193d31, #37624b);
}
.hero-copy {
  position: relative;
  z-index: 2;
  padding: 30px 36px;
}
.hero-label {
  font-size: 10px;
  font-weight: 600;
  color: #b5d1bf;
  letter-spacing: 1.5px;
  display: flex;
  align-items: center;
  gap: 7px;
}
.hero-label span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #b6db96;
}
.hero-copy h2 {
  font-size: 36px;
  line-height: 1.35;
  letter-spacing: 1px;
  color: #f7fbf1;
  margin: 17px 0 11px;
  font-weight: 600;
}
.hero-copy > p {
  font-size: 12px;
  color: #bdcebf;
  line-height: 1.8;
  margin: 0;
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 21px;
}
.hero-primary,
.hero-secondary {
  border: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 17px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 8px;
  padding: 11px 16px;
}
.hero-primary {
  background: #d4e9b6;
  color: #274630;
}
.hero-primary:hover {
  background: #e0f0c8;
}
.hero-secondary {
  background: #ffffff0d;
  color: #e2ecdf;
  border: 1px solid #ffffff26;
}
.hero-actions svg {
  width: 14px;
  height: 14px;
}
.hero-footnote {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #9fbea8;
  font-size: 10px;
  margin-top: 21px;
}
.hero-footnote svg {
  width: 12px;
  height: 12px;
}
.hero-art {
  position: absolute;
  width: 460px;
  height: 100%;
  right: 18px;
  top: 0;
  pointer-events: none;
}
.globe {
  position: absolute;
  left: 120px;
  top: 32px;
  width: 245px;
  height: 245px;
  transform: rotate(-22deg);
  border-radius: 50%;
  background: radial-gradient(circle at 27% 20%, #c4dfa630, #73a67807 64%);
  box-shadow:
    inset 0 0 40px #bfd99716,
    0 0 70px #c0e2a60d;
}
.globe svg {
  width: 100%;
  height: 100%;
  stroke: #bfd8a95c;
  stroke-width: 0.7;
}
.orbit {
  position: absolute;
  width: 350px;
  height: 160px;
  border: 1px solid #c7e3b426;
  border-radius: 50%;
  left: 65px;
  top: 73px;
  transform: rotate(-30deg);
}
.orbit-two {
  width: 445px;
  height: 205px;
  left: 20px;
  top: 55px;
  transform: rotate(-30deg);
}
.orbit-three {
  width: 550px;
  height: 260px;
  left: -35px;
  top: 28px;
  transform: rotate(-30deg);
  border-style: dashed;
  border-color: #bfd8a918;
}
.orbit-pin {
  position: absolute;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #d4e7b5;
  box-shadow:
    0 0 0 7px #d4e7b514,
    0 0 18px #b4df9a45;
}
.pin-one {
  left: 92px;
  top: 197px;
}
.pin-two {
  right: 48px;
  top: 74px;
  width: 5px;
  height: 5px;
}
.art-label {
  position: absolute;
  bottom: 27px;
  right: 55px;
  font-size: 9px;
  letter-spacing: 2px;
  color: #c7dcb092;
  display: flex;
  align-items: center;
  gap: 6px;
}
.art-label span {
  width: 4px;
  height: 4px;
  background: #d1e5b2;
  border-radius: 50%;
}
.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 15px;
  margin: 20px 0 24px;
}
.metric-card {
  min-height: 116px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 19px 20px;
  text-align: left;
  color: var(--ink);
}
.metric-card > div {
  min-width: 0;
}
.metric-icon,
.quick-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: 12px;
}
.mint {
  color: #528f71;
  background: #eff7f1;
}
.blue {
  color: #688ca8;
  background: #f0f5f9;
}
.amber {
  color: #b19059;
  background: #faf5eb;
}
.lavender {
  color: #9686b8;
  background: #f5f2fa;
}
.metric-icon svg {
  width: 21px;
  height: 21px;
}
.metric-label {
  display: block;
  color: #8c9a93;
  font-size: 11px;
}
.metric-card strong {
  font-size: 21px;
  font-weight: 650;
  display: inline-block;
  margin: 6px 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  letter-spacing: -0.5px;
}
.metric-card em {
  font-style: normal;
  color: #98a29c;
  font-size: 11px;
  font-weight: 400;
}
.metric-card small {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #96a399;
  font-size: 10px;
  white-space: nowrap;
}
.metric-card small svg {
  width: 11px;
  height: 11px;
}
.metric-action:hover {
  border-color: #bed6c6;
  box-shadow: 0 3px 16px #27463008;
}
.overview-main {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(265px, 1fr);
  gap: 20px;
}
.overview-panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 24px;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 23px;
}
.section-kicker {
  display: block;
  color: #97a59c;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 1.6px;
  margin-bottom: 7px;
}
.section-heading h2 {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.3px;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.text-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #668d72;
  font-size: 11px;
  white-space: nowrap;
}
.text-button svg {
  width: 12px;
  height: 12px;
}
.text-button:hover {
  color: #275e3a;
}
.subscription-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 25px;
}
.subscription-info {
  flex: 1;
  min-width: 0;
}
.service-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #5f9676;
  background: #f0f7f1;
  font-size: 10px;
  padding: 5px 8px;
  border-radius: 5px;
}
.service-state i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
}
.service-state.warning {
  color: #b28c4b;
  background: #fcf6eb;
}
.subscription-info h3 {
  font-size: 27px;
  font-weight: 600;
  margin: 15px 0 7px;
  letter-spacing: -0.6px;
  overflow-wrap: anywhere;
}
.subscription-info > p {
  color: #94a098;
  font-size: 11px;
  margin: 0 0 25px;
}
.plan-details {
  display: flex;
  gap: 25px;
}
.plan-details > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.plan-details span {
  color: #9aa79e;
  font-size: 10px;
}
.plan-details strong {
  font-size: 16px;
  font-weight: 600;
}
.plan-details small {
  font-size: 9px;
  color: #9aa79e;
  font-weight: 400;
}
.usage-meter {
  display: flex;
  align-items: center;
  flex-direction: column;
  flex: none;
  width: 153px;
}
.usage-ring {
  width: 132px;
  height: 132px;
  background: conic-gradient(#71aa83 var(--usage), #eff5ef 0);
  border-radius: 50%;
  padding: 8px;
  transform: rotate(-10deg);
}
.usage-ring > div {
  height: 100%;
  width: 100%;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  transform: rotate(10deg);
}
.usage-ring strong {
  font-size: 28px;
  letter-spacing: -1px;
  font-weight: 600;
}
.usage-ring strong small {
  font-size: 12px;
  color: #8e9d91;
  margin-left: 3px;
  font-weight: 400;
}
.usage-ring span {
  font-size: 9px;
  color: #95a197;
  margin-top: 5px;
}
.meter-caption {
  font-size: 10px;
  color: #94a298;
  margin-top: 13px;
}
.reset-note {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 13px 0;
  border-top: 1px solid #f0f3f0;
  border-bottom: 1px solid #f0f3f0;
  margin-top: 22px;
  font-size: 10px;
  color: #9aa69f;
}
.reset-note > svg {
  width: 12px;
  height: 12px;
}
.reset-note .text-button {
  margin-left: auto;
}
.subscription-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 19px;
  flex-wrap: wrap;
}
.solid-button,
.soft-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 10px 13px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid transparent;
}
.solid-button {
  background: #397e58;
  color: white;
}
.solid-button:hover:not(:disabled) {
  background: #2b6745;
}
.soft-button {
  background: white;
  color: #698675;
  border-color: #e2eae4;
}
.soft-button:hover {
  background: #f5f8f4;
}
.solid-button svg,
.soft-button svg {
  width: 13px;
  height: 13px;
}
.support-link {
  margin-left: auto;
}
.subscription-alert {
  margin-top: 15px;
}
.small-decoration {
  color: #b5c3b8;
  font-size: 22px;
}
.quick-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quick-menu > button {
  display: flex;
  align-items: center;
  gap: 13px;
  border: 0;
  background: transparent;
  width: 100%;
  text-align: left;
  padding: 8px 0;
  color: var(--ink);
  border-radius: 8px;
}
.quick-menu > button:hover {
  background: #f7f9f6;
  transform: translateX(2px);
}
.quick-icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
}
.quick-icon svg {
  width: 18px;
  height: 18px;
}
.quick-menu > button > span:nth-child(2) {
  display: flex;
  flex-direction: column;
  gap: 5px;
  flex: 1;
}
.quick-menu strong {
  font-size: 12px;
  font-weight: 500;
}
.quick-menu small {
  font-size: 10px;
  color: #9aa89e;
}
.quick-menu > button > svg {
  width: 13px;
  height: 13px;
  color: #b5c2b8;
}
.quick-note {
  display: flex;
  align-items: start;
  gap: 7px;
  font-size: 10px;
  line-height: 1.8;
  color: #94a69a;
  padding: 12px 13px;
  margin-top: 15px;
  background: #f6f9f3;
  border-radius: 8px;
}
.quick-note svg {
  width: 12px;
  height: 12px;
  flex: none;
  margin-top: 3px;
}
.overview-lower {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}
.activity-list {
  display: flex;
  flex-direction: column;
}
.activity-row {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  border: 0;
  border-bottom: 1px solid #f1f4f1;
  background: transparent;
  color: var(--ink);
  text-align: left;
  padding: 14px 0;
}
.activity-row:last-child {
  border-bottom: 0;
}
.activity-row:hover {
  background: #f9fbf8;
}
.activity-icon {
  width: 33px;
  height: 36px;
  border-radius: 7px;
  background: #f5f7f4;
  color: #8ba292;
  display: grid;
  place-items: center;
  flex: none;
}
.activity-icon svg {
  width: 16px;
  height: 16px;
}
.ticket-icon {
  background: #f4f6fb;
  color: #909eb9;
}
.activity-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  flex: 1;
}
.activity-copy strong {
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-copy small {
  font-size: 9px;
  color: #a5afa7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-value {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: right;
  flex: none;
}
.activity-value strong {
  font-size: 12px;
  font-weight: 500;
}
.activity-value small {
  font-size: 9px;
  color: #7c9f88;
}
.order-state.pending,
.order-state.failed,
.order-state.paid {
  color: #be9562;
}
.order-state.cancelled {
  color: #a8b0aa;
}
.activity-row > svg {
  width: 12px;
  height: 12px;
  color: #bbc6be;
  flex: none;
}
.ticket-status {
  font-size: 10px;
  color: #7b9986;
  white-space: nowrap;
}
.unread-chip {
  font-size: 9px;
  font-weight: 400;
  color: #a98554;
  background: #fbf4e9;
  border-radius: 4px;
  padding: 3px 5px;
}
.compact-empty,
.section-empty {
  text-align: center;
  padding: 16px 10px;
  color: #97a59a;
}
.compact-empty h3,
.section-empty h3,
.support-empty h3 {
  font-size: 14px;
  font-weight: 500;
  color: #607968;
  margin: 14px 0 9px;
}
.compact-empty p,
.section-empty p,
.support-empty p {
  font-size: 11px;
  line-height: 1.8;
  color: #9aab9e;
  margin: 0 0 16px;
}
.section-empty > svg {
  width: 30px;
  height: 30px;
  color: #c6ad80;
}
.section-empty {
  padding: 20px 18px;
  min-height: 256px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}
.empty-icon {
  display: inline-grid;
  place-items: center;
  width: 44px;
  height: 44px;
  background: #f0f6ef;
  border-radius: 12px;
  color: #9bb3a0;
}
.empty-icon svg {
  width: 22px;
  height: 22px;
}
.compact-empty {
  min-height: 155px;
}
.support-empty {
  position: relative;
  background: #f5f8f1;
  border: 1px solid #edf2e7;
  border-radius: 10px;
  padding: 19px 20px;
}
.support-empty h3 {
  margin-top: 0;
}
.support-empty p {
  max-width: 80%;
  font-size: 10px;
}
.support-symbol {
  position: absolute;
  right: 19px;
  top: 24px;
  color: #bbd0b2;
}
.support-symbol svg {
  width: 33px;
  height: 33px;
}
.support-empty .soft-button {
  background: #ffffffa6;
  border-color: #dde7d7;
  color: #78936a;
}
.overview-footer {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  margin-top: 29px;
  color: #b1bcb4;
  font-size: 10px;
}
.footer-brand {
  color: #8fa598;
}
.footer-brand span {
  margin: 0 8px;
  color: #c7d1c8;
}
.spinning {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (min-width: 1450px) {
  .hero-copy {
    padding: 36px 40px;
  }
  .hero-copy h2 {
    font-size: 41px;
  }
  .connection-hero {
    min-height: 310px;
  }
  .hero-art {
    right: 65px;
  }
  .overview-panel {
    padding: 26px;
  }
}
@media (max-width: 1150px) {
  .overview-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .overview-main {
    grid-template-columns: minmax(0, 1.5fr) minmax(250px, 1fr);
  }
  .plan-details {
    gap: 16px;
  }
  .usage-meter {
    width: 125px;
  }
  .usage-ring {
    width: 112px;
    height: 112px;
  }
  .subscription-actions {
    gap: 8px;
  }
  .support-link {
    margin-left: 0;
  }
  .hero-art {
    right: -45px;
  }
  .hero-copy h2 {
    font-size: 32px;
  }
}
@media (max-width: 1000px) {
  .overview-main {
    grid-template-columns: 1fr;
  }
  .quick-menu {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 28px;
  }
  .overview-lower {
    grid-template-columns: 1fr;
  }
  .hero-art {
    opacity: 0.65;
  }
  .hero-copy {
    padding: 27px;
  }
  .heading-tools {
    gap: 10px;
  }
  .today-label {
    display: none;
  }
}
@media (max-width: 550px) {
  .overview-heading {
    margin-bottom: 22px;
    align-items: start;
  }
  .overview h1 {
    font-size: 25px;
  }
  .heading-caption {
    font-size: 11px;
  }
  .overline {
    font-size: 8px;
  }
  .refresh-button {
    padding: 8px;
    gap: 4px;
  }
  .refresh-button span {
    display: none;
  }
  .connection-hero {
    min-height: 287px;
    border-radius: 14px;
  }
  .hero-copy {
    padding: 24px;
    max-width: 100%;
  }
  .hero-copy h2 {
    font-size: 32px;
  }
  .hero-copy > p {
    font-size: 10px;
    max-width: 215px;
  }
  .hero-art {
    width: 290px;
    right: -118px;
    opacity: 0.3;
  }
  .globe {
    left: 25px;
  }
  .hero-label {
    font-size: 8px;
  }
  .hero-primary,
  .hero-secondary {
    font-size: 10px;
    padding: 10px 12px;
    gap: 9px;
  }
  .hero-actions {
    gap: 10px;
  }
  .hero-footnote {
    font-size: 9px;
  }
  .art-label {
    display: none;
  }
  .overview-metrics {
    gap: 10px;
    margin: 14px 0 18px;
  }
  .metric-card {
    padding: 14px 12px;
    gap: 8px;
    min-height: 106px;
    align-items: start;
  }
  .metric-icon {
    width: 28px;
    height: 28px;
    border-radius: 8px;
    margin-top: 3px;
  }
  .metric-icon svg {
    width: 15px;
    height: 15px;
  }
  .metric-card strong {
    font-size: 17px;
    margin: 8px 0;
    white-space: normal;
    overflow-wrap: anywhere;
  }
  .metric-card small {
    font-size: 8px;
    white-space: normal;
    line-height: 1.6;
  }
  .metric-label {
    font-size: 9px;
  }
  .metric-card em {
    font-size: 9px;
  }
  .overview-panel {
    padding: 19px 17px;
  }
  .section-heading {
    gap: 8px;
  }
  .section-heading h2 {
    font-size: 16px;
    flex-wrap: wrap;
  }
  .section-kicker {
    font-size: 8px;
  }
  .section-heading > .text-button {
    font-size: 10px;
  }
  .subscription-content {
    gap: 8px;
  }
  .subscription-info h3 {
    font-size: 22px;
  }
  .subscription-info > p {
    max-width: 160px;
    font-size: 10px;
    line-height: 1.7;
  }
  .plan-details {
    gap: 13px;
    flex-wrap: wrap;
  }
  .plan-details span {
    font-size: 9px;
  }
  .plan-details strong {
    font-size: 14px;
  }
  .usage-meter {
    width: 100px;
  }
  .usage-ring {
    width: 92px;
    height: 92px;
    padding: 6px;
  }
  .usage-ring strong {
    font-size: 24px;
  }
  .usage-ring span {
    font-size: 8px;
  }
  .meter-caption {
    font-size: 8px;
  }
  .subscription-actions .solid-button,
  .subscription-actions .soft-button {
    flex: 1;
  }
  .support-link {
    padding-top: 7px;
    width: 100%;
    justify-content: start;
  }
  .reset-note {
    font-size: 9px;
  }
  .quick-menu {
    gap: 6px 15px;
  }
  .quick-menu > button {
    gap: 8px;
  }
  .quick-icon {
    width: 30px;
    height: 30px;
  }
  .quick-menu strong {
    font-size: 11px;
  }
  .quick-menu small {
    font-size: 8px;
    line-height: 1.5;
  }
  .quick-menu > button > svg {
    display: none;
  }
  .overview-main,
  .overview-lower {
    gap: 15px;
  }
  .overview-lower {
    margin-top: 15px;
  }
  .overview-footer {
    font-size: 8px;
  }
  .activity-copy strong {
    font-size: 11px;
  }
  .activity-row {
    gap: 9px;
  }
  .ticket-status {
    font-size: 9px;
  }
  .section-empty p {
    font-size: 10px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .overview button {
    transition: none;
  }
  .spinning {
    animation: none;
  }
}
</style>
