/**
 * 목 픽스처 — 백엔드 시드(backend/src/main/resources/db/seed/R__demo_seed.sql)와 **같은 ID·같은 수치**.
 * "이 한 세트가 목 데이터이자 백엔드 시드이자 시연 데이터다" (docs/12-frontend-plan.md §5.2)
 * 필드 형태는 docs/08-dto.md의 Response record와 1:1 (UUID=string, 금액=number 원, 시각=ISO-8601).
 * 한쪽을 수정하면 반드시 함께 수정한다.
 *
 * 시연 계정(비밀번호 전부 test1234!): seoyeon@hanbit.co.kr(관리자) · jihun@hanbit.co.kr(영업)
 */

// ── id 상수 (시드 UUID 규칙: 테이블별 hex 프리픽스) ──────────────────────────
const U = (p: string, n: number, w = 12) => `${p}000000-0000-4000-8000-${String(n).padStart(w, '0')}`
const member = (n: number) => U('1e', n)
const customer = (n: number) => U('2c', n)
const contact = (n: number) => U('3c', n)
const product = (n: number) => U('4b', n)
const deal = (n: number) => U('5d', n)
const quote = (n: number) => U('6a', n)
const order = (n: number) => U('8a', n)

export const COMPANY = { id: U('c0', 1), name: '한빛오피스', businessNo: '123-45-67890' }

// ── 로그인 데모 (MSW auth 핸들러용) — LoginResponse 형태 ─────────────────────
export const demoAccounts = [
  { email: 'seoyeon@hanbit.co.kr', password: 'test1234!', login: { accessToken: 'mock-access-seoyeon', memberId: member(1), name: '김서연', role: 'COMPANY_ADMIN', companyName: '한빛오피스' } },
  { email: 'jihun@hanbit.co.kr', password: 'test1234!', login: { accessToken: 'mock-access-jihun', memberId: member(2), name: '박지훈', role: 'SALES_REP', companyName: '한빛오피스' } },
]

