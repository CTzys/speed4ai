<template>
  <section v-loading="loading" class="package-center">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="mb"
      ><template #default
        ><el-button link @click="load">重新加载</el-button></template
      ></el-alert
    >
    <template v-if="tab === 'home'">
      <div class="page-heading">
        <div>
          <p class="eyebrow">SUBSCRIPTION · 我的服务</p>
          <h1>首页</h1>
          <p>欢迎回来，在这里管理您的订阅与服务。</p>
        </div>
        <el-button @click="load">刷新状态</el-button>
      </div>
      <div class="home-stats">
        <section class="panel"><small>我的订阅</small><strong>{{ current?.planName || '尚未开通' }}</strong><span>{{ current ? statuses[current.status] : '选择适合您的套餐' }}</span></section>
        <section class="panel"><small>剩余流量</small><strong>{{ current ? bytes(remaining) : '—' }}</strong><span>本周期已使用 {{ current ? bytes(current.usedBytes) : '—' }}</span></section>
        <section class="panel"><small>到期时间</small><strong>{{ current ? date(current.expiryTime) : '—' }}</strong><span>有效期以当前订阅为准</span></section>
        <section class="panel"><small>待办订单</small><strong>{{ orders.filter(o => o.status === 'pending').length }} 笔</strong><el-button link type="primary" @click="emit('navigate', 'orders')">查看订单 →</el-button></section>
      </div>
      <section v-if="current" class="panel package-summary">
        <div class="summary-title">
          <h2>{{ current.planName }}</h2>
          <el-tag :type="current.status === 1 ? 'success' : 'warning'">{{
            statuses[current.status]
          }}</el-tag>
        </div>
        <div class="package-metrics">
          <div>
            <small>有效期至</small
            ><strong>{{ date(current.expiryTime) }}</strong>
          </div>
          <div>
            <small>本周期已使用</small
            ><strong>{{ bytes(current.usedBytes) }}</strong>
          </div>
          <div>
            <small>套餐周期额度</small
            ><strong>{{ bytes(current.baseBytes) }}</strong>
          </div>
          <div>
            <small>剩余补充流量</small
            ><strong>{{ bytes(current.extraBytes) }}</strong>
          </div>
        </div>
        <el-progress
          :percentage="
            Math.min(
              100,
              Math.round(
                (current.usedBytes / Math.max(1, current.totalBytes)) * 100,
              ),
            )
          "
          :status="current.status === 3 ? 'exception' : undefined"
        />
        <p>
          节点配置：{{ syncStatuses[current.syncStatus] }} · 节点上限
          {{ current.nodeLimit
          }}<span v-if="current.nextResetTime">
            · 下次重置 {{ date(current.nextResetTime) }}</span
          >
        </p>
        <el-alert
          v-if="current.lastError"
          :title="current.lastError"
          type="warning"
          :closable="false"
        />
        <div class="package-actions">
          <el-button type="primary" @click="emit('navigate', 'plans')"
            >续订 / 升级 / 补充流量</el-button
          ><el-button
            :disabled="current.status !== 1 || current.syncStatus !== 2"
            @click="copyLink"
            >{{ current.status === 1 && [0, 1].includes(current.syncStatus) ? "节点配置中…" : "复制订阅链接" }}</el-button
          >
        </div>
      </section>
      <section v-else-if="!error" class="panel">
        <el-empty description="尚未开通套餐"
          ><el-button type="primary" @click="emit('navigate', 'plans')"
            >选择套餐</el-button
          ></el-empty
        >
      </section>
      <div class="home-bottom home-section"><section class="panel"><h2>快捷入口</h2><div class="quick-links"><button @click="emit('navigate', 'nodes')"><strong>◈ 节点列表</strong><small>查看节点与订阅配置 →</small></button><button @click="emit('navigate', 'orders')"><strong>▤ 我的订单</strong><small>付款与开通状态 →</small></button><button @click="emit('navigate', 'docs')"><strong>▧ 使用文档</strong><small>了解客户端使用方法 →</small></button></div></section><section class="panel"><h2>服务提示</h2><p class="detail-note">复制订阅链接并导入客户端，节点配置完成后即可使用。</p><p class="detail-note">续订保留剩余补充流量；订阅实际过期后，补充流量失效。</p></section></div>
    </template>
    <template v-if="tab === 'nodes'">
      <div class="page-heading"><div><p class="eyebrow">NODES · 连接服务</p><h1>节点列表</h1><p>查看您的订阅配置与连接入口。</p></div><el-button @click="load">刷新状态</el-button></div>
      <section class="panel region-panel">
        <h2>支持地区</h2>
        <p class="detail-note">支持地区与城市按后台配置展示，实际可用节点以您的订阅配置为准。</p>
        <el-alert v-if="regionsError" :title="regionsError" type="warning" :closable="false" />
        <div v-else-if="regions.length" class="region-list">
          <div v-for="region in regions" :key="region.id" class="region-card">
            <strong>{{ region.name }}</strong>
            <div v-if="region.cities.length" class="region-cities">
              <el-tag v-for="city in region.cities" :key="city.id" type="success" effect="plain">{{ city.name }}</el-tag>
            </div>
            <span v-else>暂未配置城市</span>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="暂无配置的支持地区" />
      </section>
      <section class="panel"><h2>订阅配置</h2><template v-if="current"><p>{{ current.planName }} · 最多 {{ current.nodeLimit }} 个节点</p><el-tag>{{ syncStatuses[current.syncStatus] }}</el-tag><el-alert v-if="current.lastError" :title="current.lastError" type="warning" :closable="false" /><div class="package-actions"><el-button type="primary" :disabled="current.status !== 1 || current.syncStatus !== 2" :loading="busy" @click="copyLink">复制订阅链接</el-button></div><p class="detail-note">配置完成后，可导入订阅链接，在客户端查看实际节点。</p></template><el-empty v-else-if="!error" description="开通订阅后即可使用节点"><el-button type="primary" @click="emit('navigate', 'plans')">选择套餐</el-button></el-empty></section>
    </template>
    <template v-if="tab === 'usage'">
      <div class="page-heading"><div><p class="eyebrow">TRAFFIC · 使用统计</p><h1>流量明细</h1><p>查看当前周期的使用量与流量额度。</p></div><el-button @click="load">刷新统计</el-button></div>
      <template v-if="current"><div class="home-stats"><section class="panel"><small>本周期已使用</small><strong>{{ bytes(current.usedBytes) }}</strong></section><section class="panel"><small>套餐周期额度</small><strong>{{ bytes(current.baseBytes) }}</strong></section><section class="panel"><small>剩余补充流量</small><strong>{{ bytes(current.extraBytes) }}</strong></section><section class="panel"><small>剩余流量</small><strong>{{ bytes(remaining) }}</strong></section></div><section class="panel"><h2>当前周期</h2><el-progress :percentage="usagePercent" /><p>下次重置：{{ current.nextResetTime ? date(current.nextResetTime) : '不自动重置' }}</p><el-button @click="emit('navigate', 'plans')">补充流量 / 流量重置</el-button></section><section class="panel home-section"><h2>流量使用记录</h2><el-empty description="暂无法展示逐日流量记录" /><p class="detail-note">当前接口仅提供累计使用量，尚未提供逐日、上传和下载明细。</p></section></template><section v-else-if="!error" class="panel"><el-empty description="尚未开通订阅，暂无流量统计" /></section>
    </template>
    <template v-if="tab === 'plans'">
      <div class="page-heading">
        <div>
          <p class="eyebrow">PLANS · 套餐</p>
          <h1>订阅套餐</h1>
          <p>每个账户同时只能有一份有效订阅，过期后可直接购买新套餐。升级结算时自动抵扣旧套餐剩余价值。</p>
        </div>
      </div>
      <div class="package-grid">
        <section v-for="plan in plans" :key="plan.id" class="panel plan-card">
          <div class="summary-title">
            <h2>{{ plan.name }}</h2>
            <el-tag v-if="plan.id === current?.planId">当前套餐</el-tag>
          </div>
          <p class="plan-description">{{ plan.description }}</p>
          <h3>
            {{ bytes(plan.total_bytes) }}
            <small>{{
              plan.reset_mode === "none" ? "套餐流量" : "每周期"
            }}</small>
          </h3>
          <p>
            最多 {{ plan.node_limit }} 个节点 ·
            {{
              ["monthly", "monthly_first", "monthly_expiry"].includes(
                plan.reset_mode,
              )
                ? "每月重置"
                : plan.reset_mode === "interval"
                  ? "按指定间隔重置"
                  : ["yearly_first", "yearly_expiry"].includes(plan.reset_mode)
                    ? "每年重置"
                    : "不自动重置"
            }}
          </p>
          <div
            v-for="price in plan.prices.filter((p) => p.kind === 'period')"
            :key="price.id"
            class="price-row"
          >
            <span
              >{{ price.name }} <small>· {{ price.months }} 个月</small></span
            ><strong>{{ money(price.price) }}</strong
            ><el-button
              :disabled="busy || !canBuy(plan)"
              @click="checkout(price)"
              >{{
                !current || [4, 5].includes(current.status)
                  ? "开通"
                  : plan.id === current.planId
                  ? "续订"
                  : current && current.status !== 5
                    ? "升级"
                    : "开通"
              }}</el-button
            >
          </div>
          <template
            v-if="
              plan.id === current?.planId &&
              current &&
              [1, 2, 3].includes(current.status)
            "
            ><h4>补充流量 / 流量重置</h4>
            <div
              v-for="price in plan.prices.filter((p) =>
                ['traffic', 'reset'].includes(p.kind),
              )"
              :key="price.id"
              class="price-row"
            >
              <span>{{
                price.kind === "reset" ? "重置套餐周期流量" : bytes(price.bytes)
              }}</span
              ><strong>{{ money(price.price) }}</strong
              ><el-button :disabled="busy" @click="checkout(price)">{{
                price.kind === "reset" ? "重置" : "加购"
              }}</el-button>
            </div></template
          >
        </section>
      </div>
      <el-empty
        v-if="!loading && !error && !plans.length"
        description="暂无公开套餐，请联系管理员"
      />
    </template>
    <template v-if="tab === 'orders'">
      <div class="page-heading">
        <div>
          <p class="eyebrow">ORDERS · 订单</p>
          <h1>套餐订单</h1>
          <p>查看付款、折抵金额和权益处理状态。</p>
        </div>
        <el-button @click="load">刷新</el-button>
      </div>
      <section class="panel">
        <el-table :data="orders"
          ><el-table-column
            prop="number"
            label="订单号"
            min-width="210"
          /><el-table-column prop="plan_name" label="套餐" /><el-table-column
            label="类型"
            ><template #default="s">{{
              kinds[s.row.kind]
            }}</template></el-table-column
          ><el-table-column label="应付"
            ><template #default="s">{{
              money(s.row.amount)
            }}</template></el-table-column
          ><el-table-column label="状态"
            ><template #default="s">{{
              orderStatuses[s.row.status]
            }}</template></el-table-column
          ><el-table-column label="操作" min-width="180"
            ><template #default="s"
              ><el-button link @click="inspect(s.row)">查看</el-button
              ><el-button
                v-if="s.row.status === 'pending'"
                link
                type="danger"
                @click="cancel(s.row)"
                >取消</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </section>
    </template>
    <el-dialog
      v-model="dialog"
      title="套餐订单"
      width="min(520px, 94vw)"
      :close-on-click-modal="false"
    >
      <template v-if="selected"
        ><h3>{{ selected.plan_name }} · {{ kinds[selected.kind] }}</h3>
        <el-descriptions :column="1" border
          ><el-descriptions-item label="订单号">{{
            selected.number
          }}</el-descriptions-item
          ><el-descriptions-item label="套餐原价">{{
            money(selected.original_amount)
          }}</el-descriptions-item
          ><el-descriptions-item label="旧套餐折抵"
            >−{{ money(selected.credit_amount) }}</el-descriptions-item
          ><el-descriptions-item label="应付金额"
            ><strong>{{ money(selected.amount) }}</strong></el-descriptions-item
          ><el-descriptions-item label="状态">{{
            orderStatuses[selected.status]
          }}</el-descriptions-item></el-descriptions
        >
        <p v-if="selected.kind === 'upgrade'">
          付款成功后切换当前套餐，并从升级时重新计算购买周期。旧套餐折抵不重复使用，超过新套餐价格的部分不退回。
        </p>
        <p v-if="selected.kind === 'traffic'">
          补充流量不延长有效期；到期前续订则保留，实际过期后失效。
        </p>
        <el-alert
          v-if="selected.error"
          :title="selected.error"
          type="warning"
          :closable="false"
        />
        <template v-if="selected.status === 'pending'"
          ><el-select
            v-model="channel"
            placeholder="选择支付方式"
            class="payment-select"
            ><el-option
              v-for="c in channels"
              :key="c"
              :value="c"
              :label="channelNames[c] || c" /></el-select
          ><el-alert
            v-if="!channels.length"
            title="暂未配置支付渠道，请联系管理员"
            type="warning"
            :closable="false"
          /><el-alert
            v-if="channel === 'mock'"
            title="测试支付：点击确认后将模拟付款成功并开通订阅。"
            type="info"
            :closable="false"
            class="mb"
          /><el-button
            type="primary"
            :disabled="!channel || busy"
            :loading="busy"
            @click="pay"
            >{{ channel === "mock" ? "确认支付成功" : "前往支付" }}</el-button
          ></template
        >
        <div v-if="paymentQr" class="payment-qr">
          <img :src="paymentQr" alt="订单支付二维码" />
          <p>使用支付应用扫码，完成后刷新支付状态。</p>
        </div>
        <p v-if="paymentContent && !paymentQr">
          <a
            v-if="safePaymentUrl"
            :href="safePaymentUrl"
            target="_blank"
            rel="noopener noreferrer"
            >打开支付页面</a
          ><span v-else
            >支付渠道返回的内容暂不支持在线展示，请选择其他渠道或联系客服。</span
          >
        </p> </template
      ><template #footer
        ><el-button @click="dialog = false">关闭</el-button
        ><el-button :loading="busy" @click="refreshOrder"
          >刷新支付与开通状态</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from "vue";
