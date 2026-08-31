# DTO 설계서 — v1.6.4

> 🧭 [문서 지도](README.md) · ← [07 API 명세서](07-api-spec.md) · [09 권한 매트릭스](09-permissions-matrix.md) →

💡 API 명세서 v1.6의 요청·응답 필드 정의. **Java record 시그니처가 곧 명세**라 그대로 복사해 구현에 쓰면 된다.
💡 각 도메인 담당자가 자기 패키지(`domain/{도메인}/dto`)에 생성한다. 공통 규칙은 아래 §0.

## 변경 이력

| 버전 | 변경 |
| --- | --- |
| v1.6.4 | **refresh 전달 = 쿠키 확정(2026-08-27)** — `LoginResponse`·`RefreshTokenResponse`에서 **refreshToken 필드 제거**, **`RefreshTokenRequest` 폐기**(요청 바디 없음 — 쿠키가 자격 증명). 검증 노트 #8 추가 |
| v1.6.3 | **화면 설계 공백 반영(2026-08-26)** — **`ApproveQuoteRequest` 신설** · `RejectQuoteRequest`에 응답자 필드 추가(AP-19, Q-44) · `QuoteDetailResponse`에 응답자 표시 필드 · **`WaitingQuote`에 `firstViewedAt`**(대시보드에서 미열람/열람 구분 — `10-screen-design.md` GAP-08) |
| v1.6.2 | **Q-38·39·43 반영(2026-08-26)** — SendQuoteResponse에 **version 추가**(Q-38 응답 일관) · §0 페이징 규칙에 파라미터 표준 명시(Q-39) · 403 `FORBIDDEN` 확정 반영(Q-43) |
| v1.6.1 | 검수 보정(2026-08-26) — **ChangeAssigneeRequest에 version 추가**(§0 "deal 수정·전이 Request에 version" 규칙 일관, STALE_VERSION 적용) · MEMBER_INACTIVE_TRANSFER_REQUIRED 400→**422** 반영 · StageCount 주석에 진행 단계 기준 명시(DL-18 정합 — WON 금액은 monthWonAmount) |
| v1.6 | **API 명세서 전 엔드포인트 ↔ DTO 전수 대조 결과 보강** — 누락 4건 추가: `UpdateContactRequest`(PATCH contacts/{cid}) · `UpdateActivityRequest`(PATCH activities/{id}) · `UpdateNotificationSettingsRequest`(PUT /me/notification-settings) · `ResendViewTokenRequest`(POST view-token/resend). 주석 보강 3건: StageMoveRequest에 reopen 포함 명시 · preview는 PublicQuoteResponse 재사용 명시 · CompanyResponse에 ON-12 이용 현황(memberCount) 추가. 전부 API 명세서에 이미 있던 엔드포인트로 DTO만 빠져 있었음 — 명세서 쪽 변경 없음. ApplicationResponse에서 applicationNo 제거(채번 APPLICATION 제외) |
| v1.5 | 인증 record(로그인·재발급·재설정 — purpose 2종) · 감사 상세(AuditLogDetailResponse, payload 규약) · TaskResponse 배정 필드 제거(Q-29) · 알림 type에 INQUIRY_RECEIVED·EMAIL_FAILED · 낙관적 락 에러명 STALE_VERSION |

## 0. 공통 규칙

| 규칙 | 내용 |
| --- | --- |
| 형태 | 전부 `record` — Request와 Response는 반드시 분리, 엔티티를 API에 직접 노출 금지 |
| 네이밍 | `{행위}{도메인}Request` / `{도메인}{뷰}Response` (예: CreateDealRequest, DealDetailResponse) |
| ID | `UUID` |
| 금액 | `Long` — 원 단위 정수 (Q-12), 음수 불가 |
| 날짜·시각 | `LocalDate` / `Instant` — JSON은 ISO-8601 |
| enum | **상태 전이표 v1.6**의 영문 코드 문자열 그대로 (예: `"SENT"`, `"WON"`) |
| 검증 | Request에 Bean Validation 어노테이션 — 400 응답의 `fieldErrors`로 변환 |
| 페이징 | 목록은 공통 `PageResponse<T>` 사용 · 요청은 **`?page=0&size=20`** (0-base · 기본 20 · 최대 100) · 정렬은 엔드포인트별 기본값 고정 (Q-39) |
| 낙관적 락 | quote·deal의 수정·전이 Request에 `version` 포함, Response에 항상 `version` 반환 |

