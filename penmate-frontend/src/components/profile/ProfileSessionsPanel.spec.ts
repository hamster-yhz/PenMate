import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProfileSessionsPanel from './ProfileSessionsPanel.vue'

const sessions = [
  {
    sessionId: 'current-session',
    deviceName: 'Desktop',
    browserName: 'Chrome',
    operatingSystem: 'Windows',
    ipAddress: '127.0.0.1',
    lastSeenAt: '2026-07-22T02:00:00Z',
    current: true,
  },
  {
    sessionId: 'other-session',
    deviceName: 'Mobile',
    browserName: 'Safari',
    operatingSystem: 'iOS',
    ipAddress: '10.0.0.8',
    lastSeenAt: '2026-07-21T02:00:00Z',
    current: false,
  },
]

describe('ProfileSessionsPanel', () => {
  it('marks the current device and only lets another device be revoked', async () => {
    const wrapper = mount(ProfileSessionsPanel, { props: { sessions } })

    expect(wrapper.text()).toContain('当前设备')
    const revokeButtons = wrapper.findAll('.revoke-button')
    expect(revokeButtons).toHaveLength(1)

    await revokeButtons[0].trigger('click')
    expect(wrapper.emitted('revoke')).toEqual([['other-session']])
  })

  it('offers one bulk action only when another session exists', async () => {
    const wrapper = mount(ProfileSessionsPanel, { props: { sessions } })

    const revokeOthers = wrapper.get('[data-testid="profile-sessions-revoke-others"]')
    await revokeOthers.trigger('click')
    expect(wrapper.emitted('revokeOthers')).toHaveLength(1)

    await wrapper.setProps({ sessions: [sessions[0]] })
    expect(wrapper.find('[data-testid="profile-sessions-revoke-others"]').exists()).toBe(false)
  })

  it('disables session actions while bulk revocation is running and keeps an action error visible', () => {
    const wrapper = mount(ProfileSessionsPanel, {
      props: {
        sessions,
        revokingOtherSessions: true,
        actionError: '退出其他设备失败，请重试',
      },
    })

    expect(wrapper.get('[data-testid="profile-sessions-revoke-others"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="profile-sessions-revoke-others"]').text()).toContain('正在退出')
    expect(wrapper.get('.revoke-button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[role="alert"]').text()).toBe('退出其他设备失败，请重试')
  })

  it('shows an inline retry action after loading fails', async () => {
    const wrapper = mount(ProfileSessionsPanel, { props: { error: '加载设备失败' } })

    expect(wrapper.get('[role="alert"]').text()).toContain('加载设备失败')
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
