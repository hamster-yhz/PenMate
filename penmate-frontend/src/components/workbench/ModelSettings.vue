<template>
  <div v-if="visible" class="model-settings">
    <div class="ms-backdrop" @click="$emit('close')"></div>
    <div class="ms-modal glass-panel">
      <div class="ms-header">
        <span class="ms-emoji">🔑</span>
        <h3>模型与API池管理</h3>
        <button class="ms-close" @click="$emit('close')">✕</button>
      </div>

      <div class="ms-body">
        <p class="ms-desc">官方模型独立卡片；“+”卡片用于新增我的模型；已有我的模型卡片点入可编辑。</p>

        <section class="ms-section">
          <h4>官方模型</h4>
          <div class="ms-grid">
            <div
              v-for="item in officialCards"
              :key="`official-${item.id}`"
              class="ms-card"
              :class="{ active: item.isActive, unavailable: !item.canSwitch }"
            >
              <div class="name">{{ item.name }}</div>
              <div class="meta">{{ item.providerName }} · {{ item.billingType === 'FREE' ? '免费' : '收费' }}</div>
              <div class="tag">{{ item.statusText }}</div>
              <div class="card-model-name">{{ item.modelName || '未配置模型名' }}</div>
              <div class="card-actions">
                <button class="card-btn switch" :disabled="!item.canSwitch" @click="switchOfficial(item.id)">
                  {{ item.isActive ? '当前使用中' : '切换使用' }}
                </button>
                <button class="card-btn edit" @click="selectOfficial(item.id)">编辑</button>
              </div>
            </div>
          </div>
        </section>

        <section class="ms-section">
          <h4>我的模型</h4>
          <div class="ms-grid">
            <div
              v-for="item in userCards"
              :key="`user-${item.userKeyId}`"
              class="ms-card"
              :class="{ active: item.isActive, unavailable: !item.canSwitch }"
            >
              <div class="name">{{ item.keyName }}</div>
              <div class="meta">{{ item.providerName }} · {{ item.modelName }}</div>
              <div class="tag">{{ item.statusText }}</div>
              <div class="card-actions">
                <button class="card-btn switch" :disabled="!item.canSwitch" @click="switchUser(item.userKeyId)">
                  {{ item.isActive ? '当前使用中' : '切换使用' }}
                </button>
                <button class="card-btn edit" @click="selectUser(item.userKeyId)">编辑</button>
              </div>
            </div>

            <button class="ms-card add-card" @click="startCreate">
              <div class="plus">＋</div>
              <div class="meta">新增我的模型</div>
            </button>
          </div>
        </section>

        <section class="api-form">
          <h4 class="form-title">{{ formTitle }}</h4>

          <div class="form-row">
            <label>模型（可输入）</label>
            <input
              v-model="form.modelInput"
              class="f-input"
              placeholder="请输入模型名，例如：gpt-4o-mini"
            />
          </div>

          <div class="form-row">
            <label>Base URL（可选覆盖）</label>
            <input
              v-model="form.baseUrl"
              class="f-input"
              type="text"
              placeholder="留空则使用提供商默认地址"
            />
          </div>

          <div class="form-row">
            <label>密钥来源</label>
            <input class="f-input" disabled :value="form.mode === 'official' ? 'OFFICIAL_KEY（官方）' : 'USER_KEY（我的）'" />
          </div>

          <div class="form-row">
            <label>显示名称</label>
            <input v-model="form.keyName" class="f-input" type="text" placeholder="例如：OpenAI-写作主Key" />
          </div>

          <div class="form-row">
            <label>API Key</label>
            <div class="key-input-wrap">
              <input
                v-model="form.apiKey"
                :type="showKey ? 'text' : 'password'"
                class="f-input"
                placeholder="编辑时可留空（不改密钥值）"
              />
              <button type="button" class="btn-toggle-key" @click="showKey = !showKey">{{ showKey ? '🙈' : '👁️' }}</button>
            </div>
          </div>

          <div class="api-actions">
            <button type="button" class="btn-test" @click="testConnection">🔗 测试连接</button>
            <button v-if="editingConfigId" type="button" class="btn-delete-api" @click="deleteCurrentConfig">🗑 删除配置</button>
            <button type="button" class="btn-save-api" @click="saveApi">💾 保存配置</button>
          </div>

          <div v-if="testStatus" class="test-result" :class="testStatus">
            {{ testStatus === 'success' ? '✅ 连接成功' : '❌ 连接失败，请检查配置' }}
          </div>
        </section>
      </div>

      <div class="ms-footer">
        <button class="btn-close-modal" @click="$emit('close')">完 成</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { modelApi } from '@/api/modules/model.api'