**공통 응답 record (이미 스켈레톤에 있음)**

```java
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {
    public record FieldError(String field, String reason) {}
}

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
```

---

## A. 조민석 — 온보딩 · 인증 · 구성원

```java
// ── 온보딩 (public / admin)
public record CreateApplicationRequest(
        @NotBlank String companyName,
        @NotBlank String businessNo,        // 사업자등록번호 — 승인 시 전역 중복 검사
        @NotBlank @Email String email) {}

public record ApplicationResponse(
        // applicationNo 제거(v1.6) — 신청은 번호 미사용, id로 식별 (A 합의: 채번 APPLICATION 제외)
        UUID id, String companyName, String businessNo,
        String email, String status,        // PENDING / APPROVED / REJECTED
        String rejectReason, Instant decidedAt, Instant createdAt) {}

public record RejectApplicationRequest(@NotBlank String reason) {}   // ON-14

public record SuspendCompanyRequest(@NotBlank String reason) {}      // ON-08

public record CompanyResponse(
        UUID id, String name, String businessNo, String status,      // ACTIVE / SUSPENDED
        String suspendReason,
        int memberCount,                 // v1.6 보강 — ON-12 "이용 현황"의 v1 범위(구성원 수). 확장(딜·견적 수 등)은 합의 후
        Instant createdAt) {}

// ── 인증
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        boolean rememberMe) {}                                        // AU-10

public record LoginResponse(
        String accessToken,                  // 15분 (Q-32) — Authorization: Bearer 로 사용
        UUID memberId, String name, String role, String companyName) {}
// v1.6.4: refreshToken 필드 없음 — HttpOnly 쿠키(2jo_rt)로만 전달한다.
// 바디에 실으면 HttpOnly가 무의미해지므로 절대 되살리지 않는다.

// RefreshTokenRequest 폐기(v1.6.4) — 재발급 요청에 바디가 없다. 쿠키가 곧 자격 증명.
// 회전: 기존 행 REVOKED(ROTATED) + 새 행 + 새 쿠키로 교체.
// 폐기·만료 토큰이면 401 REFRESH_TOKEN_NOT_ACTIVE (쿠키도 함께 삭제)

public record RefreshTokenResponse(String accessToken) {}
// 플랫폼 관리자(/admin/api/v1/auth/*)도 위 record를 그대로 재사용 —
// LoginResponse의 companyName은 null, refresh_token 행은 actor_type=PLATFORM_ADMIN,
// 쿠키는 2jo_admin_rt (Path /admin/api/v1/auth)로 분리해 구성원 세션과 공존한다

public record RequestPasswordResetRequest(@NotBlank @Email String email) {}
// 미가입 이메일도 응답 동일 — 202 Accepted 고정 (SC-09 인증 확장)
// 이 요청은 purpose=RESET(30분) 전용. INITIAL_SETUP(7일)은 가입 승인 시 시스템이 발급 (Q-33·34)

public record ExecutePasswordResetRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String newPassword) {}
// RESET·INITIAL_SETUP 공용 — 토큰의 purpose로 구분, 요청 형태는 동일 (Q-33)
// 완료 시 해당 구성원 refresh_token 전 행 폐기. INITIAL_SETUP 완료 = 첫 비밀번호 설정(password_hash NULL 해소)

public record MeResponse(
        UUID memberId, String name, String email, String phone,
        String role, UUID companyId, String companyName) {}

public record UpdateMeRequest(@NotBlank String name, String phone) {}

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword) {}

public record NotificationSettingResponse(List<Entry> settings) {    // NT-07: 메일 채널만
    public record Entry(String type, boolean emailEnabled) {}         // 행 없으면 기본 ON
}

public record UpdateNotificationSettingsRequest(                      // v1.6 보강 — PUT /me/notification-settings
        @NotEmpty List<Entry> settings) {                             // 전체 교체(PUT 의미) — 응답과 같은 구조
    public record Entry(@NotBlank String type, boolean emailEnabled) {}
}

// ── 구성원 · 초대
public record MemberResponse(
        UUID id, String name, String email, String phone,
        String role, String status, Instant createdAt) {}            // ACTIVE / INACTIVE

public record MemberOptionResponse(UUID id, String name) {}           // DL-04 배정용, 활성만

public record ChangeRoleRequest(@NotBlank String role) {}

public record DeactivateMemberRequest(
        UUID transferToMemberId) {}
// MB-14: 담당 Deal 1건 이상이면 필수 → 없으면 422 MEMBER_INACTIVE_TRANSFER_REQUIRED (v1.6.1: 400→422)
// 할 일은 Deal을 따라 자동 이동(Q-29) — 별도 이관 없음
// 검증: 같은 회사의 활성 구성원 (타사·비활성 대상은 SC-09에 따라 404)
// 효과: 대상 구성원 refresh_token 전 행 폐기 (즉시 차단, MB-10)

public record CreateInvitationRequest(
        @NotBlank @Email String email,
        @NotBlank String role) {}                                     // MB-02 역할 필수

public record InvitationResponse(
        UUID id, String email, String role, String status,           // 4상태
        Instant expiresAt, Instant createdAt) {}

public record InvitationInfoResponse(String companyName, String email, String role) {} // 수락 화면용

public record AcceptInvitationRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 8) String password) {}
```

