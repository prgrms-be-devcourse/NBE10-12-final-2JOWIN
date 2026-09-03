import { Badge, type BadgeProps } from '@radix-ui/themes'
import { INDUSTRY_COLORS } from '../constants'

/** 업종 배지. 목록에 없는 업종은 gray */
export function IndustryBadge({ industry, size = '1' }: { industry: string; size?: BadgeProps['size'] }) {
  const color = (INDUSTRY_COLORS as Record<string, BadgeProps['color']>)[industry] ?? 'gray'
  return (
    <Badge color={color} variant="soft" size={size}>
      {industry}
    </Badge>
  )
}
