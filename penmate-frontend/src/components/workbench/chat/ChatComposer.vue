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
  <div class="chat-input-area" data-testid="chat-composer">
    <div v-if="!currentModelName" class="model-warning-inline" data-testid="model-warning">
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
        class="chat-textarea"
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

<style scoped>
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.model-warning-inline {
  color: #ad6800;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
}

.model-warning-btn,
.btn-send {
  cursor: pointer;
}

.model-warning-btn {
  margin-left: 8px;
}

.input-plugins,
.input-model-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
}

.input-model-value.empty {
  color: #8c8c8c;
}

.input-wrap {
  display: flex;
  gap: 8px;
}

.chat-textarea {
  flex: 1;
  min-height: 88px;
  resize: vertical;
}

.btn-send[disabled] {
  cursor: not-allowed;
}
</style>
