<template>
  <div v-if="visible" class="model-settings">
    <div class="ms-backdrop" @click="$emit('close')"></div>
    <div class="ms-modal glass-panel">
      <div class="ms-header">
        <span class="ms-emoji">🔑</span>
        <div class="ms-title-group">
          <h3>模型设置</h3>
          <p>统一维护模型配置池，再为主 Agent / Dirty Work Agent 选择默认模型。</p>
        </div>
        <button class="ms-close" @click="$emit('close')">✕</button>
      </div>

      <div class="ms-body">
        <section class="ms-section">
          <div class="section-title-row">
            <div>
              <h4>模型池</h4>
              <p class="section-desc">用户视角只维护模型配置；创建与编辑时直接填写 Key，模型绑定 key 仅作为后端内部实现。</p>
            </div>
            <button type="button" class="btn-save-api" @click="startCreate">新增模型</button>
          </div>

          <div v-if="userConfigs.length === 0" class="empty-state">
            <strong>还没有模型配置</strong>
            <span>先新增一个模型配置到模型池，随后即可分配给不同 Agent。</span>
            <button type="button" class="btn-save-api" @click="startCreate">创建第一条配置</button>
          </div>

          <div v-else class="config-list">
            <article
              v-for="item in userConfigs"
              :key="item.modelConfigId"
              class="config-card"
              :class="{
                active: editingConfigId === item.modelConfigId && formMode === 'edit',
                assigned: isConfigAssigned(item.modelConfigId),
              }"
            >
              <div class="config-card-head">
                <div>
                  <strong>{{ item.modelName || '未命名模型' }}</strong>
                  <span>{{ item.providerName || `provider-${item.providerId}` }}</span>
                </div>
                <div class="config-status-group">
                  <span class="config-type-badge" :class="item.keySourceType === 'OFFICIAL_KEY' ? 'official' : 'user'">
                    {{ item.keySourceType === 'OFFICIAL_KEY' ? '官方模型' : '用户模型' }}
                  </span>
                  <span class="config-status">{{ item.status || 'active' }}</span>
                </div>
              </div>

              <div class="config-card-meta">
                {{ item.keySourceTypeLabel }} · 已配置 Key
                <span v-if="item.baseUrl"> · 自定义 Base URL</span>
              </div>

              <div class="config-card-tags">
                <span v-if="mainAgentModelConfigId === String(item.modelConfigId)" class="config-tag primary">主 Agent</span>
                <span v-if="dirtyWorkAgentModelConfigId === String(item.modelConfigId)" class="config-tag secondary">Dirty Work Agent</span>
              </div>

              <div class="config-card-actions">
                <button type="button" class="card-action-btn" @click="assignRole('main', item.modelConfigId)">设为主 Agent</button>
                <button type="button" class="card-action-btn" @click="assignRole('dirty', item.modelConfigId)">设为副 Agent</button>
                <button type="button" class="card-action-btn" @click="selectConfig(item)">编辑</button>
                <button type="button" class="card-delete-btn" @click="deleteConfig(item.modelConfigId)">删除</button>
              </div>
            </article>
          </div>
        </section>

        <section v-if="isFormVisible" class="api-form">
          <div class="section-title-row">
            <div>
              <h4 class="form-title">{{ formMode === 'edit' ? '编辑模型配置' : '新增模型配置' }}</h4>
              <p class="section-desc">用户只配置模型与 Key 输入值；后端会在内部创建并绑定对应 key 记录。</p>
            </div>
            <button type="button" class="ms-close-inline" @click="cancelForm">收起</button>
          </div>

          <div class="form-grid">
            <div class="form-row">
              <label>供应商</label>
              <select v-model="form.providerId" class="f-input">
                <option value="">请选择</option>
                <option v-for="item in providerOptions" :key="item.providerId" :value="String(item.providerId)">
                  {{ item.providerName }}
                </option>
              </select>
            </div>

            <div class="form-row">
              <label>模型名</label>
              <input v-model="form.modelName" class="f-input" placeholder="例如：gpt-4o-mini" />
            </div>

            <div class="form-row form-row-full">
              <label>Base URL（可选）</label>
              <input v-model="form.baseUrl" class="f-input" placeholder="留空则使用提供商默认地址" />
            </div>

            <div class="form-row">
              <label>模型类别</label>
              <select v-model="form.keySourceType" class="f-input">
                <option value="USER_KEY">用户模型</option>
                <option value="OFFICIAL_KEY">官方模型</option>
              </select>
            </div>

            <div class="form-row">
              <label>Key</label>
              <input v-model="form.apiKey" class="f-input" placeholder="例如：sk-xxx" />
            </div>

            <div class="form-row form-row-full form-tip-row">
              <label>配置说明</label>
              <span class="form-tip">接口与界面只暴露模型配置与 Key 输入；key 记录生成与绑定属于后端内部实现。</span>
            </div>
          </div>

          <div class="api-actions">
            <button type="button" class="btn-save-api" @click="saveConfig">保存模型配置</button>
            <button type="button" class="btn-secondary" @click="cancelForm">取消</button>
          </div>
        </section>
      </div>

      <div class="ms-footer">
        <button class="btn-close-modal" @click="$emit('close')">完成</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { modelApi } from '@/api/modules/model.api'
