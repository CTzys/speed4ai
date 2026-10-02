<template>
  <ContentWrap>
    <el-form :inline="true" @submit.prevent>
      <el-form-item
        ><el-input v-model="keyword" placeholder="搜索地区或城市名称" clearable
      /></el-form-item>
      <el-form-item
        ><el-button @click="load">刷新</el-button
        ><el-button type="primary" v-hasPermi="['xray:region:create']" @click="open()"
          >新增地区</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert
      class="mb-4"
      title="按“地区 → 城市”维护，例如美国 → 纽约。展开地区可管理城市；停用后保留已有节点关联。"
      type="info"
      :closable="false"
    />
    <el-table v-loading="loading" :data="filtered" row-key="id">
      <el-table-column type="expand">
        <template #default="s">
          <div class="mx-8 my-4">
            <div class="flex justify-between items-center mb-3"
              ><strong>{{ s.row.name }}的城市</strong
              ><el-button
                type="primary"
                plain
                v-hasPermi="['xray:region:create']"
                @click="openCity(s.row.id)"
                >新增城市</el-button
              ></div
            >
            <el-table :data="citiesFor(s.row.id)" border empty-text="暂无城市，请先新增城市">
              <el-table-column prop="name" label="城市名称" /><el-table-column
                prop="sort"
                label="排序"
                width="120"
              />
              <el-table-column label="状态" width="120"
                ><template #default="c"
                  ><el-tag :type="c.row.status === 0 ? 'success' : 'info'">{{
                    c.row.status === 0 ? '启用' : '停用'
                  }}</el-tag></template
                ></el-table-column
              >
              <el-table-column label="操作" width="180"
                ><template #default="c"
                  ><el-button
                    link
                    type="primary"
                    v-hasPermi="['xray:region:update']"
                    @click="openCity(s.row.id, c.row)"
                    >编辑</el-button
                  ><el-button
                    link
                    :type="c.row.status === 0 ? 'warning' : 'success'"
                    v-hasPermi="['xray:region:update']"
                    :disabled="saving"
                    @click="toggleCity(c.row)"
                    >{{ c.row.status === 0 ? '停用' : '启用' }}</el-button
                  ></template
                ></el-table-column
              >
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="地区名称" />
      <el-table-column label="城市数" width="120"
        ><template #default="s">{{ citiesFor(s.row.id).length }}</template></el-table-column
      >
      <el-table-column prop="sort" label="排序" width="120" />
      <el-table-column label="状态" width="120"
        ><template #default="s"
          ><el-tag :type="s.row.status === 0 ? 'success' : 'info'">{{
            s.row.status === 0 ? '启用' : '停用'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="操作" width="260"
        ><template #default="s"
          ><el-button
            link
            type="primary"
            v-hasPermi="['xray:region:create']"
            @click="openCity(s.row.id)"
            >新增城市</el-button
          ><el-button link type="primary" v-hasPermi="['xray:region:update']" @click="open(s.row)"
            >编辑地区</el-button
          ><el-button
            link
            :type="s.row.status === 0 ? 'warning' : 'success'"
            v-hasPermi="['xray:region:update']"
            :disabled="saving"
            @click="toggle(s.row)"
            >{{ s.row.status === 0 ? '停用' : '启用' }}</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>
  <Dialog v-model="visible" :title="form.id ? '编辑地区' : '新增地区'" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="地区名称" prop="name"
        ><el-input v-model="form.name" maxlength="64" placeholder="例如：美国、日本"
      /></el-form-item>
      <el-form-item label="排序"
        ><el-input-number v-model="form.sort" :min="0" :max="99999" /><div
          class="ml-2 text-gray-500"
          >数字越小越靠前</div
        ></el-form-item
      >
      <el-form-item label="状态"
        ><el-radio-group v-model="form.status"
          ><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group
        ></el-form-item
      >
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </Dialog>
  <Dialog v-model="cityVisible" :title="cityForm.id ? '编辑城市' : '新增城市'" width="480px">
    <el-form ref="cityFormRef" :model="cityForm" :rules="cityRules" label-width="90px">
      <el-form-item label="所属地区">{{
        list.find((r) => r.id === cityForm.regionId)?.name
      }}</el-form-item>
      <el-form-item label="城市名称" prop="name"
        ><el-input v-model="cityForm.name" maxlength="64" placeholder="例如：纽约、洛杉矶、东京"
      /></el-form-item>
      <el-form-item label="排序"
        ><el-input-number v-model="cityForm.sort" :min="0" :max="99999"
      /></el-form-item>
      <el-form-item label="状态"
        ><el-radio-group v-model="cityForm.status"
          ><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group
        ></el-form-item
      >
    </el-form>
    <template #footer
      ><el-button @click="cityVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveCity">保存</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/xray/region'
import * as CityApi from '@/api/xray/city'
defineOptions({ name: 'XrayRegion' })
const msg = useMessage()
const loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  keyword = ref(''),
  list = ref<Api.RegionVO[]>([]),
  cities = ref<CityApi.CityVO[]>([]),
  formRef = ref(),
  cityFormRef = ref(),
  cityVisible = ref(false)
const form = ref<Api.RegionVO>({ name: '', sort: 0, status: 0 })
const cityForm = ref<CityApi.CityVO>({ regionId: 0, name: '', sort: 0, status: 0 })
const citiesFor = (regionId: number) => cities.value.filter((c) => c.regionId === regionId)
const filtered = computed(() =>
  list.value.filter(
    (r) =>
      r.name.includes(keyword.value.trim()) ||
      citiesFor(r.id!).some((c) => c.name.includes(keyword.value.trim()))
  )
)
const rules = {
  name: [{ required: true, whitespace: true, message: '请输入地区名称', trigger: 'blur' }]
}
const cityRules = {
  name: [{ required: true, whitespace: true, message: '请输入城市名称', trigger: 'blur' }]
}
const load = async () => {
  loading.value = true
  try {
    const [regions, cityRows] = await Promise.all([Api.getList(), CityApi.getList()])
    list.value = regions
    cities.value = cityRows
  } finally {
    loading.value = false
  }
}
const open = async (row?: Api.RegionVO) => {
  form.value = row ? { ...row } : { name: '', sort: 0, status: 0 }
  visible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
const openCity = async (regionId: number, row?: CityApi.CityVO) => {
  cityForm.value = row ? { ...row } : { regionId, name: '', sort: 0, status: 0 }
  cityVisible.value = true
  await nextTick()
  cityFormRef.value?.clearValidate()
}
const save = async () => {
  if (!(await formRef.value.validate())) return
  saving.value = true
  try {
    form.value.id ? await Api.update(form.value) : await Api.create(form.value)
    visible.value = false
    msg.success('保存成功')
    await load()
  } finally {
    saving.value = false
  }
}
const saveCity = async () => {
  if (!(await cityFormRef.value.validate())) return
  saving.value = true
  try {
    cityForm.value.id ? await CityApi.update(cityForm.value) : await CityApi.create(cityForm.value)
    cityVisible.value = false
    msg.success('保存成功')
    await load()
  } finally {
    saving.value = false
  }
}
const toggle = async (row: Api.RegionVO) => {
  await msg.confirm(
    row.status === 0
      ? '停用地区后，该地区及其城市不能用于新增节点，已有节点关联保留，是否继续？'
      : '确定启用该地区？'
  )
  saving.value = true
  try {
    await Api.update({ ...row, status: row.status === 0 ? 1 : 0 })
    msg.success('更新成功')
    await load()
  } finally {
    saving.value = false
  }
}
const toggleCity = async (row: CityApi.CityVO) => {
  await msg.confirm(
    row.status === 0
      ? '停用城市后不能用于新增节点，已有节点关联保留，是否继续？'
      : '确定启用该城市？'
  )
  saving.value = true
  try {
    await CityApi.update({ ...row, status: row.status === 0 ? 1 : 0 })
    msg.success('更新成功')
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
onActivated(load)
</script>
