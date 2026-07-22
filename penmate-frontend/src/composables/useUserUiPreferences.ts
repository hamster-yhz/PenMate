import { reactive, readonly } from 'vue'
import { authApi } from '@/api/modules/auth.api'
import type { UserUiPreferences } from '@/entities/auth/model'
import { setAppearancePreference } from '@/composables/useAppearance'
import { getSession } from '@/stores/session'

export const DEFAULT_USER_UI_PREFERENCES: UserUiPreferences = {
  themeMode: 'SYSTEM',
  editorFontFamily: 'SERIF',
  editorFontSize: 17,
  editorLineHeight: 1.9,
  editorParagraphSpacing: 0.35,
  editorContentWidth: 760,
  typewriterMode: false,
  highlightCurrentParagraph: true,
}

const preferences = reactive<UserUiPreferences>({ ...DEFAULT_USER_UI_PREFERENCES })
let loadedUserId = ''
let loadPromise: Promise<UserUiPreferences> | null = null

const applyPreferences = (next: UserUiPreferences) => {
  Object.assign(preferences, next)
  setAppearancePreference(next.themeMode.toLowerCase() as 'system' | 'light' | 'dark')
  return preferences
}

export const loadUserUiPreferences = (force = false) => {
  const userId = getSession().userId || ''
  if (!userId) return Promise.resolve(preferences)
  if (!force && loadedUserId === userId) return Promise.resolve(preferences)
  if (loadPromise) return loadPromise

  loadPromise = authApi.getUiPreferences()
    .then((result) => {
      loadedUserId = userId
      return applyPreferences(result)
    })
    .finally(() => { loadPromise = null })
  return loadPromise
}

export const saveUserUiPreferences = async (next: UserUiPreferences) => {
  const saved = await authApi.saveUiPreferences(next)
  loadedUserId = getSession().userId || loadedUserId
  applyPreferences(saved)
  return saved
}

export const useUserUiPreferences = () => ({
  uiPreferences: readonly(preferences),
  loadUserUiPreferences,
  saveUserUiPreferences,
})
