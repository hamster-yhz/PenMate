import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RegisterForm from './RegisterForm.vue'

const mountRegisterForm = (
  overrides: Partial<{
    username: string
    email: string
    password: string
    confirmPassword: string
    loading: boolean
  }> = {},
) =>
  mount(RegisterForm, {
    props: {
      username: 'new-writer',
      email: 'writer@example.com',
      password: 'secret-pass',
      confirmPassword: 'secret-pass',
      loading: false,
      ...overrides,
    },
  })

describe('RegisterForm', () => {
  it('blocks_submit_when_loading', async () => {
    const wrapper = mountRegisterForm({ loading: true })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect((wrapper.get('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
  })
})
