<template>
  <div v-if="open" class="editor-overlay">
    <section ref="editorRef" class="type-editor" role="dialog" aria-modal="true" aria-label="Story Bible 结构管理" tabindex="-1">
      <header class="dialog-header">
        <div><strong>结构管理</strong><span>类型、分类与标签</span></div>
        <button type="button" class="icon-button" title="关闭" aria-label="关闭结构管理" @click="emit('close')"><CloseOutlined /></button>
      </header>

      <div class="manager-grid">
        <section class="types-section">
          <h3>节点类型</h3>
          <div class="type-list">
            <div v-for="type in nodeTypes" :key="type.typeId" class="type-entry">
              <div class="manager-row">
                <span class="type-name">
                  <i :style="{ backgroundColor: typeColor(type) }"></i>
                  {{ type.displayName }}
                  <small>{{ type.system ? '内置' : '自定义' }} · {{ schemaFieldCount(type) }} 个字段</small>
                </span>
                <div class="manager-actions">
                  <button v-if="!type.system" type="button" title="编辑类型" @click="startTypeEdit(type)"><EditOutlined /></button>
                  <button v-if="!type.system" type="button" title="归档类型" class="danger" @click="emit('archiveType', type)"><DeleteOutlined /></button>
                </div>
              </div>
              <details v-if="type.system && schemaFieldCount(type)">
                <summary>查看字段</summary>
                <div class="schema-field-list">
                  <span v-for="field in parsedSchema(type).fields" :key="field.key">
                    <strong>{{ field.title }}</strong><code>{{ field.key }}</code>
                  </span>
                </div>
              </details>
            </div>
          </div>

          <form class="type-form" @submit.prevent="submitType">
            <div class="form-heading">
              <strong>{{ typeDraft.typeId ? `编辑 ${typeDraft.displayName}` : '新建自定义类型' }}</strong>
              <button v-if="typeDraft.typeId" type="button" title="取消编辑" @click="resetTypeDraft"><CloseOutlined />取消</button>
            </div>
            <div class="type-basics">
              <label><span>类型名称</span><input v-model.trim="typeDraft.displayName" data-dialog-initial-focus placeholder="例如：城市" required /></label>
              <label><span>语义分组</span><select v-model="typeDraft.semanticFamily"><option value="CHARACTER">人物</option><option value="WORLD">世界</option><option value="THING">事物</option><option value="NARRATIVE">叙事</option><option value="TIMELINE">时间线</option></select></label>
              <label><span>图标</span><select v-model="typeDraft.iconCode"><option value="user">人物</option><option value="environment">地点</option><option value="team">组织</option><option value="gift">物品</option><option value="global">世界规则</option><option value="calendar">事件</option><option value="bulb">线索</option><option value="bookmark">其他</option></select></label>
              <label><span>颜色</span><input v-model="typeColorValue" type="color" aria-label="类型颜色" /></label>
            </div>
            <div class="schema-metadata">
              <label><span>类型说明</span><input v-model.trim="typeDescription" placeholder="这个类型记录什么，以及不记录什么" /></label>
              <label><span>标题占位</span><input v-model.trim="titlePlaceholder" placeholder="例如：城市名称" /></label>
              <label><span>摘要占位</span><input v-model.trim="summaryPlaceholder" placeholder="例如：这座城市的核心特征" /></label>
            </div>

            <div class="field-editor">
              <header><div><strong>专属字段</strong><span>{{ typeFields.length }} 项</span></div><button type="button" @click="addField"><PlusOutlined />添加字段</button></header>
              <div v-for="(field, index) in typeFields" :key="field.id" class="field-row">
                <div class="field-primary">
                  <label><span>显示名</span><input v-model.trim="field.label" placeholder="例如：人口规模" required /></label>
                  <label><span>稳定键</span><input v-model.trim="field.key" :disabled="Boolean(field.originalKey)" pattern="[A-Za-z][A-Za-z0-9_]*" required /></label>
                  <label><span>控件</span><select v-model="field.control"><option value="string">单行文本</option><option value="multiline">多行文本</option><option value="string-list">文本列表</option><option value="integer">整数</option><option value="number">数字</option><option value="boolean">是 / 否</option><option value="enum">枚举选项</option></select></label>
                  <button type="button" title="删除字段" class="danger" @click="removeField(field.id)"><DeleteOutlined /></button>
                </div>
                <div class="field-secondary">
                  <label><span>分组</span><input v-model.trim="field.section" placeholder="例如：基础资料" /></label>
                  <label><span>输入提示</span><input v-model.trim="field.placeholder" placeholder="输入框中的示例" /></label>
                  <label><span>字段说明</span><input v-model.trim="field.description" placeholder="含义或填写边界" /></label>
                </div>
                <label v-if="field.control === 'enum'" class="enum-options">
                  <span>枚举选项</span>
                  <textarea v-model="field.optionsText" rows="2" placeholder="每行一项；可写 值 | 显示名"></textarea>
                </label>
                <span class="field-order">{{ index + 1 }}</span>
              </div>
              <p v-if="!typeFields.length">未定义专属字段</p>
            </div>
            <p v-if="typeFormError" class="form-error" role="alert">{{ typeFormError }}</p>
            <div class="form-actions"><button type="submit"><SaveOutlined v-if="typeDraft.typeId" /><PlusOutlined v-else />{{ typeDraft.typeId ? '保存类型' : '新建类型' }}</button></div>
          </form>
        </section>

        <section>
          <h3>分类</h3>
          <div v-for="category in categories" :key="category.categoryId" class="manager-row">
            <span>{{ category.name }}</span>
            <div class="manager-actions"><button type="button" title="编辑分类" @click="startCategoryEdit(category)"><EditOutlined /></button><button type="button" title="删除分类" class="danger" @click="emit('deleteCategory', category)"><DeleteOutlined /></button></div>
          </div>
          <form class="inline-form compact" @submit.prevent="submitCategory"><input v-model.trim="categoryName" placeholder="分类名称" required /><div class="form-actions"><button type="submit"><SaveOutlined v-if="categoryId" /><PlusOutlined v-else />{{ categoryId ? '保存' : '新建' }}</button><button v-if="categoryId" type="button" title="取消编辑" @click="resetCategoryDraft"><CloseOutlined /></button></div></form>
        </section>

        <section>
          <h3>标签</h3>
          <div v-for="tag in tags" :key="tag.tagId" class="manager-row">
            <span><i :style="{ backgroundColor: tag.color || '#8b93a7' }"></i>{{ tag.name }}</span>
            <div class="manager-actions"><button type="button" title="编辑标签" @click="startTagEdit(tag)"><EditOutlined /></button><button type="button" title="删除标签" class="danger" @click="emit('deleteTag', tag)"><DeleteOutlined /></button></div>
          </div>
          <form class="inline-form tag-form" @submit.prevent="submitTag"><input v-model.trim="tagName" placeholder="标签名称" required /><input v-model="tagColor" type="color" aria-label="标签颜色" /><div class="form-actions"><button type="submit"><SaveOutlined v-if="tagId" /><PlusOutlined v-else />{{ tagId ? '保存' : '新建' }}</button><button v-if="tagId" type="button" title="取消编辑" @click="resetTagDraft"><CloseOutlined /></button></div></form>
        </section>

        <section class="views-section">
          <h3>系统视图</h3>
          <div v-for="view in views" :key="view.viewCode" class="view-row"><input v-model="view.displayName" :aria-label="`${view.viewCode} 名称`" /><input v-model.number="view.sortOrder" type="number" :aria-label="`${view.viewCode} 顺序`" /><label><input v-model="view.hidden" type="checkbox" />隐藏</label><button type="button" title="保存视图" @click="emit('saveView', view)"><SaveOutlined /></button></div>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type { StoryBibleCategory, StoryBibleNodeType, StoryBibleSemanticFamily, StoryBibleTag, StoryBibleViewPreference } from '@/entities/story-bible/model'
