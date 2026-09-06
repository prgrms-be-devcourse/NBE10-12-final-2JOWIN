import { api } from '../../shared/api/client'
import type {
  ActivityResponse, CreateActivityRequest, CreateTaskRequest, PageParams, PageResponse, TaskResponse,
  UpdateActivityRequest, UpdateTaskRequest,
} from '../../shared/api/types'
import type { ActivityType } from '../../shared/ui/status'

/**
 * 상담 기록 · 할 일 API — 07 §B (AC-01~09) · activity/dto. 호출은 이 파일 안에서만 한다 (12 §8).
 * 범위는 담당 Deal 기준(🔶). 상담 기록 수정·삭제는 작성자 본인만 — 타인 것은 404 ACTIVITY_NOT_AUTHOR (AC-04·05).
 * "내 할 일" 목록 API는 v1에 없다(GAP-04 확정) — 등록·완료만 있고, 노출은 대시보드 후속 필요(DB-05)뿐이다.
 */

export interface ActivityListParams extends PageParams {
  type?: ActivityType
}

/** GET /api/v1/deals/{dealId}/activities?type= — 수동·자동 기록 한 줄기 (AC-06·07) */
export async function fetchDealActivities(dealId: string, params: ActivityListParams = {}) {
  const { data } = await api.get<PageResponse<ActivityResponse>>(`/deals/${dealId}/activities`, { params })
  return data
}

/** POST /api/v1/deals/{dealId}/activities — channel · content · occurredAt (AC-01~03) */
export async function createActivity(dealId: string, body: CreateActivityRequest) {
  const { data } = await api.post<ActivityResponse>(`/deals/${dealId}/activities`, body)
  return data
}

/** PATCH /api/v1/activities/{id} — 작성자 본인만 (AC-04) */
export async function updateActivity(id: string, body: UpdateActivityRequest) {
  const { data } = await api.patch<ActivityResponse>(`/activities/${id}`, body)
  return data
}

/** DELETE /api/v1/activities/{id} — 작성자 본인만 (AC-05) */
export async function deleteActivity(id: string) {
  await api.delete(`/activities/${id}`)
}

/** POST /api/v1/deals/{dealId}/tasks — content · dueDate (AC-09) */
export async function createTask(dealId: string, body: CreateTaskRequest) {
  const { data } = await api.post<TaskResponse>(`/deals/${dealId}/tasks`, body)
  return data
}

/** PATCH /api/v1/tasks/{id} — 완료 처리(done)·수정 (Q-29) */
export async function updateTask(id: string, body: UpdateTaskRequest) {
  const { data } = await api.patch<TaskResponse>(`/tasks/${id}`, body)
  return data
}
