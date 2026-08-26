# API 명세서 — v1.6.4

> 🧭 [문서 지도](README.md) · ← [06 ERD](06-erd.md) · [08 DTO 설계서](08-dto.md) →

💡 요구사항·상태 전이표·ERD·권한 매트릭스의 **v1.6 계열 최신본 기준** (각 문서 변경 이력 참조). 같은 내용의 중복 사본 「api 명세서 ㅍ1.6」은 이 문서로 통합·폐기했다 — 정리 리포트 참조.
💡 **각자 자기 도메인만 검토**하고, 이견 없으면 확정 → 그대로 구현에 들어간다. **표에 없는 엔드포인트는 v1에 없다.**
💡 공통 규칙(URL·상태 코드·에러 포맷·페이징)은 업무 분담 v2.0 §1 요약을, 요청·응답 필드 상세는 「DTO 설계서 v1.6」를 따른다.

## 변경 이력

| 버전 | 변경 |
| --- | --- |
| v1.6.4 | **refresh 전달 = 쿠키 확정(2026-08-27)** — 로그인·재발급·로그아웃에 쿠키 규약 명시(HttpOnly·Secure·SameSite·Path 한정, 구성원/관리자 쿠키 분리). **refresh 원문은 요청·응답 바디에서 사라진다** |
| v1.6.3 | **화면 설계 공백 반영(2026-08-26)** — 승인·반려 요청에 **응답자 이름·직책**(AP-19, Q-44) · 대시보드 응답 대기 목록에 **열람 여부**(`firstViewedAt` — 미열람/열람 구분이 담당자 행동을 가른다) · 딜 상세와 타임라인의 책임 범위 명시 |
| v1.6.2 | **Q-36~43 반영(2026-08-26)** — 페이지네이션 표준 명시(Q-39) · **403 `FORBIDDEN` 코드·문구 확정**(Q-43 — 부록·신설 코드 요약에 수록) · 견적 만료 배치 소유 C(Q-37) · 링크 발급은 발송 트랜잭션 내 동기(Q-40) · 고객 문의 조회 API 부재 확정(Q-42) |
| v1.6.1 | **검수 보정(2026-08-26)** — 주문 전환의 자동 성사는 **Deal 단계 무관** 명시(전이표 §5 정합) · MEMBER_INACTIVE_TRANSFER_REQUIRED **400→422**(규칙 위반 계열 통일 — LAST_ADMIN_PROTECTED와 같은 층) · 담당자 변경에 version 포함(낙관적 락 일관) · NT-12 수신자 규칙 참조 명시(요구사항 §2.13 표) · 응답 완료 링크의 열람 허용 명시 · 대시보드 pipeline은 진행 단계 기준 명시 |
| v1.6 | **Q-35**(NT-12 메일 실패 알림 채널 = 인앱 전용 확정) · **A 합의: 채번에서 APPLICATION 제외**(신청은 v1에서 번호 미사용 — id로 식별, application_no 제거) · **C 2차 일부**(QUOTE_ALREADY_SENT → QUOTE_NOT_DRAFT 통합 · quote.status CHECK 값 명시 · quote_item 품목명·단위 값 복사 명시) |
| v1.5 | 인증 UX 확정: **AU-12 신설**(세션 만료·무효화 시 로그인 화면 안내) + **에러별 사용자 안내 문구 부록**(ErrorCode enum의 message 원본, 낙관적 락 에러명 `STALE_VERSION` 확정) · 실사용 점검(CU-14·NT-12, CONTACT_HAS_QUOTES·EMAIL_FAILED) · 플랫폼 관리자 세션(/admin refresh, 에러 2건) · C 리뷰 10건 + 보정 2건 · 감사 로그 상세 조회 |

## 경로 체계

| 프리픽스 | 대상 | 인증 |
| --- | --- | --- |
| `/api/v1` | 구성원 (기업 관리자 · 영업 담당자) | 로그인 세션 |
| `/admin/api/v1` | 플랫폼 관리자 | 별도 로그인 (AU-08) |
| `/public/api/v1` | 방문자 · 고객 열람 링크 | 없음 / 링크 토큰 |

> 표기: 🔶 = 영업 담당자는 **담당 Deal 기준** 리소스만 (권한 매트릭스 v1.6, SC-02·04 — 고객사는 담당 개념이 없어 🔶 아님). 표준 CRUD 필드는 DTO 설계서 참조.

**목록 공통 파라미터 (Q-39 확정)** — 모든 목록 엔드포인트에 동일 적용한다.

| 파라미터 | 규칙 |
| --- | --- |
| `page` | 0-base, 기본 0 |
| `size` | 기본 20, **최대 100** (초과 시 100으로 절삭) |
| 정렬 | **엔드포인트별 기본값 고정** — 클라이언트 지정 없음 (예: 목록은 `created_at DESC`) |
| 응답 | 공통 `PageResponse<T>`(content·page·size·totalElements·totalPages) |

---

