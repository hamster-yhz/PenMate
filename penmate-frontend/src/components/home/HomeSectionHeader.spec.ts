import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

const missingComponent = (name: string) =>
  defineComponent({
    name,
    template: `<div data-testid="missing-${name}"></div>`,
  })

const currentDir = dirname(fileURLToPath(import.meta.url))

const loadHomeSectionHeader = async () => {
  const componentPath = resolve(currentDir, 'HomeSectionHeader.vue')

  if (!existsSync(componentPath)) {
    return missingComponent('home-section-header')
  }

  return (await import(/* @vite-ignore */ pathToFileURL(componentPath).href)).default
}

describe('HomeSectionHeader', () => {
  it('renders_title_subtitle_and_divider_image', async () => {
    const HomeSectionHeader = await loadHomeSectionHeader()
    const wrapper = mount(HomeSectionHeader, {
      props: {
        title: '六 大 神 通',
        subtitle: '汇聚天地灵气，锻造写作神兵',
        dividerImg: '/divider.png',
      },
    })

    expect(wrapper.find('[data-testid="home-section-header"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('六 大 神 通')
    expect(wrapper.text()).toContain('汇聚天地灵气，锻造写作神兵')
    expect(wrapper.get('[data-testid="home-section-divider"]').attributes('src')).toBe('/divider.png')
  })
})
