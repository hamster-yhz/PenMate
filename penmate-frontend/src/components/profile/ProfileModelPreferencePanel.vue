<template>
  <section class="model-preferences" data-testid="profile-model-preference-panel">
    <header>
      <div>
        <h2>默认模型与 Agent</h2>
        <p>三个模型分别承担创作、上下文筛选和语义检索。</p>
      </div>
      <button type="button" :disabled="loading || saving" data-testid="model-preference-save" @click="emit('save')">
        <LoadingOutlined v-if="saving" />
        <SaveOutlined v-else />
        {{ saving ? '保存中' : '保存' }}
      </button>
    </header>

    <div v-if="loading" class="state-text">正在加载模型配置...</div>
    <div v-else-if="error" class="state-text error-text" role="alert">
      <span>{{ error }}</span>
      <button type="button" data-testid="model-preference-retry" @click="emit('retry')">重新加载</button>
    </div>
    <div v-else class="preference-list">
      <div class="preference-row">
        <span class="preference-copy"><strong>创作模型</strong><small>负责 Agent 决策、正文生成与改写</small></span>
        <ModelPicker
          :model-value="creativeModelConfigId"
          label="创作模型"
          :options="chatOptions"
          :disabled="saving"
          test-id="model-preference-creative-select"
          @update:model-value="emit('update:creative-model-config-id', $event)"
        />
      </div>

      <div class="preference-row">
        <span class="preference-copy"><strong>上下文筛选模型</strong><small>负责智能筛选 Story Bible 与上下文</small></span>
        <ModelPicker
          :model-value="contextSelectorModelConfigId"
          label="上下文筛选模型"
          :options="chatOptions"
          :disabled="saving"
          test-id="model-preference-selector-select"
          @update:model-value="emit('update:context-selector-model-config-id', $event)"
        />
      </div>

      <div class="preference-row">
        <span class="preference-copy"><strong>Embedding 模型</strong><small>负责向量索引与语义检索</small></span>
        <ModelPicker
          :model-value="embeddingModelConfigId"
          label="Embedding 模型"
          :options="embeddingOptions"
          :disabled="saving"
          test-id="model-preference-embedding-select"
          @update:model-value="emit('update:embedding-model-config-id', $event)"
        />
      </div>

      <div v-if="successMessage" class="state-text success-text">{{ successMessage }}</div>
      <div v-else-if="!options.length" class="state-text">暂无模型配置，请先在“模型服务”中添加。</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { LoadingOutlined, SaveOutlined } from '@ant-design/icons-vue'
import ModelPicker, { type ModelPickerOption } from '@/components/model/ModelPicker.vue'
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

const pickerOptions = computed<ModelPickerOption[]>(() =>
  props.options.map((option) => ({
    id: option.modelConfigId,
    displayName: option.displayName || option.modelName,
    modelName: option.modelName,
    providerName: option.providerName,
    type: option.modelType === 'EMBEDDING' ? 'EMBEDDING' : 'CHAT',
    official: option.scopeType === 'SYSTEM' || option.keySourceType === 'OFFICIAL_KEY',
    usable: option.usable,
    unavailableReason: option.unavailableReason,
  })),
)
const chatOptions = computed(() => pickerOptions.value.filter((option) => option.type === 'CHAT'))
const embeddingOptions = computed(() => pickerOptions.value.filter((option) => option.type === 'EMBEDDING'))
</script>

<style scoped>
.model-preferences { background: var(--bg-surface); border: 1px solid var(--border-subtle); }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
h2 { margin: 0 0 4px; font-size: 15px; letter-spacing: 0; }
p { margin: 0; color: var(--text-muted); font-size: 12px; }
header button { display: inline-flex; min-width: 78px; min-height: 34px; align-items: center; justify-content: center; gap: 6px; color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); border-radius: 5px; cursor: pointer; }
header button:disabled { cursor: not-allowed; opacity: 0.55; }
.preference-list { display: grid; }
.preference-row { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(260px, 340px); align-items: center; gap: 24px; min-height: 92px; padding: 13px 20px; border-bottom: 1px solid var(--border-subtle); }
.preference-copy { display: grid; gap: 4px; }
.preference-copy strong { font-size: 13px; }
.preference-copy small { color: var(--text-muted); font-size: 11px; }
.preference-row :deep(.model-picker-label) { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.state-text { padding: 14px 20px; color: var(--text-muted); font-size: 12px; }
.error-text { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--danger); background: var(--danger-soft); }
.error-text button { min-height: 30px; padding: 0 9px; color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }
.success-text { color: var(--success); }
@media (max-width: 680px) { .preference-row { grid-template-columns: 1fr; gap: 9px; } }
</style>
