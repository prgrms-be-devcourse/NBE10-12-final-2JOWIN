# 기술 스택 v1.2

> 🧭 [문서 지도](README.md) · ← [13 개발 워크플로우](13-dev-workflow.md) · [15 정리 리포트](15-cleanup-report.md) →

> v1.2 (2026-08-27, 초기 세팅 반영) — **Spring Boot 4.1.1 확정**(§1.2 — 3.x는 2026-06 OSS 지원 종료로 Initializr에서 제외되어 4.x 전환, 스캐폴드 빌드·테스트로 검증 완료) · **Java 21 고정** · **빌드 = Gradle(Groovy DSL)** · **모듈 경계 = Spring Modulith 확정**(단일 Gradle 모듈 + `boundary` 패키지 — 경계 인터페이스 9종을 별도 모듈로 두어 B↔C·C↔D 양방향 호출의 순환 회피) · 테스트·품질 도구 확정(JUnit·Mockito·JaCoCo·SpotBugs·springdoc Swagger) · 프론트 HTTP 클라이언트 = **Axios** · 모니터링에 **Promtail** 명시 · §3-5 확정 처리
> v1.1 (2026-08-27) — **UI = Radix Themes 확정**(§1.1) · 알림 폴링 30초 · **refresh 전달 = 쿠키 확정**(§2-2) · **오리진 구성 = 서브도메인 분리 확정**(§1.4, 도메인 문자열은 잠정) · §3의 남은 결정을 잠정 기본값으로 채움

> 팀 확정 스택(2026-08-26)을 기록하고, **이 프로젝트의 기획 문서(요구사항·ERD·API·워크플로우)가 스택에 실제로 요구하는 것**과 대조해 보완할 결정과 보안 유의점을 명시한다.
> 스택 변경은 팀 합의 + 버전 업으로만.

## 스택 한눈에

| 계층 | 기술 | 역할 |
| --- | --- | --- |
| 프론트엔드 | **TypeScript · React · Radix** | 구성원 웹 앱(`/api/v1`) · 관리자 페이지(`/admin/api/v1`) · 고객 열람 페이지(`/public/api/v1`) — UI는 **Radix Themes**(스타일 포함) 채택, 하부의 Radix Primitives가 접근성·키보드 동작을 담당 |
| 백엔드 | **Java 21 · Spring Boot 4.1 · Gradle** | API 서버 단일 애플리케이션 — 도메인 A~D 모듈 분리 (업무 분담 §7 경계 규칙, Spring Modulith 검증) |
| 데이터베이스 | **PostgreSQL** | 원본 저장소 25테이블 (ERD v1.6) · Flyway 마이그레이션 |
| 인프라 | **AWS** | 백엔드·DB 운영 환경 |
| 배포·CI | **GitHub Actions · Vercel** | CI(빌드·테스트·Flyway 검증, 워크플로우 §3) · 프론트 배포(Vercel) · 백엔드 배포(AWS, GitHub Actions 트리거) |
| 모니터링 | **Prometheus · Loki · Promtail · Grafana** | 메트릭(Prometheus) · 로그 수집(Promtail)→저장(Loki) · 대시보드·알람(Grafana) |

---

## 1. 계층별 상세 — 프로젝트 근거와 함께

