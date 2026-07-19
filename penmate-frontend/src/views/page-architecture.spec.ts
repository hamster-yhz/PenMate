import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const readView = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), 'utf8')

describe('route view architecture', () => {
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
})
