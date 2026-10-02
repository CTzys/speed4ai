<template>
  <Dialog v-model="visible" title="新建订阅" width="1100px">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="24">
        <el-col :span="12"
          ><el-form-item label="关联用户" prop="userId">
            <el-select
              v-model="form.userId"
              filterable
              remote
              :remote-method="searchUsers"
              :loading="userLoading"
              placeholder="搜索会员邮箱或昵称"
              class="w-full"
            >
              <el-option
                v-for="u in users"
                :key="u.id"
                :value="u.id"
                :label="`${u.nickname || '用户'} · ${u.email || u.id}`"
              />
            </el-select> </el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="关联订单"
            ><el-input
              v-model="form.orderNo"
              maxlength="64"
              placeholder="可空；填写来源订单号，暂不校验订单模块" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="节点数量上限"
            ><el-input-number v-model="form.nodeLimit" :min="1" :max="20" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="开始时间" prop="startTime"
            ><el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="到期时间" prop="expiryTime"
            ><el-date-picker
              v-model="form.expiryTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="流量额度"
            ><el-switch v-model="form.unlimited" active-text="无限流量" /><el-input-number
              v-if="!form.unlimited"
              v-model="quotaGiB"
              :min="0.01"
              :max="1000000"
              :precision="2"
              class="ml-3"
            /><span v-if="!form.unlimited" class="ml-2">GiB</span></el-form-item
          ></el-col
        >
        <el-col :span="12"
          ><el-form-item label="流量计费"
            ><el-radio-group v-model="form.trafficMode"
              ><el-radio value="both">上传＋下载</el-radio
              ><el-radio value="download">仅下载</el-radio></el-radio-group
            ></el-form-item
          ></el-col
        >
        <el-col :span="12"
          ><el-form-item label="流量重置"
            ><el-select v-model="form.resetMode"
              ><el-option label="不自动重置" value="none" /><el-option
                label="每月（按开始日）"
                value="monthly" /><el-option label="固定天数" value="interval" /></el-select
            ><el-input-number
              v-if="form.resetMode === 'interval'"
              v-model="form.resetIntervalDays"
              :min="1"
              :max="365"
              class="mt-2" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="允许地区"
            ><el-select
              v-model="form.regionId"
              clearable
              @change="clearGeography"
              placeholder="不限地区"
              ><el-option
                v-for="r in options.regions.filter((r) => r.status === 0)"
                :key="r.id"
                :label="r.name"
                :value="r.id" /></el-select></el-form-item
          ><el-form-item label="允许城市"
            ><el-select
              v-model="form.cityId"
              :disabled="!form.regionId"
              clearable
              @change="clearAssignments"
              placeholder="不限城市"
              ><el-option
                v-for="c in options.cities.filter(
                  (c) => c.regionId === form.regionId && c.status === 0
                )"
                :key="c.id"
                :label="c.name"
                :value="c.id" /></el-select></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="备注"
        ><el-input v-model="form.remark" type="textarea" maxlength="500"
      /></el-form-item>
      <el-divider content-position="left">出口节点与客户端</el-divider>
      <AssignmentFields
        v-model="form.assignments"
        :options="options"
        :region-id="form.regionId"
        :city-id="form.cityId"
        removable
      />
      <el-button
        class="mt-3"
        :disabled="form.assignments.length >= form.nodeLimit"
        @click="form.assignments.push({ publicHost: '' })"
        >增加节点</el-button
      >
      <el-alert
        class="mt-3"
        :closable="false"
        type="info"
        title="保存后自动创建客户端认证信息；订阅生效时检测出口并配置服务器。未来开始的订阅等待开始时间再开通。"
      />
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="loading" @click="save">创建订阅</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import dayjs from 'dayjs'
import * as Api from '@/api/subscription'
import AssignmentFields from './AssignmentFields.vue'
const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false),
  loading = ref(false),
  userLoading = ref(false)
const formRef = ref()
const quotaGiB = ref(100)
const options = ref<Api.Options>({ nodes: [], servers: [], regions: [], cities: [] })
const users = ref<Awaited<ReturnType<typeof Api.getUsers>>>([])
const defaults = (): Api.CreateReq => ({
  userId: undefined,
  orderNo: '',
  startTime: dayjs().format('YYYY-MM-DD HH:mm:ss'),
  expiryTime: dayjs().add(30, 'day').format('YYYY-MM-DD HH:mm:ss'),
  unlimited: false,
  totalBytes: 0,
  trafficMode: 'both',
  resetMode: 'none',
  resetIntervalDays: 30,
  nodeLimit: 1,
  assignments: [{ publicHost: '' }]
})
const form = ref<Api.CreateReq>(defaults())
const rules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  expiryTime: [{ required: true, message: '请选择到期时间', trigger: 'change' }]
}
const searchUsers = async (keyword: string) => {
  userLoading.value = true
  try {
    users.value = await Api.getUsers(keyword)
  } finally {
    userLoading.value = false
  }
}
const clearAssignments = () => {
  form.value.assignments.forEach((a) => {
    a.nodeId = undefined
  })
}
const clearGeography = () => {
  form.value.cityId = undefined
  clearAssignments()
}
const open = async () => {
  form.value = defaults()
  quotaGiB.value = 100
  visible.value = true
  loading.value = true
  try {
    options.value = await Api.getOptions()
    await searchUsers('')
    formRef.value?.clearValidate()
  } finally {
    loading.value = false
  }
}
const save = async () => {
  await formRef.value.validate()
  if (
    !form.value.assignments.length ||
    form.value.assignments.some(
      (a) => !a.nodeId || !a.serverId || !a.inboundId || !a.publicHost.trim()
    )
  ) {
    message.warning('请完整选择至少一个出口节点、服务器、入站和连接地址')
    return
  }
  form.value.totalBytes = form.value.unlimited ? 0 : Math.round(quotaGiB.value * Api.GiB)
  loading.value = true
  try {
    await Api.create(form.value)
    message.success('订阅已创建，服务器配置任务已排队')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