### 1.1 프론트엔드 — TypeScript + React

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| API 계약 | DTO 설계서 v1.6의 record가 곧 응답 형태 — TS 타입을 DTO와 1:1로 작성 (UUID=string, 금액=number 원 단위, 날짜 ISO-8601) | 08-dto.md §0 |
| 에러 처리 | ErrorCode → 사용자 문구는 **API 부록 문구를 그대로 노출**. 404는 통일 문구, 409 STALE_VERSION은 "새로고침 후 재시도" 플로우 | 07-api-spec.md 부록, SC-09 |
| 세션 처리 | access 15분 만료 → refresh 회전 호출, REFRESH_TOKEN_NOT_ACTIVE(401) 수신 시 로그인 화면 이동 | AU-12, Q-32 |
| 화면 3종 분리 | 구성원 앱 / 관리자 페이지(별도 경로, AU-08) / 고객 열람 페이지(비로그인, 토큰 링크) — 라우팅·번들 분리 권장 | AU-08, SC-07 |
| **UI 라이브러리** | **Radix Themes 확정** — 디자이너 없이 4주를 가야 하므로 색·간격·타이포·컴포넌트가 완비된 스타일드 라이브러리를 쓴다. 토큰: **accent `indigo` · gray `slate` · radius `medium`**. Dialog·Select·Tabs·DropdownMenu의 포커스 트랩·키보드 동작을 Primitives가 처리하므로 확인 모달 3종(발송·승인·비활성화)의 접근성 비용이 사라진다 | 10-screen-design.md §2 · `wireframes/v2` |
| 인앱 알림 | 폴링 기반 (실시간 푸시는 NT-09 미룸) — **주기 30초 확정** (열람 알림의 체감 즉시성과 서버 부하의 절충. 탭 비활성 시 중단) | Q-23 |

### 1.2 백엔드 — Java + Spring

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| Java·Boot 버전 | **Java 21 LTS + Spring Boot 4.1.1 고정** — CI·로컬·운영 동일 (record 필수라 17 미만 불가. 3.x는 OSS 지원 종료로 채택 불가, v1.2 확정) | 08-dto.md §0 · 초기 세팅 |
| 빌드 | **Gradle (Groovy DSL)** 단일 모듈 — 멀티모듈은 경계 인터페이스의 양방향 호출(B↔C·C↔D)이 모듈 순환이 되어 api/impl 분리 20+모듈이 강제되므로 배제 | 초기 세팅 확정 |
| 핵심 의존성 | Spring Web · Spring Data JPA(@Version 낙관적 락) · Spring Security(필터 체인 3분리) · **JJWT**(HS256, §3-4) · Bean Validation · **Flyway** · Spring Scheduler(D 배치) · **JavaMailSender**(발송 서비스는 §3-3) · **springdoc-openapi**(Swagger UI — API 테스트) | 06-erd.md · 07-api-spec.md · 13-dev-workflow.md |
| 테스트·품질 | **JUnit 5 + Mockito**(단위) · **JaCoCo**(커버리지 — PR 코멘트 자동) · **SpotBugs**(정적 분석 — PR 라인 어노테이션 자동, 위반 시 빌드 실패) — 플로우: 구현 → 단위 테스트 → Swagger API 테스트. `./gradlew test`는 스키마 검증(contextLoads)을 포함해 compose PG 필요 | 팀 확정 (2026-08-27) |
| 모듈 경계 | **Spring Modulith 확정** — `com.twojo.{도메인}` 최상위 패키지 = 모듈, 타인 Repository 주입 금지. 경계 인터페이스 9종(업무 분담 §7.2)은 **`boundary` 패키지**에 계약만 두고 소유 도메인이 구현(순환 회피). 검증 테스트가 CI required — 위반 = 머지 차단 | 11-work-breakdown.md §7.3 · 초기 세팅 |
| 상태 전이 | 전이표 v1.6을 엔티티 메서드로 구현 — 표에 없는 전이는 코드로 차단 | 05-state-transitions.md |
| 배치 | 만료 전이(**견적**·링크·초대·토큰 — 견적 만료 전이 배치의 소유 C vs D는 미결, 08 §3-2)·리마인드(NT-05)·임박(NT-06)·보존 삭제(30일·90일) — v1은 단일 인스턴스 스케줄러로 충분, 다중 인스턴스가 되면 중복 실행 방지 필요(email_log UNIQUE가 발송 중복은 이미 방어) | 06-erd.md · 07-api-spec.md |

