<template>
  <section class="settings-section">
    <header>
      <h1>上下文索引</h1>
      <p>索引用于检索故事设定、正文和知识文档。</p>
    </header>
    <dl class="status-list">
      <div>
        <dt>RAG</dt>
        <dd>{{ ai.ragEnabled ? '已开启' : '已关闭' }}</dd>
      </div>
      <div>
        <dt>当前状态</dt>
        <dd>
          <span class="status-badge" :class="index.status.toLowerCase()">{{ indexStatusLabel }}</span>
        </dd>
      </div>
      <div>
        <dt>Embedding 模型</dt>
        <dd>{{ selectedEmbeddingName }}</dd>
      </div>
      <div>
        <dt>上次完成</dt>
        <dd>{{ lastCompletedLabel }}</dd>
      </div>
      <div v-if="rebuilding" class="index-progress-row">
        <dt>重建进度</dt>
        <dd>
          <div class="index-progress-heading">
            <span>{{ progressLabel }}</span>
            <span v-if="hasDeterminateProgress">{{ progressPercent }}%</span>
          </div>
          <progress
            class="index-progress"
            :value="hasDeterminateProgress ? boundedProgressCurrent : undefined"
            :max="hasDeterminateProgress ? index.progressTotal : undefined"
            :aria-label="progressLabel"
          ></progress>
        </dd>
      </div>
      <div v-if="rebuilding && index.progressMessage">
        <dt>当前步骤</dt>
        <dd>{{ index.progressMessage }}</dd>
      </div>
      <div v-if="index.lastErrorMessage">
        <dt>失败原因</dt>
        <dd class="danger-text">{{ index.lastErrorMessage }}</dd>
      </div>
    </dl>
    <SaveFeedback
      :saving="rebuilding"
      :saving-text="cancelling ? '正在停止索引重建…' : '正在重建索引…'"
      :error="error"
      :success="success"
    />
    <div class="section-actions">
      <button
        v-if="rebuilding"
        class="stop-rebuild-button"
        type="button"
        :disabled="cancelling || !index.rebuildJobId"
        @click="emit('stop')"
      >
        <LoadingOutlined v-if="cancelling" />
        <StopOutlined v-else />
        {{ cancelling ? '正在停止' : '停止重建' }}
      </button>
      <button v-else class="primary-button" type="button" :disabled="!canRebuild" @click="emit('rebuild')">
        <SyncOutlined />重建索引
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { LoadingOutlined, StopOutlined, SyncOutlined } from '@ant-design/icons-vue'
import SaveFeedback from './SaveFeedback.vue'
import type { ModelOption, ProjectAiSettings, ProjectIndexState } from '@/features/project-settings/useProjectSettings'

const props = defineProps<{
  ai: ProjectAiSettings
  index: ProjectIndexState
  embeddingModels: ModelOption[]
  canRebuild: boolean
  rebuilding: boolean
  cancelling: boolean
  error: string
  success: string
}>()

const emit = defineEmits<{ rebuild: []; stop: [] }>()
const hasDeterminateProgress = computed(() => props.index.progressTotal > 0)
const boundedProgressCurrent = computed(() =>
  Math.min(Math.max(props.index.progressCurrent, 0), props.index.progressTotal),
)
const progressPercent = computed(() =>
  hasDeterminateProgress.value ? Math.round((boundedProgressCurrent.value / props.index.progressTotal) * 100) : 0,
)
const progressLabel = computed(() => {
  if (props.cancelling) return '正在停止'
  if (!hasDeterminateProgress.value) return props.index.status === 'QUEUED' ? '等待任务开始' : '正在准备索引'
  return `${boundedProgressCurrent.value}/${props.index.progressTotal}`
})
const indexStatusLabel = computed(
  () =>
    ({
      READY: '可用',
      ACTIVE: '可用',
      QUEUED: '等待重建',
      BUILDING: '正在重建',
      REINDEX_REQUIRED: '需要重建',
      UNBOUND: '未配置',
      FAILED: '失败',
      CANCELLING: '正在停止',
      CANCELLED: '已停止',
    })[props.index.status] || props.index.status,
)
const selectedEmbeddingName = computed(
  () => props.embeddingModels.find((model) => model.id === props.ai.embeddingModelConfigId)?.label || '未配置',
)
const lastCompletedLabel = computed(() => {
  if (!props.index.lastCompletedAt) return '尚未完成'
  const value = new Date(props.index.lastCompletedAt)
  return Number.isNaN(value.getTime()) ? props.index.lastCompletedAt : value.toLocaleString()
})
</script>
