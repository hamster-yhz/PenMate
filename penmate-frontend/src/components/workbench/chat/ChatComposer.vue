<script setup lang="ts">
import { CheckOutlined, CloseOutlined, RedoOutlined, SendOutlined, SettingOutlined, StopOutlined, ToolOutlined } from '@ant-design/icons-vue'
import { computed, nextTick, ref, watch } from 'vue'
import type { WorkbenchSkillCatalogItem } from '@/components/workbench/workbenchTypes'

const props = withDefaults(defineProps<{
  modelValue?: string; isGenerating?: boolean; canCancelRun?: boolean; isCancelling?: boolean;
  canRetryRun?: boolean; isRetrying?: boolean; currentModelName?: string; activePlugins?: string[];
  activeChapterTitle?: string; selectedText?: string; boundStyleName?: string;
  skillCatalog?: WorkbenchSkillCatalogItem[]; activeSkills?: string[]; skillCatalogLoading?: boolean;
}>(), {
  modelValue: '', isGenerating: false, canCancelRun: false, isCancelling: false, canRetryRun: false,
  isRetrying: false, currentModelName: '', activePlugins: () => [], activeChapterTitle: '', selectedText: '', boundStyleName: '',
  skillCatalog: () => [], activeSkills: () => [], skillCatalogLoading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]; send: []; cancel: []; retry: []; 'open-model-settings': []; 'clear-selected-text': [];
  'add-skill': [name: string]; 'remove-skill': [name: string]; 'refresh-skill-catalog': [];
}>()
const textarea = ref<HTMLTextAreaElement | null>(null)
const skillMenuOpen = ref(false)
const skillQuery = ref('')
const highlightedSkill = ref(0)
const slashRange = ref<{ start: number; end: number } | null>(null)
const slashSource = ref('')
const sendDisabled = computed(() =>
  !props.modelValue.trim() || props.isGenerating || props.canCancelRun || !props.currentModelName,
)
const selectedPreview = computed(() => props.selectedText.trim().replace(/\s+/g, ' ').slice(0, 28))
const activeSkillSet = computed(() => new Set(props.activeSkills))
const catalogSkillSet = computed(() => new Set(props.skillCatalog.map((skill) => skill.name)))
const filteredSkills = computed(() => {
  const query = skillQuery.value.toLowerCase()
  return props.skillCatalog.filter((skill) =>
    !query || skill.name.toLowerCase().includes(query) || skill.description.toLowerCase().includes(query),
  )
})

