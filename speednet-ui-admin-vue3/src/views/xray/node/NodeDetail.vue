<template>
  <el-drawer v-model="visible" :title="detail?.node?.name || '节点详情'" size="850px">
    <div v-loading="loading" v-if="detail">
      <el-button class="mb-4" @click="load">刷新详情</el-button>
      <el-tabs>
        <el-tab-pane label="基本信息">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="地址"
              >{{ detail.node.host }}:{{ detail.node.port }}</el-descriptions-item
            >
            <el-descriptions-item label="认证">{{
              detail.node.authType ? detail.node.username + ' / ******' : '无认证'
            }}</el-descriptions-item>
            <el-descriptions-item label="地区">{{ detail.node.region || '—' }}</el-descriptions-item
            ><el-descriptions-item label="城市">{{
              detail.node.city || '未分配'
            }}</el-descriptions-item>
            <el-descriptions-item label="标签">{{ detail.node.tags || '—' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{
              detail.node.shelfStatus ? '已上架' : '已下架'
            }}</el-descriptions-item
            ><el-descriptions-item label="配置版本">{{
              detail.node.configVersion
            }}</el-descriptions-item>
            <el-descriptions-item label="检测">{{
              ['未检测', '正常', '异常'][detail.node.healthStatus]
            }}</el-descriptions-item
            ><el-descriptions-item label="最后检测">{{
              time(detail.node.lastCheckTime)
            }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{
              detail.node.remark || '—'
            }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="部署服务器">
          <el-alert
            class="mb-4"
            title="配置已核对表示 SOCKS5 出站已保存并回读匹配。用户路由由订阅管理配置；此状态不表示端到端运行验证成功。"
            type="info"
            :closable="false"
          />
          <el-table :data="detail.servers">
            <el-table-column prop="serverName" label="服务器" /><el-table-column
              prop="outboundTag"
              label="出站标识"
              min-width="180"
            />
            <el-table-column label="状态" width="140"
              ><template #default="s">{{ deployLabels[s.row.status] }}</template></el-table-column
            >
            <el-table-column prop="appliedVersion" label="生效版本" width="90" />
            <el-table-column label="最近同步" width="170"
              ><template #default="s">{{ time(s.row.lastSyncTime) }}</template></el-table-column
            >
            <el-table-column prop="lastError" label="错误" show-overflow-tooltip />
            <el-table-column label="操作" width="150"
              ><template #default="s"
                ><el-button
                  link
                  type="primary"
                  v-hasPermi="['xray:node:deploy']"
                  @click="emit('operation', detail.node.id, s.row.serverId, 'verify')"
                  >核对</el-button
                ><el-button
                  link
                  type="danger"
                  v-hasPermi="['xray:node:deploy']"
                  :disabled="s.row.status === 5"
                  @click="emit('operation', detail.node.id, s.row.serverId, 'remove')"
                  >移除</el-button
                ></template
              ></el-table-column
            >
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="用户领用">
          <el-alert
            v-if="!detail.subscriptionIntegrated"
            class="mb-4"
            title="订阅模块尚未接入，暂不能产生真实领用记录、到期撤销或实时在线人数。"
            type="info"
            :closable="false"
          />
          <el-table :data="detail.users" empty-text="暂无领用记录">
            <el-table-column prop="userId" label="用户 ID" width="90" /><el-table-column
              prop="nickname"
              label="昵称"
            /><el-table-column prop="email" label="邮箱" />
            <el-table-column prop="subscriptionId" label="订阅 ID" /><el-table-column
              prop="serverId"
              label="服务器 ID"
            />
            <el-table-column label="到期时间" width="170"
              ><template #default="s">{{ time(s.row.expiryTime) }}</template></el-table-column
            >
            <el-table-column label="订阅状态"
              ><template #default="s">{{
                s.row.released
                  ? '已释放'
                  : ['待生效', '生效中', '暂停', '流量耗尽', '已到期', '已结束'][
                      s.row.businessStatus
                    ] || '未生效'
              }}</template></el-table-column
            >
            <el-table-column label="授权状态"
              ><template #default="s">{{
                ['待开通', '已生效', '待撤销', '已撤销', '撤销失败'][s.row.authorizationStatus]
              }}</template></el-table-column
            >
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="检测与操作记录">
          <h4>最近 50 次检测</h4>
          <el-table :data="detail.checks"
            ><el-table-column label="时间" width="170"
              ><template #default="s">{{ time(s.row.createTime) }}</template></el-table-column
            ><el-table-column prop="source" label="来源" /><el-table-column label="结果" width="75"
              ><template #default="s">{{
                s.row.status === 1 ? '正常' : '异常'
              }}</template></el-table-column
            ><el-table-column prop="latencyMs" label="耗时 ms" width="90" /><el-table-column
              prop="message"
              label="说明"
              min-width="220"
          /></el-table>
          <h4 class="mt-6">最近 50 次操作</h4>
          <el-table :data="detail.tasks"
            ><el-table-column label="时间" width="170"
              ><template #default="s">{{ time(s.row.createTime) }}</template></el-table-column
            ><el-table-column label="操作" width="90"
              ><template #default="s">{{
                actionLabels[s.row.action] || s.row.action
              }}</template></el-table-column
            ><el-table-column label="状态" width="90"
              ><template #default="s">{{
                ['等待', '执行中', '成功', '失败'][s.row.status]
              }}</template></el-table-column
            ><el-table-column prop="message" label="说明"
          /></el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/node'
import dayjs from 'dayjs'
const emit = defineEmits(['operation'])
const visible = ref(false),
  loading = ref(false),
  detail = ref<any>(),
  nodeId = ref<number>()
const deployLabels = ['待同步', '同步中', '配置已核对', '同步失败', '远端缺失或不一致', '已移除']
const actionLabels: Record<string, string> = {
  check: '检测',
  deploy: '部署',
  verify: '核对',
  remove: '移除'
}
const time = (value: any) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—')
const load = async () => {
  if (!nodeId.value) return
  loading.value = true
  try {
    detail.value = await Api.getDetail(nodeId.value)
  } finally {
    loading.value = false
  }
}
const open = async (id: number) => {
  nodeId.value = id
  detail.value = undefined
  visible.value = true
  await load()
}
defineExpose({ open })
</script>
