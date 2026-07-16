import request from '@/utils/request'

export type StoryBibleSemanticFamily = 'CORE' | 'CHARACTER' | 'WORLD' | 'THING' | 'NARRATIVE' | 'TIMELINE'
export type StoryBibleInclusionPolicy = 'ALWAYS_INCLUDE' | 'AUTO_RETRIEVE' | 'MANUAL_ONLY'
export type StoryBibleCanonStatus = 'DRAFT' | 'CANON' | 'ARCHIVED'
export type StoryBibleRoutingMode = 'RETRIEVAL' | 'LLM_SELECTOR' | 'RETRIEVAL_THEN_LLM'

export interface StoryBibleRoot {
  storyBibleId: string
  projectId: string
  title: string
  description?: string | null
  contentRevision: number
}

export interface StoryBibleViewPreference {
  storyBibleId: string
  viewCode: string
  displayName: string
  hidden: boolean
  sortOrder: number
}

export interface StoryBibleNodeType {
  typeId: string
  storyBibleId: string
  typeCode: string
  semanticFamily: StoryBibleSemanticFamily
  displayName: string
  iconCode?: string | null
  fieldSchemaJson: string
  system: boolean
  sortOrder: number
}

export interface StoryBibleNode {
  nodeId: string
  storyBibleId: string
  typeId: string
  title: string
  summary?: string | null
  bodyMarkdown?: string | null
  attributesJson: string
  inclusionPolicy: StoryBibleInclusionPolicy
  canonStatus: StoryBibleCanonStatus
  revision: number
  createdAt?: string
  updatedAt?: string
}

export interface StoryBibleAlias {
  aliasId: string
  nodeId: string
  alias: string
}

export interface StoryBibleNodeDetails {
  node: StoryBibleNode
  aliases: StoryBibleAlias[]
  categoryIds: string[]
  tagIds: string[]
}

export interface StoryBibleCategory {
  categoryId: string
  storyBibleId: string
  parentCategoryId?: string | null
  name: string
  sortOrder: number
}

export interface StoryBibleTag {
  tagId: string
  storyBibleId: string
  name: string
  normalizedName: string
  color?: string | null
}

export interface StoryBibleRelation {
  relationId: string
  storyBibleId: string
  sourceNodeId: string
  relationType: string
  targetNodeId: string
  description?: string | null
  attributesJson: string
  revision: number
}

export interface StoryBibleRelationUpdatePayload {
  expectedRevision: number
  relationType: string
  targetNodeId: string
  description?: string | null
  attributesJson: string
}

export interface StoryBibleProgression {
  progressionId: string
  storyBibleId: string
  nodeId: string
  anchorChapterId: string
  endChapterId?: string | null
  storyEventNodeId?: string | null
  patchJson: string
  summary?: string | null
  revision: number
}

export interface StoryBibleProgressionUpdatePayload {
  expectedRevision: number
  anchorChapterId: string
  endChapterId?: string | null
  storyEventNodeId?: string | null
  patchJson: string
  summary?: string | null
}

export interface StoryBibleChangeset {
  changesetId: string
  storyBibleId: string
  contentRevision: number
  actorType: 'USER' | 'AGENT' | 'SYSTEM'
  actorId?: string | null
  sourceRunId?: string | null
  changeSummary: string
  createdAt: string
}

export interface StoryBibleRoutingPreference {
  mode: StoryBibleRoutingMode
  routerModelConfigId?: string | null
  routerModelConfigRevision: number
  inherited: boolean
}

export interface StoryBibleNodePayload {
  typeId: string
  title: string
  summary?: string | null
  bodyMarkdown?: string | null
  attributesJson: string
  inclusionPolicy: StoryBibleInclusionPolicy
  canonStatus: StoryBibleCanonStatus
  aliases: string[]
  categoryIds: string[]
  tagIds: string[]
}

export interface StoryBibleNodeUpdatePayload extends StoryBibleNodePayload {
  expectedRevision: number
}

const base = (projectId: string) => `/v1/novels/${projectId}/story-bible`
const operatorQuery = (operatorId: string) => `operatorId=${encodeURIComponent(operatorId)}`
const optionalNodeIds = (nodeIds?: string[]) => nodeIds?.length
  ? `?${nodeIds.map((id) => `nodeIds=${encodeURIComponent(id)}`).join('&')}`
  : ''

