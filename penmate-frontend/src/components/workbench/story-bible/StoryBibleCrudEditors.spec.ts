import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StoryBibleProgressionsTab from './StoryBibleProgressionsTab.vue'
import StoryBibleRelationsTab from './StoryBibleRelationsTab.vue'
import StoryBibleTypeEditor from './StoryBibleTypeEditor.vue'

const nodes = [
  {
    nodeId: '71',
    storyBibleId: '11',
    typeId: '21',
    title: 'Mira',
    attributesJson: '{}',
    inclusionPolicy: 'AUTO_RETRIEVE' as const,
    canonStatus: 'CANON' as const,
    revision: 1,
  },
  {
    nodeId: '72',
    storyBibleId: '11',
    typeId: '21',
    title: 'Nox',
    attributesJson: '{}',
    inclusionPolicy: 'AUTO_RETRIEVE' as const,
    canonStatus: 'CANON' as const,
    revision: 1,
  },
]

describe('Story Bible CRUD editors', () => {
  it('emits optimistic relation updates with the current revision', async () => {
    const wrapper = mount(StoryBibleRelationsTab, {
      props: {
        nodeId: '71',
        nodes,
        relations: [
          {
            relationId: '91',
            storyBibleId: '11',
            sourceNodeId: '71',
            targetNodeId: '72',
            relationType: 'ALLY_OF',
            description: 'Old',
            attributesJson: '{}',
            revision: 2,
          },
        ],
      },
    })

    await wrapper.get('[title="编辑关系"]').trigger('click')
    await wrapper.get('[aria-label="编辑关系说明"]').setValue('New')
    await wrapper.get('[title="保存关系"]').trigger('click')

    expect(wrapper.emitted('update')?.[0]).toEqual([
      {
        relationId: '91',
        update: expect.objectContaining({ expectedRevision: 2, description: 'New' }),
      },
    ])
  })

  it('emits optimistic progression updates with the current revision', async () => {
    const wrapper = mount(StoryBibleProgressionsTab, {
      props: {
        chapterId: '301',
        chapters: [
          { chapterId: '301', displayNo: 1, title: 'Opening' },
          { chapterId: '302', displayNo: 2, title: 'Reveal' },
        ],
        effectiveState: null,
        progressions: [
          {
            progressionId: '92',
            storyBibleId: '11',
            nodeId: '71',
            anchorChapterId: '301',
            endChapterId: null,
            storyEventNodeId: null,
            patchJson: '[]',
            summary: 'Old',
            revision: 4,
          },
        ],
      },
    })

    await wrapper.get('[title="编辑状态演进"]').trigger('click')
    await wrapper.get('[aria-label="编辑变化摘要"]').setValue('New')
    await wrapper.get('[title="保存状态演进"]').trigger('click')

    expect(wrapper.emitted('update')?.[0]).toEqual([
      {
        progressionId: '92',
        update: expect.objectContaining({ expectedRevision: 4, summary: 'New' }),
      },
    ])
  })

  it('shows progression anchors with full-book chapter numbers instead of business ids', () => {
    const wrapper = mount(StoryBibleProgressionsTab, {
      props: {
        chapterId: '301',
        chapters: [
          { chapterId: '301', displayNo: 1, title: 'Opening' },
          { chapterId: '302', displayNo: 2, title: 'Reveal' },
        ],
        effectiveState: null,
        progressions: [
          {
            progressionId: '92',
            storyBibleId: '11',
            nodeId: '71',
            anchorChapterId: '301',
            endChapterId: '302',
            storyEventNodeId: null,
            patchJson: '[]',
            summary: 'Changed',
            revision: 4,
          },
        ],
      },
    })

    expect(wrapper.text()).toContain('第 1 章 · Opening')
    expect(wrapper.text()).toContain('第 2 章 · Reveal')
    expect(wrapper.find('.anchor').text()).not.toContain('301')
    expect(wrapper.find('.anchor').text()).not.toContain('302')
  })

  it('supports editing types categories and tags from structure management', async () => {
    const wrapper = mount(StoryBibleTypeEditor, {
      props: {
        open: true,
        nodeTypes: [
          {
            typeId: '21',
            storyBibleId: '11',
            typeCode: 'CHARACTER',
            semanticFamily: 'CHARACTER',
            displayName: 'Character',
            iconCode: 'user',
            fieldSchemaJson: '{}',
            system: true,
            sortOrder: 1,
          },
        ],
        categories: [{ categoryId: '31', storyBibleId: '11', parentCategoryId: null, name: 'Cast', sortOrder: 1 }],
        tags: [{ tagId: '41', storyBibleId: '11', name: 'Lead', normalizedName: 'lead', color: '#112233' }],
        views: [],
      },
    })

    await wrapper.get('[title="编辑类型"]').trigger('click')
    await wrapper.findAll('form')[0].get('input').setValue('Person')
    await wrapper.findAll('form')[0].trigger('submit')
    await wrapper.get('[title="编辑分类"]').trigger('click')
    await wrapper.findAll('form')[1].get('input').setValue('Main cast')
    await wrapper.findAll('form')[1].trigger('submit')
    await wrapper.get('[title="编辑标签"]').trigger('click')
    await wrapper.findAll('form')[2].findAll('input')[0].setValue('Primary')
    await wrapper.findAll('form')[2].trigger('submit')

    expect(wrapper.emitted('saveType')?.[0]?.[0]).toMatchObject({ typeId: '21', displayName: 'Person' })
    expect(wrapper.emitted('saveCategory')?.[0]?.[0]).toMatchObject({ categoryId: '31', name: 'Main cast' })
    expect(wrapper.emitted('saveTag')?.[0]?.[0]).toMatchObject({ tagId: '41', name: 'Primary' })
  })
})