import { getSession } from '@/stores/session'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
}>()

type AnyRecord = Record<string, unknown>

type ProviderOption = {
  providerId: string
  providerName: string
}

type KeySourceType = 'USER_KEY' | 'OFFICIAL_KEY'

type UserModelConfig = {
  modelConfigId: string
  providerId: string
  providerName: string
  modelName: string
  baseUrl: string
  keySourceType: KeySourceType
  keySourceTypeLabel: string
  userKeyId: string | null
  officialKeyId: string | null
  keyName: string
  maskedApiKey: string
  status: string
}

type FormMode = 'create' | 'edit' | null

type RoleKey = 'main' | 'dirty'

const session = getSession()
const providerOptions = ref<ProviderOption[]>([])
const userConfigs = ref<UserModelConfig[]>([])
const mainAgentModelConfigId = ref('')
const dirtyWorkAgentModelConfigId = ref('')
const editingConfigId = ref<string | null>(null)
const formMode = ref<FormMode>(null)

const form = reactive({
  providerId: '',
  modelName: '',
  baseUrl: '',
  keySourceType: 'USER_KEY' as KeySourceType,
  apiKey: '',
})

const getUserId = () => {
  if (typeof session.userId !== 'string') {
    return null
  }
  const normalized = session.userId.trim()
  return normalized || null
}
const getOperatorId = () => getUserId()

const isFormVisible = computed(() => formMode.value !== null)
const deriveKeySourceType = (item: AnyRecord): KeySourceType =>
  String(item.keySourceType ?? 'USER_KEY') === 'OFFICIAL_KEY' ? 'OFFICIAL_KEY' : 'USER_KEY'

const formatKeySourceTypeLabel = (keySourceType: KeySourceType) => (keySourceType === 'OFFICIAL_KEY' ? '官方模型' : '用户模型')
const extractPreferenceRecord = (payload: unknown): AnyRecord => {
  if (!payload || typeof payload !== 'object') {
    return {}
  }

  const record = payload as AnyRecord
  const nestedPreferences = record.preferences
  if (nestedPreferences && typeof nestedPreferences === 'object') {
    return nestedPreferences as AnyRecord
  }

  const nestedConfig = record.config
  if (nestedConfig && typeof nestedConfig === 'object') {
    return nestedConfig as AnyRecord
  }

  return record
}

const normalizeBusinessStringId = (value: unknown) => {
  if (typeof value !== 'string') {
    return ''
  }
  return value.trim()
}

const normalizeAssignedConfigId = (modelConfigId: string) => {
  const normalizedId = normalizeBusinessStringId(modelConfigId)
  if (!normalizedId) {
    return ''
  }
  return userConfigs.value.some((item) => item.modelConfigId === normalizedId) ? normalizedId : ''
}

const isConfigAssigned = (modelConfigId: string) =>
  mainAgentModelConfigId.value === modelConfigId || dirtyWorkAgentModelConfigId.value === modelConfigId

