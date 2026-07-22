import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const readView = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), 'utf8')

const collectVueViews = (directory: string): string[] => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const target = join(directory, entry.name)
  if (entry.isDirectory()) return collectVueViews(target)
  return entry.name.endsWith('.vue') ? [target] : []
})

describe('route view architecture', () => {
  it('keeps every route view free of direct API imports', () => {
    for (const view of collectVueViews(resolve('src/views'))) {
      expect(readFileSync(view, 'utf8'), view).not.toContain('@/api')
    }
  })

  it('keeps AdminRbac as a workspace composer without API orchestration', () => {
    const source = readView('./AdminRbac/index.vue')

    expect(source).not.toContain('@/api/modules')
    expect(source).not.toContain('rbacApi.')
    expect(source).toContain('useRbacConsole')
    expect(source).toContain('RbacUsersWorkspace')
    expect(source).toContain('RbacRolesWorkspace')
    expect(source).toContain('RbacMenusWorkspace')
    expect(source.split(/\r?\n/).length).toBeLessThan(500)
  })

  it('keeps Workbench as a component composer without direct domain APIs', () => {
    const source = readView('./Workbench/index.vue')

    expect(source).not.toContain('@/api/modules')
    expect(source).not.toContain('useWorkbenchChat')
    expect(source).not.toContain('useWorkbenchOutline')
    expect(source).toContain('useWorkbenchPageController')
    expect(source.split(/\r?\n/).length).toBeLessThan(400)
  })

  it('keeps ProjectSettings as a small page composer', () => {
    const source = readView('./ProjectSettings/index.vue')

    expect(source).toContain('useProjectSettingsPage')
    expect(source).toContain('ProjectGeneralSection')
    expect(source).toContain('ProjectAiSection')
    expect(source).toContain('ProjectIndexSection')
    expect(source.split(/\r?\n/).length).toBeLessThan(160)
  })
})