### 1.3 데이터베이스 — PostgreSQL

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| 버전 | **15 이상 권장** — 부분 유니크 인덱스(`WHERE status='ACTIVE'` 등 4곳)·`lower(email)` 함수 인덱스는 PG 고유 문법이라 ERD가 사실상 PG를 전제 | 06-erd.md 제약조건 |
| 마이그레이션 | Flyway V1 baseline(25테이블, E 작성) + 도메인 번호대 — CI가 실제 PG 컨테이너에 태워 검증, `ddl-auto` 기준 금지 | 11-work-breakdown.md §1.2 · 13-dev-workflow.md §3 |
| 트랜잭션 | 채번 `SELECT FOR UPDATE`, 주문 전환 `FOR UPDATE` + UNIQUE(quote_id) — 격리 수준 기본(READ COMMITTED)으로 설계됨 | 06-erd.md · 11-work-breakdown.md |
| 2차 확장 | **Redis는 v1 범위 아님** — "2차 Redis"는 토큰 판정 캐시로만 계획, 원본은 항상 PG, 스키마 변경 없음 | 06-erd.md (v1.5 이력) |

### 1.4 인프라 — AWS / 배포 — Vercel + GitHub Actions

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| 역할 구분 | **Vercel = 프론트 호스팅** · **AWS = 백엔드 + PostgreSQL** | 스택 확정 |
| **오리진 구성 (확정)** | **서브도메인 분리** — 프론트 `app.x.com` · API `api.x.com`. 같은 사이트(eTLD+1 동일)라 refresh 쿠키가 **`SameSite=Lax`로 동작**한다. 오리진은 서로 다르므로 **CORS는 필요**: `Access-Control-Allow-Origin`에 `https://app.x.com` **정확히 명시** + `Allow-Credentials: true`, 프론트는 `credentials:'include'`.<br>⚠️ **`x.com`은 자리표시자다** — 도메인 확보 시 이 문자열만 교체하면 되고 구조는 바뀌지 않는다. 확보하지 못한 채 배포하면 교차 오리진이 되어 `SameSite=None; Secure` + **Origin 헤더 검증**으로 전환해야 한다(§2-1) | §2-2, Q-32 |
| CI | GitHub Actions — build / flyway-validate / flyway-version-check 3잡, PR 차단 (required check) | 13-dev-workflow.md §3 |
| CD | 프론트: Vercel Git 연동(develop=Preview, release·main=Production 권장). 백엔드: GitHub Actions → AWS (형태는 §3-1 미결) | 13-dev-workflow.md §1 |
| 메일 발송 | NT-01~06·13의 시스템 메일 — AWS 위에서는 **SES가 자연스러운 후보**이나 미확정 (§3-3). email_log가 발송 기록·중복 방지 담당 | 06-erd.md · 07-api-spec.md |

### 1.5 모니터링 — Prometheus + Loki + Grafana

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| 메트릭 | Spring Boot Actuator + Micrometer(`/actuator/prometheus`) — HTTP 상태별 카운트, DB 커넥션 풀, 배치 실행 결과 | 표준 구성 |
| 로그 | 구조화(JSON) 로그 → **Promtail 수집** → Loki 저장. **traceId 등 상관관계 ID를 로그에 포함** (v1.0 응답 규약에서 빠진 traceId의 실거처는 응답 바디가 아니라 로그·헤더) | 15-cleanup-report.md §2-2 |
| 프로젝트 특화 지표 | 메일 발송 실패율(email_log FAILED — NT-12의 운영 신호 · **NT-13 승인 메일 실패의 유일한 감지 경로**, 요구사항 §2.13 NT-12 수신자 표) · 로그인 잠금 발생(LOGIN_LOCKED) · 배치 지연(만료 전이·리마인드) · STALE_VERSION 빈도(동시 편집 충돌) | 07-api-spec.md |
| 접근 통제 | Grafana·Prometheus·Loki는 **공개망에 노출 금지** — 내부 접근만 (§2-6) | ON-11 유사 원칙 |

---

## 2. 보안 유의점 (스택 × 이 프로젝트)