const resetForm = () => {
  editingConfigId.value = null
  form.providerId = ''
  form.modelName = ''
  form.baseUrl = ''
  form.keySourceType = 'USER_KEY'
  form.apiKey = ''
}

watch(
  () => [form.providerId, form.keySourceType],
  () => {
    form.apiKey = ''
  }
)

const cancelForm = () => {
  resetForm()
  formMode.value = null
}

const selectConfig = (item: UserModelConfig) => {
  formMode.value = 'edit'
  editingConfigId.value = item.modelConfigId
  form.providerId = item.providerId
  form.modelName = item.modelName
  form.baseUrl = item.baseUrl
  form.keySourceType = item.keySourceType
  form.apiKey = ''
}

const startCreate = () => {
  resetForm()
  formMode.value = 'create'
}

const savePreferences = async (nextMainAgentModelConfigId: string, nextDirtyWorkAgentModelConfigId: string) => {
  const userId = getUserId()
  const operatorId = getOperatorId()
  if (!userId || !operatorId) {
    message.warning('缺少用户会话')
    return
  }

  const normalizedMainAgentModelConfigId = normalizeAssignedConfigId(nextMainAgentModelConfigId)
  const normalizedDirtyWorkAgentModelConfigId = normalizeAssignedConfigId(nextDirtyWorkAgentModelConfigId)

  try {
    await modelApi.saveUserModelPreferences(userId, operatorId, {
      mainAgentModelConfigId: normalizedMainAgentModelConfigId || null,
      dirtyWorkAgentModelConfigId: normalizedDirtyWorkAgentModelConfigId || null,
    })

    mainAgentModelConfigId.value = normalizedMainAgentModelConfigId
    dirtyWorkAgentModelConfigId.value = normalizedDirtyWorkAgentModelConfigId
    message.success('角色偏好已保存')
    emit('saved')
  } catch {
    message.warning('角色偏好保存失败，请稍后重试')
  }
}

const assignRole = async (role: RoleKey, modelConfigId: string) => {
  const nextValue = modelConfigId
  const nextMainAgentModelConfigId = role === 'main' ? nextValue : mainAgentModelConfigId.value
  const nextDirtyWorkAgentModelConfigId = role === 'dirty' ? nextValue : dirtyWorkAgentModelConfigId.value

  await savePreferences(nextMainAgentModelConfigId, nextDirtyWorkAgentModelConfigId)
}

const loadData = async () => {
  const userId = getUserId()
  if (!userId) {
    cancelForm()
    userConfigs.value = []
    mainAgentModelConfigId.value = ''
    dirtyWorkAgentModelConfigId.value = ''
    return
  }

  try {
    const [providersResp, configsResp, preferencesResp] = await Promise.all([
      modelApi.listProviders(),
      modelApi.listUserModelConfigs(userId),
      modelApi.getUserModelPreferences(userId),
    ])

    providerOptions.value = ((providersResp || []) as AnyRecord[])
      .map((item) => {
        const providerId = normalizeBusinessStringId(item.providerId)
        if (!providerId) {
          return null
        }
        return {
          providerId,
          providerName: String(item.displayName ?? item.name ?? item.providerCode ?? item.code ?? 'provider'),
        }
      })
      .filter((item): item is ProviderOption => item !== null)

    userConfigs.value = ((configsResp || []) as AnyRecord[])
      .map((item) => {
        const modelConfigId = normalizeBusinessStringId(item.modelConfigId)
        const providerId = normalizeBusinessStringId(item.providerId)
        if (!modelConfigId || !providerId) {
          return null
        }
        const keySourceType = deriveKeySourceType(item)
        return {
          modelConfigId,
          providerId,
          providerName: providerOptions.value.find((provider) => provider.providerId === providerId)?.providerName || `provider-${providerId}`,
          modelName: String(item.modelName ?? ''),
          baseUrl: String(item.baseUrl ?? ''),
          keySourceType,
          keySourceTypeLabel: formatKeySourceTypeLabel(keySourceType),
          userKeyId: item.userKeyId == null ? null : normalizeBusinessStringId(item.userKeyId),
          officialKeyId: item.officialKeyId == null ? null : normalizeBusinessStringId(item.officialKeyId),
          keyName: String(item.keyName ?? ''),
          maskedApiKey: String(item.maskedApiKey ?? ''),
          status: String(item.status ?? 'active'),
        }
      })
      .filter((item): item is UserModelConfig => item !== null)

    const preferenceRecord = extractPreferenceRecord(preferencesResp)
    mainAgentModelConfigId.value = normalizeAssignedConfigId(normalizeBusinessStringId(preferenceRecord.mainAgentModelConfigId))
    dirtyWorkAgentModelConfigId.value = normalizeAssignedConfigId(normalizeBusinessStringId(preferenceRecord.dirtyWorkAgentModelConfigId))

    if (formMode.value === 'edit' && editingConfigId.value) {
      const matched = userConfigs.value.find((item) => item.modelConfigId === editingConfigId.value)
      if (matched) {
        selectConfig(matched)
        return
      }
    }

    if (userConfigs.value.length === 0) {
      cancelForm()
    }
  } catch {
    providerOptions.value = []
    userConfigs.value = []
    mainAgentModelConfigId.value = ''
    dirtyWorkAgentModelConfigId.value = ''
    cancelForm()
    message.warning('模型设置加载失败，请稍后重试')
  }
}

