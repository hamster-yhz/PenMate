<template>
  <div v-if="schema.fields.length" class="attribute-editor">
    <section v-for="section in populatedSections" :key="section.key" class="attribute-section">
      <header>
        <h3>{{ section.title }}</h3>
        <p v-if="section.description">{{ section.description }}</p>
      </header>
      <div class="attribute-grid">
        <div
          v-for="field in fieldsFor(section.key)"
          :key="field.key"
          :data-field-key="field.key"
          :class="{ wide: field.control === 'multiline' || field.control === 'string-list' }"
        >
          <label class="field-label" :for="inputId(field.key)">{{ field.title }}</label>
          <select
            v-if="field.control === 'enum'"
            :id="inputId(field.key)"
            :value="textValue(field.key)"
            @change="setScalar(field, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">未设置</option>
            <option v-for="value in field.enumValues" :key="value" :value="value">
              {{ field.enumLabels[value] || value }}
            </option>
          </select>
          <div v-else-if="field.control === 'boolean'" class="boolean-control">
            <input
              :id="inputId(field.key)"
              type="checkbox"
              :checked="booleanValue(field.key)"
              @change="setBoolean(field.key, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ hasValue(field.key) ? (booleanValue(field.key) ? '是' : '否') : '未设置' }}</span>
            <button v-if="hasValue(field.key)" type="button" title="清除此项" @click="clearValue(field.key)">
              <CloseOutlined />
            </button>
          </div>
          <div v-else-if="field.control === 'string-list'" class="list-control">
            <div v-for="(item, index) in arrayValue(field.key)" :key="`${field.key}-${index}`" class="list-row">
              <input
                :value="item"
                :aria-label="`${field.title} ${index + 1}`"
                :placeholder="field.placeholder"
                @input="updateListItem(field.key, index, ($event.target as HTMLInputElement).value)"
              />
              <button type="button" title="删除此项" @click="removeListItem(field.key, index)"><DeleteOutlined /></button>
            </div>
            <button type="button" class="add-list-item" @click="addListItem(field.key)"><PlusOutlined />添加一项</button>
          </div>
          <textarea
            v-else-if="field.control === 'multiline'"
            :id="inputId(field.key)"
            :value="textValue(field.key)"
            :placeholder="field.placeholder"
            rows="4"
            @input="setScalar(field, ($event.target as HTMLTextAreaElement).value)"
          />
          <input
            v-else
            :id="inputId(field.key)"
            :type="field.control === 'integer' || field.control === 'number' ? 'number' : 'text'"
            :value="textValue(field.key)"
            :placeholder="field.placeholder"
            :min="field.minimum"
            :max="field.maximum"
            :step="field.control === 'integer' ? 1 : undefined"
            @input="setScalar(field, ($event.target as HTMLInputElement).value)"
          />
          <small v-if="field.description">{{ field.description }}</small>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CloseOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { parseStoryBibleTypeSchema, type StoryBibleSchemaField } from './storyBibleSchema'

const props = defineProps<{ schemaJson: string; modelValue: string }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>()

const schema = computed(() => parseStoryBibleTypeSchema(props.schemaJson))
const populatedSections = computed(() =>
  schema.value.sections.filter((section) => schema.value.fields.some((field) => field.section === section.key)),
)
const fieldsFor = (sectionKey: string) => schema.value.fields.filter((field) => field.section === sectionKey)
const inputId = (key: string) => `story-bible-attribute-${key}`
const attributes = () => {
  try { return JSON.parse(props.modelValue || '{}') as Record<string, unknown> }
  catch { return {} }
}
const commit = (next: Record<string, unknown>) => emit('update:modelValue', JSON.stringify(next))
const textValue = (key: string) => String(attributes()[key] ?? '')
const hasValue = (key: string) => Object.prototype.hasOwnProperty.call(attributes(), key)
const booleanValue = (key: string) => attributes()[key] === true
const arrayValue = (key: string) => {
  const value = attributes()[key]
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}
const setScalar = (field: StoryBibleSchemaField, value: string) => {
  const next = attributes()
  if (value === '') delete next[field.key]
  else if (field.control === 'integer') next[field.key] = Number.parseInt(value, 10)
  else if (field.control === 'number') next[field.key] = Number(value)
  else next[field.key] = value
  commit(next)
}
const setBoolean = (key: string, value: boolean) => {
  const next = attributes()
  next[key] = value
  commit(next)
}
const clearValue = (key: string) => {
  const next = attributes()
  delete next[key]
  commit(next)
}
const updateListItem = (key: string, index: number, value: string) => {
  const next = attributes()
  const items = arrayValue(key)
  items[index] = value
  next[key] = items
  commit(next)
}
const addListItem = (key: string) => {
  const next = attributes()
  next[key] = [...arrayValue(key), '']
  commit(next)
}
const removeListItem = (key: string, index: number) => {
  const next = attributes()
  const items = arrayValue(key).filter((_, itemIndex) => itemIndex !== index)
  if (items.length) next[key] = items
  else delete next[key]
  commit(next)
}
</script>

<style scoped lang="less">
.attribute-editor { display: grid; }
.attribute-section { padding: 16px 0; border-top: 1px solid var(--border-subtle); }
.attribute-section > header { margin-bottom: 12px; }
h3 { margin: 0; color: var(--text-primary); font-size: .82rem; }
p { margin: 3px 0 0; color: var(--text-muted); font-size: .7rem; line-height: 1.5; }
.attribute-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.attribute-grid > div { min-width: 0; display: grid; align-content: start; gap: 5px; }
.attribute-grid > div.wide { grid-column: 1 / -1; }
.field-label { color: var(--text-secondary); font-size: .74rem; }
input, textarea, select { width: 100%; min-width: 0; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); background: var(--bg-surface); outline: none; }
input, select { height: 34px; padding: 0 9px; }
textarea { min-height: 86px; padding: 9px; resize: vertical; line-height: 1.55; }
input:focus, textarea:focus, select:focus { border-color: var(--accent-border); box-shadow: 0 0 0 2px var(--focus-ring); }
small { color: var(--text-muted); font-size: .66rem; line-height: 1.45; }
.boolean-control { width: max-content; display: flex; align-items: center; gap: 8px; min-height: 34px; }
.boolean-control input { width: 16px; height: 16px; }
.boolean-control span { color: var(--text-secondary); font-size: .74rem; }
.boolean-control button { width: 28px; height: 28px; border: 0; color: var(--text-muted); background: transparent; cursor: pointer; }
.list-control { display: grid; gap: 6px; }
.list-row { display: grid; grid-template-columns: minmax(0, 1fr) 34px; gap: 6px; }
.list-row button, .add-list-item { height: 34px; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-secondary); background: var(--bg-surface); cursor: pointer; }
.list-row button { width: 34px; color: var(--danger); }
.add-list-item { width: max-content; display: flex; align-items: center; gap: 5px; padding: 0 10px; color: var(--accent); border-color: var(--accent-border); }
@media (max-width: 720px) { .attribute-grid { grid-template-columns: 1fr; } .attribute-grid > div.wide { grid-column: auto; } }
</style>
