export interface AuthorProfile {
  defaultLanguage: string
  collaborationMode: 'DIRECT' | 'COLLABORATIVE' | 'EXPLORATORY'
  defaultPov: 'PROJECT_DEFAULT' | 'FIRST_PERSON' | 'THIRD_LIMITED' | 'THIRD_OMNISCIENT'
  defaultTense: 'PROJECT_DEFAULT' | 'PAST' | 'PRESENT'
  descriptionDensity: 'LIGHT' | 'MEDIUM' | 'RICH'
  dialoguePreference: string
  bannedExpressions: string
  longTermMemory: string
}
