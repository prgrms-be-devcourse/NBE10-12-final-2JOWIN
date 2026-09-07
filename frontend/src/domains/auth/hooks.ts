import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ME_QUERY_KEY } from '../../app/session'
import type { ChangePasswordRequest, UpdateMeRequest, UpdateNotificationSettingsRequest } from '../../shared/api/types'
import { changePassword, fetchNotificationSettings, updateMe, updateNotificationSettings } from './api'

/** 본인 계정 훅 — AU-04·07 · NT-07. 세션(GET /me)은 AuthGuard가 들고 있으므로 여기서 다시 읽지 않는다 */

export const meKeys = {
  notificationSettings: ['auth', 'notification-settings'] as const,
}

/** PATCH /me — 성공 시 세션 캐시를 무효화해 헤더·프로필 표시를 갱신한다 (07 v1.6.10) */
export function useUpdateMe() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateMeRequest) => updateMe(body),
    onSuccess: (me) => {
      queryClient.setQueryData(ME_QUERY_KEY, me)
      queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
    },
  })
}

/** POST /me/password — 204. 성공 시 전 세션 폐기(전이표 §9) → 호출자가 로그인 화면으로 보낸다 */
export function useChangePassword() {
  return useMutation({ mutationFn: (body: ChangePasswordRequest) => changePassword(body) })
}

export function useNotificationSettings() {
  return useQuery({ queryKey: meKeys.notificationSettings, queryFn: fetchNotificationSettings })
}

/** PUT /me/notification-settings — 전체 교체 */
export function useUpdateNotificationSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateNotificationSettingsRequest) => updateNotificationSettings(body),
    onSuccess: (settings) => queryClient.setQueryData(meKeys.notificationSettings, settings),
  })
}
