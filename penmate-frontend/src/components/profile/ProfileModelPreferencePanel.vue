<template>
  <div class="settings-section glass-panel" data-testid="profile-model-preference-panel">
    <div class="section-header">
      <div>
        <h3 class="section-title">🤖 模型偏好</h3>
        <p class="section-desc">为主 Agent 与脏活 Agent 选择默认模型配置。</p>
      </div>
      <button
        class="save-btn"
        type="button"
        :disabled="loading || saving || !options.length"
        data-testid="model-preference-save"
        @click="emit('save')"
      >
        {{ saving ? '保存中...' : '保存偏好' }}
      </button>
    </div>

    <div v-if="loading" class="state-text">正在加载模型偏好...</div>
    <div v-else-if="error" class="state-text error-text">{{ error }}</div>
    <div v-else class="editor-body">
      <label class="field-block">
        <span class="field-label">主 Agent</span>
        <select
          class="field-select"
          data-testid="model-preference-main-select"
          :value="toSelectValue(mainAgentModelConfigId)"
          :disabled="saving || !options.length"
          @change="handleSelectChange('main-agent', $event)"
        >
          <option value="">未设置</option>
          <option v-for="option in options" :key="`main-${option.modelConfigId}`" :value="String(option.modelConfigId)">
            {{ formatOptionLabel(option) }}
          </option>
        </select>
      </label>

      <label class="field-block">
        <span class="field-label">脏活 Agent</span>
        <select
          class="field-select"
          data-testid="model-preference-dirty-select"
          :value="toSelectValue(dirtyWorkAgentModelConfigId)"
          :disabled="saving || !options.length"
          @change="handleSelectChange('dirty-work-agent', $event)"
        >
          <option value="">未设置</option>
          <option v-for="option in options" :key="`dirty-${option.modelConfigId}`" :value="String(option.modelConfigId)">
            {{ formatOptionLabel(option) }}
          </option>
        </select>
      </label>

      <div v-if="successMessage" class="state-text success-text">{{ successMessage }}</div>
      <div v-else-if="!options.length" class="state-text">暂无可选模型配置，请先在工作台完成配置。</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ProfileModelConfigOption } from '@/composables/profile/useProfileSettings'

const {
  loading,
  saving,
  error,
  successMessage,
  options,
  mainAgentModelConfigId,
  dirtyWorkAgentModelConfigId,
} = defineProps<{
  loading: boolean
  saving: boolean
  error: string
  successMessage: string
  options: ProfileModelConfigOption[]
  mainAgentModelConfigId: string | null
  dirtyWorkAgentModelConfigId: string | null
}>()

const emit = defineEmits<{
  (event: 'update:main-agent-model-config-id', value: string | null): void
  (event: 'update:dirty-work-agent-model-config-id', value: string | null): void
  (event: 'save'): void
}>()

const toSelectValue = (value: string | null) => (value == null ? '' : value)

const parseSelectValue = (event: Event) => {
  const target = event.target as HTMLSelectElement | null
  if (!target) {
    return null
  }
  const nextValue = target.value.trim()
  return nextValue || null
}

const handleSelectChange = (field: 'main-agent' | 'dirty-work-agent', event: Event) => {
  const value = parseSelectValue(event)
  if (field === 'main-agent') {
    emit('update:main-agent-model-config-id', value)
    return
  }
  emit('update:dirty-work-agent-model-config-id', value)
}

const formatOptionLabel = (option: ProfileModelConfigOption) => {
  const providerSegment = option.providerName ? `${option.providerName} / ` : ''
  const sourceSegment = option.keySourceType ? ` · ${option.keySourceType}` : ''
  return `${providerSegment}${option.modelName}${sourceSegment}`
}
</script>

<style lang="less" scoped>
.settings-section {
  padding: 20px 24px;
  background: rgba(17, 24, 39, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--xuan-paper);
  letter-spacing: 0.12em;
}

.section-desc {
  margin-top: 6px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.editor-body {
  display: grid;
  gap: 14px;
}

.field-block {
  display: grid;
  gap: 6px;
}

.field-label {
  font-size: 0.84rem;
  color: var(--text-primary);
}

.field-select {
  padding: 8px 10px;
  background: rgba(11, 17, 32, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  color: var(--text-primary);
}

.save-btn {
  padding: 6px 14px;
  min-width: 96px;
  font-size: 0.82rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.18);
  border-radius: 6px;
  cursor: pointer;
}

.save-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.state-text {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.success-text {
  color: #7ee787;
}

.error-text {
  color: #ff7b72;
}
</style>
