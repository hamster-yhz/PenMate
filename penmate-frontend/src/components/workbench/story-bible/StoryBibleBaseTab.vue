<template>
  <div class="base-tab">
    <section class="identity-section" :style="{ '--type-color': activeSchema.color }">
      <div class="type-marker" aria-hidden="true"></div>
      <div class="identity-fields">
        <div class="field-grid two-columns compact-grid">
          <label>
            <span>节点类型</span>
            <select v-model="draft.typeId" :disabled="isStoryCore">
              <option v-for="type in nodeTypes" :key="type.typeId" :value="type.typeId">{{ type.displayName }}</option>
            </select>
          </label>
          <label>
            <span>正史状态</span>
            <select v-model="draft.canonStatus" :disabled="isStoryCore">
              <option value="CANON">正史</option>
              <option value="DRAFT">草稿</option>
              <option value="ARCHIVED">已归档</option>
            </select>
          </label>
        </div>
        <p v-if="activeSchema.description" class="type-description">{{ activeSchema.description }}</p>
        <label>
          <span>标题</span>
          <input v-model="draft.title" :placeholder="activeSchema.titlePlaceholder" autocomplete="off" />
        </label>
        <label>
          <span>摘要</span>
          <textarea v-model="draft.summary" :placeholder="activeSchema.summaryPlaceholder" rows="2"></textarea>
        </label>
      </div>
    </section>

    <StoryBibleAttributeEditor
      v-if="activeSchema.fields.length"
      v-model="draft.attributesJson"
      :schema-json="activeType?.fieldSchemaJson || '{}'"
    />

    <section class="content-section">
      <header>
        <label class="section-title" for="story-bible-body">详细设定</label>
        <span>Markdown</span>
      </header>
      <textarea
        id="story-bible-body"
        v-model="draft.bodyMarkdown"
        rows="10"
        :placeholder="`${activeType?.displayName || '节点'}的背景、规则、例外和补充资料`"
      ></textarea>
    </section>

    <section class="metadata-section">
      <header><h3>检索与归档</h3></header>
      <div class="field-grid two-columns">
        <label>
          <span>注入策略</span>
          <select v-model="draft.inclusionPolicy" :disabled="isStoryCore">
            <option value="ALWAYS_INCLUDE">始终注入</option>
            <option value="AUTO_RETRIEVE">按需检索</option>
            <option value="MANUAL_ONLY">仅手动</option>
          </select>
        </label>
        <label>
          <span>别名</span>
          <input
            :value="draft.aliases.join('，')"
            placeholder="多个别名用逗号分隔"
            @input="setAliases(($event.target as HTMLInputElement).value)"
          />
        </label>
      </div>

      <div class="taxonomy-grid">
        <fieldset>
          <legend>分类</legend>
          <div v-if="categories.length" class="option-list">
            <label v-for="category in categories" :key="category.categoryId" class="check-option">
              <input v-model="draft.categoryIds" type="checkbox" :value="category.categoryId" />
              <span>{{ category.name }}</span>
            </label>
          </div>
          <span v-else class="empty-option">暂无分类</span>
        </fieldset>
        <fieldset>
          <legend>标签</legend>
          <div v-if="tags.length" class="option-list">
            <label v-for="tag in tags" :key="tag.tagId" class="check-option">
              <input v-model="draft.tagIds" type="checkbox" :value="tag.tagId" />
              <i :style="{ backgroundColor: tag.color || '#8b93a7' }"></i>
              <span>{{ tag.name }}</span>
            </label>
          </div>
          <span v-else class="empty-option">暂无标签</span>
        </fieldset>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StoryBibleCategory, StoryBibleNodeType, StoryBibleTag } from '@/entities/story-bible/model'
import type { StoryBibleNodeDraft } from '@/composables/workbench/useStoryBible'
import StoryBibleAttributeEditor from './StoryBibleAttributeEditor.vue'
import { parseStoryBibleTypeSchema } from './storyBibleSchema'

const props = defineProps<{
  draft: StoryBibleNodeDraft
  nodeTypes: StoryBibleNodeType[]
  categories: StoryBibleCategory[]
  tags: StoryBibleTag[]
}>()

const activeType = computed(() => props.nodeTypes.find((item) => item.typeId === props.draft.typeId))
const activeSchema = computed(() => parseStoryBibleTypeSchema(activeType.value?.fieldSchemaJson))
const isStoryCore = computed(() => activeType.value?.typeCode === 'STORY_CORE')
const setAliases = (value: string) => {
  props.draft.aliases = value
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
</script>

<style scoped lang="less">
.base-tab { display: grid; padding: 0 18px 24px; }
.identity-section { position: relative; display: grid; grid-template-columns: 4px minmax(0, 1fr); gap: 14px; padding: 18px 0; }
.type-marker { min-height: 100%; border-radius: 2px; background: var(--type-color); }
.identity-fields { display: grid; gap: 12px; }
label { min-width: 0; display: grid; gap: 5px; color: var(--text-secondary); font-size: .74rem; }
input, textarea, select { width: 100%; min-width: 0; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); background: var(--bg-surface); outline: none; }
input, select { height: 34px; padding: 0 9px; }
textarea { padding: 9px; resize: vertical; line-height: 1.6; }
input:focus, textarea:focus, select:focus { border-color: var(--accent-border); box-shadow: 0 0 0 2px var(--focus-ring); }
select:disabled { opacity: .72; cursor: not-allowed; }
.field-grid { display: grid; gap: 10px; }
.two-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.compact-grid { max-width: 620px; }
.type-description { margin: -2px 0 0; color: var(--text-muted); font-size: .7rem; line-height: 1.55; }
.content-section, .metadata-section { padding: 16px 0; border-top: 1px solid var(--border-subtle); }
.content-section > header, .metadata-section > header { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
h3, .section-title { margin: 0; color: var(--text-primary); font-size: .82rem; font-weight: 600; }
.content-section header span { color: var(--text-muted); font-size: .65rem; }
.taxonomy-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-top: 16px; }
fieldset { min-width: 0; margin: 0; padding: 8px 0 0; border: 0; border-top: 1px solid var(--border-subtle); }
legend { padding: 0 8px 0 0; color: var(--text-muted); font-size: .7rem; }
.option-list { display: flex; flex-wrap: wrap; gap: 6px; padding-top: 5px; }
.check-option { display: flex; grid-template-columns: none; flex-direction: row; align-items: center; gap: 5px; min-height: 28px; padding: 3px 7px; border: 1px solid var(--border-subtle); border-radius: 3px; background: var(--bg-surface); }
.check-option input { width: 14px; height: 14px; }
.check-option i { width: 7px; height: 7px; flex: 0 0 7px; border-radius: 50%; }
.empty-option { display: block; padding-top: 8px; color: var(--text-muted); font-size: .7rem; }
@media (max-width: 720px) {
  .base-tab { padding-inline: 14px; }
  .two-columns, .taxonomy-grid { grid-template-columns: 1fr; }
}
</style>