## A. 온보딩 — 조민석 (ON)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /public/api/v1/applications | 사용 신청 | 방문자 | ON-01·02 |
| POST | /admin/api/v1/auth/login | 플랫폼 관리자 로그인 | — | AU-08 |
| POST | /admin/api/v1/auth/refresh | **토큰 재발급 (회전)** — refresh_token의 actor_type=PLATFORM_ADMIN 행 사용 · **쿠키 `2jo_admin_rt`**(Path `/admin/api/v1/auth`)로 구성원 세션과 분리 | — | AU-08, Q-32 |
| POST | /admin/api/v1/auth/logout | **관리자 로그아웃** — 해당 refresh 행 폐기(사유 LOGOUT) + 쿠키 삭제, 구성원 로그아웃(AU-02)과 동일 동작 | 플랫폼 관리자 | AU-02·08 |
| GET | /admin/api/v1/applications?status= | 신청 목록 | 플랫폼 관리자 | ON-03 |
| GET | /admin/api/v1/applications/{id} | 신청 상세 | 플랫폼 관리자 |  |
| POST | /admin/api/v1/applications/{id}/approve | 승인 → 회사 생성 + 기업 관리자 계정(**비밀번호 미설정 — password_hash NULL**). **사업자번호 중복 검사**. **효과: 승인 메일 = 비밀번호 설정 링크 발송 (NT-13, Q-33)** | 플랫폼 관리자 | ON-04·06·07 |
| POST | /admin/api/v1/applications/{id}/reject | 반려 (사유 필수, 이력 보존) — **효과: 반려 통보 메일 발송 (NT-13)** | 플랫폼 관리자 | ON-05·14, Q-15 |
| GET | /admin/api/v1/companies | 회사 목록 · 이용 현황 | 플랫폼 관리자 | ON-12 |
| POST | /admin/api/v1/companies/{id}/suspend | 정지 (사유) — 효과: 구성원 차단 + 고객 링크는 열람만 + 배치 알림 중단 + **해당 회사 구성원 refresh 전 행 폐기(재발급 차단 — 없으면 최대 14일 이용 가능, ON-09 무력화)** | 플랫폼 관리자 | ON-08·09, Q-27 |
| POST | /admin/api/v1/companies/{id}/reactivate | 정지 해제 — 링크·알림 자동 복구. **구성원은 재로그인 필요(정지 시 refresh가 폐기됐으므로)** | 플랫폼 관리자 | ON-10, Q-27 |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| EMAIL_ALREADY_MEMBER | 422 | 이미 구성원인 이메일로 신청 |
| APPLICATION_ALREADY_PENDING | 409 | 같은 이메일의 검토 대기 신청 존재 |
| APPLICATION_ALREADY_DECIDED | 409 | 처리된 신청 재승인/재반려 |
| **COMPANY_BUSINESS_NO_DUPLICATED** | 409 | **이미 가입된 사업자번호의 신청 승인 — "이미 가입된 회사입니다", 반려 유도 (ERD 전역 UNIQUE)** |
| LOGIN_FAILED | 401 | 관리자 자격 증명 불일치 |
| LOGIN_LOCKED | 429 | 5회 연속 실패 → 10분 제한 (Q-30 — login_attempt의 actor_type으로 구성원과 구분) |

> `/admin/api`에는 고객사·Deal·견적 리소스가 **존재하지 않는다** (ON-11은 엔드포인트의 부재로 구현).

## A. 인증 · 계정 — 조민석 (AU · NT-07)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /api/v1/auth/login | 로그인 — body에 `rememberMe` · **응답: access는 바디, refresh는 `Set-Cookie`** | — | AU-01·10 |
| POST | /api/v1/auth/refresh | **요청 바디 없음 — 쿠키가 곧 자격 증명.** **토큰 재발급 (회전 — 기존 refresh 폐기 + 새 발급, 새 쿠키로 교체) · 재발급 시 구성원·회사 상태 검사(비활성·정지면 거부 — 폐기 누락 대비 안전망) · 회전된 토큰 재사용 감지 시 해당 구성원 세션 전체 폐기(사유 REUSE_DETECTED), 응답은 REFRESH_TOKEN_NOT_ACTIVE로 통일(감지 사실 비노출)** | — | AU-03, Q-32 |
| POST | /api/v1/auth/logout | 로그아웃 — refresh 폐기(사유 LOGOUT) + **쿠키 삭제(`Max-Age=0`)** | 전 구성원 | AU-02 |
| GET | /api/v1/me | 내 정보 (세션 확인) | 전 구성원 | AU-03·07 |
| PATCH | /api/v1/me | 프로필 수정 (이름·연락처) | 전 구성원 | AU-07 |
| POST | /api/v1/me/password | 비밀번호 변경 | 전 구성원 | AU-04 |
| POST | /public/api/v1/auth/password-reset-request | 재설정 메일 요청 — **미가입 이메일도 동일 응답 (SC-09 인증 확장)** | 비로그인 | AU-05 |
| POST | /public/api/v1/auth/password-reset | 재설정 실행 (토큰) | 비로그인 | AU-05 |
| GET · PUT | /api/v1/me/notification-settings | 알림 수신 설정 (메일 채널만, Q-23) | 전 구성원 | NT-07 |