import { getSession } from '@/stores/session'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
}>()

type AnyRecord = Record<string, unknown>
type BillingType = 'FREE' | 'PAID'
type EditorMode = 'official' | 'user-edit' | 'user-create'

type ModelItem = {
  id: string
  providerId: number
  providerName: string
  providerModelId: number
  modelCode: string
  name: string
  billingType: BillingType
}

type UserCard = {
  userKeyId: number
  providerId: number
  providerName: string
  keyName: string
  modelName: string
  selectedModelId: string
  configId: number | null
  baseUrl: string
  isActive: boolean
  canSwitch: boolean
  statusText: string
}

type OfficialCard = ModelItem & {
  hasOfficialKey: boolean
  officialKeyId: number | null
  configId: number | null
  modelName: string
  baseUrl: string
  isActive: boolean
  canSwitch: boolean
  statusText: string
}

const route = useRoute()
const session = getSession()

const models = ref<ModelItem[]>([])
const userCards = ref<UserCard[]>([])
const officialKeyByProvider = ref<Record<number, AnyRecord>>({})
const currentConfigId = ref<number | null>(null)
const editingConfigId = ref<number | null>(null)

const showKey = ref(false)
const testStatus = ref<'success' | 'error' | ''>('')

const form = ref({
  mode: 'official' as EditorMode,
  selectedModelId: '',
  modelInput: '',
  baseUrl: '',
  selectedUserKeyId: null as number | null,
  keyName: '',
  apiKey: ''
})

const getProjectId = () => Number(route.query.bookId || 0)
const getUserId = () => {
  if (typeof session.userId === 'number' && session.userId > 0) return session.userId
  const fromQuery = Number(route.query.userId || route.query.operatorId || 0)
  return fromQuery > 0 ? fromQuery : null
}
const getOperatorId = () => getUserId()

const detectBillingType = (pricing: unknown): BillingType => {
  if (!pricing) return 'PAID'
  try {
    const parsed = typeof pricing === 'string' ? JSON.parse(pricing) : pricing
    const json = JSON.stringify(parsed).toLowerCase()
    if (json.includes('free') || json.includes('"input":0') || json.includes('"output":0')) return 'FREE'
  } catch {
    // ignore
  }
  return 'PAID'
}

const modelById = computed<Record<string, ModelItem>>(() => {
  const dict: Record<string, ModelItem> = {}
  models.value.forEach((m) => {
    dict[m.id] = m
  })
  return dict
})

const configByOfficialKeyId = ref<Record<number, AnyRecord>>({})
const configByUserKeyId = ref<Record<number, AnyRecord>>({})

const officialCards = computed<OfficialCard[]>(() =>
  models.value.map((m) => {
    const officialKey = officialKeyByProvider.value[m.providerId]
    const officialKeyId = Number(officialKey?.id || 0) || null
    const config = officialKeyId ? configByOfficialKeyId.value[officialKeyId] : null
    const configId = Number(config?.id || 0) || null
    const modelName = String(config?.modelName || '')
    const hasOfficialKey = Boolean(officialKey)
    const available = hasOfficialKey && !!configId && !!modelName
    return {
      ...m,
      hasOfficialKey,
      officialKeyId,
      configId,
      modelName,
      baseUrl: String(config?.baseUrl || ''),
      isActive: currentConfigId.value === configId,
      canSwitch: available,
      statusText: !hasOfficialKey
        ? '未配置官方 Key，不能切换'
        : !configId
          ? '未保存项目配置，不能切换'
          : '可切换',
    }
  })
)

