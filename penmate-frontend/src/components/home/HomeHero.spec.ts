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

const loadHomeHero = async () => {
  const componentPath = resolve(currentDir, 'HomeHero.vue')

  if (!existsSync(componentPath)) {
    return missingComponent('home-hero')
  }

  return (await import(/* @vite-ignore */ pathToFileURL(componentPath).href)).default
}

const mountHomeHero = async () => {
  const HomeHero = await loadHomeHero()

  return mount(HomeHero, {
    props: {
      heroBg: '/hero-bg.png',
      logoImg: '/logo.png',
    },
  })
}

describe('HomeHero', () => {
  it('renders_hero_copy_and_primary_actions', async () => {
    const wrapper = await mountHomeHero()

    expect(wrapper.find('[data-testid="home-hero"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('AI驱动的智能写作平台')
    expect(wrapper.text()).toContain('执 笔 问 道')
    expect(wrapper.text()).toContain('以AI为墨，以心为笺')
    expect(wrapper.text()).toContain('踏入工作区')
    expect(wrapper.text()).toContain('一览功能')
    expect(wrapper.text()).toContain('创意无限')
  })

  it('emits_enter_workbench_when_primary_cta_is_clicked', async () => {
    const wrapper = await mountHomeHero()

    const primaryCta = wrapper.find('[data-testid="home-hero-primary-cta"]')

    expect(primaryCta.exists()).toBe(true)

    await primaryCta.trigger('click')

    expect(wrapper.emitted('enter-workbench')).toEqual([[]])
  })
})
