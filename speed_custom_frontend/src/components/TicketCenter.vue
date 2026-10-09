<template>
  <div class="ticket-center">
    <div class="page-heading">
      <div>
        <p class="eyebrow">SUPPORT · 客户服务</p>
        <h1>工单系统</h1>
        <p>反馈使用问题，查看客服回复与处理进度。</p>
      </div>
      <el-button type="primary" @click="newTicket()">提交工单</el-button>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      class="ticket-error"
    />
    <section v-if="!detail" class="panel">
      <div class="ticket-tools">
        <el-select
          v-model="filter"
          placeholder="全部状态"
          clearable
          @change="
            page = 1;
            load();
          "
          ><el-option
            v-for="(label, key) in ticketStatuses"
            :key="key"
            :label="label"
            :value="key" /></el-select
        ><el-input
          v-model="keyword"
          placeholder="搜索标题或工单编号"
          clearable
          @keyup.enter="
            page = 1;
            load();
          "
        /><el-button
          @click="
            page = 1;
            load();
          "
          >查询</el-button
        ><el-button @click="load()">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" @row-click="open"
        ><el-table-column
          prop="number"
          label="工单编号"
          min-width="230"
        /><el-table-column label="问题" min-width="200"
          ><template #default="s"
            ><span>{{ s.row.title }}</span
            ><el-tag v-if="s.row.unread_count" type="danger" size="small"
              >{{ s.row.unread_count }} 条新回复</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="分类" min-width="120"
          ><template #default="s">{{
            ticketCategories[s.row.category]
          }}</template></el-table-column
        ><el-table-column label="状态" min-width="100"
          ><template #default="s"
            ><el-tag>{{ ticketStatuses[s.row.status] }}</el-tag></template
          ></el-table-column
        ><el-table-column label="最后更新" min-width="180"
          ><template #default="s">{{
            time(s.row.update_time)
          }}</template></el-table-column
        ><el-table-column label="操作" width="80"
          ><template #default="s"
            ><el-button link type="primary" @click.stop="open(s.row)"
              >查看</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load()"
      />
    </section>
    <section v-else class="panel" v-loading="loading">
      <div class="ticket-tools">
        <el-button
          @click="
            detail = null;
            load();
          "
          >返回列表</el-button
        ><el-button @click="open(detail.ticket)">刷新对话</el-button
        ><el-tag>{{ ticketStatuses[detail.ticket.status] }}</el-tag
        ><el-button
          v-if="detail.ticket.status !== 'closed'"
          :disabled="busy"
          @click="closeTicket"
          >{{
            detail.ticket.status === "resolved"
              ? "确认解决并关闭"
              : "撤回并关闭"
          }}</el-button
        ><el-button v-if="canReopen" :disabled="busy" @click="action('reopen')"
          >重新打开</el-button
        >
      </div>
      <h2>{{ detail.ticket.title }}</h2>
      <p class="ticket-meta">
        {{ detail.ticket.number }} ·
        {{ ticketCategories[detail.ticket.category] }}
      </p>
      <p v-if="detail.snapshot.order" class="ticket-meta">
        关联订单：{{ detail.snapshot.order.number }} ·
        {{ detail.snapshot.order.plan_name }}
      </p>
      <p v-if="detail.snapshot.subscription" class="ticket-meta">
        关联订阅：{{ detail.snapshot.subscription.number }}
      </p>
      <el-alert
        v-if="detail.ticket.status === 'closed'"
        :title="`关闭原因：${detail.ticket.close_reason}`"
        type="info"
        :closable="false"
      />
      <el-button v-if="detail.nextBeforeSeq" :disabled="loading" @click="older"
        >加载更早消息</el-button
      >
      <div class="ticket-thread">
        <article
          v-for="message in detail.messages"
          :key="message.id"
          :class="[
            'ticket-message',
            message.sender_type === 'staff' ? 'staff-message' : '',
          ]"
        >
          <div class="ticket-meta">
            {{ message.sender_type === "staff" ? "客服" : "我" }} ·
            {{ time(message.create_time) }}
          </div>
          <p>{{ message.body }}</p>
          <el-button
            v-for="file in message.attachments"
            :key="file.id"
            link
            type="primary"
            @click="preview(file)"
            >{{ file.name }}</el-button
          >
        </article>
      </div>
      <template v-if="detail.ticket.status !== 'closed'"
        ><el-input
          v-model="reply"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="补充问题或回复客服"
        />
        <div class="ticket-tools">
          <label class="upload-button"
            >添加截图<input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              multiple
              :disabled="busy || uploading"
              @change="upload($event, replyFiles)" /></label
          ><span>每条消息最多 3 张，每张不超过 5 MB</span>
        </div>
        <div class="ticket-tools">
          <el-tag
            v-for="file in replyFiles"
            :key="file.id"
            closable
            @close="removeFile(file, replyFiles)"
            >{{ file.name }}</el-tag
          >
        </div>
        <el-button
          type="primary"
          :loading="busy"
          :disabled="uploading || !reply.trim()"
          @click="send"
          >发送回复</el-button
        ></template
      >
    </section>
    <el-dialog
      v-model="creating"
      title="提交工单"
      width="min(620px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      :before-close="beforeClose"
    >
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-form label-position="top"
        ><el-form-item label="标题" required
          ><el-input
            v-model="draft.title"
            maxlength="120"
            show-word-limit /></el-form-item
        ><el-form-item label="问题分类" required
          ><el-select v-model="draft.category"
            ><el-option
              v-for="(label, key) in ticketCategories"
              :key="key"
              :label="label"
              :value="key" /></el-select></el-form-item
        ><el-form-item label="关联订单（可选）"
          ><el-select
            v-model="draft.orderId"
            clearable
            placeholder="选择与问题有关的订单"
            ><el-option
              v-for="order in orders"
              :key="order.id"
              :label="`${order.plan_name} · ${order.number}`"
              :value="order.id" /></el-select></el-form-item
        ><el-form-item v-if="current" label="关联订阅（可选）"
          ><el-checkbox v-model="linkSubscription">{{
            current.planName
          }}</el-checkbox></el-form-item
        ><el-form-item label="问题描述" required
          ><el-input
            v-model="draft.body"
            type="textarea"
            :rows="5"
            maxlength="5000"
            show-word-limit
            placeholder="请描述发生时间、客户端和设备、现象及已尝试的步骤。请勿发送密码或完整订阅链接。"
        /></el-form-item>
        <div class="ticket-tools">
          <label class="upload-button"
            >添加截图<input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              multiple
              :disabled="busy || uploading"
              @change="upload($event, createFiles)" /></label
          ><span>最多 3 张，每张不超过 5 MB</span>
        </div>
        <div class="ticket-tools">
          <el-tag
            v-for="file in createFiles"
            :key="file.id"
            closable
            @close="removeFile(file, createFiles)"
            >{{ file.name }}</el-tag
          >
        </div></el-form
      >
      <template #footer
        ><el-button :disabled="busy" @click="creating = false">取消</el-button
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="uploading || !draft.title.trim() || !draft.body.trim()"
          @click="submit"
          >提交</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="previewOpen"
      title="工单截图"
      width="min(900px, 94vw)"
      @closed="clearPreview"
      ><img
        v-if="previewUrl"
        :src="previewUrl"
        alt="工单截图"
        class="ticket-preview"
    /></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { ElMessageBox, ElMessage } from "element-plus";