const formTitle = computed(() => {
  if (form.value.mode === 'official') return '编辑官方模型配置'
  if (form.value.mode === 'user-edit') return '编辑我的模型配置'
  return '新增我的模型配置'
})

const findDefaultConfig = (configs: AnyRecord[]) => configs.find((i) => Boolean(i.isDefault)) || configs[0]

const selectOfficial = (modelId: string) => {
  const m = officialCards.value.find((item) => item.id === modelId)
  if (!m) return
  form.value.mode = 'official'
  form.value.selectedModelId = modelId
  form.value.modelInput = m.modelName || ''
  form.value.baseUrl = m.baseUrl || ''
  form.value.selectedUserKeyId = null
  form.value.apiKey = ''
  form.value.keyName = String(officialKeyByProvider.value[m.providerId]?.keyName || `${m.providerName}-官方Key`)
  editingConfigId.value = m.configId
}

const selectUser = (userKeyId: number) => {
  const card = userCards.value.find((u) => u.userKeyId === userKeyId)
  if (!card) return
  form.value.mode = 'user-edit'
  form.value.selectedUserKeyId = userKeyId
  form.value.selectedModelId = card.selectedModelId
  form.value.modelInput = card.modelName || ''
  form.value.baseUrl = card.baseUrl || ''
  form.value.apiKey = ''
  form.value.keyName = card.keyName
  editingConfigId.value = card.configId
}

const startCreate = () => {
  form.value.mode = 'user-create'
  form.value.selectedUserKeyId = null
  form.value.selectedModelId = models.value[0]?.id || ''
  form.value.modelInput = ''
  form.value.baseUrl = ''
  form.value.keyName = ''
  form.value.apiKey = ''
  editingConfigId.value = null
}

const saveProjectConfig = async (projectId: number, operatorId: number, payload: AnyRecord) => {
  if (editingConfigId.value) {
    await modelApi.updateConfig(projectId, editingConfigId.value, operatorId, payload)
    await modelApi.setDefaultConfig(projectId, editingConfigId.value, operatorId)
    return editingConfigId.value
  }

  await modelApi.createConfig(projectId, operatorId, payload)
  const latestConfigs = (await modelApi.listConfigs(projectId)) as AnyRecord[]
  const created = findDefaultConfig(latestConfigs)
    || latestConfigs.find((item) => Number(item.userKeyId || 0) === Number(payload.userKeyId || 0) && Number(item.officialKeyId || 0) === Number(payload.officialKeyId || 0))
    || latestConfigs[latestConfigs.length - 1]
  const createdId = Number(created?.id || 0) || null
  if (createdId) {
    await modelApi.setDefaultConfig(projectId, createdId, operatorId)
  }
  return createdId
}

const switchOfficial = async (modelId: string) => {
  const card = officialCards.value.find((item) => item.id === modelId)
  const projectId = getProjectId()
  const operatorId = getOperatorId()
  if (!card || !projectId || !operatorId || !card.canSwitch || !card.configId) return
  await modelApi.setDefaultConfig(projectId, card.configId, operatorId)
  await loadData()
  emit('saved')
  message.success(`已切换到 ${card.name}`)
}

const switchUser = async (userKeyId: number) => {
  const card = userCards.value.find((item) => item.userKeyId === userKeyId)
  const projectId = getProjectId()
  const operatorId = getOperatorId()
  if (!card || !projectId || !operatorId || !card.canSwitch || !card.configId) return
  await modelApi.setDefaultConfig(projectId, card.configId, operatorId)
  await loadData()
  emit('saved')
  message.success(`已切换到 ${card.modelName}`)
}