> (각주) 승인 통보의 비밀번호 설정 링크도 위 password-reset 두 엔드포인트를 그대로 쓴다 — `purpose=INITIAL_SETUP`, 수명 7일 (Q-33·34. RESET은 30분 유지).

**refresh 쿠키 규약 (v1.6.4 확정)** — access token은 종전대로 `Authorization: Bearer` 헤더로 보낸다.

| 항목 | 값 |
| --- | --- |
| 이름 | 구성원 **`2jo_rt`** · 플랫폼 관리자 **`2jo_admin_rt`** — **분리**해야 한 브라우저에서 두 세션이 공존한다 (AU-08 · Q-28) |
| 속성 | `HttpOnly` · `Secure` · `SameSite=Lax` (교차 오리진 배포 시 `None` + **Origin 헤더 검증**) |
| `Path` | **`/api/v1/auth`** · **`/admin/api/v1/auth`** — 일반 API 요청·`/public` 요청에는 **전송되지 않는다** |
| 수명 | `rememberMe=false` → **세션 쿠키**(브라우저 종료 시 소멸, DB `expires_at` 12h) · `true` → `Max-Age` 14일 (Q-32) |
| 삭제 | 로그아웃 시 `Max-Age=0`으로 즉시 만료 |
| 금지 | **refresh 원문을 응답 바디·`localStorage`·URL에 두지 않는다** — 두는 순간 `HttpOnly`가 무의미해진다 |

> 프론트는 인증 요청에 **`credentials: 'include'`**가 필요하고, 서버는 `Access-Control-Allow-Credentials: true` + **허용 오리진 정확 명시**(와일드카드 금지)로 응답한다.

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| LOGIN_FAILED | 401 | 자격 증명 불일치 · **미승인 신청자·비활성 구성원·정지 회사도 동일 응답** (ON-13, MB-10, ON-09) |
| LOGIN_LOCKED | 429 | 5회 연속 실패 → 10분 제한 (AU-06·09) — **미가입 이메일도 동일 동작 (SC-09 인증 확장)** |
| **REFRESH_TOKEN_NOT_ACTIVE** | 401 | 폐기·만료된 refresh 토큰으로 재발급 |
| **RESET_TOKEN_NOT_ACTIVE** | 409 | 사용·만료된 재설정 토큰으로 재설정 실행 |

## A. 구성원 · 초대 — 조민석 (MB)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | /api/v1/members | 구성원 목록 | 기업 관리자 | MB-07 |
| GET | /api/v1/members/options | 담당자 선택지 (이름·id만, **활성 구성원만**) | 전 구성원 | DL-04 배정용 |
| PATCH | /api/v1/members/{id}/role | 역할 변경 | 기업 관리자 | MB-08 |
| POST | /api/v1/members/{id}/deactivate | 비활성화 — **body `transferToMemberId`: 담당 Deal 1건 이상이면 필수, 0건이면 생략. 대상은 같은 회사의 활성 구성원. 효과: refresh_token 전 행 폐기 + 할 일은 Deal을 따라 자동 이동(Q-29)** | 기업 관리자 | MB-09·10·12·**14** |
| POST | /api/v1/members/{id}/reactivate | 재활성화 | 기업 관리자 |  |
| POST | /api/v1/invitations | 초대 발송 (email · role) | 기업 관리자 | MB-01·02, NT-01 |
| GET | /api/v1/invitations?status= | 초대 목록 | 기업 관리자 |  |
| POST | /api/v1/invitations/{id}/resend | 재발송 — **기존 초대 EXPIRED(RESENT) 종결 + 새 토큰 발급(새 행)** (열람 링크와 동일 패턴) | 기업 관리자 | MB-06, Q-31 |
| POST | /api/v1/invitations/{id}/cancel | 취소 | 기업 관리자 | MB-05 |
| GET | /public/api/v1/invitations/{token} | 초대 정보 확인 | 링크 | MB-03 |
| POST | /public/api/v1/invitations/{token}/accept | 수락 → 계정 생성 (name · password) | 링크 | MB-03 |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| EMAIL_ALREADY_MEMBER | 422 | 타사 소속 이메일 초대 (MB-13) |
| INVITATION_NOT_PENDING | 409 | 만료·취소·수락된 초대 링크 사용 (MB-04) |
| LAST_ADMIN_PROTECTED | 422 | 마지막 기업 관리자 비활성화·강등 (MB-11) |
| **MEMBER_INACTIVE_TRANSFER_REQUIRED** | 422 | **담당 Deal이 있는데 이관 대상 없이 비활성화 (MB-14, Q-29). 타사·비활성 대상 지정은 SC-09에 따라 404** (v1.6.1: 400→422 — 규칙 위반 계열, LAST_ADMIN_PROTECTED와 동일 층) |

