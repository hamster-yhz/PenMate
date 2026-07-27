export interface ProjectLedger {
  ledgerId: string
  projectId: string
  title: string
  content?: string | null
  contentRevision: string
  leaseOwnerType?: string | null
  leaseOwnerId?: string | null
  leaseExpiresAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  offset?: number | null
  end?: number | null
  totalCharacters?: number | null
  complete?: boolean | null
}