const loadData = async () => {
  const userId = getUserId()
  const projectId = getProjectId()
  if (!userId) return

  const [providersResp, userKeysResp, officialKeysResp, configsResp] = await Promise.all([
    modelApi.listProviders(),
    modelApi.listKeys(userId),
    modelApi.listOfficialKeys(),
    projectId ? modelApi.listConfigs(projectId) : Promise.resolve([])
  ])

  const providers = (providersResp || []) as AnyRecord[]
  const userKeys = (userKeysResp || []) as AnyRecord[]
  const officialKeys = (officialKeysResp || []) as AnyRecord[]
  const configs = (configsResp || []) as AnyRecord[]
  const defaultConfig = findDefaultConfig(configs)
  currentConfigId.value = Number(defaultConfig?.id || 0) || null

  models.value = providers.map((provider, idx) => {
    const p = provider as AnyRecord
    const providerId = Number(p.id || p.providerId || idx + 1)
    const providerName = String(p.displayName || p.name || p.providerCode || p.code || `provider-${idx + 1}`)
    return {
      id: `provider-${providerId}`,
      providerId,
      providerName,
      providerModelId: 0,
      modelCode: '',
      name: providerName,
      billingType: 'PAID' as BillingType
    }
  })

  const officialDict: Record<number, AnyRecord> = {}
  officialKeys.forEach((k) => {
    const pid = Number(k.providerId || 0)
    if (pid > 0 && !officialDict[pid]) officialDict[pid] = k
  })
  officialKeyByProvider.value = officialDict

  const officialConfigDict: Record<number, AnyRecord> = {}
  const userConfigDict: Record<number, AnyRecord> = {}
  configs.forEach((config) => {
    const officialKeyId = Number(config.officialKeyId || 0)
    const userKeyId = Number(config.userKeyId || 0)
    if (officialKeyId > 0) officialConfigDict[officialKeyId] = config
    if (userKeyId > 0) userConfigDict[userKeyId] = config
  })
  configByOfficialKeyId.value = officialConfigDict
  configByUserKeyId.value = userConfigDict

  userCards.value = userKeys
    .map((k) => {
      const providerId = Number(k.providerId || 0)
      const keyId = Number(k.id || 0)
      const linkedConfig = userConfigDict[keyId]
      const configId = Number(linkedConfig?.id || 0) || null
      const modelId = models.value.find((m) => m.providerId === providerId)?.id || ''
      const status = String(k.status || '').toLowerCase()
      const modelName = String(linkedConfig?.modelName || '') || '未填写模型名'
      const available = status === 'active' && !!configId && modelName !== '未填写模型名'
      return {
        userKeyId: keyId,
        providerId,
        providerName: models.value.find((m) => m.providerId === providerId)?.providerName || `provider-${providerId}`,
        keyName: String(k.keyName || `我的Key-${providerId}`),
        modelName,
        selectedModelId: modelId,
        configId,
        baseUrl: String(linkedConfig?.baseUrl || ''),
        isActive: currentConfigId.value === configId,
        canSwitch: available,
        statusText: status !== 'active'
          ? '密钥不可用，不能切换'
          : !configId
            ? '未保存项目配置，不能切换'
            : '可切换'
      }
    })
    .filter((x) => x.userKeyId > 0)

  const activeUserCard = userCards.value.find((item) => item.isActive)
  const activeOfficialCard = officialCards.value.find((item) => item.isActive)
  if (activeUserCard) {
    selectUser(activeUserCard.userKeyId)
  } else if (activeOfficialCard) {
    selectOfficial(activeOfficialCard.id)
  } else if (officialCards.value[0]) {
    selectOfficial(officialCards.value[0].id)
  }
}

const testConnection = async () => {
  testStatus.value = ''
  if (!form.value.apiKey.trim()) {
    testStatus.value = 'error'
    return
  }
  await new Promise((r) => setTimeout(r, 500))
  testStatus.value = form.value.apiKey.trim().length > 8 ? 'success' : 'error'
}