import { useDialogFocus } from '@/composables/useDialogFocus'
import { parseStoryBibleTypeSchema, storyBibleSchemaFieldCount, type StoryBibleFieldControl } from './storyBibleSchema'

const props = defineProps<{ open: boolean; nodeTypes: StoryBibleNodeType[]; categories: StoryBibleCategory[]; tags: StoryBibleTag[]; views: StoryBibleViewPreference[] }>()
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

type RawProperty = Record<string, unknown> & { type?: string; title?: string; enum?: string[]; description?: string; 'x-penmate-control'?: StoryBibleFieldControl; 'x-penmate-section'?: string; 'x-penmate-placeholder'?: string; 'x-penmate-enum-labels'?: Record<string, string> }
type RawSchema = Record<string, unknown> & { properties?: Record<string, RawProperty>; 'x-penmate-color'?: string; 'x-penmate-description'?: string; 'x-penmate-title-placeholder'?: string; 'x-penmate-summary-placeholder'?: string }
interface TypeFieldDraft { id: number; originalKey: string; key: string; label: string; control: StoryBibleFieldControl; section: string; description: string; placeholder: string; optionsText: string; raw: RawProperty }

let fieldId = 0
const typeDraft = reactive({ typeId: '', typeCode: '', semanticFamily: 'WORLD' as StoryBibleSemanticFamily, displayName: '', iconCode: 'bookmark', fieldSchemaJson: '{}', sortOrder: 500 })
const typeColorValue = ref('#6f8fa8')
const typeDescription = ref('')
const titlePlaceholder = ref('')
const summaryPlaceholder = ref('')
const typeFields = ref<TypeFieldDraft[]>([])
const typeFormError = ref('')
const originalSchema = ref<RawSchema>({})
const categoryId = ref('')
const categoryName = ref('')
const categoryParentId = ref<string | null>(null)
const categorySortOrder = ref(0)
const tagId = ref('')
const tagName = ref('')
const tagColor = ref('#6f8fa8')

