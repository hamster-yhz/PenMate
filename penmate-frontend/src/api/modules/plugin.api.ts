import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const pluginApi = {
  listCatalog() {
    return request.get<AnyRecord[]>('/v1/plugins/catalog')
  },
  getCatalogItem(pluginCode: string) {
    return request.get<AnyRecord>(`/v1/plugins/catalog/${pluginCode}`)
  },
  listProjectPlugins(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/plugins`)
  },
  installPlugin(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/plugins/install?operatorId=${operatorId}`, payload)
  },
  updateInstall(projectId: IdLike, pluginCode: string, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<string>(`/v1/novels/${projectId}/plugins/${pluginCode}?operatorId=${operatorId}`, payload)
  },
  deleteInstall(projectId: IdLike, pluginCode: string, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/plugins/${pluginCode}?operatorId=${operatorId}`)
  },
  listCallLogs(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/plugins/call-logs`)
  }
}

