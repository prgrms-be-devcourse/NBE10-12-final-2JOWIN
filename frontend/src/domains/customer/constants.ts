/** 업종·규모 선택지 — 서버는 자유 문자열(08-dto.md)이지만 화면에서는 제한한다. 픽스처·시드와 동일 */
export const INDUSTRIES = ['건설', '제조', '유통', 'IT', '에너지', '서비스', '교육', '기타'] as const
export const COMPANY_SIZES = ['~50명', '50~100명', '100~300명', '300명 이상'] as const

/** 목록 기본 페이지 크기 (12-frontend-plan.md §6.4 — 20, 최대 100) */
export const PAGE_SIZE = 20

/** Select 공통 설정 — popper 배치, 10줄(328px) 초과 시 스크롤 */
export const SELECT_CONTENT = {
  position: 'popper',
  style: { maxHeight: 'min(328px, var(--radix-select-content-available-height))' },
} as const

/** 업종 분류 색 — 의미 색(blue·amber·green·red)과 그 이웃은 쓰지 않는다 (10 §2.3) */
export const INDUSTRY_COLORS = {
  건설: 'brown',
  제조: 'teal',
  유통: 'plum',
  IT: 'violet',
  에너지: 'bronze',
  서비스: 'pink',
  교육: 'cyan',
  기타: 'gray',
} as const satisfies Record<(typeof INDUSTRIES)[number], string>
