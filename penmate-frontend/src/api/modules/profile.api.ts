import request from '@/utils/request'
import type { AuthorProfile } from '@/entities/author/model'

type AnyRecord = Record<string, unknown>

export const profileApi = {
  profileMenus(userId: string) {
    return request.get<AnyRecord[]>(`/v1/profile/menus?userId=${userId}`)
  },
  systemMenus() {
    return request.get<AnyRecord[]>('/v1/menus')
  },
  getAuthorProfile() {
    return request.get<AuthorProfile>('/v1/author-profile')
  },
  saveAuthorProfile(profile: AuthorProfile) {
    return request.put<AuthorProfile>('/v1/author-profile', profile)
  },
}
