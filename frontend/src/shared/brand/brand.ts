import logoUrl from '../../assets/brand/logo.svg'
import markUrl from '../../assets/brand/mark.svg'

/** 브랜드 상수. 이름·문구·로고·색은 여기서만 정의한다. 파비콘·매니페스트는 index.html·public/ 담당 */
export const BRAND = {
  name: '2JO',
  /** 로그인 헤드라인 (01-problem-definition.md §1) */
  headline: '견적, 보내면 끝이 아니라 시작.',
  tagline: '견적 → 승인 → 주문, 한 줄로 이어집니다.',
  logo: {
    src: logoUrl,
    /** SVG viewBox 1341×620 */
    aspectRatio: 1341 / 620,
  },
  /** 정사각 심볼 — 접힌 사이드바용 */
  mark: markUrl,
  /** 로고 색. index.html theme-color·매니페스트와 동일 */
  color: {
    navy: '#0b1323',
    blue: '#2266ef',
  },
} as const
