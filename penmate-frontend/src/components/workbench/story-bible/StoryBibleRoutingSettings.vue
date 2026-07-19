<template>
  <section class="routing-settings">
    <header>
      <div><strong>故事圣经注入方式</strong><span>用户默认与当前 Session</span></div>
      <button type="button" class="icon-button" title="关闭" @click="emit('close')"><CloseOutlined /></button>
    </header>

    <div class="setting-section">
      <label>用户默认</label>
      <div class="mode-options">
        <button
          v-for="option in modeOptions"
          :key="option.value"
          type="button"
          :class="{ active: userMode === option.value }"
          @click="userMode = option.value"
        >
          {{ option.label }}
        </button>
      </div>
      <button type="button" class="save-button" @click="emit('saveUser', userMode)">
        <SaveOutlined /> 保存默认设置
      </button>
    </div>

    <div v-if="sessionPreference" class="setting-section">
      <label class="toggle-line">
        <input v-model="inheritUser" type="checkbox" />
        <span>当前 Session 继承用户默认</span>
      </label>
      <div class="mode-options" :class="{ disabled: inheritUser }">
        <button
          v-for="option in modeOptions"
          :key="option.value"
          type="button"
          :disabled="inheritUser"
          :class="{ active: sessionMode === option.value }"
          @click="sessionMode = option.value"
        >
          {{ option.label }}
        </button>
      </div>
      <button type="button" class="save-button" @click="saveSession"><SaveOutlined /> 保存 Session 设置</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { CloseOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type { StoryBibleRoutingMode, StoryBibleRoutingPreference } from '@/api/modules/storyBible.api'

const props = defineProps<{
  userPreference: StoryBibleRoutingPreference | null
  sessionPreference: StoryBibleRoutingPreference | null
}>()
const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saveUser', mode: StoryBibleRoutingMode): void
  (event: 'saveSession', mode: StoryBibleRoutingMode | null): void
}>()
const modeOptions: Array<{ value: StoryBibleRoutingMode; label: string }> = [
  { value: 'RETRIEVAL', label: '规则匹配 + Embedding' },
  { value: 'LLM_SELECTOR', label: '直接使用 LLM' },
  { value: 'RETRIEVAL_THEN_LLM', label: '规则匹配 + Embedding，LLM 兜底' },
]
const userMode = ref<StoryBibleRoutingMode>('RETRIEVAL_THEN_LLM')
const sessionMode = ref<StoryBibleRoutingMode>('RETRIEVAL_THEN_LLM')
const inheritUser = ref(true)
watch(
  () => props.userPreference,
  (value) => {
    if (value) userMode.value = value.mode
  },
  { immediate: true },
)
watch(
  () => props.sessionPreference,
  (value) => {
    if (!value) return
    sessionMode.value = value.mode
    inheritUser.value = value.inherited
  },
  { immediate: true },
)
const saveSession = () => emit('saveSession', inheritUser.value ? null : sessionMode.value)
</script>

<style scoped lang="less">
.routing-settings {
  position: absolute;
  top: 52px;
  right: 12px;
  z-index: 40;
  width: min(520px, calc(100% - 24px));
  border: 1px solid var(--border-gold);
  border-radius: 6px;
  background: rgba(11, 17, 32, 0.98);
  box-shadow: var(--shadow-lg);
}
header {
  min-height: 54px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--border-subtle);
}
header div {
  display: grid;
  gap: 2px;
}
header span {
  color: var(--text-muted);
  font-size: 0.68rem;
}
.icon-button {
  width: 30px;
  height: 30px;
  border: 0;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}
.setting-section {
  display: grid;
  gap: 9px;
  padding: 14px;
  border-bottom: 1px solid var(--border-subtle);
}
.setting-section > label {
  color: var(--text-secondary);
  font-size: 0.72rem;
}
.mode-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  overflow: hidden;
}
.mode-options button {
  min-height: 44px;
  padding: 5px 8px;
  border: 0;
  border-right: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  background: rgba(17, 24, 39, 0.75);
  cursor: pointer;
}
.mode-options button:last-child {
  border-right: 0;
}
.mode-options button.active {
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.12);
}
.mode-options.disabled {
  opacity: 0.46;
}
.toggle-line {
  display: flex;
  align-items: center;
  gap: 7px;
}
.save-button {
  width: max-content;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-gold);
  border-radius: 4px;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  cursor: pointer;
}
@media (max-width: 620px) {
  .mode-options {
    grid-template-columns: 1fr;
  }
  .mode-options button {
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle);
  }
}
</style>
