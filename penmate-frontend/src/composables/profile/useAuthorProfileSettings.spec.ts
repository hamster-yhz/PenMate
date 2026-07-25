import { describe, expect, it, vi } from 'vitest'
import { profileApi } from '@/api/modules/profile.api'
import { useAuthorProfileSettings } from './useAuthorProfileSettings'
import type { AuthorProfile } from '@/entities/author/model'

vi.mock('@/api/modules/profile.api', () => ({
  profileApi: { getAuthorProfile: vi.fn(), saveAuthorProfile: vi.fn() },
}))

const stored = (): AuthorProfile => ({
  defaultLanguage: 'zh-CN', collaborationMode: 'DIRECT', defaultPov: 'FIRST_PERSON',
  defaultTense: 'PAST', descriptionDensity: 'LIGHT', dialoguePreference: '短对白',
  bannedExpressions: '显而易见', longTermMemory: '保留含混结尾',
})

describe('useAuthorProfileSettings', () => {
  it('loads_and_saves_the_current_users_author_profile', async () => {
    vi.mocked(profileApi.getAuthorProfile).mockResolvedValue(stored())
    vi.mocked(profileApi.saveAuthorProfile).mockResolvedValue({ ...stored(), descriptionDensity: 'RICH' })
    const settings = useAuthorProfileSettings()

    await settings.loadAuthorProfile()
    expect(settings.authorProfileLoaded.value).toBe(true)
    expect(settings.authorProfile.longTermMemory).toBe('保留含混结尾')

    await settings.saveAuthorProfile({ ...stored(), descriptionDensity: 'RICH' })
    expect(settings.authorProfile.descriptionDensity).toBe('RICH')
    expect(settings.authorProfileSaved.value).toBe(true)
  })
})
