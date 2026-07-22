<template>
  <form class="settings-surface appearance-panel" @submit.prevent="save">
    <header>
      <div><h2>外观与编辑器</h2><p>应用到你的所有设备；工作台三栏宽度仍只保存在当前设备。</p></div>
      <span v-if="statusText" class="save-status" :class="saveState" role="status">{{ statusText }}</span>
    </header>

    <div class="setting-line">
      <span><strong>主题</strong><small>可以跟随系统，也可以固定浅色或深色</small></span>
      <div class="segmented" role="radiogroup" aria-label="主题模式">
        <button v-for="option in themeOptions" :key="option.value" type="button" :class="{ active: draft.themeMode === option.value }" @click="draft.themeMode = option.value">{{ option.label }}</button>
      </div>
    </div>

    <div class="setting-line">
      <span><strong>正文字体</strong><small>只影响小说正文，不改变界面字体</small></span>
      <select v-model="draft.editorFontFamily" aria-label="正文字体">
        <option value="SERIF">宋体</option>
        <option value="SANS">黑体</option>
        <option value="SYSTEM">系统默认</option>
      </select>
    </div>

    <div class="setting-line slider-line">
      <span><strong>字号</strong><small>{{ draft.editorFontSize }} px</small></span>
      <input v-model.number="draft.editorFontSize" type="range" min="14" max="24" step="1" aria-label="正文字号" />
    </div>
    <div class="setting-line slider-line">
      <span><strong>行高</strong><small>{{ draft.editorLineHeight.toFixed(2) }}</small></span>
      <input v-model.number="draft.editorLineHeight" type="range" min="1.5" max="2.4" step="0.05" aria-label="正文行高" />
    </div>
    <div class="setting-line slider-line">
      <span><strong>段间距</strong><small>{{ draft.editorParagraphSpacing.toFixed(2) }} em</small></span>
      <input v-model.number="draft.editorParagraphSpacing" type="range" min="0" max="2" step="0.05" aria-label="正文段间距" />
    </div>
    <div class="setting-line slider-line">
      <span><strong>正文宽度</strong><small>{{ draft.editorContentWidth }} px</small></span>
      <input v-model.number="draft.editorContentWidth" type="range" min="560" max="1000" step="20" aria-label="正文宽度" />
    </div>

    <label class="setting-line toggle-line">
      <span><strong>默认开启打字机模式</strong><small>输入时让当前光标保持在视野中部</small></span>
      <input v-model="draft.typewriterMode" type="checkbox" role="switch" />
    </label>
    <label class="setting-line toggle-line">
      <span><strong>高亮当前段落</strong><small>用低对比底色标记光标所在段落</small></span>
      <input v-model="draft.highlightCurrentParagraph" type="checkbox" role="switch" />
    </label>

    <footer>
      <p v-if="error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="saving || !dirty">{{ saving ? '正在保存' : '保存外观设置' }}</button>
    </footer>
  </form>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { ThemeMode, UserUiPreferences } from '@/entities/auth/model'
import { useUserUiPreferences } from '@/composables/useUserUiPreferences'

const themeOptions: Array<{ label: string; value: ThemeMode }> = [
  { label: '跟随系统', value: 'SYSTEM' },
  { label: '浅色', value: 'LIGHT' },
  { label: '深色', value: 'DARK' },
]
const { uiPreferences, loadUserUiPreferences, saveUserUiPreferences } = useUserUiPreferences()
const draft = reactive<UserUiPreferences>({ ...uiPreferences })
const baseline = ref(JSON.stringify(draft))
const saving = ref(false)
const error = ref('')
const saved = ref(false)
const dirty = computed(() => JSON.stringify(draft) !== baseline.value)
const saveState = computed(() => error.value ? 'error' : saved.value ? 'saved' : dirty.value ? 'dirty' : '')
const statusText = computed(() => error.value ? '保存失败' : saving.value ? '正在保存' : saved.value ? '已保存' : dirty.value ? '有未保存修改' : '')

const resetDraft = (value: UserUiPreferences) => {
  Object.assign(draft, value)
  baseline.value = JSON.stringify(draft)
}

onMounted(async () => {
  try { resetDraft(await loadUserUiPreferences()) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '加载外观设置失败' }
})
watch(dirty, (value) => { if (value) saved.value = false })

const save = async () => {
  if (!dirty.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    resetDraft(await saveUserUiPreferences({ ...draft }))
    saved.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存外观设置失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.appearance-panel > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
.appearance-panel h2 { margin: 0 0 4px; font-size: 15px; }
.appearance-panel p { margin: 0; color: var(--text-muted); font-size: 12px; }
.save-status { flex: 0 0 auto; color: var(--text-muted); font-size: 11px; }
.save-status.dirty { color: var(--warning); }
.save-status.saved { color: var(--accent); }
.save-status.error { color: var(--danger); }
.setting-line { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(240px, 320px); align-items: center; gap: 24px; min-height: 72px; padding: 12px 20px; border-bottom: 1px solid var(--border-subtle); }
.setting-line > span { display: grid; gap: 3px; }
.setting-line strong { font-size: 13px; }
.setting-line small { color: var(--text-muted); font-size: 11px; }
.setting-line select { height: 36px; padding: 0 10px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; }
.segmented { display: grid; grid-template-columns: repeat(3, 1fr); padding: 3px; background: var(--bg-subtle); border-radius: 5px; }
.segmented button { min-height: 32px; color: var(--text-secondary); background: transparent; border: 0; border-radius: 3px; cursor: pointer; }
.segmented button.active { color: var(--text-primary); background: var(--bg-surface); box-shadow: var(--shadow-xs); font-weight: 600; }
.slider-line input { width: 100%; accent-color: var(--accent); }
.toggle-line { cursor: pointer; }
.toggle-line input { justify-self: end; width: 38px; height: 20px; accent-color: var(--accent); }
.appearance-panel footer { display: flex; min-height: 64px; align-items: center; justify-content: flex-end; gap: 16px; padding: 12px 20px; }
.appearance-panel footer p { margin-right: auto; color: var(--danger); }
.appearance-panel footer button { min-height: 36px; padding: 0 14px; color: var(--text-inverse); background: var(--accent); border: 0; border-radius: 4px; cursor: pointer; }
.appearance-panel footer button:disabled { cursor: default; opacity: .5; }
@media (max-width: 620px) { .setting-line { grid-template-columns: 1fr; gap: 10px; } .toggle-line { grid-template-columns: 1fr auto; } }
</style>