const schemaDetails = (type: StoryBibleNodeType): RawSchema => { try { return JSON.parse(type.fieldSchemaJson || '{}') as RawSchema } catch { return {} } }
const parsedSchema = (type: StoryBibleNodeType) => parseStoryBibleTypeSchema(type.fieldSchemaJson)
const schemaFieldCount = (type: StoryBibleNodeType) => storyBibleSchemaFieldCount(type.fieldSchemaJson)
const typeColor = (type: StoryBibleNodeType) => parsedSchema(type).color
const controlFrom = (property: RawProperty): StoryBibleFieldControl => {
  if (property['x-penmate-control']) return property['x-penmate-control']
  if (Array.isArray(property.enum)) return 'enum'
  if (property.type === 'array') return 'string-list'
  if (property.type === 'integer' || property.type === 'number' || property.type === 'boolean') return property.type
  return 'string'
}
const optionsFrom = (property: RawProperty) => (property.enum || []).map((value) => {
  const label = property['x-penmate-enum-labels']?.[value]
  return label && label !== value ? `${value} | ${label}` : value
}).join('\n')
const parseOptions = (value: string) => value.split(/\r?\n|,/).map((item) => item.trim()).filter(Boolean).map((item) => {
  const [rawValue, ...rawLabel] = item.split('|')
  const optionValue = rawValue.trim()
  return { value: optionValue, label: rawLabel.join('|').trim() || optionValue }
})
const propertyFrom = (field: TypeFieldDraft, order: number): RawProperty => {
  const property: RawProperty = { ...field.raw, title: field.label, description: field.description || undefined, 'x-penmate-control': field.control, 'x-penmate-section': field.section || '专属字段', 'x-penmate-order': order, 'x-penmate-placeholder': field.placeholder || undefined }
  delete property.enum
  delete property.items
  delete property['x-penmate-enum-labels']
  if (field.control === 'string-list') { property.type = 'array'; property.items = { type: 'string' } }
  else if (field.control === 'integer' || field.control === 'number' || field.control === 'boolean') property.type = field.control
  else property.type = 'string'
  if (field.control === 'enum') {
    const options = parseOptions(field.optionsText)
    property.enum = options.map((option) => option.value)
    property['x-penmate-enum-labels'] = Object.fromEntries(options.map((option) => [option.value, option.label]))
  }
  return property
}
const submitType = () => {
  typeFormError.value = ''
  const keys = typeFields.value.map((field) => field.key.trim())
  if (new Set(keys).size !== keys.length) { typeFormError.value = '稳定键不能重复'; return }
  const invalidEnum = typeFields.value.find((field) => field.control === 'enum' && !parseOptions(field.optionsText).length)
  if (invalidEnum) { typeFormError.value = `“${invalidEnum.label}”至少需要一个枚举选项`; return }
  const properties = Object.fromEntries(typeFields.value.map((field, index) => [field.key.trim(), propertyFrom(field, (index + 1) * 10)]))
  const sectionTitles = typeFields.value.map((field) => field.section.trim() || '专属字段').filter((value, index, all) => all.indexOf(value) === index)
  const schema: RawSchema = { ...originalSchema.value, type: 'object', title: typeDraft.displayName, properties, additionalProperties: false, 'x-penmate-color': typeColorValue.value, 'x-penmate-description': typeDescription.value || undefined, 'x-penmate-title-placeholder': titlePlaceholder.value || undefined, 'x-penmate-summary-placeholder': summaryPlaceholder.value || undefined, 'x-penmate-sections': sectionTitles.map((title, index) => ({ key: title, title, order: (index + 1) * 10 })) }
  if (Array.isArray(schema.required)) schema.required = schema.required.filter((key) => keys.includes(String(key)))
  emit('saveType', { ...typeDraft, typeCode: typeDraft.typeCode || `CUSTOM_${Date.now().toString(36).toUpperCase()}`, fieldSchemaJson: JSON.stringify(schema) })
  resetTypeDraft()
}
const startTypeEdit = (type: StoryBibleNodeType) => {
  const schema = schemaDetails(type)
  originalSchema.value = schema
  Object.assign(typeDraft, { typeId: type.typeId, typeCode: type.typeCode, semanticFamily: type.semanticFamily, displayName: type.displayName, iconCode: type.iconCode || 'bookmark', fieldSchemaJson: type.fieldSchemaJson, sortOrder: type.sortOrder })
  typeColorValue.value = schema['x-penmate-color'] || '#6f8fa8'
  typeDescription.value = schema['x-penmate-description'] || ''
  titlePlaceholder.value = schema['x-penmate-title-placeholder'] || ''
  summaryPlaceholder.value = schema['x-penmate-summary-placeholder'] || ''
  typeFields.value = Object.entries(schema.properties || {}).map(([key, property]) => ({ id: ++fieldId, originalKey: key, key, label: property.title || key, control: controlFrom(property), section: property['x-penmate-section'] || '专属字段', description: property.description || '', placeholder: property['x-penmate-placeholder'] || '', optionsText: optionsFrom(property), raw: property }))
}
const resetTypeDraft = () => {
  Object.assign(typeDraft, { typeId: '', typeCode: '', semanticFamily: 'WORLD', displayName: '', iconCode: 'bookmark', fieldSchemaJson: '{}', sortOrder: 500 })
  typeColorValue.value = '#6f8fa8'; typeDescription.value = ''; titlePlaceholder.value = ''; summaryPlaceholder.value = ''; typeFields.value = []; typeFormError.value = ''; originalSchema.value = {}
}
const addField = () => { const id = ++fieldId; typeFields.value.push({ id, originalKey: '', key: `field_${id}`, label: '', control: 'string', section: '专属字段', description: '', placeholder: '', optionsText: '', raw: {} }) }
const removeField = (id: number) => { typeFields.value = typeFields.value.filter((field) => field.id !== id) }
const submitCategory = () => { emit('saveCategory', { categoryId: categoryId.value || undefined, parentCategoryId: categoryParentId.value, name: categoryName.value, sortOrder: categorySortOrder.value }); resetCategoryDraft() }
const startCategoryEdit = (category: StoryBibleCategory) => { categoryId.value = category.categoryId; categoryName.value = category.name; categoryParentId.value = category.parentCategoryId || null; categorySortOrder.value = category.sortOrder }
const resetCategoryDraft = () => { categoryId.value = ''; categoryName.value = ''; categoryParentId.value = null; categorySortOrder.value = 0 }
const submitTag = () => { emit('saveTag', { tagId: tagId.value || undefined, name: tagName.value, color: tagColor.value }); resetTagDraft() }
const startTagEdit = (tag: StoryBibleTag) => { tagId.value = tag.tagId; tagName.value = tag.name; tagColor.value = tag.color || '#6f8fa8' }
const resetTagDraft = () => { tagId.value = ''; tagName.value = ''; tagColor.value = '#6f8fa8' }
</script>

