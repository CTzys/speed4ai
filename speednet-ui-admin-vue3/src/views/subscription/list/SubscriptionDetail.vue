<template>
  <Dialog v-model="visible" title="订阅详情" width="1200px">
    <div v-loading="loading" v-if="detail">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="订阅编号">{{
          detail.subscription.number
        }}</el-descriptions-item>
        <el-descriptions-item label="用户"
          >{{ detail.subscription.userName }} ·
          {{ detail.subscription.userEmail || detail.subscription.userId }}</el-descriptions-item
        >
        <el-descriptions-item label="状态"
          >{{ Api.statuses[detail.subscription.status] }} /
          {{ Api.syncStatuses[detail.subscription.syncStatus] }}</el-descriptions-item
        >
        <el-descriptions-item label="开始时间">{{
          Api.time(detail.subscription.startTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="到期时间">{{
          Api.time(detail.subscription.expiryTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{
          Api.time(detail.subscription.endedTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="流量额度">{{
          detail.subscription.unlimited ? '无限' : Api.bytes(detail.subscription.totalBytes)
        }}</el-descriptions-item>
        <el-descriptions-item label="周期已用">{{
          Api.bytes(detail.subscription.usedBytes)
        }}</el-descriptions-item>
        <el-descriptions-item label="流量计费">{{
          detail.subscription.trafficMode === 'download' ? '仅下载' : '上传＋下载'
        }}</el-descriptions-item>
        <el-descriptions-item label="累计上传">{{
          Api.bytes(detail.subscription.lifetimeUpload)
        }}</el-descriptions-item>
        <el-descriptions-item label="累计下载">{{
          Api.bytes(detail.subscription.lifetimeDownload)
        }}</el-descriptions-item>
        <el-descriptions-item label="下次流量重置">{{
          Api.time(detail.subscription.nextResetTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="流量同步时间">{{
          Api.time(detail.subscription.lastTrafficTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="配置核对时间">{{
          Api.time(detail.subscription.lastSyncTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="节点数量"
          >{{ detail.subscription.clientCount }} /
          {{ detail.subscription.nodeLimit }}</el-descriptions-item
        >
        <el-descriptions-item label="备注" :span="3">{{
          detail.subscription.remark || '—'
        }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="detail.subscription.lastError"
        class="mt-3"
        :closable="false"
        type="error"
        :title="detail.subscription.lastError"
      />
      <div class="mt-3 flex gap-2">
        <el-button @click="refresh">刷新</el-button>
        <el-button v-hasPermi="['subscription:sync']" type="primary" @click="sync"
          >同步服务器配置与流量</el-button
        >
        <el-button v-hasPermi="['subscription:credentials']" @click="showLink"
          >查看订阅链接</el-button
        >
        <el-button
          v-if="detail.subscription.status !== 5"
          v-hasPermi="['subscription:assign']"
          :disabled="detail.subscription.clientCount >= detail.subscription.nodeLimit"
          @click="openAssign"
          >分配节点</el-button
        >
      </div>
      <div v-if="feedLink" class="mt-3"
        ><el-input :model-value="feedLink" readonly
          ><template #append><el-button @click="copyLink">复制</el-button></template></el-input
        ><div class="text-gray-500 mt-1"
          >订阅链接包含访问凭据，请仅提供给关联用户。节点配置完成且订阅生效后才可使用。</div
        ></div
      >
      <el-tabs class="mt-4">
        <el-tab-pane label="节点与客户端">
          <el-table :data="detail.clients" border>
            <el-table-column prop="nodeName" label="出口节点" min-width="140" />
            <el-table-column prop="serverId" label="服务器 ID" width="105" />
            <el-table-column prop="inboundId" label="入站 ID" width="90" />
            <el-table-column prop="protocol" label="协议" width="90" />
            <el-table-column prop="publicHost" label="连接地址" min-width="160" />
            <el-table-column prop="email" label="客户端标识" min-width="130" />
            <el-table-column label="已用上传 / 下载" min-width="170"
              ><template #default="s"
                >{{ Api.bytes(s.row.usedUpload) }} / {{ Api.bytes(s.row.usedDownload) }}</template
              ></el-table-column
            >
            <el-table-column label="状态" width="110"
              ><template #default="s"
                >{{ s.row.released ? '已释放' : '已分配' }} /
                {{ Api.syncStatuses[s.row.syncStatus] }}</template
              ></el-table-column
            >
            <el-table-column prop="lastError" label="错误" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="210"
              ><template #default="s"
                ><el-button
                  v-hasPermi="['subscription:credentials']"
                  link
                  type="primary"
                  @click="showCredentials(s.row)"
                  >凭据</el-button
                >
                <el-button
                  v-if="!s.row.released && detail.subscription.status !== 5"
                  v-hasPermi="['subscription:reset-client']"
                  link
                  type="warning"
                  @click="resetClient(s.row)"
                  >重置认证</el-button
                >
                <el-button
                  v-if="!s.row.released"
                  v-hasPermi="['subscription:assign']"
                  link
                  type="danger"
                  @click="release(s.row)"
                  >释放</el-button
                ></template
              ></el-table-column
            >
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="关联订单"
          ><el-table :data="detail.orders"
            ><el-table-column prop="orderNo" label="订单号" /><el-table-column label="用途"
              ><template #default="s">{{
                orderPurpose[s.row.purpose] || s.row.purpose
              }}</template></el-table-column
            ><el-table-column label="关联时间"
              ><template #default="s">{{ Api.time(s.row.createTime) }}</template></el-table-column
            ></el-table
          ><div class="text-gray-500 mt-2"
            >第一版记录外部订单号；支付自动开通待订单模块接入。</div
          ></el-tab-pane
        >
        <el-tab-pane label="操作与流量历史"
          ><el-table v-loading="logsLoading" :data="logs"
            ><el-table-column label="时间" width="175"
              ><template #default="s">{{ Api.time(s.row.createTime) }}</template></el-table-column
            ><el-table-column prop="action" label="操作" width="110" /><el-table-column
              prop="message"
              label="说明"
              min-width="220"
            /><el-table-column label="上传 / 下载" width="190"
              ><template #default="s"
                >{{ Api.bytes(s.row.uploadBytes) }} / {{ Api.bytes(s.row.downloadBytes) }}</template
              ></el-table-column
            ><el-table-column prop="creator" label="操作人 ID" width="110" /><el-table-column
              label="结果"
              width="70"
              ><template #default="s">{{
                s.row.success ? '成功' : '失败'
              }}</template></el-table-column
            ></el-table
          ><Pagination
            v-model:page="logQuery.pageNo"
            v-model:limit="logQuery.pageSize"
            :total="logTotal"
            @pagination="loadLogs"
        /></el-tab-pane>
      </el-tabs>
    </div>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </Dialog>
  <Dialog v-model="assignVisible" title="分配节点" width="1050px">
    <AssignmentFields
      v-model="newAssignments"
      :options="options"
      :region-id="detail?.subscription.regionId"
      :city-id="detail?.subscription.cityId"
    />
    <template #footer
      ><el-button @click="assignVisible = false">取消</el-button
      ><el-button type="primary" :loading="loading" @click="assign">保存分配</el-button></template
    >
  </Dialog>
  <Dialog v-model="credentialVisible" title="客户端认证" width="600px">
    <el-form label-width="100px">
      <el-form-item :label="clientCredential.protocol === 'trojan' ? '密码' : 'UUID'"
        ><el-input
          :model-value="clientCredential.credential"
          type="password"
          show-password
          readonly
      /></el-form-item>
      <el-form-item label="连接"
        ><el-input
          :model-value="clientCredential.connectionUri || '尚未配置成功或当前已停用'"
          type="textarea"
          readonly
          :rows="4"
      /></el-form-item>
    </el-form>
    <template #footer><el-button @click="credentialVisible = false">关闭</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/subscription'
import AssignmentFields from './AssignmentFields.vue'
const message = useMessage()
const emit = defineEmits(['success'])
const visible = ref(false),
  loading = ref(false),
  assignVisible = ref(false)
const detail = ref<Api.Detail>(),
  id = ref<number>(),
  feedLink = ref('')
const logs = ref<Api.LogVO[]>([])
const logsLoading = ref(false)
const logTotal = ref(0)
const logQuery = reactive({ pageNo: 1, pageSize: 10 })
let logRequest = 0
const loadLogs = async () => {
  if (!id.value) return
  const request = ++logRequest
  logsLoading.value = true
  try {
    const result = await Api.getLogPage({ subscriptionId: id.value, ...logQuery })
    if (request !== logRequest) return
    logs.value = result.list
    logTotal.value = result.total
  } finally {
    if (request === logRequest) logsLoading.value = false
  }
}
const options = ref<Api.Options>({ nodes: [], servers: [], regions: [], cities: [] })
const credentialVisible = ref(false)
const clientCredential = ref({ protocol: '', credential: '', connectionUri: '' })
const showCredentials = async (client: Api.ClientVO) => {
  clientCredential.value = await Api.credentials(id.value!, client.id)
  credentialVisible.value = true
}
const resetClient = async (client: Api.ClientVO) => {
  await message.confirm('旧 UUID / 密码将被撤销，用户需要刷新订阅后重新连接，是否继续？')
  loading.value = true
  try {
    await Api.resetClient(id.value!, client.id)
    message.success('旧认证已撤销，新认证配置任务已排队')
    await refresh()
    emit('success')
  } finally {
    loading.value = false
  }
}
const newAssignments = ref<Api.Assignment[]>([{ publicHost: '' }])
const orderPurpose = { create: '来源订单', extend: '续费', 'add-traffic': '增加流量' }
const refresh = async () => {
  if (!id.value) return
  loading.value = true
  try {
    detail.value = await Api.getDetail(id.value)
    await loadLogs()
  } finally {
    loading.value = false
  }
}
const open = async (value: number) => {
  ++logRequest
  logs.value = []
  logTotal.value = 0
  logQuery.pageNo = 1
  id.value = value
  detail.value = undefined
  feedLink.value = ''
  clientCredential.value = { protocol: '', credential: '', connectionUri: '' }
  credentialVisible.value = false
  visible.value = true
  await refresh()
}
const sync = async () => {
  if (!id.value) return
  if (await Api.sync(id.value)) message.success('配置和流量同步任务已排队，请稍后刷新')
  else message.warning('任务队列已满，系统会自动重试')
  await refresh()
  emit('success')
}
const showLink = async () => {
  if (!id.value) return
  const r = await Api.link(id.value)
  feedLink.value = new URL(r.path, import.meta.env.VITE_BASE_URL || window.location.origin).href
}
const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(feedLink.value)
    message.success('已复制订阅链接')
  } catch {
    message.warning('请手动选择并复制链接')
  }
}
const release = async (client: Api.ClientVO) => {
  await message.confirm('释放后将禁用该客户端并撤销出口路由，是否继续？')
  await Api.release(id.value!, client.id)
  message.success('已释放，撤销任务已排队')
  await refresh()
  emit('success')
}
const openAssign = async () => {
  options.value = await Api.getOptions()
  const owned = detail.value!.clients.filter((c) => !c.released).map((c) => c.nodeId)
  options.value.nodes = options.value.nodes.filter((n) => !owned.includes(n.id))
  newAssignments.value = [{ publicHost: '' }]
  assignVisible.value = true
}
const assign = async () => {
  const a = newAssignments.value[0]
  if (!a?.nodeId || !a.serverId || !a.inboundId || !a.publicHost.trim()) {
    message.warning('请完整填写节点、服务器、入站和连接地址')
    return
  }
  loading.value = true
  try {
    await Api.assign(id.value!, a)
    assignVisible.value = false
    message.success('已保存节点分配')
    await refresh()
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
