import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const profileApi = {
  profileMenus(userId: string) {
    return request.get<AnyRecord[]>(`/v1/profile/menus?userId=${userId}`)
  },
  systemMenus() {
    return request.get<AnyRecord[]>('/v1/menus')
  },
}
