import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

export const styleApi = {
  listStyles(projectId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/styles`)
  },
  getStyle(projectId: string, styleId: string) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/styles/${styleId}`)
  },
  createStyle(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/styles`, payload)
  },
  updateStyle(projectId: string, styleId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/novels/${projectId}/styles/${styleId}`, payload)
  },
  deleteStyle(projectId: string, styleId: string, _operatorId: string) {
    void _operatorId
    return request.delete<string>(`/v1/novels/${projectId}/styles/${styleId}`)
  },
  switchStyle(projectId: string, _operatorId: string, payload: AnyRecord, sessionId?: string | null) {
    const sessionQuery =
      sessionId === undefined || sessionId === null || sessionId.trim() === '' ? '' : `?sessionId=${sessionId}`
    return request.post<string>(
      `/v1/novels/${projectId}/styles/switch${sessionQuery}`,
      payload,
    )
  },
  analyzeSample(projectId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/styles/analyze-sample`, payload)
  },
}