const saveConfig = async () => {
  const userId = getUserId()
  const operatorId = getOperatorId()
  if (!userId || !operatorId) {
    message.warning('缺少用户会话')
    return
  }
  if (!form.providerId || !form.modelName.trim()) {
    message.warning('请选择供应商并填写模型名')
    return
  }
  if (!form.apiKey.trim()) {
    message.warning('请填写 Key')
    return
  }

  const payload: AnyRecord = {
    providerId: form.providerId,
    modelName: form.modelName,
    baseUrl: form.baseUrl,
    modelCategory: form.keySourceType === 'OFFICIAL_KEY' ? 'OFFICIAL_MODEL' : 'USER_MODEL',
    apiKey: form.apiKey,
    status: 'active',
  }

  try {
    if (editingConfigId.value) {
      await modelApi.updateUserModelConfig(userId, editingConfigId.value, operatorId, payload)
    } else {
      await modelApi.createUserModelConfig(userId, operatorId, payload)
    }

    message.success('模型配置已保存')
    await loadData()
    cancelForm()
    emit('saved')
  } catch {
    message.warning('模型配置保存失败，请稍后重试')
  }
}

const deleteConfig = async (modelConfigId: string) => {
  const userId = getUserId()
  const operatorId = getOperatorId()
  if (!userId || !operatorId) return

  try {
    await modelApi.deleteUserModelConfig(userId, modelConfigId, operatorId)
    if (mainAgentModelConfigId.value === modelConfigId) {
      mainAgentModelConfigId.value = ''
    }
    if (dirtyWorkAgentModelConfigId.value === modelConfigId) {
      dirtyWorkAgentModelConfigId.value = ''
    }
    if (editingConfigId.value === modelConfigId) {
      cancelForm()
    }
    message.success('模型配置已删除')
    await loadData()
    emit('saved')
  } catch {
    message.warning('模型配置删除失败，请稍后重试')
  }
}

watch(
  () => getUserId(),
  () => {
    void loadData()
  },
  { immediate: true }
)
</script>