import {
  tickets,
  packages,
  ticketStatuses,
  ticketCategories,
  type Ticket,
  type TicketDetail,
  type TicketAttachment,
  type PackageOrder,
  type CurrentPackage,
} from "../api";
function time(value: unknown) {
  if (typeof value === "string") return value.replace("T", " ").slice(0, 19);
  if (typeof value === "number" && Number.isFinite(value)) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) {
      return new Intl.DateTimeFormat("zh-CN", {
        year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit", second: "2-digit",
        hourCycle: "h23", timeZone: "Asia/Shanghai",
      }).format(date);
    }
  }
  return "—";
}
const emit = defineEmits<{ read: [] }>();
const props = defineProps<{
  context?: { orderId?: number; subscriptionId?: number };
}>();
const rows = ref<Ticket[]>([]),
  total = ref(0),
  page = ref(1),
  filter = ref(""),
  keyword = ref(""),
  detail = ref<TicketDetail | null>(null),
  loading = ref(false),
  busy = ref(false),
  uploading = ref(false),
  error = ref(""),
  creating = ref(false),
  reply = ref(""),
  createFiles = ref<TicketAttachment[]>([]),
  replyFiles = ref<TicketAttachment[]>([]),
  orders = ref<PackageOrder[]>([]),
  current = ref<CurrentPackage | null>(null),
  linkSubscription = ref(false);
const draft = reactive({
  title: "",
  category: "connection",
  body: "",
  orderId: undefined as number | undefined,
});
const previewOpen = ref(false),
  previewUrl = ref("");
const canReopen = computed(
  () =>
    detail.value &&
    (detail.value.ticket.status === "resolved" ||
      (detail.value.ticket.status === "closed" &&
        detail.value.ticket.closed_at &&
        Date.now() <=
          new Date(detail.value.ticket.closed_at).getTime() + 7 * 86400000)),
);
let lastPayload = "",
  requestKey = ""; // Preserve the key for a retry of the same payload.
