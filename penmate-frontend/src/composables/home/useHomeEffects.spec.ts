import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))

const loadUseHomeEffects = async () => {
  const composablePath = resolve(currentDir, 'useHomeEffects.ts')

  if (!existsSync(composablePath)) {
    return () => ({
      isScrolled: ref(false),
      particleStyle: () => ({}),
    })
  }

  return (await import(/* @vite-ignore */ pathToFileURL(composablePath).href)).useHomeEffects
}

describe('useHomeEffects', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('tracks_scrolled_state_and_cleans_up_scroll_listener', async () => {
    Object.defineProperty(window, 'scrollY', {
      configurable: true,
      writable: true,
      value: 0,
    })

    const useHomeEffects = await loadUseHomeEffects()
    const addEventListenerSpy = vi.spyOn(window, 'addEventListener')
    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener')

    const wrapper = mount(
      defineComponent({
        setup() {
          return useHomeEffects()
        },
        template: `<div data-testid="home-scroll-flag">{{ isScrolled ? 'yes' : 'no' }}</div>`,
      }),
    )

    const scrollRegistration = addEventListenerSpy.mock.calls.find(([eventName]) => eventName === 'scroll')

    expect(scrollRegistration).toBeTruthy()
    expect(wrapper.get('[data-testid="home-scroll-flag"]').text()).toBe('no')

    const scrollHandler = scrollRegistration?.[1] as EventListener

    Object.defineProperty(window, 'scrollY', {
      configurable: true,
      writable: true,
      value: 120,
    })

    scrollHandler(new Event('scroll'))
    await nextTick()

    expect(wrapper.get('[data-testid="home-scroll-flag"]').text()).toBe('yes')

    wrapper.unmount()

    expect(removeEventListenerSpy).toHaveBeenCalledWith('scroll', scrollHandler)
  })

  it('builds_particle_styles_for_the_floating_background', async () => {
    const useHomeEffects = await loadUseHomeEffects()

    const wrapper = mount(
      defineComponent({
        setup() {
          return useHomeEffects()
        },
        template: '<div></div>',
      }),
    )

    const style = (wrapper.vm as unknown as { particleStyle: (index: number) => Record<string, unknown> }).particleStyle(1)

    expect(style.bottom).toBe('-10px')
    expect(style.width).toMatch(/px$/)
    expect(style.height).toMatch(/px$/)
    expect(style.left).toMatch(/%$/)
    expect(style.animationDuration).toMatch(/s$/)
    expect(style.animationDelay).toMatch(/s$/)
    expect(style.opacity).toBeTypeOf('number')
  })

  it('reads_initial_scroll_state_and_keeps_particle_styles_stable_across_rerenders', async () => {
    Object.defineProperty(window, 'scrollY', {
      configurable: true,
      writable: true,
      value: 120,
    })

    const useHomeEffects = await loadUseHomeEffects()

    const wrapper = mount(
      defineComponent({
        setup() {
          return useHomeEffects()
        },
        template: `<div data-testid="home-scroll-flag">{{ isScrolled ? 'yes' : 'no' }}</div>`,
      }),
    )

    expect(wrapper.get('[data-testid="home-scroll-flag"]').text()).toBe('yes')

    const firstStyle = (wrapper.vm as unknown as { particleStyle: (index: number) => Record<string, unknown> }).particleStyle(1)
    const secondStyle = (wrapper.vm as unknown as { particleStyle: (index: number) => Record<string, unknown> }).particleStyle(1)

    expect(secondStyle).toEqual(firstStyle)
  })
})
