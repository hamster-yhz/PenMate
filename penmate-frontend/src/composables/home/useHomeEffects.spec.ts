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

})
