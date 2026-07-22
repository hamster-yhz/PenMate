import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingLoginForm = defineComponent({
  name: 'MissingLoginForm',
  template: '<div data-testid="missing-login-form"></div>',
})

type LoginFormProps = {
  username: string
  password: string
  loading: boolean
}

const loadLoginForm = async (): Promise<Component> => {
  try {
    const componentPath = './LoginForm.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingLoginForm
  }
}

const mountLoginForm = async (overrides: Partial<LoginFormProps> = {}) => {
  const LoginForm = await loadLoginForm()

  return mount(LoginForm, {
    props: {
      username: '',
      password: '',
      loading: false,
      ...overrides,
    },
  })
}

describe('LoginForm', () => {
  it('emits_submit_payload_when_form_is_submitted', async () => {
    const wrapper = await mountLoginForm({
      username: 'writer@example.com',
      password: 'secret-pass',
    })

    expect(wrapper.find('[data-testid="missing-login-form"]').exists()).toBe(false)

    await wrapper.get('[data-testid="login-form"]').trigger('submit')

    expect(wrapper.emitted('submit')).toEqual([
      [
        {
          username: 'writer@example.com',
          password: 'secret-pass',
        },
      ],
    ])
  })

  it('disables_submit_button_and_blocks_submit_when_loading', async () => {
    const wrapper = await mountLoginForm({
      username: 'writer@example.com',
      password: 'secret-pass',
      loading: true,
    })

    expect(wrapper.find('[data-testid="missing-login-form"]').exists()).toBe(false)

    const submitButton = wrapper.get('[data-testid="login-submit"]')

    expect((submitButton.element as HTMLButtonElement).disabled).toBe(true)

    await wrapper.get('[data-testid="login-form"]').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })
})
