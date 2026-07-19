<template>
  <div class="base-tab">
    <div class="field-grid two-columns">
      <label>
        <span>节点类型</span>
        <select v-model="draft.typeId">
          <option v-for="type in nodeTypes" :key="type.typeId" :value="type.typeId">{{ type.displayName }}</option>
        </select>
      </label>
      <label>
        <span>正史状态</span>
        <select v-model="draft.canonStatus">
          <option value="CANON">正史</option>
          <option value="DRAFT">草稿</option>
          <option value="ARCHIVED">已归档</option>
        </select>
      </label>
    </div>

    <label>
      <span>标题</span>
      <input v-model="draft.title" autocomplete="off" />
    </label>
    <label>
      <span>摘要</span>
      <textarea v-model="draft.summary" rows="2"></textarea>
    </label>
    <label>
      <span>正文设定</span>
      <textarea v-model="draft.bodyMarkdown" rows="9"></textarea>
    </label>

    <div v-if="schemaFields.length" class="schema-fields">
      <h3>结构化属性</h3>
      <div class="field-grid two-columns">
        <label v-for="field in schemaFields" :key="field.key">
          <span>{{ field.title }}</span>
          <input
            :type="field.type === 'number' || field.type === 'integer' ? 'number' : 'text'"
            :value="attributeValue(field.key)"
            @input="setAttribute(field.key, ($event.target as HTMLInputElement).value, field.type)"
          />
        </label>
      </div>
    </div>

    <div class="field-grid two-columns">
      <label>
        <span>注入策略</span>
        <select v-model="draft.inclusionPolicy">
          <option value="ALWAYS_INCLUDE">始终注入</option>
          <option value="AUTO_RETRIEVE">按需检索</option>
          <option value="MANUAL_ONLY">仅手动</option>
        </select>
      </label>
      <label>
        <span>别名</span>
        <input :value="draft.aliases.join('，')" @input="setAliases(($event.target as HTMLInputElement).value)" />
      </label>
    </div>

    <fieldset>
      <legend>分类</legend>
      <label v-for="category in categories" :key="category.categoryId" class="check-option">
        <input v-model="draft.categoryIds" type="checkbox" :value="category.categoryId" />
        <span>{{ category.name }}</span>
      </label>
    </fieldset>
    <fieldset>
      <legend>标签</legend>
      <label v-for="tag in tags" :key="tag.tagId" class="check-option">
        <input v-model="draft.tagIds" type="checkbox" :value="tag.tagId" />
        <i :style="{ backgroundColor: tag.color || '#8b93a7' }"></i>
        <span>{{ tag.name }}</span>
      </label>
    </fieldset>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StoryBibleCategory, StoryBibleNodeType, StoryBibleTag } from '@/api/modules/storyBible.api'
import type { StoryBibleNodeDraft } from '@/composables/workbench/useStoryBible'

const props = defineProps<{
  draft: StoryBibleNodeDraft
  nodeTypes: StoryBibleNodeType[]
  categories: StoryBibleCategory[]
  tags: StoryBibleTag[]
}>()

const activeType = computed(() => props.nodeTypes.find((item) => item.typeId === props.draft.typeId))
const schemaFields = computed(() => {
  try {
    const schema = JSON.parse(activeType.value?.fieldSchemaJson || '{}') as {
      properties?: Record<string, { title?: string; type?: string }>
    }
    return Object.entries(schema.properties || {}).map(([key, value]) => ({
      key,
      title: value.title || key,
      type: value.type || 'string',
    }))
  } catch {
    return []
  }
})
const attributes = () => {
  try {
    return JSON.parse(props.draft.attributesJson || '{}') as Record<string, unknown>
  } catch {
    return {}
  }
}
const attributeValue = (key: string) => String(attributes()[key] ?? '')
const setAttribute = (key: string, value: string, type: string) => {
  const next = attributes()
  next[key] = type === 'number' || type === 'integer' ? Number(value) : value
  props.draft.attributesJson = JSON.stringify(next)
}
const setAliases = (value: string) => {
  props.draft.aliases = value
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
</script>

<style scoped lang="less">
.base-tab {
  display: grid;
  gap: 12px;
  padding: 16px;
}
label {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 0.74rem;
}
input,
textarea,
select {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  background: rgba(11, 17, 32, 0.68);
  outline: none;
}
input,
select {
  height: 34px;
  padding: 0 9px;
}
textarea {
  resize: vertical;
  padding: 9px;
  line-height: 1.6;
}
input:focus,
textarea:focus,
select:focus {
  border-color: var(--border-gold);
}
.field-grid {
  display: grid;
  gap: 10px;
}
.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.schema-fields {
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  background: rgba(17, 24, 39, 0.4);
}
.schema-fields h3 {
  margin: 0 0 10px;
  color: var(--amber-gold);
  font-size: 0.78rem;
}
fieldset {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 10px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
}
legend {
  padding: 0 5px;
  color: var(--text-muted);
  font-size: 0.7rem;
}
.check-option {
  display: flex;
  grid-template-columns: none;
  flex-direction: row;
  align-items: center;
  gap: 5px;
  padding: 4px 7px;
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
}
.check-option input {
  width: 14px;
  height: 14px;
}
.check-option i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
@media (max-width: 720px) {
  .two-columns {
    grid-template-columns: 1fr;
  }
}
</style>
