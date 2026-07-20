import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const pluginApi = {
  listCatalog() {
    return request.get<AnyRecord[]>('/v1/plugins/catalog')
  },
  getCatalogItem(pluginCode: string) {
    return request.get<AnyRecord>(`/v1/plugins/catalog/${pluginCode}`)
  },
  listProjectPlugins(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/plugins`)
  },
  installPlugin(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<string>(`/v1/novels/${projectId}/plugins/install`, payload)
  },
  updateInstall(projectId: string, pluginCode: string, _operatorId: string, payload: AnyRecord) {
    return request.patch<string>(`/v1/novels/${projectId}/plugins/${pluginCode}`, payload)
  },
  deleteInstall(projectId: string, pluginCode: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/plugins/${pluginCode}`)
  },
  listCallLogs(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/plugins/call-logs`)
  },
}
