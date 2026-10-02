<template>
  <Dialog v-model="visible" :title="title" width="560px">
    <el-alert
      :closable="false"
      type="info"
      :title="ids.length > 1 ? `本次操作 ${ids.length} 份订阅，逐条执行并报告结果` : number"
      class="mb-4"
    />
    <el-form label-width="110px">
      <template v-if="kind === 'extend'">
        <el-form-item label="延长方式"
          ><el-radio-group v-model="extendMode"
            ><el-radio value="days">增加天数</el-radio
            ><el-radio value="date">指定到期时间</el-radio></el-radio-group
          ></el-form-item
        >
        <el-form-item v-if="extendMode === 'days'" label="延长天数"
          ><el-input-number v-model="days" :min="1" :max="3650"
        /></el-form-item>
        <el-form-item v-else label="新的到期时间"
          ><el-date-picker v-model="expiryTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
        /></el-form-item>
        <div class="text-gray-500 mb-3"
          >未到期从原到期时间延长；已到期从当前时间延长。已结束的订阅不能恢复。</div
        >
      </template>
      <el-form-item v-if="kind === 'add-traffic'" label="增加流量"
        ><el-input-number v-model="quota" :min="0.01" :max="1000000" :precision="2" /><span
          class="ml-2"
          >GiB</span
        ></el-form-item
      >
      <el-alert
        v-if="kind === 'end'"
        :closable="false"
        type="warning"
        title="结束后无法恢复，订阅链接停止提供连接，系统会撤销服务器客户端。"
        class="mb-4"
      />
      <el-alert
        v-if="kind === 'reset-traffic'"
        :closable="false"
        type="warning"
        title="重置当前周期流量，累计历史保留；服务器无法采集流量时会拒绝重置。"
        class="mb-4"
      />
      <el-alert
        v-if="kind === 'pause'"
        :closable="false"
        type="warning"
        title="暂停后禁用客户端，到期时间保持。"
        class="mb-4"
      />
      <el-alert
        v-if="kind === 'reset-link'"
        :closable="false"
        type="warning"
        title="旧订阅链接立即失效，需要重新导入新链接；客户端 UUID 不变。"
        class="mb-4"
      />
      <el-form-item v-if="['extend', 'add-traffic'].includes(kind)" label="关联订单"
        ><el-input
          v-model="orderNo"
          maxlength="64"
          placeholder="可空，记录本次续费 / 加流量的订单号"
      /></el-form-item>
      <el-form-item :label="kind === 'remark' ? '备注' : '操作原因'"
        ><el-input v-model="remark" type="textarea" maxlength="500"
      /></el-form-item>
    </el-form>
    <el-alert v-if="failures.length" type="error" :closable="false" :title="failures.join('；')" />
    <template #footer
      ><el-button @click="visible = false">关闭</el-button
      ><el-button type="primary" :loading="loading" @click="save">确认</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/subscription'
const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false),
  loading = ref(false)
const ids = ref<number[]>([]),
  kind = ref(''),
  number = ref(''),
  remark = ref(''),
  orderNo = ref('')
const days = ref(30),
  quota = ref(10),
  expiryTime = ref(''),
  extendMode = ref('days')
const failures = ref<string[]>([])
const labels = {
  extend: '延长订阅',
  'add-traffic': '增加流量',
  'reset-traffic': '重置流量',
  end: '结束订阅',
  pause: '暂停订阅',
  resume: '恢复订阅',
  'reset-link': '重置订阅链接',
  remark: '编辑备注'
}
const title = computed(() => labels[kind.value] || '订阅操作')
const open = (action: string, rows: Api.SubscriptionVO[]) => {
  ids.value = rows.map((r) => r.id)
  kind.value = action
  number.value = rows[0]?.number || ''
  remark.value = action === 'remark' ? rows[0]?.remark || '' : ''
  orderNo.value = ''
  days.value = 30
  quota.value = 10
  expiryTime.value = ''
  extendMode.value = 'days'
  failures.value = []
  visible.value = true
}
const save = async () => {
  if (kind.value === 'extend' && extendMode.value === 'date' && !expiryTime.value) {
    message.warning('请选择新的到期时间')
    return
  }
  loading.value = true
  failures.value = []
  const failedIds: number[] = []
  let count = 0
  try {
    for (const id of ids.value) {
      const req: Api.ActionReq = {
        id,
        action: kind.value,
        remark: remark.value,
        orderNo: orderNo.value || undefined
      }
      if (kind.value === 'extend') {
        if (extendMode.value === 'days') req.days = days.value
        else req.expiryTime = expiryTime.value
      }
      if (kind.value === 'add-traffic') req.bytes = Math.round(quota.value * Api.GiB)
      try {
        await Api.action(req)
        count++
      } catch {
        failures.value.push(`订阅 ${id} 操作失败，请查看错误提示`)
        failedIds.push(id)
      }
    }
    if (count) {
      message.success(`已完成 ${count} 份订阅的操作`)
      emit('success')
    }
    if (!failedIds.length) visible.value = false
    else ids.value = failedIds
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
