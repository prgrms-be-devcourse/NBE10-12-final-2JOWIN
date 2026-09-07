# 업무 분담 — ERD · API 기준

> 🧭 [문서 지도](README.md) · ← [10 화면 설계](10-screen-design.md) · [12 프론트엔드 계획](12-frontend-plan.md) →

**문서명:** Work Breakdown by Table & Endpoint
**버전:** v2.0 (2026-08-26) · **v2.0.5** (2026-09-04 — §7.2 인터페이스 표에 `NotificationCommand`(notify·notifyForDeal) 행 추가(#75)) · **v2.0.4** (2026-09-03 — §7.2 인터페이스 표에 `MailCommand`(schedule) 행 추가(#47) · §2 `MemberQuery`에 A 내부 인터페이스 3건 추가(#40)) · **v2.0.3** (2026-08-27 — 프론트엔드 소유 확정: E가 오너, 플랫폼 + 로그인·고객 열람 담당 / 도메인 화면은 각 백엔드 담당자 · 게이트에 프론트 조건 추가 · Seed=목 픽스처. 상세는 `12-frontend-plan.md`) · **v2.0.2** (화면 설계 공백 반영 — `QuoteCommand.approve/reject`에 응답자 정보(AP-19) · `QuoteQuery` 반환에 첫 열람 시각(GAP-08)) · **검수 보정 v2.0.1** (2026-08-26 — 인터페이스 보강(§4·§5·§7.2: CU-08·12·14와 D 대시보드·배치의 데이터 통로 신설) · NT-12 수신자 규칙 참조 · WON 전이 단계 무관 정합 · AU-09 잠금 판정 문구 정정)
**상태:** 확정 — **요구사항 · 전이표 · ERD · API · DTO · 권한 매트릭스의 v1.6 계열 최신본 기준** (각 문서 변경 이력 참조)

> ⚠️ v1.0(8/21 Draft)은 구버전 스키마(tenant/app_user/quotation/quote_share 등)와 폐기된 결정(customer.owner_id 신설, Deal 단계 QUALIFIED/PROPOSAL, `/api/platform` 경로 등)을 기준으로 작성되어 있었다. 이 v2.0이 그것을 **전면 대체**한다. v1.0의 "착수 전 확정 사항" 8건은 전부 결정 완료되어 요구사항 정의서 3절(Q-15~35)에 반영됐다 — 정리 내역은 `15-cleanup-report.md` 참조.

---

## 0. 이 문서의 사용법

담당자별로 **소유 테이블**과 **소유 엔드포인트**를 지정한다. 원칙은 하나다.

> **테이블 하나에 소유자 한 명. 스키마를 바꿀 수 있는 사람은 소유자뿐이다.**

다른 사람의 테이블이 필요하면 **직접 조회하지 않고 소유자가 공개한 인터페이스를 호출**한다.
이 규칙이 지켜지면 마이그레이션 충돌과 모듈 간 결합이 동시에 해결된다.

### 담당 요약

| 담당 | 이름 | 영역 | 요구사항 | 소유 테이블 | 엔드포인트 |
| --- | --- | --- | --- | --- | --- |
| **A** | 조민석 | 인증 · 온보딩 · 구성원 · 접근범위 | AU, ON, MB, SC | 8 | API 명세서 §A |
| **B** | 한상민 | 고객사 · 카탈로그 · 활동이력 | CU, PR, AC | 6 | API 명세서 §B |
| **C** | 최선진 | Deal · 견적 · 주문 | DL, QT, OD | 5 | API 명세서 §C |
| **D** | 이준형 | 고객승인 · 알림 · 현황 | AP, NT, DB | 5 | API 명세서 §D |
| **E** | 김대연 | 인프라 · 공통 골격 · 통합 · **프론트엔드 오너** | — | 1 (공통) | — |

> **프론트엔드 소유 (v2.0.3 추가)** — E가 프론트엔드 오너를 겸한다. 단 **E가 전 화면을 만들지는 않는다**: E는 프론트 플랫폼(골격·라우팅·API 클라이언트·인증·에러·테마·MSW·리뷰)과 소유자 없는 화면(로그인·고객 열람)을 맡고, **각 도메인 화면은 그 도메인의 백엔드 담당자가** 만든다. 배분·주차·커트라인은 `12-frontend-plan.md`가 정본.

엔드포인트의 정본은 API 명세서 v1.6이다 — 이 문서에는 다시 나열하지 않는다. **표에 없는 엔드포인트는 v1에 없다.**

---

## 1. 공통 규약 (착수 전 확정, 전원)

### 1.1 공통 컬럼 (ERD v1.6 확정)

```sql
id           UUID         PRIMARY KEY   -- 전 테이블. 표시 번호(quote_no 등)는 별도 컬럼
company_id   UUID         NOT NULL      -- 집계 루트만 직접 보유. 자식은 부모 경유 격리.
                                        -- 예외: login_attempt는 없음 (테넌트 격리 예외)
created_at   TIMESTAMPTZ  NOT NULL      -- 전 테이블
updated_at   TIMESTAMPTZ  NOT NULL      -- 전 테이블
version      INT                        -- quote · deal만 (낙관적 락, JPA @Version)
deleted_at   TIMESTAMPTZ                -- 소프트 삭제 대상만 (customer · deal · activity)
```

### 1.2 마이그레이션 규약

Flyway **V1 통합 베이스라인(테이블 25개)은 E(김대연)가 ERD v1.6 그대로 1회 작성**한다 (ERD "다음 단계" 합의). 이후 스키마 변경은 소유자가 팀 합의 + ERD 버전 업을 거쳐 본인 번호 대역에 추가한다.

```
V1__baseline.sql   공통 (E 작성, ERD v1.6 = 원본)
V1xx__*.sql        A    V2xx__*.sql   B
V3xx__*.sql        C    V4xx__*.sql   D
```

### 1.3 응답 규약 (DTO 설계서 §0)

```java
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {
    public record FieldError(String field, String reason) {}
}
```

| 상황 | HTTP |
| --- | --- |
| 입력 오류 (VALIDATION_FAILED + fieldErrors) | 400 |
| 인증 실패 (LOGIN_FAILED, REFRESH_TOKEN_NOT_ACTIVE) | 401 |
| 역할 자체로 갈리는 행위 (`FORBIDDEN` — 예: 영업의 카탈로그 편집. 권한 매트릭스 ⭕/✕ 층, Q-43) | 403 |
| **리소스 조회 범위 밖 / 존재하지 않음 — 구별 금지 (SC-09, 매트릭스 🔶/▫ 층)** | **404** |
| 상태 충돌 (QUOTE_NOT_DRAFT, STALE_VERSION 등) | 409 |
| 만료된 열람 링크 | 410 |
| 규칙 위반 (EMAIL_ALREADY_MEMBER, LAST_ADMIN_PROTECTED 등) | 422 |
| 로그인 제한 (LOGIN_LOCKED) | 429 |

에러 코드·문구의 정본은 API 명세서 부록, 역할·스코프 판정의 정본은 권한 매트릭스 v1.6(구현 위치 3층 구분). ErrorCode enum PR은 E(김대연) — **403은 `FORBIDDEN` 단일 코드로 확정(Q-43)**.

**이 문서 §1이 「공통 개발 컨벤션」의 정본이다 (Q-36 확정)** — 부재하던 원본 문서 추적은 중단하고, URL·상태 코드·에러 포맷·페이징·채번 규칙은 여기와 API 명세서를 기준으로 삼는다.

목록 요청 파라미터는 **`?page=0&size=20`**(0-base · 기본 20 · 최대 100), 정렬은 엔드포인트별 기본값 고정 (Q-39).

> v1.0의 `traceId` 필드는 폐기 — v1.6 계약은 `fieldErrors`다.

### 1.4 접근 범위 (SC절)

모든 조회는 접근 컨텍스트를 인자로 받는다.

```java
record AccessContext(UUID companyId, UUID memberId, Role role, AccessScope scope)

AccessScope.COMPANY_ALL   기업 관리자 (SC-05)
AccessScope.OWNED_ONLY    영업 담당자 — deal.assignee_member_id 기준 (SC-02·04)
```

- 담당 판정 축은 **deal.assignee_member_id 하나뿐**이다. 견적·주문·상담·할 일의 범위는 전부 Deal에서 파생된다.
- **고객사·카탈로그는 회사 공유 자원** — 담당 개념이 없다 (SC-03, v1.2에서 owner 제거 확정).
- 모든 참조 ID(customerId·assigneeMemberId·transferToMemberId 등)는 같은 회사 소속인지 서비스에서 검사 — 실패는 404 (DTO 검증 노트 #3).

### 1.5 삭제 정책 (v1.6 확정)

| 방식 | 대상 |
| --- | --- |
| **상태 전이** | 회사 정지 · 구성원 비활성화 · 상품 판매 중지 · 견적 회수 · Deal 실패 · 가입 반려 · 초대 취소/만료 · 링크 만료 |
| **소프트 삭제** (`deleted_at`) | customer · deal · activity |
| **하드 삭제** | quote_item(DRAFT PUT 전체 갱신 시 교체) · customer_contact(단, CU-14·PRIMARY 제약) · 만료 토큰·읽은 알림(보존 기간 후 배치) |
| **삭제 API 없음** | orders · quote(발송 후는 회수만) · company · member · product · audit_log · email_log |

---

## 2. A — 조민석 · 인증 · 온보딩 · 구성원 · 접근범위

**소유 테이블 (8)** — 스키마 상세는 ERD v1.6이 정본.

| 테이블 | 근거 | 핵심 포인트 |
| --- | --- | --- |
| `application` | ON-01~07·14, Q-15 | 반려 이력 보존 · 번호 없음(id 식별, v1.6) |
| `platform_admin` | AU-08 | 구성원과 별도 계정 |
| `company` | ON-07~12 | ACTIVE/SUSPENDED · business_no 전역 UNIQUE |
| `member` | AU, MB-07~14 | lower(email) 전역 UNIQUE · password_hash NULL 허용(Q-33) |
| `invitation` | MB-01~06·13, Q-31 | 재발송 = EXPIRED(RESENT) + 새 행 |
| `refresh_token` | AU-02·03·10, Q-28·32 | 세션 원본, "즉시 차단"의 실체 · 관리자 세션 공용(actor_type) |
| `password_reset_token` | AU-05, Q-33·34 | purpose RESET(30분)/INITIAL_SETUP(7일) |
| `login_attempt` | AU-06·09, Q-30 | 미가입 포함 기록 · 테넌트 격리 예외 |

**엔드포인트**: API 명세서 §A 3개 섹션 (온보딩 / 인증·계정 / 구성원·초대). NT-07 수신 설정 API도 A 소유 (발송 시 설정 확인은 D 책임).

**A가 공개하는 인터페이스**

```java
public interface MemberQuery {
    MemberSummary get(UUID memberId);                    // 이름 표시용 (B·C·D)
    List<MemberSummary> findAllActive(UUID companyId);   // 담당자 선택지 (C)
    boolean isActive(UUID memberId);                     // 배정·이관 대상 검증 (C)
    List<UUID> findAdminIds(UUID companyId);             // Q-26 폴백 수신자 (D)
    Optional<AuthCredential> findCredentialByEmail(String email);  // 로그인 자격 조회 (A 내부)
    AuthCredential getCredential(UUID memberId);         // refresh 회전 시 claim 재구성 (A 내부)
    MemberContact getContact(UUID memberId);             // 담당자 연락처 표시 (D — AP-18)
}
```

**B·C·D는 `member` 테이블을 직접 조회하지 않는다.**

**A가 책임지는 공통 기반** (E와 1주차 공동 구축, 이후 유지보수 A)

```
AccessContext / 인증 필터 체인 (/api·/admin/api·/public/api 3분리)
토큰 발급·회전·폐기 공통 로직 (refresh_token 회전, REUSE_DETECTED 감지)
로그인 잠금 판정 (AU-09 — 마지막 성공 이후 연속 실패 5회, 마지막 실패로부터 10분간 차단. 10분은 잠금 지속 시간)
회사 정지·구성원 비활성화 시 refresh 일괄 폐기 훅 (ON-09 · MB-10의 실체)
```

**A 주의 사항**

- 승인(approve)은 **멱등** — company.application_id UNIQUE. 사업자번호 중복이면 COMPANY_BUSINESS_NO_DUPLICATED로 반려 유도.
- 인증 경로 전체에 SC-09 확장 적용 — 미가입·미승인·비활성·정지를 응답으로 구별하지 않는다 (LOGIN_FAILED 통일, 재설정 요청 202 고정).
- MB-14: 비활성화는 담당 Deal 이관과 한 트랜잭션 — 이관 대상 검증은 같은 회사·활성(위반 시 404).

---

## 3. B — 한상민 · 고객사 · 카탈로그 · 활동이력

**소유 테이블 (6)**

| 테이블 | 근거 | 핵심 포인트 |
| --- | --- | --- |
| `customer` | CU-01~08 | **owner 없음** — created_by_member_id는 기록용 (SC-03) · 소프트 삭제 |
| `customer_contact` | CU-09~11·14 | 대표 1명 부분 유니크 · 발송 이력 있으면 삭제 불가(CONTACT_HAS_QUOTES) |
| `product` | PR-01~10 | UNIQUE(company_id, name) 판매 중지 포함 |
| `activity` | AC-01~08·10 | 수동 상담 기록 — author_member_id는 수정·삭제 판정용, 조회 경로 아님 |
| `task` | AC-09, Q-29 | **배정 컬럼 없음** — deal의 순수 자식, "내 할 일" = 담당 Deal의 미완료 할 일 |
| `audit_log` | AC-07·11 | 자동 이벤트 — B의 리스너가 적재, payload 규약(변경 필드만 before/after) |

**엔드포인트**: API 명세서 §B 3개 섹션 (고객사 / 상품 / 활동·감사).

**B가 공개하는 인터페이스**

```java
public interface CustomerQuery {
    CustomerSummary get(AccessContext ctx, UUID customerId);          // C Deal 생성 검증·표시
    boolean existsContactInCustomer(UUID customerId, UUID contactId); // C의 CONTACT_NOT_IN_CUSTOMER 검증
    ContactSummary getContact(UUID contactId);                        // D 발송 수신자 정보
}

public interface ProductQuery {
    ProductSnapshot get(AccessContext ctx, UUID productId);  // 이름·단위·현재 단가 → QT-24 스냅샷 원천
    boolean isSellable(AccessContext ctx, UUID productId);   // PR-06
}
```

**C는 견적 작성 시 `ProductQuery.get()`으로 카탈로그 값을 가져와 quote_item에 값 복사한다** (단가·품목명·단위, C 2차 2-2). `product` 테이블을 직접 조인하지 않는다.

**B가 구독하는 이벤트 (AC-07 자동 기록 — audit_log 적재)**

```java
@ApplicationModuleListener void on(DealStageChanged e)   // C 발행 (advance·revert·lose·reopen·자동 승급·자동 성사)
@ApplicationModuleListener void on(QuoteSent e)          // C 발행
@ApplicationModuleListener void on(QuoteViewed e)        // C 발행 (D가 quote.markViewed() 호출 → C 도메인이 발행)
@ApplicationModuleListener void on(QuoteApproved e)      // C 발행 (경계 합의 — 상태 변경 주체가 C이므로 발행도 C)
@ApplicationModuleListener void on(QuoteRejected e)      // C 발행
@ApplicationModuleListener void on(OrderCreated e)       // C 발행
@ApplicationModuleListener void on(MemberDeactivated e)  // A 발행 (인증 6종 이벤트)
```

**B가 이벤트 페이로드 포맷을 정의해 먼저 공유한다** (API 경계 합의). 페이로드에 `companyId` 필수, 비밀번호·토큰·해시 금지.

**B 주의 사항**

- CU-08 "진행 중 Deal": WON·LOST 아닌 Deal이 하나라도 있으면 삭제 불가 — 판정은 `DealQuery.hasOpenDeals(customerId)` 경유(deal 직접 조회 금지). CU-12 딜 이력은 `DealQuery.summariesByCustomer()`, CU-14 발송 이력 판정은 D의 `ViewTokenQuery.existsForContact()` 경유 (v2.0.1).
- PR-07·08은 C의 quote_item 값 복사로 보장 — B는 product 행을 이력 없이 덮어써도 견적에 영향 없음이 설계로 보장되지만, 이름 변경 시 중복 검사는 필수.
- AC-04·05는 본인 작성분만 — `author_member_id = ctx.memberId()` 조건 필수, 위반은 404 ACTIVITY_NOT_AUTHOR.

---

## 4. C — 최선진 · Deal · 견적 · 주문

**소유 테이블 (5)**

| 테이블 | 근거 | 핵심 포인트 |
| --- | --- | --- |
| `deal` | DL-01~18 | 6단계 고정(LEAD/CONSULT/QUOTE/NEGOTIATION/WON/LOST) · assignee_member_id가 SC-02 축 · lost_from_stage(재개용) · version |
| `quote` | QT, Q-25 | 7상태 · 금액 3분리 서버 계산 · valid_until = 링크 만료(Q-17) · version |
| `quote_item` | QT-02~08·24 | 작성 시점 단가·품목명·단위 값 복사 |
| `orders` | OD-01~10 | 상태 없음 · UNIQUE(quote_id) 1견적 1주문 · deal_id 컬럼 없음(quote 경유) |
| `order_item` | OD-04 | FK 없는 값 복사 스냅샷 |

- `document_sequence`(공통)로 quote_no·order_no 채번 — `FOR UPDATE` 행 락, UNIQUE(company_id, no)가 최종 방어.

**엔드포인트**: API 명세서 §C 3개 섹션 (Deal / 견적 / 주문). AP-13·14(재발송·수동 만료)의 구성원용 엔드포인트도 C 소유(내부에서 D의 ViewTokenCommand 호출).

**C가 공개하는 인터페이스 (경계 합의의 실체)**

```java
public interface QuoteCommand {          // D가 호출 — D는 quote 상태를 직접 바꾸지 않는다
    void markViewed(UUID quoteId);       // GET /public/quotes/{token} 첫 열람
    void approve(UUID quoteId, Responder responder);            // AP-08·19 — 토큰 소진과 한 트랜잭션 (D 주관)
    void reject(UUID quoteId, String reason, Responder responder);

    record Responder(String name, String title) {}              // v2.0.2 — 자기 신고 신원(Q-44), title은 null 허용
}

public interface DealQuery {
    UUID assigneeIdOf(UUID dealId);              // D 알림 수신자 결정(Q-26) · B 접근 판정
    boolean isOpen(UUID dealId);                 // 진행 중(리드~협상) 여부
    boolean hasOpenDeals(UUID customerId);       // B의 CU-08 판정 — 고객사 삭제 차단 (v2.0.1 보강)
    List<DealSummary> summariesByCustomer(UUID customerId);  // B의 CU-12 — 고객사 상세 Deal 이력 (v2.0.1 보강)
}

public interface QuoteQuery {                    // D 소비 — 배치·대시보드의 견적 후보 조회 (v2.0.1 보강)
    List<QuoteSummary> findAwaitingResponse(UUID companyId);  // SENT·VIEWED — NT-05 리마인드 · DB-03 응답 대기
                                                              // QuoteSummary: 견적번호·고객사·발송 시각·**첫 열람 시각(null=미열람)**·유효기간 (v2.0.2, GAP-08)
    List<QuoteSummary> findExpiringUntil(LocalDate date);     // NT-06 임박 알림 후보 (valid_until 기준)
}

public interface SalesStatsQuery {               // D 소비 — 대시보드 집계 (DB-01~08, v2.0.1 보강. SC 범위는 ctx로 적용)
    List<StageCount> pipeline(AccessContext ctx);                                      // DB-01
    WonStats monthlyWon(AccessContext ctx, YearMonth month);                           // DB-02 — 주문 합계 (DL-18)
    List<MemberPerformance> performance(UUID companyId, LocalDate from, LocalDate to); // DB-06·08
    List<StageConversion> conversions(UUID companyId, LocalDate from, LocalDate to);   // DB-07
}
```

**B·D는 deal·quote·orders 테이블을 직접 조회하지 않는다** — 대시보드·배치 집계도 위 인터페이스 경유가 원칙이다. 집계 성능 문제가 확인되면 읽기 전용 뷰 허용 여부를 팀 합의로 결정한다.

**C가 발행하는 이벤트**: DealStageChanged · QuoteSent · QuoteViewed · QuoteApproved · QuoteRejected · OrderCreated (B §3 참조). **QuoteSent에 토큰이나 URL을 넣지 않는다** — 링크 토큰은 D가 발급 시점에 생성한다.

**C가 구현해야 하는 핵심 규칙**

| 규칙 | 요구사항 | 구현 |
| --- | --- | --- |
| 금액은 서버가 계산 | QT-08·22 | Request에 supply/vat/total 필드 자체가 없음 (DTO 검증 노트 #1) |
| 카탈로그 스냅샷 | QT-24, PR-04 | `ProductQuery.get()` → quote_item에 단가·품목명·단위 값 복사 |
| 판매 중지 차단 | PR-06 | `ProductQuery.isSellable()` — 위반 409 PRODUCT_DISCONTINUED |
| 발송 후 불변 | QT-14·16 | status≠DRAFT면 409 QUOTE_NOT_DRAFT |
| 발송 시 자동 승급 | Q-25 | 단계 < QUOTE면 QUOTE로 (시스템 전이) — 응답에 갱신 단계 포함 |
| 종결 Deal 차단 | Q-25 | 작성·발송·복제 시 409 QUOTE_DEAL_CLOSED |
| 수신인 검증 | CONTACT_NOT_IN_CUSTOMER | `CustomerQuery.existsContactInCustomer()` — 복합 FK 불가 영역, 서비스 검증이 유일 방어 |
| 승인 없이 성사 불가 | DL-09 | WON 전이는 convert-to-order 경로만 — **단계 무관(진행 중이면 어디서든 WON**, 전이표 §5 v1.6.1) |
| **1견적 1주문 · 멱등 성사** | OD-03, Q-25 | `FOR UPDATE` + UNIQUE(quote_id). 이미 WON이면 no-op(DEAL_ALREADY_WON 아님) |
| Deal 실패 부수효과 | DL-10 | 진행 중 견적 EXPIRED + **D의 `ViewTokenCommand.expire(quoteId, DEAL_LOST)` 호출** (§5 인터페이스 시그니처와 일치 — v2.0.1 표기 정정) |
| **견적 만료 전이 배치** | Q-17, Q-37 | **C 소유** — valid_until 경과 견적을 EXPIRED로 전이 + `ViewTokenCommand.expire(TIME)` 호출. 회사 정지 중에도 **전이는 계속** 돈다(알림만 중단, Q-27) |
| **열람 링크 발급 시점** | Q-40 | 발송 트랜잭션 안에서 `ViewTokenCommand.issue()` **동기 호출** — 링크 없는 SENT 견적이 존재하지 않도록. 메일 발송만 커밋 후 비동기 |
| 낙관적 락 | 검증 노트 #4 | 수정·전이 Request에 version, 불일치 409 STALE_VERSION |

**주문 생성 트랜잭션**

```
BEGIN
  SELECT ... FROM quote WHERE id = ? FOR UPDATE
  status = APPROVED 검증                         -- OD-02 (아니면 QUOTE_NOT_APPROVED)
  orders INSERT (금액·항목 값 복사)               -- OD-04, 중복이면 UNIQUE 위반 → QUOTE_ALREADY_CONVERTED
  deal.stage = WON (이미 WON이면 유지 — 멱등)      -- OD-06, Q-25
  OrderCreated 이벤트 발행                        -- AC-07 · NT
COMMIT
```

**C의 검증 테스트** — 동시성 테스트는 1주차에 선작성한다 (구현 없이 스켈레톤 가능, 대표 Evidence).

```
동일 견적 100건 동시 주문 전환 → 1건만 성공 (UNIQUE(quote_id))
부가세 별도/포함 합계 검증 · 1원·999원·홀수 금액 반올림
동시 수정 → STALE_VERSION
```

---

## 5. D — 이준형 · 고객승인 · 알림 · 현황

**소유 테이블 (5)**

| 테이블 | 근거 | 핵심 포인트 |
| --- | --- | --- |
| `quote_view_token` | AP-02~14 | 견적당 활성 1개 부분 유니크 · token_hash만 저장 · expired_reason 5종(TIME/MANUAL/WITHDRAWN/RESENT/DEAL_LOST) |
| `customer_inquiry` | AP-15, Q-20 | 기록만 — 답변 스레드 없음 |
| `notification` | NT-03~05·08·10·12 | 인앱 — 복합 FK(company_id, recipient_member_id)로 교차 테넌트 유출 차단 |
| `email_log` | NT-01~06·10·13 | UNIQUE(template_type, ref_id, recipient_email) — 배치 재실행 이중 발송 차단 |
| `notification_setting` | NT-07 | 메일 채널만 · 행 없으면 기본 ON (설정 API는 A, 발송 시 확인은 D) |

**엔드포인트**: API 명세서 §D 3개 섹션 (고객 열람·승인 / 알림 / 대시보드).

**D가 공개하는 인터페이스**

```java
public interface ViewTokenCommand {      // C가 호출 (경계 합의의 역방향)
    void issue(UUID quoteId, UUID recipientContactId);        // 발송·재발송 시 발급 (기존 활성 링크 EXPIRED(RESENT))
    void expire(UUID quoteId, String reason);                 // WITHDRAWN · MANUAL · DEAL_LOST
}

public interface ViewTokenQuery {        // B가 호출 — CU-14 판정 (v2.0.1 보강)
    boolean existsForContact(UUID contactId);   // 발송 이력(수신인 지정 이력) 존재 — true면 CONTACT_HAS_QUOTES로 삭제 차단
}
```

**D가 지키는 경계**: 견적 상태는 **C의 QuoteCommand(markViewed/approve/reject)만 호출**해 바꾼다. 승인 처리는 토큰 소진(AP-11)과 quote.approve()가 **한 트랜잭션** — 이벤트 분리 방식은 트랜잭션이 갈라지므로 쓰지 않는다.

**승인 처리 트랜잭션 (AP-08·11)**

```
BEGIN
  quote_view_token 유효성 검증 (만료 → 410 / 이미 응답 → 409 / 회사 정지 → 409 COMPANY_SUSPENDED)
  QuoteCommand.approve(quoteId, responder)   -- C 도메인 메서드 (내부에서 QuoteApproved 발행)
                                             -- responder = 요청 body의 이름·직책 (AP-19, 검증 없는 자기 신고)
  quote_view_token.status = RESPONDED        -- AP-11 재응답 차단
COMMIT
```

**D가 구현해야 하는 핵심 규칙**

| 규칙 | 요구사항 |
| --- | --- |
| 토큰이 곧 인증 — 실패 시 존재 비노출 (형식 오류 포함 404) | SC-07~09 |
| 링크 발급: raw 토큰은 메일 렌더링 시점 메모리에만 — DB엔 해시만 | ERD |
| 열람 페이지 담당자 = Deal의 **현재** 담당자 동적 조회 (스냅샷 금지) | AP-18, 검증 노트 #5 |
| 알림 수신자 = 발송 시점 유효 담당자, 비활성이면 기업 관리자 폴백 (`MemberQuery.findAdminIds`) | Q-26 |
| 회사 정지 중: 열람 허용·응답 차단·배치 알림 중단 (만료 **전이** 배치는 계속) | SC-10, Q-27 |
| 메일 실패: 재시도 1회 → 실패 시 인앱 EMAIL_FAILED (NT-07로 못 끔) — **수신자는 실패 메일별 규칙(요구사항 §2.13 NT-12 수신자 표). NT-13 실패는 인앱 수신자 없음 → email_log FAILED 지표로 감지** | NT-12, Q-35 |
| NT-05·06 배치: email_log UNIQUE가 이중 발송 차단 — 수신자 변경 시 키가 달라져 새 담당자에게 정상 발송 | NT-05·06 |
| 대시보드 집계는 SC절 범위를 따름 — 영업은 본인 담당 Deal 기준 · **집계·후보 조회는 C의 `SalesStatsQuery`·`QuoteQuery` 경유(deal·quote·orders 직접 조회 금지, v2.0.1)** | DB-01~05, SC-02 |

---

## 6. E — 김대연 · 인프라 · 공통 골격 · 통합

- **Flyway V1 베이스라인** — ERD v1.6의 25개 테이블·제약·인덱스 그대로 (ERD "다음 단계" 2번).
- **공통 스켈레톤** — ErrorResponse·PageResponse(이미 존재), **ErrorCode enum PR**(API 부록의 코드·HTTP·문구 원본).
- `document_sequence` 채번 공통 로직 (FOR UPDATE → +1 → 번호 조립, 행 없으면 INSERT).
- 로컬 환경 + Seed → CI/CD → 배포 → 모니터링 → 성능 측정.
- 확정 문서의 깃 `docs/` 반영 유지보수.
- **프론트엔드 플랫폼** (v2.0.3) — Vite·React·Radix Themes 골격, 라우팅 3분리, API 클라이언트(**토큰 재발급 큐잉** 포함), 에러 문구 매핑, 공통 컴포넌트, **MSW 목 서버**, 코드 리뷰·통합. 화면은 로그인·고객 열람 5종만 직접 담당.
- **Seed = 목 픽스처** — 시연 데이터·개발 데이터·목 데이터를 한 세트로 만든다(`12-frontend-plan.md` §5.2). 플랫폼 관리자 계정 시드 포함.

---

## 7. 담당자 간 경계 (요약)

가장 사고가 잦은 지점이다. 구현 착수 전에 인터페이스 시그니처를 확정한다.

### 7.1 소유권 이동 지점 (견적 라이프사이클)

```
C: 견적 발송 (quote.status = SENT)
        │  QuoteSent 이벤트 (토큰 없음)
D: 열람 링크 발급 + 안내 메일           ← C가 발송 트랜잭션 안에서 ViewTokenCommand.issue 동기 호출 (Q-40 확정)
                                          메일 발송만 커밋 후 비동기 (email_log SCHEDULED → SENT/FAILED)
        │  고객 열람·승인·반려
D → C: QuoteCommand.markViewed / approve / reject   ← 상태 변경 주체는 항상 C
        │  QuoteApproved 이벤트
C: 주문 전환 (구성원 실행) → Deal WON
        │
C → D: Deal 실패(lose) 시 ViewTokenCommand.expire(DEAL_LOST)   ← 역방향 경계 (대칭 2건)
```

### 7.2 인터페이스 목록

| 제공자 | 인터페이스 | 소비자 |
| --- | --- | --- |
| A | `MemberQuery` | B, C, D |
| B | `CustomerQuery` (contact 검증 포함) | C, D |
| B | `ProductQuery` | C |
| C | `QuoteCommand` (markViewed·approve·reject) | D |
| C | `DealQuery` (assigneeIdOf·isOpen·hasOpenDeals·summariesByCustomer) | B, D |
| C | `QuoteQuery` (응답 대기·임박 후보 — NT-05·06, DB-03) (v2.0.1) | D |
| C | `SalesStatsQuery` (대시보드 집계 — DB-01~08) (v2.0.1) | D |
| D | `ViewTokenCommand` (issue·expire) | C |
| D | `ViewTokenQuery` (existsForContact — CU-14) (v2.0.1) | B |
| D | `MailCommand` (schedule — 메일 예약 통로; NT-02 견적 발송·NT-14 재설정 안내) | A, D |
| D | `NotificationCommand` (notify·notifyForDeal — 인앱 알림 쓰기 통로; NT-03·05·08·10·12) | D |

### 7.3 규칙

```
다른 사람의 테이블을 JPA 연관관계로 매핑하지 않는다
→ ID(UUID)로만 참조하고, 필요한 정보는 인터페이스로 조회

다른 사람의 Repository를 직접 주입받지 않는다
→ 모듈 경계 검증(Spring Modulith Verification 등)을 빌드에 포함

이벤트 페이로드 포맷은 B가 정의해 먼저 공유 (AC-07) — companyId 필수, 비밀·토큰·해시 금지
견적·주문 이벤트(QuoteSent 등)의 페이로드에는 dealId 필수 — AC-06 Deal 타임라인 병합 키 (v2.0.1)
```

---

## 8. 주차별 산출물

> 아래 표는 **백엔드 기준**이다. 프론트 주차(A~D의 화면 작업 포함)·커트라인은 **12-frontend-plan.md §4·§7이 정본** — 게이트 표에는 프론트 조건을 병기했다.

| | 1주차 | 2주차 | 3주차 | 4주차 |
| --- | --- | --- | --- | --- |
| **A** | 공통기반 리드 + 로그인·refresh 회전 | 온보딩 + 구성원·초대 + 접근범위 | 재설정·INITIAL_SETUP + 플랫폼 관리자 + 감사 이벤트 | 보안·SC-09 테스트 |
| **B** | 고객사 CRUD | 담당자·카탈로그 | 활동이력·할 일 + 이벤트 리스너(audit) | 통합 |
| **C** | 모델 + **동시성 테스트 선작성** | Deal + 견적 작성·발송 | **주문 전환 + 멱등·스냅샷 + DEAL_LOST 연동** | 인덱스·성능 |
| **D** | 메일 발송 기반 + email_log | 열람 링크 발급·열람 | 승인·반려 + 알림(인앱·NT-05/06 배치) | 대시보드 |
| **E** | Flyway V1 + Seed + ErrorCode | CI/CD | 배포 + 모니터링 | 성능 측정·장애 시연 |

### 게이트

| 시점 | 조건 | 미달 시 |
| --- | --- | --- |
| 1주 말 | AccessContext + 필터 체인 + V1 마이그레이션 동작 · **프론트 공통 기반 + MSW로 화면 1개 표시** | 범위 재조정 |
| **2주 금** | 로그인 → 고객사 → Deal → 견적 작성 관통 · **같은 동선이 화면으로 관통(목 허용)** | 활동이력 후순위로 · **커트라인 밖 화면 공식 포기** |
| **3주 금** | 발송 → 열람 → 승인 → 주문 전환 E2E 관통 + 배포 성공 · **실 API로 관통 + 커트라인 10화면 완성** | 대시보드 후순위로 |
| 4주 중 | 검증 테스트 통과 · 문서-구현 정합 확인 | 시연 축소 |

---

## 9. 남은 실행 항목 (착수 전 이견 없으면 진행)

v1.0의 "착수 전 확정 사항" 8건은 전부 결정 완료(Q-15~35). 현재 남은 것은 실행 항목뿐이다.

| # | 항목 | 담당 |
| --- | --- | --- |
| 1 | Flyway V1 + JPA 엔티티 25개 PR | E 김대연 |
| 2 | ErrorCode enum PR (API 부록 원본) | E 김대연 |
| 3 | 자기 도메인 API·DTO 섹션 최종 검토 | A·B·C·D |
| 4 | 이벤트 페이로드 포맷 정의·공유 (AC-07) | B 한상민 |
| 5 | ✅ C 2차 잔여 결정 완료 — 만료 배치 **C 소유**(Q-37) · version 응답 일관(Q-38) · 페이지네이션 표준(Q-39). 남은 것은 구현 반영 | C·E |
| 6 | 인터페이스 시그니처(§7.2) 확정 | 전원 |
| 7 | ✅ 403 코드·문구 확정 — `FORBIDDEN`(Q-43). ErrorCode enum PR에 포함 | E 김대연 |
| 8 | 기술 스택 §3의 잠정 기본값 8건을 팀 환경 확인 후 확정 (특히 배포 형태·오리진/쿠키·메일 서비스) | 전원 (인프라 E) |
