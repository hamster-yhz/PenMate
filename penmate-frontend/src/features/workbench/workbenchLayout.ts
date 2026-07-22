export type WorkbenchLayoutPreset = 'balanced' | 'focus' | 'ai'

export interface WorkbenchLayoutState {
  preset: WorkbenchLayoutPreset
  leftPanelWidth: number
  chatPanelWidth: number
  leftCollapsed: boolean
  rightCollapsed: boolean
}

export const WORKBENCH_MOBILE_MAX_WIDTH = 900
export const WORKBENCH_MIN_EDITOR_WIDTH = 520

const PRESET_LAYOUTS: Record<WorkbenchLayoutPreset, Omit<WorkbenchLayoutState, 'preset'>> = {
  balanced: { leftPanelWidth: 220, chatPanelWidth: 440, leftCollapsed: false, rightCollapsed: false },
  focus: { leftPanelWidth: 220, chatPanelWidth: 440, leftCollapsed: true, rightCollapsed: true },
  ai: { leftPanelWidth: 200, chatPanelWidth: 600, leftCollapsed: true, rightCollapsed: false },
}

const isPreset = (value: unknown): value is WorkbenchLayoutPreset =>
  value === 'balanced' || value === 'focus' || value === 'ai'

const clamp = (value: unknown, min: number, max: number, fallback: number) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback
}

export const layoutForPreset = (preset: WorkbenchLayoutPreset): WorkbenchLayoutState => ({
  preset,
  ...PRESET_LAYOUTS[preset],
})

export const normalizeStoredLayout = (value: unknown): WorkbenchLayoutState => {
  const stored = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  const preset = isPreset(stored.preset) ? stored.preset : 'balanced'
  const defaults = layoutForPreset(preset)
  return {
    preset,
    leftPanelWidth: clamp(stored.leftPanelWidth, 160, 360, defaults.leftPanelWidth),
    chatPanelWidth: clamp(stored.chatPanelWidth, 300, 600, defaults.chatPanelWidth),
    leftCollapsed: typeof stored.leftCollapsed === 'boolean' ? stored.leftCollapsed : defaults.leftCollapsed,
    rightCollapsed: typeof stored.rightCollapsed === 'boolean' ? stored.rightCollapsed : defaults.rightCollapsed,
  }
}

interface ResponsiveWorkbenchLayoutInput {
  viewportWidth: number
  leftPanelWidth: number
  chatPanelWidth: number
  leftCollapsed: boolean
  rightCollapsed: boolean
}

export interface ResponsiveWorkbenchLayout {
  mobile: boolean
  directoryOverlay: boolean
  chatPanelWidth: number
}

export const resolveResponsiveWorkbenchLayout = ({
  viewportWidth,
  leftPanelWidth,
  chatPanelWidth,
  leftCollapsed,
  rightCollapsed,
}: ResponsiveWorkbenchLayoutInput): ResponsiveWorkbenchLayout => {
  const mobile = viewportWidth <= WORKBENCH_MOBILE_MAX_WIDTH
  const directoryOverlay = !mobile
    && !leftCollapsed
    && !rightCollapsed
    && viewportWidth < leftPanelWidth + WORKBENCH_MIN_EDITOR_WIDTH + chatPanelWidth
  const inlineDirectoryWidth = leftCollapsed || directoryOverlay ? 0 : leftPanelWidth
  const availableChatWidth = viewportWidth - WORKBENCH_MIN_EDITOR_WIDTH - inlineDirectoryWidth

  return {
    mobile,
    directoryOverlay,
    chatPanelWidth: mobile || rightCollapsed
      ? chatPanelWidth
      : Math.min(chatPanelWidth, Math.max(300, availableChatWidth)),
  }
}
