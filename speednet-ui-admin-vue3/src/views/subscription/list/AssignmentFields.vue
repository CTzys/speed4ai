<template>
  <el-table :data="modelValue" border>
    <el-table-column label="出口节点" min-width="190">
      <template #default="s">
        <el-select v-model="s.row.nodeId" filterable placeholder="选择已上架节点">
          <el-option
            v-for="n in availableNodes"
            :key="n.id"
            :value="n.id"
            :label="nodeLabel(n)"
            :disabled="modelValue.some((a, i) => i !== s.$index && a.nodeId === n.id)"
          />
        </el-select>
      </template>
    </el-table-column>
    <el-table-column label="服务器" min-width="160">
      <template #default="s">
        <el-select v-model="s.row.serverId" filterable @change="serverChanged(s.row)">
          <el-option
            v-for="server in options.servers"
            :key="server.id"
            :label="server.name"
            :value="server.id"
          />
        </el-select>
      </template>
    </el-table-column>
    <el-table-column label="入站" min-width="190">
      <template #default="s">
        <el-select
          v-model="s.row.inboundId"
          :disabled="!s.row.serverId"
          :loading="loadingServer === s.row.serverId"
          placeholder="选择入站"
        >
          <el-option
            v-for="inbound in inbounds[s.row.serverId] || []"
            :key="inbound.id"
            :value="inbound.id"
            :label="`${inbound.remark || '入站'} · ${inbound.protocol} :${inbound.port}`"
          />
        </el-select>
      </template>
    </el-table-column>
    <el-table-column label="用户连接地址" min-width="190">
      <template #default="s"
        ><el-input v-model="s.row.publicHost" placeholder="用户连接的域名 / IP"
      /></template>
    </el-table-column>
    <el-table-column v-if="removable" label="操作" width="70">
      <template #default="s"
        ><el-button link type="danger" @click="remove(s.$index)">移除</el-button></template
      >
    </el-table-column>
  </el-table>
  <div class="mt-2 text-gray-500"
    >选择启用的 VMess / VLESS / Trojan 入站。用户连接地址可使用服务器域名，不能填写 SOCKS5
    出口地址。支持 TCP、WebSocket、gRPC，安全类型为普通或 TLS。</div
  >
</template>
<script setup lang="ts">
import * as Api from '@/api/subscription'
const props = defineProps<{
  modelValue: Api.Assignment[]
  options: Api.Options
  regionId?: number
  cityId?: number
  removable?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [Api.Assignment[]] }>()
const inbounds = reactive<Record<number, Awaited<ReturnType<typeof Api.getInbounds>>>>({})
const loadingServer = ref<number>()
const availableNodes = computed(() =>
  props.options.nodes.filter(
    (n) =>
      (!props.regionId || n.regionId === props.regionId) &&
      (!props.cityId || n.cityId === props.cityId)
  )
)
const nodeLabel = (n: Api.Options['nodes'][number]) => {
  const region = props.options.regions.find((r) => r.id === n.regionId)?.name
  const city = props.options.cities.find((c) => c.id === n.cityId)?.name
  return `${n.name}${region ? ` · ${region}${city ? '-' + city : ''}` : ''}`
}
const serverChanged = async (row: Api.Assignment) => {
  row.inboundId = undefined
  row.publicHost = props.options.servers.find((s) => s.id === row.serverId)?.host || ''
  if (!row.serverId) return
  loadingServer.value = row.serverId
  try {
    inbounds[row.serverId] = await Api.getInbounds(row.serverId)
  } finally {
    loadingServer.value = undefined
  }
}
watch(
  () => props.modelValue.map((row) => row.serverId),
  async (ids) => {
    for (const id of new Set(ids)) {
      if (id && !inbounds[id]) inbounds[id] = await Api.getInbounds(id)
    }
  },
  { immediate: true }
)
const remove = (index: number) =>
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index)
  )
</script>
