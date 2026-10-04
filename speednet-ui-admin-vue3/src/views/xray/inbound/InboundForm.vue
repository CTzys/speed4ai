<template>
  <Dialog v-model="visible" :title="form.id ? '编辑入站' : '新增入站'" width="760px">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="140px">
      <el-form-item label="目标服务器" prop="serverId">
        <el-select
          v-model="form.serverId"
          filterable
          remote
          :remote-method="searchServers"
          :loading="serverLoading"
          :disabled="!!form.id"
          placeholder="按名称搜索并选择服务器"
          class="w-full"
        >
          <el-option
            v-for="server in servers"
            :key="server.id"
            :value="server.id!"
            :label="`${server.name} (${server.host})`"
            :disabled="!isConfigured(server)"
          >
            <span>{{ server.name }}（{{ server.host }}）</span>
            <el-tag
              class="ml-2"
              size="small"
              :type="isConfigured(server) ? 'success' : 'warning'"
              >{{ isConfigured(server) ? 'API 已配置' : '待配置' }}</el-tag
            >
          </el-option>
        </el-select>
      </el-form-item>
      <el-alert
        title="请选择已配置面板连接信息和 API Token 的服务器。"
        type="info"
        :closable="false"
        class="mb-4"
      />
      <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      <el-form-item label="启用"><el-switch v-model="form.enable" /></el-form-item>
      <el-form-item label="协议" prop="protocol">
        <el-select
          v-model="form.protocol"
          filterable
          allow-create
          default-first-option
          @change="changeProtocol"
        >
          <el-option v-for="p in protocols" :key="p" :label="p" :value="p" />
        </el-select>
      </el-form-item>
      <el-form-item label="监听地址"
        ><el-input v-model="form.listen" placeholder="留空使用面板默认地址"
      /></el-form-item>
      <el-form-item label="端口" prop="port">
        <el-input-number v-model="form.port" :min="1" :max="65535" :precision="0" />
        <el-button class="ml-2" :disabled="loading" @click="generateRandomPort">
          随机生成
        </el-button>
      </el-form-item>
      <el-form-item label="总流量（字节）"
        ><el-input-number
          v-model="form.total"
          :min="0"
          :max="Number.MAX_SAFE_INTEGER"
          :precision="0"
        /><span class="ml-2">0 表示不限</span></el-form-item
      >
      <el-form-item label="到期时间（毫秒）"
        ><el-input-number
          v-model="form.expiryTime"
          :min="0"
          :max="Number.MAX_SAFE_INTEGER"
          :precision="0"
        /><span class="ml-2">Unix 时间戳，0 表示不限</span></el-form-item
      >
      <el-alert
        title="切换协议会自动载入对应 JSON 模板，切回可恢复本次填写的配置。请补全客户端、密码 / 密钥及 TLS / Reality 等参数。"
        type="info"
        :closable="false"
        class="mb-4"
      />
      <el-form-item v-for="field in jsonFields" :key="field" :label="field" :prop="field">
        <el-input
          v-model="form[field]"
          type="textarea"
          :rows="field === 'settings' ? 8 : 5"
          spellcheck="false"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="loading" @click="submit">保存到服务器</el-button>
      <el-button :disabled="loading" @click="visible = false">取消</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/inbound'
import * as ServerApi from '@/api/xray/server'
import type { FormRules } from 'element-plus'
const emit = defineEmits<{ success: [serverId: number] }>()
const visible = ref(false),
  loading = ref(false),
  formRef = ref()
