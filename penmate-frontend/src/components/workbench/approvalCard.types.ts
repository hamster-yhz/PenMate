export interface ApprovalCardData {
  id: string
  message: string
  time: string
  preview?: Record<string, string>
  toolCode?: string
  toolDisplayName?: string
  riskLevel?: number
  operationCode?: string
  resolved: boolean
  resolvedAction?: 'approved' | 'rejected'
}
