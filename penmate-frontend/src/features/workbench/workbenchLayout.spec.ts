import { describe, expect, it } from 'vitest'
import { layoutForPreset, normalizeStoredLayout, resolveResponsiveWorkbenchLayout } from './workbenchLayout'

describe('workbenchLayout', () => {
  it('provides the three agreed layouts', () => {
    expect(layoutForPreset('balanced')).toEqual({
      preset: 'balanced', leftPanelWidth: 220, chatPanelWidth: 440, leftCollapsed: false, rightCollapsed: false,
    })
    expect(layoutForPreset('focus')).toMatchObject({ leftCollapsed: true, rightCollapsed: true })
    expect(layoutForPreset('ai')).toMatchObject({ leftCollapsed: true, rightCollapsed: false, chatPanelWidth: 600 })
  })

  it('restores device-local adjustments within supported panel bounds', () => {
    expect(normalizeStoredLayout({
      preset: 'ai', leftPanelWidth: 20, chatPanelWidth: 900, leftCollapsed: false, rightCollapsed: true,
    })).toEqual({
      preset: 'ai', leftPanelWidth: 160, chatPanelWidth: 600, leftCollapsed: false, rightCollapsed: true,
    })
  })

  it('uses the balanced layout when no layout has been saved', () => {
    expect(normalizeStoredLayout(null)).toEqual({
      preset: 'balanced', leftPanelWidth: 220, chatPanelWidth: 440, leftCollapsed: false, rightCollapsed: false,
    })
  })

  it('automatically overlays the directory before shrinking the editor', () => {
    expect(resolveResponsiveWorkbenchLayout({
      viewportWidth: 1080,
      leftPanelWidth: 220,
      chatPanelWidth: 440,
      leftCollapsed: false,
      rightCollapsed: false,
    })).toEqual({ mobile: false, directoryOverlay: true, chatPanelWidth: 440 })
  })

  it('keeps at least 520px for the editor before entering mobile single-pane mode', () => {
    expect(resolveResponsiveWorkbenchLayout({
      viewportWidth: 920,
      leftPanelWidth: 220,
      chatPanelWidth: 600,
      leftCollapsed: false,
      rightCollapsed: false,
    })).toEqual({ mobile: false, directoryOverlay: true, chatPanelWidth: 400 })
  })

  it('switches to mobile at 900px without rewriting stored widths', () => {
    expect(resolveResponsiveWorkbenchLayout({
      viewportWidth: 900,
      leftPanelWidth: 220,
      chatPanelWidth: 600,
      leftCollapsed: false,
      rightCollapsed: false,
    })).toEqual({ mobile: true, directoryOverlay: false, chatPanelWidth: 600 })
  })
})
