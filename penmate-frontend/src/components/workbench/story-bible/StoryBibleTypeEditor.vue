<template>
  <div v-if="open" class="editor-overlay">
    <section ref="editorRef" class="type-editor" role="dialog" aria-modal="true" aria-label="Story Bible 结构管理" tabindex="-1">
      <header>
        <div>
          <strong>结构管理</strong>
          <span>类型、分类与标签</span>
        </div>
        <button type="button" class="icon-button" title="关闭" aria-label="关闭结构管理" @click="emit('close')"><CloseOutlined /></button>
      </header>

      <div class="manager-grid">
        <section>
          <h3>自定义类型</h3>
          <div v-for="type in nodeTypes" :key="type.typeId" class="manager-row">
            <span><i :style="{ backgroundColor: typeColor(type) }"></i>{{ type.displayName }} <small v-if="type.system">内置</small></span>
            <div class="manager-actions">
              <button v-if="!type.system" type="button" title="编辑类型" @click="startTypeEdit(type)"><EditOutlined /></button>
              <button
                v-if="!type.system"
                type="button"
                title="归档类型"
                class="danger"
                @click="emit('archiveType', type)"
              >
                <DeleteOutlined />
              </button>
            </div>
          </div>
          <form class="inline-form" @submit.prevent="submitType">
            <input v-model="typeDraft.displayName" data-dialog-initial-focus placeholder="类型名称" required />
            <div class="type-appearance">
              <label><span>图标</span><select v-model="typeDraft.iconCode"><option value="user">人物</option><option value="environment">地点</option><option value="team">组织</option><option value="gift">物品</option><option value="global">世界规则</option><option value="calendar">事件</option><option value="bulb">线索</option><option value="bookmark">其他</option></select></label>
              <label><span>颜色</span><input v-model="typeColorValue" type="color" aria-label="类型颜色" /></label>
            </div>
            <div class="field-editor">
              <header><span>字段定义</span><button type="button" @click="addField"><PlusOutlined />添加字段</button></header>
              <div v-for="field in typeFields" :key="field.id" class="field-row">
                <input v-model.trim="field.name" placeholder="字段名称" required />
                <select v-model="field.type" aria-label="字段类型"><option value="string">文本</option><option value="number">数字</option><option value="boolean">是 / 否</option></select>
                <button type="button" title="删除字段" class="danger" @click="removeField(field.id)"><DeleteOutlined /></button>
              </div>
              <p v-if="!typeFields.length">未定义额外字段</p>
            </div>
            <div class="form-actions">
              <button type="submit">
                <SaveOutlined v-if="typeDraft.typeId" /><PlusOutlined v-else /> {{ typeDraft.typeId ? '保存' : '新建' }}
              </button>
              <button v-if="typeDraft.typeId" type="button" title="取消编辑" @click="resetTypeDraft">
                <CloseOutlined />
              </button>
            </div>
          </form>
        </section>

        <section>
          <h3>分类</h3>
          <div v-for="category in categories" :key="category.categoryId" class="manager-row">
            <span>{{ category.name }}</span>
            <div class="manager-actions">
              <button type="button" title="编辑分类" @click="startCategoryEdit(category)"><EditOutlined /></button>
              <button type="button" title="删除分类" class="danger" @click="emit('deleteCategory', category)">
                <DeleteOutlined />
              </button>
            </div>
          </div>
          <form class="inline-form compact" @submit.prevent="submitCategory">
            <input v-model="categoryName" placeholder="分类名称" required />
            <div class="form-actions">
              <button type="submit">
                <SaveOutlined v-if="categoryId" /><PlusOutlined v-else /> {{ categoryId ? '保存' : '新建' }}
              </button>
              <button v-if="categoryId" type="button" title="取消编辑" @click="resetCategoryDraft">
                <CloseOutlined />
              </button>
            </div>
          </form>
        </section>

        <section>
          <h3>标签</h3>
          <div v-for="tag in tags" :key="tag.tagId" class="manager-row">
            <span><i :style="{ backgroundColor: tag.color || '#8b93a7' }"></i>{{ tag.name }}</span>
            <div class="manager-actions">
              <button type="button" title="编辑标签" @click="startTagEdit(tag)"><EditOutlined /></button>
              <button type="button" title="删除标签" class="danger" @click="emit('deleteTag', tag)">
                <DeleteOutlined />
              </button>
            </div>
          </div>
          <form class="inline-form tag-form" @submit.prevent="submitTag">
            <input v-model="tagName" placeholder="标签名称" required />
            <input v-model="tagColor" type="color" aria-label="标签颜色" />
            <div class="form-actions">
              <button type="submit">
                <SaveOutlined v-if="tagId" /><PlusOutlined v-else /> {{ tagId ? '保存' : '新建' }}
              </button>
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
import type {
  StoryBibleCategory,
  StoryBibleNodeType,
  StoryBibleTag,
  StoryBibleViewPreference,
} from '@/entities/story-bible/model'
import { useDialogFocus } from '@/composables/useDialogFocus'

