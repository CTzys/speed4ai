<template>
  <ContentWrap>
    <el-alert
      :closable="false"
      type="info"
      title="订阅关联会员用户，订单可空。订阅生效时配置客户端和 SOCKS5 出口；到期、暂停、流量耗尽后撤销授权。流量默认每分钟采集，多客户端共享额度按采集周期核算。"
      class="mb-4"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="订阅"
        ><el-input v-model="query.keyword" placeholder="订阅编号" clearable @keyup.enter="search"
      /></el-form-item>
      <el-form-item label="用户 ID"
        ><el-input-number v-model="query.userId" :min="1" :controls="false" clearable
      /></el-form-item>
      <el-form-item label="订单号"
        ><el-input v-model="query.orderNo" clearable placeholder="来源订单号"
      /></el-form-item>
      <el-form-item label="状态"
        ><el-select v-model="query.status" clearable class="!w-130px"
          ><el-option v-for="(s, i) in Api.statuses" :key="i" :value="i" :label="s" /></el-select
      ></el-form-item>
      <el-form-item label="配置"
        ><el-select v-model="query.syncStatus" clearable class="!w-130px"
          ><el-option
            v-for="(s, i) in Api.syncStatuses"
            :key="i"
            :value="i"
            :label="s" /></el-select
      ></el-form-item>
      <el-form-item><el-checkbox v-model="query.expiring">7 天内到期</el-checkbox></el-form-item>
      <el-form-item
        ><el-button type="primary" @click="search">查询</el-button
        ><el-button @click="reset">重置</el-button></el-form-item
      >
    </el-form>
    <div class="flex gap-2 flex-wrap">
      <el-button v-hasPermi="['subscription:create']" type="primary" @click="formRef.open()"
        >新建订阅</el-button
      >
      <el-button @click="load">刷新</el-button>
      <el-button
        v-hasPermi="['subscription:extend']"
        :disabled="!selected.length"
        @click="actionRef.open('extend', selected)"
        >批量延长</el-button
      >
      <el-button
        v-hasPermi="['subscription:add-traffic']"
        :disabled="!selected.length"
        @click="actionRef.open('add-traffic', selected)"
        >批量增加流量</el-button
      >
      <el-button
        v-hasPermi="['subscription:end']"
        type="danger"
        plain
        :disabled="!selected.length"
        @click="actionRef.open('end', selected)"
        >批量结束</el-button
      >
    </div>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="selected = $event" row-key="id">
      <el-table-column type="selection" width="45" />
      <el-table-column label="订阅 / 用户" min-width="210"
        ><template #default="s"
          ><el-button link type="primary" @click="detailRef.open(s.row.id)">{{
            s.row.number
          }}</el-button
          ><div>{{ s.row.userName }} · {{ s.row.userEmail || s.row.userId }}</div></template
        ></el-table-column
      >
      <el-table-column label="来源订单" min-width="125"
        ><template #default="s">{{
          s.row.orderNo || '后台创建，无订单'
        }}</template></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="s"
          ><el-tag :type="s.row.status === 1 ? 'success' : s.row.status >= 3 ? 'danger' : 'info'">{{
            Api.statuses[s.row.status]
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="有效期" min-width="165"
        ><template #default="s"
          ><div>{{ Api.time(s.row.startTime) }}</div
          ><div>{{ Api.time(s.row.expiryTime) }}</div></template
        ></el-table-column
      >
      <el-table-column label="流量（已用 / 额度）" min-width="170"
        ><template #default="s"
          ><div
            >{{ Api.bytes(s.row.usedBytes) }} /
            {{ s.row.unlimited ? '无限' : Api.bytes(s.row.totalBytes) }}</div
          ><div class="text-gray-500"
            >剩余 {{ s.row.unlimited ? '无限' : Api.bytes(s.row.remainingBytes) }}</div
          ></template
        ></el-table-column
      >
      <el-table-column label="节点" width="70"
        ><template #default="s"
          >{{ s.row.clientCount }} / {{ s.row.nodeLimit }}</template
        ></el-table-column
      >
      <el-table-column label="配置" width="90"
        ><template #default="s"
          ><el-tag
            :type="s.row.syncStatus === 2 ? 'success' : s.row.syncStatus === 3 ? 'danger' : 'info'"
            >{{ Api.syncStatuses[s.row.syncStatus] }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column prop="lastError" label="错误" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="310" fixed="right"
        ><template #default="s">
          <el-button link type="primary" @click="detailRef.open(s.row.id)">详情</el-button>
          <el-button
            v-if="s.row.status !== 5"
            v-hasPermi="['subscription:extend']"
            link
            type="primary"
            @click="actionRef.open('extend', [s.row])"
            >延长</el-button
          >
          <el-button
            v-if="s.row.status !== 5"
            v-hasPermi="['subscription:add-traffic']"
            link
            type="primary"
            @click="actionRef.open('add-traffic', [s.row])"
            >加流量</el-button
          >
          <el-button
            v-if="s.row.status !== 5"
            v-hasPermi="['subscription:reset-traffic']"
            link
            type="primary"
            @click="actionRef.open('reset-traffic', [s.row])"
            >重置流量</el-button
          >
          <el-button
            v-if="s.row.status !== 5"
            v-hasPermi="['subscription:pause']"
            link
            type="primary"
            @click="actionRef.open(s.row.status === 2 ? 'resume' : 'pause', [s.row])"
            >{{ s.row.status === 2 ? '恢复' : '暂停' }}</el-button
          >
          <el-button
            v-if="s.row.status !== 5"
            v-hasPermi="['subscription:end']"
            link
            type="danger"
            @click="actionRef.open('end', [s.row])"
            >结束</el-button
          >
          <el-button
            v-hasPermi="['subscription:reset-link']"
            link
            type="warning"
            @click="actionRef.open('reset-link', [s.row])"
            >重置链接</el-button
          >
          <el-button
            v-hasPermi="['subscription:remark']"
            link
            @click="actionRef.open('remark', [s.row])"
            >备注</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>
  <SubscriptionForm ref="formRef" @success="load" />
  <SubscriptionAction ref="actionRef" @success="load" />
  <SubscriptionDetail ref="detailRef" @success="load" />
</template>
<script setup lang="ts">
import * as Api from '@/api/subscription'
import SubscriptionForm from './SubscriptionForm.vue'
import SubscriptionAction from './SubscriptionAction.vue'
import SubscriptionDetail from './SubscriptionDetail.vue'
defineOptions({ name: 'SubscriptionList' })
const loading = ref(false),
  total = ref(0)
const list = ref<Api.SubscriptionVO[]>([]),
  selected = ref<Api.SubscriptionVO[]>([])
const formRef = ref(),
  actionRef = ref(),
  detailRef = ref()
const defaults = () => ({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  userId: undefined as number | undefined,
  orderNo: '',
  status: undefined as number | undefined,
  syncStatus: undefined as number | undefined,
  expiring: false
})
const query = reactive(defaults())
const load = async () => {
  loading.value = true
  try {
    const r = await Api.getPage(query)
    list.value = r.list
    total.value = r.total
    selected.value = []
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  Object.assign(query, defaults())
  load()
}
onMounted(load)
</script>