---

## B. 한상민 — 고객사 · 상품 · 활동

```java
// ── 고객사 (회사 공유 — 담당 개념 없음, SC-03)
public record CreateCustomerRequest(
        @NotBlank String name,
        String industry, String size, String note) {}                 // CU-02

public record UpdateCustomerRequest(
        @NotBlank String name, String industry, String size, String note) {}

public record CustomerResponse(
        UUID id, String name, String industry, String size, String note,
        UUID createdByMemberId,          // v1.2: owner → created_by. 기록용, 권한 판정 아님
        Instant createdAt) {}

public record CustomerDetailResponse(
        UUID id, String name, String industry, String size, String note,
        UUID createdByMemberId, String createdByMemberName,
        List<ContactResponse> contacts,
        List<DealSummary> deals,                                      // CU-12 딜 이력
        Instant createdAt) {
    public record DealSummary(UUID id, String title, String stage,
                              Long expectedAmount, Long wonAmount, Instant createdAt) {}
}

public record CreateContactRequest(
        @NotBlank String name, String title, String phone,
        @NotBlank @Email String email) {}                             // CU-10

public record UpdateContactRequest(                                   // v1.6 보강 — PATCH: null 필드는 미변경
        String name, String title, String phone,
        @Email String email) {}
// 대표 지정은 이 PATCH가 아니라 별도 엔드포인트 POST .../contacts/{cid}/set-primary (CU-11, body 없음) —
// 지정 시 기존 대표 자동 해제. 대표 해제만 하는 동작은 없음(대표 0명 방지)

public record ContactResponse(
        UUID id, String name, String title, String phone, String email,
        boolean primary) {}                                           // CU-11

// ── 상품
public record CreateProductRequest(
        @NotBlank String name,           // 회사 내 유일(판매 중지 포함) → 409 PRODUCT_NAME_DUPLICATED
        @NotBlank String unit,
        @NotNull @PositiveOrZero Long unitPrice,
        String description) {}

public record UpdateProductRequest(
        @NotBlank String name, @NotBlank String unit,
        @NotNull @PositiveOrZero Long unitPrice, String description) {}

public record ProductResponse(
        UUID id, String name, String unit, Long unitPrice,
        String description, String status) {}                         // ACTIVE / DISCONTINUED

// ── 활동 이력 · 할 일
public record CreateActivityRequest(
        @NotBlank String channel,        // CALL / MEETING / EMAIL (AC-02)
        @NotBlank String content,
        @NotNull Instant occurredAt) {}

public record UpdateActivityRequest(                                  // v1.6 보강 — PATCH: null 필드는 미변경
        String channel,                  // CALL / MEETING / EMAIL
        String content,
        Instant occurredAt) {}           // 작성자 본인만 (AC-04) — 타인 것은 404 ACTIVITY_NOT_AUTHOR

public record ActivityResponse(
        UUID id, String type,            // MANUAL / AUTO (AC-07)
        String channel, String content,
        UUID authorMemberId, String authorMemberName, boolean authorActive, // 표시용
        Instant occurredAt) {}

public record CreateTaskRequest(@NotBlank String content, @NotNull LocalDate dueDate) {}

public record UpdateTaskRequest(String content, LocalDate dueDate, Boolean done) {}

public record TaskResponse(
        UUID id, UUID dealId, String content, LocalDate dueDate,
        Instant doneAt) {}
// v1.5: assigneeMemberId 제거 (Q-29) — "내 할 일"은 담당 Deal 기준 조회

public record AuditLogResponse(                                   // 목록 — payload 제외 (가벼움)
        UUID id, String entityType, UUID entityId, String eventType,
        String actorType, UUID actorId, String actorName, Instant occurredAt) {}

public record AuditLogDetailResponse(                             // 상세 — AC-11 "무엇을 변경했는지"의 실체
        UUID id, String entityType, UUID entityId, String eventType,
        String actorType, UUID actorId, String actorName,
        Map<String, FieldChange> changes,                         // payload 규약: 변경된 필드만
        Instant occurredAt) {
    public record FieldChange(Object before, Object after) {}
}
// payload 규약(B가 이벤트 포맷 정의 시 준수): {"stage": {"before": "NEGOTIATION", "after": "WON"}}
// 처럼 "변경된 필드만" before/after로. 비밀번호·토큰·해시는 저장 금지(기존 규약 유지)
```

