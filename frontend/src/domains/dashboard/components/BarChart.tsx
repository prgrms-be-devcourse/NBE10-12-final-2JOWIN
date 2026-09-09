import { Box, Flex, Text, Tooltip } from '@radix-ui/themes'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

export interface BarRow {
  key: string
  label: ReactNode
  value: number
  /** 막대 끝 직접 라벨 — 값의 표시 형태는 호출자가 정한다 (금액 moneyShort · 건수 등) */
  valueLabel: string
  /** 라벨 아래 보조 정보 (건수 등). 텍스트 토큰만 쓴다 */
  sublabel?: string
  /** hover 툴팁 본문 */
  tooltip: ReactNode
  /** 강조 행 — 나머지는 흐리게 (emphasis). 없으면 전부 같은 색 */
  emphasis?: boolean
  href?: string
}

interface Props {
  rows: BarRow[]
  /** 막대 색 — 기본 accent. 뜻이 있는 색(green=성사)은 호출자가 지정 */
  color?: string
  /** 최댓값 기준 — 여러 차트를 같은 눈금으로 맞출 때 */
  max?: number
  /** 라벨 열 폭 */
  labelWidth?: number
}

/**
 * 가로 막대 — 단일 계열 크기 비교 (dataviz: "compare magnitude → bar, sequential(one hue)").
 *
 * 규칙: 막대는 얇게(12px), 끝만 4px 둥글게, 기준선에서 자란다 · 값은 막대 옆 직접 라벨 · 색은 하나 ·
 * 텍스트는 데이터 색을 입지 않는다(gray 토큰) · hover에 툴팁 · 눈금선 없음(직접 라벨이 눈금을 대신한다).
 * 이 차트에 새 값을 지어내지 않는다 — 행은 전부 DTO에서 온 값이다.
 */
export function BarChart({ rows, color = 'var(--accent-9)', max, labelWidth = 72 }: Props) {
  const scale = Math.max(max ?? 0, ...rows.map((r) => r.value), 1)
  return (
    <Flex direction="column" gap="2" role="list">
      {rows.map((row) => {
        const width = `${Math.round((row.value / scale) * 100)}%`
        const bar = (
          <Flex align="center" gap="3" role="listitem" className="row-hover" style={{ borderRadius: 'var(--radius-2)', padding: '2px 4px' }}>
            <Box style={{ width: labelWidth, flexShrink: 0 }}>
              <Text as="div" size="2" truncate>
                {row.label}
              </Text>
              {row.sublabel && (
                <Text as="div" size="1" color="gray" truncate style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {row.sublabel}
                </Text>
              )}
            </Box>
            <Box flexGrow="1" style={{ position: 'relative', height: 12 }}>
              {/* 트랙은 두지 않는다 — 기준선 하나면 충분하다 */}
              <Box
                style={{
                  position: 'absolute', left: 0, top: 0, height: '100%', width,
                  minWidth: row.value > 0 ? 4 : 0,
                  background: color,
                  opacity: row.emphasis === false ? 0.45 : 1,
                  borderRadius: '0 4px 4px 0',
                  transition: 'width var(--motion-base) var(--ease-out)',
                }}
              />
            </Box>
            <Text size="2" weight="medium" style={{ fontVariantNumeric: 'tabular-nums', width: 72, textAlign: 'right', flexShrink: 0 }}>
              {row.valueLabel}
            </Text>
          </Flex>
        )
        return (
          <Tooltip key={row.key} content={row.tooltip} side="top">
            {row.href ? (
              // SPA 이동 — <a href>는 전체 리로드라 액세스 토큰(메모리)과 목 store가 날아간다
              <Link to={row.href} style={{ color: 'inherit', textDecoration: 'none', display: 'block' }}>
                {bar}
              </Link>
            ) : (
              <div>{bar}</div>
            )}
          </Tooltip>
        )
      })}
    </Flex>
  )
}

/**
 * 두 조각 누적 막대 — 부분/전체 (emphasis: 강조 1색 + 흐린 gray).
 * 범례를 항상 붙인다 — 색만으로 뜻을 전하지 않는다 (10 §2.6).
 */
export function SplitBar({
  parts, ariaLabel,
}: { parts: { key: string; label: string; value: number; color: string }[]; ariaLabel: string }) {
  const total = parts.reduce((sum, p) => sum + p.value, 0)
  return (
    <Box>
      <Flex gap="2px" aria-label={ariaLabel} role="img" style={{ height: 8, borderRadius: 4, overflow: 'hidden', background: total === 0 ? 'var(--gray-a3)' : undefined }}>
        {total > 0 &&
          parts.filter((p) => p.value > 0).map((p) => (
            <Box key={p.key} style={{ width: `${(p.value / total) * 100}%`, background: p.color, transition: 'width var(--motion-base) var(--ease-out)' }} />
          ))}
      </Flex>
      <Flex gap="3" mt="1" wrap="wrap">
        {parts.map((p) => (
          <Flex key={p.key} align="center" gap="1">
            <Box style={{ width: 8, height: 8, borderRadius: 2, background: p.color }} aria-hidden />
            <Text size="1" color="gray" style={{ fontVariantNumeric: 'tabular-nums' }}>
              {p.label} {p.value}
            </Text>
          </Flex>
        ))}
      </Flex>
    </Box>
  )
}
