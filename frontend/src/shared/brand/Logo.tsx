import { BRAND } from './brand'

interface LogoProps {
  /** 높이(px). 너비는 비율로 결정된다 */
  height?: number
  className?: string
}

/** 브랜드 워드마크 */
export function Logo({ height = 24, className }: LogoProps) {
  const { src, aspectRatio } = BRAND.logo
  return (
    <img
      src={src}
      alt={BRAND.name}
      className={className}
      draggable={false}
      style={{ display: 'block', height, width: height * aspectRatio, userSelect: 'none' }}
    />
  )
}
