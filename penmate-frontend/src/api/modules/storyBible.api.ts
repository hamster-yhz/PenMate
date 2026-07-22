import request from '@/utils/request'
import type {
  StoryBibleCanonStatus,
  StoryBibleCategory,
  StoryBibleChangeset,
  StoryBibleChangesetDetails,
  StoryBibleNode,
  StoryBibleNodeDetails,
  StoryBibleNodePayload,
  StoryBibleNodeType,
  StoryBibleNodeUpdatePayload,
  StoryBibleProgression,
  StoryBibleProgressionUpdatePayload,
  StoryBibleRelation,
  StoryBibleRelationUpdatePayload,
  StoryBibleRoot,
  StoryBibleTag,
  StoryBibleViewPreference,
} from '@/entities/story-bible/model'

export type * from '@/entities/story-bible/model'

const base = (projectId: string) => `/v1/novels/${projectId}/story-bible`
// Kept temporarily to preserve caller signatures while actor identity comes exclusively from the bearer session.
const operatorQuery = (_operatorId: string) => {
  void _operatorId
  return ''
}
const optionalNodeIds = (nodeIds?: string[]) =>
  nodeIds?.length ? `?${nodeIds.map((id) => `nodeIds=${encodeURIComponent(id)}`).join('&')}` : ''