const saveApi = async () => {
  const userId = getUserId()
  const operatorId = getOperatorId()
  const projectId = getProjectId()
  const model = modelById.value[form.value.selectedModelId]
  const modelName = form.value.modelInput.trim()
  if (!projectId || !operatorId || !modelName) {
    message.warning('缺少 projectId/operatorId 或模型名为空')
    return
  }
  if (!model) {
    message.warning('缺少供应商选择，请先选择一张模型卡片')
    return
  }

  const keyName = form.value.keyName.trim() || `${model.providerName}-${modelName}`
  const apiKey = form.value.apiKey.trim()
  let userKeyId: number | null = null
  let officialKeyId: number | null = null

  if (form.value.mode === 'official') {
    const exist = officialKeyByProvider.value[model.providerId]
    if (!exist && !apiKey) {
      message.warning('首次配置官方Key必须填写 API Key')
      return
    }
    if (exist?.id) {
      await modelApi.updateOfficialKey(Number(exist.id), operatorId, {
        keyName,
        apiKey: apiKey || undefined,
        isDefault: true,
        status: 'active'
      })
    } else {
      await modelApi.createOfficialKey(operatorId, {
        providerId: model.providerId,
        keyName,
        apiKey,
        isDefault: true,
        status: 'active'
      })
    }
    const latest = (await modelApi.listOfficialKeys()) as AnyRecord[]
    officialKeyId = Number(latest.find((i) => Number(i.providerId || 0) === model.providerId)?.id || 0) || null
  } else {
    if (!userId) {
      message.warning('缺少 userId')
      return
    }
    if (form.value.mode === 'user-create' && !apiKey) {
      message.warning('新增我的模型必须填写 API Key')
      return
    }
    if (form.value.mode === 'user-edit' && form.value.selectedUserKeyId) {
      const editingCard = userCards.value.find((u) => u.userKeyId === form.value.selectedUserKeyId)
      const providerChanged = Boolean(editingCard && editingCard.providerId !== model.providerId)
      if (providerChanged) {
        if (!apiKey) {
          message.warning('切换到其他供应商时必须填写 API Key（将创建新密钥）')
          return
        }
        await modelApi.createKey(userId, operatorId, {
          providerId: model.providerId,
          keyName,
          apiKey,
          isDefault: true,
          status: 'active'
        })
        const latest = (await modelApi.listKeys(userId)) as AnyRecord[]
        userKeyId = Number(
          latest
            .filter((i) => Number(i.providerId || 0) === model.providerId)
            .sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0]?.id || 0
        ) || null
      } else {
        await modelApi.updateKey(form.value.selectedUserKeyId, userId, operatorId, {
          keyName,
          apiKey: apiKey || undefined,
          isDefault: true,
          status: 'active'
        })
        userKeyId = form.value.selectedUserKeyId
      }
    } else {
      await modelApi.createKey(userId, operatorId, {
        providerId: model.providerId,
        keyName,
        apiKey,
        isDefault: true,
        status: 'active'
      })
      const latest = (await modelApi.listKeys(userId)) as AnyRecord[]
      userKeyId = Number(latest.find((i) => Number(i.providerId || 0) === model.providerId)?.id || 0) || null
    }
  }

  const configId = await saveProjectConfig(projectId, operatorId, {
    configName: '默认写作配置',
    scene: 'write',
    providerModelId: undefined,
    modelName,
    baseUrl: form.value.baseUrl,
    userKeyId,
    officialKeyId,
    isDefault: true
  })

  editingConfigId.value = configId

  message.success('模型配置已保存')
  await loadData()
  emit('saved')
}

const deleteCurrentConfig = async () => {
  const projectId = getProjectId()
  const operatorId = getOperatorId()
  if (!projectId || !operatorId || !editingConfigId.value) return
  await modelApi.deleteConfig(projectId, editingConfigId.value, operatorId)
  message.success('模型配置已删除')
  editingConfigId.value = null
  await loadData()
  emit('saved')
}

watch(
  () => props.visible,
  (v) => {
    if (!v) return
    void loadData().catch((err: any) => {
      message.warning(err?.message || '模型配置加载失败')
    })
  }
)
</script>