기획 문서가 이미 확정한 보안 규칙을 스택 관점에서 다시 묶은 것. **구현 시 그대로 지킬 것.**

| # | 항목 | 내용 | 근거 |
| --- | --- | --- | --- |
| 1 | 토큰 원문 미저장 | refresh·재설정·초대·열람 토큰은 **해시만 DB 저장** — raw 토큰은 발급·메일 렌더링 시점 메모리에만 존재 | 06-erd.md (token_hash UK) |
| 2 | **refresh 전달 = 쿠키 (확정, 2026-08-27)** | **`HttpOnly` + `Secure` + `SameSite` + `Path` 한정.** 이름은 구성원 `2jo_rt` / 관리자 `2jo_admin_rt`로 **분리**(한 브라우저에서 두 세션 공존, AU-08). Path를 `/api/v1/auth`·`/admin/api/v1/auth`로 좁혀 **일반 API 요청에는 아예 전송되지 않게** 한다 — CSRF 표면과 불필요한 전송을 동시에 줄인다. **refresh 원문은 응답 바디·localStorage에 절대 두지 않는다**(두면 HttpOnly의 의미가 사라진다) | 07-api-spec.md §A · 08-dto.md |
| 2-1 | **쿠키 선택의 파생 — CSRF** | 브라우저가 쿠키를 자동 전송하므로 `refresh`·`logout`이 CSRF 표면이 된다. **`SameSite=Lax`면 교차 사이트 POST에 쿠키가 붙지 않아 대부분 막힌다.** 교차 오리진 배포로 `SameSite=None`이 강제되면 **Origin 헤더 검증을 추가**한다(허용 오리진과 정확히 일치할 때만 처리) | §3-2 |
| 3 | 테넌트 격리 2중 방어 | 모든 조회에 company_id 스코프(서비스) + 복합 FK(DB) — 참조 ID 검증 실패는 404 | SC-01, 08-dto.md 검증 노트 #3 |
| 4 | 존재 비노출 | 404 통일 문구·로그인 계열 응답 통일(미가입·비활성·정지 구별 금지)·재설정 요청 202 고정 | SC-09 |
| 5 | 무차별 대입 방어 | 5회/10분 잠금은 **구성원·플랫폼 관리자·미가입 이메일 모두** 적용 (login_attempt) | AU-09, Q-30 |
| 6 | 감사·로그에 비밀 금지 | audit_log payload와 **Loki 로그 모두** 비밀번호·토큰·해시 저장 금지 — 로깅 필터에서 마스킹 | 06-erd.md (payload 규약) |
| 7 | 고객 링크 표면 최소화 | `/public/api`는 토큰이 곧 인증 — 응답에 견적 1건 외 정보 금지(SC-08), 반려 사유 등 **고객 입력값 XSS 이스케이프**(React 기본 이스케이프 유지, `dangerouslySetInnerHTML` 금지) | SC-07·08, AP-10·15 |
| 8 | 시크릿 관리 | DB 접속·메일 자격·JWT 서명 키는 GitHub Actions Secrets / AWS 보관 — 저장소·로그·프론트 번들에 포함 금지. **JWT 서명 키 교체 절차는 §3-4** | 13-dev-workflow.md §3 |
| 9 | CORS 최소 허용 | 허용 오리진은 **`https://app.x.com` 하나**(운영) — 와일드카드 금지, `Allow-Credentials: true`와 함께 쓰므로 와일드카드는 브라우저가 아예 거부한다. Preview 배포 오리진은 **개발 API에만** 허용(§3-7) | AU-08 · §1.4 |
| 10 | 관리자 경로 보호 | `/admin/api`는 별도 필터 체인 — 영업 데이터 엔드포인트 부재로 ON-11 구현. 2FA·IP 제한은 운영 백로그(Q-22) | AU-08, ON-11 |
| 11 | 모니터링 스택 자체 | Prometheus·Loki·Grafana 공개망 노출 금지(내부망/VPN) — 로그에는 영업 데이터가 흐르므로 이 스택도 SC-01의 적용 대상으로 취급 | SC-01 준용 |