export const storyBibleApi = {
  get(projectId: string) {
    return request.get<StoryBibleRoot>(base(projectId))
  },
  bootstrap(projectId: string, operatorId: string, projectTitle: string) {
    return request.post<StoryBibleRoot>(`${base(projectId)}?${operatorQuery(operatorId)}`, { projectTitle })
  },
  listViews(projectId: string) {
    return request.get<StoryBibleViewPreference[]>(`${base(projectId)}/views`)
  },
  updateView(projectId: string, viewCode: string, operatorId: string, payload: Partial<StoryBibleViewPreference>) {
    return request.patch<StoryBibleViewPreference>(
      `${base(projectId)}/views/${encodeURIComponent(viewCode)}?${operatorQuery(operatorId)}`,
      payload,
    )
  },
  listNodeTypes(projectId: string) {
    return request.get<StoryBibleNodeType[]>(`${base(projectId)}/node-types`)
  },
  createNodeType(projectId: string, operatorId: string, payload: Omit<StoryBibleNodeType, 'typeId' | 'storyBibleId' | 'system'>) {
    return request.post<StoryBibleNodeType>(`${base(projectId)}/node-types?${operatorQuery(operatorId)}`, payload)
  },
  updateNodeType(projectId: string, typeId: string, operatorId: string, payload: Pick<StoryBibleNodeType, 'displayName' | 'iconCode' | 'fieldSchemaJson' | 'sortOrder'>) {
    return request.patch<StoryBibleNodeType>(`${base(projectId)}/node-types/${typeId}?${operatorQuery(operatorId)}`, payload)
  },
  archiveNodeType(projectId: string, typeId: string, operatorId: string) {
    return request.delete<string>(`${base(projectId)}/node-types/${typeId}?${operatorQuery(operatorId)}`)
  },
  listNodes(projectId: string, filters: { typeId?: string; status?: StoryBibleCanonStatus; query?: string } = {}) {
    const params = new URLSearchParams()
    if (filters.typeId) params.set('typeId', filters.typeId)
    if (filters.status) params.set('status', filters.status)
    if (filters.query) params.set('query', filters.query)
    const suffix = params.size ? `?${params.toString()}` : ''
    return request.get<StoryBibleNode[]>(`${base(projectId)}/nodes${suffix}`)
  },
  getNode(projectId: string, nodeId: string) {
    return request.get<StoryBibleNodeDetails>(`${base(projectId)}/nodes/${nodeId}`)
  },
  createNode(projectId: string, operatorId: string, payload: StoryBibleNodePayload) {
    return request.post<StoryBibleNode>(`${base(projectId)}/nodes?${operatorQuery(operatorId)}`, payload)
  },
  updateNode(projectId: string, nodeId: string, operatorId: string, payload: StoryBibleNodeUpdatePayload) {
    return request.patch<StoryBibleNode>(`${base(projectId)}/nodes/${nodeId}?${operatorQuery(operatorId)}`, payload)
  },
  deleteNode(projectId: string, nodeId: string, operatorId: string, expectedRevision: number) {
    return request.delete<string>(`${base(projectId)}/nodes/${nodeId}?expectedRevision=${expectedRevision}&${operatorQuery(operatorId)}`)
  },
  getEffectiveState(projectId: string, nodeId: string, chapterId: string) {
    return request.get<Record<string, unknown>>(`${base(projectId)}/nodes/${nodeId}/effective-state?chapterId=${encodeURIComponent(chapterId)}`)
  },
  listCategories(projectId: string) {
    return request.get<StoryBibleCategory[]>(`${base(projectId)}/categories`)
  },
  createCategory(projectId: string, operatorId: string, payload: Pick<StoryBibleCategory, 'parentCategoryId' | 'name' | 'sortOrder'>) {
    return request.post<StoryBibleCategory>(`${base(projectId)}/categories?${operatorQuery(operatorId)}`, payload)
  },
  updateCategory(projectId: string, categoryId: string, operatorId: string, payload: Pick<StoryBibleCategory, 'parentCategoryId' | 'name' | 'sortOrder'>) {
    return request.patch<StoryBibleCategory>(`${base(projectId)}/categories/${categoryId}?${operatorQuery(operatorId)}`, payload)
  },
  deleteCategory(projectId: string, categoryId: string, operatorId: string) {
    return request.delete<string>(`${base(projectId)}/categories/${categoryId}?${operatorQuery(operatorId)}`)
  },
  listTags(projectId: string) {
    return request.get<StoryBibleTag[]>(`${base(projectId)}/tags`)
  },
  createTag(projectId: string, operatorId: string, payload: Pick<StoryBibleTag, 'name' | 'color'>) {
    return request.post<StoryBibleTag>(`${base(projectId)}/tags?${operatorQuery(operatorId)}`, payload)
  },
  updateTag(projectId: string, tagId: string, operatorId: string, payload: Pick<StoryBibleTag, 'name' | 'color'>) {
    return request.patch<StoryBibleTag>(`${base(projectId)}/tags/${tagId}?${operatorQuery(operatorId)}`, payload)
  },
  deleteTag(projectId: string, tagId: string, operatorId: string) {
    return request.delete<string>(`${base(projectId)}/tags/${tagId}?${operatorQuery(operatorId)}`)
  },
  listRelations(projectId: string, nodeIds?: string[]) {
    return request.get<StoryBibleRelation[]>(`${base(projectId)}/relations${optionalNodeIds(nodeIds)}`)
  },
  createRelation(projectId: string, operatorId: string, payload: Omit<StoryBibleRelation, 'relationId' | 'storyBibleId' | 'revision'>) {
    return request.post<StoryBibleRelation>(`${base(projectId)}/relations?${operatorQuery(operatorId)}`, payload)
  },
  updateRelation(projectId: string, relationId: string, operatorId: string, payload: StoryBibleRelationUpdatePayload) {
    return request.patch<StoryBibleRelation>(`${base(projectId)}/relations/${relationId}?${operatorQuery(operatorId)}`, payload)
  },
  deleteRelation(projectId: string, relationId: string, operatorId: string, expectedRevision: number) {
    return request.delete<string>(`${base(projectId)}/relations/${relationId}?expectedRevision=${expectedRevision}&${operatorQuery(operatorId)}`)
  },
  listProgressions(projectId: string, nodeIds?: string[]) {
    return request.get<StoryBibleProgression[]>(`${base(projectId)}/progressions${optionalNodeIds(nodeIds)}`)
  },
  createProgression(projectId: string, nodeId: string, operatorId: string, payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>) {
    return request.post<StoryBibleProgression>(`${base(projectId)}/nodes/${nodeId}/progressions?${operatorQuery(operatorId)}`, payload)
  },
  updateProgression(projectId: string, progressionId: string, operatorId: string, payload: StoryBibleProgressionUpdatePayload) {
    return request.patch<StoryBibleProgression>(`${base(projectId)}/progressions/${progressionId}?${operatorQuery(operatorId)}`, payload)
  },
  deleteProgression(projectId: string, progressionId: string, operatorId: string, expectedRevision: number) {
    return request.delete<string>(`${base(projectId)}/progressions/${progressionId}?expectedRevision=${expectedRevision}&${operatorQuery(operatorId)}`)
  },
  listChanges(projectId: string, limit = 50) {
    return request.get<StoryBibleChangeset[]>(`${base(projectId)}/changes?limit=${limit}`)
  },
  getUserRoutingPreference(projectId: string, userId: string) {
    return request.get<StoryBibleRoutingPreference>(`/v1/novels/${projectId}/agent/routing-preference?userId=${encodeURIComponent(userId)}`)
  },
  updateUserRoutingPreference(projectId: string, userId: string, payload: { mode: StoryBibleRoutingMode; routerModelConfigId?: string | null }) {
    return request.put<StoryBibleRoutingPreference>(`/v1/novels/${projectId}/agent/routing-preference?userId=${encodeURIComponent(userId)}`, payload)
  },
  getSessionRoutingPreference(projectId: string, sessionId: string, userId: string) {
    return request.get<StoryBibleRoutingPreference>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/routing-preference?userId=${encodeURIComponent(userId)}`)
  },
  updateSessionRoutingPreference(projectId: string, sessionId: string, userId: string, payload: { mode: StoryBibleRoutingMode | null; routerModelConfigId?: string | null }) {
    return request.put<StoryBibleRoutingPreference>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/routing-preference?userId=${encodeURIComponent(userId)}`, payload)
  },
}