const resize = async () => {
  await nextTick()
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${Math.min(180, Math.max(48, textarea.value.scrollHeight))}px`
}
watch(() => props.modelValue, resize, { immediate: true })

const updateSkillMenu = (value: string, cursor: number | null) => {
  const prefix = value.slice(0, cursor ?? value.length)
  const match = prefix.match(/(?:^|\s)\/([a-z0-9-]*)$/i)
  if (!match) {
    skillMenuOpen.value = false
    slashRange.value = null
    return
  }
  const wasOpen = skillMenuOpen.value
  const token = match[0].trimStart()
  slashRange.value = { start: prefix.length - token.length, end: prefix.length }
  slashSource.value = value
  skillQuery.value = match[1] || ''
  highlightedSkill.value = 0
  skillMenuOpen.value = true
  if (!wasOpen) emit('refresh-skill-catalog')
}
const updateValue = (event: Event) => {
  const target = event.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
  updateSkillMenu(target.value, target.selectionStart)
  void resize()
}
const selectSkill = async (skill: WorkbenchSkillCatalogItem) => {
  if (props.activeSkills.length >= 4 && !activeSkillSet.value.has(skill.name)) return
  const range = slashRange.value
  if (!range) return
  const source = slashSource.value || props.modelValue
  const nextValue = source.slice(0, range.start) + source.slice(range.end)
  emit('update:modelValue', nextValue)
  emit('add-skill', skill.name)
  skillMenuOpen.value = false
  slashRange.value = null
  await nextTick()
  textarea.value?.focus()
  textarea.value?.setSelectionRange(range.start, range.start)
}
const send = () => { if (!sendDisabled.value) emit('send') }
const handleKeydown = (event: KeyboardEvent) => {
  if (skillMenuOpen.value) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const count = filteredSkills.value.length
      if (count) highlightedSkill.value = (highlightedSkill.value + (event.key === 'ArrowDown' ? 1 : count - 1)) % count
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      skillMenuOpen.value = false
      return
    }
    if (event.key === 'Enter' && filteredSkills.value[highlightedSkill.value]) {
      event.preventDefault()
      void selectSkill(filteredSkills.value[highlightedSkill.value])
      return
    }
  }
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
    <div v-if="activeSkills.length" class="skill-tags" aria-label="已激活 Skill">
      <span
        v-for="skill in activeSkills"
        :key="skill"
        class="skill-tag"
        :class="{ unavailable: !catalogSkillSet.has(skill) }"
        :title="catalogSkillSet.has(skill) ? `已激活 ${skill}` : `${skill} 当前不可用`"
      >
        <ToolOutlined />
        <span>{{ skill }}</span>
        <button type="button" :aria-label="`移除 ${skill}`" :title="`移除 ${skill}`" @click="emit('remove-skill', skill)">
          <CloseOutlined />
        </button>
      </span>
    </div>
    <div class="composer-box">
      <div v-if="skillMenuOpen" class="skill-menu" role="listbox" aria-label="Skill 候选">
        <div v-if="skillCatalogLoading" class="skill-menu-empty">加载中</div>
        <button
          v-for="(skill, index) in filteredSkills"
          v-else
          :key="skill.name"
          type="button"
          role="option"
          :aria-selected="activeSkillSet.has(skill.name)"
          :class="{ highlighted: index === highlightedSkill, active: activeSkillSet.has(skill.name) }"
          :disabled="activeSkills.length >= 4 && !activeSkillSet.has(skill.name)"
          @mouseenter="highlightedSkill = index"
          @mousedown.prevent="selectSkill(skill)"
        >
          <span><strong>{{ skill.name }}</strong><small>{{ skill.description }}</small></span>
          <CheckOutlined v-if="activeSkillSet.has(skill.name)" />
        </button>
        <div v-if="!skillCatalogLoading && !filteredSkills.length" class="skill-menu-empty">无匹配 Skill</div>
      </div>
      <textarea ref="textarea" class="composer-textarea" :value="modelValue" rows="1" aria-label="发送给 Agent 的消息" data-testid="chat-input" placeholder="描述你希望 Agent 完成的写作任务" @input="updateValue" @keydown="handleKeydown" @click="updateSkillMenu(modelValue, textarea?.selectionStart ?? null)" @blur="skillMenuOpen = false" />
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
.composer { flex: 0 0 auto; display: grid; gap: 8px; padding: 10px 14px 14px; border-top: 1px solid var(--border-subtle); background: var(--bg-surface); }
.model-warning { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 9px; border-left: 2px solid var(--warning); background: var(--warning-soft); color: var(--warning); font-size: 12px; }
.model-warning button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: var(--warning); cursor: pointer; white-space: nowrap; }
.context-row { display: flex; gap: 5px; overflow-x: auto; scrollbar-width: none; }
.context-chip { flex: 0 0 auto; min-height: 24px; display: inline-flex; align-items: center; gap: 5px; padding: 3px 7px; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); background: var(--bg-subtle); color: var(--text-muted); font-size: 10px; }
.context-chip b { color: var(--accent); font-weight: 600; }
.context-chip button { width: 18px; height: 18px; display: grid; place-items: center; padding: 0; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
.skill-tags { display: flex; gap: 5px; overflow-x: auto; scrollbar-width: none; }
.skill-tag { flex: 0 0 auto; min-height: 26px; display: inline-flex; align-items: center; gap: 5px; padding: 3px 4px 3px 7px; border: 1px solid var(--accent-border); border-radius: var(--radius-sm); background: var(--accent-soft); color: var(--text-secondary); font-size: 11px; }
.skill-tag > svg { color: var(--accent); }
.skill-tag.unavailable { border-color: var(--warning); background: var(--warning-soft); color: var(--warning); }
.skill-tag button { width: 20px; height: 20px; display: grid; place-items: center; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; }
.composer-box { position: relative; border: 1px solid var(--border-strong); border-radius: var(--radius-md); background: var(--bg-editor); }
.composer-box:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); }
.skill-menu { position: absolute; z-index: 30; left: 6px; right: 6px; bottom: calc(100% + 6px); max-height: 260px; overflow-y: auto; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--bg-elevated); box-shadow: var(--shadow-md); }
.skill-menu button { width: 100%; min-height: 48px; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 7px 10px; border: 0; border-bottom: 1px solid var(--border-subtle); background: transparent; color: var(--text-secondary); text-align: left; cursor: pointer; }
.skill-menu button:last-of-type { border-bottom: 0; }
.skill-menu button.highlighted { background: var(--bg-subtle); }
.skill-menu button.active { color: var(--accent); }
.skill-menu button:disabled { cursor: not-allowed; opacity: 0.42; }
.skill-menu button span { min-width: 0; display: grid; gap: 2px; }
.skill-menu strong { font-size: 12px; font-weight: 650; }
.skill-menu small { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.skill-menu-empty { padding: 12px 10px; color: var(--text-muted); font-size: 12px; }
textarea { width: 100%; min-height: 48px; max-height: 180px; resize: none; display: block; padding: 11px 12px 4px; border: 0; outline: 0; background: transparent; color: var(--text-primary); font: inherit; line-height: 1.55; overflow-y: auto; }
textarea::placeholder { color: var(--text-muted); }
.composer-footer { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 3px 5px 5px 11px; }
.composer-hint { color: var(--text-muted); font-size: 10px; }
.composer-actions { display: flex; gap: 5px; }
.icon-button, .send-button { height: 32px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid transparent; cursor: pointer; }
.icon-button { width: 32px; background: transparent; color: var(--text-muted); }
.retry:hover { color: var(--accent); border-color: var(--accent-border); background: var(--accent-soft); }
.stop { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 32%, var(--border-subtle)); background: var(--danger-soft); }
.send-button { gap: 6px; padding: 0 12px; border-color: var(--accent); border-radius: var(--radius-sm); background: var(--accent); color: var(--text-inverse); }
.send-button:hover:not(:disabled) { background: var(--accent-hover); }
.send-button:disabled, .icon-button:disabled { cursor: not-allowed; opacity: 0.42; }
@media (max-width: 480px) { .composer { padding-inline: 10px; } .composer-hint { display: none; } }
</style>