const props = defineProps<{
  open: boolean
  nodeTypes: StoryBibleNodeType[]
  categories: StoryBibleCategory[]
  tags: StoryBibleTag[]
  views: StoryBibleViewPreference[]
}>()
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
const editorRef = ref<HTMLElement | null>(null)
useDialogFocus({ open: () => props.open, dialog: editorRef, close: () => emit('close') })

interface TypeFieldDraft { id: number; name: string; type: 'string' | 'number' | 'boolean' }
let fieldId = 0
const typeDraft = reactive({
  typeId: '',
  typeCode: '',
  semanticFamily: 'WORLD' as const,
  displayName: '',
  iconCode: 'bookmark',
  fieldSchemaJson: '{}',
  sortOrder: 500,
})
const categoryId = ref('')
const typeColorValue = ref('#6f8fa8')
const typeFields = ref<TypeFieldDraft[]>([])
const categoryName = ref('')
const categoryParentId = ref<string | null>(null)
const categorySortOrder = ref(0)
const tagId = ref('')
const tagName = ref('')
const tagColor = ref('#6f8fa8')

const submitType = () => {
  const properties = Object.fromEntries(typeFields.value.map((field) => [field.name.trim(), { type: field.type, title: field.name.trim() }]))
  emit('saveType', {
    ...typeDraft,
    typeCode: typeDraft.typeCode || `CUSTOM_${Date.now().toString(36).toUpperCase()}`,
    fieldSchemaJson: JSON.stringify({ type: 'object', properties, 'x-penmate-color': typeColorValue.value }),
  })
  resetTypeDraft()
}
const schemaDetails = (type: StoryBibleNodeType) => {
  try {
    return JSON.parse(type.fieldSchemaJson || '{}') as { properties?: Record<string, { type?: string; title?: string }>; 'x-penmate-color'?: string }
  } catch { return {} }
}
const typeColor = (type: StoryBibleNodeType) => schemaDetails(type)['x-penmate-color'] || '#6f8fa8'
const startTypeEdit = (type: StoryBibleNodeType) => {
  const schema = schemaDetails(type)
  Object.assign(typeDraft, {
    typeId: type.typeId,
    typeCode: type.typeCode,
    semanticFamily: type.semanticFamily,
    displayName: type.displayName,
    iconCode: type.iconCode || 'bookmark',
    fieldSchemaJson: type.fieldSchemaJson,
    sortOrder: type.sortOrder,
  })
  typeColorValue.value = schema['x-penmate-color'] || '#6f8fa8'
  typeFields.value = Object.entries(schema.properties || {}).map(([key, value]) => ({ id: ++fieldId, name: value.title || key, type: ['number', 'boolean'].includes(value.type || '') ? value.type as 'number' | 'boolean' : 'string' }))
}
const resetTypeDraft = () => {
  typeDraft.typeId = ''
  typeDraft.typeCode = ''
  typeDraft.displayName = ''
  typeDraft.semanticFamily = 'WORLD'
  typeDraft.iconCode = 'bookmark'
  typeDraft.fieldSchemaJson = '{}'
  typeDraft.sortOrder = 500
  typeColorValue.value = '#6f8fa8'
  typeFields.value = []
}
const addField = () => typeFields.value.push({ id: ++fieldId, name: '', type: 'string' })
const removeField = (id: number) => { typeFields.value = typeFields.value.filter((field) => field.id !== id) }
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
.editor-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: grid;
  place-items: center;
  padding: 20px;
  background: var(--overlay);
}
.type-editor {
  width: min(920px, 100%);
  max-height: min(720px, 90vh);
  overflow: auto;
  color: var(--text-primary);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-lg);
}
header {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
}
header div {
  display: grid;
  gap: 2px;
}
header span {
  color: var(--text-muted);
  font-size: 0.7rem;
}
.icon-button {
  width: 32px;
  height: 32px;
  border: 0;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}
