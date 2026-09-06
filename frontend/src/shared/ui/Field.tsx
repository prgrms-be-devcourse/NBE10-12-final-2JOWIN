import { Flex, Text } from '@radix-ui/themes'
import type { ReactNode } from 'react'

interface Props {
  label: string
  required?: boolean
  /** 서버 fieldErrors의 reason — 입력 아래 red 텍스트 (10 §6.3 400행) */
  error?: string
  hint?: string
  grow?: boolean
  /** label의 htmlFor */
  htmlFor?: string
  children: ReactNode
}

/** 라벨·입력·오류/힌트 공통 배치 — 폼마다 다른 간격으로 시작하지 않게 한다 */
export function Field({ label, required, error, hint, grow, htmlFor, children }: Props) {
  return (
    <Flex direction="column" gap="1" style={grow ? { flex: 1, minWidth: 0 } : undefined}>
      <Text as="label" htmlFor={htmlFor} size="2" weight="medium">
        {label}
        {required && (
          <Text color="red" aria-hidden>
            {' '}*
          </Text>
        )}
      </Text>
      {children}
      {error ? (
        <Text size="1" color="red">
          {error}
        </Text>
      ) : (
        hint && (
          <Text size="1" color="gray">
            {hint}
          </Text>
        )
      )}
    </Flex>
  )
}
