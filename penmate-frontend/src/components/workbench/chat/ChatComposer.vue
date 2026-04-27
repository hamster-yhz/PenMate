<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string
  isGenerating?: boolean
  currentModelName?: string
  activePlugins?: string[]
}>(), {
  modelValue: '',
  isGenerating: false,
  currentModelName: '',
  activePlugins: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  'open-model-settings': []
}>()

const sendDisabled = computed(() => !props.modelValue.trim() || props.isGenerating)
const displayModelName = computed(() => props.currentModelName || '未选择模型')

const updateValue = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

const emitSend = () => {
  if (sendDisabled.value) return
  emit('send')
}

const handleCtrlEnter = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || !event.ctrlKey) return
  event.preventDefault()
  emitSend()
}
</script>

<template>
  <div class="chat-input-area workbench-composer" data-testid="chat-composer">
    <div v-if="!currentModelName" class="model-warning-inline theme-warning" data-testid="model-warning">
      当前未选择模型，请先在模型设置中保存并切换一个可用模型。
      <button
        class="model-warning-btn"
        type="button"
        data-testid="open-model-settings"
        @click="$emit('open-model-settings')"
      >
        去选择
      </button>
    </div>

    <div class="input-plugins" v-if="activePlugins.length">
      <span class="ip-label">已挂载：</span>
      <span v-for="plugin in activePlugins" :key="plugin" class="ip-tag">{{ plugin }}</span>
    </div>

    <div class="input-model-line">
      <span class="input-model-label">当前模型：</span>
      <span
        data-testid="current-model-value"
        :class="['input-model-value', { empty: !currentModelName }]"
      >
        {{ displayModelName }}
      </span>
    </div>

    <div class="input-wrap">
      <textarea
        :value="modelValue"
        class="chat-textarea composer-textarea"
        data-testid="chat-input"
        placeholder="输入指令，例如：开始写第三卷第二章..."
        rows="3"
        @input="updateValue"
        @keydown="handleCtrlEnter"
      />
      <button
        type="button"
        class="btn-send"
        data-testid="chat-send"
        :disabled="sendDisabled"
        @click="emitSend"
      >
        <span v-if="!isGenerating">发送</span>
        <span v-else>⏳</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="less">
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.78), rgba(11, 17, 32, 0.92));
}

.workbench-composer {
  box-shadow: inset 0 1px 0 rgba(201, 169, 110, 0.03);
}

.model-warning-inline {
  padding: 10px 12px;
  font-size: 0.8rem;
  line-height: 1.6;
}

.theme-warning {
  color: #f3d19c;
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.22);
  border-radius: 10px;
}

.model-warning-btn,
.btn-send {
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);
}

.model-warning-btn {
  margin-left: 8px;
  padding: 4px 10px;
  border: 1px solid var(--border-gold);
  border-radius: 999px;
  background: rgba(201, 169, 110, 0.08);
  color: var(--amber-gold);
}

.input-plugins,
.input-model-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 0.8rem;
}

.ip-label,
.input-model-label {
  color: var(--text-muted);
}

.ip-tag,
.input-model-value {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--border-subtle);
  background: rgba(11, 17, 32, 0.58);
  color: var(--text-secondary);
}

.input-model-value.empty {
  color: var(--text-muted);
}

.input-wrap {
  display: flex;
  gap: 8px;
  align-items: stretch;
}

.chat-textarea {
  flex: 1;
  min-height: 88px;
  resize: vertical;
  padding: 12px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: rgba(11, 17, 32, 0.72);
  color: var(--text-primary);
  line-height: 1.7;
  outline: none;
  caret-color: var(--amber-gold);
}

.composer-textarea:focus {
  border-color: var(--border-gold);
  box-shadow: 0 0 0 3px rgba(201, 169, 110, 0.08);
}

.chat-textarea::placeholder {
  color: var(--text-muted);
}

.btn-send {
  min-width: 88px;
  padding: 0 18px;
  border: 1px solid var(--border-gold);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.05));
  color: var(--amber-gold);
  font-family: var(--font-heading);
  letter-spacing: 0.12em;
}

.btn-send:hover:not([disabled]),
.model-warning-btn:hover {
  box-shadow: var(--shadow-gold);
  border-color: var(--border-glow);
}

.btn-send[disabled] {
  cursor: not-allowed;
  opacity: 0.48;
}
</style>
