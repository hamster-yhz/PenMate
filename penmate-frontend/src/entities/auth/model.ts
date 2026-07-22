export interface AuthSessionItem {
  sessionId: string
  deviceName: string
  browserName: string
  operatingSystem: string
  ipAddress: string
  createdAt?: string | null
  lastSeenAt?: string | null
  refreshExpiresAt?: string | null
  current: boolean
}

export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK'
export type EditorFontFamily = 'SERIF' | 'SANS' | 'SYSTEM'

export interface UserUiPreferences {
  themeMode: ThemeMode
  editorFontFamily: EditorFontFamily
  editorFontSize: number
  editorLineHeight: number
  editorParagraphSpacing: number
  editorContentWidth: number
  typewriterMode: boolean
  highlightCurrentParagraph: boolean
}
