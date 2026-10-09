<template>
  <Dialog v-model="visible" :title="title"
    ><el-form ref="formRef" :model="form" :rules="rules" label-width="110px" v-loading="loading">
      <el-form-item label="服务器名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="主机/IP" prop="host"><el-input v-model="form.host" /></el-form-item>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="SSH 端口" prop="sshPort"
            ><el-input-number v-model="form.sshPort" :min="1" :max="65535" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="SSH 用户" prop="sshUsername"
            ><el-input v-model="form.sshUsername" /></el-form-item></el-col
      ></el-row>
      <el-form-item label="认证方式"
        ><el-radio-group v-model="form.sshAuthType"
          ><el-radio :value="1">密码</el-radio><el-radio :value="2">私钥</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item v-if="form.sshAuthType === 1" label="SSH 密码"
        ><el-input
          v-model="form.sshPassword"
          type="password"
          show-password
          :placeholder="savedSshAuthType === 1 ? '********' : form.id ? '留空表示不修改' : ''"
      /></el-form-item>
      <template v-else
        ><el-form-item label="SSH 私钥"
          ><el-input
            v-model="form.sshPrivateKey"
            type="textarea"
            :rows="5"
            :placeholder="
              savedSshAuthType === 2
                ? '********'
                : form.id
                  ? '留空表示不修改'
                  : '粘贴 PEM/OpenSSH 私钥'
            " /></el-form-item
        ><el-form-item label="私钥口令"
          ><el-input v-model="form.sshKeyPassphrase" type="password" show-password /></el-form-item
      ></template>
      <el-divider content-position="left">面板信息（安装后自动补全）</el-divider>
      <el-button
        v-if="form.id"
        v-hasPermi="['xray:server:update']"
        type="primary"
        plain
        :loading="loading"
        class="mb-4"
        @click="syncPanel"
        >从服务器读取并填入</el-button
      >
      <el-alert
        title="读取使用已保存的 SSH 信息。通过后台安装后将自动填写协议、端口、路径及 API Token，并加密保存登录账号密码；面板账号密码通过安装或从服务器读取获取。"
        type="info"
        :closable="false"
        class="mb-4"
      /><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="协议"
            ><el-select v-model="form.panelScheme" style="width: 100%"
              ><el-option label="HTTPS" value="https" /><el-option
                label="HTTP"
                value="http" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="面板端口"
            ><el-input-number
              v-model="form.panelPort"
              controls-position="right"
              style="width: 100%; min-width: 160px"
              :min="1"
              :max="65535" /></el-form-item></el-col
      ></el-row>
      <el-form-item v-if="form.id" label="面板登录账号">
        <div class="w-full">
          <el-button v-hasPermi="['xray:server:update']" @click="showCredentials"
            >查看账号密码</el-button
          >
          <div v-if="credentials">
            <el-input :model-value="credentials.username" readonly class="mt-2" />
            <el-input
              :model-value="credentials.password"
              readonly
              type="password"
              show-password
              class="mt-2"
            />
          </div>
          <div class="text-xs text-gray-500 mt-1"
            >账号密码在安装时加密保存。若在面板中修改，保存的凭据可能失效。</div
          >
        </div>
      </el-form-item>
      <el-form-item label="面板路径"><el-input v-model="form.panelPath" /></el-form-item
      ><el-form-item label="API Token">
        <div class="w-full">
          <el-tag :type="form.panelTokenConfigured ? 'success' : 'warning'" class="mb-2">{{
            form.panelTokenConfigured ? '已保存，可用于面板连接；输入框留空表示保留' : '尚未配置'
          }}</el-tag>
          <el-input
            v-model="form.panelToken"
            type="password"
            show-password
            :placeholder="
              form.panelTokenConfigured ? '********************' : '安装后自动生成，也可手动填写'
            "
          />
          <div class="text-xs text-gray-500 mt-1">已保存的 Token 不显示明文，填写新值可替换。</div>
        </div> </el-form-item
      ><el-form-item label="备注"
        ><el-input v-model="form.remark" type="textarea"
      /></el-form-item> </el-form
    ><template #footer
      ><el-button @click="submit" type="primary" :disabled="loading">确定</el-button
      ><el-button @click="visible = false">取消</el-button></template
    ></Dialog
  >
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/server'
const emit = defineEmits(['success'])
const visible = ref(false),
  loading = ref(false),
  formRef = ref()
const title = ref('')
// Only display the saved credential marker; keep the submitted secret empty until replaced.
const savedSshAuthType = ref<number>()
const credentials = ref<{ username: string; password: string }>()
const showCredentials = async () => {
  const result = await Api.getPanelCredentials(form.value.id)
  if (!result.username || !result.password) {
    useMessage().warning(
      '尚未记录面板账号密码，请尝试从服务器读取；旧版本无凭据文件时需通过 x-ui 菜单重置并记录。'
    )
    return
  }
  credentials.value = result
}
watch(visible, () => {
  credentials.value = undefined
})
const empty = () => ({
  id: undefined,
  name: '',
  host: '',
  sshPort: 22,
  sshUsername: 'root',
  sshAuthType: 1,
  sshPassword: '',
  sshPrivateKey: '',
  sshKeyPassphrase: '',
  panelScheme: 'https',
  panelPort: undefined,
  panelPath: '',
  panelToken: '',
  remark: ''
})
const form = ref<any>(empty())
const rules = {
  name: [{ required: true, message: '请输入服务器名称' }],
  host: [{ required: true, message: '请输入主机或 IP' }],
  sshUsername: [{ required: true, message: '请输入 SSH 用户名' }]
}
const open = async (type: string, id?: number) => {
  visible.value = true
  title.value = type === 'create' ? '新增服务器' : '编辑服务器'
  credentials.value = undefined
  form.value = empty()
  savedSshAuthType.value = undefined
  if (id) {
    loading.value = true
    try {
      form.value = { ...form.value, ...(await Api.getServer(id)) }
      if (form.value.sshCredentialConfigured) savedSshAuthType.value = form.value.sshAuthType
    } finally {
      loading.value = false
    }
  }
}
defineExpose({ open })
const syncPanel = async () => {
  loading.value = true
  try {
    await Api.syncPanelConfig(form.value.id)
    useMessage().success('面板信息已读取并保存')
    emit('success')
  } finally {
    try {
      const latest = await Api.getServer(form.value.id)
      for (const field of ['panelScheme', 'panelPort', 'panelPath', 'panelTokenConfigured'])
        form.value[field] = latest[field]
      form.value.panelToken = ''
    } finally {
      loading.value = false
    }
  }
}
const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    form.value.id ? await Api.updateServer(form.value) : await Api.createServer(form.value)
    useMessage().success('保存成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
