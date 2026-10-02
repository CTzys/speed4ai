<template>
  <div class="grid grid-cols-5 gap-4 mb-4">
    <ContentWrap v-for="card in cards" :key="card.key" class="!mb-0"
      ><div class="text-gray-500">{{ card.label }}</div
      ><div class="text-2xl font-bold mt-2">{{ stats[card.key] ?? '—' }}</div></ContentWrap
    >
  </div>
  <ContentWrap>
    <el-alert
      class="mb-4"
      title="上架仅开放订阅领用，不会部署到服务器。订阅生效分配时再检测和部署，可在独立的订阅管理中查看授权。"
      type="info"
      :closable="false"
    />
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item
        ><el-input
          v-model="query.keyword"
          placeholder="名称 / 地址 / 标签"
          clearable
          @keyup.enter="search"
      /></el-form-item>
      <el-form-item
        ><el-select
          v-model="query.regionId"
          @change="clearCity"
          placeholder="地区"
          clearable
          filterable
          class="!w-150px"
        >
          <el-option
            v-for="region in regionOptions"
            :key="region.id"
            :value="region.id!"
            :label="region.name + (region.status ? '（已停用）' : '')"
          /> </el-select
      ></el-form-item>
      <el-form-item>
        <el-select
          v-model="query.cityId"
          placeholder="城市"
          clearable
          filterable
          :disabled="!query.regionId"
          class="!w-150px"
        >
          <el-option
            v-for="city in cityOptions"
            :key="city.id"
            :value="city.id!"
            :label="city.name + (city.status ? '（已停用）' : '')"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        ><el-select v-model="query.shelfStatus" placeholder="上下架状态" clearable class="!w-150px"
          ><el-option label="已上架" :value="1" /><el-option label="已下架" :value="0" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-select v-model="query.healthStatus" placeholder="检测状态" clearable class="!w-150px"
          ><el-option
            v-for="(label, value) in healthLabels"
            :key="value"
            :label="label"
            :value="value" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-button type="primary" @click="search">查询</el-button
        ><el-button @click="reset">重置</el-button></el-form-item
      >
    </el-form>
    <div class="flex flex-wrap gap-2">
      <el-button type="primary" v-hasPermi="['xray:node:create']" @click="formRef.open()"
        >新增节点</el-button
      >
      <el-button v-hasPermi="['xray:node:import']" @click="importRef.open()">批量导入</el-button>
      <el-button
        v-hasPermi="['xray:node:shelf']"
        :disabled="!selected.length || busy"
        @click="shelf(selected, true)"
        >批量上架</el-button
      >
      <el-button
        v-hasPermi="['xray:node:shelf']"
        :disabled="!selected.length || busy"
        @click="shelf(selected, false)"
        >批量下架</el-button
      >
      <el-button
        v-hasPermi="['xray:node:check']"
        :disabled="!selected.length || busy"
        @click="submit(selected, 'check')"
        >批量检测</el-button
      >

      <el-button v-if="batchId" @click="showTask">查看最近任务</el-button>
      <el-button v-hasPermi="['xray:region:query']" @click="router.push('/xray/region')"
        >地区与城市管理</el-button
      >
      <span class="text-gray-500 self-center">已选 {{ selected.length }} 个</span>
    </div>
  </ContentWrap>
  <ContentWrap>
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="list"
      row-key="id"
      @selection-change="selectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column label="节点" min-width="190"
        ><template #default="s"
          ><el-button link type="primary" @click="detailRef.open(s.row.id)">{{
            s.row.name
          }}</el-button
          ><div class="text-gray-500">{{ address(s.row) }}</div
          ><div class="text-xs text-gray-400">{{
            s.row.authType ? s.row.username : '无认证'
          }}</div></template
        ></el-table-column
      >
      <el-table-column label="地区 / 城市" min-width="130"
        ><template #default="s"
          >{{ location(s.row) }}<div class="text-gray-500">{{ s.row.tags || '—' }}</div></template
        ></el-table-column
      >
      <el-table-column label="上下架" width="100"
        ><template #default="s"
          ><el-tag :type="s.row.shelfStatus === 1 ? 'success' : 'info'">{{
            s.row.shelfStatus === 1 ? '已上架' : '已下架'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="检测" min-width="180"
        ><template #default="s"
          ><el-tag
            :type="
              s.row.healthStatus === 1 ? 'success' : s.row.healthStatus === 2 ? 'danger' : 'info'
            "
            >{{ healthLabels[s.row.healthStatus] }}</el-tag
          ><span class="ml-2" v-if="s.row.healthStatus === 1">{{ s.row.latencyMs }} ms</span
          ><div class="text-xs text-gray-500 mt-1">{{ time(s.row.lastCheckTime) }}</div
          ><div class="text-xs text-red-500">{{ s.row.lastError }}</div></template
        ></el-table-column
      >
      <el-table-column label="部署服务器" width="125"
        ><template #default="s"
          ><el-button link type="primary" @click="detailRef.open(s.row.id)"
            >{{ s.row.deployedServerCount }} / {{ s.row.serverCount }} 已核对</el-button
          ></template
        ></el-table-column
      >
      <el-table-column label="用户领用" width="150"
        ><template #default="s"
          ><el-button link type="primary" @click="detailRef.open(s.row.id)"
            >有效 {{ s.row.activeUserCount }} · 过期 {{ s.row.expiredUserCount }}</el-button
          ></template
        ></el-table-column
      >
      <el-table-column label="操作" width="280" fixed="right"
        ><template #default="s">
          <el-button link type="primary" @click="detailRef.open(s.row.id)">详情</el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['xray:node:update']"
            @click="formRef.open(s.row.id)"
            >编辑</el-button
          >
          <el-button
            link
            type="primary"
            v-hasPermi="['xray:node:check']"
            :disabled="busy"
            @click="submit([s.row.id], 'check')"
            >检测</el-button
          >

          <el-button
            link
            :type="s.row.shelfStatus ? 'warning' : 'success'"
            v-hasPermi="['xray:node:shelf']"
            :disabled="busy"
            @click="shelf([s.row.id], !s.row.shelfStatus)"
            >{{ s.row.shelfStatus ? '下架' : '上架' }}</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
  <NodeForm ref="formRef" @success="load" /><NodeImport
    ref="importRef"
    @success="load"
  /><NodeDetail ref="detailRef" @operation="detailOperation" />

  <Dialog v-model="resultVisible" title="操作结果" width="850px">
    <el-alert
      v-if="taskRows.some((r) => r.status < 2)"
      title="任务在后台执行，可关闭窗口。页面每 2 秒刷新结果。"
      type="info"
      :closable="false"
      class="mb-4"
    />
    <el-table v-if="taskRows.length" :data="taskRows" max-height="450"
      ><el-table-column prop="nodeId" label="节点 ID" width="95" /><el-table-column
        prop="serverId"
        label="服务器 ID"
        width="100" /><el-table-column label="状态" width="100"
        ><template #default="s"
          ><el-tag
            :type="s.row.status === 3 ? 'danger' : s.row.status === 2 ? 'success' : 'info'"
            >{{ ['等待', '执行中', '成功', '失败'][s.row.status] }}</el-tag
          ></template
        ></el-table-column
      ><el-table-column prop="message" label="结果"
    /></el-table>
    <el-table v-else :data="operationRows" max-height="450"
      ><el-table-column prop="id" label="节点 ID" width="100" /><el-table-column
        label="状态"
        width="90"
        ><template #default="s">{{ s.row.success ? '成功' : '失败' }}</template></el-table-column
      ><el-table-column prop="message" label="结果"
    /></el-table>
    <template #footer
      ><el-button
        v-if="failedTasks.length"
        v-hasPermi="[failedTasks[0].action === 'check' ? 'xray:node:check' : 'xray:node:deploy']"
        :disabled="busy"
        @click="retryFailed"
        >重试失败项</el-button
      ><el-button v-if="batchId && taskRows.length" @click="refreshTask">刷新</el-button
      ><el-button @click="resultVisible = false">关闭</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/node'
import * as RegionApi from '@/api/xray/region'
import * as CityApi from '@/api/xray/city'
import dayjs from 'dayjs'
import NodeForm from './NodeForm.vue'
import NodeImport from './NodeImport.vue'
import NodeDetail from './NodeDetail.vue'
defineOptions({ name: 'XrayNode' })
const msg = useMessage()
const router = useRouter()
const regionOptions = ref<RegionApi.RegionVO[]>([])
const cities = ref<CityApi.CityVO[]>([])
const cityOptions = computed(() => cities.value.filter((c) => c.regionId === query.regionId))
const clearCity = () => {
  query.cityId = undefined
}
const location = (node: Api.NodeVO) =>
  node.region ? node.region + (node.city ? '-' + node.city : '（城市未分配）') : '—'
const loading = ref(false),
  busy = ref(false),
  list = ref<Api.NodeVO[]>([]),
  total = ref(0),
  stats = ref<Record<string, number>>({}),
  selected = ref<number[]>([])
const formRef = ref(),
  importRef = ref(),
  detailRef = ref(),
  tableRef = ref()
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  regionId: undefined as number | undefined,
  cityId: undefined as number | undefined,
  shelfStatus: undefined as number | undefined,
  healthStatus: undefined as number | undefined
})
const cards = [
  { key: 'total', label: '节点总数' },
  { key: 'up', label: '已上架' },
  { key: 'down', label: '已下架' },
  { key: 'healthy', label: '检测正常' },
  { key: 'unhealthy', label: '检测异常' }
]
const healthLabels = ['未检测', '正常', '异常']
const time = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '尚未检测')
const address = (node: Api.NodeVO) =>
  (node.host.includes(':') ? '[' + node.host + ']' : node.host) + ':' + node.port
