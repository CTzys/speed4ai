<template>
  <ContentWrap
    ><el-form :inline="true"
      ><el-form-item
        ><el-input
          v-model="query.name"
          placeholder="服务器名称"
          clearable
          @keyup.enter="load" /></el-form-item
      ><el-form-item
        ><el-input
          v-model="query.host"
          placeholder="主机/IP"
          clearable
          @keyup.enter="load" /></el-form-item
      ><el-form-item
        ><el-button type="primary" @click="load"><Icon icon="ep:search" />查询</el-button
        ><el-button
          type="primary"
          plain
          @click="formRef.open('create')"
          v-hasPermi="['xray:server:create']"
          ><Icon icon="ep:plus" />新增</el-button
        ></el-form-item
      ></el-form
    ></ContentWrap
  >
  <ContentWrap
    ><el-table v-loading="loading" :data="list"
      ><el-table-column label="名称" prop="name" /><el-table-column label="地址"
        ><template #default="s">{{ s.row.host }}:{{ s.row.sshPort }}</template></el-table-column
      ><el-table-column label="安装状态" width="110"
        ><template #default="s"
          ><el-tag :type="installType(s.row.installStatus)">{{
            installText(s.row.installStatus)
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="运行状态" width="110"
        ><template #default="s"
          ><el-tag :type="healthType(s.row.healthStatus)">{{
            healthText(s.row.healthStatus)
          }}</el-tag></template
        ></el-table-column
      ><el-table-column
        label="最后检测"
        prop="lastCheckTime"
        :formatter="dateFormatter"
        width="180"
      /><el-table-column label="错误" prop="lastError" show-overflow-tooltip /><el-table-column
        label="操作"
        width="730"
        fixed="right"
        ><template #default="s"
          ><el-link
            class="mr-3"
            type="primary"
            :underline="false"
            :href="panelUrl(s.row)"
            :disabled="!panelUrl(s.row)"
            :title="panelUrl(s.row) ? '打开 x-ui 管理控制台' : '请先配置有效的面板地址和端口'"
            target="_blank"
            rel="noopener noreferrer"
            >x-ui 控制台</el-link
          ><el-button
            link
            type="primary"
            v-hasPermi="['xray:inbound:query']"
            @click="router.push({ path: '/xray/inbound', query: { serverId: s.row.id } })"
            >入站</el-button
          ><el-button
            link
            type="success"
            :disabled="!!operatingId || s.row.installStatus === 1"
            v-hasPermi="['xray:server:start']"
            @click="control(s.row, true)"
            >启动</el-button
          >
          <el-button
            link
            type="danger"
            :disabled="!!operatingId || s.row.installStatus === 1"
            v-hasPermi="['xray:server:stop']"
            @click="control(s.row, false)"
            >停止</el-button
          >
          <el-button
            link
            type="primary"
            :disabled="!!operatingId"
            v-hasPermi="['xray:server:test']"
            @click="testPanel(s.row.id)"
            >测试面板</el-button
          >
          <el-button link type="primary" @click="test(s.row.id)" v-hasPermi="['xray:server:test']"
            >测试 SSH</el-button
          ><el-button
            link
            type="primary"
            @click="check(s.row.id)"
            v-hasPermi="['xray:server:check']"
            >检测</el-button
          ><el-button
            link
            type="success"
            @click="install(s.row.id)"
            v-hasPermi="['xray:install:create']"
            >安装</el-button
          ><el-button
            link
            @click="formRef.open('update', s.row.id)"
            v-hasPermi="['xray:server:update']"
            >编辑</el-button
          ><el-button
            link
            type="danger"
            @click="remove(s.row.id)"
            v-hasPermi="['xray:server:delete']"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load" /></ContentWrap
  ><ServerForm ref="formRef" @success="load" />
</template>
<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import * as Api from '@/api/xray/server'
import * as InstallApi from '@/api/xray/install'
import ServerForm from './ServerForm.vue'
defineOptions({ name: 'XrayServer' })
const router = useRouter()
const msg = useMessage(),
  loading = ref(false),
  list = ref<any[]>([]),
  total = ref(0),
  formRef = ref()
const operatingId = ref<number>()
const query = reactive({ pageNo: 1, pageSize: 10, name: '', host: '' })
const load = async () => {
  loading.value = true
  try {
    const r = await Api.getServerPage(query)
    list.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const installText = (v: number) => ['未安装', '安装中', '已安装', '安装失败'][v] || '未知'
const installType = (v: number): any => ['info', 'warning', 'success', 'danger'][v] || 'info'
const healthText = (v: number) => ['未检测', '正常', '服务停止', '不可达'][v] || '未知'
const healthType = (v: number): any => ['info', 'success', 'warning', 'danger'][v] || 'info'
const panelUrl = (server: Api.XrayServerVO): string | undefined => {
  const scheme = server.panelScheme
  const port = server.panelPort
  const host = server.host?.trim().replace(/^\[|\]$/g, '')
  const path = (server.panelPath ?? '').trim().replace(/^\/+|\/+$/g, '')
  if (
    !scheme ||
    !['http', 'https'].includes(scheme) ||
    !port ||
    !Number.isInteger(port) ||
    port < 1 ||
    port > 65535 ||
    !host ||
    /[\s/@?#\\]/.test(host) ||
    path.includes('..') ||
    /[?#\\]/.test(path)
  )
    return undefined
  try {
    const authority = host.includes(':') ? `[${host}]` : host
    const url = new URL(`${scheme}://${authority}:${port}/${path ? path + '/' : ''}`)
    if (!url.hostname || url.username || url.password) return undefined
    return url.href
  } catch {
    return undefined
  }
}
const test = async (id: number) => {
  await Api.testSsh(id)
  msg.success('SSH 连接正常')
}
const check = async (id: number) => {
  await Api.checkHealth(id)
  msg.success('检测完成')
  await load()
}
const control = async (server: Api.XrayServerVO, start: boolean) => {
  if (!server.id || operatingId.value) return
  if (!start)
    await msg.confirm(
      `确定停止 ${server.name} 的 x-ui 服务？面板和该服务管理的 Xray 入站连接将中断。`
    )
  operatingId.value = server.id
  try {
    start ? await Api.startService(server.id) : await Api.stopService(server.id)
    msg.success(start ? '服务已启动' : '服务已停止')
    await load()
  } finally {
    operatingId.value = undefined
  }
}
const testPanel = async (id: number) => {
  operatingId.value = id
  try {
    await Api.testPanel(id)
    msg.success('面板 API 连接正常，Token 验证通过')
  } finally {
    operatingId.value = undefined
  }
}
const install = async (id: number) => {
  await msg.confirm('将通过 SSH 在该服务器安装 3x-ui，是否继续？')
  await InstallApi.createInstall({ serverId: id })
  msg.success('安装任务已创建')
  await load()
}
const remove = async (id: number) => {
  await msg.delConfirm()
  await Api.deleteServer(id)
  msg.success('删除成功')
  await load()
}
onMounted(load)
</script>
