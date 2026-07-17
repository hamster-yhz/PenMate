<template>
  <div v-if="open" class="editor-overlay" @click.self="emit('close')">
    <section class="type-editor" aria-label="Story Bible 结构管理">
      <header>
        <div>
          <strong>结构管理</strong>
          <span>类型、分类与标签</span>
        </div>
        <button type="button" class="icon-button" title="关闭" @click="emit('close')"><CloseOutlined /></button>
      </header>

      <div class="manager-grid">
        <section>
          <h3>自定义类型</h3>
          <div v-for="type in nodeTypes" :key="type.typeId" class="manager-row">
            <span>{{ type.displayName }} <small>{{ type.semanticFamily }}</small></span>
            <div class="manager-actions">
              <button type="button" title="编辑类型" @click="startTypeEdit(type)"><EditOutlined /></button>
              <button v-if="!type.system" type="button" title="归档类型" class="danger" @click="emit('archiveType', type)"><DeleteOutlined /></button>
            </div>
          </div>
          <form class="inline-form" @submit.prevent="submitType">
            <input v-model="typeDraft.displayName" placeholder="类型名称" required />
            <input v-model="typeDraft.typeCode" placeholder="TYPE_CODE" required :disabled="!!typeDraft.typeId" />
            <select v-model="typeDraft.semanticFamily" :disabled="!!typeDraft.typeId">
              <option v-for="family in families" :key="family" :value="family">{{ family }}</option>
            </select>
            <div class="form-actions">
              <button type="submit"><SaveOutlined v-if="typeDraft.typeId" /><PlusOutlined v-else /> {{ typeDraft.typeId ? '保存' : '新建' }}</button>
              <button v-if="typeDraft.typeId" type="button" title="取消编辑" @click="resetTypeDraft"><CloseOutlined /></button>
            </div>
          </form>
        </section>

        <section>
          <h3>分类</h3>
          <div v-for="category in categories" :key="category.categoryId" class="manager-row">
            <span>{{ category.name }}</span>
            <div class="manager-actions">
              <button type="button" title="编辑分类" @click="startCategoryEdit(category)"><EditOutlined /></button>
              <button type="button" title="删除分类" class="danger" @click="emit('deleteCategory', category)"><DeleteOutlined /></button>
            </div>
          </div>
          <form class="inline-form compact" @submit.prevent="submitCategory">
            <input v-model="categoryName" placeholder="分类名称" required />
            <div class="form-actions">
              <button type="submit"><SaveOutlined v-if="categoryId" /><PlusOutlined v-else /> {{ categoryId ? '保存' : '新建' }}</button>
              <button v-if="categoryId" type="button" title="取消编辑" @click="resetCategoryDraft"><CloseOutlined /></button>
            </div>
          </form>
        </section>

        <section>
          <h3>标签</h3>
          <div v-for="tag in tags" :key="tag.tagId" class="manager-row">
            <span><i :style="{ backgroundColor: tag.color || '#8b93a7' }"></i>{{ tag.name }}</span>
            <div class="manager-actions">
              <button type="button" title="编辑标签" @click="startTagEdit(tag)"><EditOutlined /></button>
              <button type="button" title="删除标签" class="danger" @click="emit('deleteTag', tag)"><DeleteOutlined /></button>
            </div>
          </div>
          <form class="inline-form tag-form" @submit.prevent="submitTag">
            <input v-model="tagName" placeholder="标签名称" required />
            <input v-model="tagColor" type="color" aria-label="标签颜色" />
            <div class="form-actions">
              <button type="submit"><SaveOutlined v-if="tagId" /><PlusOutlined v-else /> {{ tagId ? '保存' : '新建' }}</button>
              <button v-if="tagId" type="button" title="取消编辑" @click="resetTagDraft"><CloseOutlined /></button>
            </div>
          </form>
        </section>

        <section>
          <h3>系统视图</h3>
          <div v-for="view in views" :key="view.viewCode" class="view-row">
            <input v-model="view.displayName" :aria-label="`${view.viewCode} 名称`" />
            <input v-model.number="view.sortOrder" type="number" :aria-label="`${view.viewCode} 顺序`" />
            <label><input v-model="view.hidden" type="checkbox" />隐藏</label>
            <button type="button" title="保存视图" @click="emit('saveView', view)"><SaveOutlined /></button>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type { StoryBibleCategory, StoryBibleNodeType, StoryBibleSemanticFamily, StoryBibleTag, StoryBibleViewPreference } from '@/api/modules/storyBible.api'

defineProps<{ open: boolean; nodeTypes: StoryBibleNodeType[]; categories: StoryBibleCategory[]; tags: StoryBibleTag[]; views: StoryBibleViewPreference[] }>()
const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saveType', payload: Record<string, unknown>): void
  (event: 'archiveType', payload: StoryBibleNodeType): void
  (event: 'saveCategory', payload: Record<string, unknown>): void
  (event: 'deleteCategory', payload: StoryBibleCategory): void
  (event: 'saveTag', payload: Record<string, unknown>): void
  (event: 'deleteTag', payload: StoryBibleTag): void
  (event: 'saveView', payload: StoryBibleViewPreference): void
}>()