---

## B. 고객사 — 한상민 (CU)

💡 고객사는 **회사 공유 자원** — 영업 담당자도 회사 전체 조회·수정 가능 (SC-03 재정의). 등록자는 `createdByMemberId`로 기록만 되며 권한 판정에 쓰지 않는다.

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /api/v1/customers | 등록 (등록자 = 생성자 기록) | 전 구성원 | CU-01·02 |
| GET | /api/v1/customers?keyword=&industry= | 목록 · 검색 — 회사 전체 | 전 구성원 | CU-03·04, SC-03 |
| GET | /api/v1/customers/{id} | 상세 (담당자·Deal 이력 포함) | 전 구성원 | CU-05·12 |
| PATCH | /api/v1/customers/{id} | 수정 | 전 구성원 | CU-06 |
| DELETE | /api/v1/customers/{id} | 소프트 삭제 | 전 구성원 | CU-07 |
| POST | /api/v1/customers/{id}/contacts | 담당자 추가 | 전 구성원 | CU-09·10 |
| PATCH · DELETE | /api/v1/customers/{id}/contacts/{cid} | 담당자 수정·삭제 | 전 구성원 |  |
| POST | /api/v1/customers/{id}/contacts/{cid}/set-primary | 대표 지정 | 전 구성원 | CU-11 |
| GET | /api/v1/customers/{id}/activities | 고객사 단위 이력 | 전 구성원 | AC-10 |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| CUSTOMER_HAS_ACTIVE_DEALS | 409 | 진행 중 Deal 있는 고객사 삭제 (CU-08) |
| PRIMARY_CONTACT_REQUIRED | 422 | 대표 담당자 삭제 시도 — 다른 담당자를 먼저 대표로 지정 |
| **CONTACT_HAS_QUOTES** | 409 | **견적 발송 이력이 있는 담당자 삭제 (CU-14) — "발송 이력이 있어 삭제할 수 없습니다"** |

## B. 상품 카탈로그 — 한상민 (PR)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | /api/v1/products?status= | 목록 (전사 공유) | 전 구성원 | PR-03·10 |
| POST | /api/v1/products | 등록 — 회사 내 이름 유일 | 기업 관리자 | PR-01·02·09 |
| PATCH | /api/v1/products/{id} | 수정 — 기존 견적 무영향, 이름 변경 시 중복 검사 | 기업 관리자 | PR-04·08 |
| POST | /api/v1/products/{id}/discontinue | 판매 중지 | 기업 관리자 | PR-05·07 |
| POST | /api/v1/products/{id}/reactivate | 판매 재개 | 기업 관리자 |  |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| **PRODUCT_NAME_DUPLICATED** | 409 | **회사 내 상품명 중복(판매 중지 포함) — 사전 검사는 UX용, 최종 방어는 DB UNIQUE 위반 예외 변환** |

## B. 활동 이력 · 감사 — 한상민 (AC)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /api/v1/deals/{dealId}/activities | 상담 기록 (channel · content · occurredAt) 🔶 | 전 구성원 | AC-01~03 |
| GET | /api/v1/deals/{dealId}/activities?type= | Deal 타임라인 (자동 기록 포함) 🔶 | 전 구성원 | AC-06·07 |
| PATCH · DELETE | /api/v1/activities/{id} | 수정·삭제 — **작성자 본인만** (작성자 비활성이면 그대로 불변) | 작성자 | AC-04·05·08 |
| POST | /api/v1/deals/{dealId}/tasks | 다음 할 일 (content · dueDate) 🔶 | 전 구성원 | AC-09 |
| PATCH | /api/v1/tasks/{id} | 완료 처리·수정 — **"내 할 일" = 내 담당 Deal의 미완료 할 일 (배정 개념 없음, Q-29)** 🔶 | 전 구성원 |  |
| GET | /api/v1/audit-logs?entityType=&from=&to= | 변경 감사 **목록** (요약 — payload 제외) | 기업 관리자 | AC-11 |
| GET | /api/v1/audit-logs/{id} | 변경 감사 **상세** — **payload(변경 전/후 값) 포함** | 기업 관리자 | AC-11 |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| ACTIVITY_NOT_AUTHOR | 404 | 타인 상담 기록 수정·삭제 — SC-09에 따라 404 |

> **경계 합의**: 자동 기록(AC-07)은 각 도메인이 도메인 이벤트를 발행하고 B의 리스너가 audit_log에 저장한다. **B가 이벤트 페이로드 포맷을 정의해 먼저 공유**한다.

---

