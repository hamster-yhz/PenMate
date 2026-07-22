import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useDialogFocus } from './useDialogFocus'

const DialogHarness = defineComponent({
  template: `
    <button ref="trigger" type="button" @click="open = true">Open</button>
    <section v-if="open" ref="dialog" role="dialog" tabindex="-1">
      <button type="button" data-dialog-initial-focus>First</button>
      <button type="button" class="last" @click="open = false">Last</button>
    </section>
  `,
  setup() {
    const open = ref(false)
    const dialog = ref<HTMLElement | null>(null)
    useDialogFocus({ open: () => open.value, dialog, close: () => { open.value = false } })
    return { open, dialog }
  },
})

afterEach(() => vi.restoreAllMocks())

describe('useDialogFocus', () => {
  it('focuses, traps tab navigation, closes with Escape, and restores focus', async () => {
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0)
      return 1
    })
    const wrapper = mount(DialogHarness, { attachTo: document.body })
    const trigger = wrapper.get('button')
    ;(trigger.element as HTMLElement).focus()
    await trigger.trigger('click')
    await nextTick()

    expect(document.activeElement?.textContent).toBe('First')
    ;(wrapper.get('.last').element as HTMLElement).focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(document.activeElement?.textContent).toBe('First')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
    wrapper.unmount()
  })
})