export const storyBibleApi = {
  get(projectId: string) {
    return request.get<StoryBibleRoot>(base(projectId))
  },
  bootstrap(projectId: string, operatorId: string, projectTitle: string) {
    return request.post<StoryBibleRoot>(`${base(projectId)}${operatorQuery(operatorId)}`, { projectTitle })
  },
  listViews(projectId: string) {
    return request.get<StoryBibleViewPreference[]>(`${base(projectId)}/views`)
  },
  updateView(projectId: string, viewCode: string, operatorId: string, payload: Partial<StoryBibleViewPreference>) {
    return request.patch<StoryBibleViewPreference>(
      `${base(projectId)}/views/${encodeURIComponent(viewCode)}${operatorQuery(operatorId)}`,
      payload,
    )
  },
  listNodeTypes(projectId: string) {
    return request.get<StoryBibleNodeType[]>(`${base(projectId)}/node-types`)
  },
  createNodeType(
    projectId: string,
    operatorId: string,
    payload: Omit<StoryBibleNodeType, 'typeId' | 'storyBibleId' | 'system'>,
  ) {
    return request.post<StoryBibleNodeType>(`${base(projectId)}/node-types${operatorQuery(operatorId)}`, payload)
  },
  updateNodeType(
    projectId: string,
    typeId: string,
    operatorId: string,
    payload: Pick<StoryBibleNodeType, 'displayName' | 'iconCode' | 'fieldSchemaJson' | 'sortOrder'>,
  ) {
    return request.patch<StoryBibleNodeType>(
      `${base(projectId)}/node-types/${typeId}${operatorQuery(operatorId)}`,
      payload,
    )
  },
  archiveNodeType(projectId: string, typeId: string, operatorId: string) {
    return request.delete<string>(`${base(projectId)}/node-types/${typeId}${operatorQuery(operatorId)}`)
  },
  listNodes(
    projectId: string,
    filters: {
      typeId?: string
      status?: StoryBibleCanonStatus
      query?: string
      categoryId?: string
      tagId?: string
    } = {},
  ) {
    const params = new URLSearchParams()
    if (filters.typeId) params.set('typeId', filters.typeId)
    if (filters.status) params.set('status', filters.status)
    if (filters.query) params.set('query', filters.query)
    if (filters.categoryId) params.set('categoryId', filters.categoryId)
    if (filters.tagId) params.set('tagId', filters.tagId)
    const suffix = params.size ? `?${params.toString()}` : ''
    return request.get<StoryBibleNode[]>(`${base(projectId)}/nodes${suffix}`)
  },
  getNode(projectId: string, nodeId: string) {
    return request.get<StoryBibleNodeDetails>(`${base(projectId)}/nodes/${nodeId}`)
  },
  createNode(projectId: string, operatorId: string, payload: StoryBibleNodePayload) {
    return request.post<StoryBibleNode>(`${base(projectId)}/nodes${operatorQuery(operatorId)}`, payload)
  },
  updateNode(projectId: string, nodeId: string, operatorId: string, payload: StoryBibleNodeUpdatePayload) {
    return request.patch<StoryBibleNode>(`${base(projectId)}/nodes/${nodeId}${operatorQuery(operatorId)}`, payload)
  },
  deleteNode(projectId: string, nodeId: string, operatorId: string, expectedRevision: number) {
    return request.delete<string>(
      `${base(projectId)}/nodes/${nodeId}?expectedRevision=${expectedRevision}${operatorQuery(operatorId)}`,
    )
  },
  getEffectiveState(projectId: string, nodeId: string, chapterId: string) {
    return request.get<Record<string, unknown>>(
      `${base(projectId)}/nodes/${nodeId}/effective-state?chapterId=${encodeURIComponent(chapterId)}`,
    )
  },
  listCategories(projectId: string) {
    return request.get<StoryBibleCategory[]>(`${base(projectId)}/categories`)
  },
  createCategory(
    projectId: string,
    operatorId: string,
    payload: Pick<StoryBibleCategory, 'parentCategoryId' | 'name' | 'sortOrder'>,
  ) {
    return request.post<StoryBibleCategory>(`${base(projectId)}/categories${operatorQuery(operatorId)}`, payload)
  },
  updateCategory(
    projectId: string,
    categoryId: string,
    operatorId: string,
    payload: Pick<StoryBibleCategory, 'parentCategoryId' | 'name' | 'sortOrder'>,
  ) {
    return request.patch<StoryBibleCategory>(
      `${base(projectId)}/categories/${categoryId}${operatorQuery(operatorId)}`,
      payload,
    )
  },
  deleteCategory(projectId: string, categoryId: string, operatorId: string) {
    return request.delete<string>(`${base(projectId)}/categories/${categoryId}${operatorQuery(operatorId)}`)
  },
  listTags(projectId: string) {
    return request.get<StoryBibleTag[]>(`${base(projectId)}/tags`)
  },
  createTag(projectId: string, operatorId: string, payload: Pick<StoryBibleTag, 'name' | 'color'>) {
    return request.post<StoryBibleTag>(`${base(projectId)}/tags${operatorQuery(operatorId)}`, payload)
  },
  updateTag(projectId: string, tagId: string, operatorId: string, payload: Pick<StoryBibleTag, 'name' | 'color'>) {
    return request.patch<StoryBibleTag>(`${base(projectId)}/tags/${tagId}${operatorQuery(operatorId)}`, payload)
  },
  deleteTag(projectId: string, tagId: string, operatorId: string) {
    return request.delete<string>(`${base(projectId)}/tags/${tagId}${operatorQuery(operatorId)}`)
  },
  listRelations(projectId: string, nodeIds?: string[]) {
    return request.get<StoryBibleRelation[]>(`${base(projectId)}/relations${optionalNodeIds(nodeIds)}`)
  },
  createRelation(
    projectId: string,
    operatorId: string,
    payload: Omit<StoryBibleRelation, 'relationId' | 'storyBibleId' | 'revision'>,
  ) {
    return request.post<StoryBibleRelation>(`${base(projectId)}/relations${operatorQuery(operatorId)}`, payload)
  },
  updateRelation(projectId: string, relationId: string, operatorId: string, payload: StoryBibleRelationUpdatePayload) {
    return request.patch<StoryBibleRelation>(
      `${base(projectId)}/relations/${relationId}${operatorQuery(operatorId)}`,
      payload,
    )
  },
  deleteRelation(projectId: string, relationId: string, operatorId: string, expectedRevision: number) {
    return request.delete<string>(
      `${base(projectId)}/relations/${relationId}?expectedRevision=${expectedRevision}${operatorQuery(operatorId)}`,
    )
  },
  listProgressions(projectId: string, nodeIds?: string[]) {
    return request.get<StoryBibleProgression[]>(`${base(projectId)}/progressions${optionalNodeIds(nodeIds)}`)
  },
  createProgression(
    projectId: string,
    nodeId: string,
    operatorId: string,
    payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>,
  ) {
    return request.post<StoryBibleProgression>(
      `${base(projectId)}/nodes/${nodeId}/progressions${operatorQuery(operatorId)}`,
      payload,
    )
  },
  updateProgression(
    projectId: string,
    progressionId: string,
    operatorId: string,
    payload: StoryBibleProgressionUpdatePayload,
  ) {
    return request.patch<StoryBibleProgression>(
      `${base(projectId)}/progressions/${progressionId}${operatorQuery(operatorId)}`,
      payload,
    )
  },
  deleteProgression(projectId: string, progressionId: string, operatorId: string, expectedRevision: number) {
    return request.delete<string>(
      `${base(projectId)}/progressions/${progressionId}?expectedRevision=${expectedRevision}${operatorQuery(operatorId)}`,
    )
  },
  listChanges(projectId: string, limit = 50) {
    return request.get<StoryBibleChangeset[]>(`${base(projectId)}/changesets?limit=${limit}`)
  },
  getChangeset(projectId: string, changesetId: string) {
    return request.get<StoryBibleChangesetDetails>(`${base(projectId)}/changesets/${changesetId}`)
  },
  listNodeChanges(projectId: string, nodeId: string, limit = 50) {
    return request.get<StoryBibleChangeset[]>(`${base(projectId)}/nodes/${nodeId}/changesets?limit=${limit}`)
  },
}
