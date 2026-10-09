<template>
  <ContentWrap
    ><el-button type="primary" @click="load()"
      ><Icon icon="ep:refresh" />刷新</el-button
    ></ContentWrap
  ><ContentWrap
    ><el-table v-loading="loading" :data="list"
      ><el-table-column label="任务编号" prop="id" width="100" /><el-table-column
        label="服务器编号"
        prop="serverId"
      /><el-table-column label="版本"
        ><template #default="s">{{ s.row.version || '当前稳定版' }}</template></el-table-column
      ><el-table-column label="状态"
        ><template #default="s"
          ><el-tag :type="types[s.row.status]">{{ texts[s.row.status] }}</el-tag></template
        ></el-table-column
      ><el-table-column label="当前步骤" prop="currentStep" /><el-table-column
        label="开始时间"
        prop="startTime"
        :formatter="dateFormatter"
        width="180"
      /><el-table-column label="错误" prop="errorMessage" show-overflow-tooltip /><el-table-column
        label="操作"
        ><template #default="s"
          ><el-button link type="primary" @click="showLogs(s.row.id)">查看日志</el-button></template
        ></el-table-column
      ></el-table
    ><Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load" /></ContentWrap
  ><Dialog v-model="logVisible" title="安装日志" width="800px"
    ><el-timeline
      ><el-timeline-item
        v-for="item in logs"
        :key="item.id"
        :timestamp="String(item.createTime)"
        :type="item.level === 2 ? 'danger' : 'primary'"
        ><b>{{ item.step }}</b
        ><pre class="log">{{ item.content }}</pre>
      </el-timeline-item></el-timeline
    ></Dialog
  >
</template>
<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { dateFormatter } from '@/utils/formatTime'
import * as Api from '@/api/xray/install'
defineOptions({ name: 'XrayInstall' })
const loading = ref(false),
  list = ref<any[]>([]),
  total = ref(0),
  query = reactive({ pageNo: 1, pageSize: 10 }),
  logVisible = ref(false),
  logs = ref<any[]>([])
const texts = ['等待安装', '安装中', '安装成功', '安装失败']
const types: any[] = ['info', 'warning', 'success', 'danger']
let fetching = false
const load = async (showLoading = true) => {
  if (fetching) return
  fetching = true
  if (showLoading) loading.value = true
  try {
    const r = await Api.getInstallPage(query)
    list.value = r.list
    total.value = r.total
  } finally {
    fetching = false
    loading.value = false
  }
}
const showLogs = async (id: number) => {
  logs.value = await Api.getInstallLogs(id)
  logVisible.value = true
}
const { pause, resume } = useIntervalFn(() => load(false), 3000, { immediate: false })
onMounted(() => {
  load()
  resume()
})
onActivated(() => {
  load()
  resume()
})
onDeactivated(pause)
onUnmounted(pause)
</script>
<style scoped>
.log {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 4px;
}
</style>