.manager-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.manager-grid > section {
  min-width: 0;
  padding: 14px;
  border-right: 1px solid var(--border-subtle);
}
.manager-grid > section:last-child {
  border-right: 0;
}
h3 {
  margin: 0 0 10px;
  color: var(--text-primary);
  font-size: 0.84rem;
}
.manager-row {
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  font-size: 0.78rem;
}
.manager-row small {
  color: var(--text-muted);
}
.manager-row button {
  border: 0;
  color: var(--accent);
  background: transparent;
  cursor: pointer;
}
.manager-row button.danger {
  color: var(--danger);
}
.manager-actions,
.form-actions {
  display: flex;
  align-items: center;
  gap: 3px;
}
.manager-actions button {
  width: 28px;
  height: 28px;
}
.manager-row span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.manager-row i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.inline-form {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}
.type-appearance { display: grid; grid-template-columns: 1fr 74px; gap: 6px; }
.type-appearance label { display: grid; gap: 4px; color: var(--text-muted); font-size: 11px; }
.type-appearance input[type='color'] { width: 100%; padding: 3px; }
.field-editor { border: 1px solid var(--border-subtle); border-radius: 4px; }
.field-editor header { min-height: 34px; height: auto; padding: 0 8px; }
.field-editor header button { display: inline-flex; align-items: center; gap: 4px; padding: 0; color: var(--accent); background: transparent; border: 0; }
.field-row { display: grid; grid-template-columns: 1fr 86px 28px; gap: 5px; padding: 6px; border-top: 1px solid var(--border-subtle); }
.field-row button { border: 0; color: var(--danger); background: transparent; }
.field-editor > p { margin: 0; padding: 10px 8px; color: var(--text-muted); font-size: 11px; }
.inline-form.compact {
  grid-template-columns: 1fr auto;
}
.tag-form {
  grid-template-columns: 1fr 36px auto;
}
input,
select,
.inline-form button {
  min-width: 0;
  height: 32px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  background: var(--bg-surface);
}
input,
select {
  padding: 0 8px;
}
.inline-form button {
  padding: 0 10px;
  color: var(--accent);
  border-color: var(--accent-border);
  cursor: pointer;
}
.form-actions > button:last-child:not(:first-child) {
  width: 32px;
  padding: 0;
}
.view-row {
  display: grid;
  grid-template-columns: minmax(100px, 1fr) 64px 58px 30px;
  gap: 5px;
  align-items: center;
  margin-bottom: 6px;
}
.view-row label {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--text-muted);
  font-size: 0.68rem;
}
.view-row label input {
  width: 14px;
  height: 14px;
}
.view-row button {
  height: 30px;
  border: 0;
  color: var(--accent);
  background: transparent;
  cursor: pointer;
}
@media (max-width: 760px) {
  .manager-grid {
    grid-template-columns: 1fr;
  }
  .manager-grid > section {
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle);
  }
}
</style>
