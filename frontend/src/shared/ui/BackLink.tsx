import { Text } from '@radix-ui/themes'
import { ArrowLeftIcon } from '@radix-ui/react-icons'
import { Link } from 'react-router'

/** 상세 화면 상단의 "← 목록" 링크 — 위치·크기를 화면마다 다르게 두지 않는다 */
export function BackLink({ to, label }: { to: string; label: string }) {
  return (
    <Text asChild size="2" color="gray" mb="3" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, textDecoration: 'none' }}>
      <Link to={to}>
        <ArrowLeftIcon /> {label}
      </Link>
    </Text>
  )
}
