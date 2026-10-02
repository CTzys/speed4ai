<template>
  <ContentWrap>
    <el-form :inline="true">
      <el-form-item label="查询服务器">
        <el-select
          v-model="serverId"
          filterable
          remote
          :remote-method="searchServers"
          :loading="serverLoading"
          placeholder="按名称搜索服务器"
          style="width: 360px"
          @change="load"
          @visible-change="(visible) => visible && searchServers()"
        >
          <el-option
            v-for="server in servers"
            :key="server.id"
            :label="`${server.name} (${server.host})`"
            :value="server.id!"
          >
            <div class="flex items-center justify-between gap-4">
              <span>{{ server.name }}</span>
              <el-tag size="small" :type="isConfigured(server) ? 'success' : 'warning'">{{
                isConfigured(server) ? 'API 已配置' : '待配置'
              }}</el-tag>
            </div>
            <div class="text-xs text-gray-500">{{ server.host }} · {{ panelAddress(server) }}</div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item
        ><el-input v-model="keyword" placeholder="搜索备注、协议或端口" clearable
      /></el-form-item>
      <el-form-item>
        <el-button :disabled="!ready || loading" @click="load">刷新</el-button>
        <el-button v-hasPermi="['xray:inbound:create']" type="primary" @click="openForm()"
          >新增入站</el-button
        >
      </el-form-item>
    </el-form>
    <el-empty
      v-if="!selected"
      description="点击新增入站开始配置，或选择服务器查看已有入站"
      :image-size="60"
    />
    <template v-else>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="当前服务器"
          >{{ selected.name }}（{{ selected.host }}）</el-descriptions-item
        >
        <el-descriptions-item label="面板地址">{{ panelAddress(selected) }}</el-descriptions-item>
        <el-descriptions-item label="API 状态"
          ><el-tag :type="ready ? 'success' : 'warning'">{{
            ready ? '已配置' : '待配置'
          }}</el-tag></el-descriptions-item
        >
      </el-descriptions>
      <el-alert
        v-if="!ready"
        title="这台服务器还缺少面板连接信息或 API Token，请在服务器管理补充后再管理入站。"
        type="warning"
        :closable="false"
        class="mt-4"
      />
    </template>
  </ContentWrap>
  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="filtered"
      :empty-text="
        !selected ? '请选择服务器' : !ready ? '请先完善该服务器的 API 配置' : '该服务器暂无入站'
      "
    >
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="备注" prop="remark" />
      <el-table-column label="协议" prop="protocol" width="130" />
      <el-table-column label="监听地址" prop="listen" />
      <el-table-column label="端口" prop="port" width="90" />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.enable ? 'success' : 'info'">{{
            row.enable ? '启用' : '停用'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="操作" width="160"
        ><template #default="{ row }">
          <el-button
            v-hasPermi="['xray:inbound:update']"
            link
            type="primary"
            :disabled="loading"
            @click="openForm(row.id)"
            >编辑</el-button
          >
          <el-button
            v-hasPermi="['xray:inbound:delete']"
            link
            type="danger"
            :disabled="loading"
            @click="remove(row)"
            >删除</el-button
          >
        </template></el-table-column
      >
    </el-table>
  </ContentWrap>
  <InboundForm ref="formRef" @success="onSaved" />
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/inbound'
import * as ServerApi from '@/api/xray/server'
import InboundForm from './InboundForm.vue'
defineOptions({ name: 'XrayInbound' })
const route = useRoute(),
  router = useRouter(),
  message = useMessage()
const serverId = ref<number>(),
  servers = ref<ServerApi.XrayServerVO[]>([]),
  serverLoading = ref(false)
const loading = ref(false),
  list = ref<Api.InboundVO[]>([]),
  keyword = ref(''),
  formRef = ref()
let loadSequence = 0,
  searchSequence = 0
const filtered = computed(() =>
  list.value.filter((row) =>
    `${row.remark} ${row.protocol} ${row.port}`.toLowerCase().includes(keyword.value.toLowerCase())
  )
)
const selected = computed(() => servers.value.find((server) => server.id === serverId.value))
const isConfigured = (server: ServerApi.XrayServerVO) =>
  ['http', 'https'].includes(server.panelScheme ?? '') &&
  !!server.panelPort &&
  !!server.panelTokenConfigured
const ready = computed(() => !!selected.value && isConfigured(selected.value))
const panelAddress = (server: ServerApi.XrayServerVO) => {
  if (!server.panelPort) return '面板端口待配置'
  const host =
    server.host.includes(':') && !server.host.startsWith('[') ? `[${server.host}]` : server.host
  const path = (server.panelPath ?? '').replace(/^\/+|\/+$/g, '')
  return `${server.panelScheme || 'https'}://${host}:${server.panelPort}/${path ? path + '/' : ''}`
}
const searchServers = async (name = '') => {
  const seq = ++searchSequence
  serverLoading.value = true
  try {
    const page = await ServerApi.getServerPage({ pageNo: 1, pageSize: 100, name })
    const current = selected.value
    if (seq === searchSequence)
      servers.value =
        current && !page.list.some((s: ServerApi.XrayServerVO) => s.id === current.id)
          ? [current, ...page.list]
          : page.list
  } finally {
    if (seq === searchSequence) serverLoading.value = false
  }
}
const load = async () => {
  const seq = ++loadSequence
  list.value = []
  if (!serverId.value || !ready.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await Api.getInbounds(serverId.value)
    if (seq === loadSequence) list.value = data
  } finally {
    if (seq === loadSequence) loading.value = false
  }
}
const openForm = (id?: number) => {
  formRef.value.open(serverId.value, id)
}
const onSaved = async (id: number) => {
  const target = await ServerApi.getServer(id)
  servers.value = [target, ...servers.value.filter((server) => server.id !== id)]
  serverId.value = id
  await router.replace({ query: { ...route.query, serverId: id } })
  await load()
}
const remove = async (row: Api.InboundVO) => {
  const target = serverId.value,
    name = selected.value?.name
  if (!target || !row.id) return
  await message.confirm(
    `确定删除服务器 ${name ?? target} 的入站 ${row.remark || row.id}（端口 ${row.port}）？关联客户端也会删除。`
  )
  await Api.deleteInbound(target, row.id)
  message.success('入站已删除')
  if (target === serverId.value) await load()
}
const selectFromRoute = async () => {
  if (route.path !== '/xray/inbound') return
  const target = Number(route.query.serverId)
  if (!Number.isSafeInteger(target) || target <= 0 || target === serverId.value) return
  serverId.value = target
  list.value = []
  if (!servers.value.some((server) => server.id === target)) {
    servers.value.push(await ServerApi.getServer(target))
  }
  if (serverId.value === target) await load()
}
onMounted(async () => {
  await searchServers()
  await selectFromRoute()
})
onActivated(selectFromRoute)
watch(() => route.fullPath, selectFromRoute)
</script>
