import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'
import App from './App.vue'

const { loadUserUiPreferencesMock } = vi.hoisted(() => ({
  loadUserUiPreferencesMock: vi.fn().mockResolvedValue({}),
}))

vi.mock('@/composables/useUserUiPreferences', () => ({
  loadUserUiPreferences: loadUserUiPreferencesMock,
}))

describe('App UI preference bootstrap', () => {
  beforeEach(() => {
    clearSession()
    loadUserUiPreferencesMock.mockClear()
  })

  it('loads preferences when an authenticated session becomes available after mount', async () => {
    const wrapper = shallowMount(App, {
      global: { stubs: { RouterView: true } },
    })

    setSession({ userId: '1001', accessToken: 'access-token' })
    await flushPromises()

    expect(loadUserUiPreferencesMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
