<template>
  <Dialog v-model="visible" :title="form.id ? '编辑节点' : '新增 SOCKS5 节点'" width="640px">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="100px">
      <el-form-item v-if="!form.id" label="粘贴连接">
        <el-input
          v-model="connection"
          placeholder="地址:端口:账号:密码 或 socks5://账号:密码@地址:端口"
        />
        <el-button class="mt-2" @click="parse">填入表单</el-button>
      </el-form-item>
      <el-form-item label="名称" prop="name"
        ><el-input v-model="form.name" maxlength="128"
      /></el-form-item>
      <el-form-item label="主机 / IP" prop="host"
        ><el-input v-model="form.host" placeholder="域名、IPv4 或 IPv6" maxlength="255"
      /></el-form-item>
      <el-form-item label="端口" prop="port"
        ><el-input-number v-model="form.port" :min="1" :max="65535"
      /></el-form-item>
      <el-form-item label="认证方式"
        ><el-radio-group v-model="form.authType"
          ><el-radio :value="0">无认证</el-radio
          ><el-radio :value="1">账号密码</el-radio></el-radio-group
        ></el-form-item
      >
      <template v-if="form.authType === 1">
        <el-form-item label="账号" prop="username"
          ><el-input v-model="form.username" autocomplete="off" maxlength="255"
        /></el-form-item>
        <el-form-item label="密码" prop="password"
          ><el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="form.passwordConfigured ? '留空保留现有密码' : '请输入密码'"
            maxlength="255"
        /></el-form-item>
      </template>
      <el-form-item label="地区" prop="regionId">
        <el-select
          v-model="form.regionId"
          @change="clearCity"
          filterable
          placeholder="请选择已创建的地区"
          class="w-full"
        >
          <el-option
            v-for="region in regionOptions"
            :key="region.id"
            :value="region.id!"
            :label="region.name + (region.status ? '（已停用）' : '')"
            :disabled="region.status !== 0 && region.id !== originalRegionId"
          />
        </el-select>
        <el-button link type="primary" @click="loadRegions">刷新地区</el-button>
        <span v-if="!regionOptions.length" class="text-gray-500"
          >请先在 Xray 管理 → 地区与城市管理中新增地区</span
        >
      </el-form-item>
      <el-form-item label="城市" prop="cityId">
        <el-select
          v-model="form.cityId"
          filterable
          :disabled="!form.regionId"
          placeholder="请先选择地区，再选择城市"
          class="w-full"
        >
          <el-option
            v-for="city in cityOptions"
            :key="city.id"
            :value="city.id!"
            :label="city.name + (city.status ? '（已停用）' : '')"
            :disabled="cityDisabled(city)"
          />
        </el-select>
        <span v-if="form.regionId && !cityOptions.length" class="text-gray-500"
          >请先在地区与城市管理中为该地区新增城市</span
        >
      </el-form-item>
      <el-form-item label="标签"
        ><el-input v-model="form.tags" maxlength="255" placeholder="多个标签用逗号分隔"
      /></el-form-item>
      <el-form-item label="备注"
        ><el-input v-model="form.remark" type="textarea" maxlength="500"
      /></el-form-item>
      <el-alert
        :closable="false"
        type="info"
        title="新节点默认下架。上架仅允许订阅领用，领用时才部署；修改连接信息后需确认并重新上架。"
      />
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="loading" @click="save">保存</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/node'
import * as RegionApi from '@/api/xray/region'
import * as CityApi from '@/api/xray/city'
const emit = defineEmits(['success'])
const msg = useMessage()
const visible = ref(false),
  loading = ref(false),
  formRef = ref(),
  connection = ref('')
const initial = (): Api.NodeVO => ({
  name: '',
  host: '',
  port: 1080,
  authType: 0,
  username: '',
  password: '',
  regionId: undefined,
  cityId: undefined,
  tags: '',
  remark: ''
})
const form = ref<Api.NodeVO>(initial())
const regionOptions = ref<RegionApi.RegionVO[]>([])
const originalRegionId = ref<number>()
const originalCityId = ref<number>()
const cities = ref<CityApi.CityVO[]>([])
const cityOptions = computed(() => cities.value.filter((c) => c.regionId === form.value.regionId))
const clearCity = () => {
  form.value.cityId = undefined
  formRef.value?.clearValidate('cityId')
}
const cityDisabled = (city: CityApi.CityVO) =>
  (city.status !== 0 ||
    regionOptions.value.find((r) => r.id === form.value.regionId)?.status !== 0) &&
  city.id !== originalCityId.value
const loadRegions = async () => {
  const [regions, cityRows] = await Promise.all([RegionApi.getList(), CityApi.getList()])
  regionOptions.value = regions
  cities.value = cityRows
}
const rules = {
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
  regionId: [{ required: true, message: '请选择地区', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机或 IP', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'change' }],
  username: [
    {
      validator: (_: any, value: string, cb: any) =>
        cb(form.value.authType === 1 && !value ? new Error('请输入账号') : undefined),
      trigger: 'blur'
    }
  ],
  password: [
    {
      validator: (_: any, value: string, cb: any) =>
        cb(
          form.value.authType === 1 && !value && !form.value.passwordConfigured
            ? new Error('请输入密码')
            : undefined
        ),
      trigger: 'blur'
    }
  ]
}
const open = async (id?: number) => {
  form.value = initial()
  connection.value = ''
  visible.value = true
  await nextTick()
  formRef.value?.clearValidate()
  originalRegionId.value = undefined
  originalCityId.value = undefined
  loading.value = true
  try {
    await loadRegions()
    if (id) {
      form.value = { ...initial(), ...(await Api.getNode(id)), password: '' }
      originalRegionId.value = form.value.regionId
      originalCityId.value = form.value.cityId
    }
  } finally {
    loading.value = false
  }
}
const parse = () => {
  try {
    const input = connection.value.trim()
    const compact = input.match(/^(\[[^\]]+\]|[^:\s]+):([0-9]+):([^:]+):(.+)$/)
    if (compact) {
      const port = Number(compact[2])
      if (!Number.isInteger(port) || port < 1 || port > 65535 || !compact[3].trim())
        throw new Error()
      const host = new URL('socks5://' + compact[1] + ':' + port).hostname.replace(/^\[|\]$/g, '')
      form.value = {
        ...form.value,
        name: compact[1] + ':' + port,
        host,
        port,
        authType: 1,
        username: compact[3],
        password: compact[4]
      }
      connection.value = ''
      return
    }
    const url = new URL(input)
    if (
      !['socks5:', 'socks:'].includes(url.protocol) ||
      !url.hostname ||
      !url.port ||
      url.search ||
      (url.pathname && url.pathname !== '/')
    )
      throw new Error()
    const decode = (value: string) => decodeURIComponent(value)
    form.value = {
      ...form.value,
      name: decode(url.hash.slice(1)) || url.hostname + ':' + url.port,
      host: url.hostname.replace(/^\[|\]$/g, ''),
      port: Number(url.port),
      authType: url.username || url.password ? 1 : 0,
      username: decode(url.username),
      password: decode(url.password)
    }
    connection.value = ''
  } catch {
    msg.error('连接格式无效，请检查地址、端口和认证信息')
  }
}
const save = async () => {
  if (!(await formRef.value.validate())) return
  loading.value = true
  try {
    form.value.id ? await Api.update(form.value) : await Api.create(form.value)
    msg.success('保存成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
watch(visible, (value) => {
  if (!value) {
    form.value = initial()
    connection.value = ''
  }
})
defineExpose({ open })
</script>