## C. Deal — 최선진 (DL)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /api/v1/deals | 생성 (customerId · title · 예상 금액 · 마감일) — 회사의 모든 고객사에 가능, **배정 대상은 활성 구성원** | 전 구성원 | DL-01~04 |
| GET | /api/v1/deals?stage=&assigneeId=&customerId= | 목록 · 보드 데이터 🔶 | 전 구성원 | DL-06·13·14 |
| GET | /api/v1/deals/{id} | 상세 — **금액: expectedAmount + wonAmount(주문 합계)** · 견적·주문 **요약 목록**만 포함하고 **활동 이력 전체는 담지 않는다**(타임라인은 `/deals/{dealId}/activities` 담당 — v1.6.3 책임 분담 명시) 🔶 | 전 구성원 | DL-15·18 |
| PATCH | /api/v1/deals/{id} | 제목·예상 금액·마감일 수정 (**version 포함**) 🔶 | 전 구성원 | DL-02·03 |
| POST | /api/v1/deals/{id}/advance | 다음 단계 — **LEAD→CONSULT→QUOTE→NEGOTIATION (인접만, version). NEGOTIATION에서 호출 시 DEAL_WON_REQUIRES_ORDER** 🔶 | 전 구성원 | DL-07 |
| POST | /api/v1/deals/{id}/revert | 이전 단계 — **NEGOTIATION→QUOTE→CONSULT→LEAD (version). LEAD에서 호출 불가** 🔶 | 전 구성원 | DL-08 |
| POST | /api/v1/deals/{id}/lose | 실패 처리 (reason, version) — **효과: 진행 중 견적 EXPIRED + 열람 링크 만료(DEAL_LOST)** 🔶 | 전 구성원 | DL-10·11, 전이표 §5 |
| POST | /api/v1/deals/{id}/reopen | 재개 → 실패 직전 단계 🔶 | 전 구성원 | DL-12 |
| PATCH | /api/v1/deals/{id}/assignee | 담당자 변경 (**version 포함** — v1.6.1) — **대상은 같은 회사의 활성 구성원** | 기업 관리자 | DL-05, SC-06 |
| DELETE | /api/v1/deals/{id} | 소프트 삭제 🔶 | 전 구성원 | DL-16 |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| DEAL_WON_REQUIRES_ORDER | 409 | 협상→성사 수동 이동 (DL-09 — 성사는 주문 전환 자동만) |
| DEAL_ALREADY_WON | 409 | 성사 Deal의 단계 변경·실패 처리. **단, 주문 전환의 자동 성사는 멱등 — 이 에러를 던지지 않는다 (Q-25)** |
| DEAL_HAS_QUOTES | 409 | 견적 연결된 Deal 삭제 (DL-17) |
| STALE_VERSION | 409 | version 불일치 (낙관적 락) — 재조회 후 재시도 안내 |

## C. 견적 — 최선진 (QT + AP-13·14)

💡 **Q-25**: 견적 작성·발송·복제는 **진행 중(리드~협상) Deal에서만.** 발송 시 Deal 단계가 견적(QUOTE) 미만이면 자동 승급(DL-07의 예외인 시스템 전이). 성사 전에 발송된 견적은 성사 후에도 끝까지 유효하다. 주문 전환의 자동 성사는 **Deal 단계와 무관**하다 — 진행 중(리드~협상)이면 어디서든 WON으로 직행한다(전이표 §5 시스템 전이, v1.6.1 명시).

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /api/v1/quotes | 작성 시작 (dealId) → DRAFT — 종결 Deal 불가 🔶 | 전 구성원 | QT-01, Q-25 |
| GET | /api/v1/quotes?status=&dealId= | 목록 · 상태 조회 🔶 | 전 구성원 | QT-20 |
| GET | /api/v1/quotes/{id} | 상세 (항목·추적·**supersededByQuoteId**) 🔶 | 전 구성원 | AP-06·07, QT-28 |
| PUT | /api/v1/quotes/{id} | 작성 중 전체 갱신 — 항목·유효기간·부가세·조건 (**version**) 🔶 | 전 구성원 | QT-02~11·23 |
| GET | /api/v1/quotes/{id}/preview | 고객 화면 미리보기 🔶 | 전 구성원 | QT-12 |
| POST | /api/v1/quotes/{id}/send | 발송 (recipientContactId · message?) — **효과: Deal 단계 자동 승급, 응답에 갱신된 단계 포함** 🔶 | 전 구성원 | QT-13~16, AP-01, Q-25 |
| POST | /api/v1/quotes/{id}/withdraw | 회수 → 링크 만료 (종결 Deal에서도 가능 — 정리 목적) 🔶 | 전 구성원 | QT-17 |
| POST | /api/v1/quotes/{id}/clone | 복제 → 새 DRAFT — 종결 Deal 불가 🔶 | 전 구성원 | QT-19, Q-18·25 |
| POST | /api/v1/quotes/{id}/view-token/resend | 수신인 변경 재발송 (contactId) 🔶 | 전 구성원 | AP-13 |
| POST | /api/v1/quotes/{id}/view-token/expire | 열람 링크 수동 만료 🔶 | 전 구성원 | AP-14 |