const load = async () => {
  loading.value = true
  try {
    const [page, counts, regions, cityRows] = await Promise.all([
      Api.getPage(query),
      Api.getStats(),
      RegionApi.getList(),
      CityApi.getList()
    ])
    regionOptions.value = regions
    cities.value = cityRows
    list.value = page.list
    total.value = page.total
    stats.value = counts
    selected.value = []
    tableRef.value?.clearSelection()
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  Object.assign(query, {
    pageNo: 1,
    keyword: '',
    regionId: undefined as number | undefined,
    cityId: undefined as number | undefined,
    shelfStatus: undefined,
    healthStatus: undefined
  })
  load()
}
const selectionChange = (rows: Api.NodeVO[]) => {
  selected.value = rows.map((r) => r.id!)
}
const resultVisible = ref(false),
  operationRows = ref<Api.OperationResult[]>([]),
  taskRows = ref<Api.TaskRow[]>([]),
  batchId = ref('')
const shelf = async (ids: number[], up: boolean) => {
  await msg.confirm(
    up
      ? '上架后允许订阅领用，不会部署到服务器。检测结果供参考，是否继续？'
      : '下架后禁止新订阅领用；已领用用户继续使用，是否继续？'
  )
  busy.value = true
  try {
    operationRows.value = await Api.shelf(ids, up)
    taskRows.value = []
    resultVisible.value = true
    await load()
  } finally {
    busy.value = false
  }
}
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
let active = true
const stopPoll = () => {
  if (timer) clearTimeout(timer)
  timer = undefined
}
const refreshTask = async () => {
  if (!batchId.value || disposed) return
  stopPoll()
  try {
    const batch = batchId.value
    const rows = await Api.getTask(batch)
    if (disposed || batch !== batchId.value) return
    taskRows.value = rows
    if (rows.some((r) => r.status < 2) && active) timer = setTimeout(refreshTask, 2000)
    else if (!rows.some((r) => r.status < 2)) {
      await load()
      busy.value = false
    }
  } catch {
    busy.value = false
    stopPoll()
  }
}
const showTask = () => {
  resultVisible.value = true
  refreshTask()
}
const submit = async (
  ids: number[],
  action: 'check' | 'deploy' | 'verify' | 'remove',
  target?: number
) => {
  if (busy.value) return
  busy.value = true
  try {
    batchId.value = await Api.submit([...ids], action, target)
    operationRows.value = []
    taskRows.value = []
    resultVisible.value = true
    await refreshTask()
  } catch {
    busy.value = false
  }
}
const failedTasks = computed(() => taskRows.value.filter((r) => r.status === 3))
const retryFailed = async () => {
  const rows = [...failedTasks.value]
  if (!rows.length) return
  const action = rows[0].action as 'check' | 'deploy' | 'verify' | 'remove'
  if (action === 'remove') await msg.confirm('重新尝试移除失败节点的服务器出站，是否继续？')
  await submit(
    rows.map((r) => r.nodeId),
    action,
    rows[0].serverId
  )
}
const detailOperation = async (id: number, target: number, action: 'verify' | 'remove') => {
  if (action === 'remove')
    await msg.confirm(
      '将移除服务器上的专属 SOCKS5 出站配置。需先下架且没有有效领用或路由引用，是否继续？'
    )
  await submit([id], action, target)
}
onMounted(load)
onBeforeUnmount(() => {
  disposed = true
  stopPoll()
})
onDeactivated(() => {
  active = false
  stopPoll()
})
onActivated(() => {
  if (!active) load()
  active = true
  disposed = false
  if (batchId.value && busy.value) refreshTask()
})
</script>
