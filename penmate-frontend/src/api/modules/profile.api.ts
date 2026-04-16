import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const profileApi = {
  profileMenus(userId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/profile/menus?userId=${userId}`)
  },
  systemMenus() {
    return request.get<AnyRecord[]>('/v1/menus')
  }
}