<style scoped lang="less">
.editor-overlay { position: fixed; inset: 0; z-index: 300; display: grid; place-items: center; padding: 20px; background: var(--overlay); }
.type-editor { width: min(1060px, 100%); max-height: min(820px, 92vh); overflow: auto; color: var(--text-primary); border: 1px solid var(--border-strong); border-radius: var(--radius-lg); background: var(--bg-surface); box-shadow: var(--shadow-lg); }
.dialog-header { position: sticky; top: 0; z-index: 2; height: 58px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; border-bottom: 1px solid var(--border-subtle); background: var(--bg-surface); }
.dialog-header div { display: grid; gap: 2px; }
.dialog-header span { color: var(--text-muted); font-size: .7rem; }
.icon-button { width: 32px; height: 32px; border: 0; color: var(--text-secondary); background: transparent; cursor: pointer; }
.manager-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.manager-grid > section { min-width: 0; padding: 16px; border-right: 1px solid var(--border-subtle); border-bottom: 1px solid var(--border-subtle); }
.types-section { grid-column: 1 / -1; border-right: 0 !important; }
.views-section { border-right: 0 !important; }
h3 { margin: 0 0 10px; font-size: .84rem; }
.type-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 18px; }
.type-entry { min-width: 0; border-bottom: 1px solid var(--border-subtle); }
.manager-row { min-height: 36px; display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--text-secondary); font-size: .78rem; }
.manager-row span { min-width: 0; display: flex; align-items: center; gap: 6px; }
.manager-row small { overflow: hidden; color: var(--text-muted); text-overflow: ellipsis; white-space: nowrap; }
.manager-row i { width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; }
.manager-actions, .form-actions { display: flex; align-items: center; gap: 3px; }
.manager-actions button, .manager-row button { width: 28px; height: 28px; border: 0; color: var(--accent); background: transparent; cursor: pointer; }
button.danger, .manager-row button.danger { color: var(--danger); }
details { padding: 0 0 8px 14px; color: var(--text-muted); font-size: .68rem; }
summary { width: max-content; cursor: pointer; }
.schema-field-list { display: flex; flex-wrap: wrap; gap: 5px; padding-top: 7px; }
.schema-field-list span { display: flex; gap: 4px; padding: 3px 5px; border: 1px solid var(--border-subtle); border-radius: 3px; }
.schema-field-list strong { color: var(--text-secondary); font-weight: 500; }
.schema-field-list code { color: var(--text-muted); }
.type-form { display: grid; gap: 12px; margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border-strong); }
.form-heading { display: flex; align-items: center; justify-content: space-between; }
.form-heading button { display: flex; align-items: center; gap: 4px; border: 0; color: var(--text-secondary); background: transparent; cursor: pointer; }
.type-basics { display: grid; grid-template-columns: minmax(180px, 1fr) 150px 130px 70px; gap: 8px; }
.schema-metadata { display: grid; grid-template-columns: 1.4fr 1fr 1fr; gap: 8px; }
label { min-width: 0; display: grid; gap: 4px; color: var(--text-muted); font-size: .68rem; }
input, select, textarea, .inline-form button, .form-actions button { min-width: 0; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); background: var(--bg-surface); }
input, select, .inline-form button, .form-actions button { height: 32px; }
input, select, textarea { padding: 0 8px; }
textarea { padding-block: 7px; resize: vertical; line-height: 1.4; }
input:disabled { color: var(--text-muted); background: var(--bg-subtle); }
.field-editor { border: 1px solid var(--border-subtle); border-radius: 4px; }
.field-editor > header { min-height: 38px; display: flex; align-items: center; justify-content: space-between; padding: 0 9px; border-bottom: 1px solid var(--border-subtle); }
.field-editor > header div { display: flex; align-items: baseline; gap: 7px; }
.field-editor > header span { color: var(--text-muted); font-size: .66rem; }
.field-editor > header button { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; color: var(--accent); background: transparent; cursor: pointer; }
.field-row { position: relative; display: grid; gap: 7px; padding: 9px 34px 9px 9px; border-bottom: 1px solid var(--border-subtle); }
.field-row:last-of-type { border-bottom: 0; }
.field-primary { display: grid; grid-template-columns: minmax(120px, 1fr) minmax(120px, .8fr) 120px 28px; gap: 7px; }
.field-secondary { display: grid; grid-template-columns: .65fr 1fr 1.4fr; gap: 7px; }
.field-primary > button { align-self: end; width: 28px; height: 32px; border: 0; background: transparent; cursor: pointer; }
.field-order { position: absolute; right: 9px; bottom: 10px; color: var(--text-muted); font-size: .62rem; }
.enum-options { max-width: 520px; }
.field-editor > p { margin: 0; padding: 12px 9px; color: var(--text-muted); font-size: .7rem; }
.form-error { margin: 0; color: var(--danger); font-size: .72rem; }
.form-actions > button, .inline-form button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 0 10px; color: var(--accent); border-color: var(--accent-border); cursor: pointer; }
.inline-form { display: grid; gap: 6px; margin-top: 12px; }
.inline-form.compact { grid-template-columns: 1fr auto; }
.tag-form { grid-template-columns: 1fr 36px auto; }
.form-actions > button:last-child:not(:first-child) { width: 32px; padding: 0; }
.view-row { display: grid; grid-template-columns: minmax(100px, 1fr) 64px 58px 30px; gap: 5px; align-items: center; margin-bottom: 6px; }
.view-row label { display: flex; align-items: center; gap: 3px; }
.view-row label input { width: 14px; height: 14px; }
.view-row button { height: 30px; border: 0; color: var(--accent); background: transparent; cursor: pointer; }
@media (max-width: 850px) { .type-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .type-basics, .schema-metadata { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .editor-overlay { padding: 0; } .type-editor { max-height: 100vh; height: 100vh; border: 0; border-radius: 0; } .manager-grid, .type-list, .type-basics, .schema-metadata, .field-primary, .field-secondary { grid-template-columns: 1fr; } .manager-grid > section { border-right: 0; } .field-primary > button { position: absolute; top: 8px; right: 4px; } .field-row { padding-right: 38px; } }
</style>
