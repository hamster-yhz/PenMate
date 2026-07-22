<template>
  <section class="model-preferences" data-testid="profile-model-preference-panel">
    <header>
      <div>
        <h2>默认模型与 Agent</h2>
        <p>三个模型分别承担创作、上下文筛选和语义检索。</p>
      </div>
      <button type="button" :disabled="loading || saving" data-testid="model-preference-save" @click="emit('save')">
        {{ saving ? '保存中' : '保存' }}
      </button>
    </header>

    <div v-if="loading" class="state-text">正在加载模型配置...</div>
    <div v-else-if="error" class="state-text error-text" role="alert">
      <span>{{ error }}</span>
      <button type="button" data-testid="model-preference-retry" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else class="preference-list">
      <label>
        <span><strong>创作模型</strong><small>负责 Agent 决策、正文生成与改写</small></span>
        <select data-testid="model-preference-creative-select" :value="toSelectValue(creativeModelConfigId)" :disabled="saving" @change="emitValue('update:creative-model-config-id', $event)">
          <option value="">未设置</option>
          <option v-for="option in chatOptions" :key="`creative-${option.modelConfigId}`" :value="option.modelConfigId">{{ formatOptionLabel(option) }}</option>
        </select>
      </label>

      <label>
        <span><strong>上下文筛选模型</strong><small>负责智能筛选 Story Bible 与上下文</small></span>
        <select data-testid="model-preference-selector-select" :value="toSelectValue(contextSelectorModelConfigId)" :disabled="saving" @change="emitValue('update:context-selector-model-config-id', $event)">
          <option value="">未设置</option>
          <option v-for="option in chatOptions" :key="`selector-${option.modelConfigId}`" :value="option.modelConfigId">{{ formatOptionLabel(option) }}</option>
        </select>
      </label>

      <label>
        <span><strong>Embedding 模型</strong><small>负责向量索引与语义检索</small></span>
        <select data-testid="model-preference-embedding-select" :value="toSelectValue(embeddingModelConfigId)" :disabled="saving" @change="emitValue('update:embedding-model-config-id', $event)">
          <option value="">未设置</option>
          <option v-for="option in embeddingOptions" :key="`embedding-${option.modelConfigId}`" :value="option.modelConfigId">{{ formatOptionLabel(option) }}</option>
        </select>
      </label>

      <div v-if="successMessage" class="state-text success-text">{{ successMessage }}</div>
      <div v-else-if="!options.length" class="state-text">暂无模型配置，请先在“模型服务”中添加。</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProfileModelConfigOption } from '@/composables/profile/useProfileSettings'

const props = defineProps<{
  loading: boolean
  saving: boolean
  error: string
  successMessage: string
  options: ProfileModelConfigOption[]
  creativeModelConfigId: string | null
  contextSelectorModelConfigId: string | null
  embeddingModelConfigId: string | null
}>()

const emit = defineEmits<{
  'update:creative-model-config-id': [string | null]
  'update:context-selector-model-config-id': [string | null]
  'update:embedding-model-config-id': [string | null]
  save: []
  retry: []
}>()

const chatOptions = computed(() => props.options.filter((option) => !option.modelType || option.modelType === 'CHAT'))
const embeddingOptions = computed(() => props.options.filter((option) => option.modelType === 'EMBEDDING'))
const toSelectValue = (value: string | null) => value ?? ''
const emitValue = (
  event: 'update:creative-model-config-id' | 'update:context-selector-model-config-id' | 'update:embedding-model-config-id',
  domEvent: Event,
) => {
  const value = (domEvent.target as HTMLSelectElement).value.trim() || null
  if (event === 'update:creative-model-config-id') emit('update:creative-model-config-id', value)
  else if (event === 'update:context-selector-model-config-id') emit('update:context-selector-model-config-id', value)
  else emit('update:embedding-model-config-id', value)
}
const formatOptionLabel = (option: ProfileModelConfigOption) => {
  const name = option.displayName || option.modelName
  const provider = option.providerName ? `${option.providerName} · ` : ''
  return `${provider}${name}${name !== option.modelName ? ` (${option.modelName})` : ''}`
}
</script>

<style scoped>
.model-preferences { background: var(--bg-surface); border: 1px solid var(--border-subtle); }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
h2 { margin: 0 0 4px; font-size: 15px; letter-spacing: 0; }
p { margin: 0; color: var(--text-muted); font-size: 12px; }
header button { min-width: 70px; min-height: 34px; color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); border-radius: 5px; cursor: pointer; }
header button:disabled { cursor: not-allowed; opacity: 0.55; }
.preference-list { display: grid; }
label { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(220px, 320px); align-items: center; gap: 24px; min-height: 78px; padding: 13px 20px; border-bottom: 1px solid var(--border-subtle); }
label > span { display: grid; gap: 4px; }
label strong { font-size: 13px; }
label small { color: var(--text-muted); font-size: 11px; }
select { width: 100%; height: 36px; padding: 0 9px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; }
.state-text { padding: 14px 20px; color: var(--text-muted); font-size: 12px; }
.error-text { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--danger); background: var(--danger-soft); }
.error-text button { min-height: 30px; padding: 0 9px; color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }
.success-text { color: var(--success); }
@media (max-width: 680px) { label { grid-template-columns: 1fr; gap: 9px; } }
</style>
