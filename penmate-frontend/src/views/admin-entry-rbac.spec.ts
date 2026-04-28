import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const readSource = (relativePath: string) =>
  readFileSync(resolve(import.meta.dirname, '..', relativePath), 'utf-8')

describe('admin RBAC entrypoints', () => {
  it('uses the dedicated rbac admin route instead of the domain console shortcut', () => {
    const myBooksSource = readSource('views/MyBooks/index.vue')
    const workbenchSource = readSource('views/Workbench/index.vue')
    const routerSource = readSource('router/index.ts')

    expect(myBooksSource).toContain("router.push('/admin/rbac')")
    expect(myBooksSource).not.toContain("router.push('/domain-console')")
    expect(myBooksSource).toContain('RBAC 管理')

    expect(workbenchSource).toContain("@go-rbac-admin=\"navigateFromUserMenu('/admin/rbac')\"")
    expect(workbenchSource).not.toContain("@go-domain-console=\"navigateFromUserMenu('/domain-console')\"")

    expect(routerSource).toContain("path: '/admin/rbac'")
    expect(routerSource).toContain("name: 'AdminRbac'")
    expect(routerSource).not.toContain("path: '/domain-console'")
  })
})
