<template>
  <ContentWrap>
    <el-radio-group v-model="view" @change="search"
      ><el-radio-button value="pending">待处理</el-radio-button
      ><el-radio-button value="mine">我负责的</el-radio-button
      ><el-radio-button value="all">{{
        manager ? '全部工单' : '可访问工单'
      }}</el-radio-button></el-radio-group
    >
    <el-form inline class="ticket-filters"
      ><el-form-item
        ><el-input
          v-model="filters.keyword"
          placeholder="标题 / 工单编号 / 订单编号"
          clearable
          @keyup.enter="search" /></el-form-item
      ><el-form-item
        ><el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px"
          ><el-option
            v-for="(label, key) in statuses"
            :key="key"
            :label="label"
            :value="key" /></el-select></el-form-item
      ><el-form-item
        ><el-select v-model="filters.category" placeholder="全部分类" clearable style="width: 150px"
          ><el-option
            v-for="(label, key) in categories"
            :key="key"
            :label="label"
            :value="key" /></el-select></el-form-item
      ><el-form-item
        ><el-input-number
          v-model="filters.memberId"
          placeholder="会员 ID"
          :min="1"
          :controls="false" /></el-form-item
      ><el-form-item
        ><el-input-number
          v-model="filters.assigneeId"
          placeholder="负责人 ID"
          :min="1"
          :controls="false" /></el-form-item
      ><el-form-item
        ><el-button type="primary" @click="search">查询</el-button
        ><el-button @click="load()">刷新</el-button></el-form-item
      ></el-form
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows"
      ><el-table-column prop="number" label="编号" min-width="235" /><el-table-column
        prop="title"
        label="标题"
        min-width="180"
      /><el-table-column label="分类" min-width="110"
        ><template #default="s">{{ categories[s.row.category] }}</template></el-table-column
      ><el-table-column label="状态" min-width="110"
        ><template #default="s"
          ><el-tag>{{ statuses[s.row.status] }}</el-tag></template
        ></el-table-column
      ><el-table-column label="优先级" width="90"
        ><template #default="s">{{ priorities[s.row.priority] }}</template></el-table-column
      ><el-table-column prop="member_id" label="会员 ID" width="90" /><el-table-column
        prop="assignee_id"
        label="负责人 ID"
        width="100"
      /><el-table-column label="未读" width="70"
        ><template #default="s"
          ><el-badge
            v-if="s.row.unread_count"
            :value="s.row.unread_count" /></template></el-table-column
      ><el-table-column prop="update_time" label="更新时间" min-width="180" /><el-table-column
        label="操作"
        width="90"
        ><template #default="s"
          ><el-button link type="primary" @click="open(s.row)">处理</el-button></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="filters.page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="load()"
    />
  </ContentWrap>
  <el-drawer
    v-model="drawer"
    :title="detail?.ticket.title || '工单详情'"
    size="min(1100px, 96vw)"
    :before-close="beforeClose"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="detail" v-loading="detailLoading" class="ticket-detail">
      <main
        ><div class="ticket-actions"
          ><el-tag>{{ statuses[detail.ticket.status] }}</el-tag
          ><span>{{ detail.ticket.number }}</span
          ><el-button @click="refreshDetail">刷新</el-button
          ><el-button
            v-if="!detail.ticket.assignee_id && detail.ticket.status !== 'closed'"
            v-hasPermi="['support:ticket:assign']"
            :disabled="busy"
            @click="action('claim')"
            >领取工单</el-button
          ><el-button
            v-if="detail.ticket.status !== 'closed'"
            v-hasPermi="['support:ticket:close']"
            :disabled="busy"
            @click="closeTicket"
            >关闭</el-button
          ><el-button
            v-if="['resolved', 'closed'].includes(detail.ticket.status)"
            v-hasPermi="['support:ticket:reply']"
            :disabled="busy"
            @click="action('reopen')"
            >重新打开</el-button
          ></div
        >
        <el-alert
          v-if="detail.ticket.status === 'closed'"
          :title="`关闭原因：${detail.ticket.close_reason}`"
          type="info"
          :closable="false"
        />
        <el-button v-if="detail.nextBeforeSeq" :disabled="detailLoading" @click="older"
          >加载更早消息</el-button
        >
        <div class="ticket-thread"
          ><article
            v-for="m in detail.messages"
            :key="m.id"
            :class="['ticket-message', { internal: m.visibility === 'internal' }]"
            ><small
              >{{ m.sender_type === 'staff' ? '员工' : '客户' }} #{{ m.sender_id }} ·
              {{ time(m.create_time) }}
              <el-tag v-if="m.visibility === 'internal'" type="warning" size="small"
                >内部备注</el-tag
              ></small
            ><p>{{ m.body }}</p
            ><el-button
              v-for="f in m.attachments"
              :key="f.id"
              link
              type="primary"
              @click="preview(f)"
              >{{ f.name }}</el-button
            ></article
          ></div
        >
        <div v-if="detail.ticket.status !== 'closed' && checkPermi(['support:ticket:reply'])"
          ><el-radio-group v-model="internal"
            ><el-radio-button :value="false">回复客户</el-radio-button
            ><el-radio-button :value="true">内部备注</el-radio-button></el-radio-group
          ><el-alert
            v-if="internal"
            title="此备注及截图仅员工可见，不会通知客户。"
            type="warning"
            :closable="false"
          /><el-input
            v-model="body"
            type="textarea"
            :rows="5"
            maxlength="5000"
            show-word-limit
            :placeholder="internal ? '记录排查过程或交接信息' : '填写给客户的回复'"
          /><div class="ticket-actions"
            ><label class="upload-button"
              >添加截图<input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                multiple
                :disabled="busy || uploading"
                @change="upload" /></label
            ><span>最多 3 张，每张不超过 5 MB</span
            ><el-tag v-for="f in files" :key="f.id" closable @close="removeFile(f)">{{
              f.name
            }}</el-tag></div
          ><div class="ticket-actions"
            ><el-select v-if="!internal" v-model="replyStatus" style="width: 170px"
              ><el-option label="等待客户补充" value="waiting" /><el-option
                label="继续处理"
                value="processing" /><el-option label="已解决" value="resolved" /></el-select
            ><el-button
              type="primary"
              :loading="busy"
              :disabled="uploading || !body.trim()"
              @click="send"
              >{{ internal ? '保存内部备注' : '发送回复' }}</el-button
            ></div
          ></div
        >
        <h3>操作记录</h3><el-button v-if="nextEvent" @click="olderEvents">加载更早记录</el-button
        ><div v-for="e in events" :key="e.id" class="ticket-event"
          >{{ time(e.create_time) }} · {{ e.actor_type === 'staff' ? '员工' : '客户' }} #{{
            e.actor_id
          }}
          · {{ e.detail }}</div
        >
      </main>
      <aside
        ><h3>客户信息</h3
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="会员"
            >{{ detail.member.nickname }} (#{{ detail.member.id }})</el-descriptions-item
          ><el-descriptions-item label="邮箱">{{ detail.member.email }}</el-descriptions-item
          ><el-descriptions-item label="分类">{{
            categories[detail.ticket.category]
          }}</el-descriptions-item
          ><el-descriptions-item label="负责人">{{
            detail.ticket.assignee_id || '未分配'
          }}</el-descriptions-item></el-descriptions
        >
        <template v-if="manager"
          ><h3>分配与优先级</h3
          ><el-select
            v-if="checkPermi(['support:ticket:assign'])"
            v-model="assignee"
            clearable
            placeholder="选择客服或取消分配"
            :disabled="busy || detail.ticket.status === 'closed'"
            ><el-option
              v-for="u in staff"
              :key="u.id"
              :value="u.id"
              :label="`${u.nickname} (#${u.id})`" /></el-select
          ><el-button
            v-if="checkPermi(['support:ticket:assign'])"
            :disabled="busy || detail.ticket.status === 'closed'"
            @click="action('assign', { assigneeId: assignee || null })"
            >保存负责人</el-button
          ><el-select v-model="priority" :disabled="busy"
            ><el-option
              v-for="(label, key) in priorities"
              :key="key"
              :label="label"
              :value="key" /></el-select
          ><el-button :disabled="busy" @click="action('priority', { priority })"
            >保存优先级</el-button
          ></template
        >
        <h3>当前关联订单</h3
        ><template v-if="detail.order"
          ><p>{{ detail.order.number }}</p
          ><p
            >{{ detail.order.plan_name }} · {{ detail.order.status }} · ¥{{
              (detail.order.amount / 100).toFixed(2)
            }}</p
          ><el-button
            v-if="checkPermi(['subscription:order:manage'])"
            link
            type="primary"
            @click="router.push('/subscription/orders')"
            >进入套餐订单</el-button
          ></template
        ><p v-else>未关联订单</p> <h3>当前关联订阅</h3
        ><template v-if="detail.subscription"
          ><p>{{ detail.subscription.number }}</p
          ><p
            >状态：{{ subscriptionStatuses[detail.subscription.status] }} · 同步：{{
              syncStatuses[detail.subscription.sync_status]
            }}</p
          ><p>到期：{{ detail.subscription.expiry_time }}</p
          ><p
            >周期已用 / 额度：{{
              gib(detail.subscription.used_upload + detail.subscription.used_download)
            }}
            / {{ gib(detail.subscription.total_bytes) }} GiB</p
          ><el-alert
            v-if="detail.subscription.last_error"
            :title="detail.subscription.last_error"
            type="warning"
            :closable="false"
          /><el-button
            v-if="checkPermi(['subscription:query'])"
            link
            type="primary"
            @click="router.push('/subscription/list')"
            >进入订阅管理</el-button
          ></template
        ><p v-else>未关联订阅</p> <h3>提交时信息</h3
        ><pre class="ticket-snapshot">{{ JSON.stringify(detail.snapshot, null, 2) }}</pre>
      </aside>
    </div>
  </el-drawer>
  <el-dialog v-model="previewOpen" title="工单截图" width="min(900px, 94vw)" @closed="clearPreview"
    ><img v-if="previewUrl" :src="previewUrl" alt="工单截图" style="max-width: 100%"
  /></el-dialog>
</template>
<script setup lang="ts">
import { checkPermi } from '@/utils/permission'
import {
  support,
  statuses,
  categories,
  priorities,
  type Ticket,
  type Detail,
  type Attachment,
  type Event as TicketEvent
} from '@/api/support'
import { ElMessageBox } from 'element-plus'
const time = (value: string) => value.replace('T', ' ').slice(0, 19)
const router = useRouter()
const manager = computed(() => checkPermi(['support:ticket:manage']))
const rows = ref<Ticket[]>([]),
  total = ref(0),
  view = ref('pending'),
  loading = ref(false),
  error = ref(''),
  drawer = ref(false),
  detail = ref<Detail | null>(null),
  detailLoading = ref(false),
  busy = ref(false),
  uploading = ref(false),
  internal = ref(false),
  body = ref(''),
  replyStatus = ref('waiting'),
  files = ref<Attachment[]>([]),
  staff = ref<{ id: number; nickname: string }[]>([]),
  assignee = ref<number>(),
  priority = ref('normal'),
  events = ref<TicketEvent[]>([]),
  nextEvent = ref(0),
  previewOpen = ref(false),
  previewUrl = ref('')
const filters = reactive({
  page: 1,
  keyword: '',
  status: '',
  category: '',
  memberId: undefined as number | undefined,
  assigneeId: undefined as number | undefined
})
const subscriptionStatuses: Record<number, string> = {
    0: '待生效',
    1: '有效',
    2: '暂停',
    3: '流量耗尽',
    4: '过期',
    5: '结束'
  },
  syncStatuses: Record<number, string> = { 0: '待同步', 1: '同步中', 2: '已核对', 3: '失败' }
const gib = (n: number) => (n / 1073741824).toFixed(2)
let lastPayload = '',
  requestKey = ''
async function run(job: () => Promise<void>) {
  error.value = ''
  try {
    await job()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e || '操作失败，请重试')
  }
}
async function load() {
  loading.value = true
  await run(async () => {
    const data = await support.page({ ...filters, size: 20, view: view.value })
    rows.value = data.list
    total.value = data.total
  })
  loading.value = false
}
function search() {
  filters.page = 1
  load()
}
async function open(t: Ticket) {
  drawer.value = true
  detailLoading.value = true
  await run(async () => {
    const [data, log] = await Promise.all([support.detail(t.id), support.events(t.id)])
    if (detail.value?.ticket.id !== t.id) {
      body.value = ''
      files.value = []
      internal.value = false
    }
    detail.value = data
    events.value = log.list
    nextEvent.value = log.nextBeforeId
    assignee.value = data.ticket.assignee_id ?? undefined
    priority.value = data.ticket.priority
    await support.read(t.id, data.latestSeq)
    if (manager.value && checkPermi(['support:ticket:assign'])) staff.value = await support.staff()
  })
  detailLoading.value = false
}
async function refreshDetail() {
  if (detail.value) await open(detail.value.ticket)
}
async function older() {
  if (!detail.value) return
  detailLoading.value = true
  await run(async () => {
    const data = await support.detail(detail.value!.ticket.id, detail.value!.nextBeforeSeq)
    detail.value!.messages.unshift(...data.messages)
    detail.value!.nextBeforeSeq = data.nextBeforeSeq
  })
  detailLoading.value = false
}
async function olderEvents() {
  if (!detail.value) return
  await run(async () => {
    const data = await support.events(detail.value!.ticket.id, nextEvent.value)
    events.value.push(...data.list)
    nextEvent.value = data.nextBeforeId
  })
}
async function action(action: string, extra: Record<string, unknown> = {}) {
  if (!detail.value) return
  busy.value = true
  await run(async () => {
    await support.action(detail.value!.ticket.id, {
      action,
      version: detail.value!.ticket.version,
      ...extra
    })
    await refreshDetail()
    await load()
  })
  busy.value = false
}
async function closeTicket() {
  try {
    const result = await ElMessageBox.prompt('请输入关闭原因', '关闭工单', {
      inputValidator: (value) => Boolean(value?.trim()) || '请填写关闭原因',
      inputPattern: /^.{1,500}$/,
      inputErrorMessage: '关闭原因不能超过 500 字'
    })
    await action('close', { reason: result.value })
  } catch {
    /* Staff cancelled. */
  }
}
async function send() {
  if (!detail.value) return
  busy.value = true
  await run(async () => {
    const id = detail.value!.ticket.id,
      data = {
        body: body.value,
        internal: internal.value,
        status: replyStatus.value,
        attachmentIds: files.value.map((f) => f.id)
      },
      signature = JSON.stringify({ id, ...data })
    if (signature !== lastPayload) {
      lastPayload = signature
      requestKey = crypto.randomUUID()
    }
    await support.reply(id, { ...data, requestKey })
    body.value = ''
    files.value = []
    lastPayload = ''
    await refreshDetail()
    await load()
  })
  busy.value = false
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement,
    selected = Array.from(input.files || [])
  input.value = ''
  if (files.value.length + selected.length > 3) {
    error.value = '每条消息最多 3 张截图'
    return
  }
  uploading.value = true
  await run(async () => {
    for (const file of selected) {
      if (file.size > 5 * 1024 * 1024) throw new Error('截图不能超过 5 MB')
      files.value.push(await support.upload(file))
    }
  })
  uploading.value = false
}
async function removeFile(file: Attachment) {
  if (busy.value || uploading.value) return
  await run(async () => {
    await support.removeAttachment(file.id)
    files.value.splice(files.value.indexOf(file), 1)
  })
}
function beforeClose(done: () => void) {
  if (!busy.value) done()
}
function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
async function preview(file: Attachment) {
  await run(async () => {
    const blob = await support.image(file.id)
    if (!blob.type.startsWith('image/')) throw new Error('截图不存在或无权访问')
    clearPreview()
    previewUrl.value = URL.createObjectURL(blob)
    previewOpen.value = true
  })
}
watch(drawer, (visible) => {
  if (!visible) load()
})
onMounted(load)
onUnmounted(clearPreview)
</script>
<style scoped>
.ticket-filters {
  margin-top: 20px;
}
.ticket-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin: 16px 0;
}
.ticket-actions span {
  font-size: 12px;
  color: #7c899f;
}
.ticket-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
}
.ticket-detail aside {
  border-left: 1px solid var(--el-border-color);
  padding-left: 20px;
}
.ticket-thread {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 20px 0;
}
.ticket-message {
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-light);
  border-radius: 10px;
  padding: 16px;
}
.ticket-message.internal {
  background: var(--el-color-warning-light-9);
}
.ticket-message p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.ticket-message small {
  color: var(--el-text-color-secondary);
}
.ticket-event {
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color);
  padding: 12px 0;
}
.ticket-snapshot {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
}
.upload-button {
  position: relative;
  color: var(--el-color-primary);
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
aside .el-select {
  width: 100%;
  margin: 8px 0;
}
@media (max-width: 800px) {
  .ticket-detail {
    grid-template-columns: 1fr;
  }
  .ticket-detail aside {
    border-left: 0;
    padding-left: 0;
  }
}
</style>