> 응답 금액 3필드(supply·vat·total)는 **항상 서버 계산값** (QT-08·22·25). 요청·응답 필드 상세는 DTO 설계서 참조.

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| QUOTE_NOT_DRAFT | 409 | 작성 중 아닌 견적 수정·발송 (QT-14·16) |
| QUOTE_EMPTY_ITEMS | 409 | 항목 0개 발송 (QT-15) |
| PRODUCT_DISCONTINUED | 409 | 판매 중지 상품을 항목에 추가 (PR-06) — 상태 충돌 계열로 통일 (422→409) |
| QUOTE_NOT_WITHDRAWABLE | 409 | 작성 중·종결 상태 회수 |
| **QUOTE_DEAL_CLOSED** | 409 | **종결(성사·실패) Deal에서 견적 작성·발송·복제 (Q-25) — 추가 거래는 새 Deal로** |
| **CONTACT_NOT_IN_CUSTOMER** | 409 | **견적의 고객사 소속이 아닌 담당자를 수신인 지정 (/send · /view-token/resend) — quote→deal→customer_id와 contact→customer_id 일치 검증. 복합 FK 불가 영역이라 서비스 검증이 유일 방어 (ERD "DB로 못 막는 것")** |
| STALE_VERSION | 409 | version 불일치 (낙관적 락) |

## C. 주문 — 최선진 (OD)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | /api/v1/quotes/{id}/convert-to-order | 주문 전환 → 스냅샷 생성 + Deal 성사(**단계 무관 — 진행 중 딜이면 어디서든 WON**, 전이표 §5). **이미 성사(WON)면 유지(멱등) — 두 번째 승인 견적도 전환 가능, 주문 추가 생성 (Q-25)** 🔶 | 전 구성원 | OD-01~07 |
| GET | /api/v1/orders?from=&to= | 목록 🔶 | 전 구성원 | OD-08 |
| GET | /api/v1/orders/{id} | 상세 (스냅샷 항목) — **dealId는 quote 조인 제공** 🔶 | 전 구성원 | OD-09 |
| PATCH | /api/v1/orders/{id}/schedule | 착수일·납기 기록 🔶 | 전 구성원 | OD-10 |

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| QUOTE_NOT_APPROVED | 409 | 미승인 견적 전환 (OD-02) |
| QUOTE_ALREADY_CONVERTED | 409 | 재전환 (OD-03) — DB `UNIQUE(quote_id)`가 동시성 최종 방어 |

---

## D. 고객 열람 · 승인 — 이준형 (AP)

토큰이 곧 인증. 실패 시에도 견적 존재 여부를 노출하지 않는다 (SC-07~09).

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | /public/api/v1/quotes/{token} | 견적 열람 → 첫 열람 시각 기록, C의 `quote.markViewed()` 호출. **담당자 정보는 Deal의 현재 담당자를 동적 조회 (발송자 스냅샷 아님)**. **응답 완료(RESPONDED) 링크도 열람은 허용** — 재응답만 차단(AP-11), 410은 만료 링크만 (전이표 §7, v1.6.1) | 링크 | AP-02·07·**18**, QT-25 |
| POST | /public/api/v1/quotes/{token}/approve | 승인 (**responderName 필수 · responderTitle 선택**) → C의 `quote.approve()`. **회사 정지 중이면 차단** | 링크 | AP-08·11·12·**19**, SC-10 |
| POST | /public/api/v1/quotes/{token}/reject | 반려 (reason 필수 · **responderName 필수 · responderTitle 선택**) → C의 `quote.reject()`. **회사 정지 중이면 차단** | 링크 | AP-09·10·11·**19**, SC-10 |
| POST | /public/api/v1/quotes/{token}/inquiries | 문의 남기기 — **효과: 담당 구성원 + 기업 관리자에게 알림 (NT-10)** | 링크 | AP-15, Q-20 |

> **문의 조회 API는 v1에 없다 (Q-42 확정)** — 구성원은 NT-10 알림으로 접수 내용을 통지받는 것이 전부이며, 확인·답변 화면은 AP-17로 미룸. 권한 매트릭스의 "고객 문의 · 확인 ▫"이 이 상태를 가리킨다.

| 에러 | HTTP | 조건 |
| --- | --- | --- |
| LINK_EXPIRED | 410 | 만료된 링크 — 만료 안내 페이지 (AP-05) |
| LINK_ALREADY_RESPONDED | 409 | 응답 완료 링크로 재응답 (AP-11) |
| **COMPANY_SUSPENDED** | 409 | **정지된 회사의 견적 승인·반려 — 열람(GET)은 허용 (SC-10, Q-27)** |
| RESOURCE_NOT_FOUND | 404 | 존재하지 않는 토큰 (형식 오류 포함) |

> **응답자 신원 (AP-19, Q-44)**: 고객사 담당자는 계정이 없어 링크 보유자가 곧 응답자다. 따라서 응답자 이름·직책은 **본인이 밝히는 자기 신고이며 시스템은 검증하지 않는다** — 화면에도 그렇게 안내한다. 값은 quote에 저장하며(`responder_name`·`responder_title`), D는 C의 도메인 메서드에 넘길 뿐 직접 쓰지 않는다.

