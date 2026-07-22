import { readdirSync, readFileSync } from 'node:fs'
import { basename, join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const collectVueComponents = (directory: string): string[] => readdirSync(directory, { withFileTypes: true })
  .flatMap((entry) => {
    const target = join(directory, entry.name)
    if (entry.isDirectory()) return collectVueComponents(target)
    return entry.name.endsWith('.vue') ? [target] : []
  })

const deferredRuntimeApiOwners = new Set([
  'PluginWorkshop.vue',
  'StyleManager.vue',
])

describe('component architecture', () => {
  it('keeps runtime API orchestration out of presentation components', () => {
    for (const component of collectVueComponents(resolve('src/components'))) {
      if (deferredRuntimeApiOwners.has(basename(component))) continue
      const source = readFileSync(component, 'utf8')
      const runtimeApiImports = source.split(/\r?\n/).filter((line) =>
        /^\s*import\s+(?!type\b).*['"]@\/api/.test(line)
        || /import\(\s*['"]@\/api/.test(line))

      expect(runtimeApiImports, component).toEqual([])
    }
  })

  it('keeps transport-layer types out of active presentation components', () => {
    for (const component of collectVueComponents(resolve('src/components'))) {
      if (deferredRuntimeApiOwners.has(basename(component))) continue
      expect(readFileSync(component, 'utf8'), component).not.toContain('@/api')
    }
  })

  it('keeps personal model service orchestration in its feature controller', () => {
    const component = readFileSync(resolve('src/components/profile/ProfileModelServicesPanel.vue'), 'utf8')
    const controller = readFileSync(resolve('src/features/profile-model-services/useProfileModelServices.ts'), 'utf8')

    expect(component).toContain('useProfileModelServices')
    expect(component).not.toContain('@/api/modules/model.api')
    expect(controller).toContain('@/api/modules/model.api')
  })
})
