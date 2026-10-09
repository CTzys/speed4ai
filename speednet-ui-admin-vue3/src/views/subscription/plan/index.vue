<template>
  <ContentWrap>
    <div class="mb-4 flex flex-wrap gap-3">
      <el-input v-model="search" clearable placeholder="搜索套餐名称" class="!w-240px" />
      <el-select v-model="regionFilter" clearable placeholder="节点地区" class="!w-180px">
        <el-option v-for="r in options.regions" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-button type="primary" @click="edit()">新增订阅套餐</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="filteredPlans">
      <el-table-column prop="name" label="套餐名称" min-width="160" />
      <el-table-column label="订阅时长 / 价格" min-width="230">
        <template #default="{ row }">
          <div v-for="p in periodPrices(row)" :key="p.months">
            {{ p.months }} 个月 · ¥{{ (p.price / 100).toFixed(2) }}
          </div>
          <span v-if="!periodPrices(row).length" class="text-gray-500">未配置可售周期</span>
        </template>
      </el-table-column>
      <el-table-column label="周期流量" min-width="120">
        <template #default="{ row }">{{ bytes(row.total_bytes) }}</template>
      </el-table-column>
      <el-table-column label="流量重置" min-width="150">
        <template #default="{ row }">{{ resetLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="节点地区" min-width="160">
        <template #default="{ row }">{{ regionNames(row) }}</template>
      </el-table-column>
      <el-table-column label="权限组 / 节点上限" min-width="160">
        <template #default="{ row }">
          {{ groups.find((g) => g.id === row.group_id)?.name || '专用节点' }} / {{ row.node_limit }}
        </template>
      </el-table-column>
      <el-table-column label="补充服务" min-width="150">
        <template #default="{ row }">{{ supplementLabel(row) }}</template>
      </el-table-column>
      <el-table-column prop="level" label="升级等级" width="90" />
      <el-table-column label="销售规则" min-width="180">
        <template #default="{ row }">
          {{ row.visible ? '展示' : '隐藏' }} / {{ row.enabled ? '可新购' : '停售' }} /
          {{ row.renew_enabled ? '可续订' : '不续订' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="90">
        <template #default="{ row }"
          ><el-button link type="primary" @click="edit(row)">编辑</el-button></template
        >
      </el-table-column>
    </el-table>
    <p class="text-gray-500 mt-3">地区来自实际配置的节点；订阅时长与流量重置周期分别设置。</p>
  </ContentWrap>
  <el-dialog
    v-model="dialog"
    title="套餐商品"
    width="min(1000px,95vw)"
    :close-on-click-modal="false"
  >
    <el-form :model="form" label-width="110px"
      ><el-form-item label="名称" required
        ><el-input v-model="form.name" maxlength="128" /></el-form-item
      ><el-form-item label="介绍"
        ><el-input v-model="form.description" type="textarea" maxlength="4000"
      /></el-form-item>
      <el-row :gutter="20"
        ><el-col :span="12"
          ><el-form-item label="升级等级"
            ><el-input-number v-model="form.level" :min="1" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item></el-col
      ></el-row>
      <el-form-item label="销售规则"
        ><el-checkbox v-model="form.visible">前台展示</el-checkbox
        ><el-checkbox v-model="form.enabled">允许新购 / 升级</el-checkbox
        ><el-checkbox v-model="form.renewEnabled">允许续订</el-checkbox></el-form-item
      >
      <el-row :gutter="20"
        ><el-col :span="12"
          ><el-form-item label="周期额度 GiB" required
            ><el-input-number
              v-model="form.gib"
              :min="0"
              :max="1000000"
              :precision="2" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="节点上限"
            ><el-input-number v-model="form.nodeLimit" :min="1" :max="20" /></el-form-item></el-col
      ></el-row>
      <el-form-item label="流量重置"
        ><el-select v-model="form.resetMode"
          ><el-option label="每月（从周期开始日计算）" value="monthly" /><el-option
            label="固定天数"
            value="interval" /><el-option label="不重置" value="none" /><el-option
            label="每月1日"
            value="monthly_first" /><el-option
            label="每月到期日"
            value="monthly_expiry" /><el-option label="每年1月1日" value="yearly_first" /><el-option
            label="每年到期日"
            value="yearly_expiry" /></el-select
        ><el-input-number
          v-if="form.resetMode === 'interval'"
          v-model="form.resetIntervalDays"
          :min="1"
          :max="365"
          class="ml-2"
      /></el-form-item>
      <el-form-item label="流量计费"
        ><el-radio-group v-model="form.trafficMode"
          ><el-radio value="both">上传 + 下载</el-radio
          ><el-radio value="download">仅下载</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item label="套餐容量"
        ><el-input-number v-model="form.capacityLimit" :min="0" :max="100000000" /><span
          class="ml-2"
          >0 为不限制；按有效订阅及待支付占用计算</span
        ></el-form-item
      ><h3>价格方案（选填）</h3
      ><p class="text-gray-500"
        >价格留空表示不售卖该周期，0 元表示免费。可先保存套餐，再配置价格。</p
      ><el-table :data="form.prices" border
        ><el-table-column label="名称"
          ><template #default="s"
            ><el-input
              v-model="s.row.name"
              placeholder="选填，自动生成" /></template></el-table-column
        ><el-table-column label="类型"
          ><template #default="s"
            ><el-select v-model="s.row.kind"
              ><el-option label="套餐周期" value="period" /><el-option
                label="补充流量"
                value="traffic" /><el-option
                label="流量重置"
                value="reset" /></el-select></template></el-table-column
        ><el-table-column label="月数 / GiB"
          ><template #default="s"
            ><el-input-number
              v-if="s.row.kind === 'period'"
              v-model="s.row.months"
              :min="1"
              :max="36" /><el-input-number
              v-else-if="s.row.kind === 'traffic'"
              v-model="s.row.gib"
              :min="0.01"
              :precision="2"
              :max="1000000" /></template></el-table-column
        ><el-table-column label="价格（元）"
          ><template #default="s"
            ><el-input-number
              v-model="s.row.yuan"
              :min="0"
              :max="1000000"
              :precision="2" /></template></el-table-column
        ><el-table-column label="启用" width="70"
          ><template #default="s"
            ><el-checkbox v-model="s.row.enabled" /></template></el-table-column
        ><el-table-column width="90"
          ><template #default="s"
            ><el-button link type="danger" @click="form.prices.splice(s.$index, 1)"
              >移除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-button class="mt-2 mb-4" @click="addPrice">添加价格</el-button>
      <h3>节点权限组（选填）</h3
      ><el-select v-model="form.groupId" clearable placeholder="选择已配置的节点权限组"
        ><el-option v-for="g in groups" :key="g.id" :value="g.id" :label="g.name" /></el-select
      ><el-button class="ml-2" @click="editGroup()">新增权限组</el-button
      ><el-button
        v-if="form.groupId"
        class="ml-2"
        @click="editGroup(groups.find((g) => g.id === form.groupId))"
        >编辑权限组</el-button
      ><p class="text-gray-500"
        >保存时可暂不配置节点。购买前必须配置权限组或下方专用节点；专用节点优先，权限组按节点上限分配。</p
      ><h3>套餐专用节点（选填）</h3
      ><AssignmentFields v-model="form.assignments" :options="options" removable /><el-button
        class="mt-2"
        :disabled="form.assignments.length >= form.nodeLimit"
        @click="form.assignments.push({ publicHost: '' })"
        >添加节点</el-button
      >
      <el-alert
        class="mt-4"
        title="编辑套餐只影响新成交的权益快照，不自动覆盖老用户订阅。升级按等级向上变更；剩余时间折抵后开启新周期，超出目标价格的折抵余额不退回。"
        type="info"
        :closable="false"
      /> </el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存套餐</el-button></template
    >
  </el-dialog>
  <el-dialog
    v-model="groupDialog"
    title="节点权限组"
    width="min(1000px,95vw)"
    :close-on-click-modal="false"
  >
    <el-form label-width="110px"
      ><el-form-item label="名称" required
        ><el-input v-model="groupForm.name" maxlength="128" /></el-form-item
    ></el-form>
    <AssignmentFields v-model="groupForm.assignments" :options="options" removable />
    <el-button
      class="mt-2"
      :disabled="groupForm.assignments.length >= 20"
      @click="groupForm.assignments.push({ publicHost: '' })"
      >添加节点</el-button
    >
    <template #footer
      ><el-button @click="groupDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveGroup"
        >保存权限组</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import request from '@/config/axios'
import AssignmentFields from '../list/AssignmentFields.vue'
import { getOptions, bytes, type Options, type Assignment } from '@/api/subscription'
interface Price {
  name: string
  kind: string
  months: number
  gib: number
  yuan: number | undefined
  enabled: boolean
}
interface Plan {
  id: number
  group_id: number | null
  name: string
  description: string
  level: number
  sort: number
  visible: boolean
  enabled: boolean
  renew_enabled: boolean
  total_bytes: number
  reset_mode: string
  reset_interval_days: number
  traffic_mode: string
  capacity_limit: number
  node_limit: number
  prices: {
    name: string
    kind: string
    months: number
    bytes: number
    price: number
    enabled: boolean
  }[]
  assignments: Assignment[]
}
const periodNames: Record<number, string> = {
  1: '月付',
  3: '季付',
  6: '半年付',
  12: '年付',
  24: '两年付',
  36: '三年付'
}
interface NodeGroup {
  id: number
  name: string
  assignments: Assignment[]
}
const search = ref('')
const regionFilter = ref<number>()
const groups = ref<NodeGroup[]>([]),
  groupDialog = ref(false),
  groupForm = ref({
    id: undefined as number | undefined,
    name: '',
    assignments: [] as Assignment[]
  })
const defaults = () => ({
  id: undefined as number | undefined,
  name: '',
  groupId: undefined as number | undefined,
  description: '',
  level: 1,
  sort: 0,
  visible: false,
  enabled: false,
  renewEnabled: true,
  gib: 100,
  resetMode: 'monthly',
  resetIntervalDays: 30,
  trafficMode: 'both',
  nodeLimit: 1,
  capacityLimit: 0,
  prices: [1, 3, 6, 12, 24, 36].map((months) => ({
    name: periodNames[months],
    kind: 'period',
    months,
    gib: 0,
    yuan: undefined,
    enabled: true
  })) as Price[],
  assignments: [] as Assignment[]
})
const plans = ref<Plan[]>([]),
  form = ref(defaults()),
  dialog = ref(false),
  loading = ref(false),
  saving = ref(false)
const options = ref<Options>({ nodes: [], servers: [], regions: [], cities: [] }),
  message = useMessage()
function fillPrices(prices: Plan['prices']): Price[] {
  const values = prices.map((v) => ({
    name: v.name,
    kind: v.kind,
    months: v.months,
    gib: v.bytes / 1073741824,
    yuan: v.price / 100,
    enabled: !!v.enabled
  }))
  return [
    ...[1, 3, 6, 12, 24, 36].map(
      (months) =>
        values.find((v) => v.kind === 'period' && v.months === months) ?? {
          name: periodNames[months],
          kind: 'period',
          months,
          gib: 0,
          yuan: undefined,
          enabled: true
        }
    ),
    ...values.filter((v) => v.kind !== 'period' || ![1, 3, 6, 12, 24, 36].includes(v.months))
  ]
}
function effectiveAssignments(p: Plan): Assignment[] {
  return p.assignments.length
    ? p.assignments
    : (groups.value.find((g) => g.id === p.group_id)?.assignments ?? []).slice(0, p.node_limit)
}
function regionIds(p: Plan): number[] {
  return [
    ...new Set(
      effectiveAssignments(p).flatMap((a) => {
        const id = options.value.nodes.find((n) => n.id === a.nodeId)?.regionId
        return id == null ? [] : [id]
      })
    )
  ]
}
function regionNames(p: Plan): string {
  const ids = regionIds(p)
  return ids.length
    ? ids
        .map((id) => options.value.regions.find((r) => r.id === id)?.name ?? `地区 ${id}`)
        .join('、')
    : '未配置地区'
}
function periodPrices(p: Plan) {
  return p.prices
    .filter((v) => v.kind === 'period' && v.enabled)
    .sort((a, b) => a.months - b.months)
}
function resetLabel(p: Plan): string {
  const names: Record<string, string> = {
    monthly: '每月（周期开始日）',
    monthly_first: '每月1日',
    monthly_expiry: '每月到期日',
    yearly_first: '每年1月1日',
    yearly_expiry: '每年到期日',
    none: '不重置'
  }
  return p.reset_mode === 'interval'
    ? `每 ${p.reset_interval_days} 天`
    : (names[p.reset_mode] ?? p.reset_mode)
}
function supplementLabel(p: Plan): string {
  return (
    p.prices
      .filter((v) => v.enabled && v.kind !== 'period')
      .map(
        (v) =>
          `${v.kind === 'traffic' ? bytes(v.bytes) + ' 流量包' : '流量重置'} ¥${(v.price / 100).toFixed(2)}`
      )
      .join('；') || '未配置'
  )
}
const filteredPlans = computed(() =>
  plans.value.filter(
    (p) =>
      p.name.toLowerCase().includes(search.value.trim().toLowerCase()) &&
      (regionFilter.value == null || regionIds(p).includes(regionFilter.value))
  )
)
async function load() {
  loading.value = true
  try {
    ;[plans.value, groups.value] = await Promise.all([
      request.get({ url: '/subscription/plan/list' }),
      request.get({ url: '/subscription/plan/groups' })
    ])
  } finally {
    loading.value = false
  }
}
async function edit(p?: Plan) {
  try {
    options.value = await getOptions()
  } catch {
    message.warning('节点选项加载失败，仍可保存套餐基本信息')
  }
  form.value = p
    ? {
        id: p.id,
        groupId: p.group_id ?? undefined,
        name: p.name,
        description: p.description,
        level: p.level,
        sort: p.sort,
        visible: !!p.visible,
        enabled: !!p.enabled,
        renewEnabled: !!p.renew_enabled,
        gib: p.total_bytes / 1073741824,
        resetMode: p.reset_mode,
        resetIntervalDays: p.reset_interval_days,
        trafficMode: p.traffic_mode,
        nodeLimit: p.node_limit,
        capacityLimit: p.capacity_limit,
        prices: fillPrices(p.prices),
        assignments: p.assignments.map((a) => ({ ...a }))
      }
    : defaults()
  dialog.value = true
}
function addPrice() {
  form.value.prices.push({
    name: '新价格',
    kind: 'period',
    months: 1,
    gib: 10,
    yuan: 10,
    enabled: true
  })
}
async function save() {
  if (!form.value.name.trim()) {
    message.error('请填写套餐名称')
    return
  }
  if (
    form.value.assignments.some(
      (a) => !a.nodeId || !a.serverId || !a.inboundId || !a.publicHost?.trim()
    )
  ) {
    message.error('已添加的节点请配置完整，或移除空节点行')
    return
  }
  saving.value = true
  try {
    await request.post({
      url: '/subscription/plan/save',
      data: {
        ...form.value,
        totalBytes: Math.round(form.value.gib * 1073741824),
        prices: form.value.prices
          .filter((p) => p.yuan !== undefined && p.yuan !== null)
          .map((p) => ({
            ...p,
            price: Math.round(p.yuan! * 100),
            bytes: Math.round(p.gib * 1073741824)
          }))
      }
    })
    message.success('保存成功')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
function editGroup(g?: NodeGroup) {
  groupForm.value = g
    ? { id: g.id, name: g.name, assignments: g.assignments.map((a) => ({ ...a })) }
    : { id: undefined, name: '', assignments: [] }
  groupDialog.value = true
}
async function saveGroup() {
  if (!groupForm.value.name.trim()) {
    message.error('请填写权限组名称')
    return
  }
  if (
    groupForm.value.assignments.some(
      (a) => !a.nodeId || !a.serverId || !a.inboundId || !a.publicHost?.trim()
    )
  ) {
    message.error('已添加的节点请配置完整，或移除空节点行')
    return
  }
  saving.value = true
  try {
    const id = await request.post({ url: '/subscription/plan/group/save', data: groupForm.value })
    form.value.groupId = id
    groupDialog.value = false
    await load()
    message.success('权限组已保存')
  } finally {
    saving.value = false
  }
}
onMounted(async () => {
  await Promise.all([
    load(),
    getOptions()
      .then((value) => {
        options.value = value
      })
      .catch(() => {
        message.warning('节点地区加载失败，请检查节点查询权限或配置')
      })
  ])
})
</script>
