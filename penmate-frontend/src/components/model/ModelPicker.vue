<template>
  <div class="model-picker-field">
    <span class="model-picker-label">{{ label }}</span>
    <button
      class="model-picker-trigger"
      type="button"
      :disabled="disabled"
      :data-testid="testId"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="openPicker"
    >
      <span class="model-picker-trigger-icon" aria-hidden="true">
        <DatabaseOutlined v-if="selectedOption?.type === 'EMBEDDING'" />
        <RobotOutlined v-else />
      </span>
      <span class="model-picker-trigger-copy">
        <span class="model-picker-selected-name">
          <strong>{{ selectedOption?.displayName || '未配置' }}</strong>
          <span v-if="selectedOption?.official" class="official-badge">
            <SafetyCertificateOutlined />官方
          </span>
        </span>
        <small v-if="selectedOption">{{ optionMeta(selectedOption) }}</small>
        <small v-else>选择一个可用模型</small>
      </span>
      <RightOutlined class="model-picker-chevron" />
    </button>
    <small v-if="description" class="model-picker-description">{{ description }}</small>
  </div>

  <Teleport to="body">
    <div v-if="open" class="model-picker-layer" role="presentation" @mousedown.self="closePicker">
      <section
        ref="dialog"
        class="model-picker-dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
      >
        <header>
          <div>
            <p>模型配置</p>
            <h2 :id="titleId">选择{{ label }}</h2>
          </div>
          <button class="model-picker-icon-button" type="button" aria-label="关闭模型选择" @click="closePicker">
            <CloseOutlined />
          </button>
        </header>

        <label class="model-picker-search">
          <SearchOutlined />
          <input v-model.trim="query" data-dialog-initial-focus type="search" placeholder="搜索模型、服务商或模型 ID" />
        </label>

        <div class="model-picker-results">
          <div v-if="!filteredOptions.length" class="model-picker-empty">
            <SearchOutlined />
            <span>{{ options.length ? '没有匹配的模型' : '暂无可用模型' }}</span>
          </div>
          <section v-for="group in groups" v-else :key="group.key" class="model-picker-group">
            <header>
              <span>
                <SafetyCertificateOutlined v-if="group.key === 'official'" />
                {{ group.label }}
              </span>
              <small>{{ group.items.length }}</small>
            </header>
            <div class="model-picker-options">
              <button
                v-for="option in group.items"
                :key="option.id"
                type="button"
                class="model-picker-option"
                :class="{ selected: option.id === modelValue }"
                :data-model-id="option.id"
                @click="select(option.id)"
              >
                <span class="model-option-icon" aria-hidden="true">
                  <DatabaseOutlined v-if="option.type === 'EMBEDDING'" />
                  <RobotOutlined v-else />
                </span>
                <span class="model-option-copy">
                  <span class="model-option-title">
                    <strong>{{ option.displayName }}</strong>
                    <span v-if="option.official" class="official-badge">
                      <SafetyCertificateOutlined />官方
                    </span>
                  </span>
                  <small>{{ optionMeta(option) }}</small>
                </span>
                <CheckOutlined v-if="option.id === modelValue" class="model-option-check" />
              </button>
            </div>
          </section>
        </div>

        <footer>
          <button v-if="allowClear && modelValue" class="model-picker-clear" type="button" @click="select(null)">
            <CloseCircleOutlined />清除选择
          </button>
          <button type="button" @click="closePicker">取消</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  CheckOutlined,
  CloseCircleOutlined,
  CloseOutlined,
  DatabaseOutlined,
  RightOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { useDialogFocus } from '@/composables/useDialogFocus'

export interface ModelPickerOption {
  id: string
  displayName: string
  modelName: string
  providerName?: string
  type: 'CHAT' | 'EMBEDDING'
  official: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: string | null
    label: string
    description?: string
    options: ModelPickerOption[]
    disabled?: boolean
    allowClear?: boolean
    testId?: string
  }>(),
  { description: '', disabled: false, allowClear: true, testId: undefined },
)

const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()
const open = ref(false)
const query = ref('')
const dialog = ref<HTMLElement | null>(null)
const titleId = `model-picker-title-${Math.random().toString(36).slice(2, 9)}`
const selectedOption = computed(() => props.options.find((option) => option.id === props.modelValue))
const filteredOptions = computed(() => {
  const keyword = query.value.toLocaleLowerCase()
  if (!keyword) return props.options
  return props.options.filter((option) =>
    [option.displayName, option.modelName, option.providerName]
      .filter(Boolean)
      .some((value) => value!.toLocaleLowerCase().includes(keyword)),
  )
})
const groups = computed(() =>
  [
    { key: 'official', label: '官方模型', items: filteredOptions.value.filter((option) => option.official) },
    { key: 'personal', label: '个人模型', items: filteredOptions.value.filter((option) => !option.official) },
  ].filter((group) => group.items.length),
)

const optionMeta = (option: ModelPickerOption) =>
  [option.providerName, option.modelName].filter(Boolean).join(' · ')
const openPicker = () => {
  if (!props.disabled) open.value = true
}
const closePicker = () => {
  open.value = false
  query.value = ''
}
const select = (value: string | null) => {
  emit('update:modelValue', value)
  closePicker()
}

