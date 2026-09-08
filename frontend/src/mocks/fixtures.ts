/**
 * 목 픽스처 — 백엔드 시드(backend/src/main/resources/db/seed/R__demo_seed.sql)와 같은 ID·같은 수치.
 * "이 한 세트가 목 데이터이자 백엔드 시드이자 시연 데이터다" (docs/12-frontend-plan.md §5.2)
 * 필드 형태는 백엔드 DTO record(없으면 docs/08-dto.md)와 1:1 (UUID=string, 금액=number 원, 시각=ISO-8601).
 * 한쪽을 수정하면 반드시 함께 수정한다.
 *
 * 이 파일은 **읽기 전용 원본**이다. 핸들러가 바꾸는 상태는 `store.ts`가 복사해서 든다.
 *
 * 시연 계정(비밀번호 전부 test1234!): seoyeon@hanbit.co.kr(관리자) · jihun@hanbit.co.kr(영업)
 * 플랫폼 관리자: admin@2jo.io
 */

import type {
  ActivityChannel, ApplicationStatus, AuditActorType, DealStage, InvitationStatus, MemberStatus, NotificationType,
  ProductStatus, QuoteStatus, Role, VatMode,
} from '../shared/ui/status'

// ── id 상수 (시드 UUID 규칙: 테이블별 hex 프리픽스) ──────────────────────────
export const U = (p: string, n: number, w = 12) => `${p}000000-0000-4000-8000-${String(n).padStart(w, '0')}`
export const memberId = (n: number) => U('1e', n)
export const customerId = (n: number) => U('2c', n)
export const contactId = (n: number) => U('3c', n)
export const productId = (n: number) => U('4b', n)
export const dealId = (n: number) => U('5d', n)
export const quoteId = (n: number) => U('6a', n)
export const viewTokenId = (n: number) => U('7a', n)
export const orderId = (n: number) => U('8a', n)

export const COMPANY = { id: U('c0', 1), name: '한빛오피스', businessNo: '123-45-67890', applicationId: U('0a', 1) }

// ── 로그인 데모 (MSW auth 핸들러용) — 비밀번호 + memberId. 응답은 핸들러가 members에서 조립 ──
export const demoAccounts = [
  { email: 'seoyeon@hanbit.co.kr', password: 'test1234!', memberId: memberId(1), accessToken: 'mock-access-seoyeon' },
  { email: 'jihun@hanbit.co.kr', password: 'test1234!', memberId: memberId(2), accessToken: 'mock-access-jihun' },
]

/** 플랫폼 관리자 1명 — platform_admin 시드. LoginResponse의 companyName은 null (08 §A) */
export const demoPlatformAdmin = {
  id: U('ad', 1), email: 'admin@2jo.io', password: 'test1234!', name: '2JO 운영자', accessToken: 'mock-admin-access',
}