const servers = ref<ServerApi.XrayServerVO[]>([])
const serverLoading = ref(false)
let searchSequence = 0
const protocols = [
  'vless',
  'vmess',
  'trojan',
  'shadowsocks',
  'socks',
  'http',
  'dokodemo-door',
  'wireguard'
]
const jsonFields = ['settings', 'streamSettings', 'sniffing'] as const
type JsonConfig = Record<(typeof jsonFields)[number], string>
const protocolSettings: Record<string, Record<string, unknown>> = {
  vless: { clients: [], decryption: 'none', fallbacks: [] },
  vmess: { clients: [] },
  trojan: { clients: [], fallbacks: [] },
  shadowsocks: { method: 'aes-256-gcm', password: '', network: 'tcp,udp' },
  socks: { auth: 'password', accounts: [], udp: true },
  http: { accounts: [], allowTransparent: false },
  'dokodemo-door': { address: '', port: 0, network: 'tcp,udp', followRedirect: false },
  wireguard: { secretKey: '', peers: [], mtu: 1420 }
}
const protocolDefaults = (protocol: string): JsonConfig => ({
  settings: JSON.stringify(
    Object.hasOwn(protocolSettings, protocol) ? protocolSettings[protocol] : {},
    null,
    2
  ),
  streamSettings: JSON.stringify(
    protocol === 'wireguard' ? {} : { network: 'tcp', security: 'none' },
    null,
    2
  ),
  sniffing: JSON.stringify(
    protocol === 'wireguard'
      ? { enabled: false }
      : { enabled: true, destOverride: ['http', 'tls'] },
    null,
    2
  )
})
const protocolDrafts = new Map<string, JsonConfig>()
let activeProtocol = 'vless'
const empty = () => ({
  id: undefined as number | undefined,
  serverId: undefined as number | undefined,
  remark: '',
  enable: true,
  listen: '',
  port: 443,
  protocol: 'vless',
  total: 0,
  expiryTime: 0,
  ...protocolDefaults('vless')
})
const form = ref(empty())
const changeProtocol = (protocol: string) => {
  if (protocol === activeProtocol) return
  protocolDrafts.set(activeProtocol, {
    settings: form.value.settings,
    streamSettings: form.value.streamSettings,
    sniffing: form.value.sniffing
  })
  Object.assign(form.value, protocolDrafts.get(protocol) ?? protocolDefaults(protocol))
  activeProtocol = protocol
  formRef.value?.clearValidate([...jsonFields])
}
const generateRandomPort = () => {
  const min = 1024
  const max = 65535
  const current = form.value.port
  const excludeCurrent = Number.isInteger(current) && current >= min && current <= max
  let port = min + Math.floor(Math.random() * (max - min + (excludeCurrent ? 0 : 1)))
  if (excludeCurrent && port >= current) port += 1
  form.value.port = port
}
const jsonValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
    callback()
  } catch {
    callback(new Error('请输入有效的 JSON 对象'))
  }
}
const rules: FormRules = {
  serverId: [{ required: true, message: '请选择目标服务器', trigger: 'change' }],
  protocol: [{ required: true, message: '请选择协议' }],
  port: [{ required: true, message: '请输入端口' }],
  ...Object.fromEntries(
    jsonFields.map((field) => [field, [{ validator: jsonValidator, trigger: 'blur' }]])
  )
}
const isConfigured = (server: ServerApi.XrayServerVO) =>
  ['http', 'https'].includes(server.panelScheme ?? '') &&
  !!server.panelPort &&
  !!server.panelTokenConfigured
const searchServers = async (name = '') => {
  const seq = ++searchSequence
  serverLoading.value = true
  try {
    const page = await ServerApi.getServerPage({ pageNo: 1, pageSize: 100, name })
    const current = servers.value.find((server) => server.id === form.value.serverId)
    if (seq === searchSequence)
      servers.value =
        current && !page.list.some((server: ServerApi.XrayServerVO) => server.id === current.id)
          ? [current, ...page.list]
          : page.list
  } finally {
    if (seq === searchSequence) serverLoading.value = false
  }
}
const open = async (targetId?: number, id?: number) => {
  protocolDrafts.clear()
  activeProtocol = 'vless'
  form.value = { ...empty(), id }
  servers.value = []
  visible.value = true
  loading.value = true
  await nextTick()
  formRef.value?.clearValidate()
  try {
    await searchServers()
    if (targetId) {
      let target = servers.value.find((server) => server.id === targetId)
      if (!target) {
        target = await ServerApi.getServer(targetId)
        servers.value.push(target!)
      }
      if (id || isConfigured(target!)) form.value.serverId = targetId
    }
    if (id && targetId) {
      const data = await Api.getInbound(targetId, id)
      form.value = {
        ...form.value,
        ...data,
        id,
        serverId: targetId,
        settings: '',
        streamSettings: '',
        sniffing: ''
      }
      for (const field of jsonFields)
        form.value[field] = JSON.stringify(
          typeof data[field] === 'string' ? JSON.parse(data[field] as string) : (data[field] ?? {}),
          null,
          2
        )
      activeProtocol = form.value.protocol
    }
  } catch (error) {
    visible.value = false
    throw error
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const { serverId: targetId, ...config } = form.value
    if (
      !targetId ||
      !servers.value.some((server) => server.id === targetId && isConfigured(server))
    ) {
      useMessage().warning('请先完善目标服务器的面板连接信息和 API Token')
      return
    }
    const warning = config.id
      ? await Api.updateInbound({ serverId: targetId, id: config.id, config })
      : await Api.createInbound({ serverId: targetId, config })
    if (typeof warning === 'string' && warning) useMessage().warning(warning)
    else useMessage().success('入站已保存到服务器')
    visible.value = false
    emit('success', targetId)
  } finally {
    loading.value = false
  }
}
</script>
