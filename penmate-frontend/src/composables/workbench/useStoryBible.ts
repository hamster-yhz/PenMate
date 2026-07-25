import { computed, ref } from 'vue'
import { storyBibleApi } from '@/api/modules/storyBible.api'
import type {
  StoryBibleCanonStatus,
  StoryBibleCategory,
  StoryBibleChangeset,
  StoryBibleInclusionPolicy,
  StoryBibleNode,
  StoryBibleNodeDetails,
  StoryBibleNodePayload,
  StoryBibleNodeType,
  StoryBibleProgression,
  StoryBibleProgressionUpdatePayload,
  StoryBibleRelation,
  StoryBibleRelationUpdatePayload,
  StoryBibleRoot,
  StoryBibleTag,
  StoryBibleViewPreference,
} from '@/entities/story-bible/model'

export interface StoryBibleNodeDraft extends StoryBibleNodePayload {
  nodeId?: string
  revision?: number
}

type Context = {
  projectId: string
  operatorId: string
  userId?: string
  sessionId?: string
  chapterId?: string
  projectTitle?: string
}

type UseStoryBibleOptions = {
  getContext: () => Context
  notify?: (message: string) => void
  notifySuccess?: (message: string) => void
}

const emptyDraft = (typeId = ''): StoryBibleNodeDraft => ({
  typeId,
  title: '',
  summary: '',
  bodyMarkdown: '',
  attributesJson: '{}',
  inclusionPolicy: 'AUTO_RETRIEVE',
  canonStatus: 'DRAFT',
  aliases: [],
  categoryIds: [],
  tagIds: [],
})

const toDraft = (details: StoryBibleNodeDetails): StoryBibleNodeDraft => ({
  nodeId: details.node.nodeId,
  revision: details.node.revision,
  typeId: details.node.typeId,
  title: details.node.title,
  summary: details.node.summary ?? '',
  bodyMarkdown: details.node.bodyMarkdown ?? '',
  attributesJson: details.node.attributesJson || '{}',
  inclusionPolicy: details.node.inclusionPolicy,
  canonStatus: details.node.canonStatus,
  aliases: details.aliases.map((item) => item.alias),
  categoryIds: [...details.categoryIds],
  tagIds: [...details.tagIds],
})

