<script setup lang="ts">
import {
  CloseOutlined, CompressOutlined, DeleteOutlined, SafetyCertificateOutlined,
  SendOutlined, SettingOutlined, StopOutlined, ToolOutlined,
} from '@ant-design/icons-vue'
import { computed, nextTick, ref, watch } from 'vue'
import type { WorkbenchSkillCatalogItem } from '@/components/workbench/workbenchTypes'
import type { ChapterContextRange } from '@/composables/workbench/workbenchOutline'
import type { AgentSafetyMode } from '@/entities/agent/model'
type QueuedRequest = {
  requestId: string
  type: 'MESSAGE' | 'COMPRESS'
  status: 'PENDING' | 'EXECUTING'
  payloadJson?: string | null
}
type ContextUsage = {
  usedTokens: number
  maxContextTokens: number | null
  usageRatio: number | null
  usageSource?: 'PROVIDER_USAGE' | 'ESTIMATE'
  contextCapacitySource?: 'MANUAL' | 'PROVIDER' | 'CATALOG' | 'FALLBACK'
}

const props = withDefaults(defineProps<{
  modelValue?: string
  isGenerating?: boolean
  canCancelRun?: boolean
  isCancelling?: boolean
  currentModelName?: string
  activePlugins?: string[]
  attachedChapterRanges?: ChapterContextRange[]
  selectedText?: string
  boundStyleName?: string
  skillCatalog?: WorkbenchSkillCatalogItem[]
  activeSkills?: string[]
  skillCatalogLoading?: boolean
  queuedRequest?: QueuedRequest | null
  contextUsage?: ContextUsage | null
  safetyMode?: AgentSafetyMode
  safetyModeSaving?: boolean
}>(), {
  modelValue: '', isGenerating: false, canCancelRun: false, isCancelling: false,
  currentModelName: '', activePlugins: () => [], attachedChapterRanges: () => [], selectedText: '', boundStyleName: '',
  skillCatalog: () => [], activeSkills: () => [], skillCatalogLoading: false,
  queuedRequest: null, contextUsage: null, safetyMode: 'STANDARD', safetyModeSaving: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  cancel: []
  'open-model-settings': []
  'clear-selected-text': []
  'remove-chapters': [chapterIds: string[]]
  'drop-chapters': [chapterIds: string[]]
  'remove-skill': [name: string]
  'compress-context': []
  'withdraw-queued-request': []
  'update:safety-mode': [mode: AgentSafetyMode]
}>()

const textarea = ref<HTMLTextAreaElement | null>(null)
const slashMenuOpen = ref(false)
const slashRange = ref<{ start: number; end: number } | null>(null)
const slashSource = ref('')
const chapterDragOver = ref(false)
const sendDisabled = computed(() => !props.modelValue.trim() || !props.currentModelName)
const selectedPreview = computed(() => props.selectedText.trim().replace(/\s+/g, ' ').slice(0, 28))
const catalogSkillSet = computed(() => new Set(props.skillCatalog.map((skill) => skill.name)))
const usageLabel = computed(() => {
  const usage = props.contextUsage
  if (!usage) return '上下文占用 --'
  const percent = usage.usageRatio == null ? '--' : `${Math.round(usage.usageRatio * 100)}%`
  const maximum = usage.maxContextTokens == null ? '--' : usage.maxContextTokens.toLocaleString()
  return `上下文占用 ${percent} · ${usage.usedTokens.toLocaleString()} / ${maximum} Token`
})
const usageTitle = computed(() => {
  const usage = props.contextUsage
  if (!usage) return '尚未计算当前会话的上下文占用'
  const usageSource = usage.usageSource === 'PROVIDER_USAGE' ? '供应商返回的 Token 计数' : '本地估算的 Token 计数'
  const capacitySource = {
    MANUAL: '手工设置的模型容量',
    PROVIDER: '供应商模型接口返回的容量',
    CATALOG: 'PenMate 能力目录中的容量',
    FALLBACK: '未识别模型的保守默认容量',
  }[usage.contextCapacitySource || 'FALLBACK']
  return `${usageSource}；${capacitySource}`
})
const queuedLabel = computed(() => props.queuedRequest?.type === 'COMPRESS' ? '压缩上下文' : '发送已登记消息')

const resize = async () => {
  await nextTick()
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${Math.min(180, Math.max(48, textarea.value.scrollHeight))}px`
}
watch(() => props.modelValue, resize, { immediate: true })

const updateSlashMenu = (value: string, cursor: number | null) => {
  const prefix = value.slice(0, cursor ?? value.length)
  const match = prefix.match(/(?:^|\s)\/([^\s]*)$/)
  if (!match) {
    slashMenuOpen.value = false
    slashRange.value = null
    return
  }
  const token = match[0].trimStart()
  slashRange.value = { start: prefix.length - token.length, end: prefix.length }
  slashSource.value = value
  slashMenuOpen.value = true
}

const updateValue = (event: Event) => {
  const target = event.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
  updateSlashMenu(target.value, target.selectionStart)
  void resize()
}

const selectCompression = async () => {
  const range = slashRange.value
  if (range) {
    const source = slashSource.value || props.modelValue
    emit('update:modelValue', source.slice(0, range.start) + source.slice(range.end))
  }
  slashMenuOpen.value = false
  slashRange.value = null
  emit('compress-context')
  await nextTick()
  textarea.value?.focus()
}

const send = () => {
  if (!sendDisabled.value) emit('send')
}

const updateSafetyMode = (event: Event) => {
  emit('update:safety-mode', (event.target as HTMLSelectElement).value as AgentSafetyMode)
}

const hasChapterPayload = (event: DragEvent) => Array.from(event.dataTransfer?.types || [])
  .includes('application/x-penmate-chat-chapters')

const handleChapterDragOver = (event: DragEvent) => {
  if (!hasChapterPayload(event)) return
  event.preventDefault()
  chapterDragOver.value = true
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}

const handleChapterDrop = (event: DragEvent) => {
  chapterDragOver.value = false
  const raw = event.dataTransfer?.getData('application/x-penmate-chat-chapters')
  if (!raw) return
  event.preventDefault()
  try {
    const payload = JSON.parse(raw) as { chapterIds?: unknown[] }
    const chapterIds = [...new Set((payload.chapterIds || []).map(String).map((item) => item.trim()).filter(Boolean))]
    if (chapterIds.length) emit('drop-chapters', chapterIds)
  } catch {
    // Ignore drag payloads from outside PenMate.
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (slashMenuOpen.value) {
    if (event.key === 'Escape') {
      event.preventDefault()
      slashMenuOpen.value = false
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      void selectCompression()
      return
    }
  }
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  send()
}
</script>

<template>
  <!-- Drag-and-drop is an optional enhancement; chapter context remains removable with keyboard buttons. -->
  <!-- eslint-disable-next-line vuejs-accessibility/no-static-element-interactions -->
  <footer
    class="composer workbench-composer" :class="{ 'chapter-drag-over': chapterDragOver }"
    role="group" aria-label="对话输入与上下文"
    data-testid="chat-composer" @dragover="handleChapterDragOver" @dragleave="chapterDragOver = false"
    @drop="handleChapterDrop"
  >
    <div v-if="!currentModelName" class="model-warning theme-warning" data-testid="model-warning">
      <span>当前未选择模型，发送前需要选择默认主模型</span>
      <button type="button" data-testid="open-model-settings" @click="emit('open-model-settings')"><SettingOutlined /> 模型设置</button>
    </div>
    <div class="context-row" aria-label="本次对话上下文">
      <span class="context-chip usage-chip" :title="usageTitle" aria-live="polite">{{ usageLabel }}</span>
      <label class="context-chip safety-mode" title="安全模式会在创建下一次运行时生效">
        <SafetyCertificateOutlined /><b>安全</b>
        <select :value="safetyMode" :disabled="safetyModeSaving" aria-label="Agent 安全模式" @change="updateSafetyMode">
          <option value="STRICT">严格</option>
          <option value="STANDARD">标准</option>
          <option value="AUTONOMOUS">自主</option>
          <option value="FULL_AUTHORITY">完全授权</option>
        </select>
      </label>
      <span class="context-chip"><b>模型</b><span data-testid="current-model-value">{{ currentModelName || '未选择模型' }}</span></span>
      <span v-for="range in attachedChapterRanges" :key="range.key" class="context-chip removable">
        <b>章节</b>{{ range.label }}
        <button type="button" :title="`移除 ${range.label}`" :aria-label="`移除 ${range.label}`" @click="emit('remove-chapters', range.chapterIds)"><CloseOutlined /></button>
      </span>
      <span v-if="boundStyleName" class="context-chip"><b>文风</b>{{ boundStyleName }}</span>
      <span v-if="selectedPreview" class="context-chip removable"><b>选中文本</b>{{ selectedPreview }}<button type="button" title="清除选中文本" aria-label="清除选中文本" @click="emit('clear-selected-text')"><CloseOutlined /></button></span>
      <span v-for="plugin in activePlugins" :key="plugin" class="context-chip"><b>插件</b>{{ plugin }}</span>
    </div>
    <div v-if="activeSkills.length" class="skill-tags" aria-label="已激活 Skill">
      <span v-for="skill in activeSkills" :key="skill" class="skill-tag" :class="{ unavailable: !catalogSkillSet.has(skill) }">
        <ToolOutlined /><span>{{ skill }}</span>
        <button type="button" :aria-label="`移除 ${skill}`" :title="`移除 ${skill}`" @click="emit('remove-skill', skill)"><CloseOutlined /></button>
      </span>
    </div>
    <div class="composer-box">
      <div v-if="queuedRequest" class="queued-request" role="status">
        <span><b>{{ queuedLabel }}</b><small>{{ queuedRequest.status === 'EXECUTING' ? '执行中' : '等待当前运行结束' }}</small></span>
        <button v-if="queuedRequest.status === 'PENDING'" type="button" title="撤回待执行请求" aria-label="撤回待执行请求" @click="emit('withdraw-queued-request')"><DeleteOutlined /></button>
      </div>
      <div v-if="slashMenuOpen" class="slash-menu" role="listbox" aria-label="命令候选">
        <button type="button" role="option" aria-selected="true" @mousedown.prevent="selectCompression">
          <CompressOutlined /><span><strong>压缩上下文</strong><small>当前运行结束后执行</small></span>
        </button>
      </div>
      <textarea
        ref="textarea" class="composer-textarea" :value="modelValue" rows="1" aria-label="发送给 Agent 的消息"
        data-testid="chat-input" :placeholder="canCancelRun ? '输入下一条消息，按 Enter 登记' : '描述你希望 Agent 完成的写作任务'"
        @input="updateValue" @keydown="handleKeydown" @click="updateSlashMenu(modelValue, textarea?.selectionStart ?? null)"
        @blur="slashMenuOpen = false"
      />
      <div class="composer-footer">
        <span class="composer-hint">Enter 发送 · Shift+Enter 换行</span>
        <div class="composer-actions">
          <button v-if="canCancelRun" type="button" class="icon-button stop" data-testid="chat-cancel" :disabled="isCancelling" title="停止运行" aria-label="停止运行" @click="emit('cancel')"><StopOutlined /></button>
          <button v-else type="button" class="send-button btn-send" data-testid="chat-send" :disabled="sendDisabled" @click="send"><SendOutlined /><span>发送</span></button>
        </div>
      </div>
    </div>
  </footer>
</template>

<style scoped lang="less">
.composer { flex: 0 0 auto; display: grid; gap: 8px; padding: 10px 14px 14px; border-top: 1px solid var(--border-subtle); background: var(--bg-surface); }
.composer.chapter-drag-over { background: var(--accent-soft); box-shadow: inset 0 2px 0 var(--accent); }
.model-warning { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 9px; border-left: 2px solid var(--warning); background: var(--warning-soft); color: var(--warning); font-size: 12px; }
.model-warning button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: var(--warning); cursor: pointer; white-space: nowrap; }
.context-row { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; min-width: 0; }
.context-chip { flex: 0 1 auto; max-width: 100%; min-height: 24px; display: inline-flex; align-items: center; gap: 5px; padding: 3px 7px; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); background: var(--bg-subtle); color: var(--text-muted); font-size: 10px; overflow-wrap: anywhere; }
.usage-chip { font-variant-numeric: tabular-nums; }
.context-chip b { color: var(--accent); font-weight: 600; }
.context-chip button { flex: 0 0 auto; width: 18px; height: 18px; display: grid; place-items: center; padding: 0; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
.safety-mode select { max-width: 74px; padding: 0; color: var(--text-secondary); font: inherit; background: transparent; border: 0; outline: 0; cursor: pointer; }
.skill-tags { display: flex; gap: 5px; overflow-x: auto; scrollbar-width: none; }
.skill-tag { flex: 0 0 auto; min-height: 26px; display: inline-flex; align-items: center; gap: 5px; padding: 3px 4px 3px 7px; border: 1px solid var(--accent-border); border-radius: var(--radius-sm); background: var(--accent-soft); color: var(--text-secondary); font-size: 11px; }
.skill-tag.unavailable { border-color: var(--warning); background: var(--warning-soft); color: var(--warning); }
.skill-tag button { width: 20px; height: 20px; display: grid; place-items: center; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; }
.composer-box { position: relative; border: 1px solid var(--border-strong); border-radius: var(--radius-md); background: var(--bg-editor); }
.composer-box:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); }
.queued-request { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 8px 6px 11px; border-bottom: 1px solid var(--border-subtle); background: var(--bg-subtle); }
.queued-request span { min-width: 0; display: grid; gap: 1px; color: var(--text-secondary); font-size: 11px; }
.queued-request b { font-weight: 650; }
.queued-request small { color: var(--text-muted); font-size: 10px; }
.queued-request button { width: 28px; height: 28px; display: grid; place-items: center; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
.slash-menu { position: absolute; z-index: 30; left: 6px; right: 6px; bottom: calc(100% + 6px); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--bg-elevated); box-shadow: var(--shadow-md); }
.slash-menu button { width: 100%; min-height: 48px; display: grid; grid-template-columns: 20px minmax(0, 1fr); align-items: center; gap: 9px; padding: 7px 10px; border: 0; background: var(--bg-subtle); color: var(--text-secondary); text-align: left; cursor: pointer; }
.slash-menu button > span { min-width: 0; display: grid; gap: 2px; }
.slash-menu strong { font-size: 12px; font-weight: 650; }
.slash-menu small { color: var(--text-muted); font-size: 11px; }
textarea { width: 100%; min-height: 48px; max-height: 180px; resize: none; display: block; padding: 11px 12px 4px; border: 0; outline: 0; background: transparent; color: var(--text-primary); font: inherit; line-height: 1.55; overflow-y: auto; }
textarea::placeholder { color: var(--text-muted); }
.composer-footer { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 3px 5px 5px 11px; }
.composer-hint { color: var(--text-muted); font-size: 10px; }
.composer-actions { display: flex; gap: 5px; }
.icon-button, .send-button { height: 32px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid transparent; cursor: pointer; }
.icon-button { width: 32px; background: transparent; color: var(--text-muted); }
.stop { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 32%, var(--border-subtle)); background: var(--danger-soft); }
.send-button { gap: 6px; padding: 0 12px; border-color: var(--accent); border-radius: var(--radius-sm); background: var(--accent); color: var(--text-inverse); }
.send-button:hover:not(:disabled) { background: var(--accent-hover); }
.send-button:disabled, .icon-button:disabled { cursor: not-allowed; opacity: 0.42; }
@media (max-width: 480px) { .composer { padding-inline: 10px; } .composer-hint { display: none; } }
</style>