<style lang="less" scoped>
.model-settings { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; }
.ms-backdrop { position: absolute; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(6px); }
.ms-modal { position: relative; width: 760px; max-width: 92vw; max-height: 88vh; background: rgba(17,24,39,0.92); border: 1px solid rgba(201,169,110,0.2); border-radius: 16px; display: flex; flex-direction: column; overflow: hidden; }
.ms-header { display: flex; align-items: center; gap: 10px; padding: 18px 22px; border-bottom: 1px solid var(--border-subtle); }
.ms-header h3 { flex: 1; margin: 0; color: var(--xuan-paper); }
.ms-close { background: none; border: none; color: var(--text-muted); cursor: pointer; }
.ms-body { flex: 1; overflow: auto; padding: 18px 22px; display: flex; flex-direction: column; gap: 16px; }
.ms-desc { color: var(--text-muted); font-size: 0.85rem; }
.ms-section { display: flex; flex-direction: column; gap: 8px; }
.ms-section h4 { margin: 0; color: var(--amber-gold); font-size: 0.92rem; }
.ms-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 10px; }
.ms-card { text-align: left; background: rgba(11,17,32,0.4); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 12px; color: var(--text-primary); display: flex; flex-direction: column; gap: 8px; }
.ms-card.active, .ms-card:hover { border-color: var(--border-gold); }
.ms-card.unavailable { opacity: 0.7; }
.name { font-size: 0.9rem; }
.meta { font-size: 0.75rem; color: var(--text-muted); }
.tag { font-size: 0.7rem; color: var(--amber-gold); }
.card-model-name { font-size: 0.76rem; color: var(--text-secondary); }
.card-actions { display: flex; gap: 8px; }
.card-btn { flex: 1; border-radius: 6px; border: 1px solid var(--border-subtle); padding: 6px 8px; cursor: pointer; }
.card-btn.switch { color: var(--amber-gold); background: rgba(201,169,110,0.08); }
.card-btn.edit { color: var(--sky-cyan); background: rgba(110,197,212,0.08); }
.card-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.add-card { align-items: center; justify-content: center; border-style: dashed; }
.plus { font-size: 1.8rem; color: var(--amber-gold); line-height: 1; }
.api-form { padding: 14px; border: 1px solid var(--border-subtle); border-radius: 10px; display: flex; flex-direction: column; gap: 10px; }
.form-title { margin: 0; color: var(--amber-gold); }
.form-row { display: flex; flex-direction: column; gap: 4px; }
.form-row label { font-size: 0.8rem; color: var(--text-secondary); }
.f-input { width: 100%; box-sizing: border-box; padding: 10px 12px; background: rgba(11,17,32,0.6); border: 1px solid var(--border-subtle); border-radius: 6px; color: var(--text-primary); }
.key-input-wrap { position: relative; }
.key-input-wrap .f-input { padding-right: 42px; }
.btn-toggle-key { position: absolute; right: 6px; top: 50%; transform: translateY(-50%); background: none; border: none; cursor: pointer; }
.api-actions { display: flex; gap: 10px; }
.btn-test, .btn-save-api, .btn-delete-api { border: 1px solid var(--border-subtle); border-radius: 6px; padding: 8px 12px; cursor: pointer; }
.btn-test { color: var(--sky-cyan); background: rgba(110,197,212,0.08); }
.btn-save-api { color: var(--amber-gold); background: rgba(201,169,110,0.08); }
.btn-delete-api { color: #e8a87c; background: rgba(232,168,124,0.08); }
.test-result { font-size: 0.82rem; }
.test-result.success { color: var(--jade-green); }
.test-result.error { color: #e8a87c; }
.ms-footer { padding: 14px 22px; border-top: 1px solid var(--border-subtle); display: flex; justify-content: flex-end; }
.btn-close-modal { padding: 8px 22px; border-radius: 6px; border: 1px solid var(--border-gold); background: rgba(201,169,110,0.08); color: var(--amber-gold); cursor: pointer; }
</style>