import QRCode from "qrcode";
import { ElMessage } from "element-plus";
import {
  packages,
  type PackagePlan,
  type PackagePrice,
  type PackageOrder,
  type CurrentPackage,
  type NodeRegion,
} from "../api";
const props = defineProps<{ tab: string; preview?: boolean }>();
const emit = defineEmits<{ navigate: [string] }>();
const plans = ref<PackagePlan[]>([]),
  orders = ref<PackageOrder[]>([]),
  current = ref<CurrentPackage | null>(null),
  channels = ref<string[]>([]);
const regions = ref<NodeRegion[]>([]);
const regionsError = ref("");
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  dialog = ref(false),
  selected = ref<PackageOrder | null>(null),
  channel = ref(""),
  paymentContent = ref(""),
  paymentQr = ref("");
const statuses = ["待生效", "生效中", "已暂停", "流量耗尽", "已到期", "已结束"],
  syncStatuses = ["待配置", "配置中", "已核对", "配置失败"];
const kinds: Record<string, string> = {
  new: "新购",
  renew: "续订",
  upgrade: "升级",
  traffic: "补充流量",
  reset: "流量重置",
};
const orderStatuses: Record<string, string> = {
  pending: "待支付",
  paid: "权益处理中",
  completed: "权益已发放",
  failed: "已支付 · 待处理",
  cancelled: "已取消 / 过期",
};
const channelNames: Record<string, string> = {
  alipay_pc: "支付宝",
  alipay_wap: "支付宝",
  alipay_qr: "支付宝扫码",
  wx_native: "微信支付",
  wx_wap: "微信 H5",
  mock: "测试支付",
};
const money = (n: number) => `¥${(n / 100).toFixed(2)}`;
const bytes = (n: number) => `${(n / 1073741824).toFixed(2)} GiB`;
const date = (v: string) => new Date(v).toLocaleString("zh-CN");
const remaining = computed(() => current.value ? Math.max(0, current.value.totalBytes - current.value.usedBytes) + current.value.extraBytes : 0);
const usagePercent = computed(() => current.value ? Math.min(100, Math.max(0, Math.round(current.value.usedBytes / Math.max(1, current.value.totalBytes) * 100))) : 0);
const safePaymentUrl = computed(() => {
  try {
    const u = new URL(paymentContent.value);
    return ["https:", "http:"].includes(u.protocol) ? u.href : "";
  } catch {
    return "";
  }
});
function canBuy(p: PackagePlan) {
  if (!current.value || [4, 5].includes(current.value.status)) return p.enabled;
  if (p.id === current.value?.planId) return p.renew_enabled;
  return (
    p.enabled &&
    (!current.value ||
      current.value.status === 5 ||
      (current.value.planLevel !== null &&
        p.level > current.value.planLevel &&
        current.value.status !== 4))
  );
}
let statusTimer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
let statusRefreshing = false;
function scheduleStatusRefresh() {
  clearTimeout(statusTimer);
  if (disposed || props.preview || statusRefreshing) return;
  const configuring = current.value && [0, 1].includes(current.value.syncStatus);
  const awaitingPayment = dialog.value && selected.value?.status === "pending";
  if (configuring || awaitingPayment)
    statusTimer = setTimeout(refreshStatus, 3000);
}
async function refreshStatus() {
  statusRefreshing = true;
  try {
    if (busy.value || loading.value) return;
    const orderId = dialog.value && selected.value?.status === "pending" ? selected.value.id : null;
    if (orderId !== null) {
      const order = await packages.refresh(orderId);
      if (disposed) return;
      if (selected.value?.id === orderId) selected.value = order;
      if (order.status !== "pending") orders.value = await packages.orders();
    }
    const latest = await packages.current();
    if (!disposed) current.value = latest;
  } catch {
    // A transient polling failure can be retried without interrupting the page.
  } finally {
    statusRefreshing = false;
    scheduleStatusRefresh();
  }
}
async function load() {
  if (props.preview) return;
  loading.value = true;
  error.value = "";
  if (props.tab === "nodes") {
    regionsError.value = "";
    try { regions.value = await packages.regions(); }
    catch (e) {
      regions.value = [];
      regionsError.value = e instanceof Error ? e.message : "地区加载失败，请刷新重试";
    }
  }
  try {
    [plans.value, current.value, orders.value, channels.value] =
      await Promise.all([
        packages.plans(),
        packages.current(),
        packages.orders(),
        packages.channels(),
      ]);
    if (!channels.value.includes(channel.value))
      channel.value = channels.value.includes("mock") ? "mock" : channels.value[0] || "";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
    scheduleStatusRefresh();
  }
}
async function perform(work: () => Promise<void>) {
  busy.value = true;
  try {
    await work();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : "操作失败");
  } finally {
    busy.value = false;
  }
}
async function checkout(price: PackagePrice) {
  await perform(async () => {
    selected.value = await packages.checkout(price.id, crypto.randomUUID());
    paymentContent.value = "";
    paymentQr.value = "";
    dialog.value = true;
    await load();
    if (selected.value.amount === 0) await refreshOrder();
  });
}
async function inspect(o: PackageOrder) {
  await perform(async () => {
    selected.value = await packages.refresh(o.id);
    paymentContent.value = "";
    paymentQr.value = "";
    dialog.value = true;
    await load();
  });
}
async function cancel(o: PackageOrder) {
  await perform(async () => {
    await packages.cancel(o.id);
    await load();
  });
}
async function refreshOrder() {
  if (!selected.value) return;
  await perform(async () => {
    selected.value = await packages.refresh(selected.value!.id);
    await load();
  });
}
async function pay() {
  if (!selected.value) return;
  await perform(async () => {
    const result = await packages.pay(selected.value!.id, channel.value);
    paymentContent.value = result.displayContent || "";
    paymentQr.value = "";
    if (
      ["qr_code", "qrcode"].includes(result.displayMode) &&
      paymentContent.value
    )
      paymentQr.value = await QRCode.toDataURL(paymentContent.value, {
        width: 240,
        margin: 2,
      });
    if (result.status === 10) {
      selected.value = await packages.refresh(selected.value!.id);
      await load();
      ElMessage.success(selected.value.status === "completed" ? "支付成功，订阅权益已更新" : "支付成功，正在处理订阅权益");
    } else if (safePaymentUrl.value)
      window.open(safePaymentUrl.value, "_blank", "noopener,noreferrer");
  });
}
async function copyLink() {
  await perform(async () => {
    const link = await packages.link();
    await navigator.clipboard.writeText(
      new URL(link.path, window.location.origin).href,
    );
    ElMessage.success("订阅链接已复制");
  });
}
watch(() => props.tab, load);
watch(dialog, scheduleStatusRefresh);
onMounted(load);
onBeforeUnmount(() => {
  disposed = true;
  clearTimeout(statusTimer);
});
</script>
<style scoped>
.region-panel { margin-bottom: 20px; }
.region-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }
.region-card { display: flex; flex-direction: column; gap: 8px; padding: 18px; border: 1px solid #e4edf1; border-radius: 12px; background: #f8fbf9; }
.region-cities { display: flex; flex-wrap: wrap; gap: 8px; }
.region-card strong { color: #22353c; }
.region-card span { color: #6f857a; font-size: 13px; }

.home-stats { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:18px; margin-bottom:22px; }
.home-stats .panel { display:flex; flex-direction:column; align-items:flex-start; gap:12px; }
.home-stats small,.home-stats span,.detail-note { color:#84939e; font-size:13px; line-height:1.8; }
.home-stats strong { font-size:21px; overflow-wrap:anywhere; }
.home-bottom { display:grid; grid-template-columns:2fr 1fr; gap:22px; }
.home-section { margin-top:22px; }
.quick-links { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
.quick-links button { border:1px solid #edf1f2; border-radius:10px; background:#fafcfb; padding:22px 12px; text-align:left; color:#40525e; cursor:pointer; }
.quick-links button:hover { background:#eaf7ed; }
.quick-links strong,.quick-links small { display:block; }
.quick-links small { color:#84939e; margin-top:12px; line-height:1.6; }
h2 { font-size:17px; }
@media(max-width:1100px) { .home-stats { grid-template-columns:repeat(2,minmax(0,1fr)); } .home-bottom { grid-template-columns:1fr; } }
@media(max-width:550px) { .home-stats { gap:10px; } .home-stats strong { font-size:17px; } .quick-links { grid-template-columns:1fr; } }

:deep(.el-descriptions__content) {
  overflow-wrap: anywhere;
  word-break: break-all;
}
.package-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
}
.summary-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.plan-card h3 {
  font-size: 28px;
  color: #3d7651;
}
.plan-card small,
.package-metrics small {
  color: #7b8497;
  font-size: 13px;
}
.plan-description {
  white-space: pre-wrap;
  line-height: 1.7;
  min-height: 40px;
}
.price-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid #edf0f5;
}
.price-row > span {
  flex: 1;
}
.package-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 25px 0;
}
.package-metrics strong,
.package-metrics small {
  display: block;
  margin-bottom: 8px;
}
.package-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}
.payment-qr {
  text-align: center;
  margin-top: 18px;
}
.payment-qr img {
  width: 240px;
  max-width: 100%;
}
.payment-select {
  width: 100%;
  margin: 20px 0;
}
.mb {
  margin-bottom: 20px;
}
@media (max-width: 700px) {
  .package-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