---

## C. 최선진 — Deal · 견적 · 주문

```java
// ── Deal
public record CreateDealRequest(
        @NotNull UUID customerId,
        @NotBlank String title,
        @PositiveOrZero Long expectedAmount,   // DL-02 — null 허용(미정)
        LocalDate dueDate,
        UUID assigneeMemberId) {}              // null이면 생성자 본인. 활성 구성원만

public record UpdateDealRequest(
        @NotBlank String title,
        @PositiveOrZero Long expectedAmount,
        LocalDate dueDate,
        @NotNull Integer version) {}           // 낙관적 락

public record DealResponse(                    // 목록·보드 공용
        UUID id, String title, String stage,
        Long expectedAmount,
        Long wonAmount,                        // DL-18: 주문 합계, 주문 없으면 null. 표시: 성사 전 expected, 성사 후 won
        UUID customerId, String customerName,
        UUID assigneeMemberId, String assigneeMemberName,
        LocalDate dueDate, Integer version, Instant createdAt) {}

public record DealDetailResponse(
        UUID id, String title, String stage,
        Long expectedAmount, Long wonAmount,
        UUID customerId, String customerName,
        UUID assigneeMemberId, String assigneeMemberName,
        LocalDate dueDate, String lostReason,
        List<QuoteSummary> quotes,             // DL-15
        List<OrderSummary> orders,
        Integer version, Instant createdAt) {
    public record QuoteSummary(UUID id, String quoteNo, String status, Long totalAmount, Instant sentAt) {}
    public record OrderSummary(UUID id, String orderNo, Long totalAmount, Instant createdAt) {}
}

public record StageMoveRequest(@NotNull Integer version) {}           // advance / revert / reopen(DL-12) 공용

public record LoseDealRequest(@NotBlank String reason, @NotNull Integer version) {}

public record ChangeAssigneeRequest(
        @NotNull UUID assigneeMemberId,        // 같은 회사 활성 구성원
        @NotNull Integer version) {}           // v1.6.1 — deal 수정·전이 공통 규칙(§0), 불일치 409 STALE_VERSION

// ── 견적
// GET /quotes/{id}/preview (QT-12)는 별도 record 없음 — D의 PublicQuoteResponse를 그대로 재사용 (고객이 볼 화면과 동일 보장)
public record CreateQuoteRequest(@NotNull UUID dealId) {}             // 종결 Deal → 409 QUOTE_DEAL_CLOSED

public record UpdateQuoteRequest(                                     // PUT — DRAFT만
        @NotNull @Future LocalDate validUntil,                        // QT-09 = 링크 만료 (Q-17)
        @NotNull String vatMode,                                      // EXCLUDED(기본) / INCLUDED (Q-16)
        @Size(max = 2000) String terms,
        @NotEmpty List<Item> items,
        @NotNull Integer version) {
    public record Item(
            UUID productId,                                           // null = 직접 입력 (QT-03)
            @NotBlank String name, @NotBlank String unit,
            @Positive int quantity,
            @PositiveOrZero Long unitPrice,                           // 0원 하한 — 음수는 400 (Q-02)
            int sortOrder) {}
}

public record SendQuoteRequest(
        @NotNull UUID recipientContactId,                             // 대표 담당자 1명 (Q-07)
        @Size(max = 500) String message) {}

public record ResendViewTokenRequest(                                 // v1.6 보강 — POST /quotes/{id}/view-token/resend (AP-13)
        @NotNull UUID recipientContactId) {}                          // 수신인 변경 재발송 — 기존 링크 EXPIRED(RESENT) + 새 행.
                                                                      // CONTACT_NOT_IN_CUSTOMER 검증은 /send와 동일

public record SendQuoteResponse(
        UUID quoteId, String status,                                  // SENT
        String dealStage,                                             // Q-25 자동 승급 반영값
        Integer version) {}                                           // Q-38 — 발송 직후 회수·수정으로 이어질 때 재조회 불필요

public record QuoteResponse(                                          // 목록
        UUID id, String quoteNo, UUID dealId, String status,
        Long totalAmount, LocalDate validUntil,
        Instant sentAt, Instant firstViewedAt, Integer version) {}

public record QuoteDetailResponse(
        UUID id, String quoteNo, UUID dealId, String dealTitle, String status,
        String vatMode, String terms, LocalDate validUntil,
        Long supplyAmount, Long vatAmount, Long totalAmount,          // 항상 서버 계산 (QT-08·22)
        List<ItemResponse> items,
        UUID clonedFromQuoteId,                                       // QT-19 복제 원본
        UUID supersededByQuoteId,                                     // QT-28 반려 → 대체 견적 링크
        String rejectReason,
        String responderName, String responderTitle,                  // v1.6.3 — 승인·반려한 사람(자기 신고, AP-19)
        Instant sentAt, Instant firstViewedAt, Instant respondedAt,
        Integer version, Instant createdAt) {
    public record ItemResponse(UUID id, UUID productId, String name, String unit,
                               int quantity, Long unitPrice, Long amount,
                               Long catalogPriceAtCreation, int sortOrder) {}   // QT-24
}

// ── 주문
public record OrderResponse(
        UUID id, String orderNo,
        UUID quoteId, String quoteNo,
        UUID dealId, String dealTitle,     // quote 조인 제공 — orders에 deal_id 컬럼 없음
        UUID customerId, String customerName,
        Long supplyAmount, Long vatAmount, Long totalAmount,          // 스냅샷 (OD-04)
        LocalDate startDate, LocalDate deliveryDate,                  // OD-10
        Instant createdAt) {}

public record OrderDetailResponse(
        UUID id, String orderNo, UUID quoteId, String quoteNo,
        UUID dealId, String dealTitle, UUID customerId, String customerName,
        Long supplyAmount, Long vatAmount, Long totalAmount,
        List<ItemResponse> items,
        LocalDate startDate, LocalDate deliveryDate, Instant createdAt) {
    public record ItemResponse(String name, String unit, int quantity,
                               Long unitPrice, Long amount) {}        // FK 없는 값 복사 (OD-04)
}

public record OrderScheduleRequest(LocalDate startDate, LocalDate deliveryDate) {}
```

