<template>
  <section class="settings-section">
    <header><h1>上下文索引</h1><p>索引用于从 Story Bible 中检索相关设定。</p></header>
    <dl class="status-list">
      <div><dt>当前状态</dt><dd><span class="status-badge" :class="index.status.toLowerCase()">{{ indexStatusLabel }}</span></dd></div>
      <div><dt>Embedding 模型</dt><dd>{{ selectedEmbeddingName }}</dd></div>
      <div><dt>上次完成</dt><dd>{{ index.lastCompletedAt || '尚未完成' }}</dd></div>
      <div v-if="index.lastErrorMessage"><dt>失败原因</dt><dd class="danger-text">{{ index.lastErrorMessage }}</dd></div>
    </dl>
    <SaveFeedback :saving="rebuilding" :error="error" :success="success" />
    <div class="section-actions"><button class="primary-button" type="button" :disabled="rebuilding || !ai.embeddingModelConfigId" @click="emit('rebuild')"><SyncOutlined />重建索引</button></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SyncOutlined } from '@ant-design/icons-vue'
import SaveFeedback from './SaveFeedback.vue'
import type { ModelOption, ProjectAiSettings, ProjectIndexState } from '@/features/project-settings/useProjectSettings'

const props = defineProps<{
  ai: ProjectAiSettings
  index: ProjectIndexState
  embeddingModels: ModelOption[]
  rebuilding: boolean
  error: string
  success: string
}>()

const emit = defineEmits<{ rebuild: [] }>()
const indexStatusLabel = computed(() => ({ READY: '可用', ACTIVE: '可用', QUEUED: '等待重建', BUILDING: '正在重建', REINDEX_REQUIRED: '需要重建', UNBOUND: '未配置', FAILED: '失败' }[props.index.status] || props.index.status))
const selectedEmbeddingName = computed(() => props.embeddingModels.find((model) => model.id === props.ai.embeddingModelConfigId)?.label || '继承账号默认')
</script>