useDialogFocus({ open: () => open.value, dialog, close: closePicker })
</script>

<style scoped>
.model-picker-field { display: grid; min-width: 0; gap: 7px; }
.model-picker-label { color: var(--text-secondary); font-size: 13px; font-weight: 600; }
.model-picker-trigger { display: grid; grid-template-columns: 34px minmax(0, 1fr) 18px; width: 100%; min-height: 58px; align-items: center; gap: 10px; padding: 8px 11px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 6px; cursor: pointer; text-align: left; transition: border-color 140ms ease, box-shadow 140ms ease, background 140ms ease; }
.model-picker-trigger:hover:not(:disabled) { background: var(--bg-subtle); border-color: var(--accent-border); }
.model-picker-trigger:focus-visible { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); outline: 0; }
.model-picker-trigger:disabled { cursor: not-allowed; opacity: .55; }
.model-picker-trigger-icon, .model-option-icon { display: grid; width: 34px; height: 34px; place-items: center; color: var(--accent); background: var(--accent-soft); border-radius: 5px; font-size: 16px; }
.model-picker-trigger-copy, .model-option-copy { display: grid; min-width: 0; gap: 3px; }
.model-picker-selected-name, .model-option-title { display: flex; min-width: 0; align-items: center; gap: 7px; }
.model-picker-selected-name strong, .model-option-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.model-picker-trigger-copy small, .model-option-copy small, .model-picker-description { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.model-picker-chevron { justify-self: end; color: var(--text-muted); font-size: 12px; }
.official-badge { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 3px; padding: 2px 5px; color: var(--warning); background: var(--warning-soft); border: 1px solid color-mix(in srgb, var(--warning) 28%, transparent); border-radius: 4px; font-size: 10px; font-weight: 650; line-height: 1.2; }

.model-picker-layer { position: fixed; inset: 0; z-index: 1200; display: grid; place-items: center; padding: 20px; background: var(--overlay); }
.model-picker-dialog { display: grid; grid-template-rows: auto auto minmax(0, 1fr) auto; width: min(590px, 100%); max-height: min(680px, calc(100vh - 40px)); overflow: hidden; color: var(--text-primary); background: var(--bg-elevated); border: 1px solid var(--border-subtle); border-radius: 8px; box-shadow: var(--shadow-md); }
.model-picker-dialog > header { display: flex; min-height: 68px; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 16px; background: var(--bg-surface); border-bottom: 1px solid var(--border-subtle); }
.model-picker-dialog > header p { margin: 0 0 3px; color: var(--text-muted); font-size: 11px; }
.model-picker-dialog > header h2 { margin: 0; font-size: 17px; letter-spacing: 0; }
.model-picker-icon-button { display: grid; width: 34px; height: 34px; place-items: center; color: var(--text-secondary); background: transparent; border: 0; border-radius: 4px; cursor: pointer; }
.model-picker-icon-button:hover { background: var(--bg-muted); }
.model-picker-search { display: flex; align-items: center; gap: 8px; margin: 12px 14px; padding: 0 10px; color: var(--text-muted); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 5px; }
.model-picker-search:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); }
.model-picker-search input { width: 100%; height: 38px; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.model-picker-results { min-height: 180px; overflow-y: auto; padding: 0 14px 14px; }
.model-picker-group + .model-picker-group { margin-top: 14px; }
.model-picker-group > header { display: flex; min-height: 30px; align-items: center; justify-content: space-between; color: var(--text-secondary); font-size: 11px; font-weight: 650; }
.model-picker-group > header span { display: inline-flex; align-items: center; gap: 5px; }
.model-picker-group > header small { color: var(--text-muted); font-weight: 400; }
.model-picker-options { overflow: hidden; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 6px; }
.model-picker-option { display: grid; grid-template-columns: 34px minmax(0, 1fr) 20px; width: 100%; min-height: 60px; align-items: center; gap: 10px; padding: 8px 10px; color: var(--text-primary); background: transparent; border: 0; border-bottom: 1px solid var(--border-subtle); cursor: pointer; text-align: left; }
.model-picker-option:last-child { border-bottom: 0; }
.model-picker-option:hover { background: var(--bg-subtle); }
.model-picker-option.selected { background: var(--accent-soft); }
.model-picker-option.selected .model-option-icon { color: var(--text-inverse); background: var(--accent); }
.model-option-check { justify-self: end; color: var(--accent); }
.model-picker-empty { display: grid; min-height: 190px; place-items: center; align-content: center; gap: 8px; color: var(--text-muted); font-size: 12px; }
.model-picker-dialog > footer { display: flex; min-height: 56px; align-items: center; justify-content: flex-end; gap: 8px; padding: 10px 14px; background: var(--bg-surface); border-top: 1px solid var(--border-subtle); }
.model-picker-dialog > footer button { min-height: 34px; padding: 0 11px; color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; cursor: pointer; }
.model-picker-dialog > footer .model-picker-clear { display: inline-flex; align-items: center; gap: 5px; margin-right: auto; color: var(--danger); border-color: var(--danger-border); }

@media (max-width: 620px) {
  .model-picker-layer { align-items: end; padding: 12px; }
  .model-picker-dialog { max-height: calc(100vh - 24px); }
  .model-picker-results { min-height: 150px; }
}
</style>
