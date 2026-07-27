export type StoryBibleSemanticFamily = 'CORE' | 'CHARACTER' | 'WORLD' | 'THING' | 'NARRATIVE' | 'TIMELINE'
export type StoryBibleInclusionPolicy = 'ALWAYS_INCLUDE' | 'AUTO_RETRIEVE' | 'MANUAL_ONLY'
export type StoryBibleCanonStatus = 'DRAFT' | 'CANON' | 'ARCHIVED'

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

export interface StoryBibleAlias { aliasId: string; nodeId: string; alias: string }

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
  archivedAt?: string | null
  undoneAt?: string | null
  undoneBy?: string | null
  undoChangesetId?: string | null
}

export interface StoryBibleChangeItem {
  changeItemId: string
  changesetId: string
  entityType: string
  entityId: string
  operation: string
  fieldPath: string
  beforeJson?: string | null
  afterJson?: string | null
  createdAt: string
}

export interface StoryBibleChangesetDetails { changeset: StoryBibleChangeset; items: StoryBibleChangeItem[] }

export interface StoryBibleRunUndoResult {
  sourceRunId: string
  changesetIds: string[]
  undoChangeset: StoryBibleChangeset
}

export interface StoryBibleChangesetPage {
  items: StoryBibleChangeset[]
  nextBeforeRevision?: number | null
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

export interface StoryBibleNodeUpdatePayload extends StoryBibleNodePayload { expectedRevision: number }
