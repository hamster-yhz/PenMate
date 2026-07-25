import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { StoryBibleNodeType } from '@/entities/story-bible/model'
import type { StoryBibleNodeDraft } from '@/composables/workbench/useStoryBible'
import StoryBibleAttributeEditor from './StoryBibleAttributeEditor.vue'
import StoryBibleBaseTab from './StoryBibleBaseTab.vue'
import StoryBibleTypeEditor from './StoryBibleTypeEditor.vue'
import { parseStoryBibleTypeSchema } from './storyBibleSchema'

const schema = (properties: Record<string, Record<string, unknown>>, metadata: Record<string, unknown> = {}) =>
  JSON.stringify({ type: 'object', properties, ...metadata })

const nodeType = (typeId: string, typeCode: string, displayName: string, fieldSchemaJson: string, system = true): StoryBibleNodeType => ({
  typeId,
  storyBibleId: '11',
  typeCode,
  semanticFamily: typeCode === 'CHARACTER' ? 'CHARACTER' : 'WORLD',
  displayName,
  iconCode: 'bookmark',
  fieldSchemaJson,
  system,
  sortOrder: 10,
})

const draft = reactive<StoryBibleNodeDraft>({
  nodeId: '71',
  typeId: 'character',
  title: 'Mira',
  summary: '',
  bodyMarkdown: '',
  attributesJson: '{}',
  inclusionPolicy: 'AUTO_RETRIEVE',
  canonStatus: 'CANON',
  aliases: [],
  categoryIds: [],
  tagIds: [],
  revision: 1,
})

describe('Story Bible schema forms', () => {
  it('parses field controls, sections and display metadata', () => {
    const parsed = parseStoryBibleTypeSchema(schema({
      traits: { type: 'array', title: '特征', 'x-penmate-section': 'profile', 'x-penmate-order': 20 },
      role: { type: 'string', title: '角色', enum: ['LEAD'], 'x-penmate-enum-labels': { LEAD: '主角' }, 'x-penmate-section': 'profile', 'x-penmate-order': 10 },
    }, {
      'x-penmate-color': '#123456',
      'x-penmate-sections': [{ key: 'profile', title: '档案' }],
    }))

    expect(parsed.color).toBe('#123456')
    expect(parsed.sections[0]?.title).toBe('档案')
    expect(parsed.fields.map((field) => [field.key, field.control])).toEqual([['role', 'enum'], ['traits', 'string-list']])
    expect(parsed.fields[0]?.enumLabels.LEAD).toBe('主角')
  })

  it('changes the dedicated form immediately when the node type changes', async () => {
    const localDraft = reactive({ ...draft, attributesJson: '{}' })
    const wrapper = mount(StoryBibleBaseTab, {
      props: {
        draft: localDraft,
        nodeTypes: [
          nodeType('character', 'CHARACTER', '人物', schema({ motivation: { type: 'string', title: '核心动机' } })),
          nodeType('location', 'LOCATION', '地点', schema({ climate: { type: 'string', title: '气候' } })),
        ],
        categories: [],
        tags: [],
      },
    })

    expect(wrapper.find('[data-field-key="motivation"]').exists()).toBe(true)
    await wrapper.get('.identity-section select').setValue('location')
    expect(wrapper.find('[data-field-key="motivation"]').exists()).toBe(false)
    expect(wrapper.find('[data-field-key="climate"]').exists()).toBe(true)
  })

  it('round-trips enum, optional boolean, list and integer values', async () => {
    const wrapper = mount(StoryBibleAttributeEditor, {
      props: {
        schemaJson: schema({
          role: { type: 'string', title: '角色', enum: ['LEAD', 'MINOR'] },
          active: { type: 'boolean', title: '启用' },
          traits: { type: 'array', title: '特征' },
          age: { type: 'integer', title: '年龄' },
        }),
        modelValue: '{}',
        'onUpdate:modelValue': (value: string) => wrapper.setProps({ modelValue: value }),
      },
    })

    await wrapper.get('[data-field-key="role"] select').setValue('LEAD')
    await wrapper.get('[data-field-key="active"] input[type="checkbox"]').setValue(true)
    await wrapper.get('[data-field-key="traits"] .add-list-item').trigger('click')
    await wrapper.get('[data-field-key="traits"] input').setValue('勇敢')
    await wrapper.get('[data-field-key="age"] input').setValue('24')

    expect(JSON.parse(wrapper.props('modelValue'))).toEqual({ role: 'LEAD', active: true, traits: ['勇敢'], age: 24 })
    await wrapper.get('[data-field-key="active"] [title="清除此项"]').trigger('click')
    expect(JSON.parse(wrapper.props('modelValue'))).not.toHaveProperty('active')
  })

  it('keeps an existing custom field key stable when its label changes', async () => {
    const wrapper = mount(StoryBibleTypeEditor, {
      props: {
        open: true,
        nodeTypes: [nodeType('custom', 'CUSTOM_CITY', '城市', schema({ population: { type: 'integer', title: '人口' } }), false)],
        categories: [],
        tags: [],
        views: [],
      },
    })

    await wrapper.get('[title="编辑类型"]').trigger('click')
    const field = wrapper.get('.field-row')
    expect(field.findAll('input')[1]?.attributes('disabled')).toBeDefined()
    await field.findAll('input')[0]!.setValue('常住人口')
    await wrapper.get('.type-form').trigger('submit')

    const payload = wrapper.emitted('saveType')?.[0]?.[0] as { fieldSchemaJson: string }
    const emittedSchema = JSON.parse(payload.fieldSchemaJson)
    expect(emittedSchema.properties.population.title).toBe('常住人口')
    expect(Object.keys(emittedSchema.properties)).toEqual(['population'])
    expect(emittedSchema.additionalProperties).toBe(false)
  })

  it('locks Story Core type, canon status and inclusion policy', () => {
    const coreDraft = reactive({ ...draft, typeId: 'core', inclusionPolicy: 'ALWAYS_INCLUDE' as const })
    const wrapper = mount(StoryBibleBaseTab, {
      props: { draft: coreDraft, nodeTypes: [nodeType('core', 'STORY_CORE', '故事核心', schema({ premise: { type: 'string' } }))], categories: [], tags: [] },
    })

    const disabledSelects = wrapper.findAll('select:disabled')
    expect(disabledSelects).toHaveLength(3)
  })
})