function key(body: unknown) {
  const signature = JSON.stringify(body);
  if (signature !== lastPayload) {
    lastPayload = signature;
    requestKey = crypto.randomUUID();
  }
  return requestKey;
}
async function run(job: () => Promise<void>) {
  error.value = "";
  try {
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败，请重试";
  }
}
async function load() {
  loading.value = true;
  await run(async () => {
    const data = await tickets.page({
      page: page.value,
      status: filter.value,
      keyword: keyword.value,
    });
    rows.value = data.list;
    total.value = data.total;
  });
  loading.value = false;
}
async function open(row: Ticket) {
  loading.value = true;
  await run(async () => {
    const next = await tickets.detail(row.id);
    if (detail.value?.ticket.id !== row.id) {
      reply.value = "";
      replyFiles.value = [];
    }
    detail.value = next;
    await tickets.read(row.id, next.latestSeq);
    emit("read");
  });
  loading.value = false;
}
async function older() {
  if (!detail.value) return;
  loading.value = true;
  await run(async () => {
    const data = await tickets.detail(
      detail.value!.ticket.id,
      detail.value!.nextBeforeSeq,
    );
    detail.value!.messages.unshift(...data.messages);
    detail.value!.nextBeforeSeq = data.nextBeforeSeq;
  });
  loading.value = false;
}
async function newTicket() {
  error.value = "";
  creating.value = true;
  await run(async () => {
    [orders.value, current.value] = await Promise.all([
      packages.orders(),
      packages.current(),
    ]);
  });
}
async function submit() {
  busy.value = true;
  await run(async () => {
    const body = {
      ...draft,
      subscriptionId: linkSubscription.value ? current.value?.id : undefined,
      attachmentIds: createFiles.value.map((f) => f.id),
    };
    const id = await tickets.create({
      ...body,
      requestKey: key({ kind: "create", ...body }),
    });
    creating.value = false;
    createFiles.value = [];
    draft.title = "";
    draft.body = "";
    lastPayload = "";
    await open({ id } as Ticket);
    ElMessage.success("工单已提交");
  });
  busy.value = false;
}
async function send() {
  if (!detail.value) return;
  busy.value = true;
  await run(async () => {
    const id = detail.value!.ticket.id,
      body = {
        body: reply.value,
        attachmentIds: replyFiles.value.map((f) => f.id),
        internal: false,
      };
    await tickets.reply(id, { ...body, requestKey: key({ id, ...body }) });
    reply.value = "";
    replyFiles.value = [];
    lastPayload = "";
    await open(detail.value!.ticket);
  });
  busy.value = false;
}
async function action(action: string, reason?: string) {
  if (!detail.value) return;
  busy.value = true;
  await run(async () => {
    await tickets.action(detail.value!.ticket.id, {
      action,
      reason,
      version: detail.value!.ticket.version,
    });
    await open(detail.value!.ticket);
  });
  busy.value = false;
}
async function closeTicket() {
  if (!detail.value) return;
  try {
    const result = await ElMessageBox.prompt("请输入关闭原因", "关闭工单", {
      inputValue:
        detail.value.ticket.status === "resolved"
          ? "客户确认问题已解决"
          : "客户撤回问题",
      inputValidator: (value) => Boolean(value?.trim()) || "请填写关闭原因",
      inputPattern: /^.{1,500}$/,
      inputErrorMessage: "关闭原因不能超过 500 字",
    });
    await action("close", result.value);
  } catch {
    /* Cancelled by customer. */
  }
}
async function upload(event: Event, target: TicketAttachment[]) {
  const input = event.target as HTMLInputElement,
    files = Array.from(input.files || []);
  input.value = "";
  if (target.length + files.length > 3) {
    error.value = "每条消息最多 3 张截图";
    return;
  }
  uploading.value = true;
  await run(async () => {
    for (const file of files) {
      if (file.size > 5 * 1024 * 1024) throw new Error("每张截图不能超过 5 MB");
      target.push(await tickets.upload(file));
    }
  });
  uploading.value = false;
}
async function removeFile(file: TicketAttachment, target: TicketAttachment[]) {
  if (busy.value || uploading.value) return;
  await run(async () => {
    await tickets.removeAttachment(file.id);
    target.splice(target.indexOf(file), 1);
  });
}
function beforeClose(done: () => void) {
  if (!busy.value) done();
}
function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = "";
}
async function preview(file: TicketAttachment) {
  await run(async () => {
    const blob = await tickets.image(file.id);
    clearPreview();
    previewUrl.value = URL.createObjectURL(blob);
    previewOpen.value = true;
  });
}
onMounted(async () => {
  await load();
  if (props.context) {
    await newTicket();
    draft.orderId = props.context.orderId;
    linkSubscription.value = Boolean(props.context.subscriptionId);
    draft.category = props.context.orderId ? "payment" : "connection";
  }
});
onUnmounted(clearPreview);
</script>
<style scoped>
.ticket-tools {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin: 16px 0;
}
.ticket-tools .el-select {
  width: 160px;
}
.ticket-tools .el-input {
  max-width: 280px;
}
.ticket-tools span,
.ticket-meta {
  color: #7c899f;
  font-size: 13px;
}
.ticket-error {
  margin-bottom: 16px;
}
.ticket-thread {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 24px 0;
}
.ticket-message {
  background: #f6f8fc;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  padding: 18px;
  max-width: 90%;
}
.staff-message {
  background: #eef4ff;
  align-self: flex-end;
  width: 90%;
}
.ticket-message p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.ticket-preview {
  max-width: 100%;
  display: block;
  margin: auto;
}
.upload-button {
  position: relative;
  color: #4e73df;
  cursor: pointer;
}
.upload-button input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  cursor: pointer;
}
.el-pagination {
  margin-top: 20px;
}
.el-select {
  width: 100%;
}
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