// ── 구성원 6명 — MemberResponse ──────────────────────────────────────────────
export const members = [
  { id: member(1), name: '김서연', email: 'seoyeon@hanbit.co.kr', phone: '010-2000-0001', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-08-10T01:00:00Z' },
  { id: member(2), name: '박지훈', email: 'jihun@hanbit.co.kr', phone: '010-2000-0002', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T01:00:00Z' },
  { id: member(3), name: '최민아', email: 'mina@hanbit.co.kr', phone: '010-2000-0003', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T01:00:00Z' },
  { id: member(4), name: '이준호', email: 'junho@hanbit.co.kr', phone: '010-2000-0004', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T02:00:00Z' },
  { id: member(5), name: '정하늘', email: 'haneul@hanbit.co.kr', phone: '010-2000-0005', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T02:00:00Z' },
  { id: member(6), name: '오세영', email: 'seyoung@hanbit.co.kr', phone: '010-2000-0006', role: 'SALES_REP', status: 'ACTIVE', createdAt: '2026-08-11T03:00:00Z' },
]
const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? ''

// ── 고객사 7 · 담당자 8 — CustomerResponse / ContactResponse ─────────────────
export const customers = [
  { id: customer(1), name: '도담건설', industry: '건설', size: '50~100명', note: '사무실 리모델링 진행 중 — 전시회에서 명함 교환', createdByMemberId: member(2), createdAt: '2026-08-18T01:00:00Z' },
  { id: customer(2), name: '성원산업', industry: '제조', size: '100~300명', note: '비품 정기납품 협의 중', createdByMemberId: member(2), createdAt: '2026-08-14T01:00:00Z' },
  { id: customer(3), name: '대한물산', industry: '유통', size: '50~100명', note: null, createdByMemberId: member(3), createdAt: '2026-08-13T01:00:00Z' },
  { id: customer(4), name: '신영건설', industry: '건설', size: '300명 이상', note: '지점 다수 — 반복 발주 기대', createdByMemberId: member(2), createdAt: '2026-08-12T01:00:00Z' },
  { id: customer(5), name: '태성기업', industry: 'IT', size: '~50명', note: null, createdByMemberId: member(4), createdAt: '2026-08-19T01:00:00Z' },
  { id: customer(6), name: '한울에너지', industry: '에너지', size: '100~300명', note: '사옥 리모델링 예산 협의 중', createdByMemberId: member(3), createdAt: '2026-08-13T02:00:00Z' },
  { id: customer(7), name: '미래상사', industry: '유통', size: '~50명', note: '전시장 가구 건 — 경쟁사 선정으로 종료', createdByMemberId: member(4), createdAt: '2026-08-12T02:00:00Z' },
]

export const contacts: Record<string, { id: string; name: string; title: string | null; phone: string | null; email: string; primary: boolean }[]> = {
  [customer(1)]: [
    { id: contact(1), name: '이수정', title: '총무팀 대리', email: 'sujeong@dodam.co.kr', phone: '010-3000-0001', primary: true },
    { id: contact(2), name: '박건우', title: '구매팀 사원', email: 'gunwoo@dodam.co.kr', phone: '010-3000-0002', primary: false },
  ],
  [customer(2)]: [{ id: contact(3), name: '강민철', title: '구매과장', email: 'minchul@sungwon.co.kr', phone: '010-3000-0003', primary: true }],
  [customer(3)]: [{ id: contact(4), name: '윤소라', title: '총무 대리', email: 'sora@daehan.co.kr', phone: '010-3000-0004', primary: true }],
  [customer(4)]: [{ id: contact(5), name: '임재현', title: '관리팀장', email: 'jaehyun@shinyoung.co.kr', phone: '010-3000-0005', primary: true }],
  [customer(5)]: [{ id: contact(6), name: '조은비', title: '대표', email: 'eunbi@taesung.kr', phone: '010-3000-0006', primary: true }],
  [customer(6)]: [{ id: contact(7), name: '서동윤', title: '시설담당', email: 'dongyun@hanul.co.kr', phone: '010-3000-0007', primary: true }],
  [customer(7)]: [{ id: contact(8), name: '노윤아', title: '구매담당', email: 'yuna@mirae.co.kr', phone: '010-3000-0008', primary: true }],
}
const customerName = (id: string) => customers.find((c) => c.id === id)?.name ?? ''

// ── 상품 8종 (판매 중지 1 포함) — ProductResponse ────────────────────────────
export const products = [
  { id: product(1), name: '1200 사무책상', unit: '개', unitPrice: 180000, description: '폭 1200mm 표준 사무용 책상', status: 'ACTIVE' },
  { id: product(2), name: '1600 사무책상', unit: '개', unitPrice: 240000, description: '폭 1600mm 대형 책상', status: 'ACTIVE' },
  { id: product(3), name: '메쉬 의자', unit: '개', unitPrice: 100000, description: '통기성 메쉬 소재 사무 의자', status: 'ACTIVE' },
  { id: product(4), name: '패브릭 소파', unit: '개', unitPrice: 450000, description: '3인용 라운지 소파', status: 'ACTIVE' },
  { id: product(5), name: '3단 수납장', unit: '개', unitPrice: 120000, description: '잠금장치 포함', status: 'ACTIVE' },
  { id: product(6), name: '회의 테이블', unit: '개', unitPrice: 350000, description: '6인용 회의 테이블', status: 'ACTIVE' },
  { id: product(7), name: '파티션(1200)', unit: '개', unitPrice: 90000, description: '높이 1200mm 패브릭 파티션', status: 'ACTIVE' },
  { id: product(8), name: '구형 1200 책상', unit: '개', unitPrice: 150000, description: '단종 모델 — 새 견적 추가 불가 데모', status: 'DISCONTINUED' },
]

// ── Deal 17건 — DealResponse (파이프라인: LEAD 4 · CONSULT 3 · QUOTE 4 · NEGO 2 · WON 3 · LOST 1)
type DealSeed = [n: number, title: string, stage: string, expected: number, custN: number, assigneeN: number, due: string, won?: number]
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
  [17, '미래상사 전시장 가구', 'LOST', 20000000, 7, 4, '2026-09-08'],
]
export const deals = dealSeeds.map(([n, title, stage, expectedAmount, custN, assigneeN, dueDate, won]) => ({
  id: deal(n),
  title,
  stage,
  expectedAmount,
  wonAmount: won ?? null, // DL-18: 성사 후 표시 금액 = 주문 합계
  customerId: customer(custN),
  customerName: customerName(customer(custN)),
  assigneeMemberId: member(assigneeN),
  assigneeMemberName: memberName(member(assigneeN)),
  dueDate,
  version: 0,
  createdAt: '2026-08-18T01:00:00Z',
}))

// ── 견적 12건 — QuoteResponse (상태별 최소 1건씩) ────────────────────────────
type QuoteSeed = [n: number, dealN: number, status: string, supply: number, valid: string, sentAt: string | null, viewedAt: string | null]
const quoteSeeds: QuoteSeed[] = [
  [1, 14, 'APPROVED', 22000000, '2026-08-31', '2026-08-18T01:00:00Z', '2026-08-19T00:30:00Z'],
  [2, 15, 'APPROVED', 14000000, '2026-09-05', '2026-08-20T02:00:00Z', '2026-08-21T01:00:00Z'],
  [3, 16, 'APPROVED', 8000000, '2026-09-10', '2026-08-22T00:00:00Z', '2026-08-23T04:00:00Z'],
  [5, 17, 'EXPIRED', 18400000, '2026-09-01', '2026-08-15T01:00:00Z', null],
  [7, 12, 'REJECTED', 26000000, '2026-09-05', '2026-08-19T01:00:00Z', '2026-08-20T00:00:00Z'],
  [8, 13, 'VIEWED', 18000000, '2026-09-12', '2026-08-21T05:00:00Z', '2026-08-24T01:00:00Z'],
  [9, 8, 'WITHDRAWN', 2900000, '2026-09-09', '2026-08-20T01:00:00Z', null],
  [10, 11, 'SENT', 1400000, '2026-09-12', '2026-08-25T06:00:00Z', null],
  [11, 9, 'SENT', 4180000, '2026-09-15', '2026-08-23T00:00:00Z', null],
  [13, 10, 'DRAFT', 4800000, '2026-09-20', null, null],
  [14, 8, 'VIEWED', 3050000, '2026-09-09', '2026-08-24T01:00:00Z', '2026-08-25T05:20:00Z'],
  [16, 12, 'SENT', 23800000, '2026-09-09', '2026-08-26T02:00:00Z', null],
]
export const quotes = quoteSeeds.map(([n, dealN, status, supply, validUntil, sentAt, firstViewedAt]) => ({
  id: quote(n),
  quoteNo: `Q-2608-${String(n).padStart(3, '0')}`,
  dealId: deal(dealN),
  status,
  totalAmount: supply + supply / 10,
  validUntil,
  sentAt,
  firstViewedAt,
  version: 0,
}))

/** 견적 상세용 부가 정보 — QuoteDetailResponse 조립 시 quotes와 병합해 사용 */
export const quoteExtras: Record<string, { vatMode: string; terms: string | null; clonedFromQuoteId: string | null; supersededByQuoteId: string | null; rejectReason: string | null; responderName: string | null; responderTitle: string | null }> = {
  [quote(1)]: { vatMode: 'EXCLUDED', terms: '납품은 지점별 순차 진행됩니다.', clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: '임재현', responderTitle: '관리팀장' },
  [quote(2)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: '윤소라', responderTitle: '총무 대리' },
  [quote(3)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: '강민철', responderTitle: '구매과장' },
  [quote(5)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
  [quote(7)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: quote(16), rejectReason: '예산 초과', responderName: '서동윤', responderTitle: '시설담당' },
  [quote(8)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
  [quote(9)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: quote(14), rejectReason: null, responderName: null, responderTitle: null },
  [quote(10)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
  [quote(11)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
  [quote(13)]: { vatMode: 'EXCLUDED', terms: null, clonedFromQuoteId: null, supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
  [quote(14)]: { vatMode: 'EXCLUDED', terms: '설치는 납품일로부터 3일 이내 진행됩니다.', clonedFromQuoteId: quote(9), supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
  [quote(16)]: { vatMode: 'EXCLUDED', terms: '단가 재조정안입니다. 검토 부탁드립니다.', clonedFromQuoteId: quote(7), supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null },
}

/** 견적 항목 — QuoteDetailResponse.ItemResponse[] (quoteId 키) */
type Item = { productId: string | null; name: string; unit: string; quantity: number; unitPrice: number; amount: number; catalogPriceAtCreation: number | null; sortOrder: number }
const item = (productId: string | null, name: string, unit: string, quantity: number, unitPrice: number, catalogPriceAtCreation: number | null, sortOrder: number): Item => ({ productId, name, unit, quantity, unitPrice, amount: quantity * unitPrice, catalogPriceAtCreation, sortOrder })
export const quoteItems: Record<string, Item[]> = {
  [quote(1)]: [
    item(product(2), '1600 사무책상', '개', 40, 240000, 240000, 0),
    item(product(3), '메쉬 의자', '개', 60, 100000, 100000, 1),
    item(product(5), '3단 수납장', '개', 20, 120000, 120000, 2),
    item(product(6), '회의 테이블', '개', 8, 350000, 350000, 3),
    item(null, '납품·설치비', '식', 1, 1200000, null, 4),
  ],
  [quote(2)]: [item(null, '중량 선반', '개', 50, 260000, null, 0), item(null, '운반·설치', '식', 1, 1000000, null, 1)],
  [quote(3)]: [item(product(3), '메쉬 의자', '개', 80, 100000, 100000, 0)],
  [quote(5)]: [item(product(4), '패브릭 소파', '개', 20, 450000, 450000, 0), item(product(6), '회의 테이블', '개', 20, 350000, 350000, 1), item(product(5), '3단 수납장', '개', 20, 120000, 120000, 2)],
  [quote(7)]: [item(product(7), '파티션(1200)', '개', 100, 90000, 90000, 0), item(product(2), '1600 사무책상', '개', 50, 240000, 240000, 1), item(product(3), '메쉬 의자', '개', 50, 100000, 100000, 2)],
  [quote(8)]: [item(product(4), '패브릭 소파', '개', 20, 450000, 450000, 0), item(product(6), '회의 테이블', '개', 20, 350000, 350000, 1), item(null, '라운지 테이블', '개', 10, 200000, null, 2)],
  [quote(9)]: [item(product(1), '1200 사무책상', '개', 10, 180000, 180000, 0), item(product(3), '메쉬 의자', '개', 10, 110000, 100000, 1)],
  [quote(10)]: [item(product(6), '회의 테이블', '개', 4, 350000, 350000, 0)],
  [quote(11)]: [item(product(6), '회의 테이블', '개', 8, 350000, 350000, 0), item(product(3), '메쉬 의자', '개', 12, 95000, 100000, 1), item(null, '운반비', '식', 1, 240000, null, 2)],
  [quote(13)]: [item(product(1), '1200 사무책상', '개', 20, 180000, 180000, 0), item(product(5), '3단 수납장', '개', 10, 120000, 120000, 1)],
  // S-01 3막 그대로 — 카탈로그 2건(의자는 5% 조정) + 직접 입력
  [quote(14)]: [item(product(1), '1200 사무책상', '개', 10, 180000, 180000, 0), item(product(3), '메쉬 의자', '개', 10, 95000, 100000, 1), item(null, '설치·배송비', '식', 1, 300000, null, 2)],
  [quote(16)]: [item(product(7), '파티션(1200)', '개', 100, 85000, 90000, 0), item(product(2), '1600 사무책상', '개', 50, 230000, 240000, 1), item(product(3), '메쉬 의자', '개', 50, 76000, 100000, 2)],
}

// ── 고객 열람 데모 — raw 토큰 → 견적 매핑 (/q/:token). PublicQuoteResponse는 핸들러가 조립
export const viewTokens: Record<string, { quoteId: string; respondable: boolean }> = {
  'demo-dodam-14': { quoteId: quote(14), respondable: true }, // 메인 시나리오 — 이수정이 여는 링크
  'demo-hanul-16': { quoteId: quote(16), respondable: true },
  'demo-shinyoung-01': { quoteId: quote(1), respondable: false }, // 응답 완료 — 열람만 가능 (AP-11)
  'demo-mirae-05': { quoteId: quote(5), respondable: false }, // 만료 — 410 처리 데모
}

// ── 초대 1건 — 시드 invitation과 같은 값 (/invite/:token) ────────────────────
/** raw 토큰 → 초대. 시드는 해시(seed-invite-hash-0001)만 갖고 원문은 메일에만 있다 */
export const invitations: Record<string, { companyName: string; email: string; role: string; expiresAt: string }> = {
  'demo-invite': {
    companyName: COMPANY.name,
    email: 'newbie@hanbit.co.kr',
    role: 'SALES_REP',
    expiresAt: '2026-09-02T14:59:59Z', // 시드 2026-09-02 23:59:59+09
  },
}

// ── 주문 3건 — OrderResponse (이달 성사 합계 48,400,000) ─────────────────────
export const orders = [
  { id: order(1), orderNo: 'O-2608-001', quoteId: quote(1), quoteNo: 'Q-2608-001', dealId: deal(14), dealTitle: '신영건설 지점 집기', customerId: customer(4), customerName: '신영건설', supplyAmount: 22000000, vatAmount: 2200000, totalAmount: 24200000, startDate: '2026-09-01', deliveryDate: '2026-09-12', createdAt: '2026-08-20T07:00:00Z' },
  { id: order(2), orderNo: 'O-2608-002', quoteId: quote(2), quoteNo: 'Q-2608-002', dealId: deal(15), dealTitle: '대한물산 창고 선반', customerId: customer(3), customerName: '대한물산', supplyAmount: 14000000, vatAmount: 1400000, totalAmount: 15400000, startDate: '2026-08-28', deliveryDate: '2026-09-05', createdAt: '2026-08-22T06:00:00Z' },
  { id: order(3), orderNo: 'O-2608-003', quoteId: quote(3), quoteNo: 'Q-2608-003', dealId: deal(16), dealTitle: '성원산업 사무의자 교체', customerId: customer(2), customerName: '성원산업', supplyAmount: 8000000, vatAmount: 800000, totalAmount: 8800000, startDate: '2026-09-02', deliveryDate: null, createdAt: '2026-08-25T02:00:00Z' },
]

// ── 알림 (박지훈 수신 기준) — NotificationResponse ───────────────────────────
export const notifications = [
  { id: U('9c', 1), type: 'QUOTE_VIEWED', message: '도담건설 담당자가 견적을 열람했습니다 (Q-2608-014)', refType: 'QUOTE', refId: quote(14), readAt: null, createdAt: '2026-08-25T05:20:00Z' },
  { id: U('9c', 4), type: 'INQUIRY_RECEIVED', message: '도담건설 담당자가 문의를 남겼습니다 (Q-2608-014)', refType: 'QUOTE', refId: quote(14), readAt: null, createdAt: '2026-08-25T06:00:00Z' },
  { id: U('9c', 2), type: 'QUOTE_APPROVED', message: '성원산업 담당자가 견적을 승인했습니다 (Q-2608-003)', refType: 'QUOTE', refId: quote(3), readAt: '2026-08-25T02:00:00Z', createdAt: '2026-08-25T01:30:00Z' },
]

// ── 대시보드 (회사 전체 = 김서연 기준. 영업 담당자 목은 assigneeMemberId로 필터해 재계산)
export const dashboardSummary = {
  pipeline: [
    { stage: 'LEAD', count: 4, expectedAmountSum: 24100000 },
    { stage: 'CONSULT', count: 3, expectedAmountSum: 24000000 },
    { stage: 'QUOTE', count: 4, expectedAmountSum: 25300000 },
    { stage: 'NEGOTIATION', count: 2, expectedAmountSum: 44000000 },
  ],
  monthWonAmount: 48400000,
  monthWonCount: 3,
  waitingQuotes: [
    { quoteId: quote(14), quoteNo: 'Q-2608-014', customerName: '도담건설', sentAt: '2026-08-24T01:00:00Z', firstViewedAt: '2026-08-25T05:20:00Z', validUntil: '2026-09-09' },
    { quoteId: quote(8), quoteNo: 'Q-2608-008', customerName: '도담건설', sentAt: '2026-08-21T05:00:00Z', firstViewedAt: '2026-08-24T01:00:00Z', validUntil: '2026-09-12' },
    { quoteId: quote(11), quoteNo: 'Q-2608-011', customerName: '성원산업', sentAt: '2026-08-23T00:00:00Z', firstViewedAt: null, validUntil: '2026-09-15' },
    { quoteId: quote(16), quoteNo: 'Q-2608-016', customerName: '한울에너지', sentAt: '2026-08-26T02:00:00Z', firstViewedAt: null, validUntil: '2026-09-09' },
    { quoteId: quote(10), quoteNo: 'Q-2608-010', customerName: '태성기업', sentAt: '2026-08-25T06:00:00Z', firstViewedAt: null, validUntil: '2026-09-12' },
  ],
  followUps: [
    { taskId: U('9b', 1), dealId: deal(5), dealTitle: '성원산업 비품 정기납품', content: '성원산업 재방문 일정 조율', dueDate: '2026-08-26' },
    { taskId: U('9b', 2), dealId: deal(10), dealTitle: '대한물산 사무실 확장', content: '대한물산 재검토 회신 확인', dueDate: '2026-09-01' },
  ],
  recentActivities: [
    { dealId: deal(8), dealTitle: '도담건설 사무가구 납품', summary: '고객이 견적을 열람했습니다', occurredAt: '2026-08-25T05:20:00Z' },
    { dealId: deal(8), dealTitle: '도담건설 사무가구 납품', summary: '견적을 발송했습니다 (Q-2608-014)', occurredAt: '2026-08-24T01:00:00Z' },
    { dealId: deal(12), dealTitle: '한울에너지 리모델링', summary: '견적 재협의 — 단가 조정안 요청받음', occurredAt: '2026-08-25T07:00:00Z' },
  ],
}