// ── 구성원 6명 — MemberResponse ──────────────────────────────────────────────
export const members: { id: string; name: string; email: string; phone: string | null; role: Role; status: MemberStatus; createdAt: string }[] = [
  { id: memberId(1), name: '김서연', email: 'seoyeon@hanbit.co.kr', phone: '010-2000-0001', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-08-10T01:00:00Z' },
  { id: memberId(2), name: '박지훈', email: 'jihun@hanbit.co.kr', phone: '010-2000-0002', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T01:00:00Z' },
  { id: memberId(3), name: '최민아', email: 'mina@hanbit.co.kr', phone: '010-2000-0003', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T01:00:00Z' },
  { id: memberId(4), name: '이준호', email: 'junho@hanbit.co.kr', phone: '010-2000-0004', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T02:00:00Z' },
  { id: memberId(5), name: '정하늘', email: 'haneul@hanbit.co.kr', phone: '010-2000-0005', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T02:00:00Z' },
  { id: memberId(6), name: '오세영', email: 'seyoung@hanbit.co.kr', phone: '010-2000-0006', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T03:00:00Z' },
]
const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? ''

// ── 고객사 7 · 담당자 8 — CustomerResponse / ContactResponse ─────────────────
export const customers: { id: string; name: string; industry: string | null; size: string | null; note: string | null; createdByMemberId: string; createdAt: string }[] = [
  { id: customerId(1), name: '도담건설', industry: '건설', size: '50~100명', note: '사무실 리모델링 진행 중 — 전시회에서 명함 교환', createdByMemberId: memberId(2), createdAt: '2026-08-18T01:00:00Z' },
  { id: customerId(2), name: '성원산업', industry: '제조', size: '100~300명', note: '비품 정기납품 협의 중', createdByMemberId: memberId(2), createdAt: '2026-08-14T01:00:00Z' },
  { id: customerId(3), name: '대한물산', industry: '유통', size: '50~100명', note: null, createdByMemberId: memberId(3), createdAt: '2026-08-13T01:00:00Z' },
  { id: customerId(4), name: '신영건설', industry: '건설', size: '300명 이상', note: '지점 다수 — 반복 발주 기대', createdByMemberId: memberId(2), createdAt: '2026-08-12T01:00:00Z' },
  { id: customerId(5), name: '태성기업', industry: 'IT', size: '~50명', note: null, createdByMemberId: memberId(4), createdAt: '2026-08-19T01:00:00Z' },
  { id: customerId(6), name: '한울에너지', industry: '에너지', size: '100~300명', note: '사옥 리모델링 예산 협의 중', createdByMemberId: memberId(3), createdAt: '2026-08-13T02:00:00Z' },
  { id: customerId(7), name: '미래상사', industry: '유통', size: '~50명', note: '전시장 가구 건 — 경쟁사 선정으로 종료', createdByMemberId: memberId(4), createdAt: '2026-08-12T02:00:00Z' },
]

export const contacts: Record<string, { id: string; name: string; title: string | null; phone: string | null; email: string; primary: boolean }[]> = {
  [customerId(1)]: [
    { id: contactId(1), name: '이수정', title: '총무팀 대리', email: 'sujeong@dodam.co.kr', phone: '010-3000-0001', primary: true },
    { id: contactId(2), name: '박건우', title: '구매팀 사원', email: 'gunwoo@dodam.co.kr', phone: '010-3000-0002', primary: false },
  ],
  [customerId(2)]: [{ id: contactId(3), name: '강민철', title: '구매과장', email: 'minchul@sungwon.co.kr', phone: '010-3000-0003', primary: true }],
  [customerId(3)]: [{ id: contactId(4), name: '윤소라', title: '총무 대리', email: 'sora@daehan.co.kr', phone: '010-3000-0004', primary: true }],
  [customerId(4)]: [{ id: contactId(5), name: '임재현', title: '관리팀장', email: 'jaehyun@shinyoung.co.kr', phone: '010-3000-0005', primary: true }],
  [customerId(5)]: [{ id: contactId(6), name: '조은비', title: '대표', email: 'eunbi@taesung.kr', phone: '010-3000-0006', primary: true }],
  [customerId(6)]: [{ id: contactId(7), name: '서동윤', title: '시설담당', email: 'dongyun@hanul.co.kr', phone: '010-3000-0007', primary: true }],
  [customerId(7)]: [{ id: contactId(8), name: '노윤아', title: '구매담당', email: 'yuna@mirae.co.kr', phone: '010-3000-0008', primary: true }],
}
const customerName = (id: string) => customers.find((c) => c.id === id)?.name ?? ''

// ── 상품 8종 (판매 중지 1 포함) — ProductResponse ────────────────────────────
export const products: { id: string; name: string; unit: string; unitPrice: number; description: string | null; status: ProductStatus }[] = [
  { id: productId(1), name: '1200 사무책상', unit: '개', unitPrice: 180000, description: '폭 1200mm 표준 사무용 책상', status: 'ACTIVE' },
  { id: productId(2), name: '1600 사무책상', unit: '개', unitPrice: 240000, description: '폭 1600mm 대형 책상', status: 'ACTIVE' },
  { id: productId(3), name: '메쉬 의자', unit: '개', unitPrice: 100000, description: '통기성 메쉬 소재 사무 의자', status: 'ACTIVE' },
  { id: productId(4), name: '패브릭 소파', unit: '개', unitPrice: 450000, description: '3인용 라운지 소파', status: 'ACTIVE' },
  { id: productId(5), name: '3단 수납장', unit: '개', unitPrice: 120000, description: '잠금장치 포함', status: 'ACTIVE' },
  { id: productId(6), name: '회의 테이블', unit: '개', unitPrice: 350000, description: '6인용 회의 테이블', status: 'ACTIVE' },
  { id: productId(7), name: '파티션(1200)', unit: '개', unitPrice: 90000, description: '높이 1200mm 패브릭 파티션', status: 'ACTIVE' },
  { id: productId(8), name: '구형 1200 책상', unit: '개', unitPrice: 150000, description: '단종 모델 — 새 견적 추가 불가 데모', status: 'DISCONTINUED' },
]

// ── Deal 17건 — DealResponse (파이프라인: LEAD 4 · CONSULT 3 · QUOTE 4 · NEGO 2 · WON 3 · LOST 1)
type DealSeed = [n: number, title: string, stage: DealStage, expected: number, custN: number, assigneeN: number, due: string, won?: number, lost?: { reason: string; from: DealStage }]
const dealSeeds: DealSeed[] = [
  [1, '태성기업 탕비실 집기', 'LEAD', 600000, 5, 2, '2026-09-16'],
  [2, '도담건설 2층 증축 가구', 'LEAD', 15000000, 1, 3, '2026-10-15'],
  [3, '성원산업 휴게실 리모델', 'LEAD', 3500000, 2, 4, '2026-09-30'],
  [4, '대한물산 지점 개소 비품', 'LEAD', 5000000, 3, 5, '2026-10-02'],
  [5, '성원산업 비품 정기납품', 'CONSULT', 8800000, 2, 2, '2026-08-30'],
  [6, '한울에너지 사옥 파티션', 'CONSULT', 6200000, 6, 3, '2026-09-20'],
  [7, '신영건설 모델하우스 가구', 'CONSULT', 9000000, 4, 6, '2026-09-25'],
  [8, '도담건설 사무가구 납품', 'QUOTE', 12000000, 1, 2, '2026-09-15'],
  [9, '성원산업 회의실 리뉴얼', 'QUOTE', 4600000, 2, 2, '2026-09-10'],
  [10, '대한물산 사무실 확장', 'QUOTE', 7300000, 3, 3, '2026-09-18'],
  [11, '태성기업 회의 테이블', 'QUOTE', 1400000, 5, 4, '2026-09-12'],
  [12, '한울에너지 리모델링', 'NEGOTIATION', 26000000, 6, 3, '2026-09-10'],
  [13, '도담건설 본사 라운지', 'NEGOTIATION', 18000000, 1, 2, '2026-09-22'],
  [14, '신영건설 지점 집기', 'WON', 24000000, 4, 2, '2026-09-05', 24200000],
  [15, '대한물산 창고 선반', 'WON', 14000000, 3, 3, '2026-08-29', 15400000],
  [16, '성원산업 사무의자 교체', 'WON', 8000000, 2, 2, '2026-09-03', 8800000],
  [17, '미래상사 전시장 가구', 'LOST', 20000000, 7, 4, '2026-09-08', undefined, { reason: '경쟁사 선정', from: 'QUOTE' }],
]
export const deals = dealSeeds.map(([n, title, stage, expectedAmount, custN, assigneeN, dueDate, won, lost]) => ({
  id: dealId(n),
  title,
  stage,
  expectedAmount,
  wonAmount: won ?? null, // DL-18: 성사 후 표시 금액 = 주문 합계
  customerId: customerId(custN),
  customerName: customerName(customerId(custN)),
  assigneeMemberId: memberId(assigneeN),
  assigneeMemberName: memberName(memberId(assigneeN)),
  dueDate,
  version: 0,
  createdAt: `2026-08-${String(10 + (n % 9)).padStart(2, '0')}T01:00:00Z`,
  /** 시드 컬럼 — DealDetailResponse.lostReason · 재개용 lost_from_stage (DL-12) */
  lostReason: lost?.reason ?? null,
  lostFromStage: lost?.from ?? null,
}))

// ── 견적 12건 — 시드 quote 행 그대로 (상태별 최소 1건씩) ─────────────────────
type QuoteSeed = [n: number, dealN: number, status: QuoteStatus, supply: number, valid: string, sentAt: string | null, viewedAt: string | null, respondedAt: string | null, terms: string | null, clonedFromN: number | null, rejectReason: string | null, responderName: string | null, responderTitle: string | null]
const quoteSeeds: QuoteSeed[] = [
  [1, 14, 'APPROVED', 22000000, '2026-08-31', '2026-08-18T01:00:00Z', '2026-08-19T00:30:00Z', '2026-08-20T06:00:00Z', '납품은 지점별 순차 진행됩니다.', null, null, '임재현', '관리팀장'],
  [2, 15, 'APPROVED', 14000000, '2026-09-05', '2026-08-20T02:00:00Z', '2026-08-21T01:00:00Z', '2026-08-22T05:00:00Z', null, null, null, '윤소라', '총무 대리'],
  [3, 16, 'APPROVED', 8000000, '2026-09-10', '2026-08-22T00:00:00Z', '2026-08-23T04:00:00Z', '2026-08-25T01:30:00Z', null, null, null, '강민철', '구매과장'],
  [5, 17, 'EXPIRED', 18400000, '2026-09-01', '2026-08-15T01:00:00Z', null, null, null, null, null, null, null],
  [7, 12, 'REJECTED', 26000000, '2026-09-05', '2026-08-19T01:00:00Z', '2026-08-20T00:00:00Z', '2026-08-21T07:00:00Z', null, null, '예산 초과', '서동윤', '시설담당'],
  [8, 13, 'VIEWED', 18000000, '2026-09-12', '2026-08-21T05:00:00Z', '2026-08-24T01:00:00Z', null, null, null, null, null, null],
  [9, 8, 'WITHDRAWN', 2900000, '2026-09-09', '2026-08-20T01:00:00Z', null, null, null, null, null, null, null],
  [10, 11, 'SENT', 1400000, '2026-09-12', '2026-08-25T06:00:00Z', null, null, null, null, null, null, null],
  [11, 9, 'SENT', 4180000, '2026-09-30', '2026-08-23T00:00:00Z', null, null, null, null, null, null, null],
  [13, 10, 'DRAFT', 4800000, '2026-09-20', null, null, null, null, null, null, null, null],
  [14, 8, 'VIEWED', 3050000, '2026-09-30', '2026-08-24T01:00:00Z', '2026-08-25T05:20:00Z', null, '설치는 납품일로부터 3일 이내 진행됩니다.', 9, null, null, null],
  [16, 12, 'SENT', 23800000, '2026-09-09', '2026-08-26T02:00:00Z', null, null, '단가 재조정안입니다. 검토 부탁드립니다.', 7, null, null, null],
]
/** 대체 관계(QT-28) — 반려·회수된 견적 → 그것을 대체한 복제본 (clonedFrom의 역방향) */
const supersededBy: Record<number, number> = { 7: 16, 9: 14 }

/** QuoteDetailResponse에서 items·dealTitle을 뺀 본체 + 목록(QuoteResponse)이 공유하는 필드 */
export const quotes = quoteSeeds.map(([n, dealN, status, supply, validUntil, sentAt, firstViewedAt, respondedAt, terms, clonedFromN, rejectReason, responderName, responderTitle]) => ({
  id: quoteId(n),
  quoteNo: `Q-2608-${String(n).padStart(3, '0')}`,
  dealId: dealId(dealN),
  status,
  vatMode: 'EXCLUDED' as VatMode,
  terms,
  validUntil,
  supplyAmount: supply,
  vatAmount: supply / 10,
  totalAmount: supply + supply / 10,
  clonedFromQuoteId: clonedFromN === null ? null : quoteId(clonedFromN),
  supersededByQuoteId: supersededBy[n] === undefined ? null : quoteId(supersededBy[n]),
  rejectReason,
  responderName,
  responderTitle,
  sentAt,
  firstViewedAt,
  respondedAt,
  version: 0,
  createdAt: sentAt ?? '2026-08-27T01:00:00Z',
}))

/** 견적 항목 — QuoteDetailResponse.ItemResponse[] (quoteId 키). id 규칙: 61...0000QQII */
export type QuoteItemSeed = { id: string; productId: string | null; name: string; unit: string; quantity: number; unitPrice: number; amount: number; catalogPriceAtCreation: number | null; sortOrder: number }
const item = (q: number, productId: string | null, name: string, unit: string, quantity: number, unitPrice: number, catalogPriceAtCreation: number | null, sortOrder: number): QuoteItemSeed => ({
  id: U('61', q * 100 + sortOrder + 1), productId, name, unit, quantity, unitPrice, amount: quantity * unitPrice, catalogPriceAtCreation, sortOrder,
})
export const quoteItems: Record<string, QuoteItemSeed[]> = {
  [quoteId(1)]: [
    item(1, productId(2), '1600 사무책상', '개', 40, 240000, 240000, 0),
    item(1, productId(3), '메쉬 의자', '개', 60, 100000, 100000, 1),
    item(1, productId(5), '3단 수납장', '개', 20, 120000, 120000, 2),
    item(1, productId(6), '회의 테이블', '개', 8, 350000, 350000, 3),
    item(1, null, '납품·설치비', '식', 1, 1200000, null, 4),
  ],
  [quoteId(2)]: [item(2, null, '중량 선반', '개', 50, 260000, null, 0), item(2, null, '운반·설치', '식', 1, 1000000, null, 1)],
  [quoteId(3)]: [item(3, productId(3), '메쉬 의자', '개', 80, 100000, 100000, 0)],
  [quoteId(5)]: [item(5, productId(4), '패브릭 소파', '개', 20, 450000, 450000, 0), item(5, productId(6), '회의 테이블', '개', 20, 350000, 350000, 1), item(5, productId(5), '3단 수납장', '개', 20, 120000, 120000, 2)],
  [quoteId(7)]: [item(7, productId(7), '파티션(1200)', '개', 100, 90000, 90000, 0), item(7, productId(2), '1600 사무책상', '개', 50, 240000, 240000, 1), item(7, productId(3), '메쉬 의자', '개', 50, 100000, 100000, 2)],
  [quoteId(8)]: [item(8, productId(4), '패브릭 소파', '개', 20, 450000, 450000, 0), item(8, productId(6), '회의 테이블', '개', 20, 350000, 350000, 1), item(8, null, '라운지 테이블', '개', 10, 200000, null, 2)],
  [quoteId(9)]: [item(9, productId(1), '1200 사무책상', '개', 10, 180000, 180000, 0), item(9, productId(3), '메쉬 의자', '개', 10, 110000, 100000, 1)],
  [quoteId(10)]: [item(10, productId(6), '회의 테이블', '개', 4, 350000, 350000, 0)],
  [quoteId(11)]: [item(11, productId(6), '회의 테이블', '개', 8, 350000, 350000, 0), item(11, productId(3), '메쉬 의자', '개', 12, 95000, 100000, 1), item(11, null, '운반비', '식', 1, 240000, null, 2)],
  [quoteId(13)]: [item(13, productId(1), '1200 사무책상', '개', 20, 180000, 180000, 0), item(13, productId(5), '3단 수납장', '개', 10, 120000, 120000, 1)],
  // S-01 3막 그대로 — 카탈로그 2건(의자는 5% 조정) + 직접 입력
  [quoteId(14)]: [item(14, productId(1), '1200 사무책상', '개', 10, 180000, 180000, 0), item(14, productId(3), '메쉬 의자', '개', 10, 95000, 100000, 1), item(14, null, '설치·배송비', '식', 1, 300000, null, 2)],
  [quoteId(16)]: [item(16, productId(7), '파티션(1200)', '개', 100, 85000, 90000, 0), item(16, productId(2), '1600 사무책상', '개', 50, 230000, 240000, 1), item(16, productId(3), '메쉬 의자', '개', 50, 76000, 100000, 2)],
}

// ── 열람 링크 — 시드 quote_view_token 그대로 (견적당 활성 최대 1개, AP-03) ──────
export type ViewTokenSeed = {
  id: string; quoteId: string; recipientContactId: string; status: 'ACTIVE' | 'RESPONDED' | 'EXPIRED'
  expiredReason: 'TIME' | 'MANUAL' | 'WITHDRAWN' | 'RESENT' | 'DEAL_LOST' | null; expiresAt: string
  /** 목 전용 — 메일 링크의 원문 토큰(/q/:token). 실제로는 해시만 저장된다 */
  rawToken: string
}
const vt = (n: number, contactN: number, status: ViewTokenSeed['status'], expiredReason: ViewTokenSeed['expiredReason'], expires: string, rawToken: string): ViewTokenSeed =>
  ({ id: viewTokenId(n), quoteId: quoteId(n), recipientContactId: contactId(contactN), status, expiredReason, expiresAt: `${expires}T14:59:59Z`, rawToken })
export const viewTokens: ViewTokenSeed[] = [
  vt(1, 5, 'RESPONDED', null, '2026-08-31', 'demo-shinyoung-01'),
  vt(2, 4, 'RESPONDED', null, '2026-09-05', 'demo-daehan-02'),
  vt(3, 3, 'RESPONDED', null, '2026-09-10', 'demo-sungwon-03'),
  vt(5, 8, 'EXPIRED', 'DEAL_LOST', '2026-09-01', 'demo-mirae-05'),
  vt(7, 7, 'RESPONDED', null, '2026-09-05', 'demo-hanul-07'),
  vt(8, 1, 'ACTIVE', null, '2026-09-12', 'demo-dodam-08'),
  vt(9, 1, 'EXPIRED', 'WITHDRAWN', '2026-09-09', 'demo-dodam-09'),
  vt(10, 6, 'ACTIVE', null, '2026-09-12', 'demo-taesung-10'),
  vt(11, 3, 'ACTIVE', null, '2026-09-30', 'demo-sungwon-11'),
  vt(14, 1, 'ACTIVE', null, '2026-09-30', 'demo-dodam-14'), // 메인 시나리오 — 이수정이 여는 링크
  vt(16, 7, 'ACTIVE', null, '2026-09-09', 'demo-hanul-16'),
]

// ── 초대 1건 — 시드 invitation과 같은 값 (PENDING) ─────────────────────────
export const invitations: { id: string; email: string; role: Role; status: InvitationStatus; expiresAt: string; createdAt: string; rawToken: string }[] = [
  { id: U('1f', 1), email: 'newbie@hanbit.co.kr', role: 'SALES_REP', status: 'PENDING', expiresAt: '2026-09-02T14:59:59Z', createdAt: '2026-08-26T15:00:00Z', rawToken: 'demo-invite' },
]

// ── 주문 3건 — OrderResponse (이달 성사 합계 48,400,000) ─────────────────────
export const orders: { id: string; orderNo: string; quoteId: string; quoteNo: string; dealId: string; dealTitle: string; customerId: string; customerName: string; supplyAmount: number; vatAmount: number; totalAmount: number; startDate: string | null; deliveryDate: string | null; createdAt: string }[] = [
  { id: orderId(1), orderNo: 'O-2608-001', quoteId: quoteId(1), quoteNo: 'Q-2608-001', dealId: dealId(14), dealTitle: '신영건설 지점 집기', customerId: customerId(4), customerName: '신영건설', supplyAmount: 22000000, vatAmount: 2200000, totalAmount: 24200000, startDate: '2026-09-01', deliveryDate: '2026-09-12', createdAt: '2026-08-20T07:00:00Z' },
  { id: orderId(2), orderNo: 'O-2608-002', quoteId: quoteId(2), quoteNo: 'Q-2608-002', dealId: dealId(15), dealTitle: '대한물산 창고 선반', customerId: customerId(3), customerName: '대한물산', supplyAmount: 14000000, vatAmount: 1400000, totalAmount: 15400000, startDate: '2026-08-28', deliveryDate: '2026-09-05', createdAt: '2026-08-22T06:00:00Z' },
  { id: orderId(3), orderNo: 'O-2608-003', quoteId: quoteId(3), quoteNo: 'Q-2608-003', dealId: dealId(16), dealTitle: '성원산업 사무의자 교체', customerId: customerId(2), customerName: '성원산업', supplyAmount: 8000000, vatAmount: 800000, totalAmount: 8800000, startDate: '2026-09-02', deliveryDate: null, createdAt: '2026-08-25T02:00:00Z' },
]
/** 주문 항목 — 시드 order_item (FK 없는 값 복사, OD-04) */
export const orderItems: Record<string, { name: string; unit: string; quantity: number; unitPrice: number; amount: number }[]> = {
  [orderId(1)]: [
    { name: '1600 사무책상', unit: '개', quantity: 40, unitPrice: 240000, amount: 9600000 },
    { name: '메쉬 의자', unit: '개', quantity: 60, unitPrice: 100000, amount: 6000000 },
    { name: '3단 수납장', unit: '개', quantity: 20, unitPrice: 120000, amount: 2400000 },
    { name: '회의 테이블', unit: '개', quantity: 8, unitPrice: 350000, amount: 2800000 },
    { name: '납품·설치비', unit: '식', quantity: 1, unitPrice: 1200000, amount: 1200000 },
  ],
  [orderId(2)]: [
    { name: '중량 선반', unit: '개', quantity: 50, unitPrice: 260000, amount: 13000000 },
    { name: '운반·설치', unit: '식', quantity: 1, unitPrice: 1000000, amount: 1000000 },
  ],
  [orderId(3)]: [{ name: '메쉬 의자', unit: '개', quantity: 80, unitPrice: 100000, amount: 8000000 }],
}

// ── 상담 기록 4건 · 할 일 3건 — 시드 activity / task (S-01 2막) ──────────────
export const activities: { id: string; dealId: string; authorMemberId: string; channel: ActivityChannel; content: string; occurredAt: string }[] = [
  { id: U('9a', 1), dealId: dealId(8), authorMemberId: memberId(2), channel: 'CALL', content: '리모델링 일정 확인', occurredAt: '2026-08-22T00:30:00Z' },
  { id: U('9a', 2), dealId: dealId(8), authorMemberId: memberId(2), channel: 'MEETING', content: '방문 미팅 — 사양 협의, 예산 1,300만 선', occurredAt: '2026-08-25T02:00:00Z' },
  { id: U('9a', 3), dealId: dealId(5), authorMemberId: memberId(2), channel: 'CALL', content: '정기납품 물량·주기 협의', occurredAt: '2026-08-24T06:00:00Z' },
  { id: U('9a', 4), dealId: dealId(12), authorMemberId: memberId(3), channel: 'MEETING', content: '견적 재협의 — 예산 상한 확인, 단가 조정안 요청받음', occurredAt: '2026-08-25T07:00:00Z' },
]

export const tasks: { id: string; dealId: string; content: string; dueDate: string; doneAt: string | null }[] = [
  { id: U('9b', 1), dealId: dealId(5), content: '성원산업 재방문 일정 조율', dueDate: '2026-08-26', doneAt: null },
  { id: U('9b', 2), dealId: dealId(10), content: '대한물산 재검토 회신 확인', dueDate: '2026-09-01', doneAt: null },
  { id: U('9b', 3), dealId: dealId(8), content: '견적서 초안 공유', dueDate: '2026-08-23', doneAt: '2026-08-23T09:00:00Z' },
]

// ── 고객 문의 1건 — 시드 customer_inquiry (조회 API는 v1에 없다, Q-42) ────────
export const inquiries = [
  { id: U('ac', 1), quoteId: quoteId(14), content: '설치 일정 조율이 가능한가요? 9월 둘째 주 희망합니다.', createdAt: '2026-08-25T06:00:00Z' },
]

// ── 인앱 알림 5건 — 시드 notification (수신자 포함) ───────────────────────────
export const notifications: { id: string; recipientMemberId: string; type: NotificationType; message: string; refType: 'QUOTE'; refId: string; readAt: string | null; createdAt: string }[] = [
  { id: U('9c', 1), recipientMemberId: memberId(2), type: 'QUOTE_VIEWED', message: '도담건설 담당자가 견적을 열람했습니다 (Q-2608-014)', refType: 'QUOTE', refId: quoteId(14), readAt: null, createdAt: '2026-08-25T05:20:00Z' },
  { id: U('9c', 2), recipientMemberId: memberId(2), type: 'QUOTE_APPROVED', message: '성원산업 담당자가 견적을 승인했습니다 (Q-2608-003)', refType: 'QUOTE', refId: quoteId(3), readAt: '2026-08-25T02:00:00Z', createdAt: '2026-08-25T01:30:00Z' },
  { id: U('9c', 3), recipientMemberId: memberId(3), type: 'QUOTE_REJECTED', message: '한울에너지 담당자가 견적을 반려했습니다 (Q-2608-007)', refType: 'QUOTE', refId: quoteId(7), readAt: '2026-08-21T08:00:00Z', createdAt: '2026-08-21T07:00:00Z' },
  { id: U('9c', 4), recipientMemberId: memberId(2), type: 'INQUIRY_RECEIVED', message: '도담건설 담당자가 문의를 남겼습니다 (Q-2608-014)', refType: 'QUOTE', refId: quoteId(14), readAt: null, createdAt: '2026-08-25T06:00:00Z' },
  { id: U('9c', 5), recipientMemberId: memberId(1), type: 'INQUIRY_RECEIVED', message: '도담건설 담당자가 문의를 남겼습니다 (Q-2608-014)', refType: 'QUOTE', refId: quoteId(14), readAt: null, createdAt: '2026-08-25T06:00:00Z' },
]

// ── 감사 로그 3건 — 시드 audit_log (payload는 "변경된 필드만 before/after" 규약) ─
export const auditLogs: { id: string; entityType: string; entityId: string; eventType: string; actorType: AuditActorType; actorId: string | null; occurredAt: string; changes: Record<string, { before: unknown; after: unknown }> }[] = [
  { id: U('9e', 1), entityType: 'DEAL', entityId: dealId(8), eventType: 'STAGE_MOVED', actorType: 'SYSTEM', actorId: null, occurredAt: '2026-08-24T01:00:00Z', changes: { stage: { before: 'CONSULT', after: 'QUOTE' } } },
  { id: U('9e', 2), entityType: 'QUOTE', entityId: quoteId(14), eventType: 'QUOTE_SENT', actorType: 'MEMBER', actorId: memberId(2), occurredAt: '2026-08-24T01:00:00Z', changes: { status: { before: 'DRAFT', after: 'SENT' } } },
  { id: U('9e', 3), entityType: 'QUOTE', entityId: quoteId(14), eventType: 'QUOTE_VIEWED', actorType: 'CUSTOMER_LINK', actorId: null, occurredAt: '2026-08-25T05:20:00Z', changes: { status: { before: 'SENT', after: 'VIEWED' } } },
]

// ── 채번 카운터 — 시드 document_sequence (회사·문서종류·연월별 last_seq). 시드 마지막 번호 Q-2608-016 · O-2608-003
export const documentSequences: { id: string; companyId: string; docType: 'QUOTE' | 'ORDER'; yearMonth: string; lastSeq: number }[] = [
  { id: U('9f', 1), companyId: COMPANY.id, docType: 'QUOTE', yearMonth: '2608', lastSeq: 16 },
  { id: U('9f', 2), companyId: COMPANY.id, docType: 'ORDER', yearMonth: '2608', lastSeq: 3 },
]

// ── 온보딩 — 시드 application(승인됨) + 회사. 시드에 없는 행은 두지 않는다 (12 §5.2 — 목 = 시드 한 세트).
//    관리자 화면의 검토 대기 건은 `/apply`로 신청을 넣으면 그 자리에서 생긴다.
export const applications: { id: string; companyName: string; businessNo: string; email: string; applicantName: string; status: ApplicationStatus; rejectReason: string | null; decidedAt: string | null; createdAt: string }[] = [
  { id: COMPANY.applicationId, companyName: '한빛오피스', businessNo: '123-45-67890', email: 'seoyeon@hanbit.co.kr', applicantName: '김서연', status: 'APPROVED', rejectReason: null, decidedAt: '2026-08-10T01:00:00Z', createdAt: '2026-08-09T00:00:00Z' },
]

export const companies: { id: string; name: string; businessNo: string; status: 'ACTIVE' | 'SUSPENDED'; suspendReason: string | null; memberCount: number; createdAt: string }[] = [
  { id: COMPANY.id, name: COMPANY.name, businessNo: COMPANY.businessNo, status: 'ACTIVE', suspendReason: null, memberCount: members.length, createdAt: '2026-08-10T01:00:00Z' },
]