const families: StoryBibleSemanticFamily[] = ['CORE', 'CHARACTER', 'WORLD', 'THING', 'NARRATIVE', 'TIMELINE']
const typeDraft = reactive({
  typeId: '',
  typeCode: '',
  semanticFamily: 'WORLD' as StoryBibleSemanticFamily,
  displayName: '',
  iconCode: 'bookmark',
  fieldSchemaJson: '{}',
  sortOrder: 500,
})
const categoryId = ref('')
const categoryName = ref('')
const categoryParentId = ref<string | null>(null)
const categorySortOrder = ref(0)
const tagId = ref('')
const tagName = ref('')
const tagColor = ref('#6f8fa8')

const submitType = () => {
  emit('saveType', { ...typeDraft })
  resetTypeDraft()
}
const startTypeEdit = (type: StoryBibleNodeType) => {
  Object.assign(typeDraft, {
    typeId: type.typeId,
    typeCode: type.typeCode,
    semanticFamily: type.semanticFamily,
    displayName: type.displayName,
    iconCode: type.iconCode || 'bookmark',
    fieldSchemaJson: type.fieldSchemaJson,
    sortOrder: type.sortOrder,
  })
}
const resetTypeDraft = () => {
  typeDraft.typeId = ''
  typeDraft.typeCode = ''
  typeDraft.displayName = ''
  typeDraft.semanticFamily = 'WORLD'
  typeDraft.iconCode = 'bookmark'
  typeDraft.fieldSchemaJson = '{}'
  typeDraft.sortOrder = 500
}
const submitCategory = () => {
  emit('saveCategory', {
    categoryId: categoryId.value || undefined,
    parentCategoryId: categoryParentId.value,
    name: categoryName.value,
    sortOrder: categorySortOrder.value,
  })
  resetCategoryDraft()
}
const startCategoryEdit = (category: StoryBibleCategory) => {
  categoryId.value = category.categoryId
  categoryName.value = category.name
  categoryParentId.value = category.parentCategoryId || null
  categorySortOrder.value = category.sortOrder
}
const resetCategoryDraft = () => {
  categoryId.value = ''
  categoryName.value = ''
  categoryParentId.value = null
  categorySortOrder.value = 0
}
const submitTag = () => {
  emit('saveTag', { tagId: tagId.value || undefined, name: tagName.value, color: tagColor.value })
  resetTagDraft()
}
const startTagEdit = (tag: StoryBibleTag) => {
  tagId.value = tag.tagId
  tagName.value = tag.name
  tagColor.value = tag.color || '#6f8fa8'
}
const resetTagDraft = () => {
  tagId.value = ''
  tagName.value = ''
  tagColor.value = '#6f8fa8'
}
</script>

<style scoped lang="less">
.editor-overlay { position: fixed; inset: 0; z-index: 300; display: grid; place-items: center; padding: 20px; background: rgba(3, 7, 16, 0.76); }
.type-editor { width: min(920px, 100%); max-height: min(720px, 90vh); overflow: auto; border: 1px solid var(--border-gold); border-radius: 6px; background: rgba(11, 17, 32, 0.98); box-shadow: var(--shadow-lg); }
header { height: 58px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; border-bottom: 1px solid var(--border-subtle); }
header div { display: grid; gap: 2px; }
header span { color: var(--text-muted); font-size: 0.7rem; }
.icon-button { width: 32px; height: 32px; border: 0; color: var(--text-secondary); background: transparent; cursor: pointer; }
.manager-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.manager-grid > section { min-width: 0; padding: 14px; border-right: 1px solid var(--border-subtle); }
.manager-grid > section:last-child { border-right: 0; }
h3 { margin: 0 0 10px; color: var(--amber-gold); font-size: 0.84rem; }
.manager-row { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 8px; border-bottom: 1px solid var(--border-subtle); color: var(--text-secondary); font-size: 0.78rem; }
.manager-row small { color: var(--text-muted); }
.manager-row button { border: 0; color: var(--amber-gold); background: transparent; cursor: pointer; }
.manager-row button.danger { color: #c9827b; }
.manager-actions, .form-actions { display: flex; align-items: center; gap: 3px; }
.manager-actions button { width: 28px; height: 28px; }
.manager-row span { display: flex; align-items: center; gap: 6px; }
.manager-row i { width: 8px; height: 8px; border-radius: 50%; }
.inline-form { display: grid; gap: 6px; margin-top: 12px; }
.inline-form.compact { grid-template-columns: 1fr auto; }
.tag-form { grid-template-columns: 1fr 36px auto; }
input, select, .inline-form button { min-width: 0; height: 32px; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); background: rgba(17, 24, 39, 0.9); }
input, select { padding: 0 8px; }
.inline-form button { padding: 0 10px; color: var(--amber-gold); border-color: var(--border-gold); cursor: pointer; }
.form-actions > button:last-child:not(:first-child) { width: 32px; padding: 0; }
.view-row { display: grid; grid-template-columns: minmax(100px, 1fr) 64px 58px 30px; gap: 5px; align-items: center; margin-bottom: 6px; }
.view-row label { display: flex; align-items: center; gap: 3px; color: var(--text-muted); font-size: 0.68rem; }
.view-row label input { width: 14px; height: 14px; }
.view-row button { height: 30px; border: 0; color: var(--amber-gold); background: transparent; cursor: pointer; }
@media (max-width: 760px) { .manager-grid { grid-template-columns: 1fr; } .manager-grid > section { border-right: 0; border-bottom: 1px solid var(--border-subtle); } }
</style>