> **경계 합의**: D는 견적 상태를 직접 바꾸지 않고 **C가 제공하는 도메인 메서드만 호출**한다. 열람 링크(quote_view_token) 자체는 D 소유 — **역방향으로, Deal 실패 시 링크 만료(DEAL_LOST)는 C가 D의 만료 커맨드를 호출**한다 (C↔D 경계 2건이 대칭).

## D. 알림 — 이준형 (NT)

💡 **수신자 규칙 (Q-26)**: 알림 수신자는 발송 시점의 유효한 담당자. 담당자가 비활성이면 기업 관리자에게 전달 (MB-11이 수신자 존재를 보장).
💡 **정지 규칙 (Q-27)**: 회사 정지 중 리마인드(NT-05)·임박(NT-06) 배치 발송 제외 — 만료 **전이** 배치는 계속 돈다 (알림만 중단).

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | /api/v1/notifications?unreadOnly= | 인앱 알림 목록 (본인 수신분) | 전 구성원 | NT-03~05·08·**10·12** |
| POST | /api/v1/notifications/{id}/read | 읽음 처리 | 본인 | NT-08 |
| POST | /api/v1/notifications/read-all | 모두 읽음 | 본인 | NT-08 |

> 알림 type enum에 **`INQUIRY_RECEIVED`(NT-10)** · **`EMAIL_FAILED`(NT-12)** 추가 — EMAIL_FAILED는 **인앱 전용**(메일 실패를 메일로 재통보하는 순환 방지 · NT-07로 끌 수 없는 운영 필수, **Q-35**). **메일 발송 실패 시 자동 재시도 1회 → 그래도 실패면 인앱 EMAIL_FAILED — 수신자는 실패 메일별 규칙(요구사항 §2.13 NT-12 수신자 표, v1.6.1)을 따른다**(고객 대상 메일은 Deal 담당 구성원 — 수신인 확인 후 재발송 유도(AP-13) · 초대 메일은 발송자 · NT-13 실패는 인앱 수신자 없음 → email_log FAILED 운영 지표로 감지). 시스템 발송 메일(NT-01·02·06·**13**)과 배치는 API가 아니라 D의 내부 스케줄러 — `email_log UNIQUE(template_type, ref_id, recipient_email)`가 재실행 이중 발송을 차단하고, 수신자가 바뀌면 키가 달라져 새 담당자에게 정상 발송된다. 수신 설정 API는 A 소유, **발송 시 설정 확인은 D 책임**.

## D. 현황 대시보드 — 이준형 (DB)

| 메서드 | 경로 | 요약 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | /api/v1/dashboard/summary?month= | 단계별 건수·금액(**진행 단계 리드~협상 기준 — WON 금액은 "이달 성사"로 별도, LOST 제외**, v1.6.1), 이달 성사(주문 합계, DL-18), **응답 대기(견적별 `firstViewedAt` 포함 — null이면 미열람. "안 봤다"와 "봤는데 답이 없다"는 담당자가 취할 행동이 다르다, AP-06)**, 후속 필요, 최근 활동 — 영업은 본인 담당 기준 | 전 구성원 🔶 | DB-01~05 |
| GET | /api/v1/dashboard/performance?from=&to= | 담당자별 실적 · 단계별 전환율 | 기업 관리자 | DB-06~08 |

---

## 신설 에러 코드 요약 (v1.0 → v1.6, ErrorCode enum PR: 김대연)

| 코드 | HTTP | 도메인 |
| --- | --- | --- |
| COMPANY_BUSINESS_NO_DUPLICATED | 409 | A 온보딩 |
| MEMBER_INACTIVE_TRANSFER_REQUIRED | 422 | A 구성원 |
| PRODUCT_NAME_DUPLICATED | 409 | B 상품 |
| QUOTE_DEAL_CLOSED | 409 | C 견적 |
| COMPANY_SUSPENDED | 409 | D 고객 열람 |
| REFRESH_TOKEN_NOT_ACTIVE | 401 | A 인증 (v1.5) |
| RESET_TOKEN_NOT_ACTIVE | 409 | A 인증 (v1.5) |
| CONTACT_HAS_QUOTES | 409 | B 고객사 (실사용 점검) |
| CONTACT_NOT_IN_CUSTOMER | 409 | C 견적 (C 리뷰 3-3) |
| **FORBIDDEN** | **403** | **공통 — 역할 자체로 갈리는 행위 위반 (Q-43)** |

## 부록 — 에러별 사용자 안내 문구 (ErrorCode enum message 원본)

💡 프론트는 이 문구를 그대로 노출한다. 404 계열은 SC-09에 따라 **전부 동일 문구** — 존재·권한을 구별해서 말하지 않는다. 낙관적 락 에러명은 `STALE_VERSION`으로 확정.

