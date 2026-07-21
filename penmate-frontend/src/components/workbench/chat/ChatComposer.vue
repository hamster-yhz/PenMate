<script setup lang="ts">
import { CloseOutlined, RedoOutlined, SendOutlined, SettingOutlined, StopOutlined } from '@ant-design/icons-vue'
import { computed, nextTick, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string; isGenerating?: boolean; canCancelRun?: boolean; isCancelling?: boolean;
  canRetryRun?: boolean; isRetrying?: boolean; currentModelName?: string; activePlugins?: string[];
  activeChapterTitle?: string; selectedText?: string; boundStyleName?: string;
}>(), {
  modelValue: '', isGenerating: false, canCancelRun: false, isCancelling: false, canRetryRun: false,
  isRetrying: false, currentModelName: '', activePlugins: () => [], activeChapterTitle: '', selectedText: '', boundStyleName: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]; send: []; cancel: []; retry: []; 'open-model-settings': []; 'clear-selected-text': [];
}>()
const textarea = ref<HTMLTextAreaElement | null>(null)
const sendDisabled = computed(() => !props.modelValue.trim() || props.isGenerating || !props.currentModelName)
const selectedPreview = computed(() => props.selectedText.trim().replace(/\s+/g, ' ').slice(0, 28))

const resize = async () => {
  await nextTick()
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${Math.min(180, Math.max(48, textarea.value.scrollHeight))}px`
}
watch(() => props.modelValue, resize, { immediate: true })

const updateValue = (event: Event) => { emit('update:modelValue', (event.target as HTMLTextAreaElement).value); void resize() }
const send = () => { if (!sendDisabled.value) emit('send') }
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  send()
}
</script>

<template>
  <footer class="composer workbench-composer" data-testid="chat-composer">
    <div v-if="!currentModelName" class="model-warning theme-warning" data-testid="model-warning">
      <span>当前未选择模型，发送前需要选择默认主模型</span>
      <button type="button" data-testid="open-model-settings" @click="emit('open-model-settings')"><SettingOutlined /> 模型设置</button>
    </div>
    <div class="context-row" aria-label="本次对话上下文">
      <span class="context-chip"><b>模型</b><span data-testid="current-model-value">{{ currentModelName || '未选择模型' }}</span></span>
      <span v-if="activeChapterTitle" class="context-chip"><b>章节</b>{{ activeChapterTitle }}</span>
      <span v-if="boundStyleName" class="context-chip"><b>文风</b>{{ boundStyleName }}</span>
      <span v-if="selectedPreview" class="context-chip removable"><b>选中文本</b>{{ selectedPreview }}<button type="button" title="清除选中文本" aria-label="清除选中文本" @click="emit('clear-selected-text')"><CloseOutlined /></button></span>
      <span v-for="plugin in activePlugins" :key="plugin" class="context-chip"><b>插件</b>{{ plugin }}</span>
    </div>
    <div class="composer-box">
      <textarea ref="textarea" class="composer-textarea" :value="modelValue" rows="1" aria-label="发送给 Agent 的消息" data-testid="chat-input" placeholder="描述你希望 Agent 完成的写作任务" @input="updateValue" @keydown="handleKeydown" />
      <div class="composer-footer">
        <span class="composer-hint">Enter 发送 · Shift+Enter 换行</span>
        <div class="composer-actions">
          <button v-if="canRetryRun && !isGenerating" type="button" class="icon-button retry" data-testid="chat-retry" :disabled="isRetrying" title="重试运行" aria-label="重试运行" @click="emit('retry')"><RedoOutlined /></button>
          <button v-if="canCancelRun" type="button" class="icon-button stop" data-testid="chat-cancel" :disabled="isCancelling" title="停止运行" aria-label="停止运行" @click="emit('cancel')"><StopOutlined /></button>
          <button v-else type="button" class="send-button btn-send" data-testid="chat-send" :disabled="sendDisabled" @click="send"><SendOutlined /><span>发送</span></button>
        </div>
      </div>
    </div>
  </footer>
</template>

<style scoped lang="less">
.composer { flex: 0 0 auto; display: grid; gap: 8px; padding: 10px 14px 14px; border-top: 1px solid var(--border-subtle); background: #0b1120; }
.model-warning { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 9px; border-left: 2px solid #d8b15e; background: rgba(216, 177, 94, 0.08); color: #e6c57e; font-size: 12px; }
.model-warning button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: #e6c57e; cursor: pointer; white-space: nowrap; }
.context-row { display: flex; gap: 5px; overflow-x: auto; scrollbar-width: none; }
.context-chip { flex: 0 0 auto; min-height: 24px; display: inline-flex; align-items: center; gap: 5px; padding: 3px 7px; border: 1px solid rgba(148, 163, 184, 0.16); background: rgba(148, 163, 184, 0.06); color: var(--text-muted); font-size: 10px; }
.context-chip b { color: #86b9d8; font-weight: 600; }
.context-chip button { width: 18px; height: 18px; display: grid; place-items: center; padding: 0; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
.composer-box { border: 1px solid rgba(148, 163, 184, 0.22); background: #111827; }
.composer-box:focus-within { border-color: rgba(105, 168, 207, 0.62); box-shadow: 0 0 0 2px rgba(105, 168, 207, 0.08); }
textarea { width: 100%; min-height: 48px; max-height: 180px; resize: none; display: block; padding: 11px 12px 4px; border: 0; outline: 0; background: transparent; color: var(--text-primary); font: inherit; line-height: 1.55; overflow-y: auto; }
textarea::placeholder { color: #68768a; }
.composer-footer { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 3px 5px 5px 11px; }
.composer-hint { color: var(--text-muted); font-size: 10px; }
.composer-actions { display: flex; gap: 5px; }
.icon-button, .send-button { height: 32px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid transparent; cursor: pointer; }
.icon-button { width: 32px; background: transparent; color: var(--text-muted); }
.retry:hover { color: #8cc4e6; border-color: rgba(105, 168, 207, 0.35); }
.stop { color: #e38b82; border-color: rgba(211, 91, 81, 0.26); background: rgba(211, 91, 81, 0.08); }
.send-button { gap: 6px; padding: 0 12px; border-color: #558eb0; background: #3f7798; color: #fff; }
.send-button:disabled, .icon-button:disabled { cursor: not-allowed; opacity: 0.42; }
@media (max-width: 480px) { .composer { padding-inline: 10px; } .composer-hint { display: none; } }
</style>
