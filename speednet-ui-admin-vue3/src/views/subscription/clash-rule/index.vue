<template>
  <ContentWrap>
    <el-alert
      title="用户专用规则完整替代通用规则；删除专用规则后恢复通用规则。保存后，客户更新 Clash 订阅即可生效。"
      type="info"
      :closable="false"
      class="mb-4"
    />
    <el-form label-width="110px" v-loading="loading">
      <el-form-item label="配置范围">
        <el-radio-group v-model="scope" :disabled="saving" @change="changeScope">
          <el-radio-button value="common">通用规则</el-radio-button>
          <el-radio-button value="user">指定用户</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="scope === 'user'" label="客户">
        <el-select
          v-model="userId"
          filterable
          remote
          :remote-method="findUsers"
          :loading="searching"
          :disabled="saving"
          placeholder="搜索昵称、邮箱或用户 ID"
          class="!w-100"
          @change="load"
        >
          <el-option
            v-for="user in users"
            :key="user.id"
            :value="user.id"
            :label="`${user.nickname} · ${user.email} (${user.id})`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="当前规则来源"
        ><el-tag>{{
          custom
            ? scope === 'common'
              ? '已保存的通用规则'
              : '用户专用规则'
            : scope === 'common'
              ? '系统默认规则'
              : '继承通用规则'
        }}</el-tag></el-form-item
      >
      <el-form-item label="路由规则">
        <el-input
          v-model="text"
          type="textarea"
          :rows="16"
          :maxlength="1000000"
          :disabled="saving || (scope === 'user' && !userId)"
          placeholder="每行一条，例如 DOMAIN-SUFFIX,example.com,DIRECT"
        />
      </el-form-item>
      <el-form-item>
        <span
          >按行顺序匹配，支持策略 DIRECT、REJECT、SpeedNet。MATCH 必须在最后；省略时自动追加
          MATCH,SpeedNet。</span
        >
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="scope === 'user' && !userId"
          @click="save"
          >{{ scope === 'common' ? '保存通用规则' : '保存用户专用规则' }}</el-button
        >
        <el-button :disabled="saving || (scope === 'user' && !userId)" @click="load"
          >重新加载</el-button
        >
        <el-button
          v-if="scope === 'user' && custom"
          type="danger"
          plain
          :disabled="saving"
          @click="remove"
          >删除专用规则，恢复通用规则</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <h3>已配置专用规则的客户</h3>
    <el-table :data="overrides">
      <el-table-column prop="userId" label="客户 ID" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button link type="primary" :disabled="saving" @click="edit(row)"
            >编辑规则</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>
</template>
<script setup lang="ts">
import * as Rules from '@/api/subscription/clash-rule'
defineOptions({ name: 'SubscriptionClashRule' })
const message = useMessage()
const scope = ref('common'),
  userId = ref<number>(),
  text = ref(''),
  custom = ref(false)
const loading = ref(false),
  saving = ref(false),
  searching = ref(false)
const users = ref<Rules.RuleUser[]>([]),
  overrides = ref<Rules.RuleOverride[]>([])
let loadSequence = 0,
  searchSequence = 0
async function load() {
  const sequence = ++loadSequence
  text.value = ''
  custom.value = false
  if (scope.value === 'user' && !userId.value) return
  loading.value = true
  try {
    const config = await Rules.getRules(scope.value === 'common' ? 0 : userId.value!)
    if (sequence === loadSequence) {
      text.value = config.rules.join('\n')
      custom.value = config.custom
    }
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
async function changeScope() {
  userId.value = undefined
  await load()
  if (scope.value === 'user') await findUsers('')
}
async function findUsers(keyword: string) {
  const sequence = ++searchSequence
  searching.value = true
  try {
    const result = await Rules.searchUsers(keyword)
    if (sequence === searchSequence) users.value = result
  } finally {
    if (sequence === searchSequence) searching.value = false
  }
}
async function save() {
  saving.value = true
  try {
    const lines = text.value
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
    if (!lines.length) {
      message.warning('请填写规则')
      return
    }
    await Rules.saveRules(scope.value === 'common' ? 0 : userId.value!, lines)
    message.success('规则已保存，客户更新订阅后生效')
    await load()
    overrides.value = await Rules.listOverrides()
  } finally {
    saving.value = false
  }
}
async function remove() {
  await message.confirm('删除后，该客户将使用通用规则，是否继续？')
  saving.value = true
  try {
    await Rules.deleteRules(userId.value!)
    message.success('已恢复通用规则')
    await load()
    overrides.value = await Rules.listOverrides()
  } finally {
    saving.value = false
  }
}
async function edit(row: Rules.RuleOverride) {
  scope.value = 'user'
  userId.value = row.userId
  users.value = [{ id: row.userId, nickname: row.nickname, email: row.email }]
  await load()
}
onMounted(async () => {
  await load()
  overrides.value = await Rules.listOverrides()
})
</script>