---

## D. 이준형 — 고객 열람 · 알림 · 대시보드

```java
// ── 고객 열람 (public — 토큰 인증, SC-07~09)
public record PublicQuoteResponse(
        String quoteNo, String status,
        String companyName,                                           // 발송 회사
        String companyBusinessNo,                                     // 10 §5.6 · GAP-05 — 회사명과 함께 최상단 표시
        AssigneeInfo assignee,                                        // AP-18: Deal의 "현재" 담당자 동적 조회
        String vatMode, String terms, LocalDate validUntil,
        Long supplyAmount, Long vatAmount, Long totalAmount,          // 3분리 표시 (QT-25)
        List<ItemView> items,
        boolean respondable) {                                        // false: 정지·응답완료 — 버튼 비활성 안내
    public record AssigneeInfo(String name, String email, String phone) {}
    public record ItemView(String name, String unit, int quantity, Long unitPrice, Long amount) {}
}

public record ApproveQuoteRequest(                                    // v1.6.3 신설 — AP-19, Q-44
        @NotBlank @Size(max = 50) String responderName,               // 응답자가 직접 밝힌 이름 — 필수
        @Size(max = 50) String responderTitle) {}                     // 직책 — 선택
// 계정이 없어 시스템이 신원을 검증할 수 없다. 화면에도 "직접 입력하신 정보로 기록됩니다"로 안내.
// 저장은 quote.responder_name·responder_title (D가 C의 도메인 메서드에 전달)

public record RejectQuoteRequest(
        @NotBlank String reason,                                      // AP-10 사유 필수
        @NotBlank @Size(max = 50) String responderName,               // v1.6.3 — 승인과 동일 (AP-19)
        @Size(max = 50) String responderTitle) {}

public record CreateInquiryRequest(@NotBlank @Size(max = 1000) String content) {} // AP-15

// ── 알림 (NT-08 · 폴링)
public record NotificationResponse(
        UUID id,
        String type,          // QUOTE_VIEWED / QUOTE_APPROVED / QUOTE_REJECTED / REMIND_NO_RESPONSE / INQUIRY_RECEIVED / EMAIL_FAILED(NT-12)
        String message,
        String refType, UUID refId,                                   // 클릭 이동 대상
        Instant readAt, Instant createdAt) {}

// ── 대시보드
public record DashboardSummaryResponse(
        List<StageCount> pipeline,                                    // DB-01 단계별 건수·금액
        Long monthWonAmount,                                          // DB-02 이달 성사 = 주문 합계 (DL-18)
        int monthWonCount,
        List<WaitingQuote> waitingQuotes,                             // DB-03 응답 대기
        List<FollowUp> followUps,                                     // DB-05 후속 필요
        List<RecentActivity> recentActivities) {                      // DB-04
    public record StageCount(String stage, int count, Long expectedAmountSum) {}
    // v1.6.1: pipeline은 진행 단계(리드~협상)만 — WON 금액은 monthWonAmount(주문 합계, DL-18), LOST는 제외
    public record WaitingQuote(UUID quoteId, String quoteNo, String customerName,
                               Instant sentAt,
                               Instant firstViewedAt,                 // v1.6.3 — null이면 미열람 (AP-06, GAP-08)
                               LocalDate validUntil) {}
    public record FollowUp(UUID taskId, UUID dealId, String dealTitle,
                           String content, LocalDate dueDate) {}
    public record RecentActivity(UUID dealId, String dealTitle, String summary, Instant occurredAt) {}
}

public record DashboardPerformanceResponse(
        List<MemberPerformance> members,                              // DB-06·07
        List<StageConversion> conversions) {                          // DB-08
    public record MemberPerformance(UUID memberId, String name,
                                    int wonCount, Long wonAmount, int activeDealCount) {}
    public record StageConversion(String fromStage, String toStage, double rate) {}
}
```