---

## 3. 보완이 필요한 결정 (잠정 기본값 확정 — 팀 환경 확인 후 최종 확정)

> **이 8건은 요구사항 3절(Q-xx)로 올리지 않는다.** AWS 계정 상태·도메인 보유·비용·팀 숙련도 같은 **외부 제약이 결정 변수**라 문서 논리만으로 확정할 수 없기 때문이다.
> 대신 **"4주 범위에서 가장 단순한 쪽"이라는 원칙으로 잠정 기본값을 정해 두었다** — 팀이 환경을 확인해 그대로면 확정, 막히면 대안으로 교체한다. 착수는 기본값 기준으로 진행해도 된다.

| # | 결정 | **잠정 기본값** | 대안 · 교체 조건 | 영향 |
| --- | --- | --- | --- | --- |
| 1 | 백엔드 배포 형태 | **EC2 단일 인스턴스 + RDS(PostgreSQL)** | 비용 문제 시 EC2 내 컨테이너 DB. ECS/Beanstalk는 4주 범위에 과함 — 단일 인스턴스면 배치 다중 실행 문제도 소멸(§1.2) | E 인프라 전체 |
| 2 | ~~오리진 구성과 쿠키·CORS~~ ✅ **확정** | **쿠키 전달(§2-2) + 서브도메인 분리(§1.4)** — `app.x.com`/`api.x.com`, `SameSite=Lax`. **도메인 문자열만 잠정**이며 확보 시 교체한다 | 확보 실패 시 교차 오리진 → `SameSite=None` + Origin 검증 | A · E |
| 3 | 메일 발송 서비스 | **AWS SES** | **샌드박스 해제·도메인 인증이 지연되면 외부 SMTP(Gmail 앱 비밀번호 등)로 대체** — email_log 상태 갱신 방식은 동일 | D 알림 · NT-12 |
| 4 | JWT 서명 방식·키 관리 | **HS256 단일 키** (AWS Secrets/환경변수 보관, 교체는 키 롤링 1회 절차 문서화) | 서비스 분리·외부 검증자가 생기면 RS256 | A 인증 |
| 5 | ~~Java·Spring 버전~~ ✅ **확정 (v1.2)** | **Java 21 LTS + Spring Boot 4.1.1** — CI·로컬·운영 동일 고정. 3.x(원안)는 OSS 지원 종료(2026-06)로 전환, 스캐폴드 빌드·테스트 검증 완료 | — (record 필수라 17 미만 불가) | 전원 |
| 6 | ~~프론트 세부 스택·담당~~ ✅ **확정** | **Vite + TanStack Query + React Router + Axios** · UI **Radix Themes**(§1.1) · **오너 E 김대연**(인프라 겸임, 도메인 화면은 각 백엔드 담당자) · **MSW 목 우선 개발** | 계획은 `12-frontend-plan.md` | 전원 |
| 7 | Vercel Preview의 API 연결 | **Preview는 개발용 API 오리진만** — 운영 API 연결 금지 | — | 리뷰 플로우 안전성 |
| 8 | 모니터링 배치 위치 | **AWS 내 셀프 호스팅**(단일 EC2 docker-compose) | 리소스 부족 시 Grafana Cloud 무료 티어 | E |

> 확정되면 기본값을 §1·§2 본문에 반영하고 이 표에서 지운다. 새 미결정은 여기 추가.

---

## 다음 단계

| 순서 | 할 일 | 담당 |
| --- | --- | --- |
| 1 | §3 보완 결정 8건 확정 (특히 1·2·3은 착수 전) | 전원 (인프라는 E) |
| 2 | 백엔드 스켈레톤에 버전 고정 반영 (Java·Spring·PG·Flyway) | E 김대연 |
| 3 | 모니터링 스택 구성 + §1.5 프로젝트 특화 지표 대시보드 | E 김대연 |
