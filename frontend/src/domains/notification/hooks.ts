import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchNotifications, markAllNotificationsRead, markNotificationRead, type NotificationListParams } from './api'

/**
 * 알림 Query 훅 — 폴링 방식 (10 §6.4 · 14 §1.1): 주기 30초, 탭 비활성 시 중단.
 * 실시간 푸시(NT-09)는 다음 버전. `refetchIntervalInBackground: false`가 "탭 비활성 시 중단"이다.
 */

export const POLL_INTERVAL_MS = 30_000

export const notificationKeys = {
  all: ['notification'] as const,
  list: (params: NotificationListParams) => ['notification', 'list', params] as const,
}

/** 벨 드롭다운용 — 미읽음만 최대 5건 + 미읽음 수(totalElements) */
export function useUnreadNotifications() {
  return useQuery({
    queryKey: notificationKeys.list({ unreadOnly: true, size: 5 }),
    queryFn: () => fetchNotifications({ unreadOnly: true, size: 5 }),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
  })
}

export function useNotificationList(params: NotificationListParams) {
  return useQuery({
    queryKey: notificationKeys.list(params),
    queryFn: () => fetchNotifications(params),
    placeholderData: (previous) => previous,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
  })
}

export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}
