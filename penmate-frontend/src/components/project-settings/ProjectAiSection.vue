<template>
  <form class="settings-section" @submit.prevent="emit('save')">
    <header><h1>AI 与上下文</h1><p>未指定时继承账号默认模型。</p></header>
    <ModelSelect v-model="ai.creativeModelConfigId" label="创作模型" :options="chatModels" :inherit-label="inheritedCreativeLabel" />
    <ModelSelect v-model="ai.routerModelConfigId" label="上下文筛选模型" :options="chatModels" :inherit-label="inheritedRouterLabel" />
    <ModelSelect v-model="ai.embeddingModelConfigId" label="Embedding 模型" :options="embeddingModels" :inherit-label="inheritedEmbeddingLabel" />
    <fieldset class="routing-field">
      <legend>上下文路由模式</legend>
      <label v-for="mode in routingModes" :key="mode.value" :class="{ disabled: mode.requiresIndex && !retrievalAvailable }">
        <input v-model="ai.storyBibleRoutingMode" type="radio" :value="mode.value" :disabled="mode.requiresIndex && !retrievalAvailable" />
        <span><strong>{{ mode.label }}</strong><small>{{ mode.description }}</small></span>
      </label>
    </fieldset>
    <p v-if="!retrievalAvailable" class="inline-notice"><InfoCircleOutlined />索引未就绪，只能使用智能筛选。</p>
    <SaveFeedback :saving="saving" :error="error" :success="success" />
    <div class="section-actions"><button class="primary-button" type="submit" :disabled="busy"><SaveOutlined />保存 AI 设置</button></div>
  </form>
</template>

<script setup lang="ts">
import { InfoCircleOutlined, SaveOutlined } from '@ant-design/icons-vue'
import ModelSelect from './ModelSelect.vue'
import SaveFeedback from './SaveFeedback.vue'
import type { ModelOption, ProjectAiSettings, RoutingMode } from '@/features/project-settings/useProjectSettings'

defineProps<{
  ai: ProjectAiSettings
  chatModels: ModelOption[]
  embeddingModels: ModelOption[]
  inheritedCreativeLabel: string
  inheritedRouterLabel: string
  inheritedEmbeddingLabel: string
  retrievalAvailable: boolean
  saving: boolean
  busy: boolean
  error: string
  success: string
}>()

const emit = defineEmits<{ save: [] }>()
const routingModes: Array<{ value: RoutingMode; label: string; description: string; requiresIndex: boolean }> = [
  { value: 'LLM_SELECTOR', label: '智能筛选', description: '由筛选模型直接选择相关设定。', requiresIndex: false },
  { value: 'RETRIEVAL', label: '语义检索', description: '只使用向量索引，速度更快。', requiresIndex: true },
  { value: 'RETRIEVAL_THEN_LLM', label: '混合筛选', description: '先检索再由筛选模型二次选择。', requiresIndex: true },
]
</script>