| 코드 | HTTP | 사용자 안내 문구 |
| --- | --- | --- |
| LOGIN_FAILED | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| LOGIN_LOCKED | 429 | 로그인 시도가 너무 많습니다. 10분 후 다시 시도해 주세요. |
| REFRESH_TOKEN_NOT_ACTIVE | 401 | 세션이 만료되었습니다. 다시 로그인해 주세요. (AU-12: 로그인 화면 이동) |
| RESET_TOKEN_NOT_ACTIVE | 409 | 이 재설정 링크는 더 이상 유효하지 않습니다. 재설정을 다시 요청해 주세요. |
| EMAIL_ALREADY_MEMBER | 422 | 이미 사용 중인 이메일입니다. |
| APPLICATION_ALREADY_PENDING | 409 | 이미 검토 중인 신청이 있습니다. |
| APPLICATION_ALREADY_DECIDED | 409 | 이미 처리된 신청입니다. |
| COMPANY_BUSINESS_NO_DUPLICATED | 409 | 이미 가입된 회사입니다. |
| INVITATION_NOT_PENDING | 409 | 이 초대는 더 이상 유효하지 않습니다. 관리자에게 재발송을 요청해 주세요. |
| LAST_ADMIN_PROTECTED | 422 | 회사에는 최소 한 명의 관리자가 필요합니다. |
| MEMBER_INACTIVE_TRANSFER_REQUIRED | 422 | 담당 중인 Deal이 있습니다. 이관받을 구성원을 지정해 주세요. |
| CUSTOMER_HAS_ACTIVE_DEALS | 409 | 진행 중인 Deal이 있어 삭제할 수 없습니다. |
| PRIMARY_CONTACT_REQUIRED | 422 | 대표 담당자는 삭제할 수 없습니다. 먼저 다른 담당자를 대표로 지정해 주세요. |
| CONTACT_HAS_QUOTES | 409 | 견적 발송 이력이 있어 삭제할 수 없습니다. |
| PRODUCT_NAME_DUPLICATED | 409 | 이미 등록된 상품명입니다. 판매 중지된 상품이라면 판매 재개를 이용하세요. |
| QUOTE_NOT_DRAFT | 409 | 작성 중인 견적만 수정·발송할 수 있습니다. |
| QUOTE_EMPTY_ITEMS | 409 | 견적 항목을 1개 이상 추가해 주세요. |
| PRODUCT_DISCONTINUED | 409 | 판매 중지된 상품은 견적에 추가할 수 없습니다. |
| QUOTE_NOT_WITHDRAWABLE | 409 | 이 상태의 견적은 회수할 수 없습니다. |
| QUOTE_DEAL_CLOSED | 409 | 종결된 Deal에는 견적을 작성할 수 없습니다. 새 Deal을 만들어 진행해 주세요. |
| CONTACT_NOT_IN_CUSTOMER | 409 | 이 Deal의 고객사에 소속된 담당자만 수신인으로 지정할 수 있습니다. |
| QUOTE_NOT_APPROVED | 409 | 승인된 견적만 주문으로 전환할 수 있습니다. |
| QUOTE_ALREADY_CONVERTED | 409 | 이미 주문으로 전환된 견적입니다. |
| STALE_VERSION | 409 | 다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요. |
| LINK_EXPIRED | 410 | 만료된 링크입니다. 담당자에게 재발송을 요청해 주세요. |
| LINK_ALREADY_RESPONDED | 409 | 이미 응답이 완료된 견적입니다. |
| COMPANY_SUSPENDED | 409 | 현재 이 견적에는 응답할 수 없습니다. 담당자에게 문의해 주세요. |
| **FORBIDDEN** | **403** | **이 작업을 수행할 권한이 없습니다.** (Q-43 — 역할 위반 전용. 영업 담당자의 카탈로그 편집·담당자 변경·감사 로그 조회 등. **리소스 범위 위반은 404로 유지**) |
| RESOURCE_NOT_FOUND | 404 | 요청한 대상을 찾을 수 없습니다. |
| ACTIVITY_NOT_AUTHOR | 404 | 요청한 대상을 찾을 수 없습니다. (SC-09 — 404 문구 통일) |
| DEAL_WON_REQUIRES_ORDER | 409 | 성사는 승인된 견적을 주문으로 전환할 때 자동으로 처리됩니다. |
| DEAL_ALREADY_WON | 409 | 성사된 Deal은 단계를 변경할 수 없습니다. |
| DEAL_HAS_QUOTES | 409 | 견적이 연결된 Deal은 삭제할 수 없습니다. |
| VALIDATION_FAILED | 400 | 입력값을 확인해 주세요. (fieldErrors 참조) |

## 확정 절차

| 순서 | 할 일 | 담당 |
| --- | --- | --- |
| 1 | 자기 도메인 섹션 검토 — 경로·에러·경계 합의 2건 | A 조민석 · B 한상민 · C 최선진 · D 이준형 |
| 2 | 확정 → 깃 `docs/` 반영 + ErrorCode PR | 김대연 |
| 3 | 구현 착수 — A 골격(인증·격리) 선행, B·C·D는 목 인증으로 병렬 | 전원 |
