<template>
  <ContentWrap
    ><el-button class="mb-4" @click="load">刷新订单</el-button
    ><el-table v-loading="loading" :data="orders"
      ><el-table-column prop="number" label="订单号" min-width="230" /><el-table-column
        prop="user_id"
        label="会员 ID"
      /><el-table-column prop="plan_name" label="套餐" /><el-table-column
        prop="kind"
        label="类型"
      /><el-table-column label="原价 / 折抵 / 应付" min-width="180"
        ><template #default="s"
          >{{ money(s.row.original_amount) }} / {{ money(s.row.credit_amount) }} /
          {{ money(s.row.amount) }}</template
        ></el-table-column
      ><el-table-column label="状态"
        ><template #default="s">{{ statuses[s.row.status] }}</template></el-table-column
      ><el-table-column prop="error" label="处理结果" min-width="200" /><el-table-column
        label="操作"
        ><template #default="s"
          ><el-button link @click="retry(s.row)">核验 / 重试</el-button></template
        ></el-table-column
      ></el-table
    ></ContentWrap
  >
</template>
<script setup lang="ts">
import request from '@/config/axios'
interface Order {
  id: number
  user_id: number
  number: string
  plan_name: string
  kind: string
  status: string
  error: string
  original_amount: number
  credit_amount: number
  amount: number
}
const orders = ref<Order[]>([]),
  loading = ref(false),
  message = useMessage()
const statuses: Record<string, string> = {
  pending: '待支付',
  paid: '权益处理中',
  completed: '已发放',
  failed: '已支付 · 待处理',
  cancelled: '取消 / 过期'
}
const money = (n: number) => `¥${(n / 100).toFixed(2)}`
async function load() {
  loading.value = true
  try {
    orders.value = await request.get({ url: '/subscription/plan/orders' })
  } finally {
    loading.value = false
  }
}
async function retry(o: Order) {
  await request.post({ url: '/subscription/plan/retry', params: { id: o.id, userId: o.user_id } })
  message.success('已核验支付状态')
  await load()
}
onMounted(load)
</script>