---

## 검증 노트 (시니어 체크 — 구현 시 그대로 지킬 것)

| # | 항목 | 내용 |
| --- | --- | --- |
| 1 | 금액 계산 | supply·vat·total은 **어떤 Request에도 없다** — 클라이언트 값 신뢰 금지, 항상 서버 계산 (QT-08·22) |
| 2 | 멱등 전환 | convert-to-order에서 Deal이 이미 WON이면 no-op 유지 — `DEAL_ALREADY_WON` 던지지 않기 (Q-25). 이중 전환은 DB `UNIQUE(quote_id)`가 최종 방어 |
| 3 | 테넌트 검증 | `transferToMemberId`·`assigneeMemberId`·`customerId` 등 **모든 참조 ID는 같은 회사 소속인지 서비스에서 검사** — 실패는 403이 아니라 404 (SC-09). 복합 FK가 DB에서 이중 방어 |
| 4 | version 규칙 | 수정·전이 Request의 version 불일치 → **409 STALE_VERSION**, 클라이언트는 재조회 후 재시도. Response는 항상 최신 version 반환 — **quote·deal이 실리는 모든 Response가 대상**(SendQuoteResponse 포함, Q-38) |
| 5 | 알림 수신자 | Response에 담당자를 실을 때는 항상 **현재** 담당자 조인 (AP-18) — 발송 시점 스냅샷 금지. 알림 생성 시점의 수신자 결정은 Q-26 |
| 7 | 응답자 신원 | `responderName`·`responderTitle`은 **검증되지 않은 자기 신고**다(AP-19, Q-44). 인증된 신원처럼 표시하지 않는다 — 구성원 화면에서도 "고객이 입력한 정보"임이 드러나야 한다 |
| 8 | **refresh 토큰** | **어떤 Request·Response record에도 refresh 토큰 필드를 두지 않는다**(v1.6.4). 전달은 `HttpOnly` 쿠키뿐이며, 프론트는 값을 읽지도 저장하지도 않는다 — `credentials: 'include'`만 붙인다 |
| 6 | enum 문자열 | DTO의 상태·단계 값은 **상태 전이표 v1.6** 영문 코드와 문자 단위로 일치 — 오타는 통합 테스트에서 enum 파싱으로 잡는다 |

## 확정 절차

| 순서 | 할 일 | 담당 |
| --- | --- | --- |
| 1 | 자기 도메인 record 검토 — 필드·검증·네이밍 | A·B·C·D |
| 2 | 확정 → 각자 `domain/{도메인}/dto` 패키지에 생성 | 각 담당 |
| 3 | 공통(ErrorResponse·PageResponse)은 스켈레톤에 이미 있음 — 그대로 사용 | — |
