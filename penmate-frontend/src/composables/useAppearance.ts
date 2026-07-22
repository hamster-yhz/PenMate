import { computed, onBeforeUnmount, ref } from 'vue'

export type ThemePreference = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'penmate.theme'
const preference = ref<ThemePreference>('system')
const systemDark = ref(false)
let initialized = false
let media: MediaQueryList | null = null

const isThemePreference = (value: string | null): value is ThemePreference =>
  value === 'system' || value === 'light' || value === 'dark'

const applyTheme = () => {
  if (typeof document === 'undefined') return
  const dark = preference.value === 'dark' || (preference.value === 'system' && systemDark.value)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

export const setAppearancePreference = (next: ThemePreference) => {
  preference.value = next
  if (typeof window !== 'undefined') window.localStorage.setItem(STORAGE_KEY, next)
  applyTheme()
}

export const initializeAppearance = () => {
  if (initialized || typeof window === 'undefined') return
  initialized = true
  const stored = window.localStorage.getItem(STORAGE_KEY)
  preference.value = isThemePreference(stored) ? stored : 'system'
  media = window.matchMedia('(prefers-color-scheme: dark)')
  systemDark.value = media.matches
  media.addEventListener('change', handleSystemThemeChange)
  applyTheme()
}

const handleSystemThemeChange = (event: MediaQueryListEvent) => {
  systemDark.value = event.matches
  applyTheme()
}

export const useAppearance = () => {
  initializeAppearance()

  const isDark = computed(
    () => preference.value === 'dark' || (preference.value === 'system' && systemDark.value),
  )

  const setTheme = setAppearancePreference

  const toggleTheme = () => setTheme(isDark.value ? 'light' : 'dark')

  onBeforeUnmount(() => {
    // The listener is intentionally shared for the app lifetime.
  })

  return { themePreference: preference, isDark, setTheme, toggleTheme }
}
