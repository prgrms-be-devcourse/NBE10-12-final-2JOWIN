import { Text } from '@radix-ui/themes'
import type { ComponentProps, CSSProperties } from 'react'
import { money, moneyShort } from '../lib/format'

type TextProps = ComponentProps<typeof Text>

interface Props {
  value: number
  /** 4,840만으로 줄인다 — 대시보드 요약처럼 자리가 좁을 때만 */
  short?: boolean
  /** '원'을 붙인다 */
  unit?: boolean
  size?: TextProps['size']
  weight?: TextProps['weight']
  color?: TextProps['color']
  style?: CSSProperties
}

/**
 * 금액 표시 (08-dto.md §0 — 원 단위 정수).
 *
 * 숫자는 **tabular-nums**로 낸다 — 표에서 자릿수가 흔들리면 금액 비교가 안 된다.
 * 금액이 근거가 되는 자리(견적서·주문)에는 `short`를 쓰지 않는다. 반올림이 숨는다.
 */
export function Money({ value, short = false, unit = false, size, weight, color, style }: Props) {
  return (
    <Text
      size={size}
      weight={weight}
      color={color}
      style={{ fontVariantNumeric: 'tabular-nums', ...style }}
    >
      {short ? moneyShort(value) : money(value)}
      {unit ? '원' : ''}
    </Text>
  )
}
