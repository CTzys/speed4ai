<template>
  <Dialog v-model="visible" title="批量导入 SOCKS5 节点" width="900px">
    <el-alert
      title="每行一个连接，最多 500 行。支持 地址:端口:账号:密码 和 socks5://账号:密码@地址:端口#名称；新节点默认下架。"
      type="info"
      :closable="false"
    />
    <div class="flex items-center gap-3 mt-4">
      <span>导入地区</span>
      <el-select
        v-model="regionId"
        @change="clearCity"
        :disabled="loading"
        filterable
        placeholder="为本批节点选择地区"
      >
        <el-option
          v-for="region in regionOptions"
          :key="region.id"
          :value="region.id!"
          :label="region.name"
          :disabled="region.status !== 0"
        />
      </el-select>
      <el-select
        v-model="cityId"
        :disabled="loading || !regionId"
        filterable
        placeholder="选择城市"
      >
        <el-option
          v-for="city in cityOptions"
          :key="city.id"
          :value="city.id!"
          :label="city.name"
          :disabled="city.status !== 0"
        />
      </el-select>
      <el-button link type="primary" :disabled="loading" @click="loadRegions"
        >刷新地区/城市</el-button
      >
    </div>
    <el-input
      v-model="text"
      :disabled="loading"
      class="mt-4"
      type="textarea"
      :rows="9"
      :maxlength="200000"
      placeholder="1.2.3.4:1080:username:password&#10;1.2.3.5:1080:username:password"
      @input="invalidatePreview"
    />
    <div class="my-3"
      ><el-button type="primary" plain :loading="loading" :disabled="!text.trim()" @click="preview"
        >解析预览</el-button
      ><span class="ml-4"
        >有效 {{ count('valid') }} · 重复 {{ count('duplicate') }} · 错误 {{ count('error') }} ·
        已导入 {{ count('imported') }}</span
      ></div
    >
    <el-table :data="rows" max-height="350">
      <el-table-column prop="line" label="行号" width="65" /><el-table-column
        prop="name"
        label="名称"
        min-width="120"
      />
      <el-table-column label="地址" min-width="160"
        ><template #default="s"
          >{{ s.row.host || '—' }}{{ s.row.port ? ':' + s.row.port : '' }}</template
        ></el-table-column
      >
      <el-table-column prop="username" label="账号" width="120" />
      <el-table-column label="状态" width="90"
        ><template #default="s"
          ><el-tag
            :type="
              s.row.status === 'error'
                ? 'danger'
                : s.row.status === 'duplicate'
                  ? 'warning'
                  : 'success'
            "
            >{{ labels[s.row.status] }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column prop="message" label="结果" min-width="220" />
    </el-table>
    <template #footer
      ><el-button @click="visible = false">关闭</el-button
      ><el-button
        type="primary"
        :loading="loading"
        :disabled="!regionId || !cityId || !count('valid') || parsedText !== text"
        @click="submit"
        >导入有效节点</el-button
      ></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/node'
import * as RegionApi from '@/api/xray/region'
import * as CityApi from '@/api/xray/city'
const emit = defineEmits(['success'])
const visible = ref(false),
  loading = ref(false),
  text = ref(''),
  parsedText = ref(''),
  rows = ref<Api.ImportRow[]>([])
const invalidatePreview = () => {
  rows.value = []
  parsedText.value = ''
}
const regionId = ref<number>()
const cityId = ref<number>()
const cities = ref<CityApi.CityVO[]>([])
const cityOptions = computed(() => cities.value.filter((c) => c.regionId === regionId.value))
const clearCity = () => {
  cityId.value = undefined
}
const regionOptions = ref<RegionApi.RegionVO[]>([])
const loadRegions = async () => {
  const [regions, cityRows] = await Promise.all([RegionApi.getList(), CityApi.getList()])
  regionOptions.value = regions
  cities.value = cityRows
}
const labels: Record<string, string> = {
  valid: '有效',
  duplicate: '重复',
  error: '错误',
  imported: '已导入'
}
const count = (status: string) => rows.value.filter((r) => r.status === status).length
const open = async () => {
  regionId.value = undefined
  cityId.value = undefined
  text.value = ''
  parsedText.value = ''
  rows.value = []
  visible.value = true
  await loadRegions()
}
const preview = async () => {
  loading.value = true
  try {
    const source = text.value
    const previewRows = await Api.preview(source)
    if (text.value === source) {
      rows.value = previewRows
      parsedText.value = source
    }
  } finally {
    loading.value = false
  }
}
const submit = async () => {
  if (!regionId.value || !cityId.value) return
  loading.value = true
  try {
    rows.value = await Api.importText(text.value, regionId.value, cityId.value)
    text.value = ''
    parsedText.value = ''
    emit('success')
  } finally {
    loading.value = false
  }
}
watch(visible, (value) => {
  if (!value) {
    text.value = ''
    parsedText.value = ''
    rows.value = []
  }
})
defineExpose({ open })
</script>
