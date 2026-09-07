import { useState } from 'react'
import { Button, Callout, Checkbox, Flex, Skeleton, Text } from '@radix-ui/themes'
import { CheckCircledIcon, InfoCircledIcon } from '@radix-ui/react-icons'
import { codeOf } from '../../../shared/api/client'
import { ErrorCallout } from '../../../shared/ui'
import { MAIL_SETTING_TYPES, NOTIFICATION_TYPE_LABEL, type NotificationType } from '../../../shared/ui/status'
import type { NotificationSettingEntry } from '../../../shared/api/types'
import { useNotificationSettings, useUpdateNotificationSettings } from '../hooks'

/** 알림별 한 줄 설명 — 03-requirements.md §2.13 채널 표 */
const DESCRIPTION: Record<NotificationType, string> = {
  QUOTE_VIEWED: '고객이 견적을 처음 열람했을 때 (NT-03)',
  QUOTE_APPROVED: '고객이 견적을 승인했을 때 (NT-04)',
  QUOTE_REJECTED: '고객이 견적을 반려했을 때 (NT-04)',
  REMIND_NO_RESPONSE: '발송 후 며칠간 응답이 없을 때 리마인드 (NT-05)',
  INQUIRY_RECEIVED: '고객이 열람 페이지에서 문의를 남겼을 때 (NT-10)',
  EMAIL_FAILED: '메일 발송 실패 (NT-12)',
}

/**
 * 알림 수신 설정 (NT-07 · Q-23) — 메일 채널만. 인앱 알림은 항상 기록된다.
 * GET/PUT /me/notification-settings — 행 없으면 기본 ON, 저장은 전체 교체.
 * EMAIL_FAILED는 인앱 전용이라 목록에 없다 (Q-35 — 메일 실패를 메일로 알릴 수 없다).
 */
export function NotificationSettingsForm() {
  const { data, isPending, error, refetch } = useNotificationSettings()
  if (isPending) {
    return (
      <Flex direction="column" gap="3" style={{ maxWidth: 560 }}>
        {[0, 1, 2, 3, 4].map((i) => (
          <Skeleton key={i} height="40px" />
        ))}
      </Flex>
    )
  }
  if (error || !data) return <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />
  return <SettingsEditor initial={data.settings} />
}

function SettingsEditor({ initial }: { initial: NotificationSettingEntry[] }) {
  // 서버 응답에 없는 종류는 기본 ON (08 §A "행 없으면 기본 ON")
  const toMap = (entries: NotificationSettingEntry[]) =>
    Object.fromEntries(MAIL_SETTING_TYPES.map((type) => [type, entries.find((e) => e.type === type)?.emailEnabled ?? true])) as Record<NotificationType, boolean>
  const [values, setValues] = useState(() => toMap(initial))
  const [saved, setSaved] = useState(false)
  const mutation = useUpdateNotificationSettings()

  const dirty = MAIL_SETTING_TYPES.some((type) => values[type] !== toMap(initial)[type])

  const save = () => {
    setSaved(false)
    mutation.mutate(
      { settings: MAIL_SETTING_TYPES.map((type) => ({ type, emailEnabled: values[type] })) },
      { onSuccess: () => setSaved(true) },
    )
  }

  return (
    <Flex direction="column" gap="4" style={{ maxWidth: 560 }}>
      {saved && !dirty && (
        <Callout.Root color="green" size="1">
          <Callout.Icon>
            <CheckCircledIcon />
          </Callout.Icon>
          <Callout.Text>저장되었습니다.</Callout.Text>
        </Callout.Root>
      )}
      {mutation.error && <ErrorCallout code={codeOf(mutation.error)} />}

      <Text size="2" color="gray">
        메일 수신 여부만 정합니다. 앱 안의 알림은 항상 기록됩니다.
      </Text>

      <Flex direction="column">
        {MAIL_SETTING_TYPES.map((type, index) => (
          <Text key={type} as="label" size="2" style={{ borderTop: index === 0 ? undefined : '1px solid var(--gray-a4)', padding: '10px 0', cursor: 'pointer' }}>
            <Flex align="center" gap="3">
              <Checkbox checked={values[type]} onCheckedChange={(checked) => setValues((prev) => ({ ...prev, [type]: checked === true }))} disabled={mutation.isPending} />
              <Flex direction="column">
                <Text weight="medium">{NOTIFICATION_TYPE_LABEL[type]} 알림</Text>
                <Text size="1" color="gray">
                  {DESCRIPTION[type]}
                </Text>
              </Flex>
            </Flex>
          </Text>
        ))}
      </Flex>

      <Callout.Root color="gray" size="1">
        <Callout.Icon>
          <InfoCircledIcon />
        </Callout.Icon>
        <Callout.Text>메일 발송 실패 알림은 앱 안에서만 오며 끌 수 없습니다. 비밀번호 재설정 안내 메일도 이 설정과 무관하게 발송됩니다.</Callout.Text>
      </Callout.Root>

      <Flex justify="end">
        <Button onClick={save} loading={mutation.isPending} disabled={!dirty}>
          저장
        </Button>
      </Flex>
    </Flex>
  )
}
