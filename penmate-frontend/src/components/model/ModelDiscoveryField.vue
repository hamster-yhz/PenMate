<template>
  <div class="model-discovery-field">
    <label class="field-label" :for="inputId">真实模型 ID</label>
    <div class="model-id-control">
      <input
        :id="inputId"
        :value="modelValue"
        type="text"
        maxlength="120"
        placeholder="例如：gpt-5"
        required
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value.trim())"
      />
      <button type="button" class="discover-button" :disabled="discovering || !providerId" @click="discover">
        <LoadingOutlined v-if="discovering" />
        <RadarChartOutlined v-else />
        {{ discovering ? '探测中' : '探测模型' }}
      </button>
    </div>

    <div v-if="discoveryError" class="discovery-message error" role="alert">
      <ExclamationCircleOutlined />
      <span>{{ discoveryError }}</span>
      <button type="button" @click="discover">重试</button>
    </div>
    <div v-else-if="selectedFromDiscovery" class="discovery-message success">
      <CheckCircleOutlined />
      <span>已从站点选择 {{ selectedFromDiscovery }}</span>
    </div>

    <section v-if="resultsOpen" class="discovery-results" aria-label="站点模型">
      <header>
        <strong>可用模型</strong>
        <span>{{ filteredModels.length === models.length ? `${models.length} 个` : `${filteredModels.length} / ${models.length}` }}</span>
        <button type="button" class="close-button" aria-label="关闭模型列表" @click="closeResults"><CloseOutlined /></button>
      </header>
      <label v-if="models.length > 6" class="model-search">
        <SearchOutlined />
        <input v-model="query" type="search" placeholder="搜索模型 ID" />
      </label>
      <div v-if="filteredModels.length" class="model-options" role="listbox" :aria-label="`${models.length} 个可用模型`">
        <button
          v-for="model in filteredModels"
          :key="model"
          type="button"
          role="option"
          :aria-selected="model === modelValue"
          :class="{ selected: model === modelValue }"
          @click="selectModel(model)"
        >
          <span>{{ model }}</span>
          <CheckOutlined v-if="model === modelValue" />
        </button>
      </div>
      <div v-else class="empty-results">
        {{ models.length ? '没有匹配的模型' : '站点没有返回可用模型' }}
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import {
  CheckCircleOutlined,
  CheckOutlined,
  CloseOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  RadarChartOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { useModelDiscovery } from '@/features/model-discovery/useModelDiscovery'

const props = defineProps<{
  modelValue: string
  providerId: string
  modelType: 'CHAT' | 'EMBEDDING'
  baseUrl: string
  apiKey: string
  modelConfigId?: string
  systemScope?: boolean
}>()
const inputId = useId()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [value: string]
}>()
const {
  discovering,
  discoveryError,
  models,
  query,
  resultsOpen,
  selectedFromDiscovery,
  filteredModels,
  discover,
  selectModel,
  closeResults,
} = useModelDiscovery({
  providerId: () => props.providerId,
  modelType: () => props.modelType,
  baseUrl: () => props.baseUrl,
  apiKey: () => props.apiKey,
  modelConfigId: () => props.modelConfigId,
  systemScope: () => props.systemScope,
}, (model) => {
  emit('update:modelValue', model)
  emit('select', model)
})
</script>

<style scoped>
.model-discovery-field { display: grid; gap: 6px; font-size: 12px; }
.field-label { font-weight: 600; }
.model-id-control { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 7px; }
.model-id-control input { min-width: 0; }
.discover-button { display: inline-flex; min-height: 38px; align-items: center; gap: 5px; padding: 0 10px; color: var(--text-secondary); background: var(--bg-subtle); border: 1px solid var(--border-strong); border-radius: 4px; white-space: nowrap; cursor: pointer; }
.discover-button:disabled { cursor: not-allowed; opacity: .55; }
.discovery-message { display: flex; min-width: 0; align-items: center; gap: 6px; padding: 7px 9px; border-radius: 4px; }
.discovery-message span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.discovery-message.error { color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); }
.discovery-message.success { color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-border, var(--border-subtle)); }
.discovery-message button { margin-left: auto; padding: 0; color: inherit; background: transparent; border: 0; cursor: pointer; }
.discovery-results { overflow: hidden; background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; box-shadow: var(--shadow-xs); }
.discovery-results > header { display: grid; grid-template-columns: 1fr auto auto; min-height: 38px; align-items: center; gap: 9px; padding: 0 9px 0 11px; background: var(--bg-subtle); border-bottom: 1px solid var(--border-subtle); }
.discovery-results header span { color: var(--text-muted); }
.close-button { display: grid; width: 28px; min-height: 28px; padding: 0; color: var(--text-muted); background: transparent; border: 0; place-items: center; cursor: pointer; }
.model-search { display: flex !important; grid-template-columns: none !important; min-height: 38px; align-items: center; gap: 7px !important; padding: 0 10px; border-bottom: 1px solid var(--border-subtle); }
.model-search input { min-height: 36px !important; padding: 0 !important; background: transparent !important; border: 0 !important; outline: 0; }
.model-options { max-height: 232px; overflow: auto; padding: 4px; }
.model-options button { display: grid; width: 100%; min-height: 34px; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 8px; padding: 0 8px; color: var(--text-secondary); text-align: left; background: transparent; border: 0; border-radius: 3px; cursor: pointer; }
.model-options button:hover { background: var(--bg-subtle); }
.model-options button.selected { color: var(--accent); background: var(--accent-soft); }
.model-options button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-results { display: grid; min-height: 76px; color: var(--text-muted); place-items: center; }
@media (max-width: 420px) { .model-id-control { grid-template-columns: 1fr; }.discover-button { justify-content: center; } }
</style>