<style lang="less" scoped>
.model-settings { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; }
.ms-backdrop { position: absolute; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(6px); }
.ms-modal { position: relative; width: 960px; max-width: 94vw; max-height: 88vh; background: rgba(17,24,39,0.92); border: 1px solid rgba(201,169,110,0.2); border-radius: 16px; display: flex; flex-direction: column; overflow: hidden; }
.ms-header { display: flex; align-items: flex-start; gap: 10px; padding: 18px 22px; border-bottom: 1px solid var(--border-subtle); }
.ms-title-group { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.ms-header h3 { margin: 0; color: var(--xuan-paper); }
.ms-title-group p { margin: 0; color: var(--text-muted); font-size: 0.85rem; }
.ms-close, .ms-close-inline { background: none; border: none; color: var(--text-muted); cursor: pointer; }
.ms-body { flex: 1; overflow: auto; padding: 18px 22px; display: flex; flex-direction: column; gap: 16px; }
.api-form { padding: 18px; border: 1px solid var(--border-subtle); border-radius: 14px; display: flex; flex-direction: column; gap: 16px; background: rgba(11,17,32,0.32); }
.ms-section { display: flex; flex-direction: column; gap: 12px; }
.section-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.section-title-row h4, .form-title { margin: 0; color: var(--amber-gold); }
.section-desc { margin: 4px 0 0; color: var(--text-muted); font-size: 0.82rem; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.form-row { display: flex; flex-direction: column; gap: 4px; }
.form-row-full { grid-column: 1 / -1; }
.form-row label { font-size: 0.8rem; color: var(--text-secondary); }
.form-tip-row { gap: 8px; }
.form-tip { color: var(--text-muted); font-size: 0.78rem; line-height: 1.5; padding: 12px 14px; border-radius: 10px; border: 1px dashed var(--border-subtle); background: rgba(11,17,32,0.4); }
.f-input { width: 100%; box-sizing: border-box; padding: 12px 14px; background: rgba(11,17,32,0.6); border: 1px solid var(--border-subtle); border-radius: 10px; color: var(--text-primary); }
.api-actions { display: flex; gap: 10px; }
.btn-save-api, .btn-secondary, .card-action-btn, .card-delete-btn { border: 1px solid var(--border-subtle); border-radius: 10px; padding: 10px 14px; cursor: pointer; }
.btn-save-api { color: var(--amber-gold); background: rgba(201,169,110,0.08); }
.btn-secondary, .card-action-btn { color: var(--text-secondary); background: rgba(255,255,255,0.03); }
.empty-state { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; padding: 20px; border: 1px dashed var(--border-subtle); border-radius: 14px; color: var(--text-muted); background: rgba(11,17,32,0.22); }
.config-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.config-card { text-align: left; background: linear-gradient(180deg, rgba(11,17,32,0.62), rgba(11,17,32,0.42)); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px; color: var(--text-primary); display: flex; flex-direction: column; gap: 10px; }
.config-card.active { border-color: var(--border-gold); box-shadow: 0 0 0 1px rgba(201,169,110,0.22); }
.config-card.assigned { background: linear-gradient(180deg, rgba(35,48,74,0.7), rgba(11,17,32,0.42)); }
.config-card-head { display: flex; justify-content: space-between; gap: 8px; }
.config-card-head strong { display: block; }
.config-status-group { display: flex; align-items: center; gap: 8px; }
.config-card-head span, .config-card-meta, .config-status { font-size: 0.75rem; color: var(--text-muted); }
.config-type-badge { border-radius: 999px; padding: 2px 8px; font-size: 0.7rem; border: 1px solid var(--border-subtle); }
.config-type-badge.user { color: #9cc8ff; background: rgba(115,181,255,0.14); }
.config-type-badge.official { color: var(--amber-gold); background: rgba(201,169,110,0.16); }
.config-card-tags { display: flex; flex-wrap: wrap; gap: 8px; min-height: 24px; }
.config-tag { border-radius: 999px; padding: 4px 8px; font-size: 0.72rem; }
.config-tag.primary { background: rgba(201,169,110,0.16); color: var(--amber-gold); }
.config-tag.secondary { background: rgba(115,181,255,0.14); color: #9cc8ff; }
.config-card-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.card-delete-btn { background: rgba(232,168,124,0.08); color: #e8a87c; }
.ms-footer { padding: 14px 22px; border-top: 1px solid var(--border-subtle); display: flex; justify-content: flex-end; }
.btn-close-modal { padding: 8px 22px; border-radius: 6px; border: 1px solid var(--border-gold); background: rgba(201,169,110,0.08); color: var(--amber-gold); cursor: pointer; }

@media (max-width: 900px) {
  .config-list,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