export const useStoryBible = (options: UseStoryBibleOptions) => {
  const root = ref<StoryBibleRoot | null>(null)
  const views = ref<StoryBibleViewPreference[]>([])
  const nodeTypes = ref<StoryBibleNodeType[]>([])
  const nodes = ref<StoryBibleNode[]>([])
  const categories = ref<StoryBibleCategory[]>([])
  const tags = ref<StoryBibleTag[]>([])
  const relations = ref<StoryBibleRelation[]>([])
  const progressions = ref<StoryBibleProgression[]>([])
  const history = ref<StoryBibleChangeset[]>([])
  const nodeHistory = ref<StoryBibleChangeset[]>([])
  const selectedNodeId = ref('')
  const selectedTypeId = ref('')
  const selectedFamily = ref<string>('')
  const selectedCategoryId = ref('')
  const selectedTagId = ref('')
  const searchQuery = ref('')
  const canonFilter = ref<StoryBibleCanonStatus | ''>('')
  const draft = ref<StoryBibleNodeDraft | null>(null)
  const effectiveState = ref<Record<string, unknown> | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const errorMessage = ref('')

  const visibleTypes = computed(() =>
    nodeTypes.value
      .filter((item) => !selectedFamily.value || item.semanticFamily === selectedFamily.value)
      .sort((a, b) => a.sortOrder - b.sortOrder),
  )

  const filteredNodes = computed(() => {
    return nodes.value.filter((node) => {
      const nodeType = nodeTypes.value.find((item) => item.typeId === node.typeId)
      if (selectedFamily.value && nodeType?.semanticFamily !== selectedFamily.value) return false
      if (selectedTypeId.value && node.typeId !== selectedTypeId.value) return false
      if (canonFilter.value && node.canonStatus !== canonFilter.value) return false
      return true
    })
  })

  const selectedNode = computed(() => nodes.value.find((node) => node.nodeId === selectedNodeId.value) || null)
  const selectedRelations = computed(() =>
    relations.value.filter(
      (relation) => relation.sourceNodeId === selectedNodeId.value || relation.targetNodeId === selectedNodeId.value,
    ),
  )
  const selectedProgressions = computed(() => progressions.value.filter((item) => item.nodeId === selectedNodeId.value))

  const requireContext = () => {
    const context = options.getContext()
    if (!context.projectId || !context.operatorId) throw new Error('Story Bible context is incomplete')
    return context
  }

  const reportError = (error: unknown, fallback: string) => {
    const message = String((error as Error)?.message || fallback)
    errorMessage.value = message
    options.notify?.(message)
  }

  const refreshRevisionAndHistory = async () => {
    const { projectId } = requireContext()
    const selectedId = selectedNodeId.value
    const [nextRoot, nextHistory, nextNodeHistory] = await Promise.all([
      storyBibleApi.get(projectId),
      storyBibleApi.listChanges(projectId),
      selectedId ? storyBibleApi.listNodeChanges(projectId, selectedId) : Promise.resolve([]),
    ])
    root.value = nextRoot
    history.value = nextHistory
    nodeHistory.value = nextNodeHistory
  }

  let latestNodeQuery = 0
  const refreshNodes = async () => {
    const { projectId } = requireContext()
    const queryId = ++latestNodeQuery
    try {
      const nextNodes = await storyBibleApi.listNodes(projectId, {
        typeId: selectedTypeId.value || undefined,
        status: canonFilter.value || undefined,
        query: searchQuery.value.trim() || undefined,
        categoryId: selectedCategoryId.value || undefined,
        tagId: selectedTagId.value || undefined,
      })
      if (queryId === latestNodeQuery) nodes.value = nextNodes
    } catch (error) {
      if (queryId === latestNodeQuery) reportError(error, 'Failed to filter Story Bible nodes')
    }
  }

  const loadWorkspace = async () => {
    const context = requireContext()
    latestNodeQuery += 1
    loading.value = true
    errorMessage.value = ''
    try {
      root.value = await storyBibleApi.bootstrap(
        context.projectId,
        context.operatorId,
        context.projectTitle || 'Story Bible',
      )
      const [nextViews, nextTypes, nextNodes, nextCategories, nextTags, nextRelations, nextProgressions, nextHistory] =
        await Promise.all([
          storyBibleApi.listViews(context.projectId),
          storyBibleApi.listNodeTypes(context.projectId),
          storyBibleApi.listNodes(context.projectId),
          storyBibleApi.listCategories(context.projectId),
          storyBibleApi.listTags(context.projectId),
          storyBibleApi.listRelations(context.projectId),
          storyBibleApi.listProgressions(context.projectId),
          storyBibleApi.listChanges(context.projectId),
        ])
      views.value = nextViews
      nodeTypes.value = nextTypes
      nodes.value = nextNodes
      categories.value = nextCategories
      tags.value = nextTags
      relations.value = nextRelations
      progressions.value = nextProgressions
      history.value = nextHistory
      if (selectedNodeId.value && nodes.value.some((node) => node.nodeId === selectedNodeId.value)) {
        await selectNode(selectedNodeId.value)
      }
    } catch (error) {
      reportError(error, 'Failed to load Story Bible')
    } finally {
      loading.value = false
    }
  }

  const selectNode = async (nodeId: string) => {
    const { projectId, chapterId } = requireContext()
    selectedNodeId.value = nodeId
    draft.value = null
    effectiveState.value = null
    nodeHistory.value = []
    try {
      const [details, nextNodeHistory, nextEffectiveState] = await Promise.all([
        storyBibleApi.getNode(projectId, nodeId),
        storyBibleApi.listNodeChanges(projectId, nodeId),
        chapterId ? storyBibleApi.getEffectiveState(projectId, nodeId, chapterId) : Promise.resolve(null),
      ])
      draft.value = toDraft(details)
      nodeHistory.value = nextNodeHistory
      effectiveState.value = nextEffectiveState
    } catch (error) {
      reportError(error, 'Failed to load Story Bible node')
    }
  }

  const createNodeDraft = (typeId = selectedTypeId.value || nodeTypes.value[0]?.typeId || '') => {
    selectedNodeId.value = ''
    selectedTypeId.value = typeId
    effectiveState.value = null
    nodeHistory.value = []
    draft.value = emptyDraft(typeId)
  }

  const saveNode = async () => {
    const context = requireContext()
    if (!draft.value || !draft.value.typeId || !draft.value.title.trim()) {
      reportError(new Error('Type and title are required'), 'Type and title are required')
      return
    }
    saving.value = true
    errorMessage.value = ''
    try {
      const payload: StoryBibleNodePayload = {
        typeId: draft.value.typeId,
        title: draft.value.title.trim(),
        summary: draft.value.summary,
        bodyMarkdown: draft.value.bodyMarkdown,
        attributesJson: draft.value.attributesJson || '{}',
        inclusionPolicy: draft.value.inclusionPolicy,
        canonStatus: draft.value.canonStatus,
        aliases: [...draft.value.aliases],
        categoryIds: [...draft.value.categoryIds],
        tagIds: [...draft.value.tagIds],
      }
      const saved = draft.value.nodeId
        ? await storyBibleApi.updateNode(context.projectId, draft.value.nodeId, context.operatorId, {
            ...payload,
            expectedRevision: draft.value.revision || 1,
          })
        : await storyBibleApi.createNode(context.projectId, context.operatorId, payload)
      const index = nodes.value.findIndex((node) => node.nodeId === saved.nodeId)
      if (index >= 0) nodes.value.splice(index, 1, saved)
      else nodes.value.unshift(saved)
      selectedNodeId.value = saved.nodeId
      await selectNode(saved.nodeId)
      history.value = await storyBibleApi.listChanges(context.projectId)
      options.notifySuccess?.('Story Bible saved')
    } catch (error) {
      reportError(error, 'Failed to save Story Bible node')
    } finally {
      saving.value = false
    }
  }

  const deleteSelectedNode = async () => {
    const context = requireContext()
    if (!draft.value?.nodeId || !draft.value.revision) return
    saving.value = true
    try {
      await storyBibleApi.deleteNode(context.projectId, draft.value.nodeId, context.operatorId, draft.value.revision)
      nodes.value = nodes.value.filter((node) => node.nodeId !== draft.value?.nodeId)
      selectedNodeId.value = ''
      draft.value = null
      effectiveState.value = null
      nodeHistory.value = []
      history.value = await storyBibleApi.listChanges(context.projectId)
      options.notifySuccess?.('Story Bible node deleted')
    } catch (error) {
      reportError(error, 'Failed to delete Story Bible node')
    } finally {
      saving.value = false
    }
  }

  const createRelation = async (payload: Omit<StoryBibleRelation, 'relationId' | 'storyBibleId' | 'revision'>) => {
    const context = requireContext()
    const relation = await storyBibleApi.createRelation(context.projectId, context.operatorId, payload)
    relations.value.push(relation)
    await refreshRevisionAndHistory()
  }

  const updateRelation = async (relationId: string, payload: StoryBibleRelationUpdatePayload) => {
    const context = requireContext()
    const relation = await storyBibleApi.updateRelation(context.projectId, relationId, context.operatorId, payload)
    const index = relations.value.findIndex((item) => item.relationId === relationId)
    if (index >= 0) relations.value.splice(index, 1, relation)
    await refreshRevisionAndHistory()
  }

  const deleteRelation = async (relation: StoryBibleRelation) => {
    const context = requireContext()
    await storyBibleApi.deleteRelation(context.projectId, relation.relationId, context.operatorId, relation.revision)
    relations.value = relations.value.filter((item) => item.relationId !== relation.relationId)
    await refreshRevisionAndHistory()
  }

  const createProgression = async (
    payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>,
  ) => {
    const context = requireContext()
    if (!selectedNodeId.value) return
    const progression = await storyBibleApi.createProgression(
      context.projectId,
      selectedNodeId.value,
      context.operatorId,
      payload,
    )
    progressions.value.push(progression)
    await selectNode(selectedNodeId.value)
    await refreshRevisionAndHistory()
  }

  const updateProgression = async (progressionId: string, payload: StoryBibleProgressionUpdatePayload) => {
    const context = requireContext()
    const progression = await storyBibleApi.updateProgression(
      context.projectId,
      progressionId,
      context.operatorId,
      payload,
    )
    const index = progressions.value.findIndex((item) => item.progressionId === progressionId)
    if (index >= 0) progressions.value.splice(index, 1, progression)
    if (selectedNodeId.value) await selectNode(selectedNodeId.value)
    await refreshRevisionAndHistory()
  }

  const deleteProgression = async (progression: StoryBibleProgression) => {
    const context = requireContext()
    await storyBibleApi.deleteProgression(
      context.projectId,
      progression.progressionId,
      context.operatorId,
      progression.revision,
    )
    progressions.value = progressions.value.filter((item) => item.progressionId !== progression.progressionId)
    if (selectedNodeId.value) await selectNode(selectedNodeId.value)
    await refreshRevisionAndHistory()
  }

  const saveNodeType = async (
    payload: Omit<StoryBibleNodeType, 'typeId' | 'storyBibleId' | 'system'> & { typeId?: string },
  ) => {
    const context = requireContext()
    const { typeId, ...command } = payload
    const saved = typeId
      ? await storyBibleApi.updateNodeType(context.projectId, typeId, context.operatorId, command)
      : await storyBibleApi.createNodeType(context.projectId, context.operatorId, command)
    const index = nodeTypes.value.findIndex((item) => item.typeId === saved.typeId)
    if (index >= 0) nodeTypes.value.splice(index, 1, saved)
    else nodeTypes.value.push(saved)
    await refreshRevisionAndHistory()
  }

  const archiveNodeType = async (type: StoryBibleNodeType) => {
    const context = requireContext()
    await storyBibleApi.archiveNodeType(context.projectId, type.typeId, context.operatorId)
    nodeTypes.value = nodeTypes.value.filter((item) => item.typeId !== type.typeId)
    await refreshRevisionAndHistory()
  }

  const saveCategory = async (
    payload: Pick<StoryBibleCategory, 'parentCategoryId' | 'name' | 'sortOrder'> & { categoryId?: string },
  ) => {
    const context = requireContext()
    const { categoryId, ...command } = payload
    const saved = categoryId
      ? await storyBibleApi.updateCategory(context.projectId, categoryId, context.operatorId, command)
      : await storyBibleApi.createCategory(context.projectId, context.operatorId, command)
    const index = categories.value.findIndex((item) => item.categoryId === saved.categoryId)
    if (index >= 0) categories.value.splice(index, 1, saved)
    else categories.value.push(saved)
    await refreshRevisionAndHistory()
  }

  const deleteCategory = async (category: StoryBibleCategory) => {
    const context = requireContext()
    await storyBibleApi.deleteCategory(context.projectId, category.categoryId, context.operatorId)
    categories.value = categories.value.filter((item) => item.categoryId !== category.categoryId)
    await refreshRevisionAndHistory()
  }

  const saveTag = async (payload: Pick<StoryBibleTag, 'name' | 'color'> & { tagId?: string }) => {
    const context = requireContext()
    const { tagId, ...command } = payload
    const saved = tagId
      ? await storyBibleApi.updateTag(context.projectId, tagId, context.operatorId, command)
      : await storyBibleApi.createTag(context.projectId, context.operatorId, command)
    const index = tags.value.findIndex((item) => item.tagId === saved.tagId)
    if (index >= 0) tags.value.splice(index, 1, saved)
    else tags.value.push(saved)
    await refreshRevisionAndHistory()
  }

  const deleteTag = async (tag: StoryBibleTag) => {
    const context = requireContext()
    await storyBibleApi.deleteTag(context.projectId, tag.tagId, context.operatorId)
    tags.value = tags.value.filter((item) => item.tagId !== tag.tagId)
    await refreshRevisionAndHistory()
  }

  const saveViewPreference = async (view: StoryBibleViewPreference) => {
    const context = requireContext()
    const saved = await storyBibleApi.updateView(context.projectId, view.viewCode, context.operatorId, view)
    const index = views.value.findIndex((item) => item.viewCode === saved.viewCode)
    if (index >= 0) views.value.splice(index, 1, saved)
    await refreshRevisionAndHistory()
  }

  return {
    root,
    views,
    nodeTypes,
    visibleTypes,
    nodes,
    filteredNodes,
    categories,
    tags,
    relations,
    progressions,
    history,
    nodeHistory,
    selectedNodeId,
    selectedNode,
    selectedTypeId,
    selectedFamily,
    selectedCategoryId,
    selectedTagId,
    selectedRelations,
    selectedProgressions,
    searchQuery,
    canonFilter,
    draft,
    effectiveState,
    loading,
    saving,
    errorMessage,
    loadWorkspace,
    refreshNodes,
    selectNode,
    createNodeDraft,
    saveNode,
    deleteSelectedNode,
    createRelation,
    updateRelation,
    deleteRelation,
    createProgression,
    updateProgression,
    deleteProgression,
    saveNodeType,
    archiveNodeType,
    saveCategory,
    deleteCategory,
    saveTag,
    deleteTag,
    saveViewPreference,
  }
}

export type StoryBibleNodeDraftCanonStatus = StoryBibleCanonStatus
export type StoryBibleNodeDraftInclusionPolicy = StoryBibleInclusionPolicy
