<template>
  <form class="settings-section" @submit.prevent="emit('save')">
    <header>
      <h1>AI 与上下文</h1>
      <p>为当前作品选择创作、筛选和语义检索模型。</p>
    </header>
    <ModelSelect v-model="ai.creativeModelConfigId" label="创作模型" description="负责 Agent 决策、正文生成与改写" :options="chatModels" />
    <ModelSelect v-model="ai.routerModelConfigId" label="上下文筛选模型" description="负责筛选与当前写作相关的上下文" :options="chatModels" />
    <ModelSelect v-model="ai.embeddingModelConfigId" label="Embedding 模型" description="负责构建向量索引与语义检索" :options="embeddingModels" />
    <label class="rag-toggle">
      <input v-model="ai.ragEnabled" type="checkbox" @change="normalizeRoutingMode" />
      <span><strong>启用 RAG</strong><small>开启增量向量同步和语义检索；关闭后保留已有索引。</small></span>
    </label>
    <fieldset class="routing-field">
      <legend>上下文路由模式</legend>
      <label
        v-for="mode in routingModes"
        :key="mode.value"
        :class="{ disabled: mode.requiresIndex && (!ai.ragEnabled || !retrievalAvailable) }"
      >
        <input
          v-model="ai.storyBibleRoutingMode"
          type="radio"
          :value="mode.value"
          :disabled="mode.requiresIndex && (!ai.ragEnabled || !retrievalAvailable)"
        />
        <span
          ><strong>{{ mode.label }}</strong
          ><small>{{ mode.description }}</small></span
        >
      </label>
    </fieldset>
    <p v-if="!ai.ragEnabled" class="inline-notice"><InfoCircleOutlined />RAG 已关闭，Agent 仍可主动调用读取与检索工具。</p>
    <p v-else-if="!retrievalAvailable" class="inline-notice"><InfoCircleOutlined />索引未就绪，可使用 Agent 自主读取或智能筛选。</p>
    <SaveFeedback :saving="saving" :error="error" :success="success" />
    <div class="section-actions">
      <button class="primary-button" type="submit" :disabled="busy"><SaveOutlined />保存 AI 设置</button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { InfoCircleOutlined, SaveOutlined } from '@ant-design/icons-vue'
import ModelSelect from './ModelSelect.vue'
import SaveFeedback from './SaveFeedback.vue'
import type { ModelOption, ProjectAiSettings, RoutingMode } from '@/features/project-settings/useProjectSettings'

const emit = defineEmits<{ save: [] }>()
const props = defineProps<{
  ai: ProjectAiSettings
  chatModels: ModelOption[]
  embeddingModels: ModelOption[]
  retrievalAvailable: boolean
  saving: boolean
  busy: boolean
  error: string
  success: string
}>()

const normalizeRoutingMode = () => {
  if (
    !props.ai.ragEnabled &&
    (props.ai.storyBibleRoutingMode === 'RETRIEVAL' || props.ai.storyBibleRoutingMode === 'RETRIEVAL_THEN_LLM')
  ) {
    props.ai.storyBibleRoutingMode = 'AGENT_DRIVEN'
  }
}
const routingModes: Array<{ value: RoutingMode; label: string; description: string; requiresIndex: boolean }> = [
  { value: 'AGENT_DRIVEN', label: 'Agent 自主读取', description: '不预装设定，由 Agent 按任务自行检查和读取。', requiresIndex: false },
  { value: 'LLM_SELECTOR', label: '智能筛选', description: '由筛选模型直接选择相关设定。', requiresIndex: false },
  { value: 'RETRIEVAL', label: '语义检索', description: '只使用向量索引，速度更快。', requiresIndex: true },
  { value: 'RETRIEVAL_THEN_LLM', label: '混合筛选', description: '先检索再由筛选模型二次选择。', requiresIndex: true },
]
</script>

<style scoped>
.rag-toggle { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: 10px; padding: 12px 0; border-block: 1px solid var(--border-subtle); cursor: pointer; }
.rag-toggle input { width: 16px; height: 16px; margin-top: 2px; accent-color: var(--accent); }
.rag-toggle span { display: grid; gap: 3px; }
.rag-toggle strong { color: var(--text-primary); font-size: 13px; }
.rag-toggle small { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
</style>
